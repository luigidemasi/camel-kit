package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Assumption;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Category;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Conflict;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Entry;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Question;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RequirementsPolicy;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RouteContract;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.SourceRef;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerStatus;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidationException;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidator;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryAnswer;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanResponse;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/** Validates one worker ledger against bounded controller-owned source bytes, without external I/O. */
final class LedgerProvenanceValidator {

    private static final int MAX_INITIAL_CONTEXT_SOURCES = 256;
    private static final long MAX_INITIAL_CONTEXT_BYTES = 8L * 1024 * 1024;
    private static final int MAX_SOURCES = ShipTreePolicy.DEFAULT_MAX_FILE_COUNT
                                           + MAX_INITIAL_CONTEXT_SOURCES
                                           + ShipInteractionBundle.MAX_EXCHANGES;
    private static final int MAX_SOURCE_BYTES = 64 * 1024 * 1024;
    private static final long MAX_AGGREGATE_SOURCE_BYTES = ShipTreePolicy.DEFAULT_MAX_AGGREGATE_BYTES
                                                           + MAX_INITIAL_CONTEXT_BYTES
                                                           + ShipInteractionBundle.MAX_SERIALIZED_BYTES;
    private static final long MAX_EXCERPT_SEARCH_BYTES = 2 * MAX_AGGREGATE_SOURCE_BYTES;
    private static final int MAX_SOURCE_ID_CHARS = 256;
    private static final int MAX_CANONICAL_LOCATOR_CHARS
            = ShipTreePolicy.MAX_PATH_CHARACTERS
              + Math.max("project:".length(), "controller:project-source#".length());
    private static final int MAX_REFERENCE_LOCATOR_CHARS
            = MAX_CANONICAL_LOCATOR_CHARS + 1 + ShipTreePolicy.MAX_PATH_CHARACTERS;

    private LedgerProvenanceValidator() {
    }

    static void validate(
            StageRequest request,
            StageResult result,
            List<SourceMaterial> sourceMaterials,
            DecisionLedger previousLedger,
            EvolutionAuthorization evolutionAuthorization) {
        requireBoundResult(request, result);
        DecisionLedger proposed = result.ledger();
        if (proposed == null) {
            throw provenanceFailure("A provenance-bearing stage result requires a decision ledger");
        }
        validateStructure(proposed, "proposed");
        if (previousLedger != null) {
            validateStructure(previousLedger, "previous");
        }
        validateRevision(request, result, previousLedger, proposed);

        ValidationContext context = ValidationContext.create(sourceMaterials);
        if (previousLedger != null) {
            validateLedger(previousLedger, context);
        }
        validateLedger(proposed, context);
        validateEvolution(
                request,
                result,
                previousLedger,
                proposed,
                evolutionAuthorization,
                context);
    }

    private static void validateStructure(DecisionLedger ledger, String label) {
        try {
            LedgerValidator.validateAnalysis(ledger);
        } catch (LedgerValidationException e) {
            throw provenanceFailure("The " + label + " decision ledger is structurally invalid: " + e.getMessage());
        }
    }

    private static void requireBoundResult(StageRequest request, StageResult result) {
        if (request == null || result == null
                || request.schemaVersion() != StageRequest.SCHEMA_VERSION
                || result.schemaVersion() != StageResult.SCHEMA_VERSION
                || request.schemaVersion() != result.schemaVersion()
                || !validIdentity(request.runId(), MAX_SOURCE_ID_CHARS)
                || request.stage() == null
                || !validIdentity(request.attemptId(), MAX_SOURCE_ID_CHARS)
                || !validIdentity(request.challenge(), MAX_SOURCE_ID_CHARS)
                || !Objects.equals(request.runId(), result.runId())
                || request.stage() != result.stage()
                || !Objects.equals(request.attemptId(), result.attemptId())
                || !Objects.equals(request.challenge(), result.challenge())
                || !Objects.equals(request.inputDigest(), result.inputDigest())
                || result.outcome() == null
                || !ShipDigest.isSha256(request.inputDigest())
                || !ShipDigest.isSha256(request.policyDigest())) {
            throw provenanceFailure("The decision ledger is not bound to the active worker request");
        }
    }

    private static void validateRevision(
            StageRequest request,
            StageResult result,
            DecisionLedger previous,
            DecisionLedger proposed) {
        if (previous == null) {
            if (request.stage() != ShipStage.DISCOVERY
                    || proposed.revision() != 1
                    || proposed.gapReviewStatus() != DecisionLedger.GapReviewStatus.NOT_RUN) {
                throw provenanceFailure(
                        "Only initial discovery may create revision 1 with gapReviewStatus NOT_RUN");
            }
            return;
        }
        if (request.stage() == ShipStage.REVIEW && result.outcome() == StageResult.Outcome.COMPLETED) {
            if (previous.gapReviewStatus() != DecisionLedger.GapReviewStatus.NOT_RUN
                    || !withGapReviewPassed(previous).equals(proposed)) {
                throw provenanceFailure(
                        "A passing gap review may only change gapReviewStatus from NOT_RUN to PASSED");
            }
            return;
        }
        if (proposed.gapReviewStatus() != DecisionLedger.GapReviewStatus.NOT_RUN) {
            throw provenanceFailure("Non-passing ledger analysis must keep gapReviewStatus NOT_RUN");
        }
        long expected;
        try {
            expected = Math.addExact(previous.revision(), 1);
        } catch (ArithmeticException e) {
            throw provenanceFailure("Decision-ledger revision overflow");
        }
        if (proposed.revision() != expected) {
            throw provenanceFailure("Decision-ledger revisions must advance exactly once per analysis");
        }
    }

    private static DecisionLedger withGapReviewPassed(DecisionLedger ledger) {
        return new DecisionLedger(
                ledger.schemaVersion(),
                ledger.revision(),
                ledger.facts(),
                ledger.decisions(),
                ledger.conflicts(),
                ledger.assumptions(),
                ledger.catalogEvidence(),
                ledger.openQuestions(),
                ledger.completeness(),
                ledger.blockingOpenItemIds(),
                DecisionLedger.GapReviewStatus.PASSED,
                ledger.requirementsPolicy());
    }

    private static void validateEvolution(
            StageRequest request,
            StageResult result,
            DecisionLedger previous,
            DecisionLedger proposed,
            EvolutionAuthorization authorization,
            ValidationContext context) {
        if (previous == null) {
            if (authorization != null) {
                throw evolutionFailure("Initial discovery cannot carry an evolution authorization");
            }
            return;
        }
        if (request.stage() == ShipStage.REVIEW && result.outcome() == StageResult.Outcome.COMPLETED) {
            if (authorization != null) {
                throw evolutionFailure("A passing gap review cannot carry an evolution authorization");
            }
            return;
        }
        if (authorization == null) {
            if (!previous.openQuestions().isEmpty()) {
                throw evolutionFailure(
                        "A pending discovery question requires its exact controller-authorized answer");
            }
            if (result.outcome() == StageResult.Outcome.NEEDS_DISCOVERY
                    && (request.stage() == ShipStage.DESIGN || request.stage() == ShipStage.REVIEW)) {
                return;
            }
            preserveExistingAnalysis(previous, proposed);
            return;
        }
        if (authorization instanceof DiscoveryAnswerAuthorization discoveryAnswer) {
            validateDiscoveryAnswerDelta(request, previous, proposed, discoveryAnswer, context);
            return;
        }
        validateRequirementsChangeDelta(
                request,
                previous,
                proposed,
                (RequirementsChangeAuthorization) authorization,
                context);
    }

    private static void preserveExistingAnalysis(DecisionLedger previous, DecisionLedger proposed) {
        preserveExistingItems("facts", previous.facts(), proposed.facts(), Entry::id);
        preserveExistingItems("decisions", previous.decisions(), proposed.decisions(), Entry::id);
        preserveExistingItems("conflicts", previous.conflicts(), proposed.conflicts(), Conflict::id);
        preserveExistingItems("assumptions", previous.assumptions(), proposed.assumptions(), Assumption::id);
        preserveCompleteness(previous.completeness(), proposed.completeness());
        preserveBlockingItems(previous.blockingOpenItemIds(), proposed.blockingOpenItemIds());
        preserveRequirementsPolicy(previous.requirementsPolicy(), proposed.requirementsPolicy());
    }

    private static <T> void preserveExistingItems(
            String kind,
            List<T> previous,
            List<T> proposed,
            java.util.function.Function<T, String> id) {
        Map<String, T> current = new LinkedHashMap<>();
        proposed.forEach(entry -> current.put(id.apply(entry), entry));
        List<String> changed = previous.stream()
                .filter(entry -> !entry.equals(current.get(id.apply(entry))))
                .map(id)
                .toList();
        if (!changed.isEmpty()) {
            throw evolutionFailure(
                    "Existing ledger " + kind + " changed without a controller-authorized interaction: "
                                   + String.join(", ", changed));
        }
    }

    private static void preserveCompleteness(
            List<Category> previous, List<Category> proposed) {
        Map<String, Category> current = new LinkedHashMap<>();
        proposed.forEach(category -> current.put(category.id(), category));
        List<String> changed = previous.stream()
                .filter(category -> {
                    Category candidate = current.get(category.id());
                    return candidate == null
                            || category.status() != candidate.status()
                            || !Objects.equals(category.rationale(), candidate.rationale());
                })
                .map(Category::id)
                .toList();
        if (!changed.isEmpty()) {
            throw evolutionFailure(
                    "Existing completeness conclusions changed without a controller-authorized interaction: "
                                   + String.join(", ", changed));
        }
    }

    private static void preserveRequirementsPolicy(
            RequirementsPolicy previous, RequirementsPolicy proposed) {
        if (previous == null) {
            return;
        }
        if (proposed == null
                || changed(previous.runtime(), proposed.runtime())
                || changed(previous.camelVersion(), proposed.camelVersion())
                || changed(previous.platformVersion(), proposed.platformVersion())
                || changed(previous.springBootVersion(), proposed.springBootVersion())
                || changed(previous.routeDsl(), proposed.routeDsl())
                || changed(previous.expressionLanguage(), proposed.expressionLanguage())
                || changed(previous.citrusVersion(), proposed.citrusVersion())
                || changed(previous.javaPolicy(), proposed.javaPolicy())
                || changed(previous.citrusDependencies(), proposed.citrusDependencies())
                || changed(previous.approvedJavaExceptions(), proposed.approvedJavaExceptions())
                || changed(previous.routes(), proposed.routes())
                || previous.citrusTestsRequired() && !proposed.citrusTestsRequired()
                || previous.oneCitrusTestPerRoute() && !proposed.oneCitrusTestPerRoute()) {
            throw evolutionFailure(
                    "Existing implementation-policy values changed without a controller-authorized interaction");
        }
    }

    private static void preserveBlockingItems(List<String> previous, List<String> proposed) {
        Set<String> existing = Set.copyOf(previous);
        List<String> retained = proposed.stream().filter(existing::contains).toList();
        if (!retained.equals(previous)) {
            throw evolutionFailure(
                    "Existing blocking ledger items changed without a controller-authorized interaction");
        }
    }

    private static boolean changed(Object previous, Object proposed) {
        if (previous == null || previous instanceof List<?> values && values.isEmpty()) {
            return false;
        }
        return !Objects.equals(previous, proposed);
    }

    private static void validateDiscoveryAnswerDelta(
            StageRequest request,
            DecisionLedger previous,
            DecisionLedger proposed,
            DiscoveryAnswerAuthorization authorization,
            ValidationContext context) {
        DiscoveryAnswer answer = authorization.answer();
        AllowedSource answerSource = context.authorizedAnswerSource(authorization);
        if (request.stage() != ShipStage.DISCOVERY
                || answer.schemaVersion() != io.github.luigidemasi.camelkit.ship.protocol.Interaction.SCHEMA_VERSION
                || !request.runId().equals(answer.runId())
                || answer.ledgerRevision() != previous.revision()) {
            throw deltaFailure("The discovery answer is not bound to this discovery evolution");
        }

        Map<String, LedgerItem> previousItems = items(previous);
        Map<String, LedgerItem> proposedItems = items(proposed);
        LedgerItem challenged = previousItems.get(answer.openItemId());
        LedgerItem resolved = proposedItems.get(answer.openItemId());
        if (challenged == null || !isOpen(challenged.status())) {
            throw deltaFailure("The discovery answer does not authorize a currently open ledger item");
        }
        if (resolved == null || !challenged.category().equals(resolved.category())) {
            throw deltaFailure("The challenged ledger item must retain its ID and category");
        }
        if (resolved.status() != LedgerStatus.RESOLVED
                && resolved.status() != LedgerStatus.NOT_APPLICABLE) {
            throw deltaFailure("The challenged ledger item must be resolved or explicitly not applicable");
        }
        if (!Set.of("fact", "decision").contains(resolved.kind())) {
            throw deltaFailure("A resolved answer must identify an unambiguous fact or decision");
        }
        List<SourceRef> answerReferences = resolved.sourceRefs().stream()
                .filter(reference -> matches(reference, answerSource, context))
                .toList();
        if (answerReferences.isEmpty()) {
            throw deltaFailure("The challenged item must cite the exact current discovery answer");
        }
        boolean answerSupportsValue = "citrus-dependencies".equals(resolved.category())
                ? csvValueSupported(resolved.value(), answerReferences)
                : answerReferences.stream().anyMatch(
                        reference -> supports(reference.excerpt(), resolved.value()));
        if (resolved.status() == LedgerStatus.RESOLVED && !answerSupportsValue) {
            throw deltaFailure("The challenged item's resolved value is absent from the current answer");
        }
        if (resolved.status() == LedgerStatus.NOT_APPLICABLE
                && !supports(answer.response(), "not applicable")
                && !supports(answer.response(), "n/a")) {
            throw deltaFailure("Only an explicit not-applicable answer may mark the challenged item not applicable");
        }

        if (!previousItems.keySet().equals(proposedItems.keySet())
                || !filteredEntries(previous.facts(), answer.openItemId())
                        .equals(filteredEntries(proposed.facts(), answer.openItemId()))
                || !filteredEntries(previous.decisions(), answer.openItemId())
                        .equals(filteredEntries(proposed.decisions(), answer.openItemId()))
                || !filteredConflicts(previous.conflicts(), answer.openItemId())
                        .equals(filteredConflicts(proposed.conflicts(), answer.openItemId()))
                || !filteredAssumptions(previous.assumptions(), answer.openItemId())
                        .equals(filteredAssumptions(proposed.assumptions(), answer.openItemId()))) {
            throw deltaFailure("A discovery answer may not mutate unrelated ledger items");
        }

        validateQuestionDelta(previous, proposed, answer, previousItems);
        validateCompletenessDelta(previous, proposed, challenged.category());
        validateBlockingDelta(previous, proposed, answer.openItemId());
        if (previous.schemaVersion() != proposed.schemaVersion()
                || previous.gapReviewStatus() != proposed.gapReviewStatus()) {
            throw deltaFailure("A discovery answer may not mutate ledger schema or gap-review state");
        }
        validatePolicyDelta(
                previous.requirementsPolicy(), proposed.requirementsPolicy(), challenged.category(), answer);
    }

    private static void validateRequirementsChangeDelta(
            StageRequest request,
            DecisionLedger previous,
            DecisionLedger proposed,
            RequirementsChangeAuthorization authorization,
            ValidationContext context) {
        AllowedSource feedbackSource = context.authorizedRequirementsChangeSource(authorization);
        String previousDigest = canonicalLedgerDigest(previous);
        if (request.stage() != ShipStage.DISCOVERY
                || authorization.schemaVersion() != Interaction.SCHEMA_VERSION
                || !request.runId().equals(authorization.runId())
                || authorization.ledgerRevision() != previous.revision()
                || !previousDigest.equals(authorization.ledgerDigest())
                || !previousDigest.equals(authorization.requirementsDigest())
                || previous.gapReviewStatus() != DecisionLedger.GapReviewStatus.PASSED
                || !previous.openQuestions().isEmpty()) {
            throw requirementsDeltaFailure(
                    "The requirements-change feedback is not bound to the exact approved requirements revision");
        }

        Map<String, LedgerItem> previousItems = items(previous);
        Map<String, LedgerItem> proposedItems = items(proposed);
        Set<String> changedItemIds = new LinkedHashSet<>();
        Set<String> changedCategories = new LinkedHashSet<>();
        for (LedgerItem existing : previousItems.values()) {
            LedgerItem candidate = proposedItems.get(existing.id());
            if (candidate == null) {
                throw requirementsDeltaFailure(
                        "Requirements-change feedback may not delete ledger item " + existing.id());
            }
            if (!existing.exact().equals(candidate.exact())) {
                validateRequirementsFeedbackItem(existing, candidate, feedbackSource, context);
                changedItemIds.add(candidate.id());
                changedCategories.add(candidate.category());
            }
        }
        for (LedgerItem candidate : proposedItems.values()) {
            if (!previousItems.containsKey(candidate.id())) {
                validateRequirementsFeedbackItem(null, candidate, feedbackSource, context);
                changedItemIds.add(candidate.id());
                changedCategories.add(candidate.category());
            }
        }

        Map<String, Category> previousCompleteness = categories(previous.completeness());
        Map<String, Category> proposedCompleteness = categories(proposed.completeness());
        for (Category existing : previousCompleteness.values()) {
            Category candidate = proposedCompleteness.get(existing.id());
            if (candidate == null) {
                throw requirementsDeltaFailure(
                        "Requirements-change feedback may not delete completeness category " + existing.id());
            }
            if (!existing.equals(candidate)) {
                requireRequirementsFeedbackReference(
                        "completeness category " + candidate.id(),
                        candidate.sourceRefs(),
                        feedbackSource,
                        context);
                changedCategories.add(candidate.id());
            }
        }
        for (Category candidate : proposedCompleteness.values()) {
            if (!previousCompleteness.containsKey(candidate.id())) {
                requireRequirementsFeedbackReference(
                        "completeness category " + candidate.id(),
                        candidate.sourceRefs(),
                        feedbackSource,
                        context);
                changedCategories.add(candidate.id());
            }
        }

        for (Question question : proposed.openQuestions()) {
            if (!changedItemIds.contains(question.openItemId())) {
                throw requirementsDeltaFailure(
                        "A requirements-change question may target only an item changed by the exact feedback");
            }
        }
        List<String> unchangedPreviousBlockers = previous.blockingOpenItemIds().stream()
                .filter(id -> !changedItemIds.contains(id))
                .toList();
        List<String> unchangedProposedBlockers = proposed.blockingOpenItemIds().stream()
                .filter(id -> !changedItemIds.contains(id))
                .toList();
        if (!unchangedPreviousBlockers.equals(unchangedProposedBlockers)) {
            throw requirementsDeltaFailure(
                    "Requirements-change feedback may alter blockers only for feedback-bound items");
        }
        if (previous.schemaVersion() != proposed.schemaVersion()) {
            throw requirementsDeltaFailure("Requirements-change feedback may not mutate the ledger schema");
        }
        boolean policyChanged = validateRequirementsPolicyDelta(
                previous.requirementsPolicy(),
                proposed.requirementsPolicy(),
                changedCategories,
                authorization.requestedChanges());
        if (changedItemIds.isEmpty() && changedCategories.isEmpty() && !policyChanged) {
            throw requirementsDeltaFailure(
                    "Requirements-change feedback must produce an explicit feedback-bound ledger delta");
        }
    }

    private static void validateRequirementsFeedbackItem(
            LedgerItem previous,
            LedgerItem proposed,
            AllowedSource feedbackSource,
            ValidationContext context) {
        if (previous != null && !previous.category().equals(proposed.category())) {
            throw requirementsDeltaFailure(
                    "Requirements-change feedback may not retag ledger item " + proposed.id());
        }
        List<SourceRef> feedbackReferences = proposed.sourceRefs().stream()
                .filter(reference -> matches(reference, feedbackSource, context))
                .toList();
        if (feedbackReferences.isEmpty()) {
            throw requirementsDeltaFailure(
                    "Changed ledger item " + proposed.id() + " must cite the exact requirements-change feedback");
        }
        boolean valueChanged = previous == null || !Objects.equals(previous.value(), proposed.value());
        if (proposed.status() == LedgerStatus.RESOLVED || valueChanged && proposed.value() != null) {
            boolean supported = "citrus-dependencies".equals(proposed.category())
                    ? csvValueSupported(proposed.value(), feedbackReferences)
                    : feedbackReferences.stream().anyMatch(
                            reference -> supports(reference.excerpt(), proposed.value()));
            if (!supported) {
                throw requirementsDeltaFailure(
                        "Changed ledger item " + proposed.id() + " is not supported by the exact feedback");
            }
        }
        if (proposed.status() == LedgerStatus.NOT_APPLICABLE
                && !supports(feedbackSourceText(feedbackSource), "not applicable")
                && !supports(feedbackSourceText(feedbackSource), "n/a")) {
            throw requirementsDeltaFailure(
                    "Only explicit requirements feedback may mark an item not applicable");
        }
    }

    private static Map<String, Category> categories(List<Category> values) {
        Map<String, Category> result = new LinkedHashMap<>();
        values.forEach(value -> result.put(value.id(), value));
        return result;
    }

    private static void requireRequirementsFeedbackReference(
            String owner,
            List<SourceRef> references,
            AllowedSource feedbackSource,
            ValidationContext context) {
        if (references.stream().noneMatch(reference -> matches(reference, feedbackSource, context))) {
            throw requirementsDeltaFailure(owner + " must cite the exact requirements-change feedback");
        }
    }

    private static boolean validateRequirementsPolicyDelta(
            RequirementsPolicy previous,
            RequirementsPolicy proposed,
            Set<String> changedCategories,
            String feedback) {
        boolean changed = false;
        Map<String, Object> oldFields = policyFields(previous);
        Map<String, Object> newFields = policyFields(proposed);
        for (Map.Entry<String, Object> field : oldFields.entrySet()) {
            Object newValue = newFields.get(field.getKey());
            if (Objects.equals(field.getValue(), newValue)) {
                continue;
            }
            changed = true;
            boolean authorized = changedCategories.stream()
                    .map(LedgerProvenanceValidator::policyFieldsFor)
                    .anyMatch(fields -> fields.contains(field.getKey()));
            if (!authorized) {
                throw requirementsDeltaFailure(
                        "Requirements-change feedback does not authorize implementation-policy field "
                                               + field.getKey());
            }
            requirePolicySupport(
                    field.getKey(),
                    newValue,
                    feedback,
                    LedgerProvenanceValidator::requirementsDeltaFailure);
        }
        return changed;
    }

    private static String feedbackSourceText(AllowedSource source) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(source.content()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw provenanceFailure("Requirements-change feedback is not strict UTF-8");
        }
    }

    private static String canonicalLedgerDigest(DecisionLedger ledger) {
        try {
            return ShipDigest.sha256(ShipJson.mapper().writeValueAsBytes(ledger));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ShipControllerException(
                    "ledger-provenance-invalid",
                    "The previous decision ledger cannot be encoded canonically",
                    e);
        }
    }

    private static void validateQuestionDelta(
            DecisionLedger previous,
            DecisionLedger proposed,
            DiscoveryAnswer answer,
            Map<String, LedgerItem> previousItems) {
        if (previous.openQuestions().size() != 1) {
            throw deltaFailure("The recorded discovery answer requires exactly one prior ledger question");
        }
        Question answeredQuestion = previous.openQuestions().get(0);
        if (!answeredQuestion.id().equals(answer.questionId())
                || !answeredQuestion.openItemId().equals(answer.openItemId())) {
            throw deltaFailure("The recorded discovery answer does not match the prior ledger question");
        }
        for (Question question : proposed.openQuestions()) {
            LedgerItem next = previousItems.get(question.openItemId());
            if (question.id().equals(answer.questionId())
                    || question.openItemId().equals(answer.openItemId())
                    || next == null
                    || !isOpen(next.status())) {
                throw deltaFailure("A follow-up question may target only a different, unchanged open item");
            }
        }
    }

    private static void validateCompletenessDelta(
            DecisionLedger previous, DecisionLedger proposed, String authorizedCategory) {
        Set<String> previousIds = categoryIds(previous.completeness());
        Set<String> proposedIds = categoryIds(proposed.completeness());
        if (!previousIds.equals(proposedIds)
                || !filteredCategories(previous.completeness(), authorizedCategory)
                        .equals(filteredCategories(proposed.completeness(), authorizedCategory))) {
            throw deltaFailure("A discovery answer may change only its own completeness category");
        }
    }

    private static void validateBlockingDelta(
            DecisionLedger previous, DecisionLedger proposed, String authorizedId) {
        if (proposed.blockingOpenItemIds().contains(authorizedId)
                || !without(previous.blockingOpenItemIds(), authorizedId)
                        .equals(without(proposed.blockingOpenItemIds(), authorizedId))) {
            throw deltaFailure("A discovery answer may remove only the challenged blocker");
        }
    }

    private static void validatePolicyDelta(
            RequirementsPolicy previous,
            RequirementsPolicy proposed,
            String authorizedCategory,
            DiscoveryAnswer answer) {
        Map<String, Object> oldFields = policyFields(previous);
        Map<String, Object> newFields = policyFields(proposed);
        Set<String> allowed = policyFieldsFor(authorizedCategory);
        for (Map.Entry<String, Object> field : oldFields.entrySet()) {
            Object newValue = newFields.get(field.getKey());
            if (Objects.equals(field.getValue(), newValue)) {
                continue;
            }
            if (!allowed.contains(field.getKey())) {
                throw deltaFailure(
                        "The answer does not authorize implementation-policy field " + field.getKey());
            }
            requirePolicySupport(field.getKey(), newValue, answer.response());
        }
    }

    private static Map<String, Object> policyFields(RequirementsPolicy policy) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("runtime", policy == null ? null : policy.runtime());
        fields.put("camelVersion", policy == null ? null : policy.camelVersion());
        fields.put("platformVersion", policy == null ? null : policy.platformVersion());
        fields.put("springBootVersion", policy == null ? null : policy.springBootVersion());
        fields.put("routeDsl", policy == null ? null : policy.routeDsl());
        fields.put("expressionLanguage", policy == null ? null : policy.expressionLanguage());
        fields.put("citrusVersion", policy == null ? null : policy.citrusVersion());
        fields.put("citrusDependencies", policy == null ? List.of() : policy.citrusDependencies());
        fields.put("javaPolicy", policy == null ? null : policy.javaPolicy());
        fields.put("approvedJavaExceptions", policy == null ? List.of() : policy.approvedJavaExceptions());
        fields.put("routes", policy == null ? List.of() : policy.routes());
        fields.put("citrusTestsRequired", policy != null && policy.citrusTestsRequired());
        fields.put("oneCitrusTestPerRoute", policy != null && policy.oneCitrusTestPerRoute());
        return fields;
    }

    private static Set<String> policyFieldsFor(String category) {
        return switch (category) {
            case "runtime" -> Set.of("runtime");
            case "camel-version" -> Set.of("camelVersion");
            case "platform-version", "deployment" -> Set.of("platformVersion");
            case "spring-boot-version" -> Set.of("springBootVersion");
            case "dsl" -> Set.of("routeDsl");
            case "expression-language" -> Set.of("expressionLanguage");
            case "citrus-version" -> Set.of("citrusVersion");
            case "citrus-dependencies" -> Set.of("citrusDependencies");
            case "java-policy" -> Set.of("javaPolicy", "approvedJavaExceptions");
            case "route-contracts", "flows", "routing-rules" -> Set.of("routes");
            case "integration-tests" -> Set.of(
                    "routes",
                    "citrusVersion",
                    "citrusDependencies",
                    "citrusTestsRequired",
                    "oneCitrusTestPerRoute");
            default -> Set.of();
        };
    }

    private static void requirePolicySupport(String field, Object value, String answer) {
        requirePolicySupport(field, value, answer, LedgerProvenanceValidator::deltaFailure);
    }

    private static void requirePolicySupport(
            String field,
            Object value,
            String answer,
            java.util.function.Function<String, ShipControllerException> failure) {
        if (value == null) {
            throw failure.apply("The answer may not erase implementation-policy field " + field);
        }
        if (value instanceof String string) {
            if (!supports(answer, string)) {
                throw failure.apply("The answer does not support implementation-policy field " + field);
            }
            return;
        }
        if (value instanceof Enum<?> enumeration) {
            if (!supports(answer, enumeration.name().replace('_', '-'))) {
                throw failure.apply("The answer does not support implementation-policy field " + field);
            }
            return;
        }
        if (value instanceof Boolean bool) {
            if (!supports(answer, bool.toString())) {
                throw failure.apply("The answer does not support implementation-policy field " + field);
            }
            return;
        }
        if (value instanceof List<?> values) {
            for (Object element : values) {
                if (element instanceof RouteContract route) {
                    if (!supports(answer, route.routeId())
                            || !supports(answer, route.routePath())
                            || !supports(answer, route.citrusTestPath())) {
                        throw failure.apply("The answer does not support implementation-policy field " + field);
                    }
                } else if (!(element instanceof String string) || !supports(answer, string)) {
                    throw failure.apply("The answer does not support implementation-policy field " + field);
                }
            }
            return;
        }
        throw failure.apply("Unsupported implementation-policy delta for field " + field);
    }

    private static Map<String, LedgerItem> items(DecisionLedger ledger) {
        Map<String, LedgerItem> result = new LinkedHashMap<>();
        ledger.facts().forEach(entry -> addItem(result, new LedgerItem(
                "fact", entry.id(), entry.category(), entry.status(), entry.value(), entry.sourceRefs(), entry)));
        ledger.decisions().forEach(entry -> addItem(result, new LedgerItem(
                "decision", entry.id(), entry.category(), entry.status(), entry.value(), entry.sourceRefs(), entry)));
        ledger.conflicts().forEach(conflict -> addItem(result, new LedgerItem(
                "conflict",
                conflict.id(),
                conflict.category(),
                conflict.status(),
                null,
                conflict.claims().stream().map(DecisionLedger.Claim::sourceRef).toList(),
                conflict)));
        ledger.assumptions().forEach(assumption -> addItem(result, new LedgerItem(
                "assumption",
                assumption.id(),
                assumption.category(),
                assumption.status(),
                assumption.proposedValue(),
                assumption.sourceRefs(),
                assumption)));
        return Collections.unmodifiableMap(result);
    }

    private static void addItem(Map<String, LedgerItem> items, LedgerItem item) {
        if (items.putIfAbsent(item.id(), item) != null) {
            throw deltaFailure("The ledger contains duplicate item ID " + item.id());
        }
    }

    private static List<Entry> filteredEntries(List<Entry> entries, String excludedId) {
        return entries.stream().filter(entry -> !excludedId.equals(entry.id())).toList();
    }

    private static List<Conflict> filteredConflicts(List<Conflict> entries, String excludedId) {
        return entries.stream().filter(entry -> !excludedId.equals(entry.id())).toList();
    }

    private static List<Assumption> filteredAssumptions(List<Assumption> entries, String excludedId) {
        return entries.stream().filter(entry -> !excludedId.equals(entry.id())).toList();
    }

    private static List<Category> filteredCategories(List<Category> categories, String excludedId) {
        return categories.stream().filter(category -> !excludedId.equals(category.id())).toList();
    }

    private static Set<String> categoryIds(List<Category> categories) {
        Set<String> result = new LinkedHashSet<>();
        categories.forEach(category -> result.add(category.id()));
        return result;
    }

    private static List<String> without(List<String> values, String excluded) {
        return values.stream().filter(value -> !excluded.equals(value)).toList();
    }

    private static boolean matches(
            SourceRef reference, AllowedSource source, ValidationContext context) {
        return reference != null
                && Objects.equals(reference.sourceId(), source.sourceId())
                && Objects.equals(reference.digest(), source.digest())
                && Objects.equals(reference.locator(), source.locator())
                && context.contains(source, reference.excerpt());
    }

    private static boolean isOpen(LedgerStatus status) {
        return status == LedgerStatus.OPEN || status == LedgerStatus.NEEDS_USER_DECISION;
    }

    private static void validateLedger(DecisionLedger ledger, ValidationContext context) {
        Set<String> citedSourceIds = new LinkedHashSet<>();
        ledger.facts().forEach(entry -> validateEntry("fact", entry, context, citedSourceIds));
        ledger.decisions().forEach(entry -> validateEntry("decision", entry, context, citedSourceIds));
        ledger.assumptions().forEach(entry -> validateAssumption(entry, context, citedSourceIds));
        ledger.conflicts().forEach(entry -> validateConflict(entry, context, citedSourceIds));
        ledger.completeness().forEach(entry -> validateCategory(entry, context, citedSourceIds));
        context.requireInitialContextCoverage(citedSourceIds);
    }

    private static void validateEntry(
            String kind, Entry entry, ValidationContext context, Set<String> citedSourceIds) {
        String owner = kind + " " + entry.id();
        validateReferences(owner, entry.sourceRefs(), context, citedSourceIds);
        if (entry.status() == LedgerStatus.RESOLVED) {
            if ("citrus-dependencies".equals(entry.category())) {
                requireCsvValueSupport(owner, entry.value(), entry.sourceRefs());
            } else {
                requireValueSupport(owner, entry.value(), entry.sourceRefs());
            }
        }
    }

    private static void validateAssumption(
            Assumption assumption, ValidationContext context, Set<String> citedSourceIds) {
        String owner = "assumption " + assumption.id();
        validateReferences(owner, assumption.sourceRefs(), context, citedSourceIds);
        if (assumption.status() == LedgerStatus.RESOLVED) {
            requireValueSupport(owner, assumption.proposedValue(), assumption.sourceRefs());
        }
    }

    private static void validateConflict(
            Conflict conflict, ValidationContext context, Set<String> citedSourceIds) {
        for (DecisionLedger.Claim claim : conflict.claims()) {
            String owner = "conflict " + conflict.id() + " claim";
            validateReference(owner, claim.sourceRef(), context, citedSourceIds);
            if (!supports(claim.sourceRef().excerpt(), claim.value())) {
                throw unsupportedValue(owner);
            }
        }
    }

    private static void validateCategory(
            Category category, ValidationContext context, Set<String> citedSourceIds) {
        validateReferences("category " + category.id(), category.sourceRefs(), context, citedSourceIds);
    }

    private static void validateReferences(
            String owner,
            List<SourceRef> references,
            ValidationContext context,
            Set<String> citedSourceIds) {
        for (SourceRef reference : references) {
            validateReference(owner, reference, context, citedSourceIds);
        }
    }

    private static void validateReference(
            String owner,
            SourceRef reference,
            ValidationContext context,
            Set<String> citedSourceIds) {
        if (reference == null
                || !validIdentity(reference.sourceId(), MAX_SOURCE_ID_CHARS)
                || !validIdentity(reference.locator(), MAX_REFERENCE_LOCATOR_CHARS)
                || !ShipDigest.isSha256(reference.digest())) {
            throw provenanceFailure(owner + " contains an invalid source reference");
        }
        AllowedSource source = context.sources().get(reference.sourceId());
        if (source == null) {
            throw provenanceFailure(owner + " cites unknown controller-owned source " + reference.sourceId());
        }
        if (!source.digest().equals(reference.digest())
                || !source.matchesLocator(reference.locator())
                || !context.contains(source, reference.excerpt())) {
            throw provenanceFailure(owner + " cites content absent from its controller-owned "
                                    + source.kind().displayName() + " source: " + reference.sourceId());
        }
        citedSourceIds.add(reference.sourceId());
    }

    private static void requireValueSupport(String owner, String value, List<SourceRef> references) {
        if (value == null || references.stream().noneMatch(reference -> supports(reference.excerpt(), value))) {
            throw unsupportedValue(owner);
        }
    }

    private static void requireCsvValueSupport(String owner, String value, List<SourceRef> references) {
        if (!csvValueSupported(value, references)) {
            throw unsupportedValue(owner);
        }
    }

    private static boolean csvValueSupported(String value, List<SourceRef> references) {
        if (value == null) {
            return false;
        }
        List<String> values = Arrays.stream(value.split(",", -1))
                .map(String::trim)
                .toList();
        return !values.isEmpty()
                && values.stream().noneMatch(String::isBlank)
                && values.stream().allMatch(candidate -> references.stream()
                        .anyMatch(reference -> supports(reference.excerpt(), candidate)));
    }

    private static boolean supports(String excerpt, String value) {
        String normalizedExcerpt = normalizeEvidence(excerpt);
        String normalizedValue = normalizeEvidence(value);
        return !normalizedValue.isEmpty()
                && (" " + normalizedExcerpt + " ").contains(" " + normalizedValue + " ");
    }

    private static String normalizeEvidence(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static byte[] strictUtf8(String value) {
        if (value == null) {
            throw provenanceFailure("A source excerpt must not be null");
        }
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new ShipControllerException(
                    "ledger-provenance-invalid", "A source excerpt is not strict UTF-8", e);
        }
    }

    private static boolean containsNonBlankUtf8(byte[] value) {
        try {
            return !StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString()
                    .isBlank();
        } catch (CharacterCodingException e) {
            throw new ShipControllerException(
                    "ledger-provenance-invalid",
                    "Controller-owned initial-context source is not strict UTF-8",
                    e);
        }
    }

    private static boolean contains(byte[] content, byte[] excerpt) {
        if (excerpt.length == 0 || excerpt.length > content.length) {
            return false;
        }
        int[] prefix = new int[excerpt.length];
        for (int index = 1, matched = 0; index < excerpt.length; index++) {
            while (matched > 0 && excerpt[index] != excerpt[matched]) {
                matched = prefix[matched - 1];
            }
            if (excerpt[index] == excerpt[matched]) {
                matched++;
            }
            prefix[index] = matched;
        }
        for (int offset = 0, matched = 0; offset < content.length; offset++) {
            while (matched > 0 && content[offset] != excerpt[matched]) {
                matched = prefix[matched - 1];
            }
            if (content[offset] == excerpt[matched]) {
                matched++;
                if (matched == excerpt.length) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean validIdentity(String value, int maximumCharacters) {
        return value != null
                && !value.isBlank()
                && value.length() <= maximumCharacters
                && value.chars().noneMatch(character -> character <= 0x1f || character == 0x7f);
    }

    private static ShipControllerException provenanceFailure(String message) {
        return new ShipControllerException("ledger-provenance-invalid", message);
    }

    private static ShipControllerException unsupportedValue(String owner) {
        return new ShipControllerException(
                "ledger-value-unsupported", owner + " has a resolved value absent from its exact excerpts");
    }

    private static ShipControllerException evolutionFailure(String message) {
        return new ShipControllerException("ledger-evolution-invalid", message);
    }

    private static ShipControllerException deltaFailure(String message) {
        return new ShipControllerException("discovery-answer-delta-invalid", message);
    }

    private static ShipControllerException requirementsDeltaFailure(String message) {
        return new ShipControllerException("requirements-change-delta-invalid", message);
    }

    /** Exact bytes and identity loaded by the controller before invoking this pure validator. */
    enum SourceKind {
        INITIAL_CONTEXT("initial-context"),
        PROJECT("project"),
        INTERACTION("interaction");

        private final String displayName;

        SourceKind(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }
    }

    record SourceMaterial(
            SourceKind kind, String sourceId, String canonicalLocator, String digest, byte[] content) {

        SourceMaterial {
            if (kind == null
                    || !validIdentity(sourceId, MAX_SOURCE_ID_CHARS)
                    || !validIdentity(canonicalLocator, MAX_CANONICAL_LOCATOR_CHARS)
                    || !ShipDigest.isSha256(digest)
                    || content == null
                    || content.length > MAX_SOURCE_BYTES) {
                throw new IllegalArgumentException("Invalid controller-owned ledger source material");
            }
            content = content.clone();
            if (!digest.equals(ShipDigest.sha256(content))) {
                throw new IllegalArgumentException("Ledger source digest does not match its exact bytes");
            }
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    sealed interface EvolutionAuthorization
            permits DiscoveryAnswerAuthorization, RequirementsChangeAuthorization {
    }

    /** Controller-verified current answer and the exact interaction source derived from its response. */
    record DiscoveryAnswerAuthorization(DiscoveryAnswer answer, SourceMaterial source)
            implements
                EvolutionAuthorization {

        DiscoveryAnswerAuthorization {
            Objects.requireNonNull(answer, "discovery answer");
            Objects.requireNonNull(source, "discovery answer source");
            if (source.kind() != SourceKind.INTERACTION) {
                throw new IllegalArgumentException("A discovery answer requires an interaction source");
            }
        }
    }

    /** Exact controller-verified design/plan request to revise the approved requirements. */
    record RequirementsChangeAuthorization(
            DesignResponse designResponse,
            PlanResponse planResponse,
            SourceMaterial source)
            implements
                EvolutionAuthorization {

        RequirementsChangeAuthorization {
            if ((designResponse == null) == (planResponse == null)) {
                throw new IllegalArgumentException(
                        "A requirements-change authorization requires exactly one design or plan response");
            }
            if (designResponse != null
                    && designResponse.decision() != DesignDecision.REQUEST_REQUIREMENTS_CHANGES
                    || planResponse != null
                            && planResponse.decision() != PlanDecision.REQUEST_REQUIREMENTS_CHANGES) {
                throw new IllegalArgumentException(
                        "Only an exact requirements-change decision may authorize ledger evolution");
            }
            Objects.requireNonNull(source, "requirements-change source");
            if (source.kind() != SourceKind.INTERACTION) {
                throw new IllegalArgumentException("Requirements-change feedback requires an interaction source");
            }
        }

        static RequirementsChangeAuthorization design(
                DesignResponse response, SourceMaterial source) {
            return new RequirementsChangeAuthorization(
                    Objects.requireNonNull(response, "design response"), null, source);
        }

        static RequirementsChangeAuthorization plan(
                PlanResponse response, SourceMaterial source) {
            return new RequirementsChangeAuthorization(
                    null, Objects.requireNonNull(response, "plan response"), source);
        }

        int schemaVersion() {
            return designResponse != null ? designResponse.schemaVersion() : planResponse.schemaVersion();
        }

        String runId() {
            return designResponse != null ? designResponse.runId() : planResponse.runId();
        }

        long ledgerRevision() {
            return designResponse != null ? designResponse.ledgerRevision() : planResponse.ledgerRevision();
        }

        String ledgerDigest() {
            return designResponse != null ? designResponse.ledgerDigest() : planResponse.ledgerDigest();
        }

        String requirementsDigest() {
            return designResponse != null
                    ? designResponse.requirementsDigest()
                    : planResponse.requirementsDigest();
        }

        String requestedChanges() {
            return designResponse != null
                    ? designResponse.requestedChanges()
                    : planResponse.requestedChanges();
        }

        String sourceLocatorSuffix() {
            return designResponse != null
                    ? "/design/response/requestedChanges"
                    : "/plan/response/requestedChanges";
        }
    }

    private record LedgerItem(
            String kind,
            String id,
            String category,
            LedgerStatus status,
            String value,
            List<SourceRef> sourceRefs,
            Object exact) {
    }

    private record AllowedSource(
            SourceKind kind,
            String sourceId,
            String locator,
            String digest,
            byte[] content,
            boolean citationRequired) {

        private static AllowedSource from(SourceMaterial source) {
            byte[] content = source.content();
            return new AllowedSource(
                    source.kind(),
                    source.sourceId(),
                    source.canonicalLocator(),
                    source.digest(),
                    content,
                    source.kind() == SourceKind.INITIAL_CONTEXT && containsNonBlankUtf8(content));
        }

        private boolean matchesLocator(String candidate) {
            return validIdentity(candidate, MAX_REFERENCE_LOCATOR_CHARS)
                    && (candidate.equals(locator)
                            || kind == SourceKind.INITIAL_CONTEXT && candidate.startsWith(locator + '#'));
        }
    }

    private record SearchKey(String sourceId, String excerpt) {
    }

    private static final class ValidationContext {

        private final Map<String, AllowedSource> sources;
        private final Map<SearchKey, Boolean> excerpts = new HashMap<>();
        private long remainingSearchBytes = MAX_EXCERPT_SEARCH_BYTES;

        private ValidationContext(Map<String, AllowedSource> sources) {
            this.sources = sources;
        }

        private static ValidationContext create(List<SourceMaterial> sourceMaterials) {
            if (sourceMaterials == null || sourceMaterials.size() > MAX_SOURCES) {
                throw provenanceFailure("Controller-owned ledger sources exceed their bounded envelope");
            }
            Map<String, AllowedSource> sources = new LinkedHashMap<>();
            long aggregate = 0;
            for (SourceMaterial material : sourceMaterials) {
                if (material == null) {
                    throw provenanceFailure("Controller-owned ledger sources contain a null entry");
                }
                AllowedSource source = AllowedSource.from(material);
                try {
                    aggregate = Math.addExact(aggregate, source.content().length);
                } catch (ArithmeticException e) {
                    throw provenanceFailure("Controller-owned ledger source size overflow");
                }
                if (aggregate > MAX_AGGREGATE_SOURCE_BYTES) {
                    throw provenanceFailure("Controller-owned ledger sources exceed their byte quota");
                }
                if (sources.putIfAbsent(source.sourceId(), source) != null) {
                    throw provenanceFailure("Controller-owned ledger sources contain duplicate ID "
                                            + source.sourceId());
                }
            }
            return new ValidationContext(Collections.unmodifiableMap(new LinkedHashMap<>(sources)));
        }

        private Map<String, AllowedSource> sources() {
            return sources;
        }

        private AllowedSource authorizedAnswerSource(DiscoveryAnswerAuthorization authorization) {
            SourceMaterial asserted = authorization.source();
            AllowedSource source = sources.get(asserted.sourceId());
            if (source == null
                    || source.kind() != SourceKind.INTERACTION
                    || source.kind() != asserted.kind()
                    || !source.locator().equals(asserted.canonicalLocator())
                    || !source.digest().equals(asserted.digest())
                    || !Arrays.equals(source.content(), asserted.content())
                    || !Arrays.equals(source.content(), strictUtf8(authorization.answer().response()))) {
                throw provenanceFailure(
                        "The discovery answer is not bound to its exact controller-owned interaction source");
            }
            return source;
        }

        private AllowedSource authorizedRequirementsChangeSource(
                RequirementsChangeAuthorization authorization) {
            SourceMaterial asserted = authorization.source();
            AllowedSource source = sources.get(asserted.sourceId());
            if (source == null
                    || source.kind() != SourceKind.INTERACTION
                    || source.kind() != asserted.kind()
                    || !source.locator().equals(asserted.canonicalLocator())
                    || !source.locator().startsWith(
                            "controller:interaction-bundle#exchanges/")
                    || !source.locator().endsWith(authorization.sourceLocatorSuffix())
                    || !source.digest().equals(asserted.digest())
                    || !Arrays.equals(source.content(), asserted.content())
                    || !Arrays.equals(source.content(), strictUtf8(authorization.requestedChanges()))) {
                throw provenanceFailure(
                        "Requirements-change feedback is not bound to its exact controller-owned interaction source");
            }
            return source;
        }

        private void requireInitialContextCoverage(Set<String> citedSourceIds) {
            List<String> missing = sources.values().stream()
                    .filter(AllowedSource::citationRequired)
                    .map(AllowedSource::sourceId)
                    .filter(sourceId -> !citedSourceIds.contains(sourceId))
                    .toList();
            if (!missing.isEmpty()) {
                throw new ShipControllerException(
                        "initial-context-unanalysed",
                        "The ledger must cite every nonempty initial-context source; missing source IDs: "
                                                      + String.join(", ", missing));
            }
        }

        private boolean contains(AllowedSource source, String excerpt) {
            SearchKey key = new SearchKey(source.sourceId(), excerpt);
            Boolean cached = excerpts.get(key);
            if (cached != null) {
                return cached;
            }
            byte[] excerptBytes = strictUtf8(excerpt);
            long cost = Math.addExact((long) source.content().length, excerptBytes.length);
            if (cost > remainingSearchBytes) {
                throw provenanceFailure("Ledger provenance exceeds the bounded excerpt-search quota");
            }
            remainingSearchBytes -= cost;
            boolean present = LedgerProvenanceValidator.contains(source.content(), excerptBytes);
            excerpts.put(key, present);
            return present;
        }
    }
}

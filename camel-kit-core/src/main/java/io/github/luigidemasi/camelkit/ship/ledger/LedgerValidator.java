package io.github.luigidemasi.camelkit.ship.ledger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.artifact.CitrusDependencyPolicy;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadRequest;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Assumption;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Category;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Conflict;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Entry;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Question;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RequirementsPolicy;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RouteContract;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.SourceRef;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;

/** Deterministic structural and completeness policy for discovery output. */
public final class LedgerValidator {

    public static final int MAX_SOURCE_EXCERPT_CHARS = 16 * 1024;
    public static final Set<String> REQUIRED_CATEGORIES = Set.of(
            "business-purpose",
            "flows",
            "message-contracts",
            "routing-rules",
            "runtime",
            "camel-version",
            "deployment",
            "dsl",
            "expression-language",
            "java-policy",
            "error-handling",
            "security",
            "observability",
            "acceptance-criteria",
            "integration-tests",
            "citrus-version",
            "citrus-dependencies");
    private static final Set<String> SINGLE_VALUED_CATEGORIES = Set.of(
            "runtime",
            "camel-version",
            "platform-version",
            "spring-boot-version",
            "dsl",
            "expression-language",
            "java-policy",
            "citrus-version",
            "citrus-dependencies");
    private static final Pattern VERSION = Pattern.compile("[0-9]+(?:\\.[0-9A-Za-z_-]+)+(?:[-+.][0-9A-Za-z_.-]+)?");
    private static final Pattern ROUTE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");

    private LedgerValidator() {
    }

    public static void validateAnalysis(DecisionLedger ledger) {
        List<String> violations = validateCommon(ledger);
        if (ledger == null) {
            throwIfAny(violations);
            return;
        }
        if (ledger.openQuestions().size() > 1) {
            violations.add("At most one blocking discovery question may be proposed");
        }
        if (ledger.openQuestions().size() == 1) {
            Question question = ledger.openQuestions().get(0);
            if (question.status() != LedgerStatus.OPEN
                    && question.status() != LedgerStatus.NEEDS_USER_DECISION) {
                violations.add("The pending discovery question must be open");
            }
            if (!allOpenItemIds(ledger).contains(question.openItemId())) {
                violations.add("Question " + question.id() + " does not reference an open ledger item");
            }
        }
        throwIfAny(violations);
    }

    /**
     * Validates the controller gate that must be satisfied before a discovery worker can query a runtime-dependent
     * catalog.
     */
    public static void validateCatalogPrerequisites(DecisionLedger ledger) {
        List<String> violations = validateCommon(ledger);
        if (ledger != null) {
            validateCatalogPrerequisitePolicy(ledger, violations);
        }
        throwIfAny(violations);
    }

    public static void validateReady(DecisionLedger ledger) {
        validateReady(ledger, DecisionLedger.GapReviewStatus.PASSED);
    }

    /** Validates a complete candidate before the independent gap-review attempt. */
    public static void validateCandidateReady(DecisionLedger ledger) {
        validateReady(ledger, DecisionLedger.GapReviewStatus.NOT_RUN);
    }

    private static void validateReady(DecisionLedger ledger, DecisionLedger.GapReviewStatus requiredReviewStatus) {
        List<String> violations = validateCommon(ledger);
        if (ledger == null) {
            throwIfAny(violations);
            return;
        }
        if (!ledger.openQuestions().isEmpty()) {
            violations.add("A requirements-ready ledger cannot contain a pending question");
        }
        if (!ledger.blockingOpenItemIds().isEmpty()) {
            violations.add("Blocking ledger items remain: " + String.join(", ", ledger.blockingOpenItemIds()));
        }
        if (!allOpenItemIds(ledger).isEmpty()) {
            violations.add("Open or undecided ledger items remain");
        }
        Map<String, Category> categories = categoriesById(ledger, violations);
        for (String required : REQUIRED_CATEGORIES) {
            Category category = categories.get(required);
            if (category == null) {
                violations.add("Missing completeness category: " + required);
                continue;
            }
            if (category.status() != LedgerStatus.RESOLVED
                    && category.status() != LedgerStatus.NOT_APPLICABLE) {
                violations.add("Completeness category is unresolved: " + required);
            }
            if (category.status() == LedgerStatus.NOT_APPLICABLE && isBlank(category.rationale())) {
                violations.add("Not-applicable category requires a rationale: " + required);
            }
            if (category.status() == LedgerStatus.RESOLVED && !hasResolvedValue(ledger, required)) {
                violations.add("Resolved completeness category has no resolved fact or decision: " + required);
            }
        }
        if (ledger.gapReviewStatus() != requiredReviewStatus) {
            violations.add(requiredReviewStatus == DecisionLedger.GapReviewStatus.PASSED
                    ? "A fresh-context gap review must pass before requirements are ready"
                    : "Discovery cannot self-attest its fresh-context gap review");
        }
        validateRequirementsPolicy(ledger, violations);
        throwIfAny(violations);
    }

    private static List<String> validateCommon(DecisionLedger ledger) {
        List<String> violations = new ArrayList<>();
        if (ledger == null) {
            return List.of("Decision ledger is required");
        }
        if (ledger.schemaVersion() != DecisionLedger.SCHEMA_VERSION) {
            violations.add("Unsupported decision-ledger schema version: " + ledger.schemaVersion());
        }
        if (ledger.revision() < 1) {
            violations.add("Decision-ledger revision must be positive");
        }

        Set<String> ids = new HashSet<>();
        ledger.facts().forEach(entry -> validateEntry("fact", entry, ids, violations));
        ledger.decisions().forEach(entry -> validateEntry("decision", entry, ids, violations));
        ledger.conflicts().forEach(entry -> validateConflict(entry, ids, violations));
        ledger.assumptions().forEach(entry -> validateAssumption(entry, ids, violations));
        ledger.openQuestions().forEach(entry -> validateQuestion(entry, ids, violations));
        validateCatalogEvidence(ledger, violations);
        validateConclusiveCategories(ledger, violations);

        Set<String> blockers = new HashSet<>();
        for (String id : ledger.blockingOpenItemIds()) {
            if (isBlank(id)) {
                violations.add("Blocking open-item IDs must not be blank");
            } else if (!blockers.add(id)) {
                violations.add("Duplicate blocking open-item ID: " + id);
            } else if (!allItemIds(ledger).contains(id)) {
                violations.add("Unknown blocking open-item ID: " + id);
            }
        }
        categoriesById(ledger, violations);
        return violations;
    }

    private static void validateCatalogEvidence(DecisionLedger ledger, List<String> violations) {
        if (!ledger.catalogEvidence().isEmpty()) {
            violations.add("Workers cannot author catalog evidence in the decision ledger");
        }
    }

    /**
     * Prevents a worker from bypassing an earlier answer by creating a fresh open item ID in the same single-valued
     * category. These categories describe one exact implementation choice, so they cannot contain competing conclusions
     * or become open again without an explicit requirements-change transition.
     */
    private static void validateConclusiveCategories(DecisionLedger ledger, List<String> violations) {
        for (String category : SINGLE_VALUED_CATEGORIES) {
            long resolved = java.util.stream.Stream.concat(ledger.facts().stream(), ledger.decisions().stream())
                    .filter(entry -> entry != null
                            && category.equals(entry.category())
                            && entry.status() == LedgerStatus.RESOLVED)
                    .count();
            long open = java.util.stream.Stream.of(
                    ledger.facts().stream().map(entry -> new CategoryStatus(entry.category(), entry.status())),
                    ledger.decisions().stream().map(entry -> new CategoryStatus(entry.category(), entry.status())),
                    ledger.conflicts().stream().map(entry -> new CategoryStatus(entry.category(), entry.status())),
                    ledger.assumptions().stream().map(entry -> new CategoryStatus(entry.category(), entry.status())))
                    .flatMap(stream -> stream)
                    .filter(entry -> category.equals(entry.category()) && isOpen(entry.status()))
                    .count();
            if (resolved > 1) {
                violations.add("Single-valued category has multiple resolved conclusions: " + category);
            }
            if (resolved > 0 && open > 0) {
                violations.add("Resolved single-valued category cannot be reopened with another item: " + category);
            } else if (open > 1) {
                violations.add("Single-valued category has multiple open items: " + category);
            }
        }
    }

    private static Map<String, Category> categoriesById(DecisionLedger ledger, List<String> violations) {
        Map<String, Category> categories = new HashMap<>();
        for (Category category : ledger.completeness()) {
            if (category == null || isBlank(category.id()) || category.status() == null) {
                violations.add("Completeness categories require an ID and status");
                continue;
            }
            if (categories.putIfAbsent(category.id(), category) != null) {
                violations.add("Duplicate completeness category: " + category.id());
            }
            validateSourceRefs("category " + category.id(), category.sourceRefs(), violations);
        }
        return categories;
    }

    private static void validateEntry(
            String type, Entry entry, Set<String> ids, List<String> violations) {
        if (entry == null || !validateIdentity(type, entry == null ? null : entry.id(),
                entry == null ? null : entry.category(), ids, violations)) {
            return;
        }
        if (entry.status() == null) {
            violations.add(type + " " + entry.id() + " is missing status");
        }
        if (entry.status() == LedgerStatus.RESOLVED && isBlank(entry.value())) {
            violations.add(type + " " + entry.id() + " is resolved without a value");
        }
        if (entry.status() == LedgerStatus.RESOLVED && entry.sourceRefs().isEmpty()) {
            violations.add(type + " " + entry.id() + " is resolved without provenance");
        }
        if (entry.status() == LedgerStatus.NOT_APPLICABLE && isBlank(entry.rationale())) {
            violations.add(type + " " + entry.id() + " is not applicable without a rationale");
        }
        validateSourceRefs(type + " " + entry.id(), entry.sourceRefs(), violations);
    }

    private static void validateConflict(Conflict entry, Set<String> ids, List<String> violations) {
        if (entry == null || !validateIdentity("conflict", entry == null ? null : entry.id(),
                entry == null ? null : entry.category(), ids, violations)) {
            return;
        }
        if (entry.status() == null) {
            violations.add("conflict " + entry.id() + " is missing status");
        } else if (!isOpen(entry.status())) {
            violations.add("conflict " + entry.id()
                           + " must remain open until a controller-recorded answer replaces it");
        }
        if (entry.claims().size() < 2) {
            violations.add("conflict " + entry.id() + " must contain at least two claims");
        }
        entry.claims().forEach(claim -> {
            if (claim == null || isBlank(claim.value()) || claim.sourceRef() == null) {
                violations.add("conflict " + entry.id() + " contains an invalid claim");
            } else {
                validateSourceRef("conflict " + entry.id(), claim.sourceRef(), violations);
            }
        });
    }

    private static void validateAssumption(Assumption entry, Set<String> ids, List<String> violations) {
        if (entry == null || !validateIdentity("assumption", entry == null ? null : entry.id(),
                entry == null ? null : entry.category(), ids, violations)) {
            return;
        }
        if (entry.status() == null) {
            violations.add("assumption " + entry.id() + " is missing status");
        } else if (!isOpen(entry.status())) {
            violations.add("assumption " + entry.id()
                           + " must remain open until a controller-recorded answer replaces it");
        }
        if (isBlank(entry.proposedValue())) {
            violations.add("assumption " + entry.id() + " is missing its proposed value");
        }
        validateSourceRefs("assumption " + entry.id(), entry.sourceRefs(), violations);
    }

    private static void validateQuestion(Question entry, Set<String> ids, List<String> violations) {
        if (entry == null || !validateIdentity("question", entry == null ? null : entry.id(),
                entry == null ? null : entry.openItemId(), ids, violations)) {
            return;
        }
        if (isBlank(entry.prompt())) {
            violations.add("question " + entry.id() + " is missing its prompt");
        } else if (entry.prompt().length() > Interaction.MAX_PROMPT_CHARS) {
            violations.add("question " + entry.id() + " prompt exceeds "
                           + Interaction.MAX_PROMPT_CHARS + " characters");
        }
        if (entry.status() == null) {
            violations.add("question " + entry.id() + " is missing status");
        }
        if (new HashSet<>(entry.options()).size() != entry.options().size()) {
            violations.add("question " + entry.id() + " contains duplicate options");
        }
        if (entry.options().size() > Interaction.MAX_OPTIONS) {
            violations.add("question " + entry.id() + " has too many options");
        }
        for (String option : entry.options()) {
            if (isBlank(option) || option.length() > Interaction.MAX_OPTION_CHARS) {
                violations.add("question " + entry.id() + " contains a blank or oversized option");
            }
        }
        if (entry.recommendation() != null
                && entry.recommendation().length() > Interaction.MAX_OPTION_CHARS) {
            violations.add("question " + entry.id() + " recommendation is oversized");
        }
    }

    private static boolean validateIdentity(
            String type, String id, String category, Set<String> ids, List<String> violations) {
        if (isBlank(id) || isBlank(category)) {
            violations.add(type + " entries require stable IDs and categories");
            return false;
        }
        if (!ids.add(id)) {
            violations.add("Duplicate ledger item ID: " + id);
        }
        return true;
    }

    private static Set<String> allItemIds(DecisionLedger ledger) {
        Set<String> ids = new LinkedHashSet<>();
        ledger.facts().forEach(entry -> ids.add(entry.id()));
        ledger.decisions().forEach(entry -> ids.add(entry.id()));
        ledger.conflicts().forEach(entry -> ids.add(entry.id()));
        ledger.assumptions().forEach(entry -> ids.add(entry.id()));
        return ids;
    }

    private static Set<String> allOpenItemIds(DecisionLedger ledger) {
        Set<String> ids = new LinkedHashSet<>();
        ledger.facts().stream().filter(entry -> isOpen(entry.status())).forEach(entry -> ids.add(entry.id()));
        ledger.decisions().stream().filter(entry -> isOpen(entry.status())).forEach(entry -> ids.add(entry.id()));
        ledger.conflicts().stream().filter(entry -> isOpen(entry.status())).forEach(entry -> ids.add(entry.id()));
        ledger.assumptions().stream().filter(entry -> isOpen(entry.status())).forEach(entry -> ids.add(entry.id()));
        return ids;
    }

    private static boolean isOpen(LedgerStatus status) {
        return status == LedgerStatus.OPEN || status == LedgerStatus.NEEDS_USER_DECISION;
    }

    private static boolean hasResolvedValue(DecisionLedger ledger, String category) {
        return java.util.stream.Stream.concat(ledger.facts().stream(), ledger.decisions().stream())
                .anyMatch(entry -> category.equals(entry.category())
                        && entry.status() == LedgerStatus.RESOLVED
                        && !isBlank(entry.value()));
    }

    private static void validateRequirementsPolicy(DecisionLedger ledger, List<String> violations) {
        RequirementsPolicy policy = ledger.requirementsPolicy();
        String runtime = validateCatalogPrerequisitePolicy(ledger, violations);
        if (policy == null) {
            return;
        }
        if (!"yaml".equals(normalize(policy.routeDsl()))) {
            violations.add("Ship protocol v1 supports only the YAML route DSL");
        }
        if (!"simple".equals(normalize(policy.expressionLanguage()))) {
            violations.add("Ship protocol v1 supports only the Simple expression language");
        }
        List<String> citrusViolations = CitrusDependencyPolicy.violations(
                policy.citrusVersion(), policy.citrusDependencies());
        violations.addAll(citrusViolations);
        if ("main".equals(runtime) && citrusViolations.isEmpty()) {
            try {
                JvmPayloadRequest.citrus(
                        policy.camelVersion(), policy.citrusVersion(), policy.citrusDependencies());
            } catch (IllegalArgumentException e) {
                violations.add("Ship protocol v1 has no direct Citrus evidence payload for Camel "
                               + policy.camelVersion() + " and Citrus " + policy.citrusVersion());
            }
        }
        if (policy.javaPolicy() == null) {
            violations.add("Implementation policy requires a Java policy");
        }
        if ("main".equals(runtime) && policy.javaPolicy() != null
                && policy.javaPolicy() != DecisionLedger.JavaPolicy.FORBIDDEN) {
            violations.add("Ship protocol v1 requires Java policy FORBIDDEN for Camel Main because its mandatory "
                           + "startup evidence does not compile Java sources");
        }
        if (policy.javaPolicy() != DecisionLedger.JavaPolicy.JUSTIFICATION_REQUIRED
                && !policy.approvedJavaExceptions().isEmpty()) {
            violations.add("Java exceptions are valid only under justification-required policy");
        }
        Set<String> paths = new HashSet<>();
        for (String exception : policy.approvedJavaExceptions()) {
            if (!safeRelative(exception) || !exception.endsWith(".java") || !paths.add(exception)) {
                violations.add("Invalid or duplicate approved Java exception: " + exception);
            }
        }
        if (policy.routes().isEmpty()) {
            violations.add("Implementation policy requires at least one route contract");
        }
        Set<String> routeIds = new HashSet<>();
        for (RouteContract route : policy.routes()) {
            if (route == null || !safeFileSegment(route.routeId()) || !routeIds.add(route.routeId())) {
                violations.add("Implementation policy contains an invalid or duplicate route ID");
                continue;
            }
            if (!safeRelative(route.routePath()) || !route.routePath().endsWith(".camel.yaml")
                    || !paths.add(route.routePath())) {
                violations.add("Invalid or duplicate route contract path: " + route.routePath());
            } else if (!hasFileName(route.routePath(), route.routeId() + ".camel.yaml")) {
                violations.add("Route contract path must be named exactly " + route.routeId() + ".camel.yaml");
            }
            if (!safeRelative(route.citrusTestPath()) || !route.citrusTestPath().endsWith(".camel.it.yaml")
                    || !paths.add(route.citrusTestPath())) {
                violations.add("Invalid or duplicate Citrus contract path: " + route.citrusTestPath());
            } else if (!("test/" + route.routeId() + ".camel.it.yaml").equals(route.citrusTestPath())) {
                violations.add(
                        "Citrus contract path must be exactly test/" + route.routeId() + ".camel.it.yaml");
            }
        }
        if (!policy.citrusTestsRequired() || !policy.oneCitrusTestPerRoute()) {
            violations.add("Ship protocol v1 requires Citrus YAML coverage for every route");
        }
        requirePolicyValue(ledger, "dsl", policy.routeDsl(), violations);
        requirePolicyValue(ledger, "expression-language", policy.expressionLanguage(), violations);
        requirePolicyValue(ledger, "citrus-version", policy.citrusVersion(), violations);
        requirePolicyCsvValues(ledger, "citrus-dependencies", policy.citrusDependencies(), violations);
        if (policy.javaPolicy() != null) {
            requirePolicyValue(ledger, "java-policy",
                    policy.javaPolicy().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'), violations);
        }
    }

    private static String validateCatalogPrerequisitePolicy(
            DecisionLedger ledger, List<String> violations) {
        RequirementsPolicy policy = ledger.requirementsPolicy();
        if (policy == null) {
            violations.add("Catalog access requires an exact runtime and version policy");
            return "";
        }

        String runtime = normalize(policy.runtime());
        boolean supportedRuntime = Set.of("main", "spring-boot", "quarkus").contains(runtime);
        if (!supportedRuntime) {
            violations.add("Implementation policy runtime must be main, spring-boot, or quarkus");
        }
        boolean validCamelVersion = !isBlank(policy.camelVersion())
                && VERSION.matcher(policy.camelVersion()).matches();
        if (!validCamelVersion) {
            violations.add("Implementation policy requires an explicit Camel version");
        }
        boolean requiresPlatform = "spring-boot".equals(runtime) || "quarkus".equals(runtime);
        boolean validPlatformVersion = !requiresPlatform
                || (!isBlank(policy.platformVersion()) && VERSION.matcher(policy.platformVersion()).matches());
        if (!validPlatformVersion) {
            violations.add("Spring Boot and Quarkus policies require an explicit platform version");
        }
        boolean validSpringBootVersion = !"spring-boot".equals(runtime)
                || (!isBlank(policy.springBootVersion()) && VERSION.matcher(policy.springBootVersion()).matches());
        if (!validSpringBootVersion) {
            violations.add("Spring Boot policies require an explicit Spring Boot version");
        }
        if (!"spring-boot".equals(runtime) && !isBlank(policy.springBootVersion())) {
            violations.add("Spring Boot version is valid only for the spring-boot runtime");
        }

        if (supportedRuntime && !"main".equals(runtime)) {
            violations.add("Ship protocol v1 has direct startup evidence only for the Camel Main runtime");
        } else if ("main".equals(runtime) && validCamelVersion) {
            try {
                JvmPayloadRequest.yamlValidator(policy.camelVersion());
                JvmPayloadRequest.camelMain(policy.camelVersion());
            } catch (IllegalArgumentException e) {
                violations.add("Ship protocol v1 has no direct YAML/startup evidence payload for Camel "
                               + policy.camelVersion());
            }
        }

        if (supportedRuntime) {
            requireCatalogCategoryResolved(ledger, "runtime", violations);
            requirePolicyValue(ledger, "runtime", runtime, violations);
        }
        if (validCamelVersion) {
            requireCatalogCategoryResolved(ledger, "camel-version", violations);
            requirePolicyValue(ledger, "camel-version", policy.camelVersion(), violations);
        }
        if (requiresPlatform && validPlatformVersion) {
            requireCatalogCategoryResolved(ledger, "platform-version", violations);
            requirePolicyValue(ledger, "platform-version", policy.platformVersion(), violations);
        }
        if ("spring-boot".equals(runtime) && validSpringBootVersion) {
            requireCatalogCategoryResolved(ledger, "spring-boot-version", violations);
            requirePolicyValue(ledger, "spring-boot-version", policy.springBootVersion(), violations);
        }
        return runtime;
    }

    private record CategoryStatus(String category, LedgerStatus status) {
    }

    private static void requireCatalogCategoryResolved(
            DecisionLedger ledger, String category, List<String> violations) {
        List<Category> projections = ledger.completeness().stream()
                .filter(candidate -> candidate != null && category.equals(candidate.id()))
                .toList();
        if (projections.size() != 1 || projections.get(0).status() != LedgerStatus.RESOLVED) {
            violations.add("Catalog prerequisite category must be resolved: " + category);
        }
        boolean unresolvedItem = ledger.facts().stream()
                .anyMatch(entry -> entry != null
                        && category.equals(entry.category()) && isOpen(entry.status()))
                || ledger.decisions().stream()
                        .anyMatch(entry -> entry != null
                                && category.equals(entry.category()) && isOpen(entry.status()))
                || ledger.conflicts().stream()
                        .anyMatch(entry -> entry != null
                                && category.equals(entry.category()) && isOpen(entry.status()))
                || ledger.assumptions().stream()
                        .anyMatch(entry -> entry != null
                                && category.equals(entry.category()) && isOpen(entry.status()));
        if (unresolvedItem) {
            violations.add("Catalog prerequisite contains an unresolved ledger item: " + category);
        }
    }

    private static void requirePolicyValue(
            DecisionLedger ledger, String category, String expected, List<String> violations) {
        Set<String> values = java.util.stream.Stream.concat(ledger.facts().stream(), ledger.decisions().stream())
                .filter(entry -> category.equals(entry.category()) && entry.status() == LedgerStatus.RESOLVED)
                .map(entry -> normalize(entry.value()))
                .collect(java.util.stream.Collectors.toSet());
        if (!values.equals(Set.of(normalize(expected)))) {
            violations.add("Implementation policy " + category + " does not match the resolved ledger value");
        }
    }

    private static void requirePolicyCsvValues(
            DecisionLedger ledger, String category, List<String> expected, List<String> violations) {
        List<String> projections = java.util.stream.Stream.concat(
                ledger.facts().stream(), ledger.decisions().stream())
                .filter(entry -> category.equals(entry.category()) && entry.status() == LedgerStatus.RESOLVED)
                .map(Entry::value)
                .toList();
        List<String> actual = projections.size() == 1 && projections.get(0) != null
                ? java.util.Arrays.stream(projections.get(0).split(",", -1))
                        .map(LedgerValidator::normalize)
                        .toList()
                : List.of();
        List<String> normalizedExpected = expected.stream().map(LedgerValidator::normalize).toList();
        if (!actual.equals(normalizedExpected)) {
            violations.add("Implementation policy " + category + " does not match the resolved ledger values");
        }
    }

    private static void validateSourceRefs(String owner, List<SourceRef> references, List<String> violations) {
        for (SourceRef reference : references) {
            validateSourceRef(owner, reference, violations);
        }
    }

    private static void validateSourceRef(String owner, SourceRef reference, List<String> violations) {
        if (reference == null || isBlank(reference.sourceId()) || isBlank(reference.locator())
                || reference.digest() == null || !reference.digest().matches("sha256:[0-9a-f]{64}")
                || isBlank(reference.excerpt())
                || reference.excerpt().length() > MAX_SOURCE_EXCERPT_CHARS
                || reference.excerpt().indexOf('\0') >= 0) {
            violations.add(owner + " contains an invalid source reference");
        }
    }

    private static boolean safeRelative(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            java.nio.file.Path path = java.nio.file.Path.of(value);
            return value.indexOf('\\') < 0
                    && !path.isAbsolute()
                    && path.equals(path.normalize())
                    && !path.startsWith("..");
        } catch (java.nio.file.InvalidPathException e) {
            return false;
        }
    }

    private static boolean safeFileSegment(String value) {
        return value != null && ROUTE_ID.matcher(value).matches();
    }

    private static boolean hasFileName(String value, String expected) {
        try {
            java.nio.file.Path name = java.nio.file.Path.of(value).getFileName();
            return name != null && expected.equals(name.toString());
        } catch (java.nio.file.InvalidPathException e) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void throwIfAny(List<String> violations) {
        if (!violations.isEmpty()) {
            throw new LedgerValidationException(violations);
        }
    }
}

package io.github.luigidemasi.camelkit.ship.ledger;

import java.util.ArrayList;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Assumption;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Category;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Claim;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Conflict;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Entry;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.GapReviewStatus;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.JavaPolicy;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Question;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RequirementsPolicy;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RouteContract;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.SourceRef;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LedgerValidatorTest {

    private static final String CITRUS_VERSION = "5.0.0-M2";
    private static final List<String> CITRUS_DEPENDENCIES = List.of(
            "org.citrusframework:citrus-camel:5.0.0-M2",
            "org.citrusframework:citrus-junit-jupiter:5.0.0-M2",
            "org.citrusframework:citrus-yaml:5.0.0-M2");

    @Test
    void nullLedgerFailsClosedWithValidationError() {
        LedgerValidationException analysis = assertThrows(
                LedgerValidationException.class, () -> LedgerValidator.validateAnalysis(null));
        LedgerValidationException ready = assertThrows(
                LedgerValidationException.class, () -> LedgerValidator.validateReady(null));

        assertTrue(analysis.getMessage().contains("Decision ledger is required"));
        assertTrue(ready.getMessage().contains("Decision ledger is required"));
    }

    @Test
    void acceptsCompleteLedgerOnlyAfterGapReview() {
        assertDoesNotThrow(() -> LedgerValidator.validateReady(readyLedger()));
    }

    @Test
    void citrusPolicyMustMatchItsResolvedVersionAndDependencyDecisions() {
        DecisionLedger ready = readyLedger();

        LedgerValidationException version = assertThrows(
                LedgerValidationException.class,
                () -> LedgerValidator.validateReady(withDecisionValue(
                        ready, "citrus-version", "5.0.0-M1")));
        assertTrue(version.getMessage().contains(
                "Implementation policy citrus-version does not match the resolved ledger value"));

        LedgerValidationException dependencies = assertThrows(
                LedgerValidationException.class,
                () -> LedgerValidator.validateReady(withDecisionValue(
                        ready, "citrus-dependencies", CITRUS_DEPENDENCIES.get(0))));
        assertTrue(dependencies.getMessage().contains(
                "Implementation policy citrus-dependencies does not match the resolved ledger values"));
    }

    @Test
    void rejectsMissingRequiredCategory() {
        DecisionLedger ready = readyLedger();
        DecisionLedger incomplete = copy(ready, ready.completeness().stream()
                .filter(category -> !"runtime".equals(category.id()))
                .toList(), ready.decisions(), List.of(), List.of(), GapReviewStatus.PASSED);

        LedgerValidationException error = assertThrows(
                LedgerValidationException.class, () -> LedgerValidator.validateReady(incomplete));

        assertTrue(error.getMessage().contains("Missing completeness category: runtime"));
    }

    @Test
    void rejectsSilentRecommendationAsResolvedDecision() {
        DecisionLedger ready = readyLedger();
        List<Entry> decisions = new ArrayList<>(
                ready.decisions().stream()
                        .filter(entry -> !"runtime".equals(entry.category()))
                        .toList());
        decisions.add(new Entry(
                "decision-runtime", "runtime", null, LedgerStatus.NEEDS_USER_DECISION,
                List.of(), "Camel Main is recommended"));
        DecisionLedger incomplete = copy(
                ready, ready.completeness(), decisions, List.of(), List.of("decision-runtime"), GapReviewStatus.PASSED);

        LedgerValidationException error = assertThrows(
                LedgerValidationException.class, () -> LedgerValidator.validateReady(incomplete));

        assertTrue(error.getMessage().contains("Blocking ledger items remain"));
        assertTrue(error.getMessage().contains("Open or undecided ledger items remain"));
    }

    @Test
    void conflictsAndAssumptionsCannotSelfAttestResolution() {
        DecisionLedger ready = readyLedger();
        SourceRef source = source();
        DecisionLedger invalid = new DecisionLedger(
                ready.schemaVersion(), ready.revision(), ready.facts(), ready.decisions(),
                List.of(new Conflict(
                        "conflict-runtime", "runtime",
                        List.of(new Claim("main", source), new Claim("spring-boot", source)),
                        LedgerStatus.RESOLVED, "worker chose one claim")),
                List.of(new Assumption(
                        "assumption-runtime", "runtime", "main", LedgerStatus.RESOLVED,
                        List.of(source), "worker accepted its recommendation")),
                ready.catalogEvidence(), ready.openQuestions(), ready.completeness(),
                ready.blockingOpenItemIds(), ready.gapReviewStatus(), ready.requirementsPolicy());

        LedgerValidationException error = assertThrows(
                LedgerValidationException.class, () -> LedgerValidator.validateAnalysis(invalid));

        assertTrue(error.getMessage().contains(
                "conflict conflict-runtime must remain open until a controller-recorded answer replaces it"));
        assertTrue(error.getMessage().contains(
                "assumption assumption-runtime must remain open until a controller-recorded answer replaces it"));
    }

    @Test
    void acceptsExactlyOneQuestionForAnOpenItem() {
        Entry decision = new Entry(
                "decision-runtime", "runtime", null, LedgerStatus.NEEDS_USER_DECISION,
                List.of(), "Camel Main is recommended");
        Question question = new Question(
                "question-runtime", decision.id(), "Which runtime should host the integration?",
                List.of("main", "spring-boot", "quarkus"), "main", LedgerStatus.OPEN);
        DecisionLedger ledger = new DecisionLedger(
                1, 1, List.of(), List.of(decision), List.of(), List.of(), List.of(), List.of(question),
                categories(LedgerStatus.OPEN), List.of(decision.id()), GapReviewStatus.NOT_RUN, null);

        assertDoesNotThrow(() -> LedgerValidator.validateAnalysis(ledger));
    }

    @Test
    void rejectsBatchedQuestions() {
        Entry runtime = new Entry(
                "decision-runtime", "runtime", null, LedgerStatus.OPEN, List.of(), null);
        Entry dsl = new Entry("decision-dsl", "dsl", null, LedgerStatus.OPEN, List.of(), null);
        DecisionLedger ledger = new DecisionLedger(
                1, 1, List.of(), List.of(runtime, dsl), List.of(), List.of(), List.of(),
                List.of(
                        new Question("q-runtime", runtime.id(), "Runtime?", List.of(), null, LedgerStatus.OPEN),
                        new Question("q-dsl", dsl.id(), "DSL?", List.of(), null, LedgerStatus.OPEN)),
                categories(LedgerStatus.OPEN), List.of(runtime.id(), dsl.id()), GapReviewStatus.NOT_RUN, null);

        LedgerValidationException error = assertThrows(
                LedgerValidationException.class, () -> LedgerValidator.validateAnalysis(ledger));

        assertTrue(error.getMessage().contains("At most one blocking discovery question"));
    }

    @Test
    void rejectsQuestionForAlreadyResolvedItem() {
        Entry runtime = new Entry(
                "decision-runtime", "runtime", "main", LedgerStatus.RESOLVED, List.of(), null);
        Question question = new Question(
                "q-runtime", runtime.id(), "Runtime?", List.of(), null, LedgerStatus.OPEN);
        DecisionLedger ledger = new DecisionLedger(
                1, 1, List.of(), List.of(runtime), List.of(), List.of(), List.of(), List.of(question),
                categories(LedgerStatus.OPEN), List.of(), GapReviewStatus.NOT_RUN, null);

        LedgerValidationException error = assertThrows(
                LedgerValidationException.class, () -> LedgerValidator.validateAnalysis(ledger));

        assertTrue(error.getMessage().contains("does not reference an open ledger item"));
    }

    @Test
    void resolvedSingleValuedChoiceCannotBeReaskedThroughAFreshItemId() {
        Entry resolved = new Entry(
                "decision-runtime", "runtime", "main", LedgerStatus.RESOLVED,
                List.of(source()), null);
        Assumption fresh = new Assumption(
                "runtime-choice-again", "runtime", "spring-boot", LedgerStatus.NEEDS_USER_DECISION,
                List.of(source()), "worker attempted to reopen a settled choice");
        Question question = new Question(
                "question-runtime-again", fresh.id(), "Which runtime?",
                List.of("main", "spring-boot"), "main", LedgerStatus.OPEN);
        DecisionLedger ledger = new DecisionLedger(
                1, 2, List.of(), List.of(resolved), List.of(), List.of(fresh), List.of(), List.of(question),
                List.of(new Category("runtime", LedgerStatus.RESOLVED, null, List.of(source()))),
                List.of(fresh.id()), GapReviewStatus.NOT_RUN, null);

        LedgerValidationException error = assertThrows(
                LedgerValidationException.class, () -> LedgerValidator.validateAnalysis(ledger));

        assertTrue(error.getMessage().contains(
                "Resolved single-valued category cannot be reopened with another item: runtime"));
    }

    @Test
    void notApplicableCategoryRequiresRationale() {
        DecisionLedger ready = readyLedger();
        List<Category> categories = ready.completeness().stream()
                .map(category -> "deployment".equals(category.id())
                        ? new Category(category.id(), LedgerStatus.NOT_APPLICABLE, null, List.of())
                        : category)
                .toList();

        LedgerValidationException error = assertThrows(
                LedgerValidationException.class,
                () -> LedgerValidator.validateReady(copy(
                        ready, categories, ready.decisions(), List.of(), List.of(), GapReviewStatus.PASSED)));

        assertTrue(error.getMessage().contains("Not-applicable category requires a rationale: deployment"));
    }

    @Test
    void requiresRouteAndCitrusBasenamesToMatchTheRouteIdExactly() {
        DecisionLedger ready = readyLedger();
        RequirementsPolicy policy = new RequirementsPolicy(
                "main", "4.21.0", null, null, "yaml", "simple",
                CITRUS_VERSION, CITRUS_DEPENDENCIES, JavaPolicy.FORBIDDEN,
                List.of(), List.of(new RouteContract(
                        "orders", "src/main/resources/routes/order-route.camel.yaml",
                        "test/order-test.camel.it.yaml")),
                true, true);
        DecisionLedger invalid = withPolicy(ready, policy);

        LedgerValidationException error = assertThrows(
                LedgerValidationException.class, () -> LedgerValidator.validateReady(invalid));

        assertTrue(error.getMessage().contains("Route contract path must be named exactly orders.camel.yaml"));
        assertTrue(error.getMessage().contains(
                "Citrus contract path must be exactly test/orders.camel.it.yaml"));
    }

    @Test
    void rejectsRouteIdThatIsNotASafeFileSegment() {
        DecisionLedger ready = readyLedger();
        RequirementsPolicy policy = new RequirementsPolicy(
                "main", "4.21.0", null, null, "yaml", "simple",
                CITRUS_VERSION, CITRUS_DEPENDENCIES, JavaPolicy.FORBIDDEN,
                List.of(), List.of(new RouteContract(
                        "orders/hidden", "src/main/resources/routes/orders/hidden.camel.yaml",
                        "test/orders/hidden.camel.it.yaml")),
                true, true);

        LedgerValidationException error = assertThrows(
                LedgerValidationException.class,
                () -> LedgerValidator.validateReady(withPolicy(ready, policy)));

        assertTrue(error.getMessage().contains("invalid or duplicate route ID"));
    }

    @Test
    void rejectsRouteIdThatCannotFormTheReservedTestEndpoint() {
        DecisionLedger ready = readyLedger();
        RequirementsPolicy policy = new RequirementsPolicy(
                "main", "4.21.0", null, null, "yaml", "simple",
                CITRUS_VERSION, CITRUS_DEPENDENCIES, JavaPolicy.FORBIDDEN,
                List.of(), List.of(new RouteContract(
                        "orders route", "src/main/resources/routes/orders route.camel.yaml",
                        "test/orders route.camel.it.yaml")),
                true, true);

        LedgerValidationException error = assertThrows(
                LedgerValidationException.class,
                () -> LedgerValidator.validateReady(withPolicy(ready, policy)));

        assertTrue(error.getMessage().contains("invalid or duplicate route ID"));
    }

    @Test
    void springBootPolicyRequiresExplicitFrameworkVersion() {
        DecisionLedger ready = readyLedger();
        RequirementsPolicy policy = new RequirementsPolicy(
                "spring-boot", "4.21.0", "4.21.0", null, "yaml", "simple",
                CITRUS_VERSION, CITRUS_DEPENDENCIES, JavaPolicy.FORBIDDEN,
                List.of(), List.of(new RouteContract(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        "test/orders.camel.it.yaml")),
                true, true);

        LedgerValidationException error = assertThrows(
                LedgerValidationException.class,
                () -> LedgerValidator.validateReady(withPolicy(ready, policy)));

        assertTrue(error.getMessage().contains("Spring Boot policies require an explicit Spring Boot version"));
    }

    @Test
    void nonSpringPolicyRejectsSpringBootFrameworkVersion() {
        DecisionLedger ready = readyLedger();
        RequirementsPolicy policy = new RequirementsPolicy(
                "main", "4.21.0", null, "4.1.0", "yaml", "simple",
                CITRUS_VERSION, CITRUS_DEPENDENCIES, JavaPolicy.FORBIDDEN,
                List.of(), List.of(new RouteContract(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        "test/orders.camel.it.yaml")),
                true, true);

        LedgerValidationException error = assertThrows(
                LedgerValidationException.class,
                () -> LedgerValidator.validateReady(withPolicy(ready, policy)));

        assertTrue(error.getMessage().contains("Spring Boot version is valid only for the spring-boot runtime"));
    }

    @Test
    void catalogGateAcceptsOnlyResolvedRuntimeSpecificVersionPolicy() {
        assertDoesNotThrow(() -> LedgerValidator.validateCatalogPrerequisites(
                catalogLedger("main", "4.21.0", null, null)));

        LedgerValidationException missingSpringVersions = assertThrows(
                LedgerValidationException.class,
                () -> LedgerValidator.validateCatalogPrerequisites(
                        catalogLedger("spring-boot", "4.21.0", null, null)));
        assertTrue(missingSpringVersions.getMessage().contains("explicit platform version"));
        assertTrue(missingSpringVersions.getMessage().contains("explicit Spring Boot version"));

        LedgerValidationException spring = assertThrows(
                LedgerValidationException.class,
                () -> LedgerValidator.validateCatalogPrerequisites(
                        catalogLedger("spring-boot", "4.21.0", "4.21.0", "3.5.0")));
        assertTrue(spring.getMessage().contains("direct startup evidence only for the Camel Main runtime"));

        LedgerValidationException quarkus = assertThrows(
                LedgerValidationException.class,
                () -> LedgerValidator.validateCatalogPrerequisites(
                        catalogLedger("quarkus", "4.21.0", "3.23.0", null)));
        assertTrue(quarkus.getMessage().contains("direct startup evidence only for the Camel Main runtime"));

        DecisionLedger bound = catalogLedger("spring-boot", "4.21.0", "4.21.0", "3.5.0");
        RequirementsPolicy mismatched = new RequirementsPolicy(
                "spring-boot", "4.21.0", "3.23.0", "3.5.0",
                null, null, null, List.of(), null, List.of(), List.of(), false, false);
        LedgerValidationException mismatch = assertThrows(
                LedgerValidationException.class,
                () -> LedgerValidator.validateCatalogPrerequisites(withPolicy(bound, mismatched)));
        assertTrue(mismatch.getMessage().contains(
                "platform-version does not match the resolved ledger value"));
    }

    @Test
    void candidateReadyRejectsEveryRuntimeTupleOutsideTheExactEvidenceMatrix() {
        for (DecisionLedger unsupported : List.of(
                readyLedger("main", "4.14.7", null, null),
                readyLedger("spring-boot", "4.21.0", "4.21.0", "4.1.0"),
                readyLedger("quarkus", "4.18.3", "3.33.1", null))) {
            LedgerValidationException error = assertThrows(
                    LedgerValidationException.class,
                    () -> LedgerValidator.validateCandidateReady(unsupported));

            assertTrue(error.getMessage().contains("direct startup evidence")
                    || error.getMessage().contains("direct YAML/startup evidence payload"),
                    error::getMessage);
        }
    }

    @Test
    void candidateReadyRejectsEveryExpressionLanguageOtherThanSimple() {
        DecisionLedger ready = readyLedger();
        for (String language : List.of("constant", "csimple", "jsonpath", "xpath")) {
            RequirementsPolicy baseline = ready.requirementsPolicy();
            RequirementsPolicy policy = new RequirementsPolicy(
                    baseline.runtime(), baseline.camelVersion(), baseline.platformVersion(),
                    baseline.springBootVersion(), baseline.routeDsl(), language,
                    baseline.citrusVersion(), baseline.citrusDependencies(), baseline.javaPolicy(),
                    baseline.approvedJavaExceptions(), baseline.routes(), baseline.citrusTestsRequired(),
                    baseline.oneCitrusTestPerRoute());

            LedgerValidationException error = assertThrows(
                    LedgerValidationException.class,
                    () -> LedgerValidator.validateCandidateReady(withPolicy(ready, policy)), language);

            assertTrue(error.getMessage().contains(
                    "Ship protocol v1 supports only the Simple expression language"), error::getMessage);
        }
    }

    @Test
    void rejectsBlankOrOversizedExactSourceExcerpts() {
        for (String excerpt : List.of(" ", "x".repeat(LedgerValidator.MAX_SOURCE_EXCERPT_CHARS + 1))) {
            SourceRef invalidSource = new SourceRef(
                    "source-test", "test", "sha256:" + "a".repeat(64), excerpt);
            Entry decision = new Entry(
                    "decision-runtime", "runtime", "main", LedgerStatus.RESOLVED,
                    List.of(invalidSource), null);
            DecisionLedger ledger = new DecisionLedger(
                    1, 1, List.of(), List.of(decision), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), GapReviewStatus.NOT_RUN, null);

            LedgerValidationException error = assertThrows(
                    LedgerValidationException.class, () -> LedgerValidator.validateAnalysis(ledger));

            assertTrue(error.getMessage().contains("contains an invalid source reference"));
        }
    }

    @Test
    void camelMainRejectsJavaPoliciesWithoutCompilationEvidence() {
        DecisionLedger ready = readyLedger();
        for (JavaPolicy javaPolicy : List.of(JavaPolicy.ALLOWED, JavaPolicy.JUSTIFICATION_REQUIRED)) {
            RequirementsPolicy policy = new RequirementsPolicy(
                    "main", "4.21.0", null, null, "yaml", "simple",
                    CITRUS_VERSION, CITRUS_DEPENDENCIES, javaPolicy,
                    List.of(), List.of(new RouteContract(
                            "orders", "src/main/resources/routes/orders.camel.yaml",
                            "test/orders.camel.it.yaml")),
                    true, true);
            String decisionValue = javaPolicy.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            List<Entry> decisions = ready.decisions().stream()
                    .map(entry -> "java-policy".equals(entry.category())
                            ? new Entry(
                                    entry.id(), entry.category(), decisionValue, entry.status(),
                                    entry.sourceRefs(), entry.rationale())
                            : entry)
                    .toList();
            DecisionLedger invalid = new DecisionLedger(
                    ready.schemaVersion(), ready.revision(), ready.facts(), decisions, ready.conflicts(),
                    ready.assumptions(), ready.catalogEvidence(), ready.openQuestions(), ready.completeness(),
                    ready.blockingOpenItemIds(), ready.gapReviewStatus(), policy);

            LedgerValidationException error = assertThrows(
                    LedgerValidationException.class, () -> LedgerValidator.validateReady(invalid));

            assertTrue(error.getMessage().contains(
                    "requires Java policy FORBIDDEN for Camel Main because its mandatory startup evidence does not "
                                                   + "compile Java sources"),
                    javaPolicy::name);
        }
    }

    private static DecisionLedger readyLedger() {
        return new DecisionLedger(
                1, 4, List.of(), resolvedDecisions(), List.of(), List.of(), List.of(), List.of(),
                categories(LedgerStatus.RESOLVED), List.of(), GapReviewStatus.PASSED, policy());
    }

    private static DecisionLedger readyLedger(
            String runtime, String camelVersion, String platformVersion, String springBootVersion) {
        DecisionLedger baseline = readyLedger();
        List<Entry> decisions = new ArrayList<>(
                baseline.decisions().stream()
                        .map(entry -> switch (entry.category()) {
                            case "runtime" -> new Entry(
                                    entry.id(), entry.category(), runtime, entry.status(), entry.sourceRefs(),
                                    entry.rationale());
                            case "camel-version" -> new Entry(
                                    entry.id(), entry.category(), camelVersion, entry.status(), entry.sourceRefs(),
                                    entry.rationale());
                            default -> entry;
                        })
                        .toList());
        List<Category> completeness = new ArrayList<>(baseline.completeness());
        if (platformVersion != null) {
            addCatalogDecision(decisions, completeness, source(), "platform-version", platformVersion);
        }
        if (springBootVersion != null) {
            addCatalogDecision(decisions, completeness, source(), "spring-boot-version", springBootVersion);
        }
        RequirementsPolicy policy = new RequirementsPolicy(
                runtime, camelVersion, platformVersion, springBootVersion, "yaml", "simple",
                CITRUS_VERSION, CITRUS_DEPENDENCIES, JavaPolicy.FORBIDDEN,
                List.of(), List.of(new RouteContract(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        "test/orders.camel.it.yaml")),
                true, true);
        return new DecisionLedger(
                baseline.schemaVersion(), baseline.revision(), baseline.facts(), decisions,
                baseline.conflicts(), baseline.assumptions(), baseline.catalogEvidence(),
                baseline.openQuestions(), completeness, baseline.blockingOpenItemIds(),
                GapReviewStatus.NOT_RUN, policy);
    }

    private static DecisionLedger catalogLedger(
            String runtime, String camelVersion, String platformVersion, String springBootVersion) {
        SourceRef source = new SourceRef(
                "source-catalog", "test", "sha256:" + "b".repeat(64),
                "main spring-boot quarkus 4.21.0 3.23.0 3.5.0");
        List<Entry> decisions = new ArrayList<>();
        List<Category> completeness = new ArrayList<>();
        addCatalogDecision(decisions, completeness, source, "runtime", runtime);
        addCatalogDecision(decisions, completeness, source, "camel-version", camelVersion);
        if (platformVersion != null) {
            addCatalogDecision(decisions, completeness, source, "platform-version", platformVersion);
        }
        if (springBootVersion != null) {
            addCatalogDecision(decisions, completeness, source, "spring-boot-version", springBootVersion);
        }
        RequirementsPolicy policy = new RequirementsPolicy(
                runtime, camelVersion, platformVersion, springBootVersion,
                null, null, null, List.of(), null, List.of(), List.of(), false, false);
        return new DecisionLedger(
                1, 1, List.of(), decisions, List.of(), List.of(), List.of(), List.of(), completeness,
                List.of(), GapReviewStatus.NOT_RUN, policy);
    }

    private static void addCatalogDecision(
            List<Entry> decisions,
            List<Category> completeness,
            SourceRef source,
            String category,
            String value) {
        decisions.add(new Entry(
                "decision-" + category, category, value, LedgerStatus.RESOLVED,
                List.of(source), null));
        completeness.add(new Category(category, LedgerStatus.RESOLVED, null, List.of(source)));
    }

    private static List<Category> categories(LedgerStatus status) {
        return LedgerValidator.REQUIRED_CATEGORIES.stream()
                .sorted()
                .map(category -> new Category(
                        category, status, status == LedgerStatus.NOT_APPLICABLE ? "N/A" : null,
                        List.of()))
                .toList();
    }

    private static DecisionLedger copy(
            DecisionLedger source,
            List<Category> categories,
            List<Entry> decisions,
            List<Question> questions,
            List<String> blockers,
            GapReviewStatus gapReviewStatus) {
        return new DecisionLedger(
                source.schemaVersion(), source.revision(), source.facts(), decisions, source.conflicts(),
                source.assumptions(), source.catalogEvidence(), questions, categories, blockers, gapReviewStatus,
                source.requirementsPolicy());
    }

    private static DecisionLedger withPolicy(DecisionLedger source, RequirementsPolicy policy) {
        return new DecisionLedger(
                source.schemaVersion(), source.revision(), source.facts(), source.decisions(), source.conflicts(),
                source.assumptions(), source.catalogEvidence(), source.openQuestions(), source.completeness(),
                source.blockingOpenItemIds(), source.gapReviewStatus(), policy);
    }

    private static DecisionLedger withDecisionValue(
            DecisionLedger source, String category, String value) {
        List<Entry> decisions = source.decisions().stream()
                .map(entry -> category.equals(entry.category())
                        ? new Entry(
                                entry.id(), entry.category(), value, entry.status(), entry.sourceRefs(),
                                entry.rationale())
                        : entry)
                .toList();
        return new DecisionLedger(
                source.schemaVersion(), source.revision(), source.facts(), decisions, source.conflicts(),
                source.assumptions(), source.catalogEvidence(), source.openQuestions(), source.completeness(),
                source.blockingOpenItemIds(), source.gapReviewStatus(), source.requirementsPolicy());
    }

    private static List<Entry> resolvedDecisions() {
        return LedgerValidator.REQUIRED_CATEGORIES.stream().sorted()
                .map(category -> new Entry(
                        "decision-" + category, category, value(category), LedgerStatus.RESOLVED,
                        List.of(source()), null))
                .toList();
    }

    private static String value(String category) {
        return switch (category) {
            case "runtime" -> "main";
            case "camel-version" -> "4.21.0";
            case "dsl" -> "yaml";
            case "expression-language" -> "simple";
            case "java-policy" -> "forbidden";
            case "citrus-version" -> CITRUS_VERSION;
            case "citrus-dependencies" -> String.join(", ", CITRUS_DEPENDENCIES);
            default -> "resolved";
        };
    }

    private static RequirementsPolicy policy() {
        return new RequirementsPolicy(
                "main", "4.21.0", null, null, "yaml", "simple",
                CITRUS_VERSION, CITRUS_DEPENDENCIES, JavaPolicy.FORBIDDEN,
                List.of(), List.of(new RouteContract(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        "test/orders.camel.it.yaml")),
                true, true);
    }

    private static SourceRef source() {
        return new SourceRef(
                "source-test", "test", "sha256:" + "a".repeat(64),
                "main 4.21.0 yaml simple forbidden resolved");
    }
}

package io.github.luigidemasi.camelkit.ship.ledger;

import java.util.List;

/**
 * Revisioned requirements and decision ledger proposed by a bounded discovery worker.
 *
 * <p>
 * The controller validates this record before persisting it. A worker cannot mark the run ready merely by setting
 * {@link Completeness#gapReviewStatus()} to {@link GapReviewStatus#PASSED}.
 */
public record DecisionLedger(
        int schemaVersion,
        long revision,
        List<Entry> facts,
        List<Entry> decisions,
        List<Conflict> conflicts,
        List<Assumption> assumptions,
        List<CatalogEvidence> catalogEvidence,
        List<Question> openQuestions,
        List<Category> completeness,
        List<String> blockingOpenItemIds,
        GapReviewStatus gapReviewStatus,
        RequirementsPolicy requirementsPolicy) {

    public static final int SCHEMA_VERSION = 1;

    public DecisionLedger {
        facts = immutable(facts);
        decisions = immutable(decisions);
        conflicts = immutable(conflicts);
        assumptions = immutable(assumptions);
        catalogEvidence = immutable(catalogEvidence);
        openQuestions = immutable(openQuestions);
        completeness = immutable(completeness);
        blockingOpenItemIds = immutable(blockingOpenItemIds);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /** Exact evidence excerpt bound to one controller-owned initial-context or interaction source. */
    public record SourceRef(String sourceId, String locator, String digest, String excerpt) {
    }

    public record Entry(
            String id,
            String category,
            String value,
            LedgerStatus status,
            List<SourceRef> sourceRefs,
            String rationale) {

        public Entry {
            sourceRefs = immutable(sourceRefs);
        }
    }

    public record Claim(String value, SourceRef sourceRef) {
    }

    public record Conflict(
            String id,
            String category,
            List<Claim> claims,
            LedgerStatus status,
            String rationale) {

        public Conflict {
            claims = immutable(claims);
        }
    }

    public record Assumption(
            String id,
            String category,
            String proposedValue,
            LedgerStatus status,
            List<SourceRef> sourceRefs,
            String rationale) {

        public Assumption {
            sourceRefs = immutable(sourceRefs);
        }
    }

    /** Raw catalog observation accepted only when bound to an exact controller-issued discovery capability. */
    public record CatalogEvidence(
            String id,
            long prerequisiteLedgerRevision,
            String requestAttemptId,
            String requestPolicyDigest,
            CatalogKind kind,
            String tool,
            String subject,
            String runtime,
            String camelVersion,
            String platformVersion,
            String springBootVersion,
            String platformBom,
            boolean verified,
            String response,
            String responseDigest) {
    }

    public record Question(
            String id,
            String openItemId,
            String prompt,
            List<String> options,
            String recommendation,
            LedgerStatus status) {

        public Question {
            options = immutable(options);
        }
    }

    public record Category(String id, LedgerStatus status, String rationale, List<SourceRef> sourceRefs) {

        public Category {
            sourceRefs = immutable(sourceRefs);
        }
    }

    /** Exact implementation policy derived before design approval and enforced on execution output. */
    public record RequirementsPolicy(
            String runtime,
            String camelVersion,
            String platformVersion,
            String springBootVersion,
            String routeDsl,
            String expressionLanguage,
            String citrusVersion,
            List<String> citrusDependencies,
            JavaPolicy javaPolicy,
            List<String> approvedJavaExceptions,
            List<RouteContract> routes,
            boolean citrusTestsRequired,
            boolean oneCitrusTestPerRoute) {

        public RequirementsPolicy {
            citrusDependencies = immutable(citrusDependencies);
            approvedJavaExceptions = immutable(approvedJavaExceptions);
            routes = immutable(routes);
        }
    }

    public record RouteContract(String routeId, String routePath, String citrusTestPath) {
    }

    public enum JavaPolicy {
        FORBIDDEN,
        JUSTIFICATION_REQUIRED,
        ALLOWED
    }

    public enum CatalogKind {
        COMPONENT,
        EIP,
        DATAFORMAT,
        LANGUAGE
    }

    public enum GapReviewStatus {
        NOT_RUN,
        FAILED,
        PASSED
    }
}

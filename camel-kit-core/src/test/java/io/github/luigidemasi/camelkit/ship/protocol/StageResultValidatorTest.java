package io.github.luigidemasi.camelkit.ship.protocol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject.Kind;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Category;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.GapReviewStatus;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.JavaPolicy;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RequirementsPolicy;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RouteContract;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.SourceRef;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerStatus;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidator;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.Operation;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.RepositoryAccess;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class StageResultValidatorTest {

    private static final String CITRUS_VERSION = "5.0.0-M2";
    private static final List<String> CITRUS_DEPENDENCIES = List.of(
            "org.citrusframework:citrus-camel:5.0.0-M2",
            "org.citrusframework:citrus-junit-jupiter:5.0.0-M2",
            "org.citrusframework:citrus-yaml:5.0.0-M2");
    private static final List<CatalogSubject> CATALOG_REQUESTS = List.of(new CatalogSubject(Kind.COMPONENT, "kafka"));

    @TempDir
    Path output;

    @Test
    void acceptsCompletedDiscoveryOnlyWithCompleteUnreviewedCandidateLedger() {
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult result = result(request, StageResult.Outcome.COMPLETED, readyLedger(), null, List.of());

        assertDoesNotThrow(() -> StageResultValidator.validate(request, result, output));
    }

    @Test
    void rejectsCrossRunStaleAndFabricatedResultIdentity() {
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult result = new StageResult(
                1, "another-run", request.stage(), "old-attempt", "fabricated", request.inputDigest(),
                StageResult.Outcome.COMPLETED, readyLedger(), null, List.of(), List.of(), null, null, null);

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("run ID"));
        assertTrue(error.getMessage().contains("attempt ID"));
        assertTrue(error.getMessage().contains("challenge"));
    }

    @Test
    void completedDiscoveryCannotReturnImplementationArtifacts() throws Exception {
        ProducedArtifact artifact = artifact("route", "early.camel.yaml", "route");
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult result = result(
                request, StageResult.Outcome.COMPLETED, readyLedger(), null, List.of(artifact));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("may not return implementation artifacts"));
    }

    @Test
    void discoveryMayReturnATypedCatalogContinuation() {
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult result = result(
                request, StageResult.Outcome.NEEDS_DISCOVERY, readyLedger(), null, List.of());

        assertDoesNotThrow(() -> StageResultValidator.validate(request, result, output));
    }

    @Test
    void catalogRequestsAreImmutableCopies() {
        StageRequest request = request(ShipStage.DISCOVERY);
        List<CatalogSubject> mutable = new ArrayList<>(CATALOG_REQUESTS);
        StageResult result = result(
                request, StageResult.Outcome.NEEDS_DISCOVERY, readyLedger(), null, mutable, List.of());

        mutable.clear();

        assertEquals(CATALOG_REQUESTS, result.catalogRequests());
        assertThrows(UnsupportedOperationException.class, () -> result.catalogRequests().clear());
    }

    @Test
    void discoveryContinuationRequiresCatalogRequests() {
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult result = result(
                request, StageResult.Outcome.NEEDS_DISCOVERY, readyLedger(), null, List.of(), List.of());

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("requires at least one catalog request"));
    }

    @Test
    void nonCatalogDiscoveryReopenCannotCarryCatalogRequests() {
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = result(
                request, StageResult.Outcome.NEEDS_DISCOVERY, readyLedger(), null,
                CATALOG_REQUESTS, List.of());

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("Only discovery may return catalog requests"));
    }

    @Test
    void designMayRequestNonCatalogRediscoveryWithoutCatalogRequests() {
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = result(
                request, StageResult.Outcome.NEEDS_DISCOVERY, readyLedger(), null, List.of(), List.of());

        assertDoesNotThrow(() -> StageResultValidator.validate(request, result, output));
    }

    @Test
    void discoveryContinuationCannotSmuggleArtifacts() throws Exception {
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult result = result(
                request, StageResult.Outcome.NEEDS_DISCOVERY, readyLedger(), null,
                List.of(artifact("route", "early.camel.yaml", "route")));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("may not return implementation artifacts"));
    }

    @Test
    void designArtifactMustUseAPortableRelativePath() throws Exception {
        StageRequest request = request(ShipStage.DESIGN);
        ProducedArtifact escaped = new ProducedArtifact("design", "../design.md", "sha256:" + "0".repeat(64), 1);
        StageResult result = result(request, StageResult.Outcome.COMPLETED, null, null, List.of(escaped));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("portable relative path"));
    }

    @Test
    void forgedArtifactDigestIsRejected() throws Exception {
        Files.writeString(output.resolve("design.md"), "candidate");
        ProducedArtifact forged = new ProducedArtifact(
                "design", "design.md", "sha256:" + "0".repeat(64), "candidate".length());
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = result(request, StageResult.Outcome.COMPLETED, null, null, List.of(forged));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("digest mismatch"));
    }

    @Test
    void rejectsNonPortableMalformedAndOversizedArtifactDeclarations() {
        StageRequest request = request(ShipStage.DESIGN);
        List<ProducedArtifact> artifacts = List.of(
                new ProducedArtifact("Design", "..\\outside", "not-a-digest", -1),
                new ProducedArtifact(
                        "design", "design.md", "sha256:" + "0".repeat(64),
                        ShipTreePolicy.current().maxFileBytes() + 1));
        StageResult result = result(request, StageResult.Outcome.COMPLETED, null, null, artifacts);

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("canonical kind"));
        assertTrue(error.getMessage().contains("per-file limit"));
    }

    @Test
    void validDesignArtifactIsAccepted() throws Exception {
        ProducedArtifact artifact = artifact("design", "design.md", "candidate");
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = result(request, StageResult.Outcome.COMPLETED, null, null, List.of(artifact));

        assertDoesNotThrow(() -> StageResultValidator.validate(request, result, output));
    }

    @Test
    void completedDesignCannotSmuggleAdditionalArtifactKinds() throws Exception {
        ProducedArtifact design = artifact("design", "design.md", "candidate");
        ProducedArtifact route = artifact("route", "orders.camel.yaml", "route");
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = result(
                request, StageResult.Outcome.COMPLETED, null, null, List.of(design, route));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("exactly one design artifact"));
    }

    @Test
    void failedResultCannotReturnArtifacts() throws Exception {
        ProducedArtifact artifact = artifact("plan", "plan.md", "candidate");
        StageRequest request = request(ShipStage.PLAN);
        StageResult result = new StageResult(
                1, request.runId(), request.stage(), request.attemptId(), request.challenge(), request.inputDigest(),
                StageResult.Outcome.FAILED, null, null, List.of(), List.of(artifact), null,
                "worker-failed", "worker failed");

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("may not return implementation artifacts"));
    }

    @Test
    void needsUserInputCannotReturnArtifacts() throws Exception {
        ProducedArtifact artifact = artifact("design", "design.md", "candidate");
        StageRequest request = request(ShipStage.DESIGN);
        DecisionLedger.Question question = new DecisionLedger.Question(
                "question-runtime", "decision-runtime", "Which runtime?", List.of("main"), "main",
                LedgerStatus.NEEDS_USER_DECISION);
        StageResult result = result(
                request, StageResult.Outcome.NEEDS_USER_INPUT, readyLedger(), question, List.of(artifact));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("may not return implementation artifacts"));
    }

    @Test
    void designCannotAskTheUserDirectly() {
        StageRequest request = request(ShipStage.DESIGN);
        DecisionLedger.Question question = new DecisionLedger.Question(
                "question-runtime", "decision-runtime", "Which runtime?", List.of("main"), "main",
                LedgerStatus.NEEDS_USER_DECISION);
        StageResult result = result(
                request, StageResult.Outcome.NEEDS_USER_INPUT, readyLedger(), question, List.of());

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("Only discovery may request user input"));
    }

    @Test
    void failedResultRequiresTypedFailure() {
        StageRequest request = request(ShipStage.PLAN);
        StageResult result = result(request, StageResult.Outcome.FAILED, null, null, List.of());

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validate(request, result, output));

        assertTrue(error.getMessage().contains("failure code and message"));
    }

    private StageRequest request(ShipStage stage) {
        StageCapability capability = new StageCapability(
                RepositoryAccess.READ_ONLY, List.of(output.toString()), List.of(),
                List.of(Operation.READ, Operation.RETURN_STRUCTURED_RESULT), false, false);
        return new StageRequest(
                1, "ship-run", stage, "attempt-1", 1, "idempotency", "challenge",
                "sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64),
                null, null, null,
                "context.json", "ledger.json", null, null, null, null, null, null, List.of(), null, null,
                "contract.md", output.toString(), capability);
    }

    private StageResult result(
            StageRequest request,
            StageResult.Outcome outcome,
            DecisionLedger ledger,
            DecisionLedger.Question question,
            List<ProducedArtifact> artifacts) {
        List<CatalogSubject> catalogRequests = outcome == StageResult.Outcome.NEEDS_DISCOVERY
                ? CATALOG_REQUESTS
                : List.of();
        return result(request, outcome, ledger, question, catalogRequests, artifacts);
    }

    private StageResult result(
            StageRequest request,
            StageResult.Outcome outcome,
            DecisionLedger ledger,
            DecisionLedger.Question question,
            List<CatalogSubject> catalogRequests,
            List<ProducedArtifact> artifacts) {
        return new StageResult(
                1, request.runId(), request.stage(), request.attemptId(), request.challenge(), request.inputDigest(),
                outcome, ledger, question, catalogRequests, artifacts, null, null, null);
    }

    private ProducedArtifact artifact(String kind, String name, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(output.resolve(name), bytes);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return new ProducedArtifact(
                kind, name, "sha256:" + java.util.HexFormat.of().formatHex(digest.digest(bytes)), bytes.length);
    }

    private DecisionLedger readyLedger() {
        SourceRef source = new SourceRef(
                "source-test", "test", "sha256:" + "a".repeat(64),
                "main 4.21.0 yaml simple forbidden resolved");
        List<Category> categories = LedgerValidator.REQUIRED_CATEGORIES.stream()
                .sorted()
                .map(category -> new Category(category, LedgerStatus.RESOLVED, null, List.of()))
                .toList();
        List<DecisionLedger.Entry> decisions = LedgerValidator.REQUIRED_CATEGORIES.stream().sorted()
                .map(category -> new DecisionLedger.Entry(
                        "decision-" + category, category, value(category), LedgerStatus.RESOLVED,
                        List.of(source), null))
                .toList();
        RequirementsPolicy policy = new RequirementsPolicy(
                "main", "4.21.0", null, null, "yaml", "simple",
                CITRUS_VERSION, CITRUS_DEPENDENCIES, JavaPolicy.FORBIDDEN,
                List.of(), List.of(new RouteContract(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        "test/orders.camel.it.yaml")),
                true, true);
        return new DecisionLedger(
                1, 1, List.of(), decisions, List.of(), List.of(), List.of(), List.of(),
                categories, List.of(), GapReviewStatus.NOT_RUN, policy);
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
}

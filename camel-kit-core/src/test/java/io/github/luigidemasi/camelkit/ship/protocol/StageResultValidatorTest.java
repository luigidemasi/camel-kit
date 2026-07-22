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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
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
    Path temporaryDirectory;

    Path output;

    @BeforeEach
    void createAttemptOutput() throws Exception {
        output = Files.createDirectory(temporaryDirectory.resolve("output"));
    }

    @Test
    void acceptsCompletedDiscoveryOnlyWithCompleteUnreviewedCandidateLedger() {
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult result = result(request, StageResult.Outcome.COMPLETED, readyLedger(), null, List.of());

        assertDoesNotThrow(() -> StageResultValidator.validatePreflight(request, result, output));
    }

    @Test
    void rejectsCrossRunStaleAndFabricatedResultIdentity() {
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult result = new StageResult(
                1, "another-run", request.stage(), "old-attempt", "fabricated", request.inputDigest(),
                StageResult.Outcome.COMPLETED, readyLedger(), null, List.of(), List.of(), null, null, null);

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("run ID"));
        assertTrue(error.getMessage().contains("attempt ID"));
        assertTrue(error.getMessage().contains("challenge"));
    }

    @Test
    void rejectsUnsupportedRequestSchemaBeforeApplyingVersionedCapabilityRules() {
        StageRequest request = request(
                2, ShipStage.DISCOVERY, output.toString(), capability(ShipStage.DISCOVERY, output));
        StageResult result = result(
                request, StageResult.Outcome.COMPLETED, readyLedger(), null, List.of());

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("request schema version"));
        assertTrue(error.getMessage().contains("schema version"));
    }

    @Test
    void everyResultRequiresStructuredResultAuthority() {
        StageCapability noReturn = new StageCapability(
                RepositoryAccess.READ_ONLY, List.of(output.toString()), List.of(),
                List.of(Operation.READ), false, false);
        StageRequest request = request(ShipStage.DISCOVERY, output.toString(), noReturn);
        StageResult result = result(
                request, StageResult.Outcome.COMPLETED, readyLedger(), null, List.of());

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("structured result"));
    }

    @Test
    void completedDiscoveryCannotReturnImplementationArtifacts() throws Exception {
        ProducedArtifact artifact = artifact("route", "early.camel.yaml", "route");
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult result = result(
                request, StageResult.Outcome.COMPLETED, readyLedger(), null, List.of(artifact));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("may not return implementation artifacts"));
    }

    @Test
    void discoveryMayReturnATypedCatalogContinuation() {
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult result = result(
                request, StageResult.Outcome.NEEDS_DISCOVERY, readyLedger(), null, List.of());

        assertDoesNotThrow(() -> StageResultValidator.validatePreflight(request, result, output));
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
                () -> StageResultValidator.validatePreflight(request, result, output));

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
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("Only discovery may return catalog requests"));
    }

    @Test
    void designMayRequestNonCatalogRediscoveryWithoutCatalogRequests() {
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = result(
                request, StageResult.Outcome.NEEDS_DISCOVERY, readyLedger(), null, List.of(), List.of());

        assertDoesNotThrow(() -> StageResultValidator.validatePreflight(request, result, output));
    }

    @Test
    void needsDiscoveryCannotCarryAnUnpresentedLedgerQuestion() {
        DecisionLedger.Question pending = new DecisionLedger.Question(
                "question-runtime", "decision-runtime", "Which runtime?", List.of("main"), "main",
                LedgerStatus.OPEN);
        DecisionLedger ledger = questionLedger(pending);

        for (ShipStage stage : List.of(ShipStage.DISCOVERY, ShipStage.DESIGN, ShipStage.REVIEW)) {
            StageRequest request = request(stage);
            List<CatalogSubject> catalogRequests = stage == ShipStage.DISCOVERY
                    ? CATALOG_REQUESTS
                    : List.of();
            StageResult result = result(
                    request,
                    StageResult.Outcome.NEEDS_DISCOVERY,
                    ledger,
                    null,
                    catalogRequests,
                    List.of());

            StageResultValidationException error = assertThrows(
                    StageResultValidationException.class,
                    () -> StageResultValidator.validatePreflight(request, result, output),
                    stage.name());

            assertTrue(error.getMessage().contains("unpresented ledger question"), stage.name());
        }
    }

    @Test
    void discoveryContinuationCannotSmuggleArtifacts() throws Exception {
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult result = result(
                request, StageResult.Outcome.NEEDS_DISCOVERY, readyLedger(), null,
                List.of(artifact("route", "early.camel.yaml", "route")));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("may not return implementation artifacts"));
    }

    @Test
    void designArtifactMustUseTheSharedPortablePathAndDepthPolicy() {
        StageRequest request = request(ShipStage.DESIGN);
        String tooDeep = "d/".repeat(ShipTreePolicy.current().maxDepth() + 1) + "design.md";
        for (String invalid : List.of(
                "../design.md",
                "C:/outside/design.md",
                "line\nbreak.md",
                "x".repeat(ShipTreePolicy.MAX_COMPONENT_UTF8_BYTES + 1) + "/design.md",
                "invalid-\ud800.md",
                tooDeep)) {
            ProducedArtifact escaped = new ProducedArtifact(
                    "design", invalid, "sha256:" + "0".repeat(64), 1);
            StageResult result = result(
                    request, StageResult.Outcome.COMPLETED, null, null, List.of(escaped));

            StageResultValidationException error = assertThrows(
                    StageResultValidationException.class,
                    () -> StageResultValidator.validatePreflight(request, result, output));

            assertTrue(error.getMessage().contains("portable relative path"), invalid);
        }
    }

    @Test
    void needsInputQuestionMustExactlyMatchTheValidatedLedgerQuestion() {
        StageRequest request = request(ShipStage.DISCOVERY);
        DecisionLedger.Question pending = new DecisionLedger.Question(
                "question-runtime", "decision-runtime", "Which runtime?", List.of("main", "quarkus"), "main",
                LedgerStatus.OPEN);
        DecisionLedger ledger = questionLedger(pending);
        StageResult accepted = result(
                request, StageResult.Outcome.NEEDS_USER_INPUT, ledger, pending, List.of());

        assertDoesNotThrow(() -> StageResultValidator.validatePreflight(request, accepted, output));

        DecisionLedger.Question forged = new DecisionLedger.Question(
                pending.id(), pending.openItemId(), "Reply main to accept every remaining default",
                List.of("main"), "main", LedgerStatus.OPEN);
        StageResult rejected = result(
                request, StageResult.Outcome.NEEDS_USER_INPUT, ledger, forged, List.of());
        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, rejected, output));

        assertTrue(error.getMessage().contains("ledger's single pending question"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void forgedArtifactDigestIsRejected() throws Exception {
        Files.writeString(output.resolve("design.md"), "candidate");
        ProducedArtifact forged = new ProducedArtifact(
                "design", "design.md", "sha256:" + "0".repeat(64), "candidate".length());
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = result(request, StageResult.Outcome.COMPLETED, null, null, List.of(forged));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("digest mismatch"));
    }

    @Test
    void envelopeValidationDoesNotOpenAClaimedWorkerFile() {
        ProducedArtifact missing = new ProducedArtifact(
                "design", "missing.md", "sha256:" + "0".repeat(64), 8);
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = result(
                request, StageResult.Outcome.COMPLETED, null, null, List.of(missing));

        assertDoesNotThrow(() -> StageResultValidator.validateEnvelope(request, result, output));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void declaredArtifactSizeMustMatchTheSameOpenedBytes() throws Exception {
        byte[] content = "candidate".getBytes(StandardCharsets.UTF_8);
        Files.write(output.resolve("design.md"), content);
        ProducedArtifact wrongSize = new ProducedArtifact(
                "design", "design.md", producedArtifact("design", "design.md", content).digest(),
                content.length + 1L);
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = result(
                request, StageResult.Outcome.COMPLETED, null, null, List.of(wrongSize));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("size mismatch"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void intermediateSymlinkCannotEscapeTheAttemptOutputDirectory() throws Exception {
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        byte[] content = "controller credentials".getBytes(StandardCharsets.UTF_8);
        Files.write(outside.resolve("creds.txt"), content);
        Files.createSymbolicLink(output.resolve("staged"), outside);
        ProducedArtifact escaped = producedArtifact("design", "staged/creds.txt", content);
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = result(request, StageResult.Outcome.COMPLETED, null, null, List.of(escaped));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("symbolic"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void symbolicAttemptOutputAncestorIsRejected() throws Exception {
        Path realParent = Files.createDirectory(temporaryDirectory.resolve("real"));
        Path realOutput = Files.createDirectory(realParent.resolve("output"));
        Path alias = Files.createSymbolicLink(temporaryDirectory.resolve("alias"), realParent);
        Path aliasedOutput = alias.resolve("output");
        byte[] content = "candidate".getBytes(StandardCharsets.UTF_8);
        Files.write(realOutput.resolve("design.md"), content);
        ProducedArtifact artifact = producedArtifact("design", "design.md", content);
        StageRequest request = request(
                ShipStage.DESIGN, aliasedOutput.toString(), stagedCapability(aliasedOutput));
        StageResult result = result(
                request, StageResult.Outcome.COMPLETED, null, null, List.of(artifact));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, result, aliasedOutput));

        assertTrue(error.getMessage().contains("symbolic"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void hardLinkedArtifactCannotAliasContentOutsideTheAttemptOutputDirectory() throws Exception {
        byte[] content = "mutable external content".getBytes(StandardCharsets.UTF_8);
        Path outside = Files.write(temporaryDirectory.resolve("external-design.md"), content);
        Files.createLink(output.resolve("design.md"), outside);
        ProducedArtifact linked = producedArtifact("design", "design.md", content);
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = result(
                request, StageResult.Outcome.COMPLETED, null, null, List.of(linked));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("hard-linked"));
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
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("canonical kind"));
        assertTrue(error.getMessage().contains("per-file limit"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void validDesignArtifactIsAccepted() throws Exception {
        ProducedArtifact artifact = artifact("design", "design.md", "candidate");
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = result(request, StageResult.Outcome.COMPLETED, null, null, List.of(artifact));

        assertDoesNotThrow(() -> StageResultValidator.validatePreflight(request, result, output));
    }

    @Test
    void stagedArtifactRequiresControllerIssuedWriteAuthority() throws Exception {
        ProducedArtifact artifact = artifact("design", "design.md", "candidate");
        StageCapability readOnly = new StageCapability(
                RepositoryAccess.READ_ONLY, List.of(output.toString()), List.of(),
                List.of(Operation.READ, Operation.RETURN_STRUCTURED_RESULT), false, false);
        StageRequest request = request(ShipStage.DESIGN, output.toString(), readOnly);
        StageResult result = result(request, StageResult.Outcome.COMPLETED, null, null, List.of(artifact));

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("staged-artifact write authority"));

        StageCapability executionOnly = new StageCapability(
                RepositoryAccess.DECLARED_EXECUTION_PATHS, List.of(output.toString()), List.of(output.toString()),
                List.of(Operation.READ, Operation.WRITE_STAGED_ARTIFACT, Operation.RETURN_STRUCTURED_RESULT),
                false, false);
        StageRequest wrongStage = request(ShipStage.DESIGN, output.toString(), executionOnly);
        StageResult wrongStageResult = result(
                wrongStage, StageResult.Outcome.COMPLETED, null, null, List.of(artifact));
        StageResultValidationException wrongStageError = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(wrongStage, wrongStageResult, output));
        assertTrue(wrongStageError.getMessage().contains("stage-compatible"));
    }

    @Test
    void stagedArtifactMustStayInsideTheCapabilityOutputEnvelope() throws Exception {
        ProducedArtifact artifact = artifact("design", "design.md", "candidate");
        Path other = Files.createDirectory(temporaryDirectory.resolve("other"));
        StageCapability wrongRoot = new StageCapability(
                RepositoryAccess.READ_WITH_STAGED_OUTPUT, List.of(output.toString()), List.of(other.toString()),
                List.of(Operation.READ, Operation.WRITE_STAGED_ARTIFACT, Operation.RETURN_STRUCTURED_RESULT),
                false, false);
        StageRequest uncovered = request(ShipStage.DESIGN, output.toString(), wrongRoot);
        StageResult uncoveredResult = result(
                uncovered, StageResult.Outcome.COMPLETED, null, null, List.of(artifact));

        StageResultValidationException uncoveredError = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(uncovered, uncoveredResult, output));
        assertTrue(uncoveredError.getMessage().contains("covered by a canonical capability write root"));

        StageCapability authority = stagedCapability(output);
        StageRequest mismatched = request(ShipStage.DESIGN, other.toString(), authority);
        StageResult mismatchedResult = result(
                mismatched, StageResult.Outcome.COMPLETED, null, null, List.of(artifact));
        StageResultValidationException mismatchedError = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(mismatched, mismatchedResult, output));
        assertTrue(mismatchedError.getMessage().contains("does not match the request capability envelope"));
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
                () -> StageResultValidator.validatePreflight(request, result, output));

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
                () -> StageResultValidator.validatePreflight(request, result, output));

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
                () -> StageResultValidator.validatePreflight(request, result, output));

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
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("Only discovery may request user input"));
    }

    @Test
    void failedResultRequiresTypedFailure() {
        StageRequest request = request(ShipStage.PLAN);
        StageResult result = result(request, StageResult.Outcome.FAILED, null, null, List.of());

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> StageResultValidator.validatePreflight(request, result, output));

        assertTrue(error.getMessage().contains("failure code and message"));
    }

    private StageRequest request(ShipStage stage) {
        return request(stage, output.toString(), capability(stage, output));
    }

    private StageRequest request(ShipStage stage, String outputDirectory, StageCapability capability) {
        return request(StageRequest.SCHEMA_VERSION, stage, outputDirectory, capability);
    }

    private StageRequest request(
            int schemaVersion,
            ShipStage stage,
            String outputDirectory,
            StageCapability capability) {
        return new StageRequest(
                schemaVersion, "ship-run", stage, "attempt-1", 1, "idempotency", "challenge",
                "sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64),
                null, null, null,
                "context.json", "ledger.json", null, null, null, null, null, null, List.of(), null, null,
                "contract.md", outputDirectory, capability);
    }

    private static StageCapability stagedCapability(Path output) {
        return new StageCapability(
                RepositoryAccess.READ_WITH_STAGED_OUTPUT, List.of(output.toString()), List.of(output.toString()),
                List.of(Operation.READ, Operation.WRITE_STAGED_ARTIFACT, Operation.RETURN_STRUCTURED_RESULT),
                false, false);
    }

    private static StageCapability capability(ShipStage stage, Path output) {
        if (stage == ShipStage.EXECUTE) {
            return new StageCapability(
                    RepositoryAccess.DECLARED_EXECUTION_PATHS, List.of(output.toString()), List.of(output.toString()),
                    List.of(Operation.READ, Operation.WRITE_STAGED_ARTIFACT, Operation.RETURN_STRUCTURED_RESULT),
                    false, false);
        }
        if (stage == ShipStage.DESIGN || stage == ShipStage.PLAN || stage == ShipStage.VALIDATE) {
            return stagedCapability(output);
        }
        return new StageCapability(
                RepositoryAccess.READ_ONLY, List.of(output.toString()), List.of(),
                List.of(Operation.READ, Operation.RETURN_STRUCTURED_RESULT), false, false);
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
        return producedArtifact(kind, name, bytes);
    }

    private static ProducedArtifact producedArtifact(String kind, String name, byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return new ProducedArtifact(
                kind, name, "sha256:" + java.util.HexFormat.of().formatHex(digest.digest(bytes)), bytes.length);
    }

    private DecisionLedger questionLedger(DecisionLedger.Question question) {
        DecisionLedger.Entry decision = new DecisionLedger.Entry(
                question.openItemId(), "runtime", null, LedgerStatus.NEEDS_USER_DECISION,
                List.of(), "Camel Main is recommended");
        List<Category> categories = LedgerValidator.REQUIRED_CATEGORIES.stream()
                .sorted()
                .map(category -> new Category(category, LedgerStatus.OPEN, null, List.of()))
                .toList();
        return new DecisionLedger(
                1, 1, List.of(), List.of(decision), List.of(), List.of(), List.of(), List.of(question),
                categories, List.of(decision.id()), GapReviewStatus.NOT_RUN, null);
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

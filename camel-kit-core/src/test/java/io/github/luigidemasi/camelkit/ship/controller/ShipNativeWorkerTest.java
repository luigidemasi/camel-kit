package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.JavaPolicy;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifestReader;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactPolicy;
import io.github.luigidemasi.camelkit.ship.artifact.CitrusDependencyPolicy;
import io.github.luigidemasi.camelkit.ship.context.ShipContext;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.ExecutionMode;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Oversight;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.RunStatus;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage;
import io.github.luigidemasi.camelkit.ship.controller.ShipStageWorker.ProposedFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipNativeWorkerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path directory;
    Path project;
    Path state;
    MutableClock clock;
    ShipController controller;
    ShipCoordinator coordinator;
    DistributionConfig distribution;

    @BeforeEach
    void setUp() throws Exception {
        project = Files.createDirectory(directory.resolve("project"));
        state = directory.resolve("state");
        clock = new MutableClock();
        distribution = DistributionConfig.loadBundled();
        controller = new ShipController(state, clock, Map.of());
        coordinator = new ShipCoordinator(
                state, controller,
                new ShipNativeWorker(controller, Duration.ofMinutes(5), Map.of(), clock, executionMode()),
                target -> {
                    throw new IOException("This fixture stops before catalog validation");
                },
                new ShipMainValidator(), distribution, Map.of(), false, clock);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void nativeProposalsReachPublicationOnlyWhenDeterministicChecksPass(boolean invalidRoute) throws Exception {
        var snapshot = io.github.luigidemasi.camelkit.ship.catalog.CatalogTestVerifier.mainSnapshot(
                Files.createDirectory(directory.resolve("catalog-fixture")));
        coordinator = new ShipCoordinator(
                state, controller,
                new ShipNativeWorker(controller, Duration.ofMinutes(5), Map.of(), clock, executionMode()),
                target -> snapshot,
                new ShipMainValidator(new ShipCoordinatorTest.DeterministicEvidenceStub()),
                distribution, Map.of(), false, clock);
        Path proposed = Files.createDirectory(directory.resolve("proposed"));
        ShipCoordinatorTest.writeGeneratedMainCandidate(proposed, policy());
        if (invalidRoute) {
            Files.writeString(proposed.resolve("orders.camel.yaml"), "not a valid Camel route");
        }
        ShipRun run = start(Oversight.NEVER);
        for (int index = 0; index < 4; index++) {
            ShipNativeWorker.Task task = controller.pendingTask(run);
            Path responseFile = receipt(task);
            ObjectNode envelope = (ObjectNode) JSON.readTree(Files.readString(responseFile));
            envelope.putNull("hostVersion");
            if (task.stage() == Stage.EXECUTE) {
                assertFalse(task.prompt().contains("application.properties"), task.prompt());
                var proposals = ((ObjectNode) envelope.path("response")).putArray("files");
                for (String path : List.of("orders.camel.yaml", "test/orders.camel.it.yaml",
                        ".camel-kit/config.properties", "pom.xml")) {
                    proposals.addObject().put("path", path).put("content", Files.readString(proposed.resolve(path)));
                }
            }
            Files.writeString(responseFile, JSON.writeValueAsString(envelope));
            run = coordinator.submit(run.id(), responseFile);
        }
        assertEquals(invalidRoute ? RunStatus.FAILED : RunStatus.COMPLETED, run.status(), run.message());
        assertEquals(!invalidRoute, Files.exists(project.resolve("orders.camel.yaml")));
        if (!invalidRoute) {
            assertNotNull(run.publication());
            var stampPath = Path.of(run.stage(Stage.VALIDATE).artifacts().get(0).path());
            var stamp = JSON.readTree(Files.readString(stampPath));
            assertEquals(executionMode().host(), stamp.path("toolVersions").get(0).path("tool").asText());
            assertEquals("UNTESTED", stamp.path("toolVersions").get(0).path("support").asText());
            org.junit.jupiter.api.Assertions.assertTrue(stamp.path("toolVersions").get(0).path("version").isNull());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = Stage.class, names = {"DISCOVERY", "DESIGN", "PLAN"})
    void rejectsFileProposalsOutsideExecuteWithoutAcceptingTheReceipt(Stage stage) throws Exception {
        ShipRun run = start(Oversight.NEVER);
        while (run.currentStage() != stage) {
            run = coordinator.submit(run.id(), receipt(controller.pendingTask(run)));
        }
        ShipRun unchanged = run;
        ShipNativeWorker.Task task = controller.pendingTask(run);
        Path input = receipt(task);
        ObjectNode envelope = (ObjectNode) JSON.readTree(Files.readString(input));
        ((ObjectNode) envelope.path("response")).putArray("files")
                .addObject().put("path", "orders.camel.yaml").put("content", "Unexpected source write");
        Files.writeString(input, JSON.writeValueAsString(envelope));

        assertThrows(IOException.class, () -> coordinator.submit(unchanged.id(), input));
        assertEquals(unchanged, controller.status(run.id()));
        assertFalse(Files.exists(evidence(task).resolve("native-result.json")));
        assertFalse(Files.exists(project.resolve("orders.camel.yaml")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, ShipNativeWorker.MAX_BYTES + 1})
    void distinguishesEmptyAndOversizedReceiptInputs(int size) throws Exception {
        ShipRun run = start(Oversight.NEVER);
        Path input = Files.write(directory.resolve("invalid-receipt.json"), new byte[size]);
        IOException failure = assertThrows(IOException.class, () -> coordinator.submit(run.id(), input));
        assertEquals(size == 0 ? "Native handoff input is empty" : "Native handoff input exceeds its size limit",
                failure.getMessage());
        assertEquals(run, controller.status(run.id()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"prompt", "deadline", "null", "unbound"})
    void refusesAlteredOrUnboundTasksAndRetriesInAFreshAttempt(String mutation) throws Exception {
        ShipRun run = start(Oversight.NEVER);
        ShipNativeWorker.Task task = controller.pendingTask(run);
        Path taskFile = evidence(task).resolve("native-task.json");
        ObjectNode altered = (ObjectNode) JSON.readTree(Files.readString(taskFile));
        if (mutation.equals("unbound")) {
            Path stateFile = state.resolve(run.id()).resolve("state.json");
            ObjectNode stateJson = (ObjectNode) JSON.readTree(Files.readString(stateFile));
            ((ObjectNode) stateJson.path("stages").get(0)).putNull("nativeEvidence");
            Files.writeString(stateFile, JSON.writeValueAsString(stateJson));
        } else if (mutation.equals("null")) {
            Files.writeString(taskFile, "null");
        } else {
            altered.put(mutation, mutation.equals("prompt") ? "Changed work" : "2027-01-01T00:00:00Z");
            Files.writeString(taskFile, JSON.writeValueAsString(altered));
        }
        assertThrows(IOException.class, () -> controller.pendingTask(controller.status(run.id())));
        ShipRun failed = coordinator.resume(run.id(), List.of());
        assertEquals(RunStatus.FAILED, failed.status());
        org.junit.jupiter.api.Assertions.assertTrue(failed.message().contains("Durable Native stage result"));
        ShipRun retry = coordinator.resume(run.id(), List.of());
        assertEquals(task.attempt() + 1, controller.pendingTask(retry).attempt());
        assertNotEquals(task.taskId(), controller.pendingTask(retry).taskId());
    }

    @Test
    void retriesAnInterruptedReceiptWriteOnlyWhileTheTaskIsEligible() throws Exception {
        ShipRun run = start(Oversight.NEVER);
        ShipNativeWorker.Task task = controller.pendingTask(run);
        Path input = receipt(task);
        Files.write(evidence(task).resolve("native-result.json"),
                JSON.writeValueAsBytes(JSON.readValue(Files.readString(input), ShipNativeWorker.Receipt.class)));
        assertEquals(Stage.DESIGN, coordinator.submit(run.id(), input).currentStage());

        ShipRun current = controller.status(run.id());
        ShipNativeWorker.Task next = controller.pendingTask(current);
        Path late = receipt(next);
        Files.write(evidence(next).resolve("native-result.json"),
                JSON.writeValueAsBytes(JSON.readValue(Files.readString(late), ShipNativeWorker.Receipt.class)));
        clock.now = clock.now.plus(Duration.ofMinutes(6));
        assertThrows(IOException.class, () -> coordinator.submit(run.id(), late));
        assertEquals(current, controller.status(run.id()));
    }

    @Test
    void restartsAnAlteredCompletedReceiptBeforeUsingItsOutput() throws Exception {
        ShipRun run = start(Oversight.NEVER);
        ShipNativeWorker.Task task = controller.pendingTask(run);
        run = coordinator.submit(run.id(), receipt(task));
        Files.writeString(evidence(task).resolve("native-result.json"), "null");
        ShipRun restarted = coordinator.resume(run.id(), List.of());
        assertEquals(Stage.DISCOVERY, restarted.currentStage());
        assertEquals(task.attempt() + 1, controller.pendingTask(restarted).attempt());
    }

    @Test
    void restartsExecuteIfAcceptedHostMetadataChangesWhilePaused() throws Exception {
        Path proposed = Files.createDirectory(directory.resolve("proposed"));
        ShipCoordinatorTest.writeGeneratedMainCandidate(proposed, policy());
        ShipRun run = start(Oversight.SMART);
        for (int index = 0; index < 3; index++) {
            run = coordinator.submit(run.id(), receipt(controller.pendingTask(run)));
        }
        run = coordinator.resume(run.id(), List.of());
        ShipNativeWorker.Task task = controller.pendingTask(run);
        Path input = receipt(task);
        ObjectNode envelope = (ObjectNode) JSON.readTree(Files.readString(input));
        ((ObjectNode) envelope.path("response").path("result")).put("materialAmbiguity", true);
        ((ObjectNode) envelope.path("response").path("result")).putArray("unansweredQuestions")
                .addObject().put("question", "Confirm orders behavior").putNull("defaultApplied");
        var files = ((ObjectNode) envelope.path("response")).putArray("files");
        for (String path : List.of("orders.camel.yaml", "test/orders.camel.it.yaml",
                ".camel-kit/config.properties", "pom.xml")) {
            files.addObject().put("path", path).put("content", Files.readString(proposed.resolve(path)));
        }
        Files.writeString(input, JSON.writeValueAsString(envelope));
        run = coordinator.submit(run.id(), input);
        assertEquals(RunStatus.PAUSED, run.status());
        assertEquals(Stage.VALIDATE, run.currentStage());
        assertNotNull(run.stage(Stage.EXECUTE).nativeEvidence().resultDigest());
        envelope.put("hostVersion", "altered-host-version");
        Files.writeString(evidence(task).resolve("native-result.json"), JSON.writeValueAsString(envelope));
        ShipRun restarted = coordinator.resume(run.id(), List.of());
        assertEquals(Stage.EXECUTE, restarted.currentStage());
        assertEquals(task.attempt() + 1, controller.pendingTask(restarted).attempt());
        assertFalse(Files.exists(project.resolve("orders.camel.yaml")));
    }

    private Path evidence(ShipNativeWorker.Task task) {
        return state.resolve(task.runId()).resolve("evidence")
                .resolve(task.stage().name().toLowerCase(java.util.Locale.ROOT) + "-" + task.attempt());
    }

    @Test
    void relaysStagesAndKeepsSmartOversightInTheController() throws Exception {
        ShipRun run = start(Oversight.SMART);
        ShipNativeWorker.Task discovery = controller.pendingTask(run);
        assertNotNull(discovery);
        assertEquals(executionMode(), run.executionMode());
        assertEquals(discovery, controller.pendingTask(coordinator.resume(run.id(), List.of())));

        Path first = receipt(discovery);
        run = coordinator.submit(run.id(), first);
        assertEquals(Stage.DESIGN, run.currentStage());
        ShipNativeWorker.Task design = controller.pendingTask(run);
        assertNotEquals(discovery.taskId(), design.taskId());
        assertEquals(run, coordinator.submit(run.id(), first));

        run = coordinator.submit(run.id(), receipt(design));
        assertEquals(Stage.PLAN, run.currentStage());
        run = coordinator.submit(run.id(), receipt(controller.pendingTask(run)));
        assertEquals(RunStatus.PAUSED, run.status());
        assertNull(controller.pendingTask(run));
        assertFalse(Files.exists(project.resolve("orders.camel.yaml")));
        run = coordinator.resume(run.id(), List.of());
        assertEquals(Stage.EXECUTE, run.currentStage());
        assertEquals(executionMode(), run.executionMode());
        assertEquals("camel-ship-worker", controller.pendingTask(run).preset());
        assertFalse(controller.pendingTask(run).forkContext());
    }

    @Test
    void rejectsConflictingAndCrossAttemptResultsWithoutAdvancing() throws Exception {
        ShipRun run = start(Oversight.NEVER);
        ShipNativeWorker.Task task = controller.pendingTask(run);
        Path accepted = receipt(task);
        ShipRun current = coordinator.submit(run.id(), accepted);
        ObjectNode conflict = (ObjectNode) JSON.readTree(Files.readString(accepted));
        ((ObjectNode) conflict.path("response").path("result")).put("report", "Different result");
        Files.writeString(accepted, JSON.writeValueAsString(conflict));
        assertThrows(IOException.class, () -> coordinator.submit(run.id(), accepted));
        assertEquals(current, controller.status(run.id()));

        Path next = receipt(controller.pendingTask(current));
        ObjectNode stale = (ObjectNode) JSON.readTree(Files.readString(next));
        stale.put("attempt", 99);
        Files.writeString(next, JSON.writeValueAsString(stale));
        assertThrows(IOException.class, () -> coordinator.submit(run.id(), next));
        assertEquals(current, controller.status(run.id()));
    }

    @Test
    void expiresPendingWorkAndRejectsOrphanResultsAfterRetryOrAbort() throws Exception {
        ShipRun run = start(Oversight.NEVER);
        ShipNativeWorker.Task old = controller.pendingTask(run);
        Path late = receipt(old);
        clock.now = clock.now.plus(Duration.ofMinutes(6));
        assertThrows(IOException.class, () -> coordinator.submit(run.id(), late));
        ShipRun failed = coordinator.resume(run.id(), List.of());
        assertEquals(RunStatus.FAILED, failed.status());
        ShipRun retry = coordinator.resume(run.id(), List.of());
        assertEquals(old.attempt() + 1, controller.pendingTask(retry).attempt());
        assertNotEquals(old.taskId(), controller.pendingTask(retry).taskId());
        assertThrows(IOException.class, () -> coordinator.submit(run.id(), late));

        Path afterAbort = receipt(controller.pendingTask(retry));
        controller.abort(run.id());
        assertNull(controller.pendingTask(retry));
        assertThrows(IOException.class, () -> coordinator.submit(run.id(), afterAbort));
        assertEquals(RunStatus.ABORTED, controller.status(run.id()).status());
        assertFalse(Files.exists(project.resolve("orders.camel.yaml")));
    }

    @Test
    void rejectsMalformedWireTypesDuplicateKeysAndNullResults() throws Exception {
        ShipRun run = start(Oversight.NEVER);
        Path input = receipt(controller.pendingTask(run));
        String original = Files.readString(input);
        for (String malformed : List.of(
                original.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                original.replace("\"attempt\":1", "\"attempt\":\"1\""),
                "null",
                original.replace("\"outcome\":\"SUCCEEDED\"", "\"outcome\":null"),
                original.replace("\"report\":\"Completed DISCOVERY\"", "\"report\":123"),
                original + " {}")) {
            Files.writeString(input, malformed);
            assertThrows(IOException.class, () -> coordinator.submit(run.id(), input));
            assertEquals(run, controller.status(run.id()));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(ShipNativeWorker.NativeOutcome.class)
    void rejectsUnsafeHostVersionsWithoutAcceptingTheReceipt(ShipNativeWorker.NativeOutcome outcome) throws Exception {
        ShipRun run = start(Oversight.NEVER);
        ShipNativeWorker.Task task = controller.pendingTask(run);
        Path input = receipt(task);
        ObjectNode envelope = (ObjectNode) JSON.readTree(Files.readString(input));
        envelope.put("outcome", outcome.name());
        if (outcome != ShipNativeWorker.NativeOutcome.SUCCEEDED) {
            envelope.putNull("response");
            envelope.put("failure", "Native child did not complete");
        }
        for (String version : List.of("", " ", "x".repeat(1025), "1\n2", "1\r2", "1\0", "1\u001b[31m", "1\u202e2")) {
            envelope.put("hostVersion", version);
            Files.writeString(input, JSON.writeValueAsString(envelope));

            assertThrows(IOException.class, () -> coordinator.submit(run.id(), input));
            assertEquals(run, controller.status(run.id()));
            assertFalse(Files.exists(evidence(task).resolve("native-result.json")));
        }
    }

    @Test
    void controllerWritesOnlyApprovedFilesAndConstructsHashes() throws Exception {
        Path candidate = Files.createDirectory(directory.resolve("candidate"));
        Path manifest = candidate.resolve("docs/camel-kit/223-native/artifact-manifest.json");
        ArtifactPolicy policy = policy();
        List<ProposedFile> files = List.of(
                new ProposedFile("pom.xml", "<project/>\n"),
                new ProposedFile(".camel-kit/config.properties", "project.runtime=main\n"),
                new ProposedFile("orders.camel.yaml", "route content\n"),
                new ProposedFile("test/orders.camel.it.yaml", "test content\n"));
        ShipNativeWorker.applyProposals(candidate, manifest, policy, files, Map.of());
        var parsed = ArtifactManifestReader.read(candidate, manifest).manifest();
        assertEquals(ShipDigest.sha256(Files.readAllBytes(candidate.resolve("orders.camel.yaml"))),
                parsed.routes().get(0).digest());
        assertEquals(policy.camelVersion(), parsed.camelVersion());
        assertFalse(Files.exists(project.resolve("orders.camel.yaml")));

        List<ProposedFile> escape = List.of(new ProposedFile("pom.xml", "would overwrite"),
                new ProposedFile(".bob/agents/camel-ship-worker.md", "broader tools"));
        assertThrows(IOException.class,
                () -> ShipNativeWorker.applyProposals(candidate, manifest, policy, escape, Map.of()));
        assertEquals("<project/>\n", Files.readString(candidate.resolve("pom.xml")));

        assertThrows(IOException.class, () -> ShipNativeWorker.applyProposals(candidate, manifest, policy,
                List.of(new ProposedFile("application.properties", "camel.main.routes-include-pattern=other.yaml")),
                Map.of()));
        assertFalse(Files.exists(candidate.resolve("application.properties")));

        IOException empty = assertThrows(IOException.class, () -> ShipNativeWorker.applyProposals(
                candidate, manifest, policy, List.of(new ProposedFile(".camel-kit/config.properties", "")), Map.of()));
        assertEquals("Native handoff input is empty", empty.getMessage());

        Path outside = Files.writeString(directory.resolve("outside"), "untouched");
        Files.delete(candidate.resolve("pom.xml"));
        Files.createSymbolicLink(candidate.resolve("pom.xml"), outside);
        assertThrows(IOException.class, () -> ShipNativeWorker.applyProposals(candidate, manifest, policy,
                List.of(new ProposedFile("pom.xml", "change")), Map.of()));
        assertEquals("untouched", Files.readString(outside));
    }

    @Test
    void aNativeRunCannotBeDrivenByAPiCoordinator() throws Exception {
        ShipRun run = controller.start(project, Oversight.SMART, List.of(), ExecutionMode.PI);
        assertThrows(IllegalArgumentException.class, () -> coordinator.run(run.id()));
        assertEquals(run, controller.status(run.id()));
    }

    ExecutionMode executionMode() {
        return ExecutionMode.BOB2_NATIVE;
    }

    private ShipRun start(Oversight oversight) throws Exception {
        return coordinator.run(controller.start(project, oversight,
                List.of(new ShipContext.TextInput("Generate an orders route")), executionMode()).id());
    }

    private Path receipt(ShipNativeWorker.Task task) throws Exception {
        ObjectNode envelope = JSON.createObjectNode();
        envelope.put("schemaVersion", 1);
        envelope.put("taskId", task.taskId());
        envelope.put("runId", task.runId());
        envelope.put("stage", task.stage().name());
        envelope.put("attempt", task.attempt());
        envelope.put("inputDigest", task.inputDigest());
        envelope.put("hostVersion", "2.0.2");
        envelope.put("outcome", "SUCCEEDED");
        envelope.putNull("failure");
        ObjectNode response = envelope.putObject("response");
        ObjectNode result = response.putObject("result");
        result.put("schemaVersion", 2);
        if (task.stage() == Stage.DISCOVERY) {
            result.put("pipelineId", "223-native");
        } else {
            result.putNull("pipelineId");
        }
        result.put("report", "Completed " + task.stage());
        result.put("materialAmbiguity", false);
        result.putArray("unansweredQuestions");
        result.set("artifactPolicy", task.stage() == Stage.PLAN ? JSON.valueToTree(policy()) : JSON.nullNode());
        response.putArray("files");
        Path path = directory.resolve(task.taskId() + ".json");
        Files.writeString(path, JSON.writeValueAsString(envelope));
        return path;
    }

    private ArtifactPolicy policy() {
        return new ArtifactPolicy(
                "main", distribution.camelMainVersion(), null, null, "yaml", "simple",
                distribution.citrusVersion(), CitrusDependencyPolicy.required(distribution.citrusVersion()),
                JavaPolicy.FORBIDDEN, List.of(),
                List.of(new ArtifactPolicy.RouteContract("orders", "orders.camel.yaml", "test/orders.camel.it.yaml")),
                true, true);
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-09T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

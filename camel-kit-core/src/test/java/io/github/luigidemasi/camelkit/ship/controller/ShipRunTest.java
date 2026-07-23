package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.context.ShipContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.Oversight.ALWAYS;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.Oversight.NEVER;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.Oversight.SMART;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.RunStatus.ABORTED;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.RunStatus.COMPLETED;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.RunStatus.FAILED;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.RunStatus.PAUSED;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.RunStatus.RUNNING;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage.DESIGN;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage.DISCOVERY;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage.EXECUTE;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage.PLAN;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage.VALIDATE;
import static io.github.luigidemasi.camelkit.ship.controller.ShipRun.StageStatus.PENDING;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipRunTest {

    private static final String RUN_ID = "ship-0123456789abcdef0123456789abcdef";
    private static final String CREATED_AT = "2026-07-23T08:00:00Z";
    private static final String UPDATED_AT = "2026-07-23T08:00:01Z";
    private static final String INPUT = digest("input");
    private static final String OUTPUT = digest("output");

    @Test
    void parsesCanonicalEnumsAndAdvancesStages() {
        assertAll(
                () -> assertSame(ALWAYS, ShipRun.Oversight.valueOf("ALWAYS")),
                () -> assertSame(SMART, ShipRun.Oversight.valueOf("SMART")),
                () -> assertSame(NEVER, ShipRun.Oversight.valueOf("NEVER")),
                () -> assertSame(RUNNING, ShipRun.RunStatus.valueOf("RUNNING")),
                () -> assertSame(DISCOVERY, ShipRun.Stage.valueOf("DISCOVERY")),
                () -> assertSame(PENDING, ShipRun.StageStatus.valueOf("PENDING")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ShipRun.Oversight.valueOf("smart")),
                () -> assertSame(DESIGN, DISCOVERY.next()),
                () -> assertSame(PLAN, DESIGN.next()),
                () -> assertSame(EXECUTE, PLAN.next()),
                () -> assertSame(VALIDATE, EXECUTE.next()),
                () -> assertNull(VALIDATE.next()));
    }

    @Test
    void appliesOversightPauseSemantics() {
        assertAll(
                () -> assertFalse(ALWAYS.pausesAfter(DISCOVERY, false)),
                () -> assertTrue(ALWAYS.pausesAfter(DESIGN, false)),
                () -> assertTrue(ALWAYS.pausesAfter(PLAN, false)),
                () -> assertTrue(ALWAYS.pausesAfter(EXECUTE, false)),
                () -> assertTrue(ALWAYS.pausesAfter(VALIDATE, false)),
                () -> assertFalse(SMART.pausesAfter(DESIGN, false)),
                () -> assertTrue(SMART.pausesAfter(PLAN, false)),
                () -> assertTrue(SMART.pausesAfter(EXECUTE, false)),
                () -> assertFalse(SMART.pausesAfter(VALIDATE, false)),
                () -> assertFalse(NEVER.pausesAfter(PLAN, false)),
                () -> assertTrue(ALWAYS.pausesAfter(DISCOVERY, true)),
                () -> assertTrue(SMART.pausesAfter(DISCOVERY, true)),
                () -> assertFalse(NEVER.pausesAfter(DISCOVERY, true)));
    }

    @Test
    void acceptsCanonicalProgressForEveryRunStatus() {
        List<ShipRun.StageRecord> running = ShipRun.pendingStages();
        List<ShipRun.StageRecord> runningStages = replace(
                running, DISCOVERY, running.get(0).start(INPUT));

        List<ShipRun.StageRecord> pausedStages = replace(
                completedThrough(DISCOVERY), DESIGN, ShipRun.StageRecord.pending(DESIGN));

        List<ShipRun.StageRecord> failed = ShipRun.pendingStages();
        List<ShipRun.StageRecord> failedStages = replace(
                failed, DISCOVERY, failed.get(0).start(INPUT).fail());

        List<ShipRun.StageRecord> aborted = ShipRun.pendingStages();
        List<ShipRun.StageRecord> abortedStages = replace(
                aborted, DISCOVERY, aborted.get(0).abort());

        assertAll(
                () -> assertDoesNotThrow(() -> run(RUNNING, DISCOVERY, runningStages, null)),
                () -> assertDoesNotThrow(() -> run(PAUSED, DESIGN, pausedStages, null)),
                () -> assertDoesNotThrow(() -> run(FAILED, DISCOVERY, failedStages, "stage failed")),
                () -> assertDoesNotThrow(() -> run(ABORTED, DISCOVERY, abortedStages, "cancelled")),
                () -> assertDoesNotThrow(() -> run(COMPLETED, VALIDATE,
                        completedThrough(VALIDATE), null)),
                () -> assertDoesNotThrow(() -> run(PAUSED, VALIDATE,
                        completedThrough(VALIDATE), null)),
                () -> assertDoesNotThrow(() -> run(ABORTED, VALIDATE,
                        completedThrough(VALIDATE), "cancelled after final approval")));
    }

    @Test
    void incrementsAttemptsAcrossResetAndAllowsOnlyLegalTransitions() {
        ShipRun.StageRecord pending = ShipRun.StageRecord.pending(DISCOVERY);
        ShipRun.StageRecord first = pending.start(INPUT);
        ShipRun.StageRecord failed = first.fail();
        ShipRun.StageRecord reset = failed.reset();
        ShipRun.StageRecord second = reset.start(digest("changed input"));
        ShipRun.StageRecord completed = second.complete(OUTPUT, List.of());

        assertAll(
                () -> assertEquals(0, pending.attempts()),
                () -> assertEquals(1, first.attempts()),
                () -> assertEquals(1, reset.attempts()),
                () -> assertEquals(2, second.attempts()),
                () -> assertEquals(2, completed.attempts()),
                () -> assertEquals(PENDING, reset.status()),
                () -> assertThrows(IllegalStateException.class, () -> pending.complete(OUTPUT, List.of())),
                () -> assertThrows(IllegalStateException.class, () -> first.start(INPUT)),
                () -> assertThrows(IllegalStateException.class,
                        completed::fail),
                () -> assertThrows(IllegalStateException.class,
                        completed::abort));
    }

    @Test
    void computesDeterministicFramedInputDigests() {
        List<ShipRun.StageRecord> completed = completedThrough(DESIGN);
        ShipRun.ArtifactRef firstArtifact = new ShipRun.ArtifactRef(
                "/tmp/camel-kit-design.md", digest("design"));
        List<ShipRun.StageRecord> stages = replace(
                completed, DESIGN, completed.get(DESIGN.ordinal()).withArtifacts(List.of(firstArtifact)));

        String first = ShipRun.inputDigest(ShipContext.none(), stages, PLAN);
        String repeated = ShipRun.inputDigest(ShipContext.none(), List.copyOf(stages), PLAN);
        ShipRun.ArtifactRef changedArtifact = new ShipRun.ArtifactRef(
                firstArtifact.path(), digest("changed design"));
        List<ShipRun.StageRecord> changed = replace(stages, DESIGN,
                stages.get(DESIGN.ordinal()).withArtifacts(List.of(changedArtifact)));

        assertAll(
                () -> assertTrue(ShipDigest.isSha256(first)),
                () -> assertEquals(first, repeated),
                () -> assertNotEquals(first,
                        ShipRun.inputDigest(ShipContext.none(), changed, PLAN)),
                () -> assertNotEquals(
                        ShipRun.digestFields(List.of("ab", "c")),
                        ShipRun.digestFields(List.of("a", "bc"))),
                () -> assertThrows(IllegalStateException.class,
                        () -> ShipRun.inputDigest(ShipContext.none(), ShipRun.pendingStages(), DESIGN)));
    }

    @Test
    void rejectsInvalidIdentifiersDigestsAndProgress() {
        List<ShipRun.StageRecord> stages = ShipRun.pendingStages();
        List<ShipRun.StageRecord> wrongOrder = new ArrayList<>(stages);
        wrongOrder.set(DISCOVERY.ordinal(), ShipRun.StageRecord.pending(DESIGN));
        wrongOrder.set(DESIGN.ordinal(), ShipRun.StageRecord.pending(DISCOVERY));

        assertAll(
                () -> assertTrue(ShipRun.isRunId(RUN_ID)),
                () -> assertFalse(ShipRun.isRunId("ship-ABC")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ShipRun.ArtifactRef("/tmp/artifact", "sha256:bad")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ShipRun.StageRecord(
                                DISCOVERY, ShipRun.StageStatus.RUNNING, 0,
                                INPUT, null, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ShipRun.StageRecord(
                                DISCOVERY, ShipRun.StageStatus.RUNNING, 1,
                                "not-a-digest", null, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> run(RUNNING, DISCOVERY, stages, null, "ship-ABC")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> run(RUNNING, DESIGN, stages, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> run(COMPLETED, VALIDATE, stages, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> run(PAUSED, DISCOVERY,
                                replace(stages, DISCOVERY, stages.get(0).start(INPUT)), null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> run(RUNNING, DISCOVERY, wrongOrder, null)));
    }

    @Test
    void rejectsUnsafePipelineMessagesAndTimestamps() {
        assertRejected(
                "Ship pipeline ID is invalid",
                () -> abortedRun(
                        "149-safe/../../escape", CREATED_AT, UPDATED_AT, "cancelled"));
        assertRejected(
                "Ship run message is invalid",
                () -> abortedRun(null, CREATED_AT, UPDATED_AT, " "));
        assertRejected(
                "Ship run message is invalid",
                () -> abortedRun(null, CREATED_AT, UPDATED_AT, "x".repeat(1025)));
        assertRejected(
                "Ship run message is invalid",
                () -> abortedRun(null, CREATED_AT, UPDATED_AT, "bad\0message"));
        assertRejected(
                "Ship run update precedes its creation",
                () -> abortedRun(null, UPDATED_AT, CREATED_AT, "cancelled"));
        assertRejected(
                "Ship created timestamp is invalid",
                () -> abortedRun(
                        null, "2026-07-23T08:00:00.000Z", UPDATED_AT, "cancelled"));
        assertRejected(
                "Ship updated timestamp is invalid",
                () -> abortedRun(
                        null, CREATED_AT, "2026-07-23T08:00:01.000Z", "cancelled"));
    }

    private static ShipRun run(
            ShipRun.RunStatus status,
            ShipRun.Stage currentStage,
            List<ShipRun.StageRecord> stages,
            String message) {
        return run(status, currentStage, stages, message, RUN_ID);
    }

    private static ShipRun run(
            ShipRun.RunStatus status,
            ShipRun.Stage currentStage,
            List<ShipRun.StageRecord> stages,
            String message,
            String id) {
        return new ShipRun(
                ShipRun.SCHEMA_VERSION,
                id,
                "/tmp/camel-kit-project",
                null,
                SMART,
                status,
                currentStage,
                ShipContext.none(),
                stages,
                CREATED_AT,
                UPDATED_AT,
                message);
    }

    private static ShipRun abortedRun(
            String pipelineId, String createdAt, String updatedAt, String message) {
        List<ShipRun.StageRecord> stages = ShipRun.pendingStages();
        stages = replace(stages, DISCOVERY, stages.get(0).abort());
        return new ShipRun(
                ShipRun.SCHEMA_VERSION,
                RUN_ID,
                "/tmp/camel-kit-project",
                pipelineId,
                SMART,
                ABORTED,
                DISCOVERY,
                ShipContext.none(),
                stages,
                createdAt,
                updatedAt,
                message);
    }

    private static void assertRejected(String message, Executable construction) {
        assertEquals(
                message,
                assertThrows(IllegalArgumentException.class, construction).getMessage());
    }

    private static List<ShipRun.StageRecord> completedThrough(ShipRun.Stage last) {
        List<ShipRun.StageRecord> stages = new ArrayList<>(ShipRun.pendingStages());
        for (ShipRun.Stage stage : ShipRun.Stage.values()) {
            if (stage.ordinal() > last.ordinal()) {
                break;
            }
            ShipRun.StageRecord completed = stages.get(stage.ordinal())
                    .start(digest(stage + " input"))
                    .complete(digest(stage + " output"), List.of());
            stages.set(stage.ordinal(), completed);
        }
        return List.copyOf(stages);
    }

    private static List<ShipRun.StageRecord> replace(
            List<ShipRun.StageRecord> stages, ShipRun.Stage stage, ShipRun.StageRecord record) {
        List<ShipRun.StageRecord> changed = new ArrayList<>(stages);
        changed.set(stage.ordinal(), record);
        return List.copyOf(changed);
    }

    private static String digest(String value) {
        return ShipDigest.sha256(value.getBytes(StandardCharsets.UTF_8));
    }
}

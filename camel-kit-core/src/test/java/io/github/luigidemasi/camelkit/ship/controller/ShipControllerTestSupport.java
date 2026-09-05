package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.Path;
import java.util.List;

/** Grants tests outside this package access to the package-private stage-completion seam. */
public final class ShipControllerTestSupport {

    private ShipControllerTestSupport() {
    }

    /** Records a worker stage result exactly as the coordinator does, with an explicit report. */
    public static ShipRun completeStage(
            ShipController controller,
            String runId,
            ShipRun.Stage stage,
            int attempt,
            String inputDigest,
            String outputDigest,
            List<Path> artifacts,
            boolean materialAmbiguity,
            String report) {
        return controller.completeStage(
                runId,
                stage,
                attempt,
                inputDigest,
                outputDigest,
                artifacts,
                materialAmbiguity,
                report);
    }

    public static ShipRun completeStage(
            ShipController controller,
            String runId,
            ShipRun.Stage stage,
            int attempt,
            String inputDigest,
            String outputDigest,
            List<Path> artifacts,
            boolean materialAmbiguity,
            String report,
            List<ShipRun.UnansweredQuestion> unansweredQuestions) {
        return controller.completeStage(runId, stage, attempt, inputDigest, outputDigest,
                artifacts, materialAmbiguity, report, unansweredQuestions);
    }
}

package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.ToolVersion;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker;

/** Execution boundary: an empty execution result yields control to the native host. */
interface ShipStageWorker {

    ShipRun.ExecutionMode mode();

    Optional<Result> recover(Request request) throws IOException;

    Optional<Result> run(Request request) throws IOException, InterruptedException;

    Recovery lockRecovery(Request request) throws IOException;

    interface Recovery extends AutoCloseable {

        Optional<Result> result();

        @Override
        void close() throws IOException;
    }

    record Request(String runId, ShipRun.Stage stage, int attempt, Path workingDirectory,
            Path sessionDirectory, Path evidenceDirectory, String inputDigest,
            boolean acceptExperimental, String prompt) {

        @Override
        public String toString() {
            return "Request[runId=" + runId + ", stage=" + stage + ", attempt=" + attempt + ", prompt=[redacted]]";
        }

        PiWorker.Request piRequest() {
            return new PiWorker.Request(
                    runId, stage, attempt, workingDirectory, sessionDirectory,
                    evidenceDirectory, inputDigest, acceptExperimental, prompt);
        }
    }

    record Result(String assistantText, String failure, List<ToolVersion> tools, List<ProposedFile> files) {

        public Result {
            tools = List.copyOf(tools);
            files = List.copyOf(files);
        }

        boolean succeeded() {
            return failure == null;
        }
    }

    /** Text only. The controller validates and writes proposals; native children cannot mutate files. */
    record ProposedFile(String path, String content) {
    }

    static ShipStageWorker pi(PiWorker worker) {
        return new ShipStageWorker() {
            @Override
            public ShipRun.ExecutionMode mode() {
                return ShipRun.ExecutionMode.PI;
            }

            @Override
            public Optional<Result> recover(Request request) throws IOException {
                return worker.recover(request.piRequest()).map(ShipStageWorker::fromPi);
            }

            @Override
            public Optional<Result> run(Request request) throws IOException, InterruptedException {
                return Optional.of(fromPi(worker.run(request.piRequest())));
            }

            @Override
            public Recovery lockRecovery(Request request) throws IOException {
                PiWorker.Recovery recovery = worker.lockRecovery(request.piRequest());
                return new Recovery() {
                    @Override
                    public Optional<Result> result() {
                        return recovery.result().map(ShipStageWorker::fromPi);
                    }

                    @Override
                    public void close() throws IOException {
                        recovery.close();
                    }
                };
            }
        };
    }

    private static Result fromPi(PiWorker.Result result) {
        return new Result(
                result.assistantText(),
                result.outcome() == PiWorker.Outcome.SUCCEEDED ? null
                        : result.failure() == null ? "Pi stage failed" : result.failure(),
                List.of(new ToolVersion(
                        "pi", result.evidence().executable(), result.version(),
                        result.support(), result.warning()), result.node()),
                List.of());
    }
}

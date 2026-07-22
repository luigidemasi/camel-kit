package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.catalog.ShipCatalogService;
import io.github.luigidemasi.camelkit.ship.protocol.ProducedArtifact;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.security.StagedArtifactSource;

/**
 * The only artifact-bearing worker-result entry point. A backend must stop the complete containment, revoke every
 * worker write handle, and retain exclusive custody until the returned lease closes.
 */
final class ShipProtectedWorkerBroker {

    private final ShipController controller;
    private final Backend backend;

    ShipProtectedWorkerBroker(ShipController controller, Backend backend) {
        this.controller = Objects.requireNonNull(controller, "Ship controller");
        this.backend = Objects.requireNonNull(backend, "protected worker backend");
    }

    ShipRunView submit(
            String runId,
            String expectedEventDigest,
            ShipCatalogService.Snapshot catalogSnapshot)
            throws IOException {
        ShipRunView current = controller.status(runId);
        if (!Objects.equals(expectedEventDigest, current.eventDigest())
                || current.activeRequest() == null) {
            throw new ShipControllerException(
                    "stale-run-head", "Ship run changed before protected worker custody was acquired");
        }
        try (CompletedAttempt completed = acquire(current.activeRequest())) {
            return controller.submitProtectedStageResult(
                    runId, expectedEventDigest, completed, catalogSnapshot);
        }
    }

    CompletedAttempt acquire(StageRequest request) throws IOException {
        Custody custody = backend.stopAndAcquireExclusiveCustody(request);
        boolean accepted = false;
        try {
            custody.requireExactAttempt(request);
            byte[] result = ShipStageResultReader.boundedCopy(custody.readResultBytes());
            CompletedAttempt completed = new CompletedAttempt(request, result, custody);
            accepted = true;
            return completed;
        } finally {
            if (!accepted) {
                custody.close();
            }
        }
    }

    interface Backend {

        Custody stopAndAcquireExclusiveCustody(StageRequest request) throws IOException;
    }

    interface Custody extends AutoCloseable {

        /** Fails unless this lease owns the exact stopped containment and its output ancestry. */
        void requireExactAttempt(StageRequest request) throws IOException;

        byte[] readResultBytes() throws IOException;

        StagedArtifactSource.Session openArtifactSource() throws IOException;

        @Override
        void close() throws IOException;
    }

    static final class CompletedAttempt implements AutoCloseable {

        private final StageRequest request;
        private final byte[] resultBytes;
        private final Custody custody;
        private boolean imported;
        private boolean closed;

        private CompletedAttempt(StageRequest request, byte[] resultBytes, Custody custody) {
            this.request = request;
            this.resultBytes = resultBytes;
            this.custody = custody;
        }

        StageResult readResult(StageRequest expected) throws IOException {
            requireBound(expected);
            return ShipStageResultReader.readBrokered(expected, resultBytes);
        }

        List<ShipBlobStore.ImportedBlob> importArtifacts(
                StageRequest expected,
                List<ProducedArtifact> artifacts,
                ShipBlobStore.Transaction transaction)
                throws IOException {
            requireBound(expected);
            if (imported) {
                throw new IOException("Protected worker artifacts were already consumed");
            }
            imported = true;
            try (StagedArtifactSource.Session source = custody.openArtifactSource()) {
                return transaction.importArtifacts(source, artifacts);
            }
        }

        private void requireBound(StageRequest expected) throws IOException {
            if (closed || expected == null
                    || !request.runId().equals(expected.runId())
                    || request.stage() != expected.stage()
                    || !request.attemptId().equals(expected.attemptId())
                    || !request.challenge().equals(expected.challenge())
                    || !request.inputDigest().equals(expected.inputDigest())
                    || !request.outputDirectory().equals(expected.outputDirectory())) {
                throw new IOException("Protected worker custody is not bound to the active attempt");
            }
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                custody.close();
            }
        }
    }
}

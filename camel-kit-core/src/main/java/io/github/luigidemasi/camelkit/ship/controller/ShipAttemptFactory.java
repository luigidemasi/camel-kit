package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.Operation;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.RepositoryAccess;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;

/** Builds one non-chaining, controller-authored worker request. */
final class ShipAttemptFactory {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<ContractResource> DISCOVERY_GUIDANCE = List.of(
            new ContractResource("authoritative-guidance", "skills/camel-brainstorm/SKILL.md"),
            new ContractResource(
                    "authoritative-guidance", "skills/shared/discovery-completeness.md"),
            new ContractResource(
                    "authoritative-guidance", "skills/camel-brainstorm/guides/greenfield-interview.md"),
            new ContractResource(
                    "authoritative-guidance", "skills/camel-brainstorm/guides/migration-discovery.md"),
            new ContractResource(
                    "authoritative-guidance", "skills/camel-brainstorm/guides/migration-graph-analysis.md"),
            new ContractResource(
                    "authoritative-guidance",
                    "skills/camel-brainstorm/guides/migration-mule-graph-analysis.md"),
            new ContractResource(
                    "authoritative-guidance",
                    "skills/camel-brainstorm/guides/migration-biztalk-graph-analysis.md"),
            new ContractResource(
                    "authoritative-guidance", "skills/camel-brainstorm/guides/version-selection.md"),
            new ContractResource("controller-evidence-policy", "distribution.properties"),
            new ContractResource("controller-execution-override", "ship/workers/discovery.md"));

    /**
     * Creates a logical request only. The controller must materialize protected input references and issue a runnable
     * capability before dispatch. The returned value is not schema-conformant wire data and must not be serialized or
     * dispatched until that materialization replaces logical references with protected roots. This substrate never
     * exposes CAS paths or opens a project path.
     */
    StageRequest create(
            String runId,
            ShipStage stage,
            int attempt,
            String policyDigest,
            Inputs inputs,
            String workerContractReference,
            String outputDirectory,
            String failureCode,
            String failureMessage) {
        requireRunId(runId);
        Objects.requireNonNull(stage, "stage");
        if (attempt < 1) {
            throw new IllegalArgumentException("Ship worker attempt must be positive");
        }
        if (!ShipDigest.isSha256(policyDigest)) {
            throw new IllegalArgumentException("Ship worker policy digest is invalid");
        }
        Objects.requireNonNull(inputs, "inputs");
        requireReference(workerContractReference, "worker contract reference");
        String output = requireAbsoluteNormalized(outputDirectory, "worker output directory");
        if ((failureCode == null) != (failureMessage == null)) {
            throw new IllegalArgumentException(
                    "Ship worker retry context requires both failure code and message");
        }

        String attemptId = stage.name().toLowerCase(Locale.ROOT)
                           + '-' + attempt + '-' + randomToken(12);
        String challenge = randomToken(32);
        String inputDigest = inputDigest(stage, inputs, workerContractReference, failureCode, failureMessage);
        String idempotencyKey = ShipDigest.sha256(Interaction.canonicalMacBytes(new String[]{
                "camel-kit-ship-attempt-v2",
                runId,
                stage.name(),
                Integer.toString(attempt),
                inputDigest,
                policyDigest
        }));
        return new StageRequest(
                StageRequest.SCHEMA_VERSION,
                runId,
                stage,
                attemptId,
                attempt,
                idempotencyKey,
                challenge,
                inputDigest,
                policyDigest,
                inputs.sourceDirectory(),
                inputs.sourceSnapshotReference(),
                inputs.projectSourceManifestReference(),
                inputs.contextReference(),
                inputs.ledgerReference(),
                inputs.catalogEvidenceReference(),
                inputs.approvedDesignReference(),
                inputs.planReference(),
                inputs.interactionReference(),
                inputs.artifactManifestReference(),
                inputs.candidateDirectory(),
                inputs.evidenceReferences(),
                normalizeFailureCode(failureCode),
                normalizeFailureMessage(failureMessage),
                workerContractReference,
                output,
                logicalCapability(stage, output));
    }

    /**
     * Materializes one protected request; only the controller package can issue it. The caller must commit and replay
     * the stage-start event under its run lock before exposing either the request or its output root to a worker.
     */
    StageRequest createRunnable(
            ShipRunView run, ShipBlobStore blobs, ShipStage stage, int attempt)
            throws IOException {
        return createRunnable(run, blobs, stage, attempt, AttemptInputs.from(run));
    }

    /** Allows one event to bind its successor request to the values that event will project. */
    StageRequest createRunnable(
            ShipRunView run,
            ShipBlobStore blobs,
            ShipStage stage,
            int attempt,
            AttemptInputs projected)
            throws IOException {
        Objects.requireNonNull(run, "Ship run view");
        Objects.requireNonNull(blobs, "Ship blob store");
        Objects.requireNonNull(stage, "Ship stage");
        Objects.requireNonNull(projected, "Projected Ship attempt inputs");
        if (!blobs.belongsToRun(run.runId())) {
            throw new IOException("Ship run view belongs to another blob store");
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("Ship attempt must be positive");
        }
        validateFailure(projected.failureCode(), projected.failureMessage());

        SourceInputs source = sourceInputs(run, blobs);
        String attemptId = stage.name().toLowerCase(Locale.ROOT)
                           + '-' + attempt + '-' + randomToken(12);
        Path output = blobs.expectedAttemptOutput(stage, attemptId);
        BlobReference contract = workerContract(blobs, stage);
        RequestInputs inputs = requestInputs(blobs, stage, output, source, projected);
        StageCapability capability = capability(blobs, stage, output, contract, inputs);
        String inputDigest = inputDigest(
                stage, inputs, contract, projected.failureCode(), projected.failureMessage());
        String policyDigest = ShipDigest.sha256(ShipJson.mapper().writeValueAsBytes(capability));
        String idempotencyKey = idempotencyKey(
                run.runId(), stage, attempt, inputDigest, policyDigest);
        Path createdOutput = blobs.createAttemptOutput(stage, attemptId);
        if (!createdOutput.equals(output)) {
            throw new IOException("Ship attempt output changed while it was created");
        }
        if (stage == ShipStage.EXECUTE) {
            ProjectSnapshot candidate = ProjectEvidenceFiles.materializeMaterial(
                    source.directory(), output);
            if (!ProjectEvidenceFiles.unchangedMaterialTree(source.snapshot(), candidate)) {
                throw new IOException("Ship execution candidate differs from the immutable project source");
            }
        }
        return new StageRequest(
                StageRequest.SCHEMA_VERSION,
                run.runId(),
                stage,
                attemptId,
                attempt,
                idempotencyKey,
                randomToken(32),
                inputDigest,
                policyDigest,
                string(inputs.sourceDirectory()),
                path(blobs, inputs.sourceSnapshot()),
                path(blobs, inputs.projectSourceManifest()),
                path(blobs, inputs.context()),
                path(blobs, inputs.ledger()),
                path(blobs, inputs.catalogEvidence()),
                path(blobs, inputs.design()),
                path(blobs, inputs.plan()),
                path(blobs, inputs.interaction()),
                path(blobs, inputs.artifactManifest()),
                string(inputs.candidateDirectory()),
                evidencePaths(blobs, inputs.evidence()),
                projected.failureCode(),
                projected.failureMessage(),
                path(blobs, contract),
                output.toString(),
                capability);
    }

    /** Re-derives every deterministic field of one projected controller-issued request. */
    void validateIssued(
            ShipRunView run,
            ShipBlobStore blobs,
            StageRequest request,
            ShipStage expectedStage,
            int expectedAttempt,
            String eventOutputDirectory)
            throws IOException {
        Objects.requireNonNull(run, "Ship run view");
        Objects.requireNonNull(blobs, "Ship blob store");
        Objects.requireNonNull(expectedStage, "Ship stage");
        if (!blobs.belongsToRun(run.runId())) {
            throw new IOException("Ship run view belongs to another blob store");
        }
        if (request == null
                || request.schemaVersion() != StageRequest.SCHEMA_VERSION
                || !run.runId().equals(request.runId())
                || request.stage() != expectedStage
                || request.attempt() != expectedAttempt) {
            throw new IOException("Stage request identity does not match the issuing event");
        }
        validateFailure(run.failureCode(), run.failureMessage());
        requireRandomIdentity(request.attemptId(), expectedStage, expectedAttempt);
        requireRandomToken(request.challenge(), 32, "Stage request challenge");

        Path output = blobs.verifyAttemptOutput(expectedStage, request.attemptId());
        if (!output.toString().equals(request.outputDirectory())
                || eventOutputDirectory != null
                        && !eventOutputDirectory.equals(request.outputDirectory())) {
            throw new IOException("Stage request output is not the exact protected attempt directory");
        }

        SourceInputs source = sourceInputs(run, blobs);
        BlobReference contract = expectedWorkerContract(blobs, expectedStage);
        RequestInputs inputs = requestInputs(
                blobs, expectedStage, output, source, AttemptInputs.from(run));
        StageCapability expectedCapability = capability(
                blobs, expectedStage, output, contract, inputs);
        String expectedInputDigest = inputDigest(
                expectedStage, inputs, contract, run.failureCode(), run.failureMessage());
        String expectedPolicyDigest = ShipDigest.sha256(
                ShipJson.mapper().writeValueAsBytes(expectedCapability));
        String expectedIdempotencyKey = idempotencyKey(
                run.runId(), expectedStage, expectedAttempt, expectedInputDigest, expectedPolicyDigest);
        if (!Objects.equals(string(inputs.sourceDirectory()), request.sourceDirectory())
                || !Objects.equals(path(blobs, inputs.sourceSnapshot()), request.sourceSnapshotReference())
                || !Objects.equals(
                        path(blobs, inputs.projectSourceManifest()),
                        request.projectSourceManifestReference())
                || !Objects.equals(path(blobs, inputs.context()), request.contextReference())
                || !Objects.equals(path(blobs, inputs.ledger()), request.ledgerReference())
                || !Objects.equals(
                        path(blobs, inputs.catalogEvidence()), request.catalogEvidenceReference())
                || !Objects.equals(path(blobs, inputs.design()), request.approvedDesignReference())
                || !Objects.equals(path(blobs, inputs.plan()), request.planReference())
                || !Objects.equals(path(blobs, inputs.interaction()), request.interactionReference())
                || !Objects.equals(
                        path(blobs, inputs.artifactManifest()), request.artifactManifestReference())
                || !Objects.equals(string(inputs.candidateDirectory()), request.candidateDirectory())
                || !evidencePaths(blobs, inputs.evidence()).equals(request.evidenceReferences())
                || !Objects.equals(run.failureCode(), request.failureCode())
                || !Objects.equals(run.failureMessage(), request.failureMessage())
                || !path(blobs, contract).equals(request.workerContractReference())
                || !expectedCapability.equals(request.capability())
                || !expectedInputDigest.equals(request.inputDigest())
                || !expectedPolicyDigest.equals(request.policyDigest())
                || !expectedIdempotencyKey.equals(request.idempotencyKey())) {
            throw new IOException("Stage request is not bound to the exact current Ship projection");
        }
    }

    private static RequestInputs requestInputs(
            ShipBlobStore blobs,
            ShipStage stage,
            Path output,
            SourceInputs source,
            AttemptInputs projected)
            throws IOException {
        if (stage == ShipStage.DISCOVERY
                && projected.catalogEvidence() != null
                && projected.ledger() == null) {
            throw new IOException("Ship discovery catalog evidence requires its decision ledger");
        }
        return switch (stage) {
            case DISCOVERY -> new RequestInputs(
                    source.directory(),
                    source.reference(),
                    source.manifestReference(),
                    requireKind(require(projected.context(), "discovery context"), "initial-context"),
                    optionalKind(projected.ledger(), "decision-ledger"),
                    optionalKind(projected.catalogEvidence(), "catalog-evidence"),
                    null,
                    null,
                    requireKind(
                            require(projected.interactionBundle(), "discovery interactions"),
                            "interaction-bundle"),
                    null,
                    null,
                    null,
                    List.of());
            case REVIEW -> new RequestInputs(
                    source.directory(),
                    source.reference(),
                    source.manifestReference(),
                    requireKind(require(projected.context(), "review context"), "initial-context"),
                    requireKind(require(projected.ledger(), "review ledger"), "decision-ledger"),
                    requireKind(
                            require(projected.catalogEvidence(), "review catalog evidence"),
                            "catalog-evidence"),
                    null,
                    null,
                    requireKind(
                            require(projected.interactionBundle(), "review interactions"),
                            "interaction-bundle"),
                    null,
                    null,
                    null,
                    List.of());
            case DESIGN -> new RequestInputs(
                    source.directory(),
                    source.reference(),
                    source.manifestReference(),
                    requireKind(require(projected.context(), "design context"), "initial-context"),
                    requireKind(require(projected.ledger(), "design ledger"), "decision-ledger"),
                    requireKind(
                            require(projected.catalogEvidence(), "design catalog evidence"),
                            "catalog-evidence"),
                    null,
                    null,
                    requireKind(
                            require(projected.interactionBundle(), "design interactions"),
                            "interaction-bundle"),
                    null,
                    null,
                    null,
                    List.of());
            case PLAN -> new RequestInputs(
                    null,
                    null,
                    null,
                    null,
                    requireKind(require(projected.ledger(), "plan ledger"), "decision-ledger"),
                    requireKind(
                            require(projected.catalogEvidence(), "plan catalog evidence"),
                            "catalog-evidence"),
                    requireKind(require(projected.design(), "approved design"), "design"),
                    null,
                    requireKind(
                            require(projected.interactionBundle(), "plan interactions"),
                            "interaction-bundle"),
                    null,
                    null,
                    null,
                    List.of());
            case EXECUTE -> new RequestInputs(
                    null,
                    null,
                    null,
                    null,
                    requireKind(require(projected.ledger(), "execution ledger"), "decision-ledger"),
                    requireKind(
                            require(projected.catalogEvidence(), "execution catalog evidence"),
                            "catalog-evidence"),
                    requireKind(
                            require(projected.design(), "approved execution design"), "design"),
                    requireKind(require(projected.plan(), "execution plan"), "plan"),
                    null,
                    null,
                    null,
                    output,
                    List.of());
            case VALIDATE -> {
                CandidateInputs candidate = candidate(projected, blobs);
                yield new RequestInputs(
                        null,
                        null,
                        null,
                        null,
                        requireKind(
                                require(projected.ledger(), "validation ledger"),
                                "decision-ledger"),
                        requireKind(
                                require(projected.catalogEvidence(), "validation catalog evidence"),
                                "catalog-evidence"),
                        requireKind(
                                require(projected.design(), "approved validation design"), "design"),
                        requireKind(require(projected.plan(), "validation plan"), "plan"),
                        null,
                        requireKind(
                                require(projected.artifactManifest(), "validation artifact manifest"),
                                "artifact-manifest"),
                        candidate.snapshot(),
                        candidate.directory(),
                        requireKinds(projected.evidence(), "command-evidence"));
            }
        };
    }

    private static SourceInputs sourceInputs(ShipRunView run, ShipBlobStore blobs) throws IOException {
        BlobReference snapshotReference = requireKind(
                require(run.sourceSnapshot(), "project source snapshot"), "project-snapshot");
        BlobReference manifestReference = requireKind(
                require(run.projectSourceManifest(), "project source manifest"),
                "project-source-manifest");
        Path directory = require(run.sourceDirectory(), "project source directory");
        if (!directory.equals(blobs.expectedSourceDirectory())) {
            throw new IOException("Ship project source is outside the protected run directory");
        }
        ProjectSnapshot snapshot = read(blobs, snapshotReference, ProjectSnapshot.class);
        ProjectSourceManifest manifest = read(blobs, manifestReference, ProjectSourceManifest.class);
        if (!Path.of(snapshot.root()).equals(directory)
                || !ProjectSourceManifest.from(snapshot).equals(manifest)) {
            throw new IOException("Ship project source manifest is not bound to its exact snapshot");
        }
        ProjectSnapshot observed = ProjectEvidenceFiles.captureSealed(directory);
        try {
            if (!ProjectEvidenceFiles.unchanged(snapshot, observed)) {
                throw new IOException("Ship project source differs from its immutable snapshot");
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Ship project source identity differs from its immutable snapshot", e);
        }
        return new SourceInputs(directory, snapshotReference, snapshot, manifestReference);
    }

    private static CandidateInputs candidate(AttemptInputs projected, ShipBlobStore blobs)
            throws IOException {
        Path directory = blobs.verifyCandidateDirectory(
                require(projected.candidateDirectory(), "validation candidate"));
        BlobReference reference = requireKind(
                require(projected.candidateSnapshot(), "validation candidate snapshot"),
                "project-snapshot");
        ProjectSnapshot snapshot = read(blobs, reference, ProjectSnapshot.class);
        if (!Path.of(snapshot.root()).equals(directory)) {
            throw new IOException("Ship validation candidate snapshot names another directory");
        }
        ProjectSnapshot observed = ProjectEvidenceFiles.captureSealed(directory);
        try {
            if (!ProjectEvidenceFiles.unchanged(snapshot, observed)) {
                throw new IOException("Ship validation candidate differs from its immutable snapshot");
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Ship validation candidate identity differs from its snapshot", e);
        }
        return new CandidateInputs(directory, reference);
    }

    private static StageCapability capability(
            ShipBlobStore blobs,
            ShipStage stage,
            Path output,
            BlobReference contract,
            RequestInputs inputs)
            throws IOException {
        List<String> reads = new ArrayList<>();
        switch (stage) {
            case DISCOVERY, REVIEW, DESIGN -> {
                reads.add(require(inputs.sourceDirectory(), "project source directory").toString());
                addPath(reads, blobs, require(inputs.sourceSnapshot(), "project source snapshot"));
                addPath(
                        reads,
                        blobs,
                        require(inputs.projectSourceManifest(), "project source manifest"));
                addPath(reads, blobs, inputs.context());
                addPath(reads, blobs, inputs.ledger());
                addPath(reads, blobs, inputs.catalogEvidence());
                addPath(reads, blobs, inputs.interaction());
            }
            case PLAN -> {
                addPath(reads, blobs, inputs.ledger());
                addPath(reads, blobs, inputs.catalogEvidence());
                addPath(reads, blobs, inputs.design());
                addPath(reads, blobs, inputs.interaction());
            }
            case EXECUTE -> {
                reads.add(require(inputs.candidateDirectory(), "execution candidate").toString());
                addPath(reads, blobs, inputs.ledger());
                addPath(reads, blobs, inputs.catalogEvidence());
                addPath(reads, blobs, inputs.design());
                addPath(reads, blobs, inputs.plan());
            }
            case VALIDATE -> {
                reads.add(require(inputs.candidateDirectory(), "validation candidate").toString());
                addPath(reads, blobs, inputs.ledger());
                addPath(reads, blobs, inputs.catalogEvidence());
                addPath(reads, blobs, inputs.design());
                addPath(reads, blobs, inputs.plan());
                addPath(reads, blobs, inputs.artifactManifest());
                for (BlobReference evidence : inputs.evidence()) {
                    addPath(reads, blobs, evidence);
                }
            }
        }
        addPath(reads, blobs, contract);
        if (reads.size() > 20_007 || new LinkedHashSet<>(reads).size() != reads.size()) {
            throw new IOException("Ship request contains duplicate or excessive protected read roots");
        }

        List<Operation> operations = new ArrayList<>(
                List.of(Operation.READ, Operation.SEARCH, Operation.RETURN_STRUCTURED_RESULT));
        RepositoryAccess access;
        List<String> writes;
        if (stage == ShipStage.DISCOVERY || stage == ShipStage.REVIEW) {
            access = RepositoryAccess.READ_ONLY;
            writes = List.of();
        } else {
            access = stage == ShipStage.EXECUTE
                    ? RepositoryAccess.DECLARED_EXECUTION_PATHS
                    : RepositoryAccess.READ_WITH_STAGED_OUTPUT;
            writes = List.of(output.toString());
            operations.add(Operation.WRITE_STAGED_ARTIFACT);
        }
        return new StageCapability(access, reads, writes, operations, false, false);
    }

    private static String inputDigest(
            ShipStage stage,
            RequestInputs inputs,
            BlobReference contract,
            String failureCode,
            String failureMessage) {
        List<String> fields = new ArrayList<>();
        fields.add("camel-kit.ship.runnable-input.v1");
        fields.add(stage.name());
        fields.add(string(inputs.sourceDirectory()));
        addBinding(fields, inputs.sourceSnapshot());
        addBinding(fields, inputs.projectSourceManifest());
        addBinding(fields, inputs.context());
        addBinding(fields, inputs.ledger());
        addBinding(fields, inputs.catalogEvidence());
        addBinding(fields, inputs.design());
        addBinding(fields, inputs.plan());
        addBinding(fields, inputs.interaction());
        addBinding(fields, inputs.artifactManifest());
        addBinding(fields, inputs.candidateSnapshot());
        fields.add(string(inputs.candidateDirectory()));
        fields.add(Integer.toString(inputs.evidence().size()));
        inputs.evidence().forEach(reference -> addBinding(fields, reference));
        fields.add(failureCode);
        fields.add(failureMessage);
        addBinding(fields, contract);
        return ShipDigest.sha256(Interaction.canonicalMacBytes(fields.toArray(String[]::new)));
    }

    private static String idempotencyKey(
            String runId, ShipStage stage, int attempt, String inputDigest, String policyDigest) {
        return ShipDigest.sha256(Interaction.canonicalMacBytes(new String[]{
                "camel-kit.ship.attempt-idempotency.v1",
                runId,
                stage.name(),
                Integer.toString(attempt),
                inputDigest,
                policyDigest
        }));
    }

    private static BlobReference workerContract(ShipBlobStore blobs, ShipStage stage)
            throws IOException {
        return blobs.writeBytes("worker-contract", workerContractContent(stage));
    }

    private static BlobReference expectedWorkerContract(ShipBlobStore blobs, ShipStage stage)
            throws IOException {
        byte[] content = workerContractContent(stage);
        BlobReference reference = new BlobReference(
                "worker-contract", ShipDigest.sha256(content), content.length);
        blobs.verifiedPath(reference);
        return reference;
    }

    private static byte[] workerContractContent(ShipStage stage) throws IOException {
        if (stage == ShipStage.DISCOVERY) {
            return discoveryContract();
        }
        byte[] contract = readResource(
                "ship/workers/" + stage.name().toLowerCase(Locale.ROOT) + ".md");
        if (stage != ShipStage.REVIEW) {
            return contract;
        }
        byte[] shared = readResource("skills/shared/discovery-completeness.md");
        return (new String(contract, StandardCharsets.UTF_8)
                + "\n\n---\n\n"
                + new String(shared, StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] discoveryContract() throws IOException {
        List<ResourceContent> resources = new ArrayList<>();
        for (ContractResource resource : DISCOVERY_GUIDANCE) {
            byte[] content = readResource(resource.path());
            resources.add(new ResourceContent(resource, content, ShipDigest.sha256(content)));
        }
        StringBuilder contract = new StringBuilder("""
                # Controller-owned Ship discovery contract bundle

                The controller assembled and content-bound this exact guidance for one DISCOVERY attempt. Resources
                marked `authoritative-guidance` define Camel Brainstorm discovery semantics. The
                `controller-evidence-policy` resource defines the exact executable Ship compatibility intersection.
                The final `controller-execution-override` preserves those semantics while overriding every standalone
                or chained instruction about tools, persistence, approval, file writes, and stage transitions.

                ## Exact resource manifest

                """);
        for (ResourceContent resource : resources) {
            contract.append("- role: ").append(resource.resource().role()).append('\n')
                    .append("  resource: ").append(resource.resource().path()).append('\n')
                    .append("  digest: ").append(resource.digest()).append('\n')
                    .append("  bytes: ").append(resource.content().length).append('\n');
        }
        for (ResourceContent resource : resources) {
            contract.append("\n--- BEGIN EXACT RESOURCE ---\n")
                    .append("role: ").append(resource.resource().role()).append('\n')
                    .append("resource: ").append(resource.resource().path()).append('\n')
                    .append("digest: ").append(resource.digest()).append('\n')
                    .append("bytes: ").append(resource.content().length).append('\n')
                    .append("--- BEGIN EXACT CONTENT ---\n")
                    .append(new String(resource.content(), StandardCharsets.UTF_8));
            if (resource.content().length == 0
                    || resource.content()[resource.content().length - 1] != '\n') {
                contract.append('\n');
            }
            contract.append("--- END EXACT CONTENT ---\n")
                    .append("--- END EXACT RESOURCE ---\n");
        }
        return contract.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readResource(String resource) throws IOException {
        try (InputStream input = ShipAttemptFactory.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing bounded Ship worker contract resource: " + resource);
            }
            return input.readAllBytes();
        }
    }

    private static <T> T read(ShipBlobStore blobs, BlobReference reference, Class<T> type)
            throws IOException {
        return ShipJson.mapper().readValue(
                blobs.readBytes(reference, ShipJson.MAX_DOCUMENT_BYTES), type);
    }

    private static String path(ShipBlobStore blobs, BlobReference reference) throws IOException {
        return reference == null ? null : blobs.verifiedPath(reference).toString();
    }

    private static void addPath(
            List<String> paths, ShipBlobStore blobs, BlobReference reference)
            throws IOException {
        if (reference != null) {
            paths.add(path(blobs, reference));
        }
    }

    private static List<String> evidencePaths(
            ShipBlobStore blobs, List<BlobReference> references)
            throws IOException {
        if (references.size() > 20_000) {
            throw new IOException("Ship request has too many evidence references");
        }
        List<String> paths = new ArrayList<>(references.size());
        for (BlobReference reference : references) {
            paths.add(path(blobs, require(reference, "evidence reference")));
        }
        if (new HashSet<>(paths).size() != paths.size()) {
            throw new IOException("Ship request contains duplicate evidence references");
        }
        return List.copyOf(paths);
    }

    private static void addBinding(List<String> fields, BlobReference reference) {
        fields.add(reference == null ? null : reference.kind());
        fields.add(reference == null ? null : reference.digest());
        fields.add(reference == null ? null : Long.toString(reference.byteSize()));
    }

    private static String string(Path value) {
        return value == null ? null : value.toString();
    }

    private static <T> T require(T value, String label) throws IOException {
        if (value == null) {
            throw new IOException("Ship request is missing its required " + label);
        }
        return value;
    }

    private static BlobReference requireKind(BlobReference reference, String kind)
            throws IOException {
        if (!kind.equals(reference.kind())) {
            throw new IOException("Ship request has the wrong protected blob kind for " + kind);
        }
        return reference;
    }

    private static BlobReference optionalKind(BlobReference reference, String kind)
            throws IOException {
        return reference == null ? null : requireKind(reference, kind);
    }

    private static List<BlobReference> requireKinds(
            List<BlobReference> references, String kind)
            throws IOException {
        for (BlobReference reference : references) {
            requireKind(reference, kind);
        }
        return references;
    }

    private static void validateFailure(String code, String message) throws IOException {
        try {
            if (!Objects.equals(code, normalizeFailureCode(code))
                    || !Objects.equals(message, normalizeFailureMessage(message))) {
                throw new IOException("Ship retry context is not canonical");
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Ship retry context is invalid", e);
        }
        if ((code == null) != (message == null)) {
            throw new IOException("Ship retry context requires both failure code and message");
        }
    }

    private static void requireRandomIdentity(
            String attemptId, ShipStage stage, int attempt)
            throws IOException {
        String prefix = stage.name().toLowerCase(Locale.ROOT) + '-' + attempt + '-';
        if (attemptId == null || !attemptId.startsWith(prefix)) {
            throw new IOException("Stage request attempt ID does not match its stage and attempt");
        }
        requireRandomToken(
                attemptId.substring(prefix.length()), 12, "Stage request attempt ID");
    }

    private static void requireRandomToken(String value, int bytes, String label)
            throws IOException {
        try {
            if (value == null
                    || !value.matches("[A-Za-z0-9_-]+")
                    || Base64.getUrlDecoder().decode(value).length != bytes) {
                throw new IOException(label + " has an invalid controller token");
            }
        } catch (IllegalArgumentException e) {
            throw new IOException(label + " has an invalid controller token", e);
        }
    }

    private static StageCapability logicalCapability(ShipStage stage, String outputDirectory) {
        RepositoryAccess access;
        List<String> writes;
        List<Operation> operations = new ArrayList<>(
                List.of(Operation.READ, Operation.SEARCH, Operation.RETURN_STRUCTURED_RESULT));
        if (stage == ShipStage.DISCOVERY || stage == ShipStage.REVIEW) {
            access = RepositoryAccess.READ_ONLY;
            writes = List.of();
        } else {
            access = stage == ShipStage.EXECUTE
                    ? RepositoryAccess.DECLARED_EXECUTION_PATHS
                    : RepositoryAccess.READ_WITH_STAGED_OUTPUT;
            writes = List.of(outputDirectory);
            operations.add(Operation.WRITE_STAGED_ARTIFACT);
        }
        // Logical blob references are deliberately not represented as filesystem read roots.
        return new StageCapability(access, List.of(), writes, operations, false, false);
    }

    private static String inputDigest(
            ShipStage stage,
            Inputs inputs,
            String workerContractReference,
            String failureCode,
            String failureMessage) {
        List<String> fields = new ArrayList<>();
        fields.add("camel-kit-ship-input-v4");
        fields.add(stage.name());
        fields.add(inputs.sourceDirectory());
        fields.add(inputs.sourceSnapshotReference());
        fields.add(inputs.projectSourceManifestReference());
        fields.add(inputs.contextReference());
        fields.add(inputs.ledgerReference());
        fields.add(inputs.catalogEvidenceReference());
        fields.add(inputs.approvedDesignReference());
        fields.add(inputs.planReference());
        fields.add(inputs.interactionReference());
        fields.add(inputs.artifactManifestReference());
        fields.add(inputs.candidateDirectory());
        fields.add(Integer.toString(inputs.evidenceReferences().size()));
        fields.addAll(inputs.evidenceReferences());
        fields.add(failureCode);
        fields.add(failureMessage);
        fields.add(workerContractReference);
        return ShipDigest.sha256(Interaction.canonicalMacBytes(fields.toArray(String[]::new)));
    }

    private static String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static void requireRunId(String runId) {
        if (runId == null || !runId.matches("ship-[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Ship worker request run ID must be canonical");
        }
    }

    private static void requireReference(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 4096 || containsAsciiControl(value)) {
            throw new IllegalArgumentException("Ship worker " + label + " is invalid");
        }
    }

    private static String requireAbsoluteNormalized(String value, String label) {
        requireReference(value, label);
        try {
            Path path = Path.of(value);
            if (!path.isAbsolute() || !path.normalize().equals(path)) {
                throw new IllegalArgumentException("Ship " + label + " must be absolute and normalized");
            }
            return path.toString();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Ship " + label + " is invalid", e);
        }
    }

    private static String normalizeFailureCode(String value) {
        if (value == null) {
            return null;
        }
        if (!value.matches("[a-z][a-z0-9-]{0,127}")) {
            throw new IllegalArgumentException("Ship worker failure code is invalid");
        }
        return value;
    }

    private static String normalizeFailureMessage(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || value.length() > 64 * 1024 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Ship worker failure message is invalid");
        }
        return value;
    }

    private static boolean containsAsciiControl(String value) {
        return value.chars().anyMatch(character -> character <= 0x1f || character == 0x7f);
    }

    private record ContractResource(String role, String path) {
    }

    private record ResourceContent(ContractResource resource, byte[] content, String digest) {

        private ResourceContent {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    record AttemptInputs(
            BlobReference context,
            BlobReference ledger,
            BlobReference catalogEvidence,
            BlobReference design,
            BlobReference plan,
            BlobReference interactionBundle,
            BlobReference artifactManifest,
            BlobReference candidateSnapshot,
            Path candidateDirectory,
            List<BlobReference> evidence,
            String failureCode,
            String failureMessage) {

        AttemptInputs {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            if (evidence.size() > 20_000
                    || evidence.stream().anyMatch(Objects::isNull)
                    || new HashSet<>(evidence).size() != evidence.size()) {
                throw new IllegalArgumentException("Projected Ship evidence references are invalid");
            }
        }

        static AttemptInputs from(ShipRunView run) {
            Objects.requireNonNull(run, "Ship run view");
            return new AttemptInputs(
                    run.context(),
                    run.ledger(),
                    run.catalogEvidence(),
                    run.design(),
                    run.plan(),
                    run.interactionBundle(),
                    run.artifactManifest(),
                    run.candidateSnapshot(),
                    run.candidateDirectory(),
                    run.evidence(),
                    run.failureCode(),
                    run.failureMessage());
        }
    }

    private record RequestInputs(
            Path sourceDirectory,
            BlobReference sourceSnapshot,
            BlobReference projectSourceManifest,
            BlobReference context,
            BlobReference ledger,
            BlobReference catalogEvidence,
            BlobReference design,
            BlobReference plan,
            BlobReference interaction,
            BlobReference artifactManifest,
            BlobReference candidateSnapshot,
            Path candidateDirectory,
            List<BlobReference> evidence) {

        private RequestInputs {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    private record SourceInputs(
            Path directory,
            BlobReference reference,
            ProjectSnapshot snapshot,
            BlobReference manifestReference) {
    }

    private record CandidateInputs(Path directory, BlobReference snapshot) {
    }

    record Inputs(
            String sourceDirectory,
            String sourceSnapshotReference,
            String projectSourceManifestReference,
            String contextReference,
            String ledgerReference,
            String catalogEvidenceReference,
            String approvedDesignReference,
            String planReference,
            String interactionReference,
            String artifactManifestReference,
            String candidateDirectory,
            List<String> evidenceReferences) {

        Inputs {
            evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
            if (evidenceReferences.size() > 1024
                    || new HashSet<>(evidenceReferences).size() != evidenceReferences.size()) {
                throw new IllegalArgumentException("Ship worker evidence references are invalid");
            }
            evidenceReferences.forEach(reference -> requireReference(reference, "evidence reference"));
            for (String reference : new String[]{
                    sourceDirectory,
                    sourceSnapshotReference,
                    projectSourceManifestReference,
                    contextReference,
                    ledgerReference,
                    catalogEvidenceReference,
                    approvedDesignReference,
                    planReference,
                    interactionReference,
                    artifactManifestReference,
                    candidateDirectory}) {
                if (reference != null) {
                    requireReference(reference, "input reference");
                }
            }
        }
    }
}

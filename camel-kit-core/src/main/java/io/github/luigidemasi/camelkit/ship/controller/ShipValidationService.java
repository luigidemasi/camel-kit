package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.RouteArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.TestArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactValidationResult;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactValidator;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord;
import io.github.luigidemasi.camelkit.ship.catalog.ShipCatalogService;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.controller.ShipEvidenceVerifier.RetainedEvidence;
import io.github.luigidemasi.camelkit.ship.evidence.CommandEvidence;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceCommand;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceRunner;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadArchive;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadRequest;
import io.github.luigidemasi.camelkit.ship.evidence.ShipJvmPayloadBootstrap;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RequirementsPolicy;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;

/** Runs and replays the closed controller-owned validation check set. */
final class ShipValidationService {

    private final EvidenceExecutor executor;
    private final Clock clock;

    ShipValidationService() {
        this(new EvidenceRunner()::run, Clock.systemUTC());
    }

    ShipValidationService(EvidenceExecutor executor, Clock clock) {
        this.executor = Objects.requireNonNull(executor, "evidence executor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    Result validate(
            ShipBlobStore blobs,
            ShipBlobStore.Transaction transaction,
            Inputs inputs)
            throws IOException {
        Objects.requireNonNull(transaction, "blob transaction");
        Objects.requireNonNull(inputs, "validation inputs");
        String runId = inputs.runId();
        Path candidate = inputs.candidate().directory();
        ProjectSnapshot candidateValue = inputs.candidate().snapshot();
        BlobReference candidateReference = inputs.candidate().reference();
        ArtifactManifest manifest = inputs.manifest().manifest();
        BlobReference manifestReference = inputs.manifest().reference();
        RequirementsPolicy policy = inputs.policy();
        CatalogUsageRecord usage = inputs.catalog().usage().usage();
        BlobReference usageReference = inputs.catalog().usage().reference();
        ShipCatalogService.Snapshot catalogSnapshot = inputs.catalog().snapshot();
        CatalogEvidenceSet approvedCatalogEvidence = inputs.catalog().approval().evidence();
        BlobReference approvedCatalogEvidenceReference = inputs.catalog().approval().reference();
        BlobReference workerValidation = inputs.workerValidation();
        Path exactCandidate = blobs.verifyCandidateDirectory(candidate);
        CatalogEvidenceValidator.validateUsage(
                catalogSnapshot,
                new CatalogEvidenceValidator.UsageBinding(
                        runId,
                        approvedCatalogEvidenceReference.digest(),
                        manifestReference.digest(),
                        candidateReference.digest(),
                        candidateValue.digest(),
                        usage.pomDigest()),
                approvedCatalogEvidence,
                manifest,
                usage);

        List<BlobReference> evidence = new ArrayList<>();
        List<ShipStamp.Check> checks = new ArrayList<>();
        ArtifactValidationResult artifactValidation = ArtifactValidator.validate(
                exactCandidate, manifest, policy);
        BlobReference artifactValidationReference = write(
                transaction, "artifact-validation", artifactValidation);
        addSimple(
                transaction, runId, "artifact-policy", true,
                artifactValidation.passed(), List.of(artifactValidationReference),
                evidence, checks);
        addSimple(
                transaction, runId, "catalog-usage", true, true,
                List.of(approvedCatalogEvidenceReference, usageReference),
                evidence, checks);

        Map<String, EvidenceCommand> expectedCommands = expectedCommands(
                manifest,
                candidateValue,
                candidateReference,
                manifestReference,
                usage,
                usageReference);
        for (String id : controllerCommandIds(manifest)) {
            EvidenceCommand expected = expectedCommands.remove(id);
            if (expected == null) {
                if (!id.matches("citrus-integration-test-[0-9]{3}")) {
                    throw new IOException(
                            "Controller failed to construct required evidence command " + id);
                }
                addSimple(
                        transaction, runId, id, true, false,
                        List.of(manifestReference), evidence, checks);
            } else {
                addCommand(
                        blobs,
                        transaction,
                        runId,
                        exactCandidate,
                        manifest,
                        expected,
                        List.of(
                                candidateReference,
                                manifestReference,
                                usageReference),
                        evidence,
                        checks);
            }
        }
        if (!expectedCommands.isEmpty()) {
            throw new IOException(
                    "Controller constructed unexpected evidence commands");
        }

        ProjectSnapshot observed = ProjectEvidenceFiles.captureSealed(exactCandidate);
        BlobReference observedReference = write(transaction, "project-snapshot", observed);
        addSimple(
                transaction, runId, "candidate-integrity", true,
                observed.equals(candidateValue),
                List.of(observedReference), evidence, checks);
        addSimple(
                transaction, runId, "bounded-validation-report", false, true,
                List.of(workerValidation), evidence, checks);

        Instant generatedAt = Instant.now(clock);
        ShipValidationReport report = new ShipValidationReport(
                ShipValidationReport.SCHEMA_VERSION,
                runId,
                manifestReference.digest(),
                candidateReference.digest(),
                usageReference.digest(),
                workerValidation,
                checks,
                evidence,
                generatedAt);
        BlobReference reportReference = write(transaction, "validation-report", report);
        List<ShipStamp.Check> failed = checks.stream()
                .filter(ShipStamp.Check::mandatory)
                .filter(check -> !check.passed())
                .toList();
        Verdict verdict = failed.isEmpty() ? Verdict.PASS
                : failed.size() == 1
                        && failed.get(0).id().matches("citrus-integration-test-[0-9]{3}")
                        ? Verdict.WAIVABLE
                : Verdict.FAIL;
        return new Result(
                verdict, report, reportReference, List.copyOf(evidence),
                verdict == Verdict.WAIVABLE ? failed.get(0) : null);
    }

    static Map<String, BlobReference> verifyReport(
            ShipBlobStore blobs,
            VerificationInputs inputs,
            ShipValidationReport report,
            List<BlobReference> evidence)
            throws IOException {
        Objects.requireNonNull(inputs, "verification inputs");
        String expectedRunId = inputs.runId();
        Path candidate = inputs.candidate().directory();
        ProjectSnapshot candidateValue = inputs.candidate().snapshot();
        BlobReference candidateReference = inputs.candidate().reference();
        ArtifactManifest manifest = inputs.manifest().manifest();
        BlobReference manifestReference = inputs.manifest().reference();
        RequirementsPolicy policy = inputs.policy();
        CatalogUsageRecord usage = inputs.usage().usage();
        BlobReference usageReference = inputs.usage().reference();
        BlobReference workerValidation = inputs.workerValidation();
        Path exactCandidate = blobs.verifyCandidateDirectory(candidate);

        if (report == null
                || !expectedRunId.equals(report.runId())
                || !manifestReference.digest()
                        .equals(report.artifactManifestDigest())
                || !candidateReference.digest()
                        .equals(report.candidateSnapshotDigest())
                || !usageReference.digest()
                        .equals(report.catalogUsageDigest())
                || !workerValidation.equals(report.workerValidation())
                || !evidence.equals(report.evidence())
                || !manifestReference.digest()
                        .equals(usage.artifactManifestDigest())
                || !candidateReference.digest()
                        .equals(usage.candidateSnapshotDigest())
                || !candidateValue.digest()
                        .equals(usage.candidateContentDigest())) {
            throw new IOException("Validation report differs from exact durable run inputs");
        }

        ArtifactValidationResult expectedArtifactValidation
                = ArtifactValidator.validate(exactCandidate, manifest, policy);
        ProjectSnapshot currentCandidate = ProjectEvidenceFiles.captureSealed(exactCandidate);
        Map<String, EvidenceCommand> expectedCommands = expectedCommands(
                manifest,
                candidateValue,
                candidateReference,
                manifestReference,
                usage,
                usageReference);

        Map<String, BlobReference> byId = new LinkedHashMap<>();
        for (int index = 0; index < evidence.size(); index++) {
            BlobReference reference = evidence.get(index);
            if (!"ship-check-evidence".equals(reference.kind())) {
                throw new IOException("Validation evidence has an invalid blob kind");
            }
            ShipCheckEvidence value = ShipJson.mapper().readValue(
                    blobs.readBytes(reference, ShipJson.MAX_DOCUMENT_BYTES),
                    ShipCheckEvidence.class);
            ShipStamp.Check check = report.checks().get(index);
            if (!report.runId().equals(value.runId())
                    || !check.id().equals(value.checkId())
                    || check.mandatory() != value.mandatory()
                    || check.passed() != value.passed()
                    || !reference.digest().equals(check.evidenceDigest())
                    || byId.putIfAbsent(check.id(), reference) != null) {
                throw new IOException("Validation check differs from retained evidence");
            }
            for (BlobReference subject : value.subjects()) {
                blobs.verify(subject);
            }

            boolean expectedPassed;
            switch (check.id()) {
                case "artifact-policy" -> {
                    requireSimple(value);
                    if (value.subjects().size() != 1
                            || !"artifact-validation".equals(
                                    value.subjects().get(0).kind())) {
                        throw new IOException(
                                "Artifact-policy evidence has invalid subjects");
                    }
                    ArtifactValidationResult retained = ShipJson.mapper().readValue(
                            blobs.readBytes(
                                    value.subjects().get(0),
                                    ShipJson.MAX_DOCUMENT_BYTES),
                            ArtifactValidationResult.class);
                    if (!expectedArtifactValidation.equals(retained)) {
                        throw new IOException(
                                "Retained artifact validation differs from deterministic replay");
                    }
                    expectedPassed = expectedArtifactValidation.passed();
                }
                case "catalog-usage" -> {
                    requireSimple(value);
                    if (value.subjects().size() != 2
                            || !"catalog-evidence".equals(
                                    value.subjects().get(0).kind())
                            || !usage.catalogEvidenceDigest().equals(
                                    value.subjects().get(0).digest())
                            || !usageReference.equals(
                                    value.subjects().get(1))) {
                        throw new IOException(
                                "Catalog-usage evidence has invalid subjects");
                    }
                    expectedPassed = true;
                }
                case "candidate-integrity" -> {
                    requireSimple(value);
                    if (value.subjects().size() != 1
                            || !"project-snapshot".equals(
                                    value.subjects().get(0).kind())) {
                        throw new IOException(
                                "Candidate-integrity evidence has invalid subjects");
                    }
                    ProjectSnapshot retained = ShipJson.mapper().readValue(
                            blobs.readBytes(
                                    value.subjects().get(0),
                                    ShipJson.MAX_DOCUMENT_BYTES),
                            ProjectSnapshot.class);
                    if (!currentCandidate.equals(retained)) {
                        throw new IOException(
                                "Retained candidate observation differs from current sealed candidate");
                    }
                    expectedPassed = currentCandidate.equals(candidateValue);
                }
                case "bounded-validation-report" -> {
                    requireSimple(value);
                    if (!value.subjects().equals(List.of(workerValidation))) {
                        throw new IOException(
                                "Worker validation evidence has invalid subjects");
                    }
                    expectedPassed = true;
                }
                default -> {
                    EvidenceCommand expected = expectedCommands.remove(check.id());
                    if (expected == null) {
                        if (!isExpectedStructuralCitrusFailure(
                                check.id(),
                                manifest,
                                usage,
                                manifestReference,
                                value)) {
                            throw new IOException(
                                    "Validation report contains an unknown check");
                        }
                        expectedPassed = false;
                    } else {
                        if (!expected.equals(value.command())
                                || !value.subjects().equals(List.of(
                                        candidateReference,
                                        manifestReference,
                                        usageReference))) {
                            throw new IOException(
                                    "Command evidence differs from controller-selected inputs");
                        }
                        expectedPassed = ShipEvidenceVerifier.verify(
                                blobs,
                                exactCandidate,
                                manifest,
                                expected,
                                value.commandEvidence(),
                                value.retainedEvidence());
                    }
                }
            }

            if (check.passed() != expectedPassed
                    || value.passed() != expectedPassed) {
                throw new IOException(
                        "Validation check result differs from deterministic replay");
            }
        }

        if (!expectedCommands.isEmpty()
                || !new ArrayList<>(byId.keySet())
                        .equals(expectedCheckIds(manifest))) {
            throw new IOException(
                    "Validation report omits or reorders controller-required checks");
        }
        return Map.copyOf(byId);
    }

    private void addCommand(
            ShipBlobStore blobs,
            ShipBlobStore.Transaction transaction,
            String runId,
            Path candidate,
            ArtifactManifest manifest,
            EvidenceCommand command,
            List<BlobReference> subjects,
            List<BlobReference> evidence,
            List<ShipStamp.Check> checks)
            throws IOException {
        Path root = blobs.privateWorkDirectory("evidence");
        Path directory = root.resolve(command.id() + '-' + UUID.randomUUID());
        CommandEvidence commandEvidence = null;
        boolean collected = false;
        Throwable pending = null;
        try {
            try {
                commandEvidence = Objects.requireNonNull(
                        executor.run(candidate, directory, command),
                        "Evidence executor returned no command evidence");
                collected = true;
            } catch (IOException | RuntimeException failure) {
                commandEvidence = failedEvidence(
                        transaction,
                        candidate,
                        command,
                        failure.getClass().getSimpleName()
                                 + ": " + safeMessage(failure));
            }

            RetainedEvidence retained = collected
                    ? retain(transaction, commandEvidence)
                    : failedRetained(transaction);
            boolean passed = ShipEvidenceVerifier.verify(
                    blobs,
                    candidate,
                    manifest,
                    command,
                    commandEvidence,
                    retained);
            ShipCheckEvidence value = new ShipCheckEvidence(
                    ShipCheckEvidence.SCHEMA_VERSION,
                    runId,
                    command.id(),
                    true,
                    passed,
                    subjects,
                    command,
                    commandEvidence,
                    retained);
            BlobReference reference = write(transaction, "ship-check-evidence", value);
            evidence.add(reference);
            checks.add(new ShipStamp.Check(
                    command.id(),
                    true,
                    passed,
                    reference.digest()));
        } catch (IOException | RuntimeException | Error failure) {
            pending = failure;
            throw failure;
        } finally {
            try {
                if (collected) {
                    EvidenceRunner.cleanupEphemeral(
                            directory, command, commandEvidence);
                } else {
                    EvidenceRunner.cleanupAbandoned(directory, command);
                }
            } catch (IOException cleanupFailure) {
                if (pending != null) {
                    pending.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private static void addSimple(
            ShipBlobStore.Transaction transaction,
            String runId,
            String id,
            boolean mandatory,
            boolean passed,
            List<BlobReference> subjects,
            List<BlobReference> evidence,
            List<ShipStamp.Check> checks)
            throws IOException {
        ShipCheckEvidence value = new ShipCheckEvidence(
                ShipCheckEvidence.SCHEMA_VERSION,
                runId, id, mandatory, passed, subjects, null, null, null);
        BlobReference reference = write(transaction, "ship-check-evidence", value);
        evidence.add(reference);
        checks.add(new ShipStamp.Check(id, mandatory, passed, reference.digest()));
    }

    private static RetainedEvidence retain(
            ShipBlobStore.Transaction transaction, CommandEvidence evidence)
            throws IOException {
        BlobReference stdout = transaction.importControllerFile(
                "evidence-stdout", Path.of(evidence.stdoutLog()), evidence.stdoutDigest());
        BlobReference stderr = transaction.importControllerFile(
                "evidence-stderr", Path.of(evidence.stderrLog()), evidence.stderrDigest());
        BlobReference sandbox = evidence.sandbox() == null
                ? null
                : transaction.importControllerFile(
                        "sandbox-executable", Path.of(evidence.sandbox().executable()),
                        evidence.sandbox().executableDigest());
        BlobReference toolchain = evidence.toolchainSnapshot() == null
                ? null
                : transaction.importControllerFile(
                        "toolchain-archive", Path.of(evidence.toolchainSnapshot()),
                        evidence.toolchainSnapshotDigest());
        BlobReference executable = evidence.executable() == null
                ? null
                : transaction.importControllerFile(
                        "command-executable", Path.of(evidence.executable()),
                        evidence.executableDigest());
        return new RetainedEvidence(stdout, stderr, sandbox, toolchain, executable);
    }

    private static CommandEvidence failedEvidence(
            ShipBlobStore.Transaction transaction,
            Path candidate,
            EvidenceCommand command,
            String message)
            throws IOException {
        BlobReference empty = transaction.writeBytes("evidence-log", new byte[0]);
        Instant now = Instant.EPOCH;
        return new CommandEvidence(
                CommandEvidence.SCHEMA_VERSION,
                command.id(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                command.arguments(),
                candidate.toString(),
                Map.of(),
                now,
                now,
                false,
                false,
                null,
                message == null || message.isBlank() ? "evidence setup failed" : message,
                "cas:" + empty.digest(),
                empty.digest(),
                "cas:" + empty.digest(),
                empty.digest(),
                command.inputDigests());
    }

    private static RetainedEvidence failedRetained(ShipBlobStore.Transaction transaction)
            throws IOException {
        BlobReference empty = transaction.writeBytes("evidence-log", new byte[0]);
        return new RetainedEvidence(empty, empty, null, null, null);
    }

    private static Map<String, EvidenceCommand> expectedCommands(
            ArtifactManifest manifest,
            ProjectSnapshot candidateValue,
            BlobReference candidateReference,
            BlobReference manifestReference,
            CatalogUsageRecord usage,
            BlobReference usageReference) {
        List<RouteArtifact> routes = canonicalRoutes(manifest);
        List<TestArtifact> tests = canonicalTests(manifest);
        List<String> commonInputs = List.of(
                candidateReference.digest(),
                manifestReference.digest(),
                usageReference.digest());

        Map<String, EvidenceCommand> result = new LinkedHashMap<>();

        JvmPayloadRequest yaml = JvmPayloadRequest.yamlValidator(manifest.camelVersion());
        result.put(
                "route-schema",
                command(
                        "route-schema",
                        Duration.ofMinutes(5),
                        yaml,
                        routes.stream()
                                .map(route -> "/workspace/" + route.path())
                                .toList(),
                        commonInputs));

        JvmPayloadRequest main = JvmPayloadRequest.camelMain(
                manifest.camelVersion(), usage.runtimeDependencies());
        List<String> packageArguments = new ArrayList<>(
                List.of(
                        "--candidate-digest=" + candidateValue.digest(),
                        "--manifest-digest=" + manifestReference.digest(),
                        "--catalog-usage-digest=" + usageReference.digest(),
                        "--pom-digest=" + usage.pomDigest(),
                        "--main-payload-digest=" + main.digest()));
        for (RouteArtifact route : routes) {
            packageArguments.add("--route=" + route.path());
            packageArguments.add("--route-digest=" + route.digest());
        }
        result.put(
                "main-package-and-inspect",
                command(
                        "main-package-and-inspect",
                        Duration.ofMinutes(5),
                        JvmPayloadRequest.mainPackage(manifest.camelVersion()),
                        packageArguments,
                        commonInputs));

        List<String> runtimeArguments = new ArrayList<>();
        for (RouteArtifact route : routes) {
            runtimeArguments.add("--route=/workspace/" + route.path());
            runtimeArguments.add("--expected-route=" + route.routeId());
        }
        result.put(
                "main-runtime-resolve-and-start",
                command(
                        "main-runtime-resolve-and-start",
                        Duration.ofMinutes(10),
                        main,
                        runtimeArguments,
                        commonInputs));

        if (tests.isEmpty()) {
            return result;
        }

        JvmPayloadRequest citrus;
        try {
            citrus = JvmPayloadRequest.citrus(
                    manifest.camelVersion(),
                    manifest.citrusVersion(),
                    manifest.citrusDependencies(),
                    usage.runtimeDependencies());
        } catch (IllegalArgumentException invalidPayload) {
            return result;
        }

        for (int index = 0; index < tests.size(); index++) {
            TestArtifact test = tests.get(index);
            List<RouteArtifact> matchingRoutes = routes.stream()
                    .filter(route -> test.routeId().equals(route.routeId()))
                    .limit(2)
                    .toList();
            if (matchingRoutes.size() != 1) {
                continue;
            }
            RouteArtifact route = matchingRoutes.get(0);
            String id = String.format(
                    Locale.ROOT,
                    "citrus-integration-test-%03d",
                    index + 1);
            result.put(
                    id,
                    command(
                            id,
                            Duration.ofMinutes(10),
                            citrus,
                            List.of(
                                    "--route=/workspace/" + route.path(),
                                    "--expected-route=" + route.routeId(),
                                    "--test=/workspace/" + test.path()),
                            commonInputs));
        }
        return result;
    }

    private static List<RouteArtifact> canonicalRoutes(
            ArtifactManifest manifest) {
        return manifest.routes().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RouteArtifact::path)
                        .thenComparing(RouteArtifact::routeId))
                .toList();
    }

    private static List<TestArtifact> canonicalTests(
            ArtifactManifest manifest) {
        return manifest.citrusTests().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TestArtifact::path)
                        .thenComparing(TestArtifact::routeId))
                .toList();
    }

    private static List<String> controllerCommandIds(
            ArtifactManifest manifest) {
        List<String> result = new ArrayList<>(
                List.of(
                        "route-schema",
                        "main-package-and-inspect",
                        "main-runtime-resolve-and-start"));
        List<TestArtifact> tests = canonicalTests(manifest);
        int citrusChecks = Math.max(1, tests.size());
        for (int index = 0; index < citrusChecks; index++) {
            result.add(String.format(
                    Locale.ROOT,
                    "citrus-integration-test-%03d",
                    index + 1));
        }
        return List.copyOf(result);
    }

    private static List<String> expectedCheckIds(
            ArtifactManifest manifest) {
        List<String> result = new ArrayList<>(
                List.of(
                        "artifact-policy",
                        "catalog-usage"));
        result.addAll(controllerCommandIds(manifest));
        result.add("candidate-integrity");
        result.add("bounded-validation-report");
        return List.copyOf(result);
    }

    private static boolean isExpectedStructuralCitrusFailure(
            String id,
            ArtifactManifest manifest,
            CatalogUsageRecord usage,
            BlobReference manifestReference,
            ShipCheckEvidence value) {
        if (!id.matches("citrus-integration-test-[0-9]{3}")
                || value.command() != null
                || value.commandEvidence() != null
                || value.retainedEvidence() != null
                || !value.mandatory()
                || value.passed()
                || !value.subjects().equals(List.of(manifestReference))) {
            return false;
        }

        List<TestArtifact> tests = canonicalTests(manifest);
        if (tests.isEmpty()) {
            return "citrus-integration-test-001".equals(id);
        }

        int selected = -1;
        for (int index = 0; index < tests.size(); index++) {
            String expected = String.format(
                    Locale.ROOT,
                    "citrus-integration-test-%03d",
                    index + 1);
            if (expected.equals(id)) {
                selected = index;
                break;
            }
        }
        if (selected < 0) {
            return false;
        }

        TestArtifact test = tests.get(selected);
        long matchingRoutes = canonicalRoutes(manifest).stream()
                .filter(route -> test.routeId().equals(route.routeId()))
                .limit(2)
                .count();
        if (matchingRoutes != 1) {
            return true;
        }

        try {
            JvmPayloadRequest.citrus(
                    manifest.camelVersion(),
                    manifest.citrusVersion(),
                    manifest.citrusDependencies(),
                    usage.runtimeDependencies());
            return false;
        } catch (IllegalArgumentException invalidPayload) {
            return true;
        }
    }

    private static void requireSimple(ShipCheckEvidence value)
            throws IOException {
        if (value.command() != null
                || value.commandEvidence() != null
                || value.retainedEvidence() != null) {
            throw new IOException(
                    "Non-command validation evidence contains command authority");
        }
    }

    private static EvidenceCommand command(
            String id,
            Duration timeout,
            JvmPayloadRequest payload,
            List<String> launcherArguments,
            List<String> inputDigests) {
        List<String> base = List.of(
                EvidenceRunner.JAVA_EXECUTABLE,
                "-cp",
                JvmPayloadArchive.SANDBOX_ARCHIVE,
                ShipJvmPayloadBootstrap.class.getName());
        List<String> arguments = new ArrayList<>(base);
        arguments.addAll(launcherArguments);
        List<String> version = new ArrayList<>(base);
        version.add("--payload-version");
        return new EvidenceCommand(
                id, arguments, version, null, timeout, inputDigests,
                Map.of("LANG", "C", "LC_ALL", "C"),
                List.of(), null, payload);
    }

    private static <T> BlobReference write(
            ShipBlobStore.Transaction transaction, String kind, T value)
            throws IOException {
        return transaction.writeBytes(kind, ShipJson.mapper().writeValueAsBytes(value));
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "unreported" : message;
    }

    record Inputs(
            String runId,
            CandidateInput candidate,
            ManifestInput manifest,
            RequirementsPolicy policy,
            CatalogInput catalog,
            BlobReference workerValidation) {

        Inputs {
            if (runId == null || !runId.matches("ship-[0-9a-f]{32}")) {
                throw new IllegalArgumentException("Validation inputs require a canonical run ID");
            }
            Objects.requireNonNull(candidate, "candidate input");
            Objects.requireNonNull(manifest, "manifest input");
            Objects.requireNonNull(policy, "requirements policy");
            Objects.requireNonNull(catalog, "catalog input");
            requireInputReference(workerValidation, "validation", "worker validation");
        }
    }

    record VerificationInputs(
            String runId,
            CandidateInput candidate,
            ManifestInput manifest,
            RequirementsPolicy policy,
            UsageInput usage,
            BlobReference workerValidation) {

        VerificationInputs {
            if (runId == null || !runId.matches("ship-[0-9a-f]{32}")) {
                throw new IllegalArgumentException(
                        "Report verification inputs require a canonical run ID");
            }
            Objects.requireNonNull(candidate, "candidate input");
            Objects.requireNonNull(manifest, "manifest input");
            Objects.requireNonNull(policy, "requirements policy");
            Objects.requireNonNull(usage, "catalog usage input");
            requireInputReference(workerValidation, "validation", "worker validation");
        }
    }

    record CandidateInput(
            Path directory, ProjectSnapshot snapshot, BlobReference reference) {

        CandidateInput {
            Objects.requireNonNull(directory, "candidate directory");
            Objects.requireNonNull(snapshot, "candidate snapshot");
            requireInputReference(reference, "project-snapshot", "candidate snapshot");
        }
    }

    record ManifestInput(ArtifactManifest manifest, BlobReference reference) {

        ManifestInput {
            Objects.requireNonNull(manifest, "artifact manifest");
            requireInputReference(reference, "artifact-manifest", "artifact manifest");
        }
    }

    record UsageInput(CatalogUsageRecord usage, BlobReference reference) {

        UsageInput {
            Objects.requireNonNull(usage, "catalog usage");
            requireInputReference(reference, "catalog-usage", "catalog usage");
        }
    }

    record ApprovalInput(CatalogEvidenceSet evidence, BlobReference reference) {

        ApprovalInput {
            Objects.requireNonNull(evidence, "approved catalog evidence");
            requireInputReference(reference, "catalog-evidence", "approved catalog evidence");
        }
    }

    record CatalogInput(
            UsageInput usage,
            ShipCatalogService.Snapshot snapshot,
            ApprovalInput approval) {

        CatalogInput {
            Objects.requireNonNull(usage, "catalog usage input");
            Objects.requireNonNull(snapshot, "catalog snapshot");
            Objects.requireNonNull(approval, "catalog approval input");
        }
    }

    private static void requireInputReference(
            BlobReference reference, String kind, String label) {
        if (reference == null || !kind.equals(reference.kind())) {
            throw new IllegalArgumentException(
                    "Validation " + label + " reference must have kind " + kind);
        }
    }

    @FunctionalInterface
    interface EvidenceExecutor {
        CommandEvidence run(Path candidate, Path evidenceDirectory, EvidenceCommand command)
                throws IOException;
    }

    enum Verdict {
        PASS,
        WAIVABLE,
        FAIL
    }

    record Result(
            Verdict verdict,
            ShipValidationReport report,
            BlobReference reportReference,
            List<BlobReference> evidence,
            ShipStamp.Check failedCheck) {
    }
}

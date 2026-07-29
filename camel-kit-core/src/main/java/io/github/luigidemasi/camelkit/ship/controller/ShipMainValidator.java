package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifestReader;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifestReader.Document;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactPolicy;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactValidationResult;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactValidator;
import io.github.luigidemasi.camelkit.ship.catalog.CamelYamlCatalogUsageExtractor;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogComponentModel;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject.Kind;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTarget;
import io.github.luigidemasi.camelkit.ship.catalog.ShipCatalogService;
import io.github.luigidemasi.camelkit.ship.evidence.CommandEvidence;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceCommand;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceRunner;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadArchive;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadRequest;
import io.github.luigidemasi.camelkit.ship.evidence.ShipJvmPayloadBootstrap;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Check;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.CommandRun;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Outcome;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.ToolVersion;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStampStore;
import io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipMainPackageMain;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;

/** Composes the deterministic controller checks currently available for Camel Main. */
final class ShipMainValidator {

    /** Bump whenever accepted manifest or mandatory validation semantics change. */
    static final int CONTRACT_VERSION = 1;
    static final int MAX_EVIDENCE_COMMANDS = 64;
    static final Duration MAX_EVIDENCE_TIMEOUT_BUDGET = Duration.ofHours(12);

    private static final int DIRECT_JVM_PREFIX_SIZE = 4;
    private static final int MAX_LOG_BYTES = 64 * 1024 * 1024;
    private static final long MAX_TOTAL_LOG_BYTES = 64L * 1024 * 1024;
    private static final long MAX_EXECUTABLE_BYTES = 128L * 1024 * 1024;
    private static final Map<String, String> LOCALE = Map.of("LC_ALL", "C", "LANG", "C");

    private final EvidenceExecutor executor;

    ShipMainValidator() {
        this(new EvidenceExecutor() {
            private final EvidenceRunner runner = new EvidenceRunner();

            @Override
            public CommandEvidence run(Path candidate, Path directory, EvidenceCommand command)
                    throws IOException {
                return runner.run(candidate, directory, command);
            }

            @Override
            public void cleanup(Path directory, EvidenceCommand command, CommandEvidence evidence)
                    throws IOException {
                EvidenceRunner.cleanupEphemeral(directory, command, evidence);
            }
        });
    }

    ShipMainValidator(EvidenceExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "evidence executor must not be null");
    }

    Result validate(
            String runId,
            Path candidateRoot,
            Path manifestPath,
            ArtifactPolicy policy,
            ShipCatalogService.Snapshot catalog,
            Path evidenceRoot,
            ToolVersion pi,
            Clock clock)
            throws IOException, InterruptedException {
        requireNotInterrupted();
        Objects.requireNonNull(policy, "artifact policy must not be null");
        Objects.requireNonNull(catalog, "catalog snapshot must not be null");
        Objects.requireNonNull(pi, "Pi tool version must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        Path evidence = privateDirectory(evidenceRoot);
        Path candidate = realDirectory(candidateRoot, "candidate root");
        List<Check> checks = new ArrayList<>();

        ArtifactManifest manifest;
        String manifestDigest;
        try {
            Document document = ArtifactManifestReader.read(
                    candidate, manifestPath);
            manifest = document.manifest();
            manifestDigest = document.digest();
        } catch (IOException | RuntimeException e) {
            checks.add(check("artifact-policy", Outcome.FAIL,
                    summary("Artifact manifest was rejected", e), null));
            checks.add(skipped("catalog-usage", "Catalog validation requires an accepted artifact manifest"));
            return persist(runId, pi, checks, evidence, clock);
        }

        ArtifactValidationResult artifacts;
        boolean main;
        try {
            artifacts = ArtifactValidator.validate(candidate, manifest, policy);
            main = "main".equals(policy.runtime()) && "main".equals(manifest.runtime());
        } catch (RuntimeException e) {
            checks.add(check("artifact-policy", Outcome.FAIL,
                    summary("Artifact validation failed", e), null));
            checks.add(skipped("catalog-usage", "Catalog validation requires accepted artifacts"));
            return persist(runId, pi, checks, evidence, clock);
        }
        if (!artifacts.passed() || !main) {
            String detail = !main
                    ? "Only the Camel Main runtime has deterministic controller evidence"
                    : finding("Artifact validation failed", artifacts);
            checks.add(check("artifact-policy", Outcome.FAIL, detail, null));
            checks.add(skipped("catalog-usage", "Catalog validation requires accepted artifacts"));
            return persist(runId, pi, checks, evidence, clock);
        }
        checks.add(check("artifact-policy", Outcome.PASS,
                "Artifacts match the independent Camel Main policy", null));

        CatalogClosure closure;
        try {
            closure = catalog(candidate, manifest, policy, catalog);
        } catch (IOException | RuntimeException e) {
            checks.add(check("catalog-usage", Outcome.FAIL,
                    summary("Catalog validation failed", e), null));
            return persist(runId, pi, checks, evidence, clock);
        }
        if (!closure.binding().validation().passed() || closure.binding().pomDigest() == null) {
            checks.add(check("catalog-usage", Outcome.FAIL,
                    finding("Catalog dependency validation failed", closure.binding().validation()), null));
            return persist(runId, pi, checks, evidence, clock);
        }
        checks.add(check("catalog-usage", Outcome.PASS,
                "Route usage and runtime dependencies match the frozen catalog", null));

        String candidateDigest;
        List<EvidenceCommand> commands;
        try {
            candidateDigest = ProjectEvidenceFiles.capture(candidate).digest();
            commands = commands(
                    manifest,
                    manifestDigest,
                    candidateDigest,
                    closure.evidence().digest(),
                    closure.binding());
        } catch (IOException | RuntimeException e) {
            checks.add(check("evidence-plan", Outcome.FAIL,
                    summary("Evidence plan was rejected", e), null));
            return persist(runId, pi, checks, evidence, clock);
        }

        for (EvidenceCommand command : commands) {
            checks.add(execute(candidate, evidence, manifest, command));
        }
        return persist(runId, pi, checks, evidence, clock);
    }

    private Check execute(
            Path candidate,
            Path evidenceRoot,
            ArtifactManifest manifest,
            EvidenceCommand command)
            throws InterruptedException {
        Path directory = evidenceRoot.resolve(command.id());
        CommandEvidence raw = null;
        try {
            requireNotInterrupted(directory, command, null);
            raw = executor.run(candidate, directory, command);
            requireNotInterrupted(directory, command, raw);
            VerifiedEvidence verified = verify(candidate, evidenceRoot, manifest, command, raw);
            Check result = outcome(command.id(), raw, verified.command());
            try {
                executor.cleanup(directory, command, raw);
            } catch (IOException e) {
                return check(command.id(), Outcome.FAIL,
                        summary("Evidence cleanup failed", e), verified.command());
            }
            requireNotInterrupted(directory, command, null);
            return result;
        } catch (IOException | RuntimeException e) {
            requireNotInterrupted(directory, command, raw);
            if (raw != null) {
                try {
                    executor.cleanup(directory, command, raw);
                } catch (IOException cleanup) {
                    e.addSuppressed(cleanup);
                }
            }
            return check(command.id(), Outcome.FAIL, summary("Command evidence was rejected", e), null);
        }
    }

    private void requireNotInterrupted(
            Path directory, EvidenceCommand command, CommandEvidence evidence)
            throws InterruptedException {
        if (!Thread.currentThread().isInterrupted()) {
            return;
        }
        Thread.interrupted();
        InterruptedException interrupted = new InterruptedException(
                "Interrupted while collecting deterministic Ship evidence");
        try {
            if (evidence != null) {
                executor.cleanup(directory, command, evidence);
            }
        } catch (IOException cleanup) {
            interrupted.addSuppressed(cleanup);
        } finally {
            Thread.currentThread().interrupt();
        }
        throw interrupted;
    }

    private static CatalogClosure catalog(
            Path candidate,
            ArtifactManifest manifest,
            ArtifactPolicy policy,
            ShipCatalogService.Snapshot snapshot)
            throws IOException {
        CatalogTarget expected = new CatalogTarget(
                policy.runtime(),
                policy.camelVersion(),
                policy.platformVersion(),
                policy.springBootVersion());
        if (!expected.equals(snapshot.target())) {
            throw new IOException("Catalog snapshot target differs from the independent artifact policy");
        }

        CamelYamlCatalogUsageExtractor extractor = new CamelYamlCatalogUsageExtractor();
        List<CatalogSubject> available = snapshot.availableSubjects();
        List<ArtifactManifest.RouteArtifact> routes = orderedRoutes(manifest);
        TreeSet<CatalogSubject> subjects = new TreeSet<>();
        for (ArtifactManifest.RouteArtifact route : routes) {
            CamelYamlCatalogUsageExtractor.Extraction extraction
                    = extractor.extractBound(candidate.resolve(route.path()), available);
            requireRouteDigest(route, extraction);
            subjects.addAll(extraction.subjects());
        }

        List<CatalogSubject> components = subjects.stream()
                .filter(subject -> subject.kind() == Kind.COMPONENT)
                .toList();
        List<CatalogComponentModel> models = snapshot.componentModelsFor(components);
        TreeSet<CatalogSubject> boundSubjects = new TreeSet<>();
        for (ArtifactManifest.RouteArtifact route : routes) {
            CamelYamlCatalogUsageExtractor.Extraction extraction
                    = extractor.extractBound(candidate.resolve(route.path()), available, models);
            requireRouteDigest(route, extraction);
            boundSubjects.addAll(extraction.subjects());
        }
        if (!subjects.equals(boundSubjects)) {
            throw new IOException("Catalog subjects changed between extraction passes");
        }

        CatalogEvidenceSet evidence = snapshot.evidenceFor(subjects);
        ArtifactValidator.CatalogDependencyBinding binding
                = ArtifactValidator.bindCatalogDependencies(candidate, policy, evidence);
        return new CatalogClosure(evidence, binding);
    }

    private static List<EvidenceCommand> commands(
            ArtifactManifest manifest,
            String manifestDigest,
            String candidateDigest,
            String catalogDigest,
            ArtifactValidator.CatalogDependencyBinding binding) {
        validateBudget(manifest);
        JvmPayloadRequest mainPayload = JvmPayloadRequest.camelMain(
                manifest.camelVersion(), binding.runtimeDependencies());
        List<String> inputs = List.of(
                manifestDigest,
                candidateDigest,
                catalogDigest,
                binding.pomDigest(),
                mainPayload.digest());
        List<ArtifactManifest.RouteArtifact> routes = orderedRoutes(manifest);
        List<EvidenceCommand> result = new ArrayList<>();

        List<String> routeArguments = directJvmArguments();
        routes.forEach(route -> routeArguments.add("/workspace/" + route.path()));
        result.add(command(
                "route-schema",
                routeArguments,
                Duration.ofMinutes(10),
                inputs,
                JvmPayloadRequest.yamlValidator(manifest.camelVersion())));

        List<String> packageArguments = directJvmArguments();
        packageArguments.add("--candidate-digest=" + candidateDigest);
        packageArguments.add("--manifest-digest=" + manifestDigest);
        packageArguments.add("--catalog-usage-digest=" + catalogDigest);
        packageArguments.add("--pom-digest=" + binding.pomDigest());
        packageArguments.add("--main-payload-digest=" + mainPayload.digest());
        routes.forEach(route -> {
            packageArguments.add("--route=" + route.path());
            packageArguments.add("--route-digest=" + route.digest());
        });
        result.add(command(
                "main-package-and-inspect",
                packageArguments,
                Duration.ofMinutes(10),
                inputs,
                JvmPayloadRequest.mainPackage(manifest.camelVersion())));

        List<String> mainArguments = directJvmArguments();
        routes.forEach(route -> {
            mainArguments.add("--route=/workspace/" + route.path());
            mainArguments.add("--expected-route=" + route.routeId());
        });
        result.add(command(
                "main-runtime-resolve-and-start",
                mainArguments,
                Duration.ofMinutes(10),
                inputs,
                mainPayload));

        List<ArtifactManifest.TestArtifact> tests = manifest.citrusTests().stream()
                .sorted(Comparator.comparing(ArtifactManifest.TestArtifact::path)
                        .thenComparing(ArtifactManifest.TestArtifact::routeId))
                .toList();
        for (int index = 0; index < tests.size(); index++) {
            ArtifactManifest.TestArtifact test = tests.get(index);
            ArtifactManifest.RouteArtifact route = routes.stream()
                    .filter(candidate -> candidate.routeId().equals(test.routeId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Citrus test has no accepted route: " + test.routeId()));
            List<String> arguments = directJvmArguments();
            arguments.add("--route=/workspace/" + route.path());
            arguments.add("--expected-route=" + route.routeId());
            arguments.add("--test=/workspace/" + test.path());
            result.add(command(
                    String.format(java.util.Locale.ROOT, "citrus-integration-test-%03d", index + 1),
                    arguments,
                    Duration.ofMinutes(30),
                    inputs,
                    JvmPayloadRequest.citrus(
                            manifest.camelVersion(),
                            manifest.citrusVersion(),
                            manifest.citrusDependencies(),
                            binding.runtimeDependencies())));
        }
        return List.copyOf(result);
    }

    private static EvidenceCommand command(
            String id,
            List<String> arguments,
            Duration timeout,
            List<String> inputs,
            JvmPayloadRequest payload) {
        List<String> versionArguments = directJvmArguments();
        versionArguments.add("--payload-version");
        return new EvidenceCommand(
                id,
                arguments,
                versionArguments,
                null,
                timeout,
                inputs,
                LOCALE,
                null,
                null,
                payload);
    }

    private static VerifiedEvidence verify(
            Path candidate,
            Path evidenceRoot,
            ArtifactManifest manifest,
            EvidenceCommand expected,
            CommandEvidence evidence)
            throws IOException {
        Path commandRoot = realDirectory(
                evidenceRoot.resolve(expected.id()), "command evidence root");
        if (evidence == null
                || evidence.schemaVersion() != CommandEvidence.SCHEMA_VERSION
                || !expected.id().equals(evidence.commandId())
                || !expected.arguments().equals(evidence.arguments())
                || !expected.inputDigests().equals(evidence.inputDigests())
                || !candidate.equals(path(evidence.workingDirectory(), "working directory"))
                || evidence.startedAt() == null
                || evidence.endedAt() == null
                || evidence.endedAt().isBefore(evidence.startedAt())) {
            throw new IOException("Command evidence identity differs from the controller plan");
        }

        String jdkDigest = evidence.controlledEnvironment().get("CAMEL_KIT_JDK_DIGEST");
        if (!ShipDigest.isSha256(jdkDigest)
                || !EvidenceRunner.expectedEnvironment(expected, jdkDigest)
                        .equals(evidence.controlledEnvironment())) {
            throw new IOException("Command evidence environment differs from controller policy");
        }
        CommandEvidence.SandboxIdentity sandbox = evidence.sandbox();
        if (sandbox == null
                || !EvidenceRunner.SANDBOX_PROVIDER.equals(sandbox.provider())
                || !sandbox.intact()
                || !ShipDigest.isSha256(sandbox.executableDigest())
                || !EvidenceRunner.expectedSandboxProfileDigest(expected, jdkDigest)
                        .equals(sandbox.profileDigest())) {
            throw new IOException("Command evidence did not use the expected intact sandbox");
        }
        requireExecutableDigest(
                sandbox.executable(), sandbox.executableDigest(), "sandbox executable");
        if (!ShipDigest.isSha256(evidence.toolchainDigest())
                || !evidence.toolchainDigest().equals(evidence.postToolchainDigest())
                || !ShipDigest.isSha256(evidence.toolchainSnapshotDigest())
                || !ShipDigest.isSha256(evidence.executableDigest())
                || !evidence.executableDigest().equals(evidence.postExecutableDigest())) {
            throw new IOException("Command execution authority changed during collection");
        }
        requireContained(
                commandRoot,
                requireExecutableDigest(
                        evidence.executable(), evidence.executableDigest(), "command executable"),
                "command executable");
        Path toolchain = path(evidence.toolchainSnapshot(), "toolchain snapshot");
        requireContained(commandRoot, toolchain, "toolchain snapshot");
        JvmPayloadArchive.Identity identity
                = JvmPayloadArchive.verify(toolchain, expected.jvmPayload(), jdkDigest);
        if (!evidence.toolchainDigest().equals(identity.aggregateDigest())
                || !evidence.toolchainSnapshotDigest().equals(identity.archiveDigest())) {
            throw new IOException("Command toolchain identity differs from its retained snapshot");
        }
        if (!expectedVersion(expected.jvmPayload(), manifest).equals(evidence.executableVersion())) {
            throw new IOException("Command launcher version differs from the controller policy");
        }

        RetainedLogs logs = retainLogs(commandRoot, evidenceRoot, expected.id(), evidence);
        if (expected.jvmPayload().kind() == JvmPayloadRequest.Kind.MAIN_PACKAGE_INSPECT
                && evidence.passed()) {
            ShipMainPackageMain.verifySummary(
                    logs.stdout(),
                    candidate,
                    expected.arguments().subList(DIRECT_JVM_PREFIX_SIZE, expected.arguments().size()));
        }
        CommandRun command = new CommandRun(
                path(evidence.executable(), "command executable").toString(),
                evidence.executableVersion(),
                expected.arguments(),
                candidate.toString(),
                expected.inputDigests(),
                evidence.launched(),
                evidence.timedOut(),
                false,
                evidence.exitCode(),
                evidence.startedAt().toString(),
                evidence.endedAt().toString(),
                logs.stdoutPath().toString(),
                evidence.stdoutDigest(),
                logs.stderrPath().toString(),
                evidence.stderrDigest());
        return new VerifiedEvidence(command);
    }

    private static RetainedLogs retainLogs(
            Path commandRoot,
            Path evidenceRoot,
            String commandId,
            CommandEvidence evidence)
            throws IOException {
        byte[] stdout = readLog(commandRoot, evidence.stdoutLog(), evidence.stdoutDigest());
        byte[] stderr = readLog(commandRoot, evidence.stderrLog(), evidence.stderrDigest());
        if ((long) stdout.length + stderr.length > MAX_TOTAL_LOG_BYTES) {
            throw new IOException("Command logs exceed the local Stamp retention limit");
        }
        Path stdoutPath = evidenceRoot.resolve(commandId + ".stdout.log");
        Path stderrPath = evidenceRoot.resolve(commandId + ".stderr.log");
        requireRetentionBudget(evidenceRoot, stdoutPath, stderrPath, stdout.length, stderr.length);
        stdoutPath = privateFile(stdoutPath, stdout);
        stderrPath = privateFile(stderrPath, stderr);
        return new RetainedLogs(stdoutPath, stdout, stderrPath);
    }

    private static void requireRetentionBudget(
            Path evidenceRoot,
            Path replacedStdout,
            Path replacedStderr,
            int stdoutBytes,
            int stderrBytes)
            throws IOException {
        long total = (long) stdoutBytes + stderrBytes;
        try (var files = Files.list(evidenceRoot)) {
            var iterator = files.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                String name = file.getFileName().toString();
                if (!name.endsWith(".stdout.log") && !name.endsWith(".stderr.log")) {
                    continue;
                }
                BasicFileAttributes attributes = Files.readAttributes(
                        file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                    throw new IOException("Retained validation log is unsafe");
                }
                if (file.equals(replacedStdout) || file.equals(replacedStderr)) {
                    continue;
                }
                if (attributes.size() > MAX_TOTAL_LOG_BYTES - total) {
                    throw new IOException("Command logs exceed the local Stamp retention limit");
                }
                total += attributes.size();
            }
        }
    }

    private static byte[] readLog(Path commandRoot, String value, String digest)
            throws IOException {
        Path file = path(value, "command log");
        requireContained(commandRoot, file, "command log");
        if (!ShipDigest.isSha256(digest)
                || Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > MAX_LOG_BYTES) {
            throw new IOException("Command log is missing, unsafe, or oversized");
        }
        byte[] content;
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            content = input.readNBytes(MAX_LOG_BYTES + 1);
        }
        if (content.length > MAX_LOG_BYTES || !digest.equals(ShipDigest.sha256(content))) {
            throw new IOException("Command log differs from its recorded digest");
        }
        return content;
    }

    private static Path requireExecutableDigest(
            String value, String expectedDigest, String label)
            throws IOException {
        Path file = path(value, label);
        if (Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !Files.isExecutable(file)
                || Files.size(file) <= 0
                || Files.size(file) > MAX_EXECUTABLE_BYTES
                || !expectedDigest.equals(digest(file))) {
            throw new IOException(label + " differs from command evidence");
        }
        return file;
    }

    private static String digest(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[16_384];
            for (int count; (count = input.read(buffer)) != -1;) {
                digest.update(buffer, 0, count);
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static void requireContained(Path root, Path supplied, String label)
            throws IOException {
        Path real = supplied.toRealPath();
        if (!real.startsWith(root) || !real.equals(supplied)) {
            throw new IOException(label + " escaped its command evidence root");
        }
    }

    private static Check outcome(String id, CommandEvidence evidence, CommandRun command) {
        if (evidence.timedOut()) {
            return check(id, Outcome.TIMED_OUT, "Controller command timed out", command);
        }
        if (evidence.launched() && evidence.exitCode() != null && evidence.exitCode() != 0) {
            return check(id, Outcome.NONZERO,
                    "Controller command exited with status " + evidence.exitCode(), command);
        }
        if (!evidence.passed()) {
            return check(id, Outcome.FAIL, "Controller command evidence failed closed", command);
        }
        return check(id, Outcome.PASS, "Controller command completed successfully", command);
    }

    private static Result persist(
            String runId,
            ToolVersion pi,
            List<Check> checks,
            Path evidence,
            Clock clock)
            throws IOException, InterruptedException {
        requireNotInterrupted();
        ShipLocalStamp stamp = ShipLocalStamp.create(runId, List.of(pi), checks, clock.instant());
        requireNotInterrupted();
        Path published = ShipLocalStampStore.write(evidence, runId, stamp);
        try {
            requireNotInterrupted();
        } catch (InterruptedException e) {
            try {
                Files.deleteIfExists(published);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
        return new Result(stamp);
    }

    private static void requireNotInterrupted()
            throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException(
                    "Interrupted before publishing deterministic Ship evidence");
        }
    }

    private static Check check(String id, Outcome outcome, String summary, CommandRun command) {
        return new Check(id, true, outcome, summary, null, command);
    }

    private static Check skipped(String id, String summary) {
        return check(id, Outcome.SKIPPED, summary, null);
    }

    private static String finding(String prefix, ArtifactValidationResult validation) {
        return validation.findings().stream()
                .findFirst()
                .map(finding -> prefix + ": " + finding.code())
                .orElse(prefix);
    }

    private static String summary(String prefix, Exception failure) {
        String message = failure.getMessage();
        String value = prefix + (message == null || message.isBlank() ? "" : ": " + message);
        value = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
        return value.length() <= 4_096 ? value : value.substring(0, 4_096);
    }

    private static void requireRouteDigest(
            ArtifactManifest.RouteArtifact route,
            CamelYamlCatalogUsageExtractor.Extraction extraction)
            throws IOException {
        if (!route.digest().equals(extraction.digest())) {
            throw new IOException("Route changed during catalog extraction: " + route.path());
        }
    }

    private static void validateBudget(ArtifactManifest manifest) {
        long count = Math.addExact(3L, manifest.citrusTests().size());
        Duration timeout;
        try {
            timeout = Duration.ofMinutes(30)
                    .plus(Duration.ofMinutes(Math.multiplyExact(30L, manifest.citrusTests().size())));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Evidence timeout budget overflowed", e);
        }
        if (count > MAX_EVIDENCE_COMMANDS
                || timeout.compareTo(MAX_EVIDENCE_TIMEOUT_BUDGET) > 0) {
            throw new IllegalArgumentException(
                    "Evidence plan exceeds " + MAX_EVIDENCE_COMMANDS + " commands or "
                                               + MAX_EVIDENCE_TIMEOUT_BUDGET);
        }
    }

    private static List<ArtifactManifest.RouteArtifact> orderedRoutes(ArtifactManifest manifest) {
        return manifest.routes().stream()
                .sorted(Comparator.comparing(ArtifactManifest.RouteArtifact::path)
                        .thenComparing(ArtifactManifest.RouteArtifact::routeId))
                .toList();
    }

    private static List<String> directJvmArguments() {
        return new ArrayList<>(
                List.of(
                        EvidenceRunner.JAVA_EXECUTABLE,
                        "-cp",
                        JvmPayloadArchive.SANDBOX_ARCHIVE,
                        ShipJvmPayloadBootstrap.class.getName()));
    }

    private static String expectedVersion(
            JvmPayloadRequest payload, ArtifactManifest manifest) {
        return switch (payload.kind()) {
            case CAMEL_YAML_VALIDATE -> "Camel direct YAML validator " + manifest.camelVersion();
            case MAIN_PACKAGE_INSPECT -> ShipMainPackageMain.PAYLOAD_VERSION;
            case CAMEL_MAIN_START -> "Camel direct Main bootstrap " + manifest.camelVersion();
            case CITRUS_YAML -> "Citrus direct YAML " + manifest.citrusVersion()
                                + " with Camel " + manifest.camelVersion();
            default -> throw new IllegalArgumentException(
                    "Unsupported evidence payload: " + payload.kind().id());
        };
    }

    private static Path realDirectory(Path supplied, String label) throws IOException {
        if (supplied == null) {
            throw new IOException(label + " is required");
        }
        Path path = supplied.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path)
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " must be a real directory");
        }
        Path real = path.toRealPath();
        if (!real.equals(path)) {
            throw new IOException(label + " must not traverse symbolic links");
        }
        return real;
    }

    private static Path privateDirectory(Path supplied) throws IOException {
        if (supplied == null) {
            throw new IOException("Evidence root is required");
        }
        Path path = supplied.toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent == null || !parent.toRealPath().equals(parent)) {
            throw new IOException(
                    "Evidence root parent must be a real directory");
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
                throw new IOException("Evidence root must be a real directory");
            }
        } catch (java.nio.file.NoSuchFileException e) {
            Files.createDirectory(
                    path,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
        }
        if (Files.getFileAttributeView(
                path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
            != null) {
            if (!Files.getPosixFilePermissions(
                    path, LinkOption.NOFOLLOW_LINKS)
                    .equals(PosixFilePermissions.fromString("rwx------"))) {
                throw new IOException(
                        "Evidence root permissions must be 0700");
            }
        }
        return realDirectory(path, "evidence root");
    }

    private static Path privateFile(Path path, byte[] content) throws IOException {
        Path parent = path.getParent();
        if (Files.getFileAttributeView(
                parent, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
            == null) {
            throw new IOException("Retained validation logs require POSIX permissions");
        }
        Path temporary = Files.createTempFile(
                parent,
                ".retained-log-",
                ".tmp",
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer encoded = ByteBuffer.wrap(content);
                while (encoded.hasRemaining()) {
                    channel.write(encoded);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException(
                        "Filesystem does not support atomic retained-log replacement",
                        e);
            }
            moved = true;
            if (!Files.getPosixFilePermissions(
                    path, LinkOption.NOFOLLOW_LINKS)
                    .equals(PosixFilePermissions.fromString("rw-------"))) {
                throw new IOException("Retained validation log permissions must be 0600");
            }
            return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static Path path(String value, String label) throws IOException {
        try {
            Path path = Path.of(value);
            if (!path.isAbsolute()
                    || !path.normalize().equals(path)
                    || !path.toString().equals(value)) {
                throw new IOException(label + " must be normalized and absolute");
            }
            return path;
        } catch (RuntimeException e) {
            throw new IOException(label + " is invalid", e);
        }
    }

    record Result(ShipLocalStamp stamp) {
    }

    @FunctionalInterface
    interface EvidenceExecutor {

        CommandEvidence run(Path candidate, Path directory, EvidenceCommand command)
                throws IOException;

        default void cleanup(Path directory, EvidenceCommand command, CommandEvidence evidence)
                throws IOException {
        }
    }

    private record CatalogClosure(
            CatalogEvidenceSet evidence,
            ArtifactValidator.CatalogDependencyBinding binding) {
    }

    private record VerifiedEvidence(CommandRun command) {
    }

    private record RetainedLogs(Path stdoutPath, byte[] stdout, Path stderrPath) {

        private RetainedLogs {
            stdout = stdout.clone();
        }

        @Override
        public byte[] stdout() {
            return stdout.clone();
        }
    }
}

package io.github.luigidemasi.camelkit.ship.worker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Prepares one controller-owned staging directory without changing the live project. */
public final class ShipWorkspace {

    private static final String WORKSPACE = "workspace";
    private static final String STALE_WORKSPACE = ".workspace-stale";
    private static final String CANDIDATE = "candidate";
    private static final String BINDING = "workspace.json";
    private static final String BINDING_TEMPORARY = ".workspace.json.tmp";
    private static final int MAX_BINDING_BYTES = 16 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);

    private ShipWorkspace() {
    }

    public static Path prepare(
            Path liveProject,
            Path runDirectory,
            String runId,
            int attempt,
            String inputDigest)
            throws IOException {
        Path project = realProject(liveProject);
        Path run = realDirectory(runDirectory, "Ship run directory");
        requireBindingFields(runId, attempt, inputDigest);
        if (run.startsWith(project) || project.startsWith(run)) {
            throw new IOException("Ship run directory and live project must be disjoint");
        }
        Path workspace = run.resolve(WORKSPACE);
        Path staleWorkspace = run.resolve(STALE_WORKSPACE);
        recoverPublication(project, runId, workspace, staleWorkspace);
        cleanAbandonedWorkspaces(run);
        if (exists(workspace)) {
            repairPublishedWorkspace(workspace);
            Binding binding = bindingFor(workspace, project, runId);
            if (attempt < binding.attempt()) {
                throw new IOException("Ship workspace belongs to a newer Execute attempt");
            }
            if (inputDigest.equals(binding.inputDigest())) {
                boolean wasLaterAttempt = attempt > binding.attempt();
                if (wasLaterAttempt) {
                    advanceAttempt(workspace, binding, attempt);
                }
                try {
                    Verification verification = verify(
                            project,
                            workspace.resolve(CANDIDATE),
                            runId,
                            attempt,
                            inputDigest);
                    return Path.of(verification.candidate().root());
                } catch (StaleBaselineException e) {
                    if (!wasLaterAttempt) {
                        throw e;
                    }
                }
            } else {
                if (attempt <= binding.attempt()) {
                    throw new IOException("Ship workspace binding does not match the active stage");
                }
                try {
                    verify(
                            project,
                            workspace.resolve(CANDIDATE),
                            runId,
                            binding.attempt(),
                            binding.inputDigest());
                } catch (StaleBaselineException ignored) {
                    // A later Execute attempt is authorized to rebuild a stale valid workspace.
                }
            }
        }

        Path temporary = Files.createTempDirectory(
                run, ".workspace-", fileAttributes(run, "rwx------"));
        try {
            Path candidate = temporary.resolve(CANDIDATE);
            createPrivateDirectory(candidate);
            ProjectSnapshot materialized = ProjectEvidenceFiles.materializeMaterial(project, candidate);
            String baseline = materialIdentity(materialized);
            writeBinding(
                    temporary.resolve(BINDING),
                    new Binding(1, project.toString(), runId, attempt, inputDigest, baseline));
            publish(temporary, workspace, staleWorkspace);
        } catch (IOException | RuntimeException preparationFailure) {
            try {
                deleteTree(temporary);
            } catch (IOException cleanupFailure) {
                preparationFailure.addSuppressed(cleanupFailure);
            }
            throw preparationFailure;
        }
        return workspace.resolve(CANDIDATE).toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Verifies one published candidate against the exact controller stage that prepared it.
     */
    public static Verification verify(
            Path liveProject,
            Path suppliedCandidate,
            String runId,
            int attempt,
            String inputDigest)
            throws IOException {
        return verify(liveProject, suppliedCandidate, runId, attempt, inputDigest, null);
    }

    /**
     * Verifies one published candidate, additionally accepting a live project whose material identity equals
     * {@code publishedIdentity} — the recorded identity of an already-published candidate, which legitimately replaced
     * the prepare-time baseline.
     */
    public static Verification verify(
            Path liveProject,
            Path suppliedCandidate,
            String runId,
            int attempt,
            String inputDigest,
            String publishedIdentity)
            throws IOException {
        Path project = realProject(liveProject);
        requireBindingFields(runId, attempt, inputDigest);
        Path candidate = realDirectory(suppliedCandidate, "Ship workspace candidate");
        Path workspace = candidate.getParent();
        if (workspace == null
                || !CANDIDATE.equals(String.valueOf(candidate.getFileName()))
                || !WORKSPACE.equals(String.valueOf(workspace.getFileName()))) {
            throw new IOException("Ship workspace candidate is not in a published workspace");
        }
        Binding binding = readBinding(workspace.resolve(BINDING));
        if (!project.toString().equals(binding.projectDirectory())
                || !runId.equals(binding.runId())
                || attempt != binding.attempt()
                || !inputDigest.equals(binding.inputDigest())) {
            throw new IOException("Ship workspace binding does not match the active stage");
        }
        ProjectSnapshot candidateSnapshot = ProjectEvidenceFiles.captureStaged(candidate);
        ProjectSnapshot baseline = ProjectEvidenceFiles.capture(project);
        String liveIdentity = materialIdentity(baseline);
        if (!liveIdentity.equals(binding.baselineDigest())
                && (publishedIdentity == null || !liveIdentity.equals(publishedIdentity))) {
            throw new StaleBaselineException(
                    "Ship workspace baseline no longer matches the live project");
        }
        return new Verification(baseline, candidateSnapshot);
    }

    private static Path realProject(Path supplied) throws IOException {
        if (supplied == null) {
            throw new IOException("Ship project directory is required");
        }
        final Path normalized;
        try {
            normalized = supplied.toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw new IOException("Ship project directory is invalid", e);
        }
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ship project must be a real directory");
        }
        return normalized.toRealPath();
    }

    private static Path realDirectory(Path supplied, String label) throws IOException {
        if (supplied == null) {
            throw new IOException(label + " is required");
        }
        Path normalized = supplied.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " must be a real directory");
        }
        return normalized.toRealPath();
    }

    private static void requireBindingFields(
            String runId, int attempt, String inputDigest)
            throws IOException {
        requireBindingFields(runId, inputDigest);
        if (attempt < 1) {
            throw new IOException("Ship workspace stage binding is invalid");
        }
    }

    private static void requireBindingFields(String runId, String inputDigest)
            throws IOException {
        if (!ShipRun.isRunId(runId) || !ShipDigest.isSha256(inputDigest)) {
            throw new IOException("Ship workspace stage binding is invalid");
        }
    }

    private static boolean exists(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path);
    }

    private static void createPrivateDirectory(Path directory) throws IOException {
        if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
        } else {
            Files.createDirectory(directory);
        }
    }

    private static Binding readBinding(Path path) throws IOException {
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ship workspace binding is invalid");
        }
        byte[] encoded;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            encoded = input.readNBytes(MAX_BINDING_BYTES + 1);
        }
        if (encoded.length == 0 || encoded.length > MAX_BINDING_BYTES) {
            throw new IOException("Ship workspace binding is invalid");
        }
        final Binding binding;
        try {
            binding = JSON.readValue(encoded, Binding.class);
        } catch (JsonProcessingException e) {
            throw new IOException("Ship workspace binding is invalid", e);
        }
        final Path project;
        try {
            project = Path.of(binding.projectDirectory());
        } catch (RuntimeException e) {
            throw new IOException("Ship workspace binding is invalid", e);
        }
        if (binding.schemaVersion() != 1
                || !ShipRun.isRunId(binding.runId())
                || binding.attempt() < 1
                || !ShipDigest.isSha256(binding.inputDigest())
                || !ShipDigest.isSha256(binding.baselineDigest())
                || !project.isAbsolute()
                || !project.normalize().toString().equals(binding.projectDirectory())) {
            throw new IOException("Ship workspace binding is invalid");
        }
        return binding;
    }

    private static Binding bindingFor(Path workspace, Path project, String runId)
            throws IOException {
        Path published = realDirectory(workspace, "Ship workspace");
        if (!WORKSPACE.equals(String.valueOf(published.getFileName()))) {
            throw new IOException("Ship workspace is not published at the expected path");
        }
        Binding binding = readBinding(published.resolve(BINDING));
        if (!project.toString().equals(binding.projectDirectory())
                || !runId.equals(binding.runId())) {
            throw new IOException("Ship workspace binding does not match its controller run");
        }
        return binding;
    }

    private static void publish(Path temporary, Path workspace, Path staleWorkspace)
            throws IOException {
        boolean displaced = false;
        try {
            if (exists(workspace)) {
                Files.move(workspace, staleWorkspace, StandardCopyOption.ATOMIC_MOVE);
                displaced = true;
            }
            Files.move(temporary, workspace, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException publicationFailure) {
            if (displaced && !exists(workspace)) {
                try {
                    Files.move(staleWorkspace, workspace, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException restorationFailure) {
                    publicationFailure.addSuppressed(restorationFailure);
                }
            }
            try {
                deleteTree(temporary);
            } catch (IOException cleanupFailure) {
                publicationFailure.addSuppressed(cleanupFailure);
            }
            throw publicationFailure;
        }
        if (displaced) {
            deleteTree(staleWorkspace);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                    Files.setPosixFilePermissions(
                            directory, PosixFilePermissions.fromString("rwx------"));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                    throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void recoverPublication(
            Path project,
            String runId,
            Path workspace,
            Path staleWorkspace)
            throws IOException {
        if (!exists(staleWorkspace)) {
            return;
        }
        if (!exists(workspace)) {
            Files.move(staleWorkspace, workspace, StandardCopyOption.ATOMIC_MOVE);
            return;
        }
        try {
            repairPublishedWorkspace(workspace);
            bindingFor(workspace, project, runId);
            ProjectEvidenceFiles.captureStaged(workspace.resolve(CANDIDATE));
        } catch (IOException invalidPublication) {
            try {
                deleteTree(workspace);
                Files.move(staleWorkspace, workspace, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException recoveryFailure) {
                invalidPublication.addSuppressed(recoveryFailure);
                throw invalidPublication;
            }
        }
        deleteTree(staleWorkspace);
    }

    private static void cleanAbandonedWorkspaces(Path run)
            throws IOException {
        List<Path> abandoned = new ArrayList<>();
        try (var entries = Files.list(run)) {
            entries.filter(path -> {
                String name = String.valueOf(path.getFileName());
                return name.startsWith(".workspace-")
                        && !STALE_WORKSPACE.equals(name);
            }).forEach(abandoned::add);
        }
        abandoned.sort(Comparator.comparing(Path::toString));
        for (Path path : abandoned) {
            deleteTree(path);
        }
    }

    private static void repairPublishedWorkspace(Path workspace) throws IOException {
        Path published = realDirectory(workspace, "Ship workspace");
        if (published.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(
                    published, PosixFilePermissions.fromString("rwx------"));
        }
        deleteTree(published.resolve(BINDING_TEMPORARY));
    }

    private static void advanceAttempt(Path workspace, Binding binding, int attempt)
            throws IOException {
        Path temporary = workspace.resolve(BINDING_TEMPORARY);
        try {
            deleteTree(temporary);
            writeBinding(
                    temporary,
                    new Binding(
                            binding.schemaVersion(),
                            binding.projectDirectory(),
                            binding.runId(),
                            attempt,
                            binding.inputDigest(),
                            binding.baselineDigest()));
            Files.move(
                    temporary,
                    workspace.resolve(BINDING),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            deleteTree(temporary);
        }
    }

    private static void writeBinding(Path target, Binding binding) throws IOException {
        byte[] encoded = JSON.writeValueAsBytes(binding);
        if (encoded.length > MAX_BINDING_BYTES) {
            throw new IOException("Ship workspace binding exceeds its size limit");
        }
        Files.createFile(target, fileAttributes(target.getParent(), "rw-------"));
        try (FileChannel channel = FileChannel.open(
                target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer content = ByteBuffer.wrap(encoded);
            while (content.hasRemaining()) {
                channel.write(content);
            }
            channel.force(true);
        }
    }

    private static FileAttribute<?>[] fileAttributes(Path path, String permissions) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix")
                ? new FileAttribute<?>[]{
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString(permissions))}
                : new FileAttribute<?>[0];
    }

    /** Framed identity of one material tree; shared by workspace baselines and publication records. */
    public static String materialIdentity(ProjectSnapshot snapshot) {
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        field(framed, "camel-kit.ship.workspace-material.v1");
        field(framed, snapshot.policyDigest());
        snapshot.directories().forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                field(framed, "directory");
                field(framed, path);
                field(framed, Integer.toString(entry.unixMode() & 0777));
            }
        });
        snapshot.files().forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                field(framed, "file");
                field(framed, path);
                field(framed, Long.toString(entry.size()));
                field(framed, entry.digest());
                field(framed, Integer.toString(entry.unixMode() & 0777));
            }
        });
        return ShipDigest.sha256(framed.toByteArray());
    }

    private static void field(ByteArrayOutputStream output, String value) {
        byte[] encoded = Objects.requireNonNull(value, "workspace identity field")
                .getBytes(StandardCharsets.UTF_8);
        output.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.length).array());
        output.writeBytes(encoded);
    }

    private record Binding(
            int schemaVersion,
            String projectDirectory,
            String runId,
            int attempt,
            String inputDigest,
            String baselineDigest) {
    }

    public static final class StaleBaselineException extends IOException {

        private static final long serialVersionUID = 1L;

        private StaleBaselineException(String message) {
            super(message);
        }
    }

    public record Verification(ProjectSnapshot baseline, ProjectSnapshot candidate) {

        public Verification {
            Objects.requireNonNull(baseline, "baseline");
            Objects.requireNonNull(candidate, "candidate");
        }
    }

}

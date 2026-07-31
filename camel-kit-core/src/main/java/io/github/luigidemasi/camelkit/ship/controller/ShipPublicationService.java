package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.ArtifactRef;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;
import io.github.luigidemasi.camelkit.ship.worker.ShipWorkspace;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Write-ahead, journal-recoverable publication of one validated workspace candidate into the live project.
 *
 * <p>
 * The caller holds the per-run mutation lock for the whole operation, so exactly one process can publish, recover, or
 * roll back at a time; a torn publication left by process death is recovered from the journal by the next locked
 * operation. The run-state write recording the publication is the commit point: a journal without a recorded
 * publication is always uncommitted and rolls back to the exact baseline.
 */
final class ShipPublicationService {

    static final int SCHEMA_VERSION = 1;
    static final String DIRECTORY = "publication";
    static final String JOURNAL = "journal.json";
    static final String RECORD = "publication.json";
    static final String BACKUP = "backup";
    private static final String STAGING_SUFFIX = ".camel-kit-publish.tmp";
    private static final int MAX_DOCUMENT_BYTES = 64 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);

    private ShipPublicationService() {
    }

    static Path publicationDirectory(Path runDirectory) {
        return runDirectory.toAbsolutePath().normalize().resolve(DIRECTORY);
    }

    static Path journalPath(Path runDirectory) {
        return publicationDirectory(runDirectory).resolve(JOURNAL);
    }

    static Path recordPath(Path runDirectory) {
        return publicationDirectory(runDirectory).resolve(RECORD);
    }

    private static Path backupDirectory(Path runDirectory) {
        return publicationDirectory(runDirectory).resolve(BACKUP);
    }

    static boolean journalExists(Path runDirectory) {
        return Files.exists(journalPath(runDirectory), LinkOption.NOFOLLOW_LINKS);
    }

    /** Computes the material publication set from the verified baseline and candidate snapshots. */
    static Journal plan(
            String runId, int attempt, String createdAt, ShipWorkspace.Verification verification)
            throws IOException {
        ProjectSnapshot baseline = verification.baseline();
        ProjectSnapshot candidate = verification.candidate();
        List<Entry> entries = new ArrayList<>();
        Map<String, ProjectSnapshot.DirectoryEntry> baselineDirectories
                = materialDirectories(baseline.directories());
        Map<String, ProjectSnapshot.DirectoryEntry> candidateDirectories
                = materialDirectories(candidate.directories());
        Map<String, ProjectSnapshot.FileEntry> baselineFiles = material(baseline.files());
        Map<String, ProjectSnapshot.FileEntry> candidateFiles = material(candidate.files());
        requireStableKind(baselineDirectories.keySet(), candidateFiles.keySet());
        requireStableKind(candidateDirectories.keySet(), baselineFiles.keySet());
        candidateDirectories.forEach((path, entry) -> {
            ProjectSnapshot.DirectoryEntry before = baselineDirectories.get(path);
            if (before == null) {
                entries.add(Entry.directory(path, Action.ADD, null, mode(entry.unixMode())));
            } else if (mode(before.unixMode()) != mode(entry.unixMode())) {
                entries.add(Entry.directory(
                        path, Action.REPLACE, mode(before.unixMode()), mode(entry.unixMode())));
            }
        });
        baselineDirectories.forEach((path, entry) -> {
            if (!candidateDirectories.containsKey(path)) {
                entries.add(Entry.directory(path, Action.DELETE, mode(entry.unixMode()), null));
            }
        });
        candidateFiles.forEach((path, entry) -> {
            ProjectSnapshot.FileEntry before = baselineFiles.get(path);
            if (before == null) {
                entries.add(Entry.file(path, Action.ADD, null, entry));
            } else if (before.size() != entry.size()
                    || !before.digest().equals(entry.digest())
                    || mode(before.unixMode()) != mode(entry.unixMode())) {
                entries.add(Entry.file(path, Action.REPLACE, before, entry));
            }
        });
        baselineFiles.forEach((path, entry) -> {
            if (!candidateFiles.containsKey(path)) {
                entries.add(Entry.file(path, Action.DELETE, entry, null));
            }
        });
        entries.sort(Comparator.comparing(Entry::path));
        return new Journal(
                SCHEMA_VERSION,
                runId,
                attempt,
                ShipWorkspace.materialIdentity(baseline),
                ShipWorkspace.materialIdentity(candidate),
                createdAt,
                List.copyOf(entries));
    }

    /**
     * Refuses a path that is a directory on one side and a file on the other. Publishing it would need an ordered
     * replace of one kind by the other, which this protocol does not model; planning it anyway emits two entries for
     * the same path that can never both apply.
     */
    private static void requireStableKind(
            java.util.Set<String> directories, java.util.Set<String> files)
            throws IOException {
        for (String path : directories) {
            if (files.contains(path)) {
                throw new UnsupportedChangeException(
                        "Ship cannot publish " + path
                                                     + " because it changes between a file and a directory");
            }
        }
    }

    /** Clears stale scratch from an earlier committed or failed attempt and writes the immutable journal. */
    static void begin(Path runDirectory, Journal journal) throws IOException {
        Path directory = publicationDirectory(runDirectory);
        if (journalExists(runDirectory)) {
            throw new IOException("Ship publication journal already exists: " + journalPath(runDirectory));
        }
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            deleteTree(backupDirectory(runDirectory));
            Files.deleteIfExists(recordPath(runDirectory));
        } else {
            Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
        }
        writeAtomically(journalPath(runDirectory), encode(journal));
    }

    /**
     * Backs up, applies, and verifies the publication set; any failure rolls the live project back to the exact
     * baseline and clears the journal before rethrowing.
     */
    static void apply(Path project, Path candidate, Path runDirectory, Journal journal)
            throws IOException {
        Path backup = backupDirectory(runDirectory);
        try {
            backupPhase(project, backup, journal);
        } catch (IOException | RuntimeException e) {
            // The backup phase only reads the live project, so nothing was mutated: clear the
            // scratch instead of leaving a journal that later reads as a torn publication.
            try {
                clean(runDirectory);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
        try {
            applyPhase(project, candidate, journal);
            String liveIdentity = ShipWorkspace.materialIdentity(
                    ProjectEvidenceFiles.capture(project));
            if (!liveIdentity.equals(journal.candidateIdentity())) {
                throw new IOException(
                        "Published Ship project differs from the validated candidate");
            }
        } catch (IOException | RuntimeException applyFailure) {
            try {
                rollback(project, runDirectory, journal);
                clean(runDirectory);
            } catch (IOException | RuntimeException rollbackFailure) {
                applyFailure.addSuppressed(rollbackFailure);
            }
            throw applyFailure;
        }
    }

    /** Writes the retained publication record and returns its state reference. */
    static ArtifactRef commitRecord(Path runDirectory, Journal journal, String appliedAt)
            throws IOException {
        byte[] encoded = encode(new PublicationRecord(
                journal.schemaVersion(),
                journal.runId(),
                journal.attempt(),
                journal.baselineIdentity(),
                journal.candidateIdentity(),
                journal.createdAt(),
                appliedAt,
                journal.entries()));
        Path record = recordPath(runDirectory);
        writeAtomically(record, encoded);
        return new ArtifactRef(record.toString(), ShipDigest.sha256(encoded));
    }

    /** Rolls back an applied but uncommitted publication set and clears the scratch. */
    static void rollbackApplied(Path project, Path runDirectory, Journal journal)
            throws IOException {
        rollback(project, runDirectory, journal);
        clean(runDirectory);
    }

    /**
     * Recovers a torn uncommitted publication left by process death: rolls the live project back to the exact baseline
     * per journal entry and clears the scratch. Idempotent; a no-op without a journal.
     *
     * <p>
     * A journal describes exactly one Execute candidate. When {@code executeAttempt} no longer matches, that candidate
     * was superseded, the journal can no longer describe the live project, and it is discarded instead of applied.
     */
    static void recover(Path project, Path runDirectory, int executeAttempt) throws IOException {
        if (!journalExists(runDirectory)) {
            return;
        }
        Journal journal = readJournal(runDirectory);
        if (journal.attempt() != executeAttempt) {
            discard(runDirectory);
            return;
        }
        rollback(project, runDirectory, journal);
        clean(runDirectory);
    }

    /**
     * Drops a journal that is no longer a write-ahead log: either the publication it describes committed, or the run
     * voided its published claim. The live project is never touched.
     *
     * <p>
     * The retained record survives, because the run state may still reference it until the voiding write lands; a later
     * publication clears it in {@link #begin}.
     */
    static void discard(Path runDirectory) throws IOException {
        Files.deleteIfExists(journalPath(runDirectory));
        deleteTree(backupDirectory(runDirectory));
    }

    /** Reads and verifies the retained publication record referenced by the run state. */
    static PublicationRecord readVerifiedRecord(
            Path runDirectory, ArtifactRef reference, String runId)
            throws IOException {
        Path expected = recordPath(runDirectory);
        if (!expected.toString().equals(reference.path())) {
            throw new IOException(
                    "Ship publication record is outside its run directory: " + reference.path());
        }
        byte[] encoded = readBounded(expected);
        if (!ShipDigest.sha256(encoded).equals(reference.digest())) {
            throw new IOException("Ship publication record does not match its recorded digest");
        }
        PublicationRecord record = decode(encoded, PublicationRecord.class, expected);
        if (record.schemaVersion() != SCHEMA_VERSION || !runId.equals(record.runId())) {
            throw new IOException("Ship publication record does not match its run");
        }
        return record;
    }

    private static void backupPhase(Path project, Path backup, Journal journal)
            throws IOException {
        for (Entry entry : journal.entries()) {
            Path live = target(project, entry.path());
            if (entry.kind() == Kind.DIRECTORY) {
                requireDirectoryBaseline(entry, live);
                continue;
            }
            switch (entry.action()) {
                case ADD -> {
                    if (Files.exists(live, LinkOption.NOFOLLOW_LINKS)) {
                        throw new StaleLiveTreeException(
                                "Live project gained a file since the publication baseline: "
                                                         + entry.path());
                    }
                }
                case REPLACE, DELETE -> {
                    Path copy = target(backup, entry.path());
                    Files.createDirectories(copy.getParent());
                    byte[] content = Files.readAllBytes(live);
                    if (content.length != entry.baselineSize()
                            || !ShipDigest.sha256(content).equals(entry.baselineDigest())) {
                        throw new StaleLiveTreeException(
                                "Live project file changed since the publication baseline: "
                                                         + entry.path());
                    }
                    // The backup is the only source rollback can restore from, so it is forced to
                    // disk like every other durability-critical write here.
                    Files.createFile(
                            copy,
                            PosixFilePermissions.asFileAttribute(
                                    PosixFilePermissions.fromString("rw-------")));
                    writeFully(copy, content);
                }
            }
        }
    }

    private static void requireDirectoryBaseline(Entry entry, Path live)
            throws IOException {
        boolean exists = Files.isDirectory(live, LinkOption.NOFOLLOW_LINKS);
        boolean expected = entry.action() != Action.ADD;
        if (exists != expected) {
            throw new StaleLiveTreeException(
                    "Live project directory changed since the publication baseline: "
                                             + entry.path());
        }
    }

    private static void applyPhase(Path project, Path candidate, Journal journal)
            throws IOException {
        for (Entry entry : directories(journal, Action.ADD)) {
            Files.createDirectory(target(project, entry.path()),
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
        }
        for (Entry entry : journal.entries()) {
            if (entry.kind() != Kind.FILE || entry.action() == Action.DELETE) {
                continue;
            }
            byte[] content = Files.readAllBytes(target(candidate, entry.path()));
            if (content.length != entry.candidateSize()
                    || !ShipDigest.sha256(content).equals(entry.candidateDigest())) {
                throw new IOException(
                        "Ship candidate file changed during publication: " + entry.path());
            }
            Path live = target(project, entry.path());
            Path temporary = stagingPath(live);
            boolean moved = false;
            try {
                createStagingFile(temporary);
                writeFully(temporary, content);
                Files.setPosixFilePermissions(temporary, permissions(entry.candidateMode()));
                move(temporary, live);
                moved = true;
            } finally {
                if (!moved) {
                    Files.deleteIfExists(temporary);
                }
            }
        }
        for (Entry entry : journal.entries()) {
            if (entry.kind() == Kind.FILE && entry.action() == Action.DELETE) {
                Files.delete(target(project, entry.path()));
            }
        }
        for (Entry entry : reversed(directories(journal, Action.DELETE))) {
            Path live = target(project, entry.path());
            try {
                Files.delete(live);
            } catch (DirectoryNotEmptyException e) {
                // Build output and other non-publishable content is invisible to the snapshots,
                // so retrying cannot help until the user clears it.
                throw new IOException(
                        "Ship cannot remove " + entry.path()
                                      + " because the live project still holds content outside the "
                                      + "publication, such as build output; remove it and resume",
                        e);
            }
        }
        for (Entry entry : journal.entries()) {
            if (entry.kind() == Kind.DIRECTORY && entry.action() != Action.DELETE) {
                Files.setPosixFilePermissions(
                        target(project, entry.path()), permissions(entry.candidateMode()));
            }
        }
    }

    /**
     * Restores the exact baseline per journal entry. Every entry must currently match its baseline side, its candidate
     * side, or be absent where the journal records a creation; anything else blocks recovery before any file is
     * touched, so a live tree edited after a tear is never overwritten.
     */
    private static void rollback(Path project, Path runDirectory, Journal journal)
            throws IOException {
        Path backup = backupDirectory(runDirectory);
        List<Runnable2> restores = new ArrayList<>();
        for (Entry entry : journal.entries()) {
            Runnable2 restore = classify(project, backup, entry);
            if (restore != null) {
                restores.add(restore);
            }
        }
        // Remove staging files an interrupted apply left beside their targets; they are material
        // to the tree policy, so a leftover would otherwise drift the live project's identity.
        for (Entry entry : journal.entries()) {
            if (entry.kind() == Kind.FILE) {
                Files.deleteIfExists(stagingPath(target(project, entry.path())));
            }
        }
        // Restoration mutates directory contents, so every touched directory is made owner-writable
        // first; the exact baseline modes are re-applied as the final step.
        for (Entry entry : journal.entries()) {
            if (entry.kind() != Kind.DIRECTORY) {
                continue;
            }
            Path live = target(project, entry.path());
            if (entry.action() == Action.DELETE
                    && !Files.isDirectory(live, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(live);
            }
            if (Files.isDirectory(live, LinkOption.NOFOLLOW_LINKS)) {
                Files.setPosixFilePermissions(
                        live, PosixFilePermissions.fromString("rwx------"));
            }
        }
        for (Runnable2 restore : restores) {
            restore.run();
        }
        for (Entry entry : reversed(directories(journal, Action.ADD))) {
            Path live = target(project, entry.path());
            if (Files.isDirectory(live, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.delete(live);
                } catch (DirectoryNotEmptyException e) {
                    // Something outside the publication landed here while it was torn. Leaving the
                    // directory keeps that content; failing here would block resume and abort alike.
                    Files.setPosixFilePermissions(live, permissions(entry.candidateMode()));
                }
            }
        }
        for (Entry entry : journal.entries()) {
            if (entry.kind() != Kind.DIRECTORY || entry.action() == Action.ADD) {
                continue;
            }
            Path live = target(project, entry.path());
            if (Files.isDirectory(live, LinkOption.NOFOLLOW_LINKS)) {
                Files.setPosixFilePermissions(live, permissions(entry.baselineMode()));
            }
        }
    }

    private static Runnable2 classify(Path project, Path backup, Entry entry)
            throws IOException {
        if (entry.kind() == Kind.DIRECTORY) {
            return null;
        }
        Path live = target(project, entry.path());
        boolean exists = Files.exists(live, LinkOption.NOFOLLOW_LINKS);
        String liveDigest = exists && Files.isRegularFile(live, LinkOption.NOFOLLOW_LINKS)
                ? ShipDigest.sha256(Files.readAllBytes(live))
                : null;
        switch (entry.action()) {
            case ADD -> {
                if (!exists) {
                    return null;
                }
                if (entry.candidateDigest().equals(liveDigest)) {
                    return () -> Files.delete(live);
                }
            }
            case REPLACE -> {
                // A mode-only replace leaves both digests equal, so the mode is the only evidence
                // of whether it was applied; restoring re-stamps the baseline mode.
                if (entry.baselineDigest().equals(liveDigest)
                        && (!exists || liveMode(live) == entry.baselineMode())) {
                    return null;
                }
                if (entry.candidateDigest().equals(liveDigest)) {
                    return () -> restoreBackup(backup, live, entry);
                }
            }
            case DELETE -> {
                if (entry.baselineDigest().equals(liveDigest)) {
                    return null;
                }
                if (!exists) {
                    return () -> restoreBackup(backup, live, entry);
                }
            }
        }
        throw new RecoveryBlockedException(
                "Live project file matches neither the publication baseline nor its candidate: "
                                           + entry.path());
    }

    private static void restoreBackup(Path backup, Path live, Entry entry)
            throws IOException {
        byte[] content = Files.readAllBytes(target(backup, entry.path()));
        if (content.length != entry.baselineSize()
                || !ShipDigest.sha256(content).equals(entry.baselineDigest())) {
            throw new RecoveryBlockedException(
                    "Ship publication backup does not match its journal entry: " + entry.path());
        }
        Files.createDirectories(live.getParent());
        Path temporary = stagingPath(live);
        boolean moved = false;
        try {
            createStagingFile(temporary);
            writeFully(temporary, content);
            Files.setPosixFilePermissions(temporary, permissions(entry.baselineMode()));
            move(temporary, live);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    /**
     * Names the staging file deterministically beside its target so a publication torn by process death leaves debris
     * the journal can still find; a random name would survive every rollback and pollute the live project.
     */
    private static Path stagingPath(Path live) {
        return live.resolveSibling("." + live.getFileName() + STAGING_SUFFIX);
    }

    private static void createStagingFile(Path staging) throws IOException {
        Files.deleteIfExists(staging);
        Files.createFile(
                staging,
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
    }

    private static void clean(Path runDirectory) throws IOException {
        deleteTree(backupDirectory(runDirectory));
        Files.deleteIfExists(recordPath(runDirectory));
        Files.deleteIfExists(journalPath(runDirectory));
    }

    private static Journal readJournal(Path runDirectory) throws IOException {
        Path journal = journalPath(runDirectory);
        Journal decoded = decode(readBounded(journal), Journal.class, journal);
        if (decoded.schemaVersion() != SCHEMA_VERSION) {
            throw new RecoveryBlockedException(
                    "Unsupported Ship publication journal schema: " + decoded.schemaVersion());
        }
        // Replay is bound to the policy in force now, not the one that planned the journal: a
        // path that became protected or denied meanwhile must never be restored or removed.
        ShipTreePolicy policy = ShipTreePolicy.current();
        List<Entry> entries = decoded.entries();
        for (int index = 0; index < entries.size(); index++) {
            Classification classification;
            try {
                classification = policy.classify(entries.get(index).path());
            } catch (RuntimeException e) {
                // The rejected path is the one input proven unsafe to display, so it is located
                // by position instead of echoed.
                throw new RecoveryBlockedException(
                        "Ship publication journal entry " + index
                                                   + " has an invalid path in " + journal);
            }
            if (classification != Classification.MATERIAL) {
                throw new RecoveryBlockedException(
                        "Ship publication journal entry is no longer publishable: "
                                                   + entries.get(index).path());
            }
        }
        return decoded;
    }

    private static List<Entry> directories(Journal journal, Action action) {
        return journal.entries().stream()
                .filter(entry -> entry.kind() == Kind.DIRECTORY && entry.action() == action)
                .toList();
    }

    private static Path target(Path root, String relative) throws IOException {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IOException("Ship publication entry escaped its root: " + relative);
        }
        return resolved;
    }

    private static Map<String, ProjectSnapshot.FileEntry> material(
            Map<String, ProjectSnapshot.FileEntry> files) {
        TreeMap<String, ProjectSnapshot.FileEntry> filtered = new TreeMap<>();
        files.forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                filtered.put(path, entry);
            }
        });
        return filtered;
    }

    private static Map<String, ProjectSnapshot.DirectoryEntry> materialDirectories(
            Map<String, ProjectSnapshot.DirectoryEntry> directories) {
        TreeMap<String, ProjectSnapshot.DirectoryEntry> filtered = new TreeMap<>();
        directories.forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                filtered.put(path, entry);
            }
        });
        return filtered;
    }

    private static List<Entry> reversed(List<Entry> entries) {
        List<Entry> copy = new ArrayList<>(entries);
        java.util.Collections.reverse(copy);
        return copy;
    }

    private static int mode(int unixMode) {
        return unixMode & 0777;
    }

    private static int liveMode(Path live) throws IOException {
        int bits = 0;
        for (java.nio.file.attribute.PosixFilePermission permission : Files.getPosixFilePermissions(live,
                LinkOption.NOFOLLOW_LINKS)) {
            bits |= switch (permission) {
                case OWNER_READ -> 0400;
                case OWNER_WRITE -> 0200;
                case OWNER_EXECUTE -> 0100;
                case GROUP_READ -> 040;
                case GROUP_WRITE -> 020;
                case GROUP_EXECUTE -> 010;
                case OTHERS_READ -> 04;
                case OTHERS_WRITE -> 02;
                case OTHERS_EXECUTE -> 01;
            };
        }
        return bits;
    }

    private static java.util.Set<java.nio.file.attribute.PosixFilePermission> permissions(int mode) {
        StringBuilder text = new StringBuilder(9);
        int[] shifts = {6, 3, 0};
        for (int shift : shifts) {
            int bits = (mode >> shift) & 07;
            text.append((bits & 04) != 0 ? 'r' : '-');
            text.append((bits & 02) != 0 ? 'w' : '-');
            text.append((bits & 01) != 0 ? 'x' : '-');
        }
        return PosixFilePermissions.fromString(text.toString());
    }

    private static byte[] encode(Object document) throws IOException {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
        } catch (JsonProcessingException e) {
            throw new IOException("Ship publication document cannot be encoded", e);
        }
    }

    private static <T> T decode(byte[] encoded, Class<T> type, Path source)
            throws IOException {
        try {
            return JSON.readValue(encoded, type);
        } catch (IOException e) {
            RecoveryBlockedException blocked = new RecoveryBlockedException(
                    "Ship publication document is corrupt: " + source);
            blocked.initCause(e);
            throw blocked;
        }
    }

    private static byte[] readBounded(Path source) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            byte[] encoded = input.readNBytes(MAX_DOCUMENT_BYTES + 1);
            if (encoded.length == 0 || encoded.length > MAX_DOCUMENT_BYTES) {
                throw new IOException(
                        "Ship publication document has an invalid size: " + source);
            }
            return encoded;
        }
    }

    private static void writeAtomically(Path targetPath, byte[] encoded) throws IOException {
        Path temporary = Files.createTempFile(
                targetPath.getParent(), ".publication-", ".tmp",
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
        boolean moved = false;
        try {
            writeFully(temporary, encoded);
            move(temporary, targetPath);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void writeFully(Path targetPath, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(
                targetPath, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void move(Path source, Path targetPath) throws IOException {
        try {
            Files.move(source, targetPath,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            throw new IOException(
                    "Filesystem does not support atomic Ship publication replacement", e);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @FunctionalInterface
    private interface Runnable2 {
        void run() throws IOException;
    }

    enum Kind {
        FILE,
        DIRECTORY
    }

    enum Action {
        ADD,
        REPLACE,
        DELETE
    }

    record Entry(
            String path,
            Kind kind,
            Action action,
            String baselineDigest,
            Long baselineSize,
            Integer baselineMode,
            String candidateDigest,
            Long candidateSize,
            Integer candidateMode) {

        Entry {
            Objects.requireNonNull(path, "entry path");
            Objects.requireNonNull(kind, "entry kind");
            Objects.requireNonNull(action, "entry action");
            boolean baseline = action != Action.ADD;
            boolean candidate = action != Action.DELETE;
            if (kind == Kind.FILE) {
                require(baseline == (baselineDigest != null && baselineSize != null
                        && baselineMode != null));
                require(candidate == (candidateDigest != null && candidateSize != null
                        && candidateMode != null));
            } else {
                require(baselineDigest == null && baselineSize == null
                        && candidateDigest == null && candidateSize == null);
                require(baseline == (baselineMode != null));
                require(candidate == (candidateMode != null));
            }
        }

        static Entry file(
                String path, Action action,
                ProjectSnapshot.FileEntry baseline, ProjectSnapshot.FileEntry candidate) {
            return new Entry(
                    path,
                    Kind.FILE,
                    action,
                    baseline == null ? null : baseline.digest(),
                    baseline == null ? null : baseline.size(),
                    baseline == null ? null : baseline.unixMode() & 0777,
                    candidate == null ? null : candidate.digest(),
                    candidate == null ? null : candidate.size(),
                    candidate == null ? null : candidate.unixMode() & 0777);
        }

        static Entry directory(String path, Action action, Integer baselineMode, Integer candidateMode) {
            return new Entry(path, Kind.DIRECTORY, action, null, null, baselineMode, null, null, candidateMode);
        }

        private static void require(boolean condition) {
            if (!condition) {
                throw new IllegalArgumentException(
                        "Ship publication entry does not match its action");
            }
        }
    }

    record Journal(
            int schemaVersion,
            String runId,
            int attempt,
            String baselineIdentity,
            String candidateIdentity,
            String createdAt,
            List<Entry> entries) {

        Journal {
            entries = List.copyOf(Objects.requireNonNull(entries, "journal entries"));
        }
    }

    record PublicationRecord(
            int schemaVersion,
            String runId,
            int attempt,
            String baselineIdentity,
            String candidateIdentity,
            String createdAt,
            String appliedAt,
            List<Entry> entries) {

        PublicationRecord {
            entries = List.copyOf(Objects.requireNonNull(entries, "record entries"));
        }
    }

    /** The live tree drifted from the recorded baseline before any file was mutated. */
    static final class StaleLiveTreeException extends IOException {

        private static final long serialVersionUID = 1L;

        StaleLiveTreeException(String message) {
            super(message);
        }
    }

    /** Recovery refused to touch a live tree that matches neither baseline nor candidate. */
    static final class RecoveryBlockedException extends IOException {

        private static final long serialVersionUID = 1L;

        RecoveryBlockedException(String message) {
            super(message);
        }
    }

    /** The candidate carries a change this publication protocol does not model. */
    static final class UnsupportedChangeException extends IOException {

        private static final long serialVersionUID = 1L;

        UnsupportedChangeException(String message) {
            super(message);
        }
    }
}

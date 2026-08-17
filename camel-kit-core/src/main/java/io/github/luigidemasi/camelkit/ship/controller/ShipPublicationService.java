package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * The caller holds both the run and project mutation locks for the whole live-tree operation. A torn publication left
 * by process death is recovered from the journal by the next locked operation. The run-state write recording the
 * publication is the commit point: a journal without a recorded publication is always uncommitted and rolls back to the
 * exact baseline.
 */
final class ShipPublicationService {

    static final int SCHEMA_VERSION = 2;
    private static final int RECORD_SCHEMA_VERSION = 1;
    static final String DIRECTORY = "publication";
    static final String JOURNAL = "journal.json";
    static final String RECORD = "publication.json";
    static final String BACKUP = "backup";
    private static final String APPLICATION = "application.marker";
    private static final String RECOVERY = "recovery.marker";
    private static final String STAGING_PREFIX = ".ckp-";
    private static final String STAGING_SUFFIX = ".tmp";
    private static final int PRIVATE_STAGING_MODE = 0600;
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
        // notExists is true only for confirmed absence; an indeterminate lookup must fail closed.
        return !Files.notExists(journalPath(runDirectory), LinkOption.NOFOLLOW_LINKS);
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
        Journal journal = new Journal(
                SCHEMA_VERSION,
                runId,
                attempt,
                baseline.rootIdentity(),
                ShipWorkspace.materialIdentity(baseline),
                ShipWorkspace.materialIdentity(candidate),
                createdAt,
                List.copyOf(entries));
        requireStagingBindings(journal, baseline, candidate);
        return journal;
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
            Files.deleteIfExists(applicationPath(runDirectory));
            Files.deleteIfExists(recoveryPath(runDirectory));
        } else {
            Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
        }
        Files.setPosixFilePermissions(
                directory, PosixFilePermissions.fromString("rwx------"));
        deletePermissionProbes(directory);
        requireOwnerPermissionMask(directory);
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
            startApplication(runDirectory, journal);
            probeStagingPaths(project, journal);
        } catch (IOException | RuntimeException e) {
            try {
                // Backup has not touched a target. Only a probe whose own cleanup failed can
                // leave Ship-owned live-tree scratch that still needs the journal.
                if (!(e instanceof StagingCleanupException)) {
                    clean(runDirectory);
                }
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
        try {
            applyPhase(project, candidate, journal);
            ProjectSnapshot published = ProjectEvidenceFiles.capture(project);
            if (!journal.baselineRootIdentity().equals(published.rootIdentity())
                    || !ShipWorkspace.materialIdentity(published).equals(
                            journal.candidateIdentity())) {
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
                RECORD_SCHEMA_VERSION,
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
    static void recover(
            Path project, Path runDirectory, String runId, int executeAttempt)
            throws IOException {
        if (!journalExists(runDirectory)) {
            return;
        }
        Journal journal = readJournal(runDirectory);
        if (!runId.equals(journal.runId())) {
            throw new RecoveryBlockedException(
                    "Ship publication journal belongs to another run");
        }
        if (journal.attempt() != executeAttempt) {
            if (Files.exists(applicationPath(runDirectory), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(recoveryPath(runDirectory), LinkOption.NOFOLLOW_LINKS)
                    || hasStaging(project, journal)) {
                throw new RecoveryBlockedException(
                        "Superseded Ship publication journal still owns live recovery scratch");
            }
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
        Files.deleteIfExists(applicationPath(runDirectory));
        Files.deleteIfExists(recoveryPath(runDirectory));
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
        if (record.schemaVersion() != RECORD_SCHEMA_VERSION || !runId.equals(record.runId())) {
            throw new IOException("Ship publication record does not match its run");
        }
        return record;
    }

    private static void backupPhase(Path project, Path backup, Journal journal)
            throws IOException {
        ProjectSnapshot observed = ProjectEvidenceFiles.capture(project);
        if (!journal.baselineRootIdentity().equals(observed.rootIdentity())
                || !ShipWorkspace.materialIdentity(observed).equals(journal.baselineIdentity())) {
            throw new StaleLiveTreeException(
                    "Live project changed since the publication baseline");
        }
        for (int index = 0; index < journal.entries().size(); index++) {
            Entry entry = journal.entries().get(index);
            if (entry.kind() == Kind.FILE
                    && Files.exists(
                            stagingPath(journal, index, target(project, entry.path())),
                            LinkOption.NOFOLLOW_LINKS)) {
                throw new StaleLiveTreeException(
                        "Live project gained Ship publication scratch since the baseline");
            }
        }
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
                    byte[] content = ProjectEvidenceFiles.readMaterial(
                            project, entry.path(), maximumBytes(entry.baselineSize()));
                    if (content.length != entry.baselineSize()
                            || !ShipDigest.sha256(content).equals(entry.baselineDigest())
                            || liveMode(live) != entry.baselineMode()) {
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
                    Files.setPosixFilePermissions(
                            copy, PosixFilePermissions.fromString("rw-------"));
                    writeFully(copy, content);
                }
            }
        }
    }

    private static void requireDirectoryBaseline(Entry entry, Path live)
            throws IOException {
        boolean present = Files.exists(live, LinkOption.NOFOLLOW_LINKS);
        boolean expected = entry.action() != Action.ADD;
        if (present != expected
                || present && (!Files.isDirectory(live, LinkOption.NOFOLLOW_LINKS)
                        || liveMode(live) != entry.baselineMode())) {
            throw new StaleLiveTreeException(
                    "Live project directory changed since the publication baseline: "
                                             + entry.path());
        }
    }

    private static void requireOwnerPermissionMask(Path publication) throws IOException {
        Path file = publication.resolve(".permission-probe-file");
        Path directory = publication.resolve(".permission-probe-directory");
        boolean fileCreated = false;
        boolean directoryCreated = false;
        try {
            Files.createFile(
                    file,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
            fileCreated = true;
            Files.createDirectory(
                    directory,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
            directoryCreated = true;
            if ((liveMode(file) & 0600) != 0600 || (liveMode(directory) & 0700) != 0700) {
                throw new IOException(
                        "Ship publication requires a POSIX umask that preserves owner permissions");
            }
        } finally {
            if (fileCreated) {
                Files.deleteIfExists(file);
            }
            if (directoryCreated) {
                Files.deleteIfExists(directory);
            }
        }
    }

    private static void deletePermissionProbes(Path publication) throws IOException {
        Files.deleteIfExists(publication.resolve(".permission-probe-file"));
        Files.deleteIfExists(publication.resolve(".permission-probe-directory"));
    }

    private static void applyPhase(Path project, Path candidate, Journal journal)
            throws IOException {
        // Release entry-count and byte quota before consuming it on the candidate side. This
        // keeps every crash-reachable live mixture within the ordinary snapshot limits.
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
                throw new IOException(
                        "Ship cannot remove " + entry.path()
                                      + " because the live project still holds content outside the "
                                      + "publication, such as build output; remove it and resume",
                        e);
            }
        }
        for (int index = 0; index < journal.entries().size(); index++) {
            Entry entry = journal.entries().get(index);
            if (shrinksOnApply(entry)) {
                publishFile(project, candidate, journal, index, entry);
            }
        }
        for (Entry entry : directories(journal, Action.ADD)) {
            Path live = target(project, entry.path());
            Files.createDirectory(live,
                    PosixFilePermissions.asFileAttribute(
                            permissions(workMode(entry))));
            Files.setPosixFilePermissions(live, permissions(workMode(entry)));
        }
        for (int index = 0; index < journal.entries().size(); index++) {
            Entry entry = journal.entries().get(index);
            if (entry.kind() != Kind.FILE
                    || entry.action() == Action.DELETE
                    || shrinksOnApply(entry)) {
                continue;
            }
            publishFile(project, candidate, journal, index, entry);
        }
        for (Entry entry : reversed(journal.entries())) {
            if (entry.kind() == Kind.DIRECTORY && entry.action() != Action.DELETE) {
                Files.setPosixFilePermissions(
                        target(project, entry.path()), permissions(entry.candidateMode()));
            }
        }
    }

    private static void publishFile(
            Path project, Path candidate, Journal journal, int index, Entry entry)
            throws IOException {
        byte[] content = ProjectEvidenceFiles.readMaterial(
                candidate, entry.path(), maximumBytes(entry.candidateSize()));
        if (content.length != entry.candidateSize()
                || !ShipDigest.sha256(content).equals(entry.candidateDigest())
                || liveMode(target(candidate, entry.path())) != entry.candidateMode()) {
            throw new IOException(
                    "Ship candidate file changed during publication: " + entry.path());
        }
        Path live = target(project, entry.path());
        replaceAtomically(
                live,
                stagingPath(journal, index, live),
                content,
                entry.candidateMode());
    }

    private static boolean shrinksOnApply(Entry entry) {
        return entry.kind() == Kind.FILE
                && entry.action() == Action.REPLACE
                && entry.candidateSize() < entry.baselineSize();
    }

    /**
     * Restores the exact baseline per journal entry. Every entry must currently match its baseline side, its candidate
     * side, or be absent where the journal records a creation; anything else blocks recovery before any file is
     * touched, so a live tree edited after a tear is never overwritten.
     */
    private static void rollback(Path project, Path runDirectory, Journal journal)
            throws IOException {
        Path backup = backupDirectory(runDirectory);
        if (!journal.baselineRootIdentity().equals(ProjectEvidenceFiles.rootIdentity(project))) {
            throw new RecoveryBlockedException(
                    "Ship project root no longer matches the publication journal");
        }
        boolean applicationStarted = applicationStarted(runDirectory, journal);
        boolean recoveryStarted = recoveryStarted(runDirectory, journal);
        cleanupStaging(
                project,
                runDirectory.resolve("workspace/candidate"),
                backup,
                journal,
                applicationStarted,
                recoveryStarted);
        final ProjectSnapshot observed;
        try {
            observed = ProjectEvidenceFiles.capture(project);
        } catch (IOException | RuntimeException e) {
            throw recoveryBlocked(
                    "Live project cannot be safely inspected for publication recovery", e);
        }
        if (!journal.baselineRootIdentity().equals(observed.rootIdentity())) {
            throw new RecoveryBlockedException(
                    "Ship project root no longer matches the publication journal");
        }
        final ProjectSnapshot candidate;
        try {
            candidate = ProjectEvidenceFiles.captureStaged(
                    runDirectory.resolve("workspace/candidate"));
        } catch (IOException | RuntimeException e) {
            throw recoveryBlocked(
                    "Ship candidate cannot be safely inspected for publication recovery", e);
        }
        if (!journal.candidateIdentity().equals(ShipWorkspace.materialIdentity(candidate))) {
            throw new RecoveryBlockedException(
                    "Ship candidate no longer matches the publication journal");
        }
        requireUnchangedMaterialEntries(observed, candidate, journal);
        List<Classified> classified = new ArrayList<>();
        for (int index = 0; index < journal.entries().size(); index++) {
            Entry entry = journal.entries().get(index);
            Side side = classify(observed, entry, recoveryStarted);
            if (!applicationStarted && side != Side.BASELINE) {
                throw new RecoveryBlockedException(
                        "Live project changed before Ship publication started: " + entry.path());
            }
            classified.add(new Classified(index, entry, side));
            if (side == Side.CANDIDATE
                    && entry.kind() == Kind.FILE
                    && entry.action() != Action.ADD) {
                readBackup(backup, entry);
            }
        }
        requireNoForeignAddedDirectoryContent(project, classified);
        startRecovery(runDirectory, journal, recoveryStarted);

        // Make candidate-side directories writable before removing candidate additions. The
        // marker makes this private work mode an explicit crash-recoverable journal state.
        for (Classified item : classified) {
            Entry entry = item.entry();
            if (item.side() != Side.CANDIDATE
                    || entry.kind() != Kind.DIRECTORY
                    || entry.action() == Action.DELETE) {
                continue;
            }
            Files.setPosixFilePermissions(
                    target(project, entry.path()), permissions(workMode(entry)));
        }

        // First release candidate-only quota and shrink growing replacements.
        for (Classified item : classified) {
            Entry entry = item.entry();
            if (item.side() != Side.CANDIDATE || entry.kind() != Kind.FILE) {
                continue;
            }
            Path live = target(project, entry.path());
            switch (entry.action()) {
                case ADD -> Files.delete(live);
                case REPLACE -> {
                    if (entry.baselineSize() < entry.candidateSize()) {
                        restoreBackup(backup, journal, item, live);
                    }
                }
                case DELETE -> {
                    // Restored after candidate-only directories release their quota.
                }
            }
        }

        for (Classified item : reversed(classified)) {
            Entry entry = item.entry();
            if (item.side() != Side.CANDIDATE
                    || entry.kind() != Kind.DIRECTORY
                    || entry.action() != Action.ADD) {
                continue;
            }
            Path live = target(project, entry.path());
            try {
                Files.delete(live);
            } catch (DirectoryNotEmptyException e) {
                throw new RecoveryBlockedException(
                        "Ship publication-created directory contains foreign content: "
                                                   + entry.path());
            }
        }

        // Recreate baseline-only directory shape in a writable mode, then restore the remaining
        // files one at a time so recovery never retains the aggregate backup in heap.
        for (Classified item : classified) {
            Entry entry = item.entry();
            if (item.side() == Side.CANDIDATE
                    && entry.kind() == Kind.DIRECTORY
                    && entry.action() == Action.DELETE) {
                Path live = target(project, entry.path());
                if (!Files.exists(live, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectory(
                            live,
                            PosixFilePermissions.asFileAttribute(permissions(workMode(entry))));
                    Files.setPosixFilePermissions(live, permissions(workMode(entry)));
                }
            }
        }
        for (Classified item : classified) {
            Entry entry = item.entry();
            if (item.side() != Side.CANDIDATE
                    || entry.kind() != Kind.FILE
                    || entry.action() == Action.ADD
                    || entry.action() == Action.REPLACE
                            && entry.baselineSize() < entry.candidateSize()) {
                continue;
            }
            restoreBackup(backup, journal, item, target(project, entry.path()));
        }
        for (Classified item : reversed(classified)) {
            Entry entry = item.entry();
            if (item.side() == Side.CANDIDATE
                    && entry.kind() == Kind.DIRECTORY
                    && entry.action() != Action.ADD) {
                Files.setPosixFilePermissions(
                        target(project, entry.path()), permissions(entry.baselineMode()));
            }
        }

        ProjectSnapshot restored = ProjectEvidenceFiles.capture(project);
        if (!journal.baselineRootIdentity().equals(restored.rootIdentity())
                || !journal.baselineIdentity().equals(
                        ShipWorkspace.materialIdentity(restored))) {
            throw new RecoveryBlockedException(
                    "Ship publication rollback did not restore the exact baseline");
        }
    }

    private static void requireUnchangedMaterialEntries(
            ProjectSnapshot observed, ProjectSnapshot candidate, Journal journal)
            throws RecoveryBlockedException {
        Set<String> changed = new HashSet<>();
        journal.entries().forEach(entry -> changed.add(entry.path()));
        for (var item : observed.directories().entrySet()) {
            if (item.getValue().classification() != Classification.MATERIAL
                    || changed.contains(item.getKey())) {
                continue;
            }
            ProjectSnapshot.DirectoryEntry expected = candidate.directories().get(item.getKey());
            if (expected == null
                    || mode(item.getValue().unixMode()) != mode(expected.unixMode())) {
                throw changedOutsidePublication(item.getKey());
            }
        }
        for (var item : candidate.directories().entrySet()) {
            if (item.getValue().classification() == Classification.MATERIAL
                    && !changed.contains(item.getKey())
                    && !observed.directories().containsKey(item.getKey())) {
                throw changedOutsidePublication(item.getKey());
            }
        }
        for (var item : observed.files().entrySet()) {
            if (item.getValue().classification() != Classification.MATERIAL
                    || changed.contains(item.getKey())) {
                continue;
            }
            ProjectSnapshot.FileEntry expected = candidate.files().get(item.getKey());
            if (expected == null
                    || item.getValue().size() != expected.size()
                    || !item.getValue().digest().equals(expected.digest())
                    || mode(item.getValue().unixMode()) != mode(expected.unixMode())) {
                throw changedOutsidePublication(item.getKey());
            }
        }
        for (var item : candidate.files().entrySet()) {
            if (item.getValue().classification() == Classification.MATERIAL
                    && !changed.contains(item.getKey())
                    && !observed.files().containsKey(item.getKey())) {
                throw changedOutsidePublication(item.getKey());
            }
        }
    }

    private static RecoveryBlockedException changedOutsidePublication(String path) {
        return new RecoveryBlockedException(
                "Live project changed outside the Ship publication set: " + path);
    }

    private static void restoreBackup(
            Path backup, Journal journal, Classified item, Path live)
            throws IOException {
        Entry entry = item.entry();
        replaceAtomically(
                live,
                stagingPath(journal, item.index(), live),
                readBackup(backup, entry),
                entry.baselineMode());
    }

    private static Side classify(
            ProjectSnapshot observed, Entry entry, boolean recoveryStarted)
            throws RecoveryBlockedException {
        boolean baseline = matches(observed, entry, Side.BASELINE);
        boolean candidate = matches(observed, entry, Side.CANDIDATE);
        boolean working = matchesWorking(observed, entry)
                && (entry.action() == Action.ADD || recoveryStarted);
        if (baseline && candidate || !baseline && !candidate && !working) {
            throw new RecoveryBlockedException(
                    "Live project entry matches neither one exact publication side: "
                                               + entry.path());
        }
        if (baseline) {
            return Side.BASELINE;
        }
        return Side.CANDIDATE;
    }

    private static boolean matchesWorking(ProjectSnapshot observed, Entry entry) {
        if (entry.kind() != Kind.DIRECTORY) {
            return false;
        }
        ProjectSnapshot.DirectoryEntry directory = observed.directories().get(entry.path());
        if (observed.files().get(entry.path()) != null || directory == null) {
            return false;
        }
        int actual = mode(directory.unixMode());
        int working = workMode(entry);
        if (entry.action() == Action.REPLACE) {
            return actual == working;
        }
        // ADD and DELETE directories are created by this protocol, so their initial mode can be
        // filtered through umask before the explicit chmod closes that crash window.
        return (actual & 0700) == 0700 && (actual & ~working) == 0;
    }

    private static int workMode(Entry entry) {
        int desired = entry.action() == Action.ADD
                ? entry.candidateMode() : entry.baselineMode();
        return desired | 0700;
    }

    private static boolean matches(ProjectSnapshot observed, Entry entry, Side side) {
        boolean expected = side == Side.BASELINE
                ? entry.action() != Action.ADD
                : entry.action() != Action.DELETE;
        ProjectSnapshot.FileEntry file = observed.files().get(entry.path());
        ProjectSnapshot.DirectoryEntry directory = observed.directories().get(entry.path());
        if (!expected) {
            return file == null && directory == null;
        }
        Integer expectedMode = side == Side.BASELINE
                ? entry.baselineMode() : entry.candidateMode();
        if (entry.kind() == Kind.DIRECTORY) {
            return file == null
                    && directory != null
                    && mode(directory.unixMode()) == expectedMode;
        }
        String expectedDigest = side == Side.BASELINE
                ? entry.baselineDigest() : entry.candidateDigest();
        Long expectedSize = side == Side.BASELINE
                ? entry.baselineSize() : entry.candidateSize();
        return directory == null
                && file != null
                && file.size() == expectedSize
                && file.digest().equals(expectedDigest)
                && mode(file.unixMode()) == expectedMode;
    }

    private static byte[] readBackup(Path backup, Entry entry)
            throws IOException {
        byte[] content = Files.readAllBytes(target(backup, entry.path()));
        if (content.length != entry.baselineSize()
                || !ShipDigest.sha256(content).equals(entry.baselineDigest())) {
            throw new RecoveryBlockedException(
                    "Ship publication backup does not match its journal entry: " + entry.path());
        }
        return content;
    }

    private static void requireNoForeignAddedDirectoryContent(
            Path project, List<Classified> classified)
            throws IOException {
        Set<String> expected = new HashSet<>();
        for (Classified item : classified) {
            if (item.side() == Side.CANDIDATE && item.entry().action() != Action.DELETE) {
                expected.add(item.entry().path());
            }
        }
        for (Classified item : classified) {
            Entry entry = item.entry();
            if (item.side() != Side.CANDIDATE
                    || entry.kind() != Kind.DIRECTORY
                    || entry.action() != Action.ADD) {
                continue;
            }
            Path live = target(project, entry.path());
            try (var paths = Files.walk(live)) {
                for (Path path : paths.toList()) {
                    if (path.equals(live)) {
                        continue;
                    }
                    String relative = project.relativize(path).toString()
                            .replace(project.getFileSystem().getSeparator(), "/");
                    if (!expected.contains(relative)) {
                        throw new RecoveryBlockedException(
                                "Ship publication-created directory contains foreign content: "
                                                           + entry.path());
                    }
                }
            }
        }
    }

    private static boolean applicationStarted(Path runDirectory, Journal journal)
            throws IOException {
        return validMarker(
                applicationPath(runDirectory), journal, "application");
    }

    static void startApplication(Path runDirectory, Journal journal) throws IOException {
        writeAtomically(applicationPath(runDirectory), markerPayload(journal));
    }

    private static boolean recoveryStarted(Path runDirectory, Journal journal)
            throws IOException {
        return validMarker(recoveryPath(runDirectory), journal, "recovery");
    }

    private static boolean validMarker(Path marker, Journal journal, String phase)
            throws IOException {
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                || !java.util.Arrays.equals(readBounded(marker), markerPayload(journal))) {
            throw new RecoveryBlockedException(
                    "Ship publication " + phase + " marker is invalid");
        }
        return true;
    }

    static void startRecovery(
            Path runDirectory, Journal journal, boolean alreadyStarted)
            throws IOException {
        if (!alreadyStarted) {
            writeAtomically(recoveryPath(runDirectory), markerPayload(journal));
        }
    }

    private static byte[] markerPayload(Journal journal) {
        return (journal.runId() + '\n'
                + journal.attempt() + '\n'
                + journal.baselineRootIdentity() + '\n'
                + journal.baselineIdentity() + '\n')
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void cleanupStaging(
            Path project,
            Path candidate,
            Path backup,
            Journal journal,
            boolean applicationStarted,
            boolean recoveryStarted)
            throws IOException {
        List<Path> owned = new ArrayList<>();
        for (int index = 0; index < journal.entries().size(); index++) {
            Entry entry = journal.entries().get(index);
            if (entry.kind() != Kind.FILE) {
                continue;
            }
            Path live = target(project, entry.path());
            Path staging = stagingPath(journal, index, live);
            if (!Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            String relative = stagingRelativePath(journal, index, entry);
            long maximum = Math.max(
                    entry.baselineSize() == null ? 0 : entry.baselineSize(),
                    entry.candidateSize() == null ? 0 : entry.candidateSize());
            final byte[] actual;
            final int actualMode;
            try {
                actual = ProjectEvidenceFiles.readMaterial(
                        project, relative, maximumBytes(maximum));
                actualMode = liveMode(staging);
            } catch (IOException | RuntimeException e) {
                throw recoveryBlocked(
                        "Ship publication staging file is not safely recoverable: " + relative,
                        e);
            }
            boolean matches = false;
            if (applicationStarted
                    && !recoveryStarted
                    && entry.action() != Action.DELETE) {
                byte[] expected = verifiedCandidate(candidate, entry);
                matches = expected != null
                        && stagingPrefix(
                                actual, actualMode, expected, entry.candidateMode());
            }
            if (!matches && recoveryStarted && entry.action() != Action.ADD) {
                Path source = target(backup, entry.path());
                if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] expected = readBackup(backup, entry);
                    matches = stagingPrefix(
                            actual, actualMode, expected, entry.baselineMode());
                }
            }
            if (!matches
                    && applicationStarted
                    && !recoveryStarted
                    && entry.action() == Action.DELETE
                    && actual.length == 0) {
                Path source = target(backup, entry.path());
                if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] expected = readBackup(backup, entry);
                    matches = stagingPrefix(
                            actual, actualMode, expected, entry.baselineMode());
                }
            }
            if (!matches) {
                throw new RecoveryBlockedException(
                        "Ship publication staging path is occupied by foreign content: "
                                                   + relative);
            }
            owned.add(staging);
        }
        for (Path staging : owned) {
            Files.delete(staging);
        }
    }

    private static boolean stagingPrefix(
            byte[] actual, int actualMode, byte[] expected, int expectedMode) {
        if (actual.length > expected.length) {
            return false;
        }
        for (int index = 0; index < actual.length; index++) {
            if (actual[index] != expected[index]) {
                return false;
            }
        }
        return actual.length < expected.length
                ? actualMode == PRIVATE_STAGING_MODE
                : actualMode == PRIVATE_STAGING_MODE || actualMode == expectedMode;
    }

    private static byte[] verifiedCandidate(Path candidate, Entry entry) {
        try {
            byte[] expected = ProjectEvidenceFiles.readMaterial(
                    candidate, entry.path(), maximumBytes(entry.candidateSize()));
            return expected.length == entry.candidateSize()
                    && ShipDigest.sha256(expected).equals(entry.candidateDigest())
                    && liveMode(target(candidate, entry.path())) == entry.candidateMode()
                            ? expected : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void replaceAtomically(
            Path live, Path staging, byte[] content, int targetMode)
            throws IOException {
        boolean created = false;
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    staging,
                    Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(
                            permissions(PRIVATE_STAGING_MODE)))) {
                created = true;
                Files.setPosixFilePermissions(staging, permissions(PRIVATE_STAGING_MODE));
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.setPosixFilePermissions(staging, permissions(targetMode));
            move(staging, live);
            moved = true;
        } finally {
            if (created && !moved) {
                Files.deleteIfExists(staging);
            }
        }
    }

    static Path stagingPath(Journal journal, int index, Path live) {
        Entry entry = journal.entries().get(index);
        if (entry.kind() != Kind.FILE || live.getParent() == null) {
            throw new IllegalArgumentException("Ship file publication staging target is invalid");
        }
        return live.resolveSibling(stagingFileName(journal, index));
    }

    private static String stagingRelativePath(Journal journal, int index, Entry entry) {
        int separator = entry.path().lastIndexOf('/');
        String name = stagingFileName(journal, index);
        return separator < 0 ? name : entry.path().substring(0, separator + 1) + name;
    }

    private static String stagingFileName(Journal journal, int index) {
        return STAGING_PREFIX
               + journal.runId().substring("ship-".length())
               + '-' + Integer.toString(journal.attempt(), Character.MAX_RADIX)
               + '-' + Integer.toString(index, Character.MAX_RADIX)
               + STAGING_SUFFIX;
    }

    private static boolean hasStaging(Path project, Journal journal) throws IOException {
        for (int index = 0; index < journal.entries().size(); index++) {
            Entry entry = journal.entries().get(index);
            if (entry.kind() == Kind.FILE
                    && Files.exists(
                            stagingPath(journal, index, target(project, entry.path())),
                            LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
        }
        return false;
    }

    private static void probeStagingPaths(Path project, Journal journal) throws IOException {
        for (int index = 0; index < journal.entries().size(); index++) {
            Entry entry = journal.entries().get(index);
            if (entry.kind() != Kind.FILE) {
                continue;
            }
            Path staging = stagingPath(journal, index, target(project, entry.path()));
            // Candidate-only parents do not exist yet. Their scratch path cannot strand a
            // baseline restore, because a failed ADD is removed rather than replaced.
            if (!Files.isDirectory(staging.getParent(), LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            boolean created = false;
            Throwable probeFailure = null;
            try (FileChannel ignored = FileChannel.open(
                    staging,
                    Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(
                            permissions(PRIVATE_STAGING_MODE)))) {
                created = true;
                Files.setPosixFilePermissions(staging, permissions(PRIVATE_STAGING_MODE));
            } catch (IOException | RuntimeException e) {
                probeFailure = e;
            }
            if (created) {
                try {
                    Files.delete(staging);
                } catch (IOException cleanupFailure) {
                    if (probeFailure != null) {
                        cleanupFailure.addSuppressed(probeFailure);
                    }
                    throw new StagingCleanupException(
                            "Ship could not remove publication path probe: " + staging,
                            cleanupFailure);
                }
            }
            if (probeFailure instanceof IOException failure) {
                throw failure;
            }
            if (probeFailure instanceof RuntimeException failure) {
                throw failure;
            }
        }
    }

    private static void clean(Path runDirectory) throws IOException {
        deleteTree(backupDirectory(runDirectory));
        Files.deleteIfExists(recordPath(runDirectory));
        Files.deleteIfExists(applicationPath(runDirectory));
        Files.deleteIfExists(recoveryPath(runDirectory));
        Files.deleteIfExists(journalPath(runDirectory));
    }

    private static Path applicationPath(Path runDirectory) {
        return publicationDirectory(runDirectory).resolve(APPLICATION);
    }

    private static Path recoveryPath(Path runDirectory) {
        return publicationDirectory(runDirectory).resolve(RECOVERY);
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
        requireStagingBindings(decoded, null, null);
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

    private static void requireStagingBindings(
            Journal journal, ProjectSnapshot baseline, ProjectSnapshot candidate)
            throws UnsupportedChangeException {
        Set<String> targets = new HashSet<>();
        journal.entries().forEach(entry -> targets.add(entry.path()));
        Set<String> staging = new HashSet<>();
        ShipTreePolicy policy = ShipTreePolicy.current();
        for (int index = 0; index < journal.entries().size(); index++) {
            Entry entry = journal.entries().get(index);
            if (entry.kind() != Kind.FILE) {
                continue;
            }
            String relative = stagingRelativePath(journal, index, entry);
            if (!staging.add(relative)
                    || targets.contains(relative)
                    || policy.classify(relative) != Classification.MATERIAL
                    || contains(baseline, relative)
                    || contains(candidate, relative)) {
                throw new UnsupportedChangeException(
                        "Ship cannot bind collision-free publication scratch for "
                                                     + entry.path());
            }
        }
    }

    private static boolean contains(ProjectSnapshot snapshot, String path) {
        return snapshot != null
                && (snapshot.files().containsKey(path)
                        || snapshot.directories().containsKey(path));
    }

    private static <T> List<T> reversed(List<T> entries) {
        List<T> copy = new ArrayList<>(entries);
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

    private static int maximumBytes(long size) {
        return Math.toIntExact(Math.max(1, size));
    }

    private static RecoveryBlockedException recoveryBlocked(
            String message, Throwable cause) {
        RecoveryBlockedException blocked = new RecoveryBlockedException(message);
        blocked.initCause(cause);
        return blocked;
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
            byte[] encoded = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
            if (encoded.length == 0 || encoded.length > MAX_DOCUMENT_BYTES) {
                throw new IOException(
                        "Ship publication document exceeds the durable read limit");
            }
            return encoded;
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
            Files.setPosixFilePermissions(
                    temporary, PosixFilePermissions.fromString("rw-------"));
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

    enum Kind {
        FILE,
        DIRECTORY
    }

    enum Action {
        ADD,
        REPLACE,
        DELETE
    }

    private enum Side {
        BASELINE,
        CANDIDATE
    }

    private record Classified(int index, Entry entry, Side side) {
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
            path = ShipTreePolicy.requireCanonicalRelativePath(path);
            Objects.requireNonNull(kind, "entry kind");
            Objects.requireNonNull(action, "entry action");
            boolean baseline = action != Action.ADD;
            boolean candidate = action != Action.DELETE;
            if (kind == Kind.FILE) {
                require(baseline == (baselineDigest != null && baselineSize != null
                        && baselineMode != null));
                require(candidate == (candidateDigest != null && candidateSize != null
                        && candidateMode != null));
                require(!baseline || validFileSide(
                        baselineDigest, baselineSize, baselineMode));
                require(!candidate || validFileSide(
                        candidateDigest, candidateSize, candidateMode));
                require(action != Action.REPLACE
                        || !baselineDigest.equals(candidateDigest)
                        || !baselineSize.equals(candidateSize)
                        || !baselineMode.equals(candidateMode));
            } else {
                require(baselineDigest == null && baselineSize == null
                        && candidateDigest == null && candidateSize == null);
                require(baseline == (baselineMode != null));
                require(candidate == (candidateMode != null));
                require(!baseline || validMode(baselineMode));
                require(!candidate || validMode(candidateMode));
                require(action != Action.REPLACE || !baselineMode.equals(candidateMode));
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

        private static boolean validFileSide(String digest, long size, int mode) {
            return ShipDigest.isSha256(digest)
                    && size >= 0
                    && size <= ShipTreePolicy.DEFAULT_MAX_FILE_BYTES
                    && validMode(mode);
        }

        private static boolean validMode(int mode) {
            return mode >= 0 && mode <= 0777;
        }
    }

    record Journal(
            int schemaVersion,
            String runId,
            int attempt,
            String baselineRootIdentity,
            String baselineIdentity,
            String candidateIdentity,
            String createdAt,
            List<Entry> entries) {

        Journal {
            if (schemaVersion != SCHEMA_VERSION
                    || !ShipRun.isRunId(runId)
                    || attempt < 0
                    || !ShipDigest.isSha256(baselineRootIdentity)
                    || !ShipDigest.isSha256(baselineIdentity)
                    || !ShipDigest.isSha256(candidateIdentity)
                    || createdAt == null
                    || createdAt.isBlank()) {
                throw new IllegalArgumentException("Invalid Ship publication journal identity");
            }
            entries = List.copyOf(Objects.requireNonNull(entries, "journal entries"));
            if (entries.size() > 2 * ShipTreePolicy.DEFAULT_MAX_FILE_COUNT) {
                throw new IllegalArgumentException("Ship publication journal has too many entries");
            }
            String previous = null;
            for (Entry entry : entries) {
                if (previous != null && previous.compareTo(entry.path()) >= 0) {
                    throw new IllegalArgumentException(
                            "Ship publication journal entries are not strictly ordered");
                }
                previous = entry.path();
            }
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

    /** A live scratch probe was created but could not be removed safely. */
    private static final class StagingCleanupException extends IOException {

        private static final long serialVersionUID = 1L;

        private StagingCleanupException(String message, Throwable cause) {
            super(message, cause);
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

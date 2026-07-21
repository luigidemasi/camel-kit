package io.github.luigidemasi.camelkit.ship.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.github.luigidemasi.camelkit.ship.ShipDigest;

/**
 * Controller-owned authenticated append-only storage for one Ship run.
 *
 * <p>
 * The store validates storage structure and authority-head continuity only. Lifecycle transitions are exclusively owned
 * by {@code camel-kit-ship-controller}; this class neither checks nor advances them.
 *
 * <p>
 * Replay detects modification relative to the current uncompromised local key and authenticated head. It cannot detect
 * coherent rollback of the complete run root, including that key and head.
 */
final class FileShipEventStore {

    static final int MAX_EVENT_BYTES = 4 * 1024 * 1024;
    static final int MAX_EVENT_COUNT = 4_096;
    static final long MAX_HISTORY_BYTES = 64L * 1024 * 1024;

    private static final int MAX_HEAD_BYTES = 4 * 1024;
    private static final int KEY_BYTES = 32;
    private static final Pattern EVENT_FILE = Pattern.compile("([0-9]{20})-([0-9a-f]{64})\\.json");
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS
            = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS
            = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> EVENT_PERMISSIONS
            = PosixFilePermissions.fromString("r--------");
    private static final byte[] EVENT_MAC_DOMAIN
            = "camel-kit.ship.event-mac.v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final byte[] HEAD_MAC_DOMAIN
            = "camel-kit.ship.current-head-mac.v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShipRunId runId;
    private final Path stateRoot;
    private final Path runRoot;
    private final Path eventsRoot;
    private final Path temporaryRoot;
    private final Path secretPath;
    private final Path headPath;

    private FileShipEventStore(Path requestedStateRoot, ShipRunId runId, OpenMode mode)
                                                                                        throws IOException {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.runRoot = ShipControllerPaths.requireRunRoot(requestedStateRoot, runId);
        this.stateRoot = runRoot.getParent();
        this.eventsRoot = runRoot.resolve("events");
        this.temporaryRoot = runRoot.resolve("tmp");
        this.secretPath = runRoot.resolve("secret.key");
        this.headPath = runRoot.resolve("current-head.json");
        initializeDirectories(mode);
        try (ShipOperationLock.Lease ignored
                = ShipOperationLock.acquireRun(stateRoot, runId.storageId())) {
            cleanTemporaryFiles();
            byte[] key = mode == OpenMode.CREATE ? createSecret() : loadSecret();
            try {
                if (mode == OpenMode.CREATE) {
                    commitHead(key, ShipEventHead.genesis(), true);
                } else {
                    replayLocked(key);
                }
            } finally {
                Arrays.fill(key, (byte) 0);
            }
        }
    }

    static FileShipEventStore create(Path stateRoot, String runId) throws IOException {
        return create(stateRoot, ShipRunId.fromStorageId(runId));
    }

    static FileShipEventStore create(Path stateRoot, ShipRunId runId) throws IOException {
        return new FileShipEventStore(stateRoot, runId, OpenMode.CREATE);
    }

    static FileShipEventStore open(Path stateRoot, String runId) throws IOException {
        return open(stateRoot, ShipRunId.fromStorageId(runId));
    }

    static FileShipEventStore open(Path stateRoot, ShipRunId runId) throws IOException {
        return new FileShipEventStore(stateRoot, runId, OpenMode.OPEN);
    }

    ShipEvent appendIfLatest(ShipEventHead expectedHead, ShipEventDraft draft) throws IOException {
        Objects.requireNonNull(expectedHead, "expectedHead");
        Objects.requireNonNull(draft, "draft");
        if (!Objects.equals(expectedHead.authorityHead(), draft.previousAuthorityHead())) {
            throw new IllegalArgumentException(
                    "Ship event draft predecessor must equal the expected authority head");
        }
        try (ShipOperationLock.Lease ignored
                = ShipOperationLock.acquireRun(stateRoot, runId.storageId())) {
            cleanTemporaryFiles();
            byte[] key = loadSecret();
            try {
                List<ShipEvent> history = replayLocked(key);
                ShipEventHead actualHead = headOf(history);
                if (!sameHead(expectedHead, actualHead)) {
                    throw new ShipEventHeadMismatchException(expectedHead, actualHead);
                }

                long sequence;
                try {
                    sequence = Math.incrementExact(actualHead.sequence());
                } catch (ArithmeticException e) {
                    throw new ShipEventStoreException("Ship event sequence is exhausted", e);
                }
                ShipEvent previous = history.isEmpty() ? null : history.get(history.size() - 1);
                ShipEventCodec.UnsignedEvent unsigned = new ShipEventCodec.UnsignedEvent(
                        ShipEvent.SCHEMA_VERSION,
                        runId,
                        sequence,
                        actualHead.eventDigest(),
                        draft.type(),
                        previous == null ? null : previous.state(),
                        draft.state(),
                        draft.previousAuthorityHead(),
                        draft.authorityHead(),
                        draft.occurredAt(),
                        draft.payload());
                byte[] unsignedBytes = ShipEventCodec.encodeUnsigned(unsigned);
                String eventDigest = ShipDigest.sha256(unsignedBytes);
                String eventHmac = hmac(key, EVENT_MAC_DOMAIN, unsignedBytes);
                ShipEvent event = new ShipEvent(
                        unsigned.schemaVersion(),
                        unsigned.runId(),
                        unsigned.sequence(),
                        unsigned.previousEventDigest(),
                        unsigned.type(),
                        unsigned.previousState(),
                        unsigned.state(),
                        unsigned.previousAuthorityHead(),
                        unsigned.authorityHead(),
                        unsigned.occurredAt(),
                        unsigned.payload(),
                        eventDigest,
                        eventHmac);
                byte[] encoded = ShipEventCodec.encode(event);
                if (encoded.length > MAX_EVENT_BYTES) {
                    throw new ShipEventStoreException(
                            "Ship event exceeds " + MAX_EVENT_BYTES + " bytes");
                }
                ShipEventCodec.decode(encoded);
                long historyBytes = 0;
                for (ShipEvent item : history) {
                    historyBytes = checkedAdd(historyBytes, ShipEventCodec.encode(item).length);
                }
                requireAppendBudget(history.size(), historyBytes, encoded.length);
                commitEvent(event, encoded);
                commitHead(key, ShipEventHead.from(event), false);
                return event;
            } finally {
                Arrays.fill(key, (byte) 0);
            }
        }
    }

    List<ShipEvent> replay() throws IOException {
        try (ShipOperationLock.Lease ignored
                = ShipOperationLock.acquireRun(stateRoot, runId.storageId())) {
            cleanTemporaryFiles();
            byte[] key = loadSecret();
            try {
                return replayLocked(key);
            } finally {
                Arrays.fill(key, (byte) 0);
            }
        }
    }

    ShipEventHead currentHead() throws IOException {
        return headOf(replay());
    }

    private List<ShipEvent> replayLocked(byte[] key) throws IOException {
        verifyManagedDirectories();
        ShipEventHead durableHead = readHead(key);
        List<Path> files = eventFiles();
        List<ShipEvent> result = new ArrayList<>(files.size());
        ShipEvent previous = null;
        long expectedSequence = 1;
        long historyBytes = 0;
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            Matcher matcher = EVENT_FILE.matcher(fileName);
            if (!matcher.matches()) {
                throw new ShipEventStoreException(
                        "Unexpected entry in Ship event directory: " + fileName);
            }
            long fileSequence;
            try {
                fileSequence = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new ShipEventStoreException(
                        "Invalid Ship event sequence in filename: " + fileName, e);
            }
            if (fileSequence != expectedSequence) {
                throw new ShipEventStoreException(
                        "Ship event sequence gap: expected " + expectedSequence
                                                  + " but found " + fileSequence);
            }

            verifyRegularFile(file, EVENT_PERMISSIONS, "Ship event");
            long size = Files.size(file);
            if (size > MAX_EVENT_BYTES) {
                throw historyBudgetExceeded();
            }
            historyBytes = checkedAdd(historyBytes, size);
            requireHistoryBudget(result.size() + 1, historyBytes);
            byte[] bytes = readNoFollow(file, MAX_EVENT_BYTES, EVENT_PERMISSIONS, "Ship event");
            ShipEvent event = ShipEventCodec.decode(bytes);
            if (!runId.equals(event.runId())) {
                throw new ShipEventStoreException("Ship event belongs to another run");
            }
            if (event.sequence() != expectedSequence) {
                throw new ShipEventStoreException(
                        "Ship event body sequence does not match its ledger position");
            }

            String predecessorDigest = previous == null
                    ? ShipEventHead.GENESIS_DIGEST : previous.eventDigest();
            AuthorityHeadId predecessorAuthority = previous == null
                    ? null : previous.authorityHead();
            ShipState predecessorState = previous == null ? null : previous.state();
            if (!constantTimeEquals(predecessorDigest, event.previousEventDigest())) {
                throw new ShipEventStoreException(
                        "Ship event digest chain mismatch at sequence " + expectedSequence);
            }
            if (!Objects.equals(predecessorAuthority, event.previousAuthorityHead())) {
                throw new ShipEventStoreException(
                        "Ship authority-head chain mismatch at sequence " + expectedSequence);
            }
            if (predecessorState != event.previousState()) {
                throw new ShipEventStoreException(
                        "Ship predecessor-state record mismatch at sequence " + expectedSequence);
            }

            byte[] unsigned = ShipEventCodec.encodeUnsigned(event);
            String computedDigest = ShipDigest.sha256(unsigned);
            if (!constantTimeEquals(computedDigest, event.eventDigest())) {
                throw new ShipEventStoreException(
                        "Ship event digest mismatch at sequence " + expectedSequence);
            }
            if (!matcher.group(2).equals(digestHex(event.eventDigest()))) {
                throw new ShipEventStoreException(
                        "Ship event filename digest mismatch at sequence " + expectedSequence);
            }
            String computedHmac = hmac(key, EVENT_MAC_DOMAIN, unsigned);
            if (!constantTimeEquals(computedHmac, event.hmac())) {
                throw new ShipEventStoreException(
                        "Ship event HMAC mismatch at sequence " + expectedSequence);
            }

            result.add(event);
            previous = event;
            expectedSequence++;
        }
        ShipEventHead eventHead = headOf(result);
        if (!sameHead(durableHead, eventHead)) {
            if (isRecoverableCommittedTail(durableHead, result, eventHead)) {
                // The immutable, authenticated event was forced before the mutable head update.
                // Advancing only that head is the sole bounded interruption recovery.
                commitHead(key, eventHead, false);
            } else {
                throw new ShipEventStoreException(
                        "Authenticated Ship current head does not match the event ledger; "
                                                  + "truncation, replacement, reordering, or a stale head was detected");
            }
        }
        return List.copyOf(result);
    }

    private boolean isRecoverableCommittedTail(
            ShipEventHead durableHead, List<ShipEvent> history, ShipEventHead eventHead) {
        long recoverySequence;
        try {
            recoverySequence = Math.incrementExact(durableHead.sequence());
        } catch (ArithmeticException e) {
            return false;
        }
        if (eventHead.sequence() != recoverySequence || history.isEmpty()) {
            return false;
        }
        ShipEvent tail = history.get(history.size() - 1);
        if (!constantTimeEquals(durableHead.eventDigest(), tail.previousEventDigest())
                || !Objects.equals(durableHead.authorityHead(), tail.previousAuthorityHead())) {
            return false;
        }
        if (durableHead.sequence() == 0) {
            return sameHead(durableHead, ShipEventHead.genesis());
        }
        if (durableHead.sequence() > history.size()) {
            return false;
        }
        ShipEvent prefixTail = history.get(Math.toIntExact(durableHead.sequence() - 1));
        return sameHead(durableHead, ShipEventHead.from(prefixTail));
    }

    private ShipEventHead readHead(byte[] key) throws IOException {
        if (!Files.exists(headPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ShipEventStoreException("Ship current head is missing");
        }
        byte[] bytes = readNoFollow(
                headPath, MAX_HEAD_BYTES, PRIVATE_FILE_PERMISSIONS, "Ship current head");
        ShipEventCodec.HeadEnvelope envelope = ShipEventCodec.decodeHead(bytes);
        if (!runId.equals(envelope.runId())) {
            throw new ShipEventStoreException("Ship current head belongs to another run");
        }
        String expectedHmac = hmac(
                key, HEAD_MAC_DOMAIN, ShipEventCodec.encodeUnsignedHead(runId, envelope.head()));
        if (!constantTimeEquals(expectedHmac, envelope.hmac())) {
            throw new ShipEventStoreException("Ship current-head HMAC mismatch");
        }
        return envelope.head();
    }

    private void commitEvent(ShipEvent event, byte[] bytes) throws IOException {
        Path target = eventsRoot.resolve(eventFileName(event.sequence(), event.eventDigest()));
        Path temporary = createPrivateTemporaryFile(".event-" + event.sequence() + "-");
        boolean committed = false;
        try {
            writeAndForce(temporary, bytes);
            setPermissions(temporary, EVENT_PERMISSIONS);
            forceFileMetadata(temporary);
            commitNoReplace(temporary, target, EVENT_PERMISSIONS, "Ship event");
            committed = true;
        } finally {
            if (!committed) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void commitHead(byte[] key, ShipEventHead head, boolean create) throws IOException {
        boolean exists = Files.exists(headPath, LinkOption.NOFOLLOW_LINKS);
        if (create == exists) {
            throw new ShipEventStoreException(
                    create ? "Ship current head already exists" : "Ship current head is missing");
        }
        if (!create) {
            verifyRegularFile(headPath, PRIVATE_FILE_PERMISSIONS, "Ship current head");
        }
        String mac = hmac(key, HEAD_MAC_DOMAIN, ShipEventCodec.encodeUnsignedHead(runId, head));
        byte[] encoded = ShipEventCodec.encodeHead(runId, head, mac);
        Path temporary = createPrivateTemporaryFile(".head-");
        boolean committed = false;
        try {
            writeAndForce(temporary, encoded);
            if (create) {
                commitNoReplace(
                        temporary, headPath, PRIVATE_FILE_PERMISSIONS, "Ship current head");
            } else {
                try {
                    Files.move(
                            temporary,
                            headPath,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    throw new ShipEventStoreException(
                            "Filesystem does not support atomic Ship current-head commits", e);
                }
                forceDirectory(runRoot);
                forceDirectory(temporaryRoot);
                verifyRegularFile(headPath, PRIVATE_FILE_PERMISSIONS, "Ship current head");
            }
            committed = true;
        } finally {
            if (!committed) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private byte[] createSecret() throws IOException {
        if (Files.exists(secretPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ShipEventStoreException("Ship event-store key already exists");
        }
        byte[] generated = new byte[KEY_BYTES];
        RANDOM.nextBytes(generated);
        Path temporary = createPrivateTemporaryFile(".secret-");
        boolean committed = false;
        try {
            writeAndForce(temporary, generated);
            commitNoReplace(
                    temporary, secretPath, PRIVATE_FILE_PERMISSIONS, "Ship event-store key");
            committed = true;
        } finally {
            Arrays.fill(generated, (byte) 0);
            if (!committed) {
                Files.deleteIfExists(temporary);
            }
        }
        return loadSecret();
    }

    private byte[] loadSecret() throws IOException {
        if (!Files.exists(secretPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ShipEventStoreException("Ship event-store key is missing");
        }
        byte[] key = readNoFollow(
                secretPath, KEY_BYTES, PRIVATE_FILE_PERMISSIONS, "Ship event-store key");
        if (key.length != KEY_BYTES) {
            Arrays.fill(key, (byte) 0);
            throw new ShipEventStoreException(
                    "Ship event-store key must contain exactly " + KEY_BYTES + " bytes");
        }
        return key;
    }

    private void commitNoReplace(
            Path temporary,
            Path target,
            Set<PosixFilePermission> permissions,
            String description)
            throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ShipEventStoreException(description + " already exists");
        }
        boolean linked = false;
        try {
            // Linking a fully forced inode is an atomic create-only publication on the same filesystem.
            Files.createLink(target, temporary);
            linked = true;
            Files.delete(temporary);
            forceDirectory(target.getParent());
            forceDirectory(temporaryRoot);
            verifyRegularFile(target, permissions, description);
        } catch (FileAlreadyExistsException e) {
            throw new ShipEventStoreException(description + " already exists", e);
        } catch (UnsupportedOperationException e) {
            throw new ShipEventStoreException(
                    "Filesystem does not support atomic no-replace Ship commits", e);
        } finally {
            if (linked) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void initializeDirectories(OpenMode mode) throws IOException {
        rejectSymbolicComponents(stateRoot);
        boolean stateRootCreated = false;
        if (!Files.exists(stateRoot, LinkOption.NOFOLLOW_LINKS)) {
            if (mode == OpenMode.OPEN) {
                throw new ShipEventStoreException("Ship state root does not exist");
            }
            try {
                Files.createDirectories(
                        stateRoot, PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
                stateRootCreated = true;
            } catch (UnsupportedOperationException e) {
                throw new ShipEventStoreException(
                        "Ship event storage requires POSIX permissions", e);
            }
        }
        rejectSymbolicComponents(stateRoot);
        verifyDirectory(stateRoot, null, "Ship state root");
        if (stateRootCreated) {
            forceDirectory(stateRoot.getParent());
        }

        if (mode == OpenMode.CREATE) {
            createRunDirectory();
            createManagedDirectory(eventsRoot, "Ship event directory");
            createManagedDirectory(temporaryRoot, "Ship temporary directory");
        } else {
            verifyDirectory(runRoot, stateRoot, "Ship run directory");
            verifyDirectory(eventsRoot, runRoot, "Ship event directory");
            verifyDirectory(temporaryRoot, runRoot, "Ship temporary directory");
        }
    }

    private void createRunDirectory() throws IOException {
        try {
            Files.createDirectory(runRoot, fileAttributes(DIRECTORY_PERMISSIONS));
            forceDirectory(stateRoot);
        } catch (FileAlreadyExistsException e) {
            throw new ShipEventStoreException("Ship run already exists: " + runId.storageId(), e);
        }
        verifyDirectory(runRoot, stateRoot, "Ship run directory");
    }

    private void createManagedDirectory(Path directory, String description) throws IOException {
        try {
            Files.createDirectory(directory, fileAttributes(DIRECTORY_PERMISSIONS));
            forceDirectory(directory.getParent());
        } catch (FileAlreadyExistsException e) {
            throw new ShipEventStoreException(description + " already exists", e);
        }
        verifyDirectory(directory, runRoot, description);
    }

    private void verifyManagedDirectories() throws IOException {
        rejectSymbolicComponents(stateRoot);
        verifyDirectory(stateRoot, null, "Ship state root");
        verifyDirectory(runRoot, stateRoot, "Ship run directory");
        verifyDirectory(eventsRoot, runRoot, "Ship event directory");
        verifyDirectory(temporaryRoot, runRoot, "Ship temporary directory");
    }

    private void verifyDirectory(Path directory, Path parent, String description) throws IOException {
        BasicFileAttributes attributes = attributesNoFollow(directory, description);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new ShipEventStoreException(description + " must be a real directory");
        }
        verifyPermissions(directory, DIRECTORY_PERMISSIONS, description);
        if (parent != null) {
            verifyOwner(directory, parent, description);
        }
    }

    private void cleanTemporaryFiles() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(temporaryRoot)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                BasicFileAttributes attributes = attributesNoFollow(path, "Ship temporary entry");
                boolean known = (name.startsWith(".event-")
                        || name.startsWith(".head-")
                        || name.startsWith(".secret-")) && name.endsWith(".pending");
                if (!known || !attributes.isRegularFile() || attributes.isSymbolicLink()) {
                    throw new ShipEventStoreException(
                            "Unexpected entry in Ship temporary directory: " + name);
                }
                verifyOwner(path, temporaryRoot, "Ship temporary entry");
                Files.delete(path);
            }
        }
        forceDirectory(temporaryRoot);
    }

    private List<Path> eventFiles() throws IOException {
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(eventsRoot)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (!EVENT_FILE.matcher(name).matches()) {
                    throw new ShipEventStoreException(
                            "Unexpected entry in Ship event directory: " + name);
                }
                BasicFileAttributes attributes = attributesNoFollow(path, "Ship event entry");
                if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                    throw new ShipEventStoreException(
                            "Ship event entry is not a regular file: " + name);
                }
                result.add(path);
                requireHistoryBudget(result.size(), 0);
            }
        }
        result.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return result;
    }

    private Path createPrivateTemporaryFile(String prefix) throws IOException {
        Path result = Files.createTempFile(
                temporaryRoot,
                prefix,
                ".pending",
                fileAttributes(PRIVATE_FILE_PERMISSIONS));
        verifyRegularFile(result, PRIVATE_FILE_PERMISSIONS, "Ship temporary file");
        return result;
    }

    private void writeAndForce(Path path, byte[] bytes) throws IOException {
        Object fileKey = verifyRegularFile(
                path, PRIVATE_FILE_PERMISSIONS, "Ship temporary file");
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        requireSameEntry(
                path, fileKey, PRIVATE_FILE_PERMISSIONS, "Ship temporary file");
    }

    private byte[] readNoFollow(
            Path path,
            int maximumBytes,
            Set<PosixFilePermission> permissions,
            String description)
            throws IOException {
        Object fileKey = verifyRegularFile(path, permissions, description);
        try (FileChannel channel = FileChannel.open(
                path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            int total = 0;
            int read;
            while ((read = channel.read(buffer)) != -1) {
                total = Math.addExact(total, read);
                if (total > maximumBytes) {
                    throw new ShipEventStoreException(
                            description + " exceeds " + maximumBytes + " bytes");
                }
                output.write(buffer.array(), 0, read);
                buffer.clear();
            }
            requireSameEntry(path, fileKey, permissions, description);
            return output.toByteArray();
        } catch (ArithmeticException e) {
            throw new ShipEventStoreException(description + " size overflow", e);
        }
    }

    private Object verifyRegularFile(
            Path path, Set<PosixFilePermission> permissions, String description)
            throws IOException {
        BasicFileAttributes attributes = attributesNoFollow(path, description);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.fileKey() == null) {
            throw new ShipEventStoreException(description + " must be a real regular file");
        }
        verifyPermissions(path, permissions, description);
        verifyOwner(path, runRoot, description);
        Object links;
        try {
            links = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
        } catch (UnsupportedOperationException e) {
            throw new ShipEventStoreException(
                    description + " filesystem lacks hard-link identity", e);
        }
        if (!(links instanceof Number count) || count.longValue() != 1) {
            throw new ShipEventStoreException(description + " must have exactly one hard link");
        }
        return attributes.fileKey();
    }

    private void requireSameEntry(
            Path path,
            Object expectedFileKey,
            Set<PosixFilePermission> permissions,
            String description)
            throws IOException {
        Object actual = verifyRegularFile(path, permissions, description);
        if (!expectedFileKey.equals(actual)) {
            throw new ShipEventStoreException(description + " changed while it was open");
        }
    }

    private BasicFileAttributes attributesNoFollow(Path path, String description) throws IOException {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new ShipEventStoreException("Cannot inspect " + description, e);
        }
    }

    private void rejectSymbolicComponents(Path path) throws IOException {
        Path current = path.toAbsolutePath().normalize().getRoot();
        for (Path component : path.toAbsolutePath().normalize()) {
            current = current == null ? component : current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new ShipEventStoreException(
                        "Managed Ship path contains a symbolic link: " + current);
            }
        }
    }

    private void verifyPermissions(
            Path path, Set<PosixFilePermission> expected, String description)
            throws IOException {
        if (Files.getFileAttributeView(
                path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
            == null) {
            throw new ShipEventStoreException(
                    description + " requires POSIX permission support");
        }
        Set<PosixFilePermission> actual = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
        if (!actual.equals(expected)) {
            throw new ShipEventStoreException(
                    description + " has unsafe permissions "
                                              + PosixFilePermissions.toString(actual) + "; expected "
                                              + PosixFilePermissions.toString(expected));
        }
    }

    private void verifyOwner(Path path, Path parent, String description) throws IOException {
        PosixFileAttributes attributes = Files.readAttributes(
                path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        PosixFileAttributes parentAttributes = Files.readAttributes(
                parent, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.owner().equals(parentAttributes.owner())) {
            throw new ShipEventStoreException(
                    description + " owner differs from its protected parent");
        }
    }

    private void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        if (Files.getFileAttributeView(
                path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
            == null) {
            throw new ShipEventStoreException("Ship event storage requires POSIX permissions");
        }
        Files.setPosixFilePermissions(path, permissions);
    }

    private FileAttribute<?>[] fileAttributes(Set<PosixFilePermission> permissions)
            throws ShipEventStoreException {
        try {
            return new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(permissions)};
        } catch (UnsupportedOperationException e) {
            throw new ShipEventStoreException(
                    "Ship event storage requires POSIX permissions", e);
        }
    }

    private void forceDirectory(Path directory) throws IOException {
        if (directory == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Directory fsync is not exposed by every POSIX provider.
        }
    }

    private void forceFileMetadata(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(
                file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }

    private static String hmac(byte[] key, byte[] domain, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(domain);
            mac.update((byte) 0);
            mac.update(ByteBuffer.allocate(Long.BYTES).putLong(message.length).array());
            return "hmac-sha256:" + HexFormat.of().formatHex(mac.doFinal(message));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String first, String second) {
        return first != null && second != null && MessageDigest.isEqual(
                first.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                second.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static boolean sameHead(ShipEventHead first, ShipEventHead second) {
        return first.sequence() == second.sequence()
                && Objects.equals(first.authorityHead(), second.authorityHead())
                && constantTimeEquals(first.eventDigest(), second.eventDigest());
    }

    private static ShipEventHead headOf(List<ShipEvent> history) {
        return history.isEmpty()
                ? ShipEventHead.genesis()
                : ShipEventHead.from(history.get(history.size() - 1));
    }

    private static String eventFileName(long sequence, String digest) {
        return String.format(Locale.ROOT, "%020d-%s.json", sequence, digestHex(digest));
    }

    private static String digestHex(String digest) {
        if (!ShipDigest.isSha256(digest)) {
            throw new IllegalArgumentException("Not a SHA-256 content address");
        }
        return digest.substring("sha256:".length());
    }

    private static long checkedAdd(long left, long right) throws ShipEventStoreException {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException e) {
            throw historyBudgetExceeded();
        }
    }

    static void requireHistoryBudget(int eventCount, long historyBytes)
            throws ShipEventStoreException {
        if (eventCount < 0 || historyBytes < 0
                || eventCount > MAX_EVENT_COUNT || historyBytes > MAX_HISTORY_BYTES) {
            throw historyBudgetExceeded();
        }
    }

    static void requireAppendBudget(int eventCount, long historyBytes, long eventBytes)
            throws ShipEventStoreException {
        requireHistoryBudget(eventCount, historyBytes);
        if (eventBytes < 0 || eventBytes > MAX_EVENT_BYTES
                || eventCount >= MAX_EVENT_COUNT
                || historyBytes > MAX_HISTORY_BYTES - eventBytes) {
            throw historyBudgetExceeded();
        }
    }

    private static ShipEventStoreException historyBudgetExceeded() {
        return new ShipEventStoreException("Ship event history exceeds the controller budget");
    }

    private enum OpenMode {
        CREATE,
        OPEN
    }
}

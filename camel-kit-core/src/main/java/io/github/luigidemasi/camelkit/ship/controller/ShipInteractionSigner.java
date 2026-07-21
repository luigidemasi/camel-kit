package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;

/** Signs canonical interaction records with one controller-local, run-bound key. */
final class ShipInteractionSigner {

    private static final int KEY_BYTES = 32;
    private static final String KEY_NAME = "interaction.key";
    private static final String PENDING_NAME = "interaction.key.pending";
    private static final String MAC_PREFIX = "hmac-sha256:";
    private static final Set<PosixFilePermission> PRIVATE_PERMISSIONS
            = PosixFilePermissions.fromString("rw-------");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path stateRoot;
    private final Path runRoot;
    private final String runId;
    private final Path keyPath;

    private ShipInteractionSigner(RunBinding binding) {
        this.stateRoot = binding.stateRoot();
        this.runRoot = binding.runRoot();
        this.runId = binding.runId();
        this.keyPath = runRoot.resolve(KEY_NAME);
    }

    static ShipInteractionSigner create(Path suppliedRunRoot) throws IOException {
        RunBinding binding = requireRunBinding(suppliedRunRoot);
        try (ShipOperationLock.Lease ignored
                = ShipOperationLock.acquireRun(binding.stateRoot(), binding.runId())) {
            recoverPending(binding);
            Path keyPath = binding.runRoot().resolve(KEY_NAME);
            if (Files.exists(keyPath, LinkOption.NOFOLLOW_LINKS)) {
                validateKey(binding.runRoot(), keyPath);
                throw new IOException("Ship interaction key already exists");
            }

            Path pending = binding.runRoot().resolve(PENDING_NAME);
            byte[] key = new byte[KEY_BYTES];
            RANDOM.nextBytes(key);
            boolean created = false;
            boolean linked = false;
            boolean installed = false;
            try {
                try {
                    Files.createFile(
                            pending, PosixFilePermissions.asFileAttribute(PRIVATE_PERMISSIONS));
                    created = true;
                } catch (FileAlreadyExistsException e) {
                    throw new IOException("Ship interaction-key pending file already exists", e);
                }
                try (FileChannel channel = FileChannel.open(
                        pending,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        LinkOption.NOFOLLOW_LINKS)) {
                    writeFully(channel, key);
                    channel.force(true);
                }
                validateManagedFile(binding.runRoot(), pending, KEY_BYTES, 1, "Ship pending interaction key");
                try {
                    Files.createLink(keyPath, pending);
                    linked = true;
                } catch (FileAlreadyExistsException e) {
                    throw new IOException("Ship interaction key already exists", e);
                } catch (UnsupportedOperationException e) {
                    throw new IOException("Filesystem does not support no-replace Ship key creation", e);
                }
                forceDirectory(binding.runRoot());
                Files.delete(pending);
                forceDirectory(binding.runRoot());
                validateKey(binding.runRoot(), keyPath);
                installed = true;
            } finally {
                Arrays.fill(key, (byte) 0);
                if (!installed) {
                    if (linked) {
                        Files.deleteIfExists(keyPath);
                    }
                    if (created) {
                        Files.deleteIfExists(pending);
                    }
                    forceDirectory(binding.runRoot());
                }
            }
            return new ShipInteractionSigner(binding);
        }
    }

    static ShipInteractionSigner open(Path suppliedRunRoot) throws IOException {
        RunBinding binding = requireRunBinding(suppliedRunRoot);
        try (ShipOperationLock.Lease ignored
                = ShipOperationLock.acquireRun(binding.stateRoot(), binding.runId())) {
            recoverPending(binding);
            validateKey(binding.runRoot(), binding.runRoot().resolve(KEY_NAME));
            return new ShipInteractionSigner(binding);
        }
    }

    boolean belongsTo(String candidateRunId) {
        return runId.equals(candidateRunId);
    }

    boolean belongsTo(ShipBlobStore blobs, String candidateRunId) {
        return blobs != null
                && belongsTo(candidateRunId)
                && blobs.belongsTo(stateRoot, candidateRunId);
    }

    String nonce() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    String sign(String[] macFields) throws IOException {
        return MAC_PREFIX + HexFormat.of().formatHex(hmac(Interaction.canonicalMacBytes(macFields)));
    }

    boolean verify(String suppliedMac, String[] macFields) throws IOException {
        if (!ShipDigest.isHmacSha256(suppliedMac)) {
            return false;
        }
        byte[] expected = hmac(Interaction.canonicalMacBytes(macFields));
        byte[] supplied = HexFormat.of().parseHex(suppliedMac.substring(MAC_PREFIX.length()));
        return MessageDigest.isEqual(expected, supplied);
    }

    private byte[] hmac(byte[] canonicalMessage) throws IOException {
        byte[] key = readExactKey();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(canonicalMessage);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private byte[] readExactKey() throws IOException {
        try (ShipOperationLock.Lease ignored = ShipOperationLock.acquireRun(stateRoot, runId)) {
            validateKey(runRoot, keyPath);
            byte[] key = new byte[KEY_BYTES];
            try (FileChannel channel = FileChannel.open(
                    keyPath, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(key);
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) {
                        throw new IOException("Ship interaction key is truncated");
                    }
                }
                if (channel.read(ByteBuffer.allocate(1)) != -1) {
                    throw new IOException("Ship interaction key has trailing bytes");
                }
            } catch (IOException e) {
                Arrays.fill(key, (byte) 0);
                throw e;
            }
            validateKey(runRoot, keyPath);
            return key;
        }
    }

    private static RunBinding requireRunBinding(Path supplied) throws IOException {
        if (supplied == null) {
            throw new IOException("Ship interaction key requires a canonical run directory");
        }
        Path absolute = supplied.toAbsolutePath().normalize();
        if (!absolute.equals(supplied) || absolute.getParent() == null
                || Files.isSymbolicLink(absolute)
                || !Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ship interaction key requires a canonical run directory");
        }
        Path real = absolute.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!real.equals(absolute)) {
            throw new IOException("Ship interaction key requires a link-free canonical run directory");
        }
        String runId = absolute.getFileName().toString();
        try {
            ShipControllerPaths.requireRunRoot(absolute.getParent(), runId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IOException("Ship interaction key requires a canonical Ship run ID", e);
        }
        return new RunBinding(absolute.getParent(), absolute, runId);
    }

    private static void recoverPending(RunBinding binding) throws IOException {
        Path key = binding.runRoot().resolve(KEY_NAME);
        Path pending = binding.runRoot().resolve(PENDING_NAME);
        boolean keyExists = Files.exists(key, LinkOption.NOFOLLOW_LINKS);
        boolean pendingExists = Files.exists(pending, LinkOption.NOFOLLOW_LINKS);
        if (!pendingExists) {
            if (keyExists) {
                validateKey(binding.runRoot(), key);
            }
            return;
        }
        if (!keyExists) {
            BasicFileAttributes staged = validateManagedFile(
                    binding.runRoot(), pending, -1, 1, "Ship pending interaction key");
            if (staged.size() > KEY_BYTES) {
                throw new IOException("Ship pending interaction key exceeds its managed bound");
            }
            Files.delete(pending);
            forceDirectory(binding.runRoot());
            return;
        }

        BasicFileAttributes installed = validateManagedFile(
                binding.runRoot(), key, KEY_BYTES, 2, "Ship interaction key");
        BasicFileAttributes staged = validateManagedFile(
                binding.runRoot(), pending, KEY_BYTES, 2, "Ship pending interaction key");
        if (!installed.fileKey().equals(staged.fileKey())) {
            throw new IOException("Ship interaction key and pending recovery entry are not the same inode");
        }
        Files.delete(pending);
        forceDirectory(binding.runRoot());
        validateKey(binding.runRoot(), key);
    }

    private static void validateKey(Path runRoot, Path keyPath) throws IOException {
        validateManagedFile(runRoot, keyPath, KEY_BYTES, 1, "Ship interaction key");
    }

    private static BasicFileAttributes validateManagedFile(
            Path runRoot, Path path, long expectedSize, long expectedLinks, String description)
            throws IOException {
        BasicFileAttributes basic = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!basic.isRegularFile() || basic.isSymbolicLink() || basic.fileKey() == null
                || expectedSize >= 0 && basic.size() != expectedSize) {
            throw new IOException(description + " is missing or invalid");
        }
        if (Files.getFileAttributeView(
                path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
            == null
                || !Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
                        .equals(PRIVATE_PERMISSIONS)) {
            throw new IOException(description + " permissions must be 0600");
        }
        PosixFileAttributes file = Files.readAttributes(
                path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        PosixFileAttributes root = Files.readAttributes(
                runRoot, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!file.owner().equals(root.owner())) {
            throw new IOException(description + " owner differs from the protected run");
        }
        Object links;
        try {
            links = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
        } catch (UnsupportedOperationException e) {
            throw new IOException(description + " filesystem lacks hard-link identity", e);
        }
        if (!(links instanceof Number count) || count.longValue() != expectedLinks) {
            throw new IOException(description + " has an invalid hard-link count");
        }
        return basic;
    }

    private static void writeFully(FileChannel channel, byte[] value) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(value);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static void forceDirectory(Path path) throws IOException {
        try (FileChannel directory = FileChannel.open(path, StandardOpenOption.READ)) {
            directory.force(true);
        }
    }

    private record RunBinding(Path stateRoot, Path runRoot, String runId) {
    }
}

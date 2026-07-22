package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.protocol.ProducedArtifact;
import io.github.luigidemasi.camelkit.ship.security.StagedArtifactSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ShipBlobStoreTest {

    private static final String RUN_ID = "ship-" + "1".repeat(32);

    @TempDir
    Path temporaryDirectory;

    Path stateRoot;
    Path runRoot;
    ShipBlobStore store;

    @BeforeEach
    void createStore() throws Exception {
        stateRoot = Files.createDirectory(
                temporaryDirectory.resolve("state"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        runRoot = Files.createDirectory(
                stateRoot.resolve(RUN_ID),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        store = ShipBlobStore.create(stateRoot, RUN_ID);
    }

    @Test
    void publicReferencesCarryIdentityButNeverAStoragePath() throws Exception {
        byte[] content = "controller-owned".getBytes(StandardCharsets.UTF_8);

        ShipBlobStore.BlobReference reference = store.writeBytes("context", content);

        assertEquals("context", reference.kind());
        assertEquals(ShipDigest.sha256(content), reference.digest());
        assertArrayEquals(content, store.readBytes(reference, content.length));
        assertTrue(java.lang.reflect.Modifier.isPublic(ShipBlobStore.class.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPublic(ShipBlobStore.BlobReference.class.getModifiers()));
        assertFalse(java.util.Arrays.stream(ShipBlobStore.BlobReference.class.getRecordComponents())
                .anyMatch(component -> Path.class.equals(component.getType())));
        assertFalse(java.util.Arrays.stream(ShipBlobStore.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .anyMatch(method -> Path.class.equals(method.getReturnType())));
    }

    @Test
    void badSecondArtifactLeavesNoPromotedOrQuarantinedBytes() throws Exception {
        Path output = Files.createDirectory(temporaryDirectory.resolve("output"));
        byte[] first = "first".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second".getBytes(StandardCharsets.UTF_8);
        Files.write(output.resolve("first.txt"), first);
        Files.write(output.resolve("second.txt"), second);
        List<ProducedArtifact> artifacts = List.of(
                artifact("route", "first.txt", first),
                new ProducedArtifact("route", "second.txt", "sha256:" + "0".repeat(64), second.length));

        assertThrows(
                ShipControllerException.class,
                () -> importArtifacts(output, artifacts));

        assertEquals(0, entryCount(runRoot.resolve("blobs")));
        assertEquals(0, entryCount(runRoot.resolve("quarantine")));
    }

    @Test
    void intermediateSymlinkAndHardLinkedLeafAreRejected() throws Exception {
        Path output = Files.createDirectory(temporaryDirectory.resolve("output"));
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        byte[] secret = "outside-secret".getBytes(StandardCharsets.UTF_8);
        Files.write(outside.resolve("secret.txt"), secret);
        Files.createSymbolicLink(output.resolve("nested"), outside);

        assertThrows(
                java.io.IOException.class,
                () -> importArtifacts(
                        output, List.of(artifact("route", "nested/secret.txt", secret))));
        assertArrayEquals(secret, Files.readAllBytes(outside.resolve("secret.txt")));

        Files.delete(output.resolve("nested"));
        Path external = Files.write(temporaryDirectory.resolve("external.txt"), secret);
        Files.createLink(output.resolve("linked.txt"), external);
        assertThrows(
                java.io.IOException.class,
                () -> importArtifacts(
                        output, List.of(artifact("route", "linked.txt", secret))));
        assertEquals(0, entryCount(runRoot.resolve("blobs")));
        assertEquals(0, entryCount(runRoot.resolve("quarantine")));
    }

    @Test
    void changedBlobPermissionsAndHardLinksFailClosed() throws Exception {
        byte[] content = "immutable".getBytes(StandardCharsets.UTF_8);
        ShipBlobStore.BlobReference reference = store.writeBytes("context", content);
        Path blob = blobPath(reference);

        Files.setPosixFilePermissions(blob, PosixFilePermissions.fromString("rw-------"));
        assertThrows(java.io.IOException.class, () -> store.verify(reference));

        Files.setPosixFilePermissions(blob, PosixFilePermissions.fromString("r--------"));
        Files.createLink(temporaryDirectory.resolve("blob-alias"), blob);
        assertThrows(java.io.IOException.class, () -> store.verify(reference));
    }

    @Test
    void existingCorruptAddressBlocksTheWholeNewBatch() throws Exception {
        byte[] existing = "existing".getBytes(StandardCharsets.UTF_8);
        ShipBlobStore.BlobReference reference = store.writeBytes("route", existing);
        Path blob = blobPath(reference);
        Files.setPosixFilePermissions(blob, PosixFilePermissions.fromString("rw-------"));
        Files.write(blob, "tampered".getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(blob, PosixFilePermissions.fromString("r--------"));

        Path output = Files.createDirectory(temporaryDirectory.resolve("output"));
        byte[] fresh = "fresh".getBytes(StandardCharsets.UTF_8);
        Files.write(output.resolve("fresh.txt"), fresh);
        Files.write(output.resolve("existing.txt"), existing);

        assertThrows(
                java.io.IOException.class,
                () -> importArtifacts(output, List.of(
                        artifact("route", "fresh.txt", fresh),
                        artifact("route", "existing.txt", existing))));

        assertFalse(Files.exists(
                runRoot.resolve("blobs").resolve(ShipDigest.sha256(fresh).substring(7) + ".blob")));
        assertEquals(0, entryCount(runRoot.resolve("quarantine")));
    }

    @Test
    void uncommittedTransactionRemovesOnlyTheBlobsItInstalled() throws Exception {
        byte[] existing = "existing".getBytes(StandardCharsets.UTF_8);
        byte[] written = "transaction-write".getBytes(StandardCharsets.UTF_8);
        byte[] imported = "transaction-import".getBytes(StandardCharsets.UTF_8);
        ShipBlobStore.BlobReference existingReference = store.writeBytes("context", existing);
        Path output = Files.createDirectory(temporaryDirectory.resolve("output"));
        Files.write(output.resolve("imported.txt"), imported);

        ShipBlobStore.BlobReference writtenReference;
        ShipBlobStore.BlobReference importedReference;
        try (ShipBlobStore.Transaction transaction = store.beginTransaction();
             StagedArtifactSource.Session source = StagedArtifactSource.open(output)) {
            assertEquals(existingReference, transaction.writeBytes("context", existing));
            writtenReference = transaction.writeBytes("context", written);
            importedReference = transaction.importArtifacts(
                    source, List.of(artifact("route", "imported.txt", imported)))
                    .get(0)
                    .reference();

            assertTrue(Files.exists(blobPath(existingReference)));
            assertTrue(Files.exists(blobPath(writtenReference)));
            assertTrue(Files.exists(blobPath(importedReference)));
        }

        store.verify(existingReference);
        assertArrayEquals(existing, store.readBytes(existingReference, existing.length));
        assertTrue(Files.exists(blobPath(existingReference)));
        assertFalse(Files.exists(blobPath(writtenReference)));
        assertFalse(Files.exists(blobPath(importedReference)));
        assertEquals(1, entryCount(runRoot.resolve("blobs")));
        assertEquals(0, entryCount(runRoot.resolve("quarantine")));
    }

    private Path blobPath(ShipBlobStore.BlobReference reference) {
        return runRoot.resolve("blobs").resolve(reference.digest().substring(7) + ".blob");
    }

    private List<ShipBlobStore.ImportedBlob> importArtifacts(
            Path output, List<ProducedArtifact> artifacts)
            throws Exception {
        try (ShipBlobStore.Transaction transaction = store.beginTransaction();
             StagedArtifactSource.Session source = StagedArtifactSource.open(output)) {
            List<ShipBlobStore.ImportedBlob> imported = transaction.importArtifacts(source, artifacts);
            transaction.commit();
            return imported;
        }
    }

    private static ProducedArtifact artifact(String kind, String path, byte[] content) {
        return new ProducedArtifact(kind, path, ShipDigest.sha256(content), content.length);
    }

    private static long entryCount(Path directory) throws Exception {
        try (var entries = Files.list(directory)) {
            return entries.count();
        }
    }
}

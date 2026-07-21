package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ShipInteractionSignerTest {

    @TempDir
    Path temporaryDirectory;

    Path stateRoot;

    @BeforeEach
    void createStateRoot() throws Exception {
        stateRoot = Files.createDirectory(
                temporaryDirectory.resolve("state"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }

    @Test
    void canonicalMacSeparatesNullEmptyOrderDomainAndDelimiterPlacement() throws Exception {
        Path runRoot = run('1');
        ShipInteractionSigner signer = ShipInteractionSigner.create(runRoot);

        String nullField = signer.sign(new String[]{"test-v1", null});
        String emptyField = signer.sign(new String[]{"test-v1", ""});
        assertNotEquals(nullField, emptyField);
        assertNotEquals(
                signer.sign(new String[]{"test-v1", "a", "b"}),
                signer.sign(new String[]{"test-v1", "b", "a"}));
        assertNotEquals(
                signer.sign(new String[]{"discovery-challenge-v1", "same"}),
                signer.sign(new String[]{"design-challenge-v1", "same"}));
        assertNotEquals(
                signer.sign(new String[]{"test-v1", "a", "b\nc"}),
                signer.sign(new String[]{"test-v1", "a\nb", "c"}));

        String stable = signer.sign(new String[]{"test-v1", "stable"});
        assertTrue(ShipInteractionSigner.open(runRoot)
                .verify(stable, new String[]{"test-v1", "stable"}));
        assertFalse(signer.verify(stable, new String[]{"test-v1", "changed"}));
    }

    @Test
    void signerIsBoundToTheCanonicalRunRootIdentity() throws Exception {
        Path runRoot = run('2');
        String runId = runRoot.getFileName().toString();
        ShipInteractionSigner signer = ShipInteractionSigner.create(runRoot);

        assertTrue(signer.belongsTo(runId));
        assertFalse(signer.belongsTo("ship-" + "3".repeat(32)));

        Path noncanonical = Files.createDirectory(
                stateRoot.resolve("run"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        assertThrows(java.io.IOException.class, () -> ShipInteractionSigner.create(noncanonical));
    }

    @Test
    void openingRejectsWrongKeyLengthAndPermissions() throws Exception {
        Path shortRoot = run('3');
        Path shortKey = Files.write(shortRoot.resolve("interaction.key"), new byte[31]);
        Files.setPosixFilePermissions(shortKey, PosixFilePermissions.fromString("rw-------"));
        assertThrows(java.io.IOException.class, () -> ShipInteractionSigner.open(shortRoot));

        Path modeRoot = run('4');
        ShipInteractionSigner.create(modeRoot);
        Files.setPosixFilePermissions(
                modeRoot.resolve("interaction.key"),
                PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(java.io.IOException.class, () -> ShipInteractionSigner.open(modeRoot));
    }

    @Test
    void openingRejectsAHardLinkedInteractionKey() throws Exception {
        Path runRoot = run('5');
        ShipInteractionSigner.create(runRoot);
        Files.createLink(
                temporaryDirectory.resolve("interaction-key-alias"),
                runRoot.resolve("interaction.key"));

        java.io.IOException failure = assertThrows(
                java.io.IOException.class, () -> ShipInteractionSigner.open(runRoot));

        assertTrue(failure.getMessage().contains("hard-link count"));
    }

    @Test
    void duplicateCreateCannotReplaceTheExistingAuthorityKey() throws Exception {
        Path runRoot = run('6');
        ShipInteractionSigner original = ShipInteractionSigner.create(runRoot);
        String[] fields = {"test-v1", "stable"};
        String signature = original.sign(fields);

        assertThrows(java.io.IOException.class, () -> ShipInteractionSigner.create(runRoot));

        assertTrue(ShipInteractionSigner.open(runRoot).verify(signature, fields));
    }

    @Test
    void openRecoversOnlyTheManagedSameInodePendingLink() throws Exception {
        Path runRoot = run('7');
        Path pending = Files.write(runRoot.resolve("interaction.key.pending"), new byte[32]);
        Files.setPosixFilePermissions(pending, PosixFilePermissions.fromString("rw-------"));
        Path key = runRoot.resolve("interaction.key");
        Files.createLink(key, pending);

        ShipInteractionSigner signer = ShipInteractionSigner.open(runRoot);

        assertTrue(signer.belongsTo(runRoot.getFileName().toString()));
        assertTrue(Files.exists(key));
        assertFalse(Files.exists(pending));
        assertTrue(signer.verify(
                signer.sign(new String[]{"test-v1", "recovered"}),
                new String[]{"test-v1", "recovered"}));
    }

    @Test
    void createRemovesOneBoundedPendingOnlyCrashRemnant() throws Exception {
        Path runRoot = run('8');
        Path pending = Files.write(runRoot.resolve("interaction.key.pending"), new byte[17]);
        Files.setPosixFilePermissions(pending, PosixFilePermissions.fromString("rw-------"));

        ShipInteractionSigner signer = ShipInteractionSigner.create(runRoot);

        assertFalse(Files.exists(pending));
        assertTrue(signer.belongsTo(runRoot.getFileName().toString()));
        assertTrue(Files.exists(runRoot.resolve("interaction.key")));
    }

    @Test
    void mismatchedKeyAndPendingInodesFailClosedWithoutDeletion() throws Exception {
        Path runRoot = run('9');
        Path key = Files.write(runRoot.resolve("interaction.key"), new byte[32]);
        Path pending = Files.write(runRoot.resolve("interaction.key.pending"), new byte[32]);
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------"));
        Files.setPosixFilePermissions(pending, PosixFilePermissions.fromString("rw-------"));

        assertThrows(java.io.IOException.class, () -> ShipInteractionSigner.open(runRoot));

        assertTrue(Files.exists(key));
        assertTrue(Files.exists(pending));
    }

    private Path run(char id) throws Exception {
        return Files.createDirectory(
                stateRoot.resolve("ship-" + Character.toString(id).repeat(32)),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }
}

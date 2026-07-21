package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledOnOs(OS.LINUX)
class NativeSessionEvidenceTest {

    private static final String RUN_A = "ship-" + "b".repeat(32);
    private static final String RUN_B = "ship-" + "c".repeat(32);

    @TempDir
    Path temporaryDirectory;

    @Test
    void nativeEvidenceRejectsCrossRunSignerBlobStoreTraceAndDeclarationMiswiring() throws Exception {
        Fixture first = fixture("first", RUN_A);
        Fixture second = fixture("second", RUN_B);
        Fixture sameIdDifferentState = fixture("shadow", RUN_A);
        ShipBlobStore.BlobReference firstTrace
                = first.blobs().writeBytes(NativeSessionEvidence.TRACE_KIND, new byte[]{1});
        ShipBlobStore.BlobReference secondTrace
                = second.blobs().writeBytes(NativeSessionEvidence.TRACE_KIND, new byte[]{2});

        NativeSessionEvidence.Binding binding = binding(first.signer(), RUN_A, firstTrace);
        ShipBlobStore.BlobReference retained = NativeSessionEvidence.store(first.blobs(), first.signer(), binding);
        assertEquals(
                binding,
                NativeSessionEvidence.verify(
                        first.blobs(), first.signer(), RUN_A, "native", retained));

        assertThrows(
                java.io.IOException.class,
                () -> binding(second.signer(), RUN_A, secondTrace));
        assertThrows(
                java.io.IOException.class,
                () -> NativeSessionEvidence.store(second.blobs(), second.signer(), binding));
        assertThrows(
                java.io.IOException.class,
                () -> NativeSessionEvidence.store(
                        sameIdDifferentState.blobs(), first.signer(), binding));
        NativeSessionEvidence.Binding foreignTrace = binding(first.signer(), RUN_A, secondTrace);
        assertThrows(
                java.io.IOException.class,
                () -> NativeSessionEvidence.store(first.blobs(), first.signer(), foreignTrace));
        assertThrows(
                java.io.IOException.class,
                () -> NativeSessionEvidence.verify(
                        second.blobs(), first.signer(), RUN_A, "native", retained));
        assertThrows(
                java.io.IOException.class,
                () -> NativeSessionEvidence.verify(
                        first.blobs(), second.signer(), RUN_A, "native", retained));
        assertThrows(
                java.io.IOException.class,
                () -> NativeSessionEvidence.verify(
                        first.blobs(), first.signer(), RUN_B, "native", retained));
        assertThrows(
                java.io.IOException.class,
                () -> NativeSessionEvidence.verify(
                        first.blobs(), first.signer(), RUN_A, null, retained));
    }

    private static NativeSessionEvidence.Binding binding(
            ShipInteractionSigner signer,
            String runId,
            ShipBlobStore.BlobReference trace)
            throws Exception {
        return NativeSessionEvidence.create(
                signer,
                runId,
                "native",
                "session-1",
                "terminal",
                "interaction-v1",
                "launcher-v1",
                "sandbox-v1",
                "provider-v1",
                "protocol-v1",
                "evidence-v1",
                "uid:1000",
                "terminal-v1",
                trace);
    }

    private Fixture fixture(String name, String runId) throws Exception {
        Path state = Files.createDirectory(
                temporaryDirectory.resolve(name),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path run = Files.createDirectory(
                state.resolve(runId),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        return new Fixture(
                ShipBlobStore.create(state, runId),
                ShipInteractionSigner.create(run));
    }

    private record Fixture(ShipBlobStore blobs, ShipInteractionSigner signer) {
    }
}

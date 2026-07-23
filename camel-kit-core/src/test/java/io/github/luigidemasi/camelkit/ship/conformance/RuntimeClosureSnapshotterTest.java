package io.github.luigidemasi.camelkit.ship.conformance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipJson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class RuntimeClosureSnapshotterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void snapshotsAPathFreeClosureAndResolvesInternalAndEntrypointLinks() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("package")).toRealPath();
        Path dist = Files.createDirectory(root.resolve("dist"));
        Path bin = Files.createDirectory(root.resolve("bin"));
        Path cli = Files.writeString(dist.resolve("cli.js"), "#!/usr/bin/env node\n");
        Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.writeString(root.resolve("package.json"), "{\"version\":\"0.81.1\"}\n");
        Files.write(root.resolve("empty"), new byte[0]);
        Files.createSymbolicLink(bin.resolve("pi"), Path.of("../dist/cli.js"));
        Path external = temporaryDirectory.resolve("pi");
        Files.createSymbolicLink(external, external.getParent().relativize(cli));
        Map<String, byte[]> blobs = new LinkedHashMap<>();

        RuntimeClosureSnapshotter.Snapshot snapshot = RuntimeClosureSnapshotter.capture(
                external, Map.of("pi", root), (digest, content) -> blobs.put(digest, content));

        RuntimeClosureManifest manifest = ShipJson.mapper().readValue(
                snapshot.manifest(), RuntimeClosureManifest.class);
        assertEquals("pi", manifest.entryPoint().rootId());
        assertEquals("dist/cli.js", manifest.entryPoint().relativePath());
        assertEquals(4, manifest.files().size());
        assertEquals(snapshot.digest(), ShipDigest.sha256(snapshot.manifest()));
        assertEquals(snapshot.manifest().length, blobs.get(snapshot.digest()).length);
        assertFalse(new String(snapshot.manifest(), StandardCharsets.UTF_8)
                .contains(temporaryDirectory.toString()));
        assertTrue(manifest.files().stream()
                .filter(file -> file.relativePath().equals("bin/pi"))
                .findFirst()
                .orElseThrow()
                .executable());
    }

    @Test
    void rejectsCyclesEscapesAndMutationDuringCapture() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("package")).toRealPath();
        Path cli = Files.writeString(root.resolve("cli.js"), "cli");
        Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwx------"));
        Path cycle = root.resolve("cycle");
        Files.createSymbolicLink(cycle, Path.of("cycle"));
        assertThrows(IOException.class, () -> RuntimeClosureSnapshotter.capture(
                cli, Map.of("pi", root), (digest, content) -> {
                }));

        Files.delete(cycle);
        Path outside = Files.writeString(temporaryDirectory.resolve("outside"), "outside");
        Files.createSymbolicLink(root.resolve("escape"), Path.of("../outside"));
        assertThrows(IOException.class, () -> RuntimeClosureSnapshotter.capture(
                cli, Map.of("pi", root), (digest, content) -> {
                }));

        Files.delete(root.resolve("escape"));
        Path mutable = Files.writeString(root.resolve("package.json"), "{}");
        assertThrows(IOException.class, () -> RuntimeClosureSnapshotter.capture(
                cli, Map.of("pi", root), (digest, content) -> {
                    if (new String(content, StandardCharsets.UTF_8).equals("{}")) {
                        Files.writeString(mutable, "{\"changed\":true}");
                    }
                }));
    }
}

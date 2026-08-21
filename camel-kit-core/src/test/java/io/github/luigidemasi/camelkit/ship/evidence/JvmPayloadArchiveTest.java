package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import io.github.luigidemasi.camelkit.ship.ShipArtifactLimits;
import io.github.luigidemasi.camelkit.ship.artifact.CitrusDependencyPolicy;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogExpressionInventory;
import io.github.luigidemasi.camelkit.ship.expression.ShipExpressionPolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class JvmPayloadArchiveTest {

    private static final String JDK_DIGEST = "sha256:" + "1".repeat(64);

    @TempDir
    Path directory;

    @Test
    void writesAReproducibleSelfVerifyingContentLockedArchive() throws Exception {
        JvmPayloadRequest request = JvmPayloadRequest.camelMain("4.21.0");
        JvmPayloadArchive.Identity first = JvmPayloadTestFixture.create(
                directory.resolve("first"), request, JDK_DIGEST);
        JvmPayloadArchive.Identity second = JvmPayloadTestFixture.create(
                directory.resolve("second"), request, JDK_DIGEST);

        assertEquals(first.archiveDigest(), second.archiveDigest());
        assertEquals(first.aggregateDigest(), second.aggregateDigest());
        assertArrayEquals(Files.readAllBytes(first.archive()), Files.readAllBytes(second.archive()));
        assertEquals(request.roots(), first.lock().roots());
        assertEquals(request.launcherClass(), first.lock().launcherClass());
        first.lock().requireRequest(request);

        JvmPayloadArchive.Identity verified = JvmPayloadArchive.verify(first.archive(), request, JDK_DIGEST);
        assertEquals(first.aggregateDigest(), verified.aggregateDigest());
    }

    @Test
    void rejectsRequestJdkAndArchiveTampering() throws Exception {
        JvmPayloadRequest request = JvmPayloadRequest.yamlValidator("4.21.0");
        JvmPayloadArchive.Identity identity = JvmPayloadTestFixture.create(
                directory.resolve("payload"), request, JDK_DIGEST);

        assertThrows(IOException.class, () -> JvmPayloadArchive.verify(
                identity.archive(), JvmPayloadRequest.yamlValidator("4.18.3"), JDK_DIGEST));
        assertThrows(IOException.class, () -> JvmPayloadArchive.verify(
                identity.archive(), request, "sha256:" + "2".repeat(64)));

        makeWritable(identity.archive());
        Files.write(identity.archive(), new byte[]{0}, StandardOpenOption.WRITE);
        assertThrows(IOException.class,
                () -> JvmPayloadArchive.verify(identity.archive(), request, JDK_DIGEST));
    }

    @Test
    void lockRejectsUnknownFieldsAndContentDigestChanges() throws Exception {
        JvmPayloadArchive.Identity identity = JvmPayloadTestFixture.create(
                directory.resolve("payload"), JvmPayloadRequest.yamlValidator("4.21.0"), JDK_DIGEST);
        byte[] canonical = identity.lock().encode();

        assertThrows(IOException.class, () -> JvmPayloadLock.parse(
                (new String(canonical, java.nio.charset.StandardCharsets.UTF_8) + "unknown=value\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> JvmPayloadLock.parse(
                new String(canonical, java.nio.charset.StandardCharsets.UTF_8)
                        .replace(identity.lock().contentDigest(), "sha256:" + "0".repeat(64))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void lockAuthenticatesDependencyExclusions() throws Exception {
        JvmPayloadArchive.Identity identity = JvmPayloadTestFixture.create(
                directory.resolve("payload"),
                JvmPayloadRequest.citrus(
                        "4.21.0", "5.0.0-M2", CitrusDependencyPolicy.required("5.0.0-M2")),
                JDK_DIGEST);
        String canonical = new String(identity.lock().encode(), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(canonical.contains("exclusion.000.groupId=org.apache.camel"));
        assertThrows(IOException.class, () -> JvmPayloadLock.parse(
                canonical.replace("exclusion.000.groupId=org.apache.camel",
                        "exclusion.000.groupId=org.example")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void isolatedLaunchersContainExactlyTheirApplicationOwnedDependencies() throws Exception {
        for (JvmPayloadRequest request : java.util.List.of(
                JvmPayloadRequest.yamlValidator("4.21.0"),
                JvmPayloadRequest.camelMain("4.21.0"),
                JvmPayloadRequest.citrus(
                        "4.21.0", "5.0.0-M2", CitrusDependencyPolicy.required("5.0.0-M2")))) {
            JvmPayloadArchive.Identity identity = JvmPayloadTestFixture.create(
                    directory.resolve(request.kind().id()), request, JDK_DIGEST);

            Set<String> launcherEntries = new HashSet<>();
            try (ZipFile payload = new ZipFile(identity.archive().toFile());
                 ZipInputStream launcher = new ZipInputStream(
                         payload.getInputStream(payload.getEntry("lib/000-controller-launcher.jar")))) {
                java.util.zip.ZipEntry entry;
                while ((entry = launcher.getNextEntry()) != null) {
                    launcherEntries.add(entry.getName());
                }
            }

            assertTrue(launcherEntries.contains(classPath(ShipArtifactLimits.class)), request.kind().id());
            boolean usesExpressionPolicy = request.kind() == JvmPayloadRequest.Kind.CAMEL_MAIN_START
                    || request.kind() == JvmPayloadRequest.Kind.CITRUS_YAML;
            assertEquals(
                    usesExpressionPolicy,
                    launcherEntries.contains(classPath(ShipExpressionPolicy.class)), request.kind().id());
            assertEquals(
                    usesExpressionPolicy,
                    launcherEntries.contains(classPath(CatalogExpressionInventory.class)), request.kind().id());
        }
    }

    @Test
    void overrideGuardToleratesTransportPropertiesButRefusesRepositoryOverrides() throws Exception {
        withProperty("https.proxyHost", "proxy.corp.example",
                JvmPayloadArchive::rejectRepositoryOverrides);
        withProperty("javax.net.ssl.trustStore", "/etc/pki/corp-truststore.p12",
                JvmPayloadArchive::rejectRepositoryOverrides);
        withProperty("camel.extra.repos", "https://repo.example/maven", () -> {
            IOException refused = assertThrows(IOException.class,
                    JvmPayloadArchive::rejectRepositoryOverrides);
            assertEquals("Controller JVM payload refuses repository override property camel.extra.repos",
                    refused.getMessage());
        });
    }

    private interface GuardProbe {
        void run() throws IOException;
    }

    private static void withProperty(String key, String value, GuardProbe probe) throws IOException {
        String previous = System.getProperty(key);
        System.setProperty(key, value);
        try {
            probe.run();
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    private static String classPath(Class<?> type) {
        return type.getName().replace('.', '/') + ".class";
    }

    private static void makeWritable(Path file) throws IOException {
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } else if (!file.toFile().setWritable(true, true)) {
            throw new IOException("Could not make fixture writable");
        }
    }
}

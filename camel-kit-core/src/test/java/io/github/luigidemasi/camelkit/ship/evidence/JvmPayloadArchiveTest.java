package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.ship.ShipArtifactLimits;
import io.github.luigidemasi.camelkit.ship.artifact.CitrusDependencyPolicy;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogExpressionInventory;
import io.github.luigidemasi.camelkit.ship.expression.ShipExpressionPolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class JvmPayloadArchiveTest {

    private static final String CAMEL_VERSION = DistributionConfig.loadBundled().camelMainVersion();

    @TempDir
    Path directory;

    @Test
    void writesAReproducibleArchive() throws Exception {
        JvmPayloadRequest request = JvmPayloadRequest.camelMain(CAMEL_VERSION);
        Path first = JvmPayloadTestFixture.create(
                directory.resolve("first"), request);
        Path second = JvmPayloadTestFixture.create(
                directory.resolve("second"), request);

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    @Test
    void isolatedLaunchersContainExactlyTheirApplicationOwnedDependencies() throws Exception {
        for (JvmPayloadRequest request : java.util.List.of(
                JvmPayloadRequest.yamlValidator(CAMEL_VERSION),
                JvmPayloadRequest.camelMain(CAMEL_VERSION),
                JvmPayloadRequest.citrus(
                        CAMEL_VERSION, "5.0.0-M2", CitrusDependencyPolicy.required("5.0.0-M2")))) {
            Path archive = JvmPayloadTestFixture.create(
                    directory.resolve(request.kind().id()), request);

            Set<String> launcherEntries = new HashSet<>();
            try (ZipFile payload = new ZipFile(archive.toFile())) {
                var payloadEntries = payload.entries();
                while (payloadEntries.hasMoreElements()) {
                    String name = payloadEntries.nextElement().getName();
                    assertTrue(
                            name.startsWith("lib/") && name.endsWith(".jar")
                                    || name.startsWith(classPath(ShipJvmPayloadBootstrap.class)
                                            .replace(".class", ""))
                                            && name.endsWith(".class"),
                            "payload must contain only the bootstrap and nested JARs: " + name);
                }
                try (ZipInputStream launcher = new ZipInputStream(
                        payload.getInputStream(payload.getEntry("lib/000-controller-launcher.jar")))) {
                    java.util.zip.ZipEntry entry;
                    while ((entry = launcher.getNextEntry()) != null) {
                        launcherEntries.add(entry.getName());
                    }
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
}

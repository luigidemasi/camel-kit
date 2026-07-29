package io.github.luigidemasi.camelkit.ship.artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifestReader.Document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledOnOs(OS.LINUX)
class ArtifactManifestReaderTest {

    private static final int MAX_MANIFEST_BYTES = 16 * 1024 * 1024;
    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private static final String VALID = String.format(Locale.ROOT, """
            {
              "schemaVersion": 1,
              "runtime": "main",
              "camelVersion": "4.21.0",
              "platformVersion": null,
              "springBootVersion": null,
              "routeDsl": "yaml",
              "expressionLanguage": "simple",
              "citrusVersion": "4.9.0",
              "citrusDependencies": ["org.citrusframework:citrus-core:4.9.0"],
              "javaPolicy": "FORBIDDEN",
              "approvedJavaExceptions": [],
              "routes": [
                {
                  "routeId": "orders",
                  "path": "routes/orders.camel.yaml",
                  "digest": "%s"
                }
              ],
              "citrusTests": [
                {
                  "routeId": "orders",
                  "path": "test/orders.camel.it.yaml",
                  "digest": "%s"
                }
              ],
              "artifacts": [
                {
                  "kind": "pom",
                  "path": "pom.xml",
                  "digest": "%s",
                  "required": true
                },
                {
                  "kind": "config",
                  "path": ".camel-kit/config.properties",
                  "digest": "%s",
                  "required": true
                }
              ],
              "testsRequired": true,
              "oneTestPerRoute": true
            }
            """, DIGEST, DIGEST, DIGEST, DIGEST);

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsCanonicalManifestFromCandidate() throws Exception {
        Path candidate = Files.createDirectory(temporaryDirectory.resolve("candidate"));
        Files.writeString(candidate.resolve("artifact-manifest.json"), VALID);

        Document document = ArtifactManifestReader.read(
                candidate, Path.of("artifact-manifest.json"));
        ArtifactManifest manifest = document.manifest();

        assertEquals(ArtifactManifest.SCHEMA_VERSION, manifest.schemaVersion());
        assertEquals("main", manifest.runtime());
        assertEquals(List.of("orders"),
                manifest.routes().stream().map(ArtifactManifest.RouteArtifact::routeId).toList());
        assertEquals(2, manifest.artifacts().size());
        assertEquals(
                ShipDigest.sha256(VALID.getBytes(StandardCharsets.UTF_8)),
                document.digest());
    }

    @Test
    void rejectsNonStrictJsonWithoutLeakingIt() throws Exception {
        Path candidate = Files.createDirectory(temporaryDirectory.resolve("candidate"));
        List<String> invalid = List.of(
                VALID.replace(
                        "\"runtime\": \"main\",",
                        "\"runtime\": \"top-secret-value\", \"runtime\": \"main\","),
                VALID + "{}",
                VALID.substring(0, VALID.lastIndexOf('}'))
                              + ", \"unknown\": \"top-secret-value\"}",
                VALID.replace("  \"runtime\": \"main\",\n", ""),
                VALID.replace("\"runtime\": \"main\"", "\"runtime\": null"),
                VALID.replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\""),
                VALID.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1.0"));

        for (int index = 0; index < invalid.size(); index++) {
            Path file = Files.writeString(candidate.resolve("invalid-" + index + ".json"),
                    invalid.get(index));
            IOException failure = assertThrows(
                    IOException.class,
                    () -> ArtifactManifestReader.read(candidate, file));
            assertFalse(failure.toString().contains("top-secret-value"));
        }
    }

    @Test
    void rejectsSchemaViolations() throws Exception {
        Path candidate = Files.createDirectory(temporaryDirectory.resolve("candidate"));
        for (String invalid : List.of(
                VALID.replace("\"runtime\": \"main\"", "\"runtime\": \"unsupported\""),
                VALID.replace(
                        "\"path\": \"routes/orders.camel.yaml\"",
                        "\"path\": \"../orders.camel.yaml\""),
                VALID.replace("\"required\": true", "\"required\": false"))) {
            Path file = Files.writeString(candidate.resolve("invalid.json"), invalid);
            assertThrows(IOException.class, () -> ArtifactManifestReader.read(candidate, file));
        }
    }

    @Test
    void rejectsInvalidUtf8() throws Exception {
        Path candidate = Files.createDirectory(temporaryDirectory.resolve("candidate"));
        Path file = Files.write(candidate.resolve("artifact-manifest.json"),
                new byte[]{'{', '"', (byte) 0xc3, '"', '}'});

        assertThrows(IOException.class, () -> ArtifactManifestReader.read(candidate, file));
    }

    @Test
    void rejectsCandidateRootThatTraversesASymbolicParent() throws Exception {
        Path parent = Files.createDirectory(
                temporaryDirectory.resolve("real-parent"));
        Path candidate = Files.createDirectory(parent.resolve("candidate"));
        Files.writeString(candidate.resolve("artifact-manifest.json"), VALID);
        Path alias = Files.createSymbolicLink(
                temporaryDirectory.resolve("parent-alias"), parent);

        assertThrows(
                IOException.class,
                () -> ArtifactManifestReader.read(
                        alias.resolve("candidate"),
                        Path.of("artifact-manifest.json")));
    }

    @Test
    void rejectsOutsideLinkedAndOversizedFiles() throws Exception {
        Path candidate = Files.createDirectory(temporaryDirectory.resolve("candidate"));
        Path outside = Files.writeString(temporaryDirectory.resolve("outside.json"), VALID);
        Path linked = Files.createSymbolicLink(
                candidate.resolve("linked.json"), outside.toAbsolutePath());
        Path hardLinked = Files.createLink(
                candidate.resolve("hard-linked.json"), outside);
        Path oversized = candidate.resolve("oversized.json");
        Files.write(oversized, new byte[MAX_MANIFEST_BYTES + 1]);

        assertThrows(IOException.class, () -> ArtifactManifestReader.read(candidate, outside));
        assertThrows(IOException.class, () -> ArtifactManifestReader.read(candidate, linked));
        assertThrows(IOException.class, () -> ArtifactManifestReader.read(candidate, hardLinked));
        assertThrows(IOException.class, () -> ArtifactManifestReader.read(candidate, oversized));
    }
}

package io.github.luigidemasi.camelkit.ship.catalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;
import io.github.luigidemasi.camelkit.ship.resolver.ResolvedExactMavenArtifact;
import io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver.ResolutionMode;

/** Small exact catalog snapshot used by controller tests without a network resolver. */
public final class CatalogTestVerifier {

    public static final String CAMEL_VERSION = "4.21.0";
    public static final CatalogTarget TARGET = new CatalogTarget("main", CAMEL_VERSION, null, null);

    private CatalogTestVerifier() {
    }

    public static ShipCatalogService.Snapshot mainSnapshot(Path directory) throws IOException {
        MavenCoordinate coordinate = MavenCoordinate.jar(
                "org.apache.camel", "camel-catalog", CAMEL_VERSION);
        Path repository = Files.createDirectory(directory.resolve("repository"));
        Path artifact = repository.resolve("org/apache/camel/camel-catalog")
                .resolve(CAMEL_VERSION)
                .resolve(coordinate.fileName());
        Files.createDirectories(artifact.getParent());
        byte[] bytes = catalogArchive();
        Files.write(artifact, bytes);
        ShipCatalogService service = new ShipCatalogService(
                repository,
                ResolutionMode.OFFLINE,
                (ignoredRepository, requested, ignoredMode) -> {
                    if (!List.of(coordinate).equals(requested)) {
                        throw new IOException("Unexpected test catalog coordinate");
                    }
                    return List.of(new ResolvedExactMavenArtifact(
                            coordinate, artifact, ShipDigest.sha256(bytes), bytes.length));
                });
        return service.snapshot(TARGET);
    }

    private static byte[] catalogArchive() throws IOException {
        String root = "org/apache/camel/catalog/";
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("META-INF/version.properties", "version=" + CAMEL_VERSION + "\n");
        entries.put(root + "components.properties", "direct\n");
        entries.put(root + "models.properties", "from\nroute\nto\n");
        entries.put(root + "dataformats.properties", "");
        entries.put(root + "languages.properties", "simple\n");
        entries.put(root + "components/direct.json", """
                {
                  "component": {
                    "kind": "component",
                    "name": "direct",
                    "groupId": "org.apache.camel",
                    "artifactId": "camel-direct",
                    "version": "4.21.0",
                    "deprecated": false,
                    "syntax": "direct:name",
                    "lenientProperties": false
                  },
                  "componentProperties": {},
                  "properties": {
                    "name": {
                      "kind": "path", "index": 0, "type": "string",
                      "javaType": "java.lang.String", "required": true, "multiValue": false
                    },
                    "timeout": {
                      "kind": "parameter", "index": 1, "type": "integer",
                      "javaType": "long", "required": false, "multiValue": false
                    }
                  }
                }
                """);
        entries.put(root + "models/from.json", """
                {"model":{"kind":"model","name":"from","deprecated":false}}
                """);
        entries.put(root + "models/route.json", """
                {"model":{"kind":"model","name":"route","deprecated":false}}
                """);
        entries.put(root + "models/to.json", """
                {"model":{"kind":"model","name":"to","deprecated":false}}
                """);
        entries.put(root + "languages/simple.json", """
                {"language":{"kind":"language","name":"simple","groupId":"org.apache.camel",
                "artifactId":"camel-core-languages","version":"4.21.0","deprecated":false}}
                """);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}

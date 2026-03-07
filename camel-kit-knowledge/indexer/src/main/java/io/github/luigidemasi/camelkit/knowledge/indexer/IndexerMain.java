package io.github.luigidemasi.camelkit.knowledge.indexer;

import io.github.luigidemasi.camelkit.knowledge.embedding.OnnxEmbeddingProvider;
import io.github.luigidemasi.camelkit.knowledge.indexer.domain.CamelMigrationDomain;
import io.github.luigidemasi.camelkit.knowledge.indexer.domain.DocumentDomain;
import io.github.luigidemasi.camelkit.knowledge.indexer.domain.RhBuildCamelDomain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI entry point for building the Lucene knowledge index.
 * Runs during CI/CD to produce the pre-built index artifact.
 *
 * Usage: java -cp indexer.jar io.github.luigidemasi.camelkit.knowledge.indexer.IndexerMain [output-dir]
 *
 * Requires DOCLING_URL environment variable pointing to a running docling-serve instance.
 */
public class IndexerMain {

    public static void main(String[] args) throws Exception {
        // Resolve base directory from class location to ensure consistency
        // regardless of the JVM working directory.
        // classesDir is typically .../indexer/target/classes
        Path classesDir = Path.of(
                IndexerMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path targetDir = classesDir.getParent();
        // Module root: .../indexer/
        Path moduleDir = targetDir.getParent();

        Path outputDir = args.length > 0
                ? Path.of(args[0]).toAbsolutePath()
                : targetDir.resolve("knowledge-index");

        // doc-cache lives in target/ (ephemeral, gitignored)
        Path cacheDir = targetDir.resolve("doc-cache");

        // Resources dir: .../indexer/src/main/resources/
        // Downloaded HTML guides are persisted here so they survive mvn clean
        Path resourcesDir = moduleDir.resolve("src/main/resources");

        String doclingUrl = System.getenv("DOCLING_URL");
        if (doclingUrl == null || doclingUrl.isBlank()) {
            System.err.println("ERROR: DOCLING_URL environment variable is required.");
            System.err.println("Start docling-serve: docker run -p 5001:5001 quay.io/docling-project/docling-serve");
            System.exit(1);
        }

        System.out.println("Building camel-kit knowledge index...");
        System.out.println("Output: " + outputDir);
        System.out.println("Resources: " + resourcesDir);
        System.out.println("Docling: " + doclingUrl);

        Files.createDirectories(outputDir);

        List<DocumentDomain> domains = buildDomains(cacheDir, resourcesDir, doclingUrl);

        System.out.println("Loading embedding model...");
        OnnxEmbeddingProvider embeddingProvider = new OnnxEmbeddingProvider();
        IndexBuilder builder = new IndexBuilder(embeddingProvider);
        int total = builder.build(outputDir, domains);

        // Write index file manifest for MCP server classpath extraction
        Path manifestPath = outputDir.resolve("INDEX_FILES");
        try (var stream = Files.list(outputDir)) {
            List<String> fileNames = stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> !n.equals("INDEX_FILES"))
                    .sorted()
                    .toList();
            Files.write(manifestPath, fileNames);
        }

        System.out.printf("%nIndex built successfully: %d documents in %d domains%n",
                total, domains.size());
    }

    private static List<DocumentDomain> buildDomains(Path cacheDir, Path resourcesDir, String doclingUrl) throws IOException {
        List<DocumentDomain> domains = new ArrayList<>();

        // Add all registered domains here
        domains.add(new CamelMigrationDomain(cacheDir, doclingUrl));
        domains.add(new RhBuildCamelDomain(cacheDir, resourcesDir, doclingUrl));
        // Future: domains.add(new RhFuseMigrationDomain(cacheDir, resourcesDir, doclingUrl));

        return domains;
    }
}

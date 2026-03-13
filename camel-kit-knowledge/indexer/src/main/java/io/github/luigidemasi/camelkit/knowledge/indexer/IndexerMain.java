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
 * System properties (override automatic path resolution):
 *   -Dindex.output=path        Output directory for the Lucene index
 *   -Dindex.resources=path     Indexer resources directory (HTML guides, errata JSON)
 *   -Dindex.cache=path         Docling markdown cache directory
 *
 * Requires DOCLING_URL environment variable pointing to a running docling-serve instance.
 */
public class IndexerMain {

    public static void main(String[] args) throws Exception {
        // Resolve base directories. System properties override automatic resolution.
        Path classesDir = Path.of(
                IndexerMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path targetDir = classesDir.getParent();
        Path moduleDir = targetDir.getParent();

        Path outputDir = resolvePath("index.output",
                args.length > 0 ? args[0] : null,
                targetDir.resolve("knowledge-index").toString());

        Path cacheDir = resolvePath("index.cache",
                null,
                targetDir.resolve("doc-cache").toString());

        Path resourcesDir = resolvePath("index.resources",
                null,
                moduleDir.resolve("src/main/resources").toString());

        String doclingUrl = System.getProperty("docling.url", System.getenv("DOCLING_URL"));
        if (doclingUrl == null || doclingUrl.isBlank()) {
            System.err.println("ERROR: DOCLING_URL environment variable or -Ddocling.url system property is required.");
            System.err.println("Start docling-serve: docker run -p 5001:5001 quay.io/docling-project/docling-serve");
            System.exit(1);
        }

        System.out.println("Building camel-kit knowledge index...");
        System.out.println("Output: " + outputDir);
        System.out.println("Resources: " + resourcesDir);
        System.out.println("Cache: " + cacheDir);
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

    private static Path resolvePath(String sysProp, String argValue, String defaultValue) {
        String value = System.getProperty(sysProp);
        if (value != null && !value.isBlank()) return Path.of(value).toAbsolutePath();
        if (argValue != null && !argValue.isBlank()) return Path.of(argValue).toAbsolutePath();
        return Path.of(defaultValue).toAbsolutePath();
    }

    private static List<DocumentDomain> buildDomains(Path cacheDir, Path resourcesDir, String doclingUrl) throws IOException {
        List<DocumentDomain> domains = new ArrayList<>();

        // Add all registered domains here
        domains.add(new CamelMigrationDomain(cacheDir, doclingUrl));
        domains.add(new RhBuildCamelDomain(cacheDir, resourcesDir, doclingUrl));

        return domains;
    }
}

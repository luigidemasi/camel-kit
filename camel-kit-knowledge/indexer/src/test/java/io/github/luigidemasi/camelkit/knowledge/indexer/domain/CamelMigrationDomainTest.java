package io.github.luigidemasi.camelkit.knowledge.indexer.domain;

import io.github.luigidemasi.camelkit.knowledge.schema.DomainMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CamelMigrationDomainTest {

    @TempDir
    Path tempDir;

    @Test
    void metadataIsCorrect() throws Exception {
        CamelMigrationDomain domain = new CamelMigrationDomain(tempDir,"http://localhost:5001");
        DomainMetadata meta = domain.metadata();

        assertEquals("camel_migration", meta.domainId());
        assertEquals("camel_migration", meta.toolName());
        assertTrue(meta.hasComponentField());
        assertTrue(meta.hasVersionFields());
    }

    /**
     * Integration test — requires docling-serve running:
     *   docker run -p 5001:5001 quay.io/docling-project/docling-serve
     *
     * Only runs when DOCLING_URL environment variable is set.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DOCLING_URL", matches = ".+")
    void buildChunksFromLiveSource() throws Exception {
        String doclingUrl = System.getenv("DOCLING_URL");
        CamelMigrationDomain domain = new CamelMigrationDomain(tempDir,doclingUrl);
        List<DocumentChunk> chunks = domain.buildChunks();

        // Should produce a substantial number of chunks from the migration guides
        assertTrue(chunks.size() > 10,
                "Expected >10 chunks but got " + chunks.size());

        // At least some chunks should have component names
        long withComponent = chunks.stream()
                .filter(c -> c.component() != null)
                .count();
        assertTrue(withComponent > 0, "Expected some chunks with component names");

        // Should have both apache-camel and openrewrite sources
        assertTrue(chunks.stream().anyMatch(c -> "apache-camel".equals(c.source())));
        assertTrue(chunks.stream().anyMatch(c -> "openrewrite".equals(c.source())));
    }
}

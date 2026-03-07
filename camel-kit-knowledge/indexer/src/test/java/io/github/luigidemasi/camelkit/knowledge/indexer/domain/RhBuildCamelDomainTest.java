package io.github.luigidemasi.camelkit.knowledge.indexer.domain;

import io.github.luigidemasi.camelkit.knowledge.schema.DomainMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class RhBuildCamelDomainTest {

    @TempDir
    Path tempDir;

    private RhBuildCamelDomain createDomain() throws IOException {
        return new RhBuildCamelDomain(tempDir, tempDir, "http://localhost:5001");
    }

    @Test
    void metadataIsCorrect() throws Exception {
        RhBuildCamelDomain domain = createDomain();
        DomainMetadata meta = domain.metadata();
        assertEquals("rh_build_camel", meta.domainId());
        assertEquals("camel_rh_build", meta.toolName());
        assertTrue(meta.hasComponentField());
        assertTrue(meta.hasVersionFields());
    }

    @Test
    void guideMapContainsAllVersions() throws Exception {
        RhBuildCamelDomain domain = createDomain();
        var versions = domain.getVersions();
        assertEquals(5, versions.size());
        assertTrue(versions.contains("4.0"));
        assertTrue(versions.contains("4.4"));
        assertTrue(versions.contains("4.8"));
        assertTrue(versions.contains("4.10"));
        assertTrue(versions.contains("4.14"));
    }

    @Test
    void guideMapHasCorrectCountPerVersion() throws Exception {
        RhBuildCamelDomain domain = createDomain();
        assertEquals(12, domain.getGuidesForVersion("4.0").size());
        assertEquals(14, domain.getGuidesForVersion("4.4").size());
        assertEquals(18, domain.getGuidesForVersion("4.8").size());
        assertEquals(18, domain.getGuidesForVersion("4.10").size());
        assertEquals(19, domain.getGuidesForVersion("4.14").size());
    }

    @Test
    void extractVersionFromHeading_detectsVersionNumbers() throws Exception {
        RhBuildCamelDomain domain = createDomain();
        assertEquals("4.14", domain.extractVersionFromHeading("Camel 4.14 GA"));
        assertEquals("4.0", domain.extractVersionFromHeading("Version 4.0"));
        assertEquals("4.10", domain.extractVersionFromHeading("Red Hat build of Apache Camel 4.10"));
        assertNull(domain.extractVersionFromHeading("General Information"));
        assertNull(domain.extractVersionFromHeading(null));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DOCLING_URL", matches = ".+")
    void buildChunksFromLocalResources() throws Exception {
        String doclingUrl = System.getenv("DOCLING_URL");
        RhBuildCamelDomain domain = new RhBuildCamelDomain(tempDir, tempDir, doclingUrl);
        List<DocumentChunk> chunks = domain.buildChunks();
        assertTrue(chunks.size() > 0, "Expected chunks but got 0");
        assertTrue(chunks.stream().allMatch(c -> "red-hat-build-camel".equals(c.source())));
        long versionsFound = chunks.stream()
                .map(DocumentChunk::sourceVersion)
                .filter(Objects::nonNull)
                .distinct().count();
        assertTrue(versionsFound >= 1, "Expected chunks from at least 1 version");
    }
}

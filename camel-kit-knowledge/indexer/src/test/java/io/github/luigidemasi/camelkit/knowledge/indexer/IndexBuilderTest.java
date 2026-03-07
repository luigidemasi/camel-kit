package io.github.luigidemasi.camelkit.knowledge.indexer;

import io.github.luigidemasi.camelkit.knowledge.indexer.domain.DocumentChunk;
import io.github.luigidemasi.camelkit.knowledge.indexer.domain.DocumentDomain;
import io.github.luigidemasi.camelkit.knowledge.schema.DomainMetadata;
import io.github.luigidemasi.camelkit.knowledge.schema.KnowledgeFields;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndexBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildIndexFromSingleDomain() throws IOException {
        // Create a test domain with 2 chunks
        DocumentDomain testDomain = new DocumentDomain() {
            @Override
            public DomainMetadata metadata() {
                return DomainMetadata.migration(
                    "test_migration", "camel_test_migration", "Test migration docs"
                );
            }

            @Override
            public List<DocumentChunk> buildChunks() {
                return List.of(
                    new DocumentChunk("chunk-1", "test-source", "component-migration",
                        "2.x", "4.x", "http4", "http4 renamed", "http4 renamed to http"),
                    new DocumentChunk("chunk-2", "test-source", "component-migration",
                        "2.x", "4.x", "netty4", "netty4 renamed", "netty4 renamed to netty")
                );
            }
        };

        // Build the index
        IndexBuilder builder = new IndexBuilder();
        int count = builder.build(tempDir, List.of(testDomain));
        assertEquals(2, count);

        // Verify we can search by component
        try (IndexReader reader = DirectoryReader.open(FSDirectory.open(tempDir))) {
            IndexSearcher searcher = new IndexSearcher(reader);

            // Exact component lookup
            TopDocs hits = searcher.search(
                new TermQuery(new Term(KnowledgeFields.COMPONENT, "http4")), 10);
            assertEquals(1, hits.totalHits.value);

            Document doc = searcher.doc(hits.scoreDocs[0].doc);
            assertEquals("chunk-1", doc.get(KnowledgeFields.ID));
            assertEquals("test_migration", doc.get(KnowledgeFields.DOMAIN));
            assertEquals("http4 renamed to http", doc.get(KnowledgeFields.CONTENT));

            // Domain metadata stored on first doc only
            assertNotNull(doc.get(KnowledgeFields.DOMAIN_META));
            assertTrue(doc.get(KnowledgeFields.DOMAIN_META).contains("camel_test_migration"));
        }
    }

    @Test
    void buildIndexFromMultipleDomains() throws IOException {
        DocumentDomain domain1 = new DocumentDomain() {
            @Override
            public DomainMetadata metadata() {
                return DomainMetadata.migration("domain_a", "camel_domain_a", "Domain A");
            }

            @Override
            public List<DocumentChunk> buildChunks() {
                return List.of(
                    new DocumentChunk("a-1", "src-a", "component-migration",
                        "2.x", "4.x", "comp-a", "title-a", "content-a")
                );
            }
        };

        DocumentDomain domain2 = new DocumentDomain() {
            @Override
            public DomainMetadata metadata() {
                return DomainMetadata.migration("domain_b", "camel_domain_b", "Domain B");
            }

            @Override
            public List<DocumentChunk> buildChunks() {
                return List.of(
                    new DocumentChunk("b-1", "src-b", "platform-change",
                        null, null, null, "title-b", "content-b")
                );
            }
        };

        IndexBuilder builder = new IndexBuilder();
        int count = builder.build(tempDir, List.of(domain1, domain2));
        assertEquals(2, count);

        // Verify domain filtering works
        try (IndexReader reader = DirectoryReader.open(FSDirectory.open(tempDir))) {
            IndexSearcher searcher = new IndexSearcher(reader);

            TopDocs hitsA = searcher.search(
                new TermQuery(new Term(KnowledgeFields.DOMAIN, "domain_a")), 10);
            assertEquals(1, hitsA.totalHits.value);

            TopDocs hitsB = searcher.search(
                new TermQuery(new Term(KnowledgeFields.DOMAIN, "domain_b")), 10);
            assertEquals(1, hitsB.totalHits.value);
        }
    }
}

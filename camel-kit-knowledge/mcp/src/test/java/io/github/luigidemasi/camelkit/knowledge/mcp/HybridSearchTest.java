package io.github.luigidemasi.camelkit.knowledge.mcp;

import io.github.luigidemasi.camelkit.knowledge.embedding.OnnxEmbeddingProvider;
import io.github.luigidemasi.camelkit.knowledge.schema.KnowledgeDocument;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests hybrid BM25 + vector search.
 * Requires model.onnx on classpath — skipped if not available.
 */
@EnabledIf("modelExists")
class HybridSearchTest {

    private static IndexSearcher searcher;
    private static OnnxEmbeddingProvider embeddingProvider;

    static boolean modelExists() {
        return HybridSearchTest.class.getClassLoader()
                .getResource("models/model.onnx") != null;
    }

    @BeforeAll
    static void setUp() throws Exception {
        embeddingProvider = new OnnxEmbeddingProvider();

        // Build a small in-memory index with embeddings
        Directory dir = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
            addDoc(writer, "doc1", "camel_migration", "http4 renamed to http",
                    "The http4 component was renamed to http in Apache Camel 3.0. Update all URIs.");
            addDoc(writer, "doc2", "camel_migration", "netty4 renamed to netty",
                    "The netty4 component was renamed to netty in Apache Camel 3.0.");
            addDoc(writer, "doc3", "camel_migration", "JMS configuration guide",
                    "Configure ActiveMQ broker URL and connection pooling for message queues.");
            writer.commit();
        }

        searcher = new IndexSearcher(DirectoryReader.open(dir));
    }

    private static void addDoc(IndexWriter writer, String id, String domain,
                               String title, String content) throws Exception {
        float[] embedding = embeddingProvider.embed(title + " " + content);
        KnowledgeDocument doc = new KnowledgeDocument(id, domain)
                .source("test")
                .docType("test")
                .sectionTitle(title)
                .content(content)
                .embedding(embedding);
        writer.addDocument(doc.build());
    }

    @Test
    void keywordSearchFindsExactMatch() throws Exception {
        List<LuceneSearchService.SearchResult> results =
                LuceneSearchService.hybridSearch(searcher, embeddingProvider,
                        "camel_migration", "http4 renamed", null, null, 10, 0.6f, 0.4f);
        assertFalse(results.isEmpty());
        assertEquals("doc1", results.get(0).id());
    }

    @Test
    void semanticSearchFindsByMeaning() throws Exception {
        List<LuceneSearchService.SearchResult> results =
                LuceneSearchService.hybridSearch(searcher, embeddingProvider,
                        "camel_migration", "change HTTP library", null, null, 10, 0.6f, 0.4f);
        assertFalse(results.isEmpty(), "Semantic search should find results for paraphrased query");
        boolean foundHttp = results.stream().anyMatch(r -> r.id().equals("doc1"));
        assertTrue(foundHttp, "Should find http4 doc via semantic similarity");
    }

    @Test
    void semanticSearchFindsMessageQueueByParaphrase() throws Exception {
        List<LuceneSearchService.SearchResult> results =
                LuceneSearchService.hybridSearch(searcher, embeddingProvider,
                        "camel_migration", "messaging system setup", null, null, 10, 0.6f, 0.4f);
        assertFalse(results.isEmpty());
        boolean foundJms = results.stream().anyMatch(r -> r.id().equals("doc3"));
        assertTrue(foundJms, "Should find JMS doc via semantic similarity to messaging");
    }
}

package io.github.luigidemasi.camelkit.knowledge.mcp;

import io.github.luigidemasi.camelkit.knowledge.embedding.EmbeddingProvider;
import io.github.luigidemasi.camelkit.knowledge.embedding.OnnxEmbeddingProvider;
import io.github.luigidemasi.camelkit.knowledge.schema.KnowledgeFields;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Embedded Lucene search service that reads the pre-built knowledge index.
 * The index is loaded from classpath resources at startup.
 */
@ApplicationScoped
public class LuceneSearchService {

    private static final float BM25_WEIGHT = 0.2f;
    private static final float VECTOR_WEIGHT = 0.8f;

    private IndexReader reader;
    private IndexSearcher searcher;
    private EmbeddingProvider embeddingProvider;
    private final StandardAnalyzer analyzer = new StandardAnalyzer();

    @PostConstruct
    void init() {
        try {
            Path indexDir = extractIndexFromClasspath();
            reader = DirectoryReader.open(FSDirectory.open(indexDir));
            searcher = new IndexSearcher(reader);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open knowledge index", e);
        }

        // Initialize embedding provider (graceful degradation if model not available)
        try {
            embeddingProvider = new OnnxEmbeddingProvider();
            embeddingProvider.embed("warmup");
        } catch (Exception e) {
            System.out.println("WARNING: ONNX embedding model not available, falling back to BM25-only search");
            embeddingProvider = null;
        }
    }

    @PreDestroy
    void close() {
        try {
            if (reader != null) reader.close();
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * Get all unique domain IDs in the index.
     */
    public Set<String> getDomains() throws IOException {
        Set<String> domains = new HashSet<>();
        for (int i = 0; i < reader.maxDoc(); i++) {
            Document doc = reader.document(i);
            String domain = doc.get(KnowledgeFields.DOMAIN);
            if (domain != null) {
                domains.add(domain);
            }
        }
        return domains;
    }

    /**
     * Get domain metadata JSON for a given domain.
     */
    public String getDomainMeta(String domainId) throws IOException {
        for (int i = 0; i < reader.maxDoc(); i++) {
            Document doc = reader.document(i);
            if (domainId.equals(doc.get(KnowledgeFields.DOMAIN))) {
                String meta = doc.get(KnowledgeFields.DOMAIN_META);
                if (meta != null) return meta;
            }
        }
        return null;
    }

    /**
     * Exact component lookup within a domain.
     */
    public List<SearchResult> lookupComponent(String domain, String component, String sourceVersion) throws IOException {
        BooleanQuery.Builder qb = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(KnowledgeFields.DOMAIN, domain)), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term(KnowledgeFields.COMPONENT, component)), BooleanClause.Occur.MUST);

        if (sourceVersion != null) {
            qb.add(new TermQuery(new Term(KnowledgeFields.SOURCE_VERSION, sourceVersion)), BooleanClause.Occur.MUST);
        }

        return executeSearch(qb.build(), 10);
    }

    /**
     * Full-text search within a domain, using hybrid BM25 + vector scoring when available.
     */
    public List<SearchResult> search(String domain, String query, String sourceVersion, String targetVersion, int maxResults)
            throws IOException, ParseException {
        return hybridSearch(searcher, embeddingProvider, domain, query, sourceVersion, targetVersion, maxResults,
                BM25_WEIGHT, VECTOR_WEIGHT);
    }

    /**
     * Hybrid BM25 + vector search. Package-private and static so tests can call it
     * directly with an in-memory index and embedding provider.
     */
    static List<SearchResult> hybridSearch(IndexSearcher searcher, EmbeddingProvider embeddingProvider,
                                           String domain, String query, String sourceVersion,
                                           String targetVersion, int maxResults,
                                           float bm25Weight, float vectorWeight)
            throws IOException, ParseException {

        StandardAnalyzer analyzer = new StandardAnalyzer();

        // --- BM25 text search ---
        BooleanQuery.Builder bm25Builder = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(KnowledgeFields.DOMAIN, domain)), BooleanClause.Occur.MUST);
        QueryParser parser = new QueryParser(KnowledgeFields.CONTENT, analyzer);
        bm25Builder.add(parser.parse(query), BooleanClause.Occur.MUST);
        if (sourceVersion != null) {
            bm25Builder.add(new TermQuery(new Term(KnowledgeFields.SOURCE_VERSION, sourceVersion)), BooleanClause.Occur.SHOULD);
        }
        if (targetVersion != null) {
            bm25Builder.add(new TermQuery(new Term(KnowledgeFields.TARGET_VERSION, targetVersion)), BooleanClause.Occur.SHOULD);
        }

        int fetchSize = maxResults * 3;
        TopDocs bm25Docs = searcher.search(bm25Builder.build(), fetchSize);

        // --- Vector search (if embedding provider available) ---
        TopDocs vectorDocs = null;
        if (embeddingProvider != null) {
            float[] queryVector = embeddingProvider.embed(query);
            Query domainFilter = new TermQuery(new Term(KnowledgeFields.DOMAIN, domain));
            KnnFloatVectorQuery vectorQuery = new KnnFloatVectorQuery(
                    KnowledgeFields.EMBEDDING, queryVector, fetchSize, domainFilter);
            vectorDocs = searcher.search(vectorQuery, fetchSize);
        }

        // --- Hybrid merge ---
        Map<Integer, float[]> scoreMap = new LinkedHashMap<>();

        float bm25Max = 0;
        for (ScoreDoc sd : bm25Docs.scoreDocs) {
            bm25Max = Math.max(bm25Max, sd.score);
        }
        for (ScoreDoc sd : bm25Docs.scoreDocs) {
            float normalizedBm25 = bm25Max > 0 ? sd.score / bm25Max : 0;
            scoreMap.put(sd.doc, new float[]{normalizedBm25, 0});
        }

        if (vectorDocs != null && vectorDocs.scoreDocs.length > 0) {
            float vectorMax = 0;
            for (ScoreDoc sd : vectorDocs.scoreDocs) {
                vectorMax = Math.max(vectorMax, sd.score);
            }
            for (ScoreDoc sd : vectorDocs.scoreDocs) {
                float normalizedVector = vectorMax > 0 ? sd.score / vectorMax : 0;
                float[] scores = scoreMap.get(sd.doc);
                if (scores != null) {
                    scores[1] = normalizedVector;
                } else {
                    scoreMap.put(sd.doc, new float[]{0, normalizedVector});
                }
            }
        }

        List<Map.Entry<Integer, Float>> combined = new ArrayList<>();
        for (Map.Entry<Integer, float[]> entry : scoreMap.entrySet()) {
            float[] scores = entry.getValue();
            float combinedScore = bm25Weight * scores[0] + vectorWeight * scores[1];
            combined.add(Map.entry(entry.getKey(), combinedScore));
        }
        combined.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < Math.min(combined.size(), maxResults); i++) {
            Map.Entry<Integer, Float> entry = combined.get(i);
            Document doc = searcher.doc(entry.getKey());
            results.add(new SearchResult(
                    doc.get(KnowledgeFields.ID),
                    doc.get(KnowledgeFields.SOURCE),
                    doc.get(KnowledgeFields.DOC_TYPE),
                    doc.get(KnowledgeFields.SOURCE_VERSION),
                    doc.get(KnowledgeFields.TARGET_VERSION),
                    doc.get(KnowledgeFields.SECTION_TITLE),
                    doc.get(KnowledgeFields.CONTENT),
                    entry.getValue()
            ));
        }

        return results;
    }

    private List<SearchResult> executeSearch(Query query, int maxResults) throws IOException {
        TopDocs topDocs = searcher.search(query, maxResults);
        List<SearchResult> results = new ArrayList<>();

        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            results.add(new SearchResult(
                    doc.get(KnowledgeFields.ID),
                    doc.get(KnowledgeFields.SOURCE),
                    doc.get(KnowledgeFields.DOC_TYPE),
                    doc.get(KnowledgeFields.SOURCE_VERSION),
                    doc.get(KnowledgeFields.TARGET_VERSION),
                    doc.get(KnowledgeFields.SECTION_TITLE),
                    doc.get(KnowledgeFields.CONTENT),
                    scoreDoc.score
            ));
        }

        return results;
    }

    private Path extractIndexFromClasspath() throws IOException {
        Path tempDir = Files.createTempDirectory("knowledge-index");
        tempDir.toFile().deleteOnExit();

        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        // Read manifest to discover index files
        List<String> indexFiles;
        try (InputStream manifestIs = cl.getResourceAsStream("knowledge-index/INDEX_FILES")) {
            if (manifestIs != null) {
                indexFiles = new String(manifestIs.readAllBytes()).lines()
                        .filter(l -> !l.isBlank())
                        .toList();
            } else {
                // Fallback to known files
                indexFiles = List.of("segments_1", "_0.cfs", "_0.cfe", "_0.si");
            }
        }

        for (String file : indexFiles) {
            try (InputStream is = cl.getResourceAsStream("knowledge-index/" + file)) {
                if (is != null) {
                    Files.copy(is, tempDir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        return tempDir;
    }

    /**
     * Search result record.
     */
    public record SearchResult(
            String id,
            String source,
            String docType,
            String sourceVersion,
            String targetVersion,
            String sectionTitle,
            String content,
            float score
    ) {}
}

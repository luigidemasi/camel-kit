package io.github.luigidemasi.camelkit.knowledge.mcp;

import io.github.luigidemasi.camelkit.knowledge.embedding.EmbeddingProvider;
import io.github.luigidemasi.camelkit.knowledge.embedding.OnnxEmbeddingProvider;
import io.github.luigidemasi.camelkit.knowledge.schema.KnowledgeFields;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

    @Inject
    IndexResolver indexResolver;

    private IndexReader reader;
    private IndexSearcher searcher;
    private EmbeddingProvider embeddingProvider;
    private final StandardAnalyzer analyzer = new StandardAnalyzer();

    @PostConstruct
    void init() {
        try {
            Path indexDir = resolveIndex();
            reader = DirectoryReader.open(FSDirectory.open(indexDir));
            searcher = new IndexSearcher(reader);
        } catch (Exception e) {
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
            String[] rtVals = doc.getValues(KnowledgeFields.RUNTIME);
            List<String> runtimes = rtVals != null && rtVals.length > 0 ? List.of(rtVals) : List.of();
            results.add(new SearchResult(
                    doc.get(KnowledgeFields.ID),
                    doc.get(KnowledgeFields.SOURCE),
                    doc.get(KnowledgeFields.DOC_TYPE),
                    doc.get(KnowledgeFields.SOURCE_VERSION),
                    doc.get(KnowledgeFields.TARGET_VERSION),
                    runtimes,
                    doc.get(KnowledgeFields.SECTION_TITLE),
                    doc.get(KnowledgeFields.CONTENT),
                    entry.getValue()
            ));
        }

        return results;
    }

    /**
     * Search by JIRA issue ID (exact match on multi-valued jira_id field).
     */
    public List<SearchResult> searchByJiraId(String jiraId) throws IOException {
        BooleanQuery query = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(KnowledgeFields.JIRA_ID, jiraId)), BooleanClause.Occur.MUST)
                .build();

        return executeSearch(query, 20);
    }

    /**
     * Search errata by CVE ID (exact match on multi-valued cve_ids field).
     */
    public List<ErrataSearchResult> searchByCve(String cveId) throws IOException {
        BooleanQuery query = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(KnowledgeFields.DOMAIN, "rh_build_camel")), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term(KnowledgeFields.DOC_TYPE, "errata")), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term(KnowledgeFields.CVE_IDS, cveId)), BooleanClause.Occur.MUST)
                .build();

        return executeErrataSearch(query, 20);
    }

    /**
     * Search errata with structured filters (advisory type, severity, version) and optional free-text.
     */
    public List<ErrataSearchResult> searchErrata(String advisoryType, String severity,
                                                  String version, String freeText, int maxResults)
            throws IOException, ParseException {

        BooleanQuery.Builder qb = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(KnowledgeFields.DOMAIN, "rh_build_camel")), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term(KnowledgeFields.DOC_TYPE, "errata")), BooleanClause.Occur.MUST);

        if (advisoryType != null) {
            qb.add(new TermQuery(new Term(KnowledgeFields.ADVISORY_TYPE, advisoryType)), BooleanClause.Occur.MUST);
        }
        if (severity != null) {
            qb.add(new TermQuery(new Term(KnowledgeFields.SEVERITY, severity)), BooleanClause.Occur.MUST);
        }
        if (version != null) {
            qb.add(new TermQuery(new Term(KnowledgeFields.FIXED_IN_VERSIONS, version)), BooleanClause.Occur.MUST);
        }
        if (freeText != null && !freeText.isBlank()) {
            QueryParser parser = new QueryParser(KnowledgeFields.CONTENT, analyzer);
            qb.add(parser.parse(freeText), BooleanClause.Occur.MUST);
        }

        return executeErrataSearch(qb.build(), maxResults);
    }

    /**
     * Search errata by fixed-in version (exact match on multi-valued fixed_in_versions field).
     */
    public List<ErrataSearchResult> searchByVersion(String version, String advisoryType, int maxResults)
            throws IOException {

        BooleanQuery.Builder qb = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(KnowledgeFields.DOMAIN, "rh_build_camel")), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term(KnowledgeFields.DOC_TYPE, "errata")), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term(KnowledgeFields.FIXED_IN_VERSIONS, version)), BooleanClause.Occur.MUST);

        if (advisoryType != null) {
            qb.add(new TermQuery(new Term(KnowledgeFields.ADVISORY_TYPE, advisoryType)), BooleanClause.Occur.MUST);
        }

        return executeErrataSearch(qb.build(), maxResults);
    }

    private List<ErrataSearchResult> executeErrataSearch(Query query, int maxResults) throws IOException {
        TopDocs topDocs = searcher.search(query, maxResults);
        List<ErrataSearchResult> results = new ArrayList<>();

        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            String[] cveIds = doc.getValues(KnowledgeFields.CVE_IDS);
            String[] fixedIn = doc.getValues(KnowledgeFields.FIXED_IN_VERSIONS);

            results.add(new ErrataSearchResult(
                    doc.get(KnowledgeFields.ID),
                    doc.get(KnowledgeFields.ERRATUM_ID),
                    doc.get(KnowledgeFields.ADVISORY_TYPE),
                    doc.get(KnowledgeFields.SEVERITY),
                    doc.get(KnowledgeFields.SECTION_TITLE),
                    doc.get(KnowledgeFields.CONTENT),
                    cveIds != null ? List.of(cveIds) : List.of(),
                    fixedIn != null ? List.of(fixedIn) : List.of(),
                    scoreDoc.score
            ));
        }

        return results;
    }

    private List<SearchResult> executeSearch(Query query, int maxResults) throws IOException {
        TopDocs topDocs = searcher.search(query, maxResults);
        List<SearchResult> results = new ArrayList<>();

        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            String[] rtVals = doc.getValues(KnowledgeFields.RUNTIME);
            List<String> runtimes = rtVals != null && rtVals.length > 0 ? List.of(rtVals) : List.of();
            results.add(new SearchResult(
                    doc.get(KnowledgeFields.ID),
                    doc.get(KnowledgeFields.SOURCE),
                    doc.get(KnowledgeFields.DOC_TYPE),
                    doc.get(KnowledgeFields.SOURCE_VERSION),
                    doc.get(KnowledgeFields.TARGET_VERSION),
                    runtimes,
                    doc.get(KnowledgeFields.SECTION_TITLE),
                    doc.get(KnowledgeFields.CONTENT),
                    scoreDoc.score
            ));
        }

        return results;
    }

    /**
     * Resolve the knowledge index. Tries IndexResolver first (Maven artifact download),
     * falls back to classpath extraction for backward compatibility.
     */
    private Path resolveIndex() throws IOException {
        // Try IndexResolver (downloads from Maven repo)
        try {
            return indexResolver.resolve();
        } catch (IndexResolver.IndexResolverException e) {
            System.out.println("WARNING: IndexResolver failed (" + e.getMessage() +
                    "), falling back to classpath extraction");
        }

        // Fallback: extract from classpath (legacy — index bundled in uber-jar)
        return extractIndexFromClasspath();
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
            List<String> runtimes,
            String sectionTitle,
            String content,
            float score
    ) {}

    /**
     * Errata-specific search result with structured fields.
     */
    public record ErrataSearchResult(
            String id,
            String erratumId,
            String advisoryType,
            String severity,
            String sectionTitle,
            String content,
            List<String> cveIds,
            List<String> fixedInVersions,
            float score
    ) {}
}

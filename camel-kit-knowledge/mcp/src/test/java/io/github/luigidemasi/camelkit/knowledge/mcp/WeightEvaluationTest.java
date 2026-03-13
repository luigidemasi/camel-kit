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

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluation test to compare different BM25/vector weight ratios for hybrid search.
 * Requires model.onnx on classpath — skipped if not available.
 */
@EnabledIf("modelExists")
class WeightEvaluationTest {

    private static IndexSearcher searcher;
    private static OnnxEmbeddingProvider embeddingProvider;

    static boolean modelExists() {
        return WeightEvaluationTest.class.getClassLoader()
                .getResource("models/model_quantized.onnx") != null;
    }

    @BeforeAll
    static void setUp() throws Exception {
        embeddingProvider = new OnnxEmbeddingProvider();

        Directory dir = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
            addDoc(writer, "renamed-components", "camel_migration", "Renamed components",
                    "The http4 component has been renamed to http in Apache Camel 3.0. The netty4 component was renamed to netty. The mongodb3 component was renamed to mongodb.",
                    "2.x", "3.x");

            addDoc(writer, "removed-components", "camel_migration", "Removed components",
                    "The old camel-http, camel-hdfs, camel-mina, camel-mongodb, camel-netty, camel-restlet components have been removed. Use their modern replacements.",
                    "2.x", "3.x");

            addDoc(writer, "jms-config", "camel_migration", "JMS endpoint configuration",
                    "Configure ActiveMQ broker URL and connection pooling for message queues. Set transacted=true for reliable messaging.",
                    "2.x", "3.x");

            addDoc(writer, "rest-dsl", "camel_migration", "REST DSL changes",
                    "The REST DSL has been updated. Use restConfiguration() to set up REST endpoints. CXF-RS components can be replaced with platform-http.",
                    "2.x", "3.x");

            addDoc(writer, "xml-to-yaml", "camel_migration", "XML route migration",
                    "Spring XML routes with camelContext can be converted to YAML DSL. Blueprint XML routes from ServiceMix/Karaf need manual conversion.",
                    "2.x", "4.x");

            addDoc(writer, "error-handling", "camel_migration", "Error handling changes",
                    "The onException clause behavior changed. Dead letter channel configuration updated. Use errorHandler() with new options.",
                    "2.x", "3.x");

            addDoc(writer, "type-converters", "camel_migration", "Type converter changes",
                    "Custom type converters must now use annotations. The old programmatic registration method is deprecated.",
                    "2.x", "3.x");

            addDoc(writer, "testing-migration", "camel_migration", "Testing framework changes",
                    "CamelTestSupport updated. Use CamelQuarkusTestSupport for Quarkus. Mock endpoints and test assertions unchanged.",
                    "2.x", "4.x");

            addDoc(writer, "cve-2025-27636", "camel_migration", "CVE-2025-27636 security advisory",
                    "Red Hat Build of Apache Camel 4.8 and 4.10 are affected by CVE-2025-27636, a header injection vulnerability in camel-bean component. Fixed in Red Hat Build of Apache Camel 4.10.2.redhat-00001 and 4.8.7.redhat-00002. CVSS 6.5 Important. Upgrade to patched version.",
                    "4.8", "4.10");

            writer.commit();
        }

        searcher = new IndexSearcher(DirectoryReader.open(dir));
    }

    private static void addDoc(IndexWriter writer, String id, String domain,
                               String title, String content,
                               String sourceVersion, String targetVersion) throws Exception {
        float[] embedding = embeddingProvider.embed(title + " " + content);
        KnowledgeDocument doc = new KnowledgeDocument(id, domain)
                .source("test")
                .docType("test")
                .sourceVersion(sourceVersion)
                .targetVersion(targetVersion)
                .sectionTitle(title)
                .content(content)
                .embedding(embedding);
        writer.addDocument(doc.build());
    }

    record EvalQuery(String query, String expectedDocId, String description) {}

    record WeightConfig(float bm25Weight, float vectorWeight, String label) {}

    record QueryResult(int rank, float score) {}

    @Test
    void evaluateWeightRatios() throws Exception {
        List<EvalQuery> queries = List.of(
                new EvalQuery("change HTTP library", "renamed-components", "Semantic: paraphrase of http4->http rename"),
                new EvalQuery("http4 renamed", "renamed-components", "Keyword: exact match"),
                new EvalQuery("messaging system setup", "jms-config", "Semantic: paraphrase of JMS/ActiveMQ config"),
                new EvalQuery("how to handle REST endpoints", "rest-dsl", "Semantic: paraphrase of REST DSL"),
                new EvalQuery("convert XML routes to YAML", "xml-to-yaml", "Mixed: some keyword overlap + semantic"),
                new EvalQuery("dealing with failures and retries", "error-handling", "Semantic: paraphrase of error handling"),
                new EvalQuery("update unit tests for new version", "testing-migration", "Semantic: paraphrase of testing changes"),
                new EvalQuery("ActiveMQ connection pooling", "jms-config", "Keyword: direct match"),
                new EvalQuery("which version of red hat build of apache camel is affected by CVE-2025-27636", "cve-2025-27636", "Mixed: CVE ID keyword + semantic context")
        );

        List<WeightConfig> configs = List.of(
                new WeightConfig(1.0f, 0.0f, "BM25 100"),
                new WeightConfig(0.6f, 0.4f, "BM25 60/Vec 40"),
                new WeightConfig(0.5f, 0.5f, "BM25 50/Vec 50"),
                new WeightConfig(0.4f, 0.6f, "BM25 40/Vec 60"),
                new WeightConfig(0.3f, 0.7f, "BM25 30/Vec 70"),
                new WeightConfig(0.2f, 0.8f, "BM25 20/Vec 80"),
                new WeightConfig(0.0f, 1.0f, "Vec 100")
        );

        // results[queryIdx][configIdx]
        QueryResult[][] results = new QueryResult[queries.size()][configs.size()];

        for (int qi = 0; qi < queries.size(); qi++) {
            EvalQuery eq = queries.get(qi);
            for (int ci = 0; ci < configs.size(); ci++) {
                WeightConfig wc = configs.get(ci);
                List<LuceneSearchService.SearchResult> searchResults =
                        LuceneSearchService.hybridSearch(searcher, embeddingProvider,
                                "camel_migration", eq.query(), null, null, 10,
                                wc.bm25Weight(), wc.vectorWeight());

                int rank = 0;
                float score = 0;
                for (int r = 0; r < searchResults.size(); r++) {
                    if (eq.expectedDocId().equals(searchResults.get(r).id())) {
                        rank = r + 1;
                        score = searchResults.get(r).score();
                        break;
                    }
                }
                results[qi][ci] = new QueryResult(rank, score);
            }
        }

        // Print detailed results
        System.out.println();
        System.out.println("=== Hybrid Search Weight Evaluation ===");
        System.out.println();

        for (int qi = 0; qi < queries.size(); qi++) {
            EvalQuery eq = queries.get(qi);
            System.out.printf("Query: \"%s\" (expected: %s)%n", eq.query(), eq.expectedDocId());
            System.out.printf("  %s%n", eq.description());
            for (int ci = 0; ci < configs.size(); ci++) {
                WeightConfig wc = configs.get(ci);
                QueryResult qr = results[qi][ci];
                String rankStr = qr.rank() == 0 ? "NOT FOUND" : "#" + qr.rank();
                System.out.printf("  %-15s: rank=%-10s score=%.4f%n", wc.label(), rankStr, qr.score());
            }
            System.out.println();
        }

        // Print summary table
        System.out.println("=== SUMMARY ===");
        int col = 16;
        System.out.printf("%-20s", "");
        for (WeightConfig wc : configs) {
            System.out.printf("  %-" + col + "s", wc.label());
        }
        System.out.println();

        // Avg rank
        System.out.printf("%-20s", "Avg rank:");
        for (int ci = 0; ci < configs.size(); ci++) {
            double sum = 0;
            int count = 0;
            for (int qi = 0; qi < queries.size(); qi++) {
                int rank = results[qi][ci].rank();
                if (rank > 0) {
                    sum += rank;
                    count++;
                } else {
                    sum += queries.size() + 1; // penalty for not found
                    count++;
                }
            }
            System.out.printf("  %-" + col + ".1f", sum / count);
        }
        System.out.println();

        // #1 hits
        System.out.printf("%-20s", "#1 hits:");
        for (int ci = 0; ci < configs.size(); ci++) {
            int hits = 0;
            for (int qi = 0; qi < queries.size(); qi++) {
                if (results[qi][ci].rank() == 1) hits++;
            }
            System.out.printf("  %-" + col + "s", hits + "/" + queries.size());
        }
        System.out.println();

        // Worst rank
        System.out.printf("%-20s", "Worst rank:");
        for (int ci = 0; ci < configs.size(); ci++) {
            int worst = 0;
            for (int qi = 0; qi < queries.size(); qi++) {
                int rank = results[qi][ci].rank();
                if (rank == 0) rank = queries.size() + 1;
                worst = Math.max(worst, rank);
            }
            System.out.printf("  %-" + col + "d", worst);
        }
        System.out.println();

        // Avg score
        System.out.printf("%-20s", "Avg score:");
        for (int ci = 0; ci < configs.size(); ci++) {
            double sum = 0;
            for (int qi = 0; qi < queries.size(); qi++) {
                sum += results[qi][ci].score();
            }
            System.out.printf("  %-" + col + ".4f", sum / queries.size());
        }
        System.out.println();
        System.out.println();
    }
}

package io.github.luigidemasi.camelkit.knowledge.mcp;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;
import io.github.luigidemasi.camelkit.knowledge.embedding.EmbeddingProvider;
import io.github.luigidemasi.camelkit.knowledge.embedding.OnnxEmbeddingProvider;
import io.github.luigidemasi.camelkit.knowledge.schema.KnowledgeDocument;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Empirical comparison of embedding models using real indexer documents.
 * Loads markdown files from the indexer resources, chunks them with SectionChunker logic,
 * builds indexes with each model, and evaluates search quality with realistic queries.
 *
 * Run with: mvn test -pl camel-kit-knowledge/mcp -Dmodel.comparison=true -Dtest=ModelComparisonTest
 */
@EnabledIfSystemProperty(named = "model.comparison", matches = "true")
class ModelComparisonTest {

    private static final String HF_RESOLVE = "https://huggingface.co/%s/resolve/main/%s";
    private static final Path MODELS_DIR = Path.of("target/test-models");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{2,4})\\s+(.+)$", Pattern.MULTILINE);

    // Boilerplate section titles to skip
    private static final Set<String> SKIP_TITLES = Set.of(
            "Introduction", "Legal Notice", "Preface",
            "Providing feedback on Red Hat build of Apache Camel documentation",
            "Learn", "Try, buy, & sell", "Communities",
            "About Red Hat Documentation", "Making open source more inclusive",
            "About Red Hat", "Theme"
    );

    // --- Model configurations ---

    record ModelConfig(String name, String repo, String[] onnxFiles, String tokenizerFile,
                       int dimensions, int maxSeqLength) {}

    static final ModelConfig GRANITE_SMALL = new ModelConfig(
            "granite-small-r2 (Q8, 384d)",
            "onnx-community/granite-embedding-small-english-r2-ONNX",
            new String[]{"onnx/model_quantized.onnx", "onnx/model_quantized.onnx_data"},
            "tokenizer.json",
            384, 512);

    static final ModelConfig GRANITE_BASE = new ModelConfig(
            "granite-base-r2 (Q8, 768d)",
            "onnx-community/granite-embedding-english-r2-ONNX",
            new String[]{"onnx/model_quantized.onnx", "onnx/model_quantized.onnx_data"},
            "tokenizer.json",
            768, 512);

    // --- Evaluation queries ---
    // Each query targets a specific document file. The "expectedFile" is the basename (without .md)
    // of the source file that should appear in the top results.

    record EvalQuery(String query, String expectedFile, String type) {}
    record QueryResult(int rank, float score, String topResultTitle) {}

    static final List<EvalQuery> QUERIES = List.of(
            // Keyword-heavy queries (terms that appear literally in the documents)
            new EvalQuery("removed components Apache Camel 4", "camel-migration", "keyword"),
            new EvalQuery("javax to jakarta namespace change", "fuse7-migration", "keyword"),
            new EvalQuery("Blueprint XML DSL route migration", "fuse7-migration", "keyword"),
            new EvalQuery("CircuitBreaker EIP resilience4j", "camel-migration", "keyword"),

            // Semantic queries (paraphrased, no direct keyword overlap)
            new EvalQuery("how to move my Fuse 7 application to a modern runtime", "fuse7-migration", "semantic"),
            new EvalQuery("setting up REST endpoints in a Quarkus application", "developing-quarkus", "semantic"),
            new EvalQuery("visual route editor for integration flows", "kaoto", "semantic"),
            new EvalQuery("web console for monitoring running integrations", "hawtio", "semantic"),
            new EvalQuery("upgrading test framework from JUnit 4", "camel-migration", "semantic"),
            new EvalQuery("managing application dependencies with a BOM", "developing-quarkus", "semantic"),

            // Mixed queries (some keyword overlap + semantic understanding needed)
            new EvalQuery("migrate Camel K operator to newer version", "camel-k-migration", "mixed"),
            new EvalQuery("configure Kafka component in Spring Boot", "spring-boot-reference", "mixed"),
            new EvalQuery("Quarkus native compilation for Camel", "developing-quarkus", "mixed"),
            new EvalQuery("Spring Boot starter for CXF SOAP services", "spring-boot-reference", "mixed"),
            new EvalQuery("Camel health checks and readiness probes", "camel-migration", "mixed"),
            new EvalQuery("Kamelet custom connector reusable", "kamelets-reference", "mixed")
    );

    // --- Chunk record ---

    record DocChunk(String id, String sourceFile, String sectionTitle, String content) {}

    // --- Main comparison test ---

    @Test
    void compareModels() throws Exception {
        System.out.println();
        System.out.println("==========================================================");
        System.out.println("   EMBEDDING MODEL COMPARISON — Real Indexer Documents     ");
        System.out.println("==========================================================");
        System.out.println();

        // 1. Load and chunk real documents
        Path resourcesDir = resolveIndexerResources();
        List<DocChunk> chunks = loadAndChunkDocuments(resourcesDir);
        System.out.printf("Loaded %d chunks from indexer resources%n%n", chunks.size());

        List<ModelResult> allResults = new ArrayList<>();

        // 2. MiniLM baseline (from classpath if available)
        if (miniLMAvailable()) {
            System.out.println("[1/3] Loading MiniLM-L6-v2 (classpath)...");
            OnnxEmbeddingProvider miniLM = new OnnxEmbeddingProvider();
            ModelResult mr = evaluateModel("MiniLM-L6-v2 (FP32, 384d)", miniLM, chunks);
            mr.modelSizeMB = 87;
            allResults.add(mr);
        } else {
            System.out.println("[1/3] MiniLM-L6-v2 not on classpath — skipping baseline");
        }

        // 3. Granite small
        System.out.println("[2/3] Preparing granite-embedding-small-english-r2...");
        Path smallDir = ensureModelDownloaded(GRANITE_SMALL);
        try (FileBasedEmbeddingProvider graniteSmall = new FileBasedEmbeddingProvider(
                smallDir, "model_quantized.onnx", GRANITE_SMALL.dimensions(), GRANITE_SMALL.maxSeqLength())) {
            ModelResult mr = evaluateModel(GRANITE_SMALL.name(), graniteSmall, chunks);
            mr.modelSizeMB = dirSizeMB(smallDir);
            allResults.add(mr);
        }

        // 4. Granite base
        System.out.println("[3/3] Preparing granite-embedding-english-r2...");
        Path baseDir = ensureModelDownloaded(GRANITE_BASE);
        try (FileBasedEmbeddingProvider graniteBase = new FileBasedEmbeddingProvider(
                baseDir, "model_quantized.onnx", GRANITE_BASE.dimensions(), GRANITE_BASE.maxSeqLength())) {
            ModelResult mr = evaluateModel(GRANITE_BASE.name(), graniteBase, chunks);
            mr.modelSizeMB = dirSizeMB(baseDir);
            allResults.add(mr);
        }

        // 5. Print comparison
        printComparison(allResults);
    }

    // --- Document loading ---

    private Path resolveIndexerResources() {
        // Try relative to MCP module (Maven test cwd)
        Path fromMcp = Path.of("../indexer/src/main/resources/rh-build-camel");
        if (Files.isDirectory(fromMcp)) return fromMcp;

        // Try from project root
        Path fromRoot = Path.of("camel-kit-knowledge/indexer/src/main/resources/rh-build-camel");
        if (Files.isDirectory(fromRoot)) return fromRoot;

        throw new RuntimeException("Cannot find indexer resources. Run from MCP module or project root.");
    }

    private List<DocChunk> loadAndChunkDocuments(Path resourcesDir) throws IOException {
        List<DocChunk> chunks = new ArrayList<>();

        // Load markdown files from version 4.14 (latest) + 4.8 + KB articles
        List<Path> versionDirs = List.of(
                resourcesDir.resolve("4.14"),
                resourcesDir.resolve("4.8")
        );
        Path kbDir = resourcesDir.resolve("kb-articles");

        for (Path versionDir : versionDirs) {
            if (!Files.isDirectory(versionDir)) continue;
            String version = versionDir.getFileName().toString();
            try (Stream<Path> mdFiles = Files.list(versionDir).filter(p -> p.toString().endsWith(".md"))) {
                mdFiles.forEach(mdFile -> {
                    try {
                        chunks.addAll(chunkFile(mdFile, version));
                    } catch (IOException e) {
                        System.err.println("  Warning: failed to read " + mdFile + ": " + e.getMessage());
                    }
                });
            }
        }

        // KB articles
        if (Files.isDirectory(kbDir)) {
            try (Stream<Path> mdFiles = Files.list(kbDir).filter(p -> p.toString().endsWith(".md")
                    && !p.getFileName().toString().equals("README.md"))) {
                mdFiles.forEach(mdFile -> {
                    try {
                        chunks.addAll(chunkFile(mdFile, "kb"));
                    } catch (IOException e) {
                        System.err.println("  Warning: failed to read " + mdFile + ": " + e.getMessage());
                    }
                });
            }
        }

        return chunks;
    }

    private List<DocChunk> chunkFile(Path mdFile, String version) throws IOException {
        String markdown = Files.readString(mdFile);
        String baseName = mdFile.getFileName().toString().replaceFirst("\\.md$", "");

        List<DocChunk> chunks = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(markdown);

        int lastEnd = 0;
        String currentTitle = "Introduction";

        while (matcher.find()) {
            String content = markdown.substring(lastEnd, matcher.start()).trim();
            if (!content.isEmpty() && !SKIP_TITLES.contains(currentTitle) && content.length() > 50) {
                String id = version + "/" + baseName + ":" + slugify(currentTitle);
                chunks.add(new DocChunk(id, baseName, currentTitle, content));
            }
            currentTitle = cleanTitle(matcher.group(2).trim());
            lastEnd = matcher.end();
        }

        String remaining = markdown.substring(lastEnd).trim();
        if (!remaining.isEmpty() && !SKIP_TITLES.contains(currentTitle) && remaining.length() > 50) {
            String id = version + "/" + baseName + ":" + slugify(currentTitle);
            chunks.add(new DocChunk(id, baseName, currentTitle, remaining));
        }

        return chunks;
    }

    private String cleanTitle(String title) {
        // Remove "[... Copy link](#...)" suffix from Red Hat docs
        int copyLink = title.indexOf(" Copy link");
        if (copyLink > 0) title = title.substring(0, copyLink);
        // Remove leading "[" and trailing "](#...)"
        if (title.startsWith("[")) {
            int closeBracket = title.indexOf(']');
            if (closeBracket > 0) title = title.substring(1, closeBracket);
        }
        return title.trim();
    }

    private String slugify(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    // --- Evaluation ---

    static class ModelResult {
        String name;
        QueryResult[] results;
        double avgEmbedMs;
        long modelSizeMB;
        int totalChunks;
    }

    private ModelResult evaluateModel(String name, EmbeddingProvider provider, List<DocChunk> chunks)
            throws Exception {
        System.out.printf("  Building index (%d chunks) with %s...%n", chunks.size(), name);

        // Build in-memory index
        Directory dir = new ByteBuffersDirectory();
        long embedStart = System.nanoTime();
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
            int count = 0;
            for (DocChunk chunk : chunks) {
                float[] embedding = provider.embed(chunk.sectionTitle() + " " + chunk.content());
                KnowledgeDocument doc = new KnowledgeDocument(chunk.id(), "rh_build_camel")
                        .source("rh-docs").docType("guide")
                        .sectionTitle(chunk.sectionTitle())
                        .content(chunk.content())
                        .embedding(embedding);
                writer.addDocument(doc.build());
                count++;
                if (count % 100 == 0) {
                    System.out.printf("    Indexed %d/%d chunks...%n", count, chunks.size());
                }
            }
            writer.commit();
        }
        long embedEnd = System.nanoTime();
        double avgEmbedMs = (embedEnd - embedStart) / (chunks.size() * 1_000_000.0);

        System.out.printf("  Index built. Avg embed: %.1f ms/chunk%n", avgEmbedMs);

        IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(dir));

        // Run evaluation queries
        QueryResult[] results = new QueryResult[QUERIES.size()];
        for (int qi = 0; qi < QUERIES.size(); qi++) {
            EvalQuery eq = QUERIES.get(qi);
            List<LuceneSearchService.SearchResult> searchResults =
                    LuceneSearchService.hybridSearch(searcher, provider,
                            "rh_build_camel", eq.query(), null, null, 10, 0.2f, 0.8f);

            int rank = 0;
            float score = 0;
            String topTitle = searchResults.isEmpty() ? "N/A" : searchResults.get(0).sectionTitle();

            for (int r = 0; r < searchResults.size(); r++) {
                String resultId = searchResults.get(r).id();
                // Match: the result ID contains the expected file basename
                if (resultId.contains("/" + eq.expectedFile() + ":") ||
                        resultId.contains("/" + eq.expectedFile() + "/")) {
                    rank = r + 1;
                    score = searchResults.get(r).score();
                    break;
                }
            }
            results[qi] = new QueryResult(rank, score, topTitle);
        }

        ModelResult mr = new ModelResult();
        mr.name = name;
        mr.results = results;
        mr.avgEmbedMs = avgEmbedMs;
        mr.totalChunks = chunks.size();
        return mr;
    }

    // --- Output ---

    private void printComparison(List<ModelResult> allResults) {
        int col = allResults.stream().mapToInt(r -> r.name.length()).max().orElse(20) + 2;

        // --- Per-query detail ---
        System.out.println();
        System.out.println("=== PER-QUERY RESULTS ===");
        System.out.println();

        for (int qi = 0; qi < QUERIES.size(); qi++) {
            EvalQuery eq = QUERIES.get(qi);
            System.out.printf("Q%d [%s]: \"%s\"  (expected: %s)%n", qi + 1, eq.type(), eq.query(), eq.expectedFile());
            for (ModelResult mr : allResults) {
                QueryResult qr = mr.results[qi];
                String rankStr = qr.rank() == 0 ? "NOT FOUND" : "#" + qr.rank();
                System.out.printf("  %-" + col + "s  rank=%-12s score=%.4f  top1=\"%s\"%n",
                        mr.name, rankStr, qr.score(),
                        truncate(qr.topResultTitle(), 60));
            }
            System.out.println();
        }

        // --- Summary by query type ---
        System.out.println("=== SUMMARY BY QUERY TYPE ===");
        System.out.println();

        for (String type : List.of("keyword", "semantic", "mixed", "ALL")) {
            System.out.printf("--- %s queries ---%n", type);
            System.out.printf("%-25s", "Metric");
            for (ModelResult mr : allResults) {
                System.out.printf("  %-" + col + "s", mr.name);
            }
            System.out.println();

            List<Integer> indices = new ArrayList<>();
            for (int qi = 0; qi < QUERIES.size(); qi++) {
                if (type.equals("ALL") || QUERIES.get(qi).type().equals(type)) {
                    indices.add(qi);
                }
            }

            // Avg rank
            System.out.printf("%-25s", "Avg rank");
            for (ModelResult mr : allResults) {
                double sum = 0;
                for (int qi : indices) {
                    int r = mr.results[qi].rank();
                    sum += r > 0 ? r : QUERIES.size() + 1;
                }
                System.out.printf("  %-" + col + ".2f", sum / indices.size());
            }
            System.out.println();

            // Top-1 accuracy
            System.out.printf("%-25s", "#1 hits");
            for (ModelResult mr : allResults) {
                int hits = 0;
                for (int qi : indices) {
                    if (mr.results[qi].rank() == 1) hits++;
                }
                System.out.printf("  %-" + col + "s", hits + "/" + indices.size());
            }
            System.out.println();

            // Top-3 accuracy
            System.out.printf("%-25s", "Top-3 hits");
            for (ModelResult mr : allResults) {
                int hits = 0;
                for (int qi : indices) {
                    int r = mr.results[qi].rank();
                    if (r >= 1 && r <= 3) hits++;
                }
                System.out.printf("  %-" + col + "s", hits + "/" + indices.size());
            }
            System.out.println();

            // Not found
            System.out.printf("%-25s", "Not found");
            for (ModelResult mr : allResults) {
                int nf = 0;
                for (int qi : indices) {
                    if (mr.results[qi].rank() == 0) nf++;
                }
                System.out.printf("  %-" + col + "d", nf);
            }
            System.out.println();
            System.out.println();
        }

        // --- Infrastructure metrics ---
        System.out.println("=== INFRASTRUCTURE ===");
        System.out.println();
        System.out.printf("%-25s", "Metric");
        for (ModelResult mr : allResults) {
            System.out.printf("  %-" + col + "s", mr.name);
        }
        System.out.println();
        System.out.println("-".repeat(25 + allResults.size() * (col + 2)));

        System.out.printf("%-25s", "Chunks indexed");
        for (ModelResult mr : allResults) {
            System.out.printf("  %-" + col + "d", mr.totalChunks);
        }
        System.out.println();

        System.out.printf("%-25s", "Avg embed (ms/chunk)");
        for (ModelResult mr : allResults) {
            System.out.printf("  %-" + col + ".1f", mr.avgEmbedMs);
        }
        System.out.println();

        System.out.printf("%-25s", "Model size (MB)");
        for (ModelResult mr : allResults) {
            System.out.printf("  %-" + col + "d", mr.modelSizeMB);
        }
        System.out.println();
        System.out.println();
    }

    private String truncate(String s, int max) {
        if (s == null) return "N/A";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    // --- Model download ---

    private Path ensureModelDownloaded(ModelConfig config) throws Exception {
        String dirName = config.repo().substring(config.repo().indexOf('/') + 1);
        Path modelDir = MODELS_DIR.resolve(dirName);
        Files.createDirectories(modelDir);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        for (String onnxFile : config.onnxFiles()) {
            String fileName = onnxFile.substring(onnxFile.lastIndexOf('/') + 1);
            Path target = modelDir.resolve(fileName);
            if (!Files.exists(target)) {
                String url = String.format(HF_RESOLVE, config.repo(), onnxFile);
                downloadFile(client, url, target);
            }
        }

        Path tokenizerTarget = modelDir.resolve("tokenizer.json");
        if (!Files.exists(tokenizerTarget)) {
            String url = String.format(HF_RESOLVE, config.repo(), config.tokenizerFile());
            downloadFile(client, url, tokenizerTarget);
        }

        return modelDir;
    }

    private void downloadFile(HttpClient client, String url, Path target) throws Exception {
        System.out.printf("  Downloading %s ...%n", url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Download failed: HTTP " + response.statusCode() + " for " + url);
        }

        try (InputStream is = response.body()) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }

        long sizeMB = Files.size(target) / (1024 * 1024);
        System.out.printf("  Downloaded %s (%d MB)%n", target.getFileName(), sizeMB);
    }

    private long dirSizeMB(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            long bytes = stream.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0; }
            }).sum();
            return bytes / (1024 * 1024);
        }
    }

    private boolean miniLMAvailable() {
        return getClass().getClassLoader().getResource("models/model_quantized.onnx") != null;
    }

    // --- File-based embedding provider for downloaded models ---

    static class FileBasedEmbeddingProvider implements EmbeddingProvider, AutoCloseable {

        private final int dimensions;
        private final int maxSeqLength;
        private final OrtSession session;
        private final OrtEnvironment env;
        private final HuggingFaceTokenizer tokenizer;
        private final boolean needsTokenTypeIds;

        FileBasedEmbeddingProvider(Path modelDir, String modelFile, int dimensions, int maxSeqLength)
                throws Exception {
            this.dimensions = dimensions;
            this.maxSeqLength = maxSeqLength;

            Path tokenizerPath = modelDir.resolve("tokenizer.json");
            try (InputStream is = Files.newInputStream(tokenizerPath)) {
                tokenizer = HuggingFaceTokenizer.newInstance(is, Map.of(
                        "padding", "false",
                        "truncation", "true",
                        "maxLength", String.valueOf(maxSeqLength)
                ));
            }

            env = OrtEnvironment.getEnvironment();
            session = env.createSession(modelDir.resolve(modelFile).toString());
            needsTokenTypeIds = session.getInputNames().contains("token_type_ids");

            System.out.printf("  Model loaded: %s (%d dims, token_type_ids=%s, inputs=%s)%n",
                    modelFile, dimensions, needsTokenTypeIds, session.getInputNames());
        }

        @Override
        public float[] embed(String text) {
            try {
                Encoding encoding = tokenizer.encode(text);
                long[] inputIds = encoding.getIds();
                long[] attentionMask = encoding.getAttentionMask();

                int seqLen = Math.min(inputIds.length, maxSeqLength);
                long[] truncIds = new long[seqLen];
                long[] truncMask = new long[seqLen];
                System.arraycopy(inputIds, 0, truncIds, 0, seqLen);
                System.arraycopy(attentionMask, 0, truncMask, 0, seqLen);

                long[] shape = {1, seqLen};
                OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(truncIds), shape);
                OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(truncMask), shape);

                Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
                inputs.put("input_ids", inputIdsTensor);
                inputs.put("attention_mask", attentionMaskTensor);

                OnnxTensor tokenTypeIdsTensor = null;
                if (needsTokenTypeIds) {
                    long[] tokenTypeIds = new long[seqLen];
                    tokenTypeIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), shape);
                    inputs.put("token_type_ids", tokenTypeIdsTensor);
                }

                try (OrtSession.Result result = session.run(inputs)) {
                    Object output = result.get(0).getValue();
                    float[] pooled;

                    if (output instanceof float[][][]) {
                        pooled = meanPool(((float[][][]) output)[0], truncMask);
                    } else if (output instanceof float[][]) {
                        pooled = ((float[][]) output)[0];
                    } else {
                        throw new RuntimeException("Unexpected ONNX output type: " + output.getClass());
                    }

                    return l2Normalize(pooled);
                } finally {
                    inputIdsTensor.close();
                    attentionMaskTensor.close();
                    if (tokenTypeIdsTensor != null) tokenTypeIdsTensor.close();
                }

            } catch (OrtException e) {
                throw new RuntimeException("ONNX inference failed", e);
            }
        }

        @Override
        public int dimensions() {
            return dimensions;
        }

        private float[] meanPool(float[][] tokenEmbeddings, long[] attentionMask) {
            int dims = tokenEmbeddings[0].length;
            float[] pooled = new float[dims];
            float maskSum = 0;
            for (int t = 0; t < tokenEmbeddings.length; t++) {
                float mask = attentionMask[t];
                maskSum += mask;
                for (int d = 0; d < dims; d++) {
                    pooled[d] += tokenEmbeddings[t][d] * mask;
                }
            }
            if (maskSum > 0) {
                for (int d = 0; d < dims; d++) {
                    pooled[d] /= maskSum;
                }
            }
            return pooled;
        }

        private float[] l2Normalize(float[] vector) {
            float norm = 0;
            for (float v : vector) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < vector.length; i++) vector[i] /= norm;
            }
            return vector;
        }

        @Override
        public void close() throws Exception {
            if (session != null) session.close();
        }
    }
}

package io.github.luigidemasi.camelkit.knowledge.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Embeds text using granite-embedding-small-english-r2 via ONNX Runtime.
 * Thread-safe. Model loaded lazily on first call.
 */
public class OnnxEmbeddingProvider implements EmbeddingProvider {

    private static final int DIMENSIONS = 384;
    private static final int MAX_SEQ_LENGTH = 512;
    private static final String MODEL_FILE = "models/model_quantized.onnx";
    private static final String MODEL_DATA_FILE = "models/model_quantized.onnx_data";
    private static final String TOKENIZER_FILE = "models/tokenizer.json";

    private volatile OrtSession session;
    private volatile OrtEnvironment env;
    private volatile HuggingFaceTokenizer tokenizer;
    private final Object lock = new Object();

    @Override
    public float[] embed(String text) {
        ensureInitialized();
        try {
            // Tokenize
            Encoding encoding = tokenizer.encode(text);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();

            // Truncate to max sequence length
            int seqLen = Math.min(inputIds.length, MAX_SEQ_LENGTH);
            long[] truncatedIds = new long[seqLen];
            long[] truncatedMask = new long[seqLen];
            System.arraycopy(inputIds, 0, truncatedIds, 0, seqLen);
            System.arraycopy(attentionMask, 0, truncatedMask, 0, seqLen);

            // Create ONNX tensors — shape [1, seqLen]
            long[] shape = {1, seqLen};
            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env,
                    LongBuffer.wrap(truncatedIds), shape);
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env,
                    LongBuffer.wrap(truncatedMask), shape);

            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);

            // Run inference
            try (OrtSession.Result result = session.run(inputs)) {
                Object output = result.get(0).getValue();
                float[] pooled;

                if (output instanceof float[][][]) {
                    // Token-level output [1, seqLen, dims] — mean pooling needed
                    pooled = meanPool(((float[][][]) output)[0], truncatedMask);
                } else if (output instanceof float[][]) {
                    // Already pooled [1, dims]
                    pooled = ((float[][]) output)[0];
                } else {
                    throw new RuntimeException("Unexpected ONNX output shape: " + output.getClass());
                }

                // L2 normalize
                return l2Normalize(pooled);
            } finally {
                inputIdsTensor.close();
                attentionMaskTensor.close();
            }

        } catch (OrtException e) {
            throw new RuntimeException("ONNX inference failed", e);
        }
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] meanPool(float[][] tokenEmbeddings, long[] attentionMask) {
        float[] pooled = new float[DIMENSIONS];
        float maskSum = 0;

        for (int t = 0; t < tokenEmbeddings.length; t++) {
            float mask = attentionMask[t];
            maskSum += mask;
            for (int d = 0; d < DIMENSIONS; d++) {
                pooled[d] += tokenEmbeddings[t][d] * mask;
            }
        }

        if (maskSum > 0) {
            for (int d = 0; d < DIMENSIONS; d++) {
                pooled[d] /= maskSum;
            }
        }
        return pooled;
    }

    private float[] l2Normalize(float[] vector) {
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    private void ensureInitialized() {
        if (session == null) {
            synchronized (lock) {
                if (session == null) {
                    try {
                        // Load tokenizer from classpath
                        try (InputStream is = getClass().getClassLoader()
                                .getResourceAsStream(TOKENIZER_FILE)) {
                            if (is == null) {
                                throw new RuntimeException("tokenizer.json not found on classpath");
                            }
                            tokenizer = HuggingFaceTokenizer.newInstance(is, Map.of(
                                    "padding", "false",
                                    "truncation", "true",
                                    "maxLength", String.valueOf(MAX_SEQ_LENGTH)
                            ));
                        }

                        // Extract ONNX model files to temp dir (external data format
                        // requires both files in the same directory for file-based loading)
                        env = OrtEnvironment.getEnvironment();
                        Path tempDir = Files.createTempDirectory("camel-kit-onnx-");
                        tempDir.toFile().deleteOnExit();

                        Path modelPath = extractClasspathResource(MODEL_FILE, tempDir, "model_quantized.onnx");
                        Path dataPath = extractClasspathResource(MODEL_DATA_FILE, tempDir, "model_quantized.onnx_data");
                        modelPath.toFile().deleteOnExit();
                        dataPath.toFile().deleteOnExit();

                        int threads = Integer.parseInt(
                                System.getProperty("onnx.threads",
                                        String.valueOf(Runtime.getRuntime().availableProcessors())));
                        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
                        opts.setIntraOpNumThreads(threads);
                        opts.setInterOpNumThreads(threads);
                        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                        session = env.createSession(modelPath.toString(), opts);

                        System.out.println("  ONNX embedding model loaded (granite-embedding-small-english-r2, "
                                + DIMENSIONS + " dims, Q8, " + threads + " threads)");
                    } catch (IOException | OrtException e) {
                        throw new RuntimeException("Failed to load ONNX embedding model", e);
                    }
                }
            }
        }
    }

    private Path extractClasspathResource(String resourcePath, Path targetDir, String fileName) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException(resourcePath + " not found on classpath");
            }
            Path target = targetDir.resolve(fileName);
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        }
    }
}

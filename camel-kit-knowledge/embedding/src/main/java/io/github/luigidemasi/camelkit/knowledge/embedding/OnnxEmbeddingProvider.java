package io.github.luigidemasi.camelkit.knowledge.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.LongBuffer;
import java.util.Map;

/**
 * Embeds text using all-MiniLM-L6-v2 via ONNX Runtime.
 * Thread-safe. Model loaded lazily on first call.
 */
public class OnnxEmbeddingProvider implements EmbeddingProvider {

    private static final int DIMENSIONS = 384;
    private static final int MAX_SEQ_LENGTH = 256;

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
            // token_type_ids: all zeros for single-sentence encoding
            long[] tokenTypeIds = new long[seqLen];
            OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(env,
                    LongBuffer.wrap(tokenTypeIds), shape);

            Map<String, OnnxTensor> inputs = Map.of(
                    "input_ids", inputIdsTensor,
                    "attention_mask", attentionMaskTensor,
                    "token_type_ids", tokenTypeIdsTensor
            );

            // Run inference
            try (OrtSession.Result result = session.run(inputs)) {
                // Output shape: [1, seqLen, 384] — token embeddings
                float[][][] tokenEmbeddings = (float[][][]) result.get(0).getValue();

                // Mean pooling with attention mask
                float[] pooled = meanPool(tokenEmbeddings[0], truncatedMask);

                // L2 normalize
                return l2Normalize(pooled);
            } finally {
                inputIdsTensor.close();
                attentionMaskTensor.close();
                tokenTypeIdsTensor.close();
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
                                .getResourceAsStream("models/tokenizer.json")) {
                            if (is == null) {
                                throw new RuntimeException("tokenizer.json not found on classpath");
                            }
                            tokenizer = HuggingFaceTokenizer.newInstance(is, Map.of(
                                    "padding", "false",
                                    "truncation", "true",
                                    "maxLength", String.valueOf(MAX_SEQ_LENGTH)
                            ));
                        }

                        // Load ONNX model from classpath
                        env = OrtEnvironment.getEnvironment();
                        try (InputStream is = getClass().getClassLoader()
                                .getResourceAsStream("models/model.onnx")) {
                            if (is == null) {
                                throw new RuntimeException("model.onnx not found on classpath");
                            }
                            // OrtSession needs a file path or byte array
                            byte[] modelBytes = is.readAllBytes();
                            session = env.createSession(modelBytes);
                        }

                        System.out.println("  ONNX embedding model loaded (all-MiniLM-L6-v2, " + DIMENSIONS + " dims)");
                    } catch (IOException | OrtException e) {
                        throw new RuntimeException("Failed to load ONNX embedding model", e);
                    }
                }
            }
        }
    }
}

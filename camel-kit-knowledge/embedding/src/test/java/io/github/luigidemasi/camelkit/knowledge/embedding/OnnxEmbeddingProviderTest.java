package io.github.luigidemasi.camelkit.knowledge.embedding;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OnnxEmbeddingProvider.
 * Requires model.onnx to be present — skipped if not downloaded.
 */
@EnabledIf("modelExists")
class OnnxEmbeddingProviderTest {

    private static OnnxEmbeddingProvider provider;

    static boolean modelExists() {
        return OnnxEmbeddingProviderTest.class.getClassLoader()
                .getResource("models/model_quantized.onnx") != null;
    }

    @BeforeAll
    static void setUp() {
        provider = new OnnxEmbeddingProvider();
    }

    @Test
    void embedReturns384Dimensions() {
        float[] embedding = provider.embed("Apache Camel migration guide");
        assertEquals(384, embedding.length);
        assertEquals(384, provider.dimensions());
    }

    @Test
    void embedProducesNonZeroVector() {
        float[] embedding = provider.embed("HTTP component renamed from http4 to http");
        boolean hasNonZero = false;
        for (float v : embedding) {
            if (v != 0.0f) {
                hasNonZero = true;
                break;
            }
        }
        assertTrue(hasNonZero, "Embedding should not be all zeros");
    }

    @Test
    void similarTextsHaveHighCosineSimilarity() {
        float[] a = provider.embed("migrate http4 component to http");
        float[] b = provider.embed("rename http4 to http in Camel 3");
        float[] c = provider.embed("how to cook pasta with tomato sauce");

        float simAB = cosineSimilarity(a, b);
        float simAC = cosineSimilarity(a, c);

        assertTrue(simAB > 0.5, "Similar texts should have high similarity, got: " + simAB);
        assertTrue(simAC < 0.4, "Unrelated texts should have low similarity, got: " + simAC);
        assertTrue(simAB > simAC, "Related texts should be more similar than unrelated");
    }

    @Test
    void embedIsIdempotent() {
        float[] a = provider.embed("test idempotency");
        float[] b = provider.embed("test idempotency");
        assertArrayEquals(a, b, "Same input should produce same output");
    }

    @Test
    void embedHandlesEmptyString() {
        float[] embedding = provider.embed("");
        assertEquals(384, embedding.length);
    }

    private static float cosineSimilarity(float[] a, float[] b) {
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }
}

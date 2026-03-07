package io.github.luigidemasi.camelkit.knowledge.embedding;

/**
 * Provides text-to-vector embeddings for semantic search.
 */
public interface EmbeddingProvider {

    /**
     * Embed a text string into a dense vector.
     *
     * @param text the text to embed
     * @return float array of embedding dimensions
     */
    float[] embed(String text);

    /**
     * @return the number of dimensions in the embedding vector
     */
    int dimensions();
}

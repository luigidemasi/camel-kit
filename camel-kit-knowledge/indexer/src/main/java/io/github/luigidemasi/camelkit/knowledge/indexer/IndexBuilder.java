package io.github.luigidemasi.camelkit.knowledge.indexer;

import io.github.luigidemasi.camelkit.knowledge.embedding.EmbeddingProvider;
import io.github.luigidemasi.camelkit.knowledge.indexer.domain.DocumentChunk;
import io.github.luigidemasi.camelkit.knowledge.indexer.domain.DocumentDomain;
import io.github.luigidemasi.camelkit.knowledge.schema.DomainMetadata;
import io.github.luigidemasi.camelkit.knowledge.schema.KnowledgeDocument;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds a Lucene index from a list of document domains.
 * Each domain contributes its chunks and metadata to a single shared index.
 */
public class IndexBuilder {

    private final EmbeddingProvider embeddingProvider;

    /** Creates an IndexBuilder without embedding support. */
    public IndexBuilder() {
        this(null);
    }

    /** Creates an IndexBuilder with optional embedding support. */
    public IndexBuilder(EmbeddingProvider embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    /**
     * Build the Lucene index at the given path from the provided domains.
     *
     * @param indexPath directory where the Lucene index will be written
     * @param domains   list of domains to index
     * @return total number of documents indexed
     */
    public int build(Path indexPath, List<DocumentDomain> domains) throws IOException {
        int totalDocs = 0;

        try (Directory dir = FSDirectory.open(indexPath);
             IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {

            for (DocumentDomain domain : domains) {
                DomainMetadata meta = domain.metadata();
                String metaJson = serializeMeta(meta);
                List<DocumentChunk> chunks = domain.buildChunks();

                int embeddedCount = 0;
                boolean metaWritten = false;
                for (DocumentChunk chunk : chunks) {
                    KnowledgeDocument doc = new KnowledgeDocument(chunk.id(), meta.domainId())
                            .source(chunk.source())
                            .docType(chunk.docType())
                            .sectionTitle(chunk.sectionTitle())
                            .content(chunk.content());

                    if (chunk.sourceVersion() != null) {
                        doc.sourceVersion(chunk.sourceVersion());
                    }
                    if (chunk.targetVersion() != null) {
                        doc.targetVersion(chunk.targetVersion());
                    }
                    if (chunk.component() != null) {
                        doc.component(chunk.component());
                    }

                    // Generate embedding if provider is available
                    if (embeddingProvider != null) {
                        String textToEmbed = chunk.sectionTitle() + " " + chunk.content();
                        float[] vector = embeddingProvider.embed(textToEmbed);
                        doc.embedding(vector);
                        embeddedCount++;
                    }

                    // Store domain metadata on the first document only
                    if (!metaWritten) {
                        doc.domainMeta(metaJson);
                        metaWritten = true;
                    }

                    writer.addDocument(doc.build());
                    totalDocs++;
                }

                System.out.printf("  Domain '%s': %d chunks indexed, %d embedded%n",
                        meta.domainId(), chunks.size(), embeddedCount);
            }

            writer.commit();
        }

        System.out.printf("Index built: %d total documents%n", totalDocs);
        return totalDocs;
    }

    private String serializeMeta(DomainMetadata meta) {
        // Simple JSON serialization without Jackson dependency
        return String.format(
            "{\"domainId\":\"%s\",\"toolName\":\"%s\",\"description\":\"%s\",\"hasComponentField\":%s,\"hasVersionFields\":%s}",
            escape(meta.domainId()),
            escape(meta.toolName()),
            escape(meta.description()),
            meta.hasComponentField(),
            meta.hasVersionFields()
        );
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

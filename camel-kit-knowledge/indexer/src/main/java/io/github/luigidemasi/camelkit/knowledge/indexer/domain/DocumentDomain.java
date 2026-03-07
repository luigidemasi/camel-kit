package io.github.luigidemasi.camelkit.knowledge.indexer.domain;

import io.github.luigidemasi.camelkit.knowledge.schema.DomainMetadata;

import java.io.IOException;
import java.util.List;

/**
 * Defines a document domain — a collection of docs from a specific knowledge area.
 * Each domain produces chunks that are indexed together and served via a dedicated MCP tool.
 *
 * Implement this interface to add a new knowledge domain to the indexer.
 */
public interface DocumentDomain {

    /**
     * Metadata describing this domain (tool name, description, field capabilities).
     * Stored in the Lucene index and used by the MCP server for dynamic tool registration.
     */
    DomainMetadata metadata();

    /**
     * Fetch, parse, and chunk all documents for this domain.
     * Called once during index build. May fetch docs from URLs or local files.
     *
     * @return list of document chunks ready for indexing
     * @throws IOException if fetching or parsing fails
     */
    List<DocumentChunk> buildChunks() throws IOException;
}

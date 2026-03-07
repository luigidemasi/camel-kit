package io.github.luigidemasi.camelkit.knowledge.indexer.domain;

/**
 * A single chunk of documentation, ready for indexing.
 * Produced by parsers/chunkers, consumed by the IndexBuilder.
 */
public record DocumentChunk(
    String id,              // unique chunk ID
    String source,          // e.g., "apache-camel", "red-hat-fuse", "openrewrite"
    String docType,         // e.g., "component-migration", "platform-change", "recipe"
    String sourceVersion,   // e.g., "2.x" (nullable)
    String targetVersion,   // e.g., "4.x" (nullable)
    String component,       // exact component name for lookup (nullable)
    String sectionTitle,    // section heading
    String content          // chunk text content
) {}

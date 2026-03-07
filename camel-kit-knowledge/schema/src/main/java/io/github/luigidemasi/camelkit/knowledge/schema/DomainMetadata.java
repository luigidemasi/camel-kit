package io.github.luigidemasi.camelkit.knowledge.schema;

/**
 * Metadata describing a document domain. Stored as JSON in the Lucene index
 * and read by the MCP server to dynamically register tools.
 */
public record DomainMetadata(
    String domainId,        // e.g., "rh_fuse_migration"
    String toolName,        // e.g., "camel_rh_fuse_migration"
    String description,     // tool description for LLM
    boolean hasComponentField,  // whether docs have exact-match component field
    boolean hasVersionFields    // whether docs have source_version/target_version
) {
    /**
     * Create metadata for a migration domain (has component, version fields).
     */
    public static DomainMetadata migration(String domainId, String toolName, String description) {
        return new DomainMetadata(domainId, toolName, description, true, true);
    }

    /**
     * Create metadata for a general documentation domain (no component field).
     */
    public static DomainMetadata general(String domainId, String toolName, String description) {
        return new DomainMetadata(domainId, toolName, description, false, false);
    }
}

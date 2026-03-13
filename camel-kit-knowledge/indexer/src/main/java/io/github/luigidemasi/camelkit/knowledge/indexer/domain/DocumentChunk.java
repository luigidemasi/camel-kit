package io.github.luigidemasi.camelkit.knowledge.indexer.domain;

import java.util.List;

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
    String content,         // chunk text content
    // Runtime (multi-valued: "quarkus", "spring-boot", "main")
    List<String> runtimes,
    // JIRA issue IDs referenced in this chunk (multi-valued, nullable)
    List<String> jiraIds,
    // Errata-specific fields (all nullable for non-errata chunks)
    String erratumId,       // e.g., "RHSA-2026:0467"
    String advisoryType,    // e.g., "Security Advisory", "Bug Fix", "Enhancement"
    String severity,        // e.g., "Critical", "Important", "Moderate", "Low"
    List<String> cveIds,    // CVE identifiers (multi-valued)
    List<String> fixedInVersions // product versions where this erratum applies (multi-valued)
) {
    /** Convenience constructor for non-errata chunks (backwards compatible). */
    public DocumentChunk(String id, String source, String docType,
                         String sourceVersion, String targetVersion, String component,
                         String sectionTitle, String content) {
        this(id, source, docType, sourceVersion, targetVersion, component,
             sectionTitle, content, null, null, null, null, null, null, null);
    }
}

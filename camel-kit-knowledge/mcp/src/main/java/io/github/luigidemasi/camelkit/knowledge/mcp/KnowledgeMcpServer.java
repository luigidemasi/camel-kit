package io.github.luigidemasi.camelkit.knowledge.mcp;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP server that exposes knowledge search tools.
 *
 * Each domain in the index gets two tool patterns:
 * - lookup: exact component name match
 * - search: full-text search across domain docs
 *
 * TODO: Replace static tools with dynamic registration based on index metadata
 * once the Quarkus MCP programmatic tool registration API is available.
 */
public class KnowledgeMcpServer {

    @Inject
    LuceneSearchService searchService;

    @Tool(description = "Look up migration documentation for a specific Apache Camel component. " +
            "Returns detailed migration context including behavioral changes, option renames, " +
            "OpenRewrite recipes, and gotchas. " +
            "Call this for EVERY component during Camel migration.")
    public String camel_migration_lookup(
            @ToolArg(description = "Component name to look up, e.g., 'http4', 'camel-cxf', 'netty4'") String component,
            @ToolArg(description = "Source Camel version, e.g., '2.x' or '3.x'", required = false) String source_version
    ) {
        try {
            List<LuceneSearchService.SearchResult> results =
                    searchService.lookupComponent("camel_migration", component, source_version);

            if (results.isEmpty()) {
                return "{\"found\":false,\"results\":[]}";
            }

            return formatResults(results);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Search Apache Camel migration documentation and OpenRewrite recipes by keyword. " +
            "Use when camel_migration_lookup returns no results, or for broader queries " +
            "like 'Blueprint XML migration', 'javax to jakarta', or 'ChangeType http4'.")
    public String camel_migration_search(
            @ToolArg(description = "Search query, e.g., 'Blueprint XML property placeholder migration'") String query,
            @ToolArg(description = "Source version filter, e.g., '2.x'", required = false) String source_version,
            @ToolArg(description = "Target version filter, e.g., '4.x'", required = false) String target_version,
            @ToolArg(description = "Maximum results to return (default 5)", required = false) String max_results
    ) {
        try {
            int maxResults = max_results != null ? Integer.parseInt(max_results) : 5;
            List<LuceneSearchService.SearchResult> results =
                    searchService.search("camel_migration", query, source_version, target_version, maxResults);

            return formatResults(results);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Look up Red Hat Build of Apache Camel documentation for a specific component. " +
            "Returns support status, configuration reference, and known issues. " +
            "Use this to check if a component is supported by Red Hat.")
    public String camel_rh_build_component_info(
            @ToolArg(description = "Component name, e.g., 'camel-kafka', 'camel-amqp', 'kafka'") String component,
            @ToolArg(description = "Product version, e.g., '4.14'. Optional — omit for all versions.", required = false) String version
    ) {
        try {
            List<LuceneSearchService.SearchResult> results =
                    searchService.lookupComponent("rh_build_camel", component, version);

            if (results.isEmpty()) {
                return "{\"found\":false,\"results\":[]}";
            }

            return formatResults(results);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Search Red Hat Build of Apache Camel documentation by keyword. " +
            "Use for general questions about supported configurations, release notes, " +
            "migration guides, or any Red Hat-specific Camel information.")
    public String camel_rh_build_search(
            @ToolArg(description = "Search query, e.g., 'supported databases PostgreSQL'") String query,
            @ToolArg(description = "Product version filter, e.g., '4.14'. Optional.", required = false) String version,
            @ToolArg(description = "Maximum results to return (default 5)", required = false) String max_results
    ) {
        try {
            int maxResults = max_results != null ? Integer.parseInt(max_results) : 5;
            List<LuceneSearchService.SearchResult> results =
                    searchService.search("rh_build_camel", query, version, null, maxResults);

            return formatResults(results);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // Future: Add camel_rh_fuse_migration_lookup, camel_rh_fuse_migration_search, etc.
    // These follow the exact same pattern with different domain IDs.

    private String formatResults(List<LuceneSearchService.SearchResult> results) {
        String items = results.stream()
                .map(r -> String.format(
                        "{\"id\":\"%s\",\"source\":\"%s\",\"doc_type\":\"%s\"," +
                        "\"source_version\":\"%s\",\"target_version\":\"%s\"," +
                        "\"section_title\":\"%s\",\"content\":\"%s\",\"score\":%.2f}",
                        escape(r.id()), escape(r.source()), escape(r.docType()),
                        escape(r.sourceVersion()), escape(r.targetVersion()),
                        escape(r.sectionTitle()), escape(r.content()), r.score()
                ))
                .collect(Collectors.joining(","));

        return "{\"found\":true,\"total_hits\":" + results.size() + ",\"results\":[" + items + "]}";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}

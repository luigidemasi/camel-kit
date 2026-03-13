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
            @ToolArg(description = "Maximum results to return (default 5)", required = false) Integer max_results
    ) {
        try {
            int maxResults = max_results != null ? max_results : 5;
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
            @ToolArg(description = "Maximum results to return (default 5)", required = false) Integer max_results
    ) {
        try {
            int maxResults = max_results != null ? max_results : 5;
            List<LuceneSearchService.SearchResult> results =
                    searchService.search("rh_build_camel", query, version, null, maxResults);

            return formatResults(results);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Look up a JIRA issue to find in which Red Hat Build of Apache Camel release " +
            "it was fixed or implemented. Accepts JIRA issue IDs like CEQ-12480, CSB-8351, RHBAC-127, " +
            "CAMEL-22784, or ENTESB-*. Returns the release version, runtime, description, and context.")
    public String camel_rh_build_jira_lookup(
            @ToolArg(description = "JIRA issue ID, e.g., 'CSB-8351', 'CEQ-12480', 'CAMEL-22784', 'RHBAC-127'") String jira_id
    ) {
        try {
            List<LuceneSearchService.SearchResult> results =
                    searchService.searchByJiraId(jira_id.toUpperCase());

            if (results.isEmpty()) {
                return "{\"found\":false,\"jira_id\":\"" + escape(jira_id) + "\",\"results\":[]}";
            }

            return formatResults(results);
        } catch (Exception e) {
            return "{\"error\":\"" + escape(e.getMessage()) + "\"}";
        }
    }

    @Tool(description = "Search Red Hat errata by CVE ID. " +
            "Returns all errata that address a specific CVE, with severity, advisory type, " +
            "and affected product versions. Use for questions like 'is CVE-2021-44228 fixed?'")
    public String camel_rh_build_cve_search(
            @ToolArg(description = "CVE identifier, e.g., 'CVE-2021-44228'") String cve_id
    ) {
        try {
            List<LuceneSearchService.ErrataSearchResult> results =
                    searchService.searchByCve(cve_id);

            if (results.isEmpty()) {
                return "{\"found\":false,\"results\":[]}";
            }

            return formatErrataResults(results);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Search Red Hat errata by type, severity, and/or version. " +
            "Use for questions like 'all Critical security fixes in Camel 4.14' " +
            "or 'Bug Fix advisories for version 4.8'.")
    public String camel_rh_build_bugfix_search(
            @ToolArg(description = "Advisory type: 'Security Advisory', 'Bug Fix', or 'Enhancement'", required = false) String advisory_type,
            @ToolArg(description = "Severity: 'Critical', 'Important', 'Moderate', or 'Low'", required = false) String severity,
            @ToolArg(description = "Product version, e.g., '4.14'", required = false) String version,
            @ToolArg(description = "Optional free-text search within errata content", required = false) String query,
            @ToolArg(description = "Maximum results to return (default 10)", required = false) Integer max_results
    ) {
        try {
            int maxResults = max_results != null ? max_results : 10;
            List<LuceneSearchService.ErrataSearchResult> results =
                    searchService.searchErrata(advisory_type, severity, version, query, maxResults);

            return formatErrataResults(results);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Get all errata (security fixes, bug fixes, enhancements) for a specific " +
            "Red Hat Build of Apache Camel release version. " +
            "Use for questions like 'what was fixed in Camel 4.14?' or 'release notes for 4.8'.")
    public String camel_rh_build_release_info(
            @ToolArg(description = "Product version, e.g., '4.14', '4.8', '7.12'") String version,
            @ToolArg(description = "Optional advisory type filter: 'Security Advisory', 'Bug Fix', or 'Enhancement'", required = false) String advisory_type,
            @ToolArg(description = "Maximum results to return (default 20)", required = false) Integer max_results
    ) {
        try {
            int maxResults = max_results != null ? max_results : 20;
            List<LuceneSearchService.ErrataSearchResult> results =
                    searchService.searchByVersion(version, advisory_type, maxResults);

            return formatErrataResults(results);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "Search Red Hat Build of Apache Camel supported configurations — " +
            "platforms, databases, JDKs, operating systems, and middleware versions. " +
            "Use for questions like 'is PostgreSQL 16 supported with Camel 4.14?' " +
            "or 'which JDK versions work with Camel 4.8?'")
    public String camel_rh_build_supported_configs(
            @ToolArg(description = "Search query, e.g., 'PostgreSQL 16 supported', 'JDK 21'") String query,
            @ToolArg(description = "Product version filter, e.g., '4.14'. Optional.", required = false) String version,
            @ToolArg(description = "Maximum results to return (default 5)", required = false) Integer max_results
    ) {
        try {
            int maxResults = max_results != null ? max_results : 5;
            List<LuceneSearchService.SearchResult> results =
                    searchService.search("rh_build_camel", query, version, null, maxResults);

            return formatResults(results);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String formatResults(List<LuceneSearchService.SearchResult> results) {
        String items = results.stream()
                .map(r -> {
                    String runtimeArray = r.runtimes().stream()
                            .map(rt -> "\"" + escape(rt) + "\"")
                            .collect(Collectors.joining(","));
                    return String.format(
                        "{\"id\":\"%s\",\"source\":\"%s\",\"doc_type\":\"%s\"," +
                        "\"source_version\":\"%s\",\"target_version\":\"%s\"," +
                        "\"runtimes\":[%s]," +
                        "\"section_title\":\"%s\",\"content\":\"%s\",\"score\":%.2f}",
                        escape(r.id()), escape(r.source()), escape(r.docType()),
                        escape(r.sourceVersion()), escape(r.targetVersion()),
                        runtimeArray,
                        escape(r.sectionTitle()), escape(r.content()), r.score()
                    );
                })
                .collect(Collectors.joining(","));

        return "{\"found\":true,\"total_hits\":" + results.size() + ",\"results\":[" + items + "]}";
    }

    private String formatErrataResults(List<LuceneSearchService.ErrataSearchResult> results) {
        String items = results.stream()
                .map(r -> {
                    String cveArray = r.cveIds().stream()
                            .map(c -> "\"" + escape(c) + "\"")
                            .collect(Collectors.joining(","));
                    String versionArray = r.fixedInVersions().stream()
                            .map(v -> "\"" + escape(v) + "\"")
                            .collect(Collectors.joining(","));
                    return String.format(
                            "{\"erratum_id\":\"%s\",\"advisory_type\":\"%s\"," +
                            "\"severity\":\"%s\",\"section_title\":\"%s\"," +
                            "\"cve_ids\":[%s],\"fixed_in_versions\":[%s]," +
                            "\"content\":\"%s\",\"score\":%.2f}",
                            escape(r.erratumId()), escape(r.advisoryType()),
                            escape(r.severity()), escape(r.sectionTitle()),
                            cveArray, versionArray,
                            escape(r.content()), r.score()
                    );
                })
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

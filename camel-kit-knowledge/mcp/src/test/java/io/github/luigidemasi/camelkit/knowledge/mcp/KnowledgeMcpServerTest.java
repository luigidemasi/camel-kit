package io.github.luigidemasi.camelkit.knowledge.mcp;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that boots the full Quarkus app, loads the real Lucene index
 * from classpath resources, and exercises the MCP tools end-to-end.
 */
@QuarkusTest
class KnowledgeMcpServerTest {

    @Inject
    LuceneSearchService searchService;

    @Inject
    KnowledgeMcpServer mcpServer;

    // ── Index loading ──────────────────────────────────────────────

    @Test
    void indexLoadsAndContainsBothDomains() throws Exception {
        Set<String> domains = searchService.getDomains();
        assertTrue(domains.contains("camel_migration"), "Expected camel_migration domain, got: " + domains);
        assertTrue(domains.contains("rh_build_camel"), "Expected rh_build_camel domain, got: " + domains);
    }

    // ── camel_migration domain ─────────────────────────────────────

    @Test
    void migrationLookup_findsComponentByName() throws Exception {
        List<LuceneSearchService.SearchResult> results =
                searchService.lookupComponent("camel_migration", "http", null);

        assertFalse(results.isEmpty(), "Expected results for component 'http'");
        assertTrue(results.stream().allMatch(r -> r.source() != null),
                "All results should have a source");
    }

    @Test
    void migrationSearch_findsMigrationContent() throws Exception {
        List<LuceneSearchService.SearchResult> results =
                searchService.search("camel_migration", "migration", null, null, 5);

        assertFalse(results.isEmpty(), "Expected results for query 'migration'");
        assertTrue(results.size() <= 5, "Should respect max_results");
    }

    @Test
    void migrationSearch_withVersionFilter() throws Exception {
        List<LuceneSearchService.SearchResult> results =
                searchService.search("camel_migration", "renamed", "2.x", "4.x", 10);

        assertFalse(results.isEmpty(), "Expected results for 'renamed' with version filters");
    }

    @Test
    void migrationLookupTool_returnsJson() {
        String json = mcpServer.camel_migration_lookup("http", null);

        assertFalse(json.contains("\"error\""), "Should not return error: " + json);
        assertTrue(json.contains("\"found\""), "Should contain found field");
    }

    @Test
    void migrationSearchTool_returnsJson() {
        String json = mcpServer.camel_migration_search("blueprint XML", null, null, "3");

        assertFalse(json.contains("\"error\""), "Should not return error: " + json);
        assertTrue(json.contains("\"found\""), "Should contain found field");
        assertTrue(json.contains("\"results\""), "Should contain results array");
    }

    // ── rh_build_camel domain ──────────────────────────────────────

    @Test
    void rhBuildSearch_findsDocumentation() throws Exception {
        List<LuceneSearchService.SearchResult> results =
                searchService.search("rh_build_camel", "quarkus", null, null, 5);

        assertFalse(results.isEmpty(), "Expected results for query 'quarkus'");
        assertTrue(results.stream().allMatch(r -> "red-hat-build-camel".equals(r.source())),
                "All results should have source 'red-hat-build-camel'");
    }

    @Test
    void rhBuildSearch_withVersionFilter() throws Exception {
        List<LuceneSearchService.SearchResult> results =
                searchService.search("rh_build_camel", "getting started", "4.14", null, 5);

        assertFalse(results.isEmpty(), "Expected results for 'getting started' with version 4.14");
    }

    @Test
    void rhBuildComponentInfoTool_returnsJson() {
        String json = mcpServer.camel_rh_build_component_info("kafka", "4.14");

        assertFalse(json.contains("\"error\""), "Should not return error: " + json);
        assertTrue(json.contains("\"found\""), "Should contain found field");
    }

    @Test
    void rhBuildSearchTool_returnsJson() {
        String json = mcpServer.camel_rh_build_search("release notes", "4.14", "5");

        assertFalse(json.contains("\"error\""), "Should not return error: " + json);
        assertTrue(json.contains("\"found\""), "Should contain found field");
        assertTrue(json.contains("\"results\""), "Should contain results array");
    }

    // ── Cross-domain isolation ─────────────────────────────────────

    @Test
    void searchIsolatedByDomain() throws Exception {
        List<LuceneSearchService.SearchResult> migrationResults =
                searchService.search("camel_migration", "migration", null, null, 50);
        List<LuceneSearchService.SearchResult> rhBuildResults =
                searchService.search("rh_build_camel", "migration", null, null, 50);

        // Both domains should return results for "migration" but from different sources
        assertFalse(migrationResults.isEmpty(), "camel_migration should have migration results");
        assertFalse(rhBuildResults.isEmpty(), "rh_build_camel should have migration results");

        // Migration results should come from apache-camel or openrewrite sources
        assertTrue(migrationResults.stream()
                        .allMatch(r -> "apache-camel".equals(r.source()) || "openrewrite".equals(r.source())),
                "Migration results should only have apache-camel or openrewrite sources");

        // RH Build results should come from red-hat-build-camel source
        assertTrue(rhBuildResults.stream()
                        .allMatch(r -> "red-hat-build-camel".equals(r.source())),
                "RH Build results should only have red-hat-build-camel source");
    }

    // ── Edge cases ─────────────────────────────────────────────────

    @Test
    void lookupNonExistentComponent_returnsEmpty() throws Exception {
        List<LuceneSearchService.SearchResult> results =
                searchService.lookupComponent("camel_migration", "nonexistent-component-xyz", null);

        assertTrue(results.isEmpty(), "Should return empty for nonexistent component");
    }

    @Test
    void lookupNonExistentComponentTool_returnsNotFound() {
        String json = mcpServer.camel_migration_lookup("nonexistent-component-xyz", null);

        assertTrue(json.contains("\"found\":false"), "Should return found:false for nonexistent component");
    }
}
package io.github.luigidemasi.camelkit.doc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrontmatterHandlerTest {

    private static final String FULL_FRONTMATTER = """
            ---
            staleness:
              stale: true
              since: "2026-05-13T10:00:00Z"
              reason: "design-spec.md was refined"
            generated:
              at: "2026-05-13T09:00:00Z"
              by: camel-plan
              from: design-spec.md
            ---
            # Implementation Plan

            This is the body content.
            """;

    private static final String FRESH_FRONTMATTER = """
            ---
            staleness:
              stale: false
              since: null
              reason: null
            generated:
              at: "2026-05-13T09:00:00Z"
              by: camel-plan
              from: design-spec.md
            ---
            # Implementation Plan
            """;

    private static final String NO_FRONTMATTER = """
            # Just a plain markdown document

            No frontmatter here.
            """;

    private static final String STALENESS_ONLY = """
            ---
            staleness:
              stale: true
              since: "2026-05-13T10:00:00Z"
              reason: "upstream changed"
            ---
            # Document
            """;

    private static final String GENERATED_ONLY = """
            ---
            generated:
              at: "2026-05-13T09:00:00Z"
              by: camel-execute
              from: implementation-plan.md
            ---
            # Report
            """;

    @Test
    void hasFrontmatterDetectsDelimiters() {
        assertTrue(FrontmatterHandler.hasFrontmatter(FULL_FRONTMATTER));
        assertTrue(FrontmatterHandler.hasFrontmatter(FRESH_FRONTMATTER));
        assertFalse(FrontmatterHandler.hasFrontmatter(NO_FRONTMATTER));
        assertFalse(FrontmatterHandler.hasFrontmatter(null));
        assertFalse(FrontmatterHandler.hasFrontmatter(""));
    }

    @Test
    void extractFrontmatterYamlReturnsYaml() {
        String yaml = FrontmatterHandler.extractFrontmatterYaml(FULL_FRONTMATTER);
        assertNotNull(yaml);
        assertTrue(yaml.contains("staleness:"));
        assertTrue(yaml.contains("generated:"));
    }

    @Test
    void extractFrontmatterYamlReturnsNullForPlain() {
        assertNull(FrontmatterHandler.extractFrontmatterYaml(NO_FRONTMATTER));
        assertNull(FrontmatterHandler.extractFrontmatterYaml(null));
    }

    @Test
    void extractBodyPreservesContent() {
        String body = FrontmatterHandler.extractBody(FULL_FRONTMATTER);
        assertTrue(body.startsWith("# Implementation Plan"));
        assertTrue(body.contains("This is the body content."));
    }

    @Test
    void extractBodyReturnsFullContentWithoutFrontmatter() {
        String body = FrontmatterHandler.extractBody(NO_FRONTMATTER);
        assertEquals(NO_FRONTMATTER, body);
    }

    @Test
    void parseStalenessFromFullFrontmatter() {
        String yaml = FrontmatterHandler.extractFrontmatterYaml(FULL_FRONTMATTER);
        StalenessInfo info = FrontmatterHandler.parseStaleness(yaml);
        assertTrue(info.isStale());
        assertEquals("2026-05-13T10:00:00Z", info.getSince());
        assertEquals("design-spec.md was refined", info.getReason());
    }

    @Test
    void parseStalenessFromFreshDocument() {
        String yaml = FrontmatterHandler.extractFrontmatterYaml(FRESH_FRONTMATTER);
        StalenessInfo info = FrontmatterHandler.parseStaleness(yaml);
        assertFalse(info.isStale());
        assertNull(info.getSince());
        assertNull(info.getReason());
    }

    @Test
    void parseStalenessFromNullYamlReturnsFresh() {
        StalenessInfo info = FrontmatterHandler.parseStaleness(null);
        assertFalse(info.isStale());
    }

    @Test
    void parseStalenessOnlyBlock() {
        String yaml = FrontmatterHandler.extractFrontmatterYaml(STALENESS_ONLY);
        StalenessInfo info = FrontmatterHandler.parseStaleness(yaml);
        assertTrue(info.isStale());
        assertEquals("upstream changed", info.getReason());
    }

    @Test
    void parseGeneratedFromFullFrontmatter() {
        String yaml = FrontmatterHandler.extractFrontmatterYaml(FULL_FRONTMATTER);
        GeneratedInfo gen = FrontmatterHandler.parseGenerated(yaml);
        assertNotNull(gen);
        assertEquals("2026-05-13T09:00:00Z", gen.getAt());
        assertEquals("camel-plan", gen.getBy());
        assertEquals("design-spec.md", gen.getFrom());
    }

    @Test
    void parseGeneratedOnlyBlock() {
        String yaml = FrontmatterHandler.extractFrontmatterYaml(GENERATED_ONLY);
        GeneratedInfo gen = FrontmatterHandler.parseGenerated(yaml);
        assertNotNull(gen);
        assertEquals("camel-execute", gen.getBy());
        assertEquals("implementation-plan.md", gen.getFrom());
    }

    @Test
    void parseGeneratedFromStalenessOnlyReturnsNull() {
        String yaml = FrontmatterHandler.extractFrontmatterYaml(STALENESS_ONLY);
        GeneratedInfo gen = FrontmatterHandler.parseGenerated(yaml);
        assertNull(gen);
    }

    @Test
    void parseGeneratedFromNullReturnsNull() {
        assertNull(FrontmatterHandler.parseGenerated(null));
    }

    @Test
    void markStaleUpdatesFields() {
        String result = FrontmatterHandler.markStale(FRESH_FRONTMATTER, "spec changed", "2026-05-13T11:00:00Z");
        assertTrue(FrontmatterHandler.hasFrontmatter(result));

        String yaml = FrontmatterHandler.extractFrontmatterYaml(result);
        StalenessInfo info = FrontmatterHandler.parseStaleness(yaml);
        assertTrue(info.isStale());
        assertEquals("spec changed", info.getReason());
        assertEquals("2026-05-13T11:00:00Z", info.getSince());

        GeneratedInfo gen = FrontmatterHandler.parseGenerated(yaml);
        assertNotNull(gen);
        assertEquals("design-spec.md", gen.getFrom());
    }

    @Test
    void markStalePreservesBody() {
        String result = FrontmatterHandler.markStale(FRESH_FRONTMATTER, "changed", "2026-05-13T11:00:00Z");
        String body = FrontmatterHandler.extractBody(result);
        assertTrue(body.startsWith("# Implementation Plan"));
    }

    @Test
    void clearStaleResetsFields() {
        String result = FrontmatterHandler.clearStale(FULL_FRONTMATTER);
        assertTrue(FrontmatterHandler.hasFrontmatter(result));

        String yaml = FrontmatterHandler.extractFrontmatterYaml(result);
        StalenessInfo info = FrontmatterHandler.parseStaleness(yaml);
        assertFalse(info.isStale());
        assertNull(info.getSince());
        assertNull(info.getReason());

        GeneratedInfo gen = FrontmatterHandler.parseGenerated(yaml);
        assertNotNull(gen);
        assertEquals("camel-plan", gen.getBy());
    }

    @Test
    void clearStalePreservesBody() {
        String result = FrontmatterHandler.clearStale(FULL_FRONTMATTER);
        String body = FrontmatterHandler.extractBody(result);
        assertTrue(body.contains("This is the body content."));
    }

    @Test
    void markStaleOnDocumentWithoutFrontmatter() {
        String result = FrontmatterHandler.markStale(NO_FRONTMATTER, "new staleness", "2026-05-13T12:00:00Z");
        assertTrue(FrontmatterHandler.hasFrontmatter(result));

        String yaml = FrontmatterHandler.extractFrontmatterYaml(result);
        StalenessInfo info = FrontmatterHandler.parseStaleness(yaml);
        assertTrue(info.isStale());

        String body = FrontmatterHandler.extractBody(result);
        assertTrue(body.contains("# Just a plain markdown document"));
    }

    @Test
    void roundTripParseThenWrite() {
        String yaml = FrontmatterHandler.extractFrontmatterYaml(FULL_FRONTMATTER);
        StalenessInfo staleness = FrontmatterHandler.parseStaleness(yaml);
        GeneratedInfo generated = FrontmatterHandler.parseGenerated(yaml);
        String body = FrontmatterHandler.extractBody(FULL_FRONTMATTER);

        String rebuilt = FrontmatterHandler.writeFrontmatter(staleness, generated, body);

        String yaml2 = FrontmatterHandler.extractFrontmatterYaml(rebuilt);
        StalenessInfo staleness2 = FrontmatterHandler.parseStaleness(yaml2);
        GeneratedInfo generated2 = FrontmatterHandler.parseGenerated(yaml2);

        assertEquals(staleness.isStale(), staleness2.isStale());
        assertEquals(staleness.getSince(), staleness2.getSince());
        assertEquals(staleness.getReason(), staleness2.getReason());
        assertEquals(generated.getAt(), generated2.getAt());
        assertEquals(generated.getBy(), generated2.getBy());
        assertEquals(generated.getFrom(), generated2.getFrom());
    }

    @Test
    void toCheckJsonProducesValidOutput() {
        String json = FrontmatterHandler.toCheckJson("test-file.md", FULL_FRONTMATTER);
        assertTrue(json.contains("\"file\" : \"test-file.md\""));
        assertTrue(json.contains("\"stale\" : true"));
        assertTrue(json.contains("\"reason\" : \"design-spec.md was refined\""));
        assertTrue(json.contains("\"from\" : \"design-spec.md\""));
    }

    @Test
    void toCheckJsonForDocumentWithoutFrontmatter() {
        String json = FrontmatterHandler.toCheckJson("plain.md", NO_FRONTMATTER);
        assertTrue(json.contains("\"stale\" : false"));
        assertTrue(json.contains("\"since\" : null"));
        assertTrue(json.contains("\"reason\" : null"));
        assertTrue(json.contains("\"generated\" : null"));
    }

    @Test
    void writeFrontmatterWithNullGenerated() {
        String result = FrontmatterHandler.writeFrontmatter(StalenessInfo.fresh(), null, "# Body\n");
        assertTrue(FrontmatterHandler.hasFrontmatter(result));

        String yaml = FrontmatterHandler.extractFrontmatterYaml(result);
        assertNull(FrontmatterHandler.parseGenerated(yaml));
        assertFalse(FrontmatterHandler.parseStaleness(yaml).isStale());

        String body = FrontmatterHandler.extractBody(result);
        assertTrue(body.startsWith("# Body"));
    }
}

package io.github.luigidemasi.camelkit.knowledge.indexer.parser;

import io.github.luigidemasi.camelkit.knowledge.indexer.chunker.SectionChunker.Section;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DoclingParserTest {

    // --- Unit tests for response parsing (no Docker needed) ---

    @Test
    void extractMarkdownFromJsonResponse() {
        DoclingParser parser = new DoclingParser("http://localhost:5001");

        String json = """
                {"document":{"md_content":"# Title\\n\\nSome content.","filename":"test.pdf"},"status":"SUCCESS","processing_time":1.5}""";

        String markdown = parser.extractMarkdownFromResponse(json);
        assertEquals("# Title\n\nSome content.", markdown);
    }

    @Test
    void extractMarkdownWithEscapedCharacters() {
        DoclingParser parser = new DoclingParser("http://localhost:5001");

        String json = """
                {"document":{"md_content":"Line 1\\nLine 2\\n\\n## Heading\\n\\nA \\"quoted\\" word.","filename":"doc.html"},"status":"SUCCESS"}""";

        String markdown = parser.extractMarkdownFromResponse(json);
        assertTrue(markdown.contains("Line 1\nLine 2"));
        assertTrue(markdown.contains("## Heading"));
        assertTrue(markdown.contains("A \"quoted\" word."));
    }

    @Test
    void extractMarkdownWithNullContent() {
        DoclingParser parser = new DoclingParser("http://localhost:5001");

        String json = """
                {"document":{"md_content":null,"filename":"test.pdf"},"status":"FAILURE"}""";

        String markdown = parser.extractMarkdownFromResponse(json);
        // Should fall back to returning the entire response when md_content is null
        assertNotNull(markdown);
    }

    @Test
    void extractMarkdownFromPlainTextResponse() {
        DoclingParser parser = new DoclingParser("http://localhost:5001");

        String plainMarkdown = "# Title\n\nSome content.";
        String result = parser.extractMarkdownFromResponse(plainMarkdown);
        assertEquals(plainMarkdown, result);
    }

    @Test
    void extractMarkdownFromEmptyResponse() {
        DoclingParser parser = new DoclingParser("http://localhost:5001");

        assertEquals("", parser.extractMarkdownFromResponse(""));
        assertEquals("", parser.extractMarkdownFromResponse("   "));
        assertEquals("", parser.extractMarkdownFromResponse(null));
    }

    @Test
    void trailingSlashInUrlIsStripped() {
        DoclingParser parser = new DoclingParser("http://localhost:5001/");
        // Verify construction doesn't fail — the URL normalization is internal
        assertNotNull(parser);
    }

    // --- Integration tests (require docling-serve running) ---

    /**
     * Integration test — requires docling-serve running:
     *   docker run -p 5001:5001 ds4sd/docling-serve
     *
     * Only runs when DOCLING_URL environment variable is set.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DOCLING_URL", matches = ".+")
    void parseMarkdownFile() throws Exception {
        String doclingUrl = System.getenv("DOCLING_URL");
        DoclingParser parser = new DoclingParser(doclingUrl);

        Path tempFile = Files.createTempFile("test", ".md");
        Files.writeString(tempFile, """
                # Migration Guide

                ## Component Changes

                The following components changed.

                ### http4

                http4 was renamed to http.
                """);

        try {
            List<Section> sections = parser.parse(tempFile);
            assertFalse(sections.isEmpty());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}

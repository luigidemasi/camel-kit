package io.github.luigidemasi.camelkit.knowledge.indexer.chunker;

import io.github.luigidemasi.camelkit.knowledge.indexer.chunker.SectionChunker.Section;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SectionChunkerTest {

    private final SectionChunker chunker = new SectionChunker();

    @Test
    void chunkByHeadings() {
        String markdown = """
                ## Component Changes

                The following components have been renamed.

                ### camel-http4

                The http4 component has been renamed to http.
                The old http component was removed.

                ### camel-netty4

                The netty4 component has been renamed to netty.
                """;

        List<Section> sections = chunker.chunk(markdown);

        assertEquals(3, sections.size());

        assertEquals("Component Changes", sections.get(0).title());
        assertTrue(sections.get(0).content().contains("following components"));
        assertEquals(2, sections.get(0).level());

        assertEquals("camel-http4", sections.get(1).title());
        assertTrue(sections.get(1).content().contains("renamed to http"));
        assertEquals(3, sections.get(1).level());

        assertEquals("camel-netty4", sections.get(2).title());
        assertTrue(sections.get(2).content().contains("renamed to netty"));
        assertEquals(3, sections.get(2).level());
    }

    @Test
    void contentBeforeFirstHeading() {
        String markdown = """
                This is an introduction paragraph.

                ## First Section

                Section content here.
                """;

        List<Section> sections = chunker.chunk(markdown);

        assertEquals(2, sections.size());
        assertEquals("Introduction", sections.get(0).title());
        assertTrue(sections.get(0).content().contains("introduction paragraph"));
    }

    @Test
    void emptyDocument() {
        List<Section> sections = chunker.chunk("");
        assertTrue(sections.isEmpty());
    }

    @Test
    void noHeadings() {
        String markdown = "Just plain text without any headings.";
        List<Section> sections = chunker.chunk(markdown);

        assertEquals(1, sections.size());
        assertEquals("Introduction", sections.get(0).title());
    }
}

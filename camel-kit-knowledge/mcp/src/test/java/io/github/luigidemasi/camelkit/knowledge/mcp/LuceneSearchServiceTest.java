package io.github.luigidemasi.camelkit.knowledge.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LuceneSearchServiceTest {

    @Test
    void searchResultRecord() {
        var result = new LuceneSearchService.SearchResult(
                "id-1", "apache-camel", "component-migration",
                "2.x", "4.x", List.of("quarkus"), "http4 renamed", "http4 renamed to http", 12.5f
        );

        assertEquals("id-1", result.id());
        assertEquals("apache-camel", result.source());
        assertEquals(12.5f, result.score());
    }
}

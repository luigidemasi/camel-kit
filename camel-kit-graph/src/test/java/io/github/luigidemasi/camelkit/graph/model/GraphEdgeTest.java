package io.github.luigidemasi.camelkit.graph.model;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GraphEdgeTest {

    @Test
    void createSimpleEdge() {
        GraphEdge edge = new GraphEdge(
            "class:com.example.Foo",
            "class:com.example.Bar",
            EdgeType.EXTENDS,
            Map.of()
        );

        assertEquals("class:com.example.Foo", edge.from());
        assertEquals("class:com.example.Bar", edge.to());
        assertEquals(EdgeType.EXTENDS, edge.type());
    }

    @Test
    void createEdgeWithProperties() {
        GraphEdge edge = new GraphEdge(
            "route:processOrders",
            "processor:processOrders:marshal:0",
            EdgeType.PROCESSES,
            Map.of("order", "0")
        );

        assertEquals("0", edge.properties().get("order"));
    }
}

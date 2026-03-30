package io.github.luigidemasi.camelkit.graph.model;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GraphNodeTest {

    @Test
    void createClassNode() {
        GraphNode node = new GraphNode(
            "class:com.example.OrderRoute",
            NodeType.CLASS,
            Map.of("fqn", "com.example.OrderRoute",
                   "name", "OrderRoute",
                   "package", "com.example",
                   "file", "src/main/java/com/example/OrderRoute.java")
        );

        assertEquals("class:com.example.OrderRoute", node.id());
        assertEquals(NodeType.CLASS, node.type());
        assertEquals("OrderRoute", node.properties().get("name"));
    }

    @Test
    void createEndpointNode() {
        GraphNode node = new GraphNode(
            "endpoint:kafka:orders",
            NodeType.CAMEL_ENDPOINT,
            Map.of("uri", "kafka:orders", "component", "kafka", "scheme", "kafka")
        );

        assertEquals(NodeType.CAMEL_ENDPOINT, node.type());
        assertEquals("kafka", node.properties().get("component"));
    }

    @Test
    void nodeIdPrefix() {
        GraphNode node = new GraphNode("route:processOrders", NodeType.CAMEL_ROUTE, Map.of());
        assertTrue(node.id().startsWith("route:"));
    }
}

package io.github.luigidemasi.camelkit.plan;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanAnalyzerTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    static final String FOUR_TASK_PLAN = """
            # Implementation Plan

            ## Tasks

            ### Task 1: Order Ingestion Route
            - Create: `src/main/resources/camel/order-ingestion.camel.yaml`
            - Create: `src/main/resources/schemas/order.json`
            - [ ] Implement route logic
            - [ ] Add error handling

            ### Task 2: Inventory Check Route
            - Create: `src/main/resources/camel/inventory-check.camel.yaml`
            - Modify: `src/main/resources/application.properties`
            - [ ] Implement route logic

            ### Task 3: Order Fulfillment Route
            - Create: `src/main/resources/camel/order-fulfillment.camel.yaml`
            - Modify: `src/main/resources/application.properties`
            - [ ] Wire up to inventory check

            ### Task 4: Schema Definitions
            - Create: `src/main/resources/schemas/inventory.json`
            - Create: `src/main/resources/schemas/fulfillment.json`
            - [ ] Define JSON schemas
            """;

    @Test
    void parsesTasksFromPlan() {
        PlanAnalyzer analyzer = new PlanAnalyzer();
        List<PlanAnalyzer.TaskInfo> tasks = analyzer.parseTasks(FOUR_TASK_PLAN);
        assertEquals(4, tasks.size());
        assertEquals(1, tasks.get(0).number());
        assertEquals("Order Ingestion Route", tasks.get(0).name());
        assertEquals(2, tasks.get(1).number());
        assertEquals("Inventory Check Route", tasks.get(1).name());
        assertEquals(3, tasks.get(2).number());
        assertEquals("Order Fulfillment Route", tasks.get(2).name());
        assertEquals(4, tasks.get(3).number());
        assertEquals("Schema Definitions", tasks.get(3).name());
    }

    @Test
    void extractsFilesList() {
        PlanAnalyzer analyzer = new PlanAnalyzer();
        List<PlanAnalyzer.TaskInfo> tasks = analyzer.parseTasks(FOUR_TASK_PLAN);

        assertEquals(List.of(
                "src/main/resources/camel/order-ingestion.camel.yaml",
                "src/main/resources/schemas/order.json"),
                tasks.get(0).files());

        assertEquals(List.of(
                "src/main/resources/camel/inventory-check.camel.yaml",
                "src/main/resources/application.properties"),
                tasks.get(1).files());

        assertEquals(List.of(
                "src/main/resources/schemas/inventory.json",
                "src/main/resources/schemas/fulfillment.json"),
                tasks.get(3).files());
    }

    @Test
    void detectsFileDependencies() {
        PlanAnalyzer analyzer = new PlanAnalyzer();
        List<PlanAnalyzer.TaskInfo> tasks = analyzer.parseTasks(FOUR_TASK_PLAN);
        Map<Integer, List<Integer>> deps = analyzer.findDependencies(tasks);

        // Task 3 shares application.properties with Task 2
        assertTrue(deps.containsKey(3));
        assertTrue(deps.get(3).contains(2));

        // Task 1 and Task 4 have no dependencies
        assertFalse(deps.containsKey(1));
        assertFalse(deps.containsKey(4));
    }

    @Test
    void computesParallelWaves() {
        PlanAnalyzer analyzer = new PlanAnalyzer();
        List<PlanAnalyzer.TaskInfo> tasks = analyzer.parseTasks(FOUR_TASK_PLAN);
        List<PlanAnalyzer.Wave> waves = analyzer.computeWaves(tasks);

        assertEquals(2, waves.size());

        // Wave 1: tasks 1, 2, 4 (no dependencies)
        assertEquals(1, waves.get(0).waveNumber());
        assertEquals(List.of(1, 2, 4), waves.get(0).taskNumbers());

        // Wave 2: task 3 (depends on task 2)
        assertEquals(2, waves.get(1).waveNumber());
        assertEquals(List.of(3), waves.get(1).taskNumbers());
    }

    @Test
    void singleTaskProducesSingleWave() {
        String plan = """
                ### Task 1: Single Route
                - Create: `src/main/resources/camel/route.yaml`
                - [ ] Implement
                """;
        PlanAnalyzer analyzer = new PlanAnalyzer();
        List<PlanAnalyzer.TaskInfo> tasks = analyzer.parseTasks(plan);
        List<PlanAnalyzer.Wave> waves = analyzer.computeWaves(tasks);

        assertEquals(1, waves.size());
        assertEquals(1, waves.get(0).waveNumber());
        assertEquals(List.of(1), waves.get(0).taskNumbers());
    }

    @Test
    void allTasksIndependentProducesSingleWave() {
        String plan = """
                ### Task 1: Route A
                - Create: `a.yaml`
                - [ ] Implement

                ### Task 2: Route B
                - Create: `b.yaml`
                - [ ] Implement

                ### Task 3: Route C
                - Create: `c.yaml`
                - [ ] Implement
                """;
        PlanAnalyzer analyzer = new PlanAnalyzer();
        List<PlanAnalyzer.TaskInfo> tasks = analyzer.parseTasks(plan);
        List<PlanAnalyzer.Wave> waves = analyzer.computeWaves(tasks);

        assertEquals(1, waves.size());
        assertEquals(List.of(1, 2, 3), waves.get(0).taskNumbers());
    }

    @Test
    void generatesJsonOutput() throws Exception {
        PlanAnalyzer analyzer = new PlanAnalyzer();
        List<PlanAnalyzer.TaskInfo> tasks = analyzer.parseTasks(FOUR_TASK_PLAN);
        String json = analyzer.toJson(tasks);

        JsonNode root = MAPPER.readTree(json);
        assertTrue(root.has("waves"));
        assertTrue(root.has("dependencies"));
        assertTrue(root.has("tasks"));

        // Verify waves structure
        JsonNode waves = root.get("waves");
        assertTrue(waves.isArray());
        assertEquals(2, waves.size());
        assertEquals(1, waves.get(0).get("wave").asInt());
        assertTrue(waves.get(0).get("tasks").isArray());
        assertTrue(waves.get(0).has("taskNames"));

        // Verify dependencies
        JsonNode deps = root.get("dependencies");
        assertTrue(deps.has("3"));
        assertEquals(2, deps.get("3").get(0).asInt());

        // Verify tasks
        JsonNode tasksNode = root.get("tasks");
        assertEquals(4, tasksNode.size());
        assertEquals(1, tasksNode.get(0).get("number").asInt());
        assertEquals("Order Ingestion Route", tasksNode.get(0).get("name").asText());
        assertTrue(tasksNode.get(0).get("files").isArray());
    }
}

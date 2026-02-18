package io.github.luigidemasi.camelkit.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

/**
 * Processes Citrus JSON schemas and generates a simplified reference
 * that AI coding assistants can use for reliable test generation.
 */
public class CitrusSchemaProcessor {

    private final ObjectMapper mapper;

    public CitrusSchemaProcessor() {
        this.mapper = new ObjectMapper();
    }

    /**
     * Process cached Citrus schemas and generate a simplified quick reference.
     *
     * @param citrusCacheDir the directory containing cached Citrus schemas
     * @return path to the generated quick reference file
     */
    public Path generateQuickReference(Path citrusCacheDir) throws Exception {
        Path actionsFile = citrusCacheDir.resolve("citrus-catalog-aggregate-test-actions.json");
        Path endpointsFile = citrusCacheDir.resolve("citrus-catalog-aggregate-endpoints.json");
        Path containersFile = citrusCacheDir.resolve("citrus-catalog-aggregate-test-containers.json");

        ObjectNode reference = mapper.createObjectNode();
        reference.put("description", "Citrus YAML Quick Reference - Use this for generating valid Citrus tests");

        // Process test actions
        if (Files.exists(actionsFile)) {
            JsonNode actions = mapper.readTree(Files.readString(actionsFile));
            reference.set("actions", extractActions(actions));
        }

        // Process endpoints
        if (Files.exists(endpointsFile)) {
            JsonNode endpoints = mapper.readTree(Files.readString(endpointsFile));
            reference.set("endpoints", extractEndpoints(endpoints));
        }

        // Process containers
        if (Files.exists(containersFile)) {
            JsonNode containers = mapper.readTree(Files.readString(containersFile));
            reference.set("testcontainers", extractContainers(containers));
        }

        // Write the quick reference
        Path outputFile = citrusCacheDir.resolve("citrus-quick-reference.json");
        Files.writeString(outputFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(reference));

        // Also generate a markdown version for easier AI consumption
        generateMarkdownReference(citrusCacheDir, reference);

        return outputFile;
    }

    private ArrayNode extractActions(JsonNode actionsRoot) {
        ArrayNode result = mapper.createArrayNode();

        // The schema is an object with action names as keys
        Iterator<Map.Entry<String, JsonNode>> fields = actionsRoot.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String actionName = entry.getKey();
            JsonNode actionDef = entry.getValue();

            ObjectNode action = mapper.createObjectNode();
            action.put("name", actionName);
            action.put("kind", actionDef.path("kind").asText());
            action.put("title", actionDef.path("title").asText());
            action.put("description", actionDef.path("description").asText());
            action.put("group", actionDef.path("group").asText(""));

            // Extract properties from propertiesSchema
            JsonNode propsSchema = actionDef.path("propertiesSchema").path("properties");
            if (propsSchema.isObject()) {
                ArrayNode props = mapper.createArrayNode();
                Iterator<Map.Entry<String, JsonNode>> propFields = propsSchema.fields();
                while (propFields.hasNext()) {
                    Map.Entry<String, JsonNode> propEntry = propFields.next();
                    ObjectNode prop = mapper.createObjectNode();
                    prop.put("name", propEntry.getKey());
                    JsonNode propDef = propEntry.getValue();
                    prop.put("type", propDef.path("type").asText("string"));
                    prop.put("title", propDef.path("title").asText());
                    prop.put("description", propDef.path("description").asText());
                    props.add(prop);
                }
                action.set("properties", props);
            }

            result.add(action);
        }

        return result;
    }

    private ArrayNode extractEndpoints(JsonNode endpointsRoot) {
        ArrayNode result = mapper.createArrayNode();

        // The schema is an object with endpoint names as keys
        Iterator<Map.Entry<String, JsonNode>> fields = endpointsRoot.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String endpointName = entry.getKey();
            JsonNode endpointDef = entry.getValue();

            ObjectNode endpoint = mapper.createObjectNode();
            endpoint.put("name", endpointName);
            endpoint.put("kind", endpointDef.path("kind").asText());
            endpoint.put("title", endpointDef.path("title").asText());
            endpoint.put("description", endpointDef.path("description").asText());

            // Extract properties
            JsonNode propsSchema = endpointDef.path("propertiesSchema").path("properties");
            if (propsSchema.isObject()) {
                ArrayNode props = mapper.createArrayNode();
                Iterator<Map.Entry<String, JsonNode>> propFields = propsSchema.fields();
                while (propFields.hasNext()) {
                    Map.Entry<String, JsonNode> propEntry = propFields.next();
                    ObjectNode prop = mapper.createObjectNode();
                    prop.put("name", propEntry.getKey());
                    JsonNode propDef = propEntry.getValue();
                    prop.put("type", propDef.path("type").asText("string"));
                    prop.put("title", propDef.path("title").asText());
                    props.add(prop);
                }
                endpoint.set("properties", props);
            }

            result.add(endpoint);
        }

        return result;
    }

    private ArrayNode extractContainers(JsonNode containersRoot) {
        ArrayNode result = mapper.createArrayNode();

        // The schema is an object with container names as keys
        Iterator<Map.Entry<String, JsonNode>> fields = containersRoot.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String containerName = entry.getKey();
            JsonNode containerDef = entry.getValue();

            ObjectNode container = mapper.createObjectNode();
            container.put("name", containerName);
            container.put("kind", containerDef.path("kind").asText());
            container.put("title", containerDef.path("title").asText());
            container.put("description", containerDef.path("description").asText());

            // Extract properties
            JsonNode propsSchema = containerDef.path("propertiesSchema").path("properties");
            if (propsSchema.isObject()) {
                ArrayNode props = mapper.createArrayNode();
                Iterator<Map.Entry<String, JsonNode>> propFields = propsSchema.fields();
                while (propFields.hasNext()) {
                    Map.Entry<String, JsonNode> propEntry = propFields.next();
                    ObjectNode prop = mapper.createObjectNode();
                    prop.put("name", propEntry.getKey());
                    JsonNode propDef = propEntry.getValue();
                    prop.put("type", propDef.path("type").asText("string"));
                    prop.put("title", propDef.path("title").asText());
                    props.add(prop);
                }
                container.set("properties", props);
            }

            result.add(container);
        }

        return result;
    }

    private void generateMarkdownReference(Path citrusCacheDir, ObjectNode reference) throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("# Citrus YAML Quick Reference\n\n");
        md.append("Use this reference when generating Citrus YAML tests.\n\n");

        // Actions section
        md.append("## Test Actions\n\n");
        md.append("| Action | Kind | Description |\n");
        md.append("|--------|------|-------------|\n");
        JsonNode actions = reference.path("actions");
        if (actions.isArray()) {
            for (JsonNode action : actions) {
                String name = action.path("name").asText();
                String kind = action.path("kind").asText();
                String description = action.path("description").asText();
                if (!name.isEmpty()) {
                    // Truncate description for table
                    if (description.length() > 60) {
                        description = description.substring(0, 57) + "...";
                    }
                    md.append("| `").append(name).append("` | ").append(kind).append(" | ").append(description).append(" |\n");
                }
            }
        }
        md.append("\n");

        // Common actions detail
        md.append("### Common Actions Detail\n\n");
        String[] commonActions = {"send", "receive", "sql", "sleep", "echo", "camel", "testcontainers"};
        for (String actionName : commonActions) {
            for (JsonNode action : actions) {
                if (actionName.equals(action.path("name").asText())) {
                    md.append("#### `").append(actionName).append("`\n\n");
                    md.append(action.path("description").asText()).append("\n\n");
                    JsonNode props = action.path("properties");
                    if (props.isArray() && props.size() > 0) {
                        md.append("| Property | Type | Description |\n");
                        md.append("|----------|------|-------------|\n");
                        for (JsonNode prop : props) {
                            md.append("| `").append(prop.path("name").asText()).append("` ");
                            md.append("| ").append(prop.path("type").asText()).append(" ");
                            md.append("| ").append(prop.path("description").asText()).append(" |\n");
                        }
                        md.append("\n");
                    }
                    break;
                }
            }
        }

        // Endpoints section
        md.append("## Endpoints\n\n");
        md.append("| Endpoint | Kind | Description |\n");
        md.append("|----------|------|-------------|\n");
        JsonNode endpoints = reference.path("endpoints");
        if (endpoints.isArray()) {
            for (JsonNode endpoint : endpoints) {
                String name = endpoint.path("name").asText();
                String kind = endpoint.path("kind").asText();
                String description = endpoint.path("description").asText();
                if (!name.isEmpty()) {
                    if (description.length() > 60) {
                        description = description.substring(0, 57) + "...";
                    }
                    md.append("| `").append(name).append("` | ").append(kind).append(" | ").append(description).append(" |\n");
                }
            }
        }
        md.append("\n");

        // Testcontainers section
        md.append("## Testcontainers\n\n");
        md.append("| Container | Description |\n");
        md.append("|-----------|-------------|\n");
        JsonNode containers = reference.path("testcontainers");
        if (containers.isArray()) {
            for (JsonNode container : containers) {
                String name = container.path("name").asText();
                String description = container.path("description").asText();
                if (!name.isEmpty()) {
                    if (description.length() > 80) {
                        description = description.substring(0, 77) + "...";
                    }
                    md.append("| `").append(name).append("` | ").append(description).append(" |\n");
                }
            }
        }
        md.append("\n");

        Path mdFile = citrusCacheDir.resolve("citrus-quick-reference.md");
        Files.writeString(mdFile, md.toString());
    }
}

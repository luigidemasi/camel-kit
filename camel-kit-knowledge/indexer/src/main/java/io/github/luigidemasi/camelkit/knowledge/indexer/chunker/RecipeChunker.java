package io.github.luigidemasi.camelkit.knowledge.indexer.chunker;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses OpenRewrite YAML recipe files and produces one chunk per recipe definition.
 * Extracts structured fields (recipe type, old/new names) for exact-match indexing.
 *
 * OpenRewrite recipes follow this structure:
 * <pre>
 * type: specs.openrewrite.org/v1beta/recipe
 * name: org.apache.camel.upgrade.CamelMigrationRecipe
 * recipeList:
 *   - org.openrewrite.java.ChangeType:
 *       oldFullyQualifiedTypeName: org.apache.camel.http4.HttpComponent
 *       newFullyQualifiedTypeName: org.apache.camel.http.HttpComponent
 * </pre>
 */
public class RecipeChunker {

    /**
     * A single recipe extracted from an OpenRewrite YAML file.
     */
    public record Recipe(
        String recipeType,        // e.g., "ChangeType", "ChangeMethodName"
        String oldName,           // e.g., "org.apache.camel.http4.HttpComponent"
        String newName,           // e.g., "org.apache.camel.http.HttpComponent"
        String componentName,     // extracted short component name (e.g., "http4")
        String rawYaml            // the raw YAML text of this recipe entry
    ) {}

    /**
     * Parse an OpenRewrite YAML recipe file and extract individual recipes.
     *
     * @param yamlContent the full YAML file content
     * @return list of parsed recipes
     */
    /**
     * Parse an OpenRewrite YAML recipe file and extract individual recipes.
     * Supports multi-document YAML files (separated by {@code ---}).
     *
     * @param yamlContent the full YAML file content
     * @return list of parsed recipes
     */
    @SuppressWarnings("unchecked")
    public List<Recipe> chunk(String yamlContent) {
        List<Recipe> recipes = new ArrayList<>();
        Yaml yaml = new Yaml();

        for (Object parsed : yaml.loadAll(yamlContent)) {
            if (!(parsed instanceof Map<?, ?> root)) {
                continue;
            }

            Object recipeList = root.get("recipeList");
            if (!(recipeList instanceof List<?> list)) {
                continue;
            }

            // Extract recipe name for context
            String recipeName = root.containsKey("name") ? root.get("name").toString() : null;

            for (Object entry : list) {
                if (entry instanceof Map<?, ?> recipeMap) {
                    for (Map.Entry<?, ?> e : recipeMap.entrySet()) {
                        String fullRecipeType = e.getKey().toString();
                        String recipeType = fullRecipeType.contains(".")
                                ? fullRecipeType.substring(fullRecipeType.lastIndexOf('.') + 1)
                                : fullRecipeType;

                        if (e.getValue() instanceof Map<?, ?> params) {
                            String oldName = getStringParam(params, "oldFullyQualifiedTypeName", "oldMethodName", "oldPackageName");
                            String newName = getStringParam(params, "newFullyQualifiedTypeName", "newMethodName", "newPackageName");
                            String componentName = extractComponentName(oldName);
                            String rawYaml = formatEntry(fullRecipeType, params);

                            recipes.add(new Recipe(recipeType, oldName, newName, componentName, rawYaml));
                        }
                    }
                }
            }
        }

        return recipes;
    }

    private String getStringParam(Map<?, ?> params, String... keys) {
        for (String key : keys) {
            Object val = params.get(key);
            if (val != null) {
                return val.toString();
            }
        }
        return null;
    }

    /**
     * Extract a short component name from a fully qualified type.
     * e.g., "org.apache.camel.component.http4.HttpComponent" -> "http4"
     */
    private String extractComponentName(String fqcn) {
        if (fqcn == null) return null;
        String[] parts = fqcn.split("\\.");
        // Look for the component package segment (before the class name)
        for (int i = parts.length - 2; i >= 0; i--) {
            if (!parts[i].equals("component") && !parts[i].equals("camel")
                    && !parts[i].equals("apache") && !parts[i].equals("org")) {
                return parts[i];
            }
        }
        return null;
    }

    private String formatEntry(String type, Map<?, ?> params) {
        StringBuilder sb = new StringBuilder();
        sb.append(type).append(":\n");
        for (Map.Entry<?, ?> p : params.entrySet()) {
            sb.append("  ").append(p.getKey()).append(": ").append(p.getValue()).append("\n");
        }
        return sb.toString();
    }
}

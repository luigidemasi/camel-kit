package io.github.luigidemasi.camelkit.doc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

public final class FrontmatterHandler {

    private static final String DELIMITER = "---";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
            new YAMLFactory()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .disable(YAMLGenerator.Feature.SPLIT_LINES));
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private FrontmatterHandler() {
    }

    public static boolean hasFrontmatter(String content) {
        return content != null && content.startsWith(DELIMITER);
    }

    public static String extractFrontmatterYaml(String content) {
        if (!hasFrontmatter(content)) {
            return null;
        }
        int start = DELIMITER.length();
        int end = content.indexOf("\n" + DELIMITER, start);
        if (end < 0) {
            return null;
        }
        return content.substring(start, end).trim();
    }

    public static String extractBody(String content) {
        if (!hasFrontmatter(content)) {
            return content;
        }
        int start = DELIMITER.length();
        int end = content.indexOf("\n" + DELIMITER, start);
        if (end < 0) {
            return content;
        }
        int bodyStart = end + 1 + DELIMITER.length();
        if (bodyStart < content.length() && content.charAt(bodyStart) == '\n') {
            bodyStart++;
        }
        return content.substring(bodyStart);
    }

    @SuppressWarnings("unchecked")
    public static StalenessInfo parseStaleness(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return StalenessInfo.fresh();
        }
        try {
            Map<String, Object> root = YAML_MAPPER.readValue(yaml, Map.class);
            Object stalenessObj = root.get("staleness");
            if (stalenessObj instanceof Map) {
                return YAML_MAPPER.convertValue(stalenessObj, StalenessInfo.class);
            }
            return StalenessInfo.fresh();
        } catch (Exception e) {
            return StalenessInfo.fresh();
        }
    }

    @SuppressWarnings("unchecked")
    public static GeneratedInfo parseGenerated(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> root = YAML_MAPPER.readValue(yaml, Map.class);
            Object generatedObj = root.get("generated");
            if (generatedObj instanceof Map) {
                return YAML_MAPPER.convertValue(generatedObj, GeneratedInfo.class);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String writeFrontmatter(StalenessInfo staleness, GeneratedInfo generated, String body) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("staleness", staleness);
            if (generated != null) {
                root.put("generated", generated);
            }
            String yaml = YAML_MAPPER.writeValueAsString(root).trim();
            StringBuilder sb = new StringBuilder();
            sb.append(DELIMITER).append('\n');
            sb.append(yaml).append('\n');
            sb.append(DELIMITER).append('\n');
            if (body != null && !body.isEmpty()) {
                sb.append(body);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize frontmatter: " + e.getMessage(), e);
        }
    }

    public static String markStale(String content, String reason) {
        return markStale(content, reason, Instant.now().toString());
    }

    public static String markStale(String content, String reason, String since) {
        String yaml = extractFrontmatterYaml(content);
        String body = extractBody(content);
        StalenessInfo staleness = StalenessInfo.stale(reason, since);
        GeneratedInfo generated = parseGenerated(yaml);
        return writeFrontmatter(staleness, generated, body);
    }

    public static String clearStale(String content) {
        String yaml = extractFrontmatterYaml(content);
        String body = extractBody(content);
        StalenessInfo staleness = StalenessInfo.fresh();
        GeneratedInfo generated = parseGenerated(yaml);
        return writeFrontmatter(staleness, generated, body);
    }

    public static String toCheckJson(String filePath, String content) {
        try {
            String yaml = extractFrontmatterYaml(content);
            StalenessInfo staleness = parseStaleness(yaml);
            GeneratedInfo generated = parseGenerated(yaml);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("file", filePath);
            result.put("stale", staleness.isStale());
            result.put("since", staleness.getSince());
            result.put("reason", staleness.getReason());
            result.put("generated", generated);

            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build check JSON: " + e.getMessage(), e);
        }
    }
}

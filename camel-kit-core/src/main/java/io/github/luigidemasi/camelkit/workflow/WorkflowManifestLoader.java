package io.github.luigidemasi.camelkit.workflow;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Loads the bundled Camel-Kit workflow manifest.
 */
public final class WorkflowManifestLoader {

    public static final String DEFAULT_RESOURCE = "workflow/camel-kit-workflow.yaml";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private WorkflowManifestLoader() {
        // Utility class
    }

    public static WorkflowManifest loadDefault() throws IOException {
        return load(DEFAULT_RESOURCE);
    }

    public static WorkflowManifest load(String resourcePath) throws IOException {
        try (InputStream input = WorkflowManifestLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Workflow manifest not found on classpath: " + resourcePath);
            }
            return OBJECT_MAPPER.readValue(input, WorkflowManifest.class);
        }
    }
}

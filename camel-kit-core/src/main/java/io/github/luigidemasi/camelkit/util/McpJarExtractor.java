package io.github.luigidemasi.camelkit.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Extracts embedded MCP runner JARs from classpath resources to the project's
 * {@code .camel-kit/mcp/} directory. Used in standalone (fat JAR) mode where
 * MCP JARs are bundled inside the distribution JAR rather than fetched via JBang.
 */
public class McpJarExtractor {

    private static final String RESOURCE_PREFIX = "mcp-jars/";

    private static final String[] MCP_JARS = {
        "camel-jbang-mcp-runner.jar",
        "camel-kit-knowledge-mcp-runner.jar"
    };

    /**
     * Check if running in standalone mode by looking for the marker resource.
     * The standalone fat JAR includes {@code META-INF/camel-kit-standalone}.
     */
    public static boolean isStandaloneMode() {
        return McpJarExtractor.class.getClassLoader()
                .getResource("META-INF/camel-kit-standalone") != null;
    }

    /**
     * Extracts embedded MCP runner JARs from classpath to the target directory.
     *
     * @param mcpDir target directory (e.g., {@code .camel-kit/mcp/})
     * @return number of JARs successfully extracted
     */
    public static int extractMcpJars(Path mcpDir) throws Exception {
        Files.createDirectories(mcpDir);
        int count = 0;
        for (String jarName : MCP_JARS) {
            try (InputStream in = McpJarExtractor.class.getClassLoader()
                    .getResourceAsStream(RESOURCE_PREFIX + jarName)) {
                if (in != null) {
                    Files.copy(in, mcpDir.resolve(jarName), StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
        }
        return count;
    }
}

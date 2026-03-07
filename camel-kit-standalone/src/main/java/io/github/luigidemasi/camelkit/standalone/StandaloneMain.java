package io.github.luigidemasi.camelkit.standalone;

import io.github.luigidemasi.camelkit.CamelKitMain;

/**
 * Entry point for the standalone (fat JAR) distribution of Camel-Kit.
 * <p>
 * Usage: {@code java -jar camel-kit-standalone.jar init --here}
 * <p>
 * This JAR bundles everything needed for offline operation:
 * <ul>
 *   <li>All camel-kit skills and templates</li>
 *   <li>MCP runner JARs (camel-jbang-mcp, camel-kit-knowledge-mcp)</li>
 *   <li>Citrus test schemas</li>
 * </ul>
 */
public class StandaloneMain {

    public static void main(String[] args) {
        CamelKitMain.run(args);
    }
}

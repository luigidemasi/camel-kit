package io.github.luigidemasi.camelkit.plugin.wanaku;

/**
 * Configuration for Wanaku deployment target.
 *
 * <p>Holds the connection details for a Wanaku MCP Router instance
 * where generated Camel routes will be deployed as MCP tools via
 * the Camel Integration Capability.</p>
 */
public record WanakuConfig(
    String url,           // Wanaku router URL (e.g., http://localhost:8080)
    String namespace      // Wanaku namespace for tool registration (e.g., "default", "ns-1")
) {
    public static final String DEFAULT_URL = "http://localhost:8080";
    public static final String DEFAULT_NAMESPACE = "default";

    public static WanakuConfig defaults() {
        return new WanakuConfig(DEFAULT_URL, DEFAULT_NAMESPACE);
    }
}

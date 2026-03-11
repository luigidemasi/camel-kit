package io.github.luigidemasi.camelkit.plugin.wanaku;

import io.github.luigidemasi.camelkit.util.TemplateUtils;

import java.io.IOException;

/**
 * Generates Wanaku rules file templates.
 *
 * <p>The Wanaku rules file maps Camel route IDs to MCP tools and resources,
 * defining how each route is exposed to AI agents via the Wanaku MCP Router's
 * Camel Integration Capability.</p>
 *
 * <p>The generated template provides the structure that the AI agent
 * (via the {@code /camel-wanaku} command) fills in with actual route
 * IDs, descriptions, and property mappings.</p>
 *
 * @see <a href="https://wanaku.ai/docs/">Wanaku MCP Router Documentation</a>
 */
public class WanakuRulesGenerator {

    private static final String TEMPLATE_PATH = "templates/wanaku/rules-template.yaml";

    /**
     * Read the Wanaku rules YAML template.
     *
     * @return the template content
     * @throws IOException if the template cannot be read
     */
    public String readTemplate() throws IOException {
        return TemplateUtils.readTemplate(TEMPLATE_PATH);
    }
}

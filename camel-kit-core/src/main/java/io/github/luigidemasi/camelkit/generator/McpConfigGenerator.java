package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.util.AnsiColors;
import io.github.luigidemasi.camelkit.util.TemplateUtils;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;

import com.fasterxml.jackson.databind.ObjectMapper;

class McpConfigGenerator {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    void generate(InitContext ctx, WorkflowManifest workflow) {
        try {
            Path configFile = ctx.projectDir().resolve(ctx.agent().mcpConfigPath());
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }

            QuteTemplateEngine qute = new QuteTemplateEngine();
            String template = TemplateUtils.readTemplate(ctx.agent().mcpConfigTemplatePath());
            String processed = qute.renderString(template, templateData(ctx, workflow));
            Files.writeString(configFile, processed);

            ctx.printer().println(AnsiColors.green("✓") + " MCP config created for " + ctx.agent().name());
        } catch (Exception e) {
            ctx.printer().println(AnsiColors.yellow("  Warning: Could not create MCP config: " + e.getMessage()));
        }
    }

    private Map<String, Object> templateData(InitContext ctx, WorkflowManifest workflow) throws IOException {
        DistributionConfig dist = CamelKitMain.distribution();
        Map<String, Object> data = new java.util.HashMap<>(
                Map.of(
                        "CAMEL_MCP_VERSION", CamelKitMain.CAMEL_MCP_VERSION,
                        "KNOWLEDGE_VERSION", CamelKitMain.KNOWLEDGE_MCP_VERSION,
                        "CITRUS_MCP_VERSION", resolvedCitrusMcpVersion(ctx, dist),
                        "CAMEL_MCP_REPOS", CamelKitMain.CAMEL_MCP_REPOS,
                        "KNOWLEDGE_MCP_REPOS", CamelKitMain.KNOWLEDGE_MCP_REPOS,
                        "CITRUS_MCP_REPOS", CamelKitMain.CITRUS_MCP_REPOS,
                        "CAMEL_CATALOG_REPOS", CamelKitMain.CAMEL_CATALOG_REPOS));

        WorkflowManifest.WorkflowMcpServer camelServer = workflow.mcpServer("camel");
        data.put("CAMEL_TOOLS_JSON", toJsonArray(camelServer.allowedTools()));
        data.put("CAMEL_VERSION", dist.camelMainVersion());
        data.put("CAMEL_MAIN_VERSION", dist.camelMainVersion());
        data.put("CAMEL_SPRINGBOOT_VERSION", dist.camelSpringbootVersion());
        data.put("CAMEL_QUARKUS_VERSION", dist.camelQuarkusVersion());
        data.put("SPRINGBOOT_BOM_VERSION", dist.springbootBomVersion());
        data.put("SPRING_BOOT_VERSION", dist.springBootVersion());
        data.put("QUARKUS_PLATFORM_VERSION", dist.quarkusPlatformVersion());
        data.put("CAMEL_MAIN_SUPPORTED", dist.camelMainSupported());
        data.put("CAMEL_SPRINGBOOT_SUPPORTED", dist.camelSpringbootSupported());
        data.put("CAMEL_QUARKUS_SUPPORTED", dist.camelQuarkusSupported());

        WorkflowManifest.WorkflowMcpServer knowledgeServer = workflow.mcpServer("camel-knowledge");
        data.put("KNOWLEDGE_TOOLS_JSON", toJsonArray(knowledgeServer.allowedTools()));
        data.put("KNOWLEDGE_DESCRIPTION", knowledgeServer.description());

        WorkflowManifest.WorkflowMcpServer citrusServer = workflow.mcpServer("citrus");
        data.put("CITRUS_VERSION", ctx.citrusVersion());
        data.put("CITRUS_TOOLS_JSON", toJsonArray(citrusServer.allowedTools()));
        data.put("CITRUS_DESCRIPTION", citrusServer.description());
        return data;
    }

    private static String resolvedCitrusMcpVersion(InitContext ctx, DistributionConfig dist) {
        if (dist.citrusMcpVersion().equals(dist.citrusVersion())) {
            return ctx.citrusVersion();
        }
        return dist.citrusMcpVersion();
    }

    private static String toJsonArray(List<String> values) throws IOException {
        return JSON_MAPPER.writeValueAsString(values);
    }
}

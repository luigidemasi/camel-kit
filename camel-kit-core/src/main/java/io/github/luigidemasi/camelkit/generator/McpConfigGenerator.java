package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
            String processed = qute.renderString(template, templateData(ctx.distribution(), workflow));
            Files.writeString(configFile, processed);

            ctx.printer().println(AnsiColors.green("✓") + " MCP config created for " + ctx.agent().name());
        } catch (Exception e) {
            throw new IllegalStateException("Could not create MCP config: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> templateData(DistributionConfig dist, WorkflowManifest workflow) throws IOException {
        Map<String, Object> data = new java.util.HashMap<>(
                Map.of(
                        "CAMEL_MCP_VERSION", dist.camelMcpVersion(),
                        "KNOWLEDGE_VERSION", dist.knowledgeMcpVersion(),
                        "CITRUS_MCP_VERSION", dist.citrusMcpVersion(),
                        "CAMEL_MCP_REPOS", dist.camelMcpRepos(),
                        "KNOWLEDGE_MCP_REPOS", dist.knowledgeMcpRepos(),
                        "CITRUS_MCP_REPOS", dist.citrusMcpRepos(),
                        "CAMEL_CATALOG_REPOS", dist.camelCatalogRepos(),
                        "PI_VERSION", dist.piVersion(),
                        "PI_MCP_ADAPTER_VERSION", dist.piMcpAdapterVersion()));

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
        data.put("CITRUS_TOOLS_JSON", toJsonArray(citrusServer.allowedTools()));
        data.put("CITRUS_DESCRIPTION", citrusServer.description());
        return data;
    }

    private static String toJsonArray(List<String> values) throws IOException {
        return JSON_MAPPER.writeValueAsString(values);
    }
}

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

    void generate(InitContext ctx, WorkflowManifest workflow) throws InvalidAgentConfigurationException {
        try {
            Path configFile = ctx.projectDir().resolve(ctx.agent().mcpConfigPath());
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }

            QuteTemplateEngine qute = new QuteTemplateEngine();
            String template = TemplateUtils.readTemplate(ctx.agent().mcpConfigTemplatePath());
            String processed = qute.renderString(template, templateData(ctx.distribution(), workflow));
            if ("toml".equals(ctx.agent().mcpConfigFormat())) {
                new CodexConfigMerger().merge(configFile, processed);
            } else if ("opencode".equals(ctx.agentName())) {
                new OpenCodeConfigMerger().merge(configFile, processed);
            } else {
                Files.writeString(configFile, processed);
            }

            ctx.printer().println(AnsiColors.green("✓") + " MCP config created for " + ctx.agent().name());
        } catch (InvalidAgentConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not create MCP config: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> templateData(DistributionConfig dist, WorkflowManifest workflow) throws IOException {
        Map<String, Object> data = new java.util.HashMap<>(
                VersionPlaceholderResolver.buildVersionTemplateData(dist));

        WorkflowManifest.WorkflowMcpServer camelServer = workflow.mcpServer("camel");
        data.put("CAMEL_TOOLS_JSON", toJsonArray(camelServer.allowedTools()));

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

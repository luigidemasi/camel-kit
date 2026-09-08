package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.util.AnsiColors;
import io.github.luigidemasi.camelkit.util.TemplateUtils;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class McpConfigGenerator {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper ANTIGRAVITY_MAPPER
            = io.github.luigidemasi.camelkit.config.OpenCodeProjectConfig.newJsonMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

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
            } else if ("antigravity".equals(ctx.agentName())) {
                ObjectNode config = readAntigravityConfig(configFile);
                ObjectNode generated = (ObjectNode) JSON_MAPPER.readTree(processed).path("mcpServers");
                if (!Files.exists(configFile)) {
                    AtomicFileWriter.write(configFile, processed);
                } else if (!generated.properties().stream()
                        .allMatch(server -> server.getValue().equals(config.path("mcpServers").get(server.getKey())))) {
                    String original = Files.readString(configFile);
                    String bom = original.startsWith("\uFEFF") ? "\uFEFF" : "";
                    JsoncObjectEditor editor = new JsoncObjectEditor(original.substring(bom.length()));
                    if (!config.has("mcpServers")) {
                        editor.upsertRootMember("mcpServers", generated);
                    } else {
                        editor.removeObjectMembers("mcpServers", generated.fieldNames());
                        editor.appendObjectMembers("mcpServers", generated);
                    }
                    AtomicFileWriter.write(configFile, bom + editor.content());
                }
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

    static ObjectNode readAntigravityConfig(Path configFile) throws InvalidAgentConfigurationException {
        if (!Files.exists(configFile, LinkOption.NOFOLLOW_LINKS)) {
            return JSON_MAPPER.createObjectNode();
        }
        if (!Files.isRegularFile(configFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvalidAgentConfigurationException(
                    "Antigravity MCP config must be a regular file: " + configFile);
        }
        try {
            JsonNode config = ANTIGRAVITY_MAPPER.readTree(configFile.toFile());
            if (config == null || !config.isObject()
                    || (config.has("mcpServers") && !config.path("mcpServers").isObject())) {
                throw new InvalidAgentConfigurationException(
                        "Antigravity MCP config must contain a JSON object with an mcpServers object: " + configFile);
            }
            return (ObjectNode) config;
        } catch (IOException e) {
            throw new InvalidAgentConfigurationException(
                    "Invalid Antigravity MCP config " + configFile + ": " + e.getMessage()
                                                         + ". Fix JSON syntax and remove duplicate keys before retrying.",
                    e);
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

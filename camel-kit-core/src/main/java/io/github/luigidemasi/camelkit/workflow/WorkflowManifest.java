package io.github.luigidemasi.camelkit.workflow;

import java.util.List;

/**
 * Machine-readable Camel-Kit workflow contract.
 */
public record WorkflowManifest(
        String version,
        String description,
        List<WorkflowCommand> commands,
        List<WorkflowSkill> skills,
        List<WorkflowStage> stages,
        List<WorkflowArtifact> artifacts,
        List<WorkflowMcpServer> mcpServers,
        WorkflowWebsiteReference websiteReference) {

    public WorkflowManifest {
        commands = List.copyOf(nullToEmpty(commands));
        skills = List.copyOf(nullToEmpty(skills));
        stages = List.copyOf(nullToEmpty(stages));
        artifacts = List.copyOf(nullToEmpty(artifacts));
        mcpServers = List.copyOf(nullToEmpty(mcpServers));
    }

    public List<WorkflowCommand> generatedCommandStubs() {
        return commands.stream()
                .filter(WorkflowCommand::generatedStub)
                .toList();
    }

    public WorkflowMcpServer mcpServer(String id) {
        return mcpServers.stream()
                .filter(server -> server.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown MCP server in workflow manifest: " + id));
    }

    private static <T> List<T> nullToEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }

    public record WorkflowCommand(
            String name,
            List<String> aliases,
            String skill,
            boolean generatedStub,
            boolean userFacing,
            String tier,
            String description,
            boolean standalone,
            boolean chained,
            List<String> skillOnlyAgents) {

        public WorkflowCommand {
            aliases = List.copyOf(nullToEmpty(aliases));
            skillOnlyAgents = List.copyOf(nullToEmpty(skillOnlyAgents));
        }

        public String shortName() {
            return name.startsWith("camel-") ? name.substring("camel-".length()) : name;
        }

        public boolean isSkillOnly(String agentName) {
            return skillOnlyAgents.contains(agentName);
        }
    }

    public record WorkflowSkill(
            String name,
            boolean userInvocable,
            String status,
            String generatedCommand) {
    }

    public record WorkflowStage(
            String id,
            String skill,
            String kind,
            boolean standalone,
            boolean chained,
            List<String> inputs,
            List<String> outputs,
            List<String> transitions) {

        public WorkflowStage {
            inputs = List.copyOf(nullToEmpty(inputs));
            outputs = List.copyOf(nullToEmpty(outputs));
            transitions = List.copyOf(nullToEmpty(transitions));
        }
    }

    public record WorkflowArtifact(
            String id,
            String path,
            List<String> producedBy,
            List<String> consumedBy) {

        public WorkflowArtifact {
            producedBy = List.copyOf(nullToEmpty(producedBy));
            consumedBy = List.copyOf(nullToEmpty(consumedBy));
        }
    }

    public record WorkflowMcpServer(
            String id,
            String displayName,
            String description,
            List<String> allowedTools) {

        public WorkflowMcpServer {
            allowedTools = List.copyOf(nullToEmpty(allowedTools));
        }
    }

    public record WorkflowWebsiteReference(
            String commandReference,
            String architectureReference,
            String userGuide,
            String readme) {
    }
}

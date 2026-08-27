package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

import io.github.luigidemasi.camelkit.util.AnsiColors;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest.WorkflowCommand;

class CommandStubGenerator {

    void generate(InitContext ctx, WorkflowManifest workflow) throws Exception {
        List<WorkflowCommand> commands = workflow.generatedCommandStubs().stream()
                .filter(command -> !command.isSkillOnly(ctx.agentName()))
                .toList();

        for (WorkflowCommand command : commands) {
            String content = commandContent(ctx, command);
            String filename = command.name() + "." + ctx.agent().fileFormat();
            Files.writeString(ctx.commandsDir().resolve(filename), content);
        }

        ctx.printer().println(AnsiColors.green("✓") + " Created " + commands.size() + " command stubs");
    }

    private String commandContent(InitContext ctx, WorkflowCommand command) {
        String content;
        if ("camel-ship".equals(command.name())) {
            String placeholder = ctx.agent().argPlaceholder();
            if (placeholder == null) {
                // No documented all-arguments placeholder for this harness — forward the options in prose.
                content = "Run `" + ctx.commandPrefix() + " ship` once, appending every option supplied to this "
                          + "command invocation verbatim. Add no defaults and do not orchestrate the workflow "
                          + "yourself. Return the command output and whether it succeeded.";
            } else {
                content = "Run `" + ctx.commandPrefix() + " ship " + placeholder
                          + "` once using the supplied Ship options. Add no defaults and do not orchestrate the "
                          + "workflow yourself. Return the command output and whether it succeeded.";
            }
        } else {
            content = "Read " + ctx.agent().skillsDirectory() + "/" + command.skill()
                      + "/SKILL.md and follow those instructions";
            String placeholder = ctx.agent().argPlaceholder();
            if (placeholder != null) {
                content += ". Requested input: " + placeholder;
            }
        }
        if ("toml".equals(ctx.agent().fileFormat())) {
            return wrapInToml(command.shortName(), content);
        }
        if ("bob2".equals(ctx.agentName())) {
            return wrapInBobMarkdown(command, content);
        }
        return content;
    }

    private String wrapInBobMarkdown(WorkflowCommand command, String content) {
        return String.format(Locale.ROOT, """
                ---
                description: "%s"
                argument-hint: "%s"
                ---
                %s
                """, yamlDoubleQuoted(command.description()), yamlDoubleQuoted(commandArgumentHint(command)), content);
    }

    private String wrapInToml(String cmd, String content) {
        String escaped = content.replace("\"\"\"", "\\\"\\\"\\\"");
        return String.format(Locale.ROOT, """
                description = "Camel-Kit %s command"

                prompt = \"\"\"
                %s
                \"\"\"
                """, cmd, escaped);
    }

    private String commandArgumentHint(WorkflowCommand command) {
        return switch (command.name()) {
            case "camel-start" -> "<request>";
            case "camel-brainstorm" -> "<integration-request>";
            case "camel-migrate" -> "<source-platform-or-artifacts>";
            case "camel-plan" -> "<design-spec-or-pipeline-id>";
            case "camel-execute" -> "<pipeline-id-or-plan>";
            case "camel-validate" -> "<pipeline-id-or-route-path>";
            case "camel-ship" -> "[ship-options]";
            case "camel-knowledge" -> "<camel-question>";
            case "camel-debug" -> "<error-or-route-path>";
            default -> "<arguments>";
        };
    }

    private String yamlDoubleQuoted(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

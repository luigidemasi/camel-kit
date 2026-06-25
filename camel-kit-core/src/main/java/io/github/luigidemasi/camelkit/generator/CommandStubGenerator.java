package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

import io.github.luigidemasi.camelkit.util.AnsiColors;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest.WorkflowCommand;

class CommandStubGenerator {

    void generate(InitContext ctx, WorkflowManifest workflow) throws Exception {
        List<WorkflowCommand> commands = workflow.generatedCommandStubs();
        String agentBaseFolder = ctx.agent().folder().substring(0, ctx.agent().folder().lastIndexOf("/"));

        for (WorkflowCommand command : commands) {
            String content = commandContent(ctx, agentBaseFolder, command);
            String filename = command.name() + "." + ctx.agent().fileFormat();
            Files.writeString(ctx.commandsDir().resolve(filename), content);
        }

        ctx.printer().println(AnsiColors.green("✓") + " Created " + commands.size() + " skill reference commands");
    }

    private String commandContent(InitContext ctx, String agentBaseFolder, WorkflowCommand command) {
        String content = "Read " + agentBaseFolder + "/skills/" + command.skill()
                         + "/SKILL.md and follow those instructions";
        if ("toml".equals(ctx.agent().fileFormat())) {
            return wrapInToml(command.shortName(), content);
        }
        return content;
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
}

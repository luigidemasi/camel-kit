package io.github.luigidemasi.camelkit.command.plan;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "plan",
         description = "Implementation plan analysis",
         subcommands = {PlanAnalyzeCommand.class, CommandLine.HelpCommand.class})
public class PlanCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}

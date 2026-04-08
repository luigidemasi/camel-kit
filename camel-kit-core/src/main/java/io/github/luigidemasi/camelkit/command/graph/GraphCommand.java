package io.github.luigidemasi.camelkit.command.graph;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "graph",
         description = "Project graph analysis, generation, and visualization",
         subcommands = {CommandLine.HelpCommand.class})
public class GraphCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}

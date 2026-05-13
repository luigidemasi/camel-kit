package io.github.luigidemasi.camelkit.command.doc;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "doc",
         description = "Pipeline document staleness management",
         subcommands = {
                 DocCheckCommand.class, DocStaleCommand.class, DocUnstaleCommand.class,
                 CommandLine.HelpCommand.class
         })
public class DocCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}

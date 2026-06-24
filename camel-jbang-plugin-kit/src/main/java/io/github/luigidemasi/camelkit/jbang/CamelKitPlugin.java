package io.github.luigidemasi.camelkit.jbang;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
import org.apache.camel.dsl.jbang.core.common.Plugin;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.command.DoctorCommand;
import io.github.luigidemasi.camelkit.command.graph.GraphCommand;
import io.github.luigidemasi.camelkit.command.plan.PlanCommand;

import picocli.CommandLine;

/**
 * Camel JBang plugin for Camel Kit - Design Apache Camel Integrations with AI
 */
@CamelJBangPlugin(name = "camel-jbang-plugin-kit", firstVersion = "4.18.0")
public class CamelKitPlugin implements Plugin {

    @Override
    public void customize(CommandLine commandLine, CamelJBangMain main) {
        // Add the 'kit' subcommand with nested commands
        CamelKitMain camelKitMain = new CamelKitMain();
        camelKitMain.disableTui();
        CommandLine kitCommand = new CommandLine(new KitCommand(main))
                .addSubcommand("init", new CommandLine(new KitInitCommand(main)))
                .addSubcommand("doctor", new CommandLine(new DoctorCommand(camelKitMain)))
                .addSubcommand("graph", new CommandLine(new GraphCommand()))
                .addSubcommand("plan", new CommandLine(new PlanCommand()));

        commandLine.addSubcommand("kit", kitCommand);
    }
}

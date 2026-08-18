package io.github.luigidemasi.camelkit.jbang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
import org.apache.camel.dsl.jbang.core.common.Plugin;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.command.DoctorCommand;
import io.github.luigidemasi.camelkit.command.graph.GraphCommand;
import io.github.luigidemasi.camelkit.command.plan.PlanCommand;
import io.github.luigidemasi.camelkit.command.ship.ShipCommand;

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
                .addSubcommand("init", new CommandLine(new KitInitCommand(main, camelKitMain)))
                .addSubcommand("doctor", new CommandLine(new DoctorCommand(camelKitMain)))
                .addSubcommand("graph", new CommandLine(new GraphCommand()))
                .addSubcommand("plan", new CommandLine(new PlanCommand()))
                .addSubcommand("ship", new CommandLine(new ShipCommand()));

        commandLine.addSubcommand("kit", kitCommand);
        preserveShipAtArguments(commandLine);
    }

    // Camel's plugin hook has no argv, while picocli expands @files before subcommand dispatch. Route expansion at the
    // root so direct Ship context stays literal without changing argument-file behavior for sibling commands.
    private static void preserveShipAtArguments(CommandLine commandLine) {
        boolean expandAtFiles = commandLine.isExpandAtFiles();
        CommandLine.IParameterPreprocessor inherited = commandLine.getCommandSpec().preprocessor();
        commandLine.getCommandSpec().preprocessor((arguments, command, argument, info) -> {
            if (expandAtFiles) {
                if (isShipInvocation(arguments)) {
                    arguments.replaceAll(value -> value.startsWith("@@") ? value.substring(1) : value);
                } else if (hasArgumentFile(arguments)) {
                    replace(arguments, expandArgumentFiles(commandLine, arguments));
                }
            }
            return inherited.preprocess(arguments, command, argument, info);
        });
        commandLine.setExpandAtFiles(false);
    }

    private static boolean isShipInvocation(Stack<String> arguments) {
        if (arguments.size() < 2 || !"kit".equals(arguments.peek())) {
            return false;
        }
        int index = arguments.size() - 2;
        while (index >= 0 && isKitVersionOption(arguments.get(index))) {
            index--;
        }
        return index >= 0 && "ship".equals(arguments.get(index));
    }

    private static boolean isKitVersionOption(String argument) {
        return "-V".equals(argument)
                || argument.startsWith("-V=")
                || "--version".equals(argument)
                || argument.startsWith("--version=");
    }

    private static boolean hasArgumentFile(Stack<String> arguments) {
        return arguments.stream().anyMatch(argument -> argument.length() > 1 && argument.charAt(0) == '@');
    }

    private static List<String> expandArgumentFiles(CommandLine commandLine, Stack<String> arguments) {
        List<String> ordered = new ArrayList<>(arguments);
        Collections.reverse(ordered);
        return new CommandLine(CommandLine.Model.CommandSpec.create())
                .setAtFileCommentChar(commandLine.getAtFileCommentChar())
                .setUseSimplifiedAtFiles(commandLine.isUseSimplifiedAtFiles())
                .setStopAtUnmatched(true)
                .parseArgs(ordered.toArray(String[]::new))
                .expandedArgs();
    }

    private static void replace(Stack<String> arguments, List<String> expanded) {
        arguments.clear();
        for (int index = expanded.size() - 1; index >= 0; index--) {
            arguments.push(expanded.get(index));
        }
    }
}

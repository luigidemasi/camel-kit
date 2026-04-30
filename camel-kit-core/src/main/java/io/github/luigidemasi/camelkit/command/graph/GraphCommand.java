package io.github.luigidemasi.camelkit.command.graph;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "graph",
         description = "Project graph analysis, generation, and visualization",
         subcommands = {
                 GraphStatsCommand.class, GraphFindCommand.class, GraphNeighborsCommand.class,
                 GraphPathCommand.class, GraphSubgraphCommand.class, GraphRouteFlowCommand.class,
                 GraphImpactCommand.class, GraphRouteTopologyCommand.class, GraphDeadCodeCommand.class,
                 GraphProjectNormsCommand.class, GraphProjectContextCommand.class, GraphRouteContextCommand.class,
                 GraphGenerateCommand.class, GraphVisualizeCommand.class,
                 CommandLine.HelpCommand.class
         })
public class GraphCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}

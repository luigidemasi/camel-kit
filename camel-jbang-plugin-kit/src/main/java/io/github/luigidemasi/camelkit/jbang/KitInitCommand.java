package io.github.luigidemasi.camelkit.jbang;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.command.InitCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Adapter command for `camel kit init` - delegates to camel-kit-core InitCommand
 */
@Command(name = "init", description = "Initialize a new Camel-Kit project")
public class KitInitCommand extends CamelCommand {

    @Parameters(index = "0", description = "Project name", arity = "0..1")
    String projectName;

    @Option(names = {"-a", "--ai"}, description = "AI agent: bob, gemini, claude",
            defaultValue = "bob")
    String ai;

    @Option(names = {"--here"}, description = "Initialize in current directory")
    boolean here;

    @Option(names = {"-v", "--camel-version"},
            description = "Camel version (use 'default' for bundled catalog)",
            defaultValue = "default")
    String camelVersion;

    @Option(names = {"--citrus-version"},
            description = "Citrus version for test schemas",
            defaultValue = "default")
    String citrusVersion;

    @Option(names = {"--no-fetch"}, description = "Skip external catalog fetching")
    boolean noFetch;

    public KitInitCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // Create a camel-kit-core main instance.
        // TUI is enabled; if the backend is unavailable in this context,
        // InitCommand catches the exception and falls back to normal mode.
        CamelKitMain camelKitMain = new CamelKitMain();

        // Create the InitCommand and set its parameters
        InitCommand initCommand = new InitCommand(camelKitMain);
        initCommand.projectName = this.projectName;
        initCommand.ai = this.ai;
        initCommand.here = this.here;
        initCommand.camelVersion = this.camelVersion;
        initCommand.citrusVersion = this.citrusVersion;
        initCommand.noFetch = this.noFetch;

        // Execute the command
        return initCommand.call();
    }
}

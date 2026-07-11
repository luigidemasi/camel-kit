package io.github.luigidemasi.camelkit.jbang;

import java.nio.file.Path;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.command.InitCommand;

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

    @Option(names = {"-a", "--ai"},
            description = "AI agent: bob2 (IBM Bob 2, default), bob (IBM Bob 1 legacy), "
                          + "gemini, claude, codex, copilot, pi, qwen, opencode",
            defaultValue = "bob2")
    String ai;

    @Option(names = {"--here"}, description = "Initialize in current directory")
    boolean here;

    @Option(names = {"--citrus-version"},
            description = "Citrus version for test schemas",
            defaultValue = "default")
    String citrusVersion;

    @Option(names = {"--no-fetch"}, description = "Skip external catalog fetching")
    boolean noFetch;

    @Option(names = {"--force"}, description = "Overwrite existing project without prompting")
    boolean force;

    @Option(names = {"--silent"}, description = "Suppress all output (no banner, no progress, no summary)")
    boolean silent;

    @Option(names = {"-c", "--config"},
            description = "Path to config properties file (default: ~/.camel-kit/config.properties)")
    Path configFile;

    @Option(names = {"-p", "--property"}, arity = "1..*",
            description = "Override config property: -p key=value (repeatable)")
    List<String> properties;

    @Option(names = {"--source-platform"},
            description = "Source platform for migration graph analysis: mulesoft, camel, biztalk, auto (default: auto)")
    String sourcePlatform;

    private final CamelKitMain camelKitMain;

    public KitInitCommand(CamelJBangMain main) {
        this(main, new CamelKitMain());
    }

    public KitInitCommand(CamelJBangMain main, CamelKitMain camelKitMain) {
        super(main);
        this.camelKitMain = camelKitMain;
        this.camelKitMain.disableTui();
    }

    @Override
    public Integer doCall() throws Exception {
        InitCommand initCommand = new InitCommand(camelKitMain);
        initCommand.projectName = this.projectName;
        initCommand.ai = this.ai;
        initCommand.here = this.here;
        initCommand.citrusVersion = this.citrusVersion;
        initCommand.noFetch = this.noFetch;
        initCommand.force = this.force;
        initCommand.silent = this.silent;
        initCommand.configFile = this.configFile;
        initCommand.properties = this.properties;
        initCommand.sourcePlatform = this.sourcePlatform;

        return initCommand.call();
    }
}

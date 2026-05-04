package io.github.luigidemasi.camelkit.jbang;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

import io.github.luigidemasi.camelkit.VersionProvider;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Base command for `camel kit` - displays usage information
 */
@Command(name = "kit",
         mixinStandardHelpOptions = true,
         versionProvider = VersionProvider.class,
         description = "Design Apache Camel Integrations with AI")
public class KitCommand extends CamelCommand {

    public KitCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // When 'camel kit' is called without subcommand, show help
        new CommandLine(this).usage(System.out);
        return 0;
    }
}

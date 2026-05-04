package io.github.luigidemasi.camelkit.jbang;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Base command for `camel kit` - displays usage information
 */
@Command(name = "kit", versionProvider = KitVersionProvider.class,
         description = "Design Apache Camel Integrations with AI")
public class KitCommand extends CamelCommand {

    @CommandLine.Option(names = {"-V", "--version"}, description = "Print version information")
    private boolean versionRequested;

    public KitCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (versionRequested) {
            for (String v : new KitVersionProvider().getVersion()) {
                System.out.println(v);
            }
            return 0;
        }
        new CommandLine(this).usage(System.out);
        return 0;
    }
}

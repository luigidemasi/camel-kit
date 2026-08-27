package io.github.luigidemasi.camelkit;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

import io.github.luigidemasi.camelkit.jbang.CamelKitPlugin;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CamelKitCommandParityTest {

    @Test
    void pluginRegistersStandaloneCommandSurface() {
        CamelJBangMain main = new CamelJBangMain();
        CommandLine pluginRoot = new CommandLine(main);
        new CamelKitPlugin().customize(pluginRoot, main);

        assertEquals(CamelKitMain.commandLine(new CamelKitMain()).getSubcommands().keySet(),
                pluginRoot.getSubcommands().get("kit").getSubcommands().keySet());
    }
}

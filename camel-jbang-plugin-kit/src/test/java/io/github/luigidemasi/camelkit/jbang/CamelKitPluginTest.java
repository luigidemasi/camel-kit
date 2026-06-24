package io.github.luigidemasi.camelkit.jbang;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class CamelKitPluginTest {

    @Test
    void registersDoctorUnderKitCommand() {
        CamelJBangMain main = new CamelJBangMain();
        CommandLine root = new CommandLine(main);

        new CamelKitPlugin().customize(root, main);

        CommandLine kit = root.getSubcommands().get("kit");
        assertNotNull(kit);
        assertTrue(kit.getSubcommands().containsKey("init"));
        assertTrue(kit.getSubcommands().containsKey("doctor"));
        assertTrue(kit.getSubcommands().containsKey("graph"));
        assertTrue(kit.getSubcommands().containsKey("plan"));
    }
}

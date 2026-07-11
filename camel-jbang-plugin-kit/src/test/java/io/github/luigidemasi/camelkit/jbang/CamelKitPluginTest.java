package io.github.luigidemasi.camelkit.jbang;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class CamelKitPluginTest {

    @Test
    void kitInitDefaultsToBob2WhenAgentOptionIsOmitted() {
        KitInitCommand command = new KitInitCommand(new CamelJBangMain());

        new CommandLine(command).parseArgs("orders");

        assertEquals("bob2", command.ai);
    }

    @Test
    void kitInitAcceptsCodexAndShowsItInHelp() {
        KitInitCommand command = new KitInitCommand(new CamelJBangMain());
        CommandLine commandLine = new CommandLine(command);

        commandLine.parseArgs("orders", "--ai", "codex");

        assertEquals("codex", command.ai);
        assertTrue(commandLine.getUsageMessage().contains("codex"));
    }

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

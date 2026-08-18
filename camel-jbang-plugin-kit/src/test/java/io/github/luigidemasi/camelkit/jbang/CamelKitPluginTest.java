package io.github.luigidemasi.camelkit.jbang;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

import io.github.luigidemasi.camelkit.command.ship.ShipCommand;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class CamelKitPluginTest {

    @TempDir
    Path tempDir;

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

    @Test
    void registersShipWithQualifiedHelpAndPreservesCamelArgumentFiles() throws Exception {
        Path arguments = Files.writeString(tempDir.resolve("ship-arguments"), "EXPANDED");
        String literal = "@" + arguments.toAbsolutePath();
        CamelJBangMain main = new CamelJBangMain();
        CommandLine root = new CommandLine(main);
        new CamelKitPlugin().customize(root, main);
        CommandLine ship = root.getSubcommands().get("kit").getSubcommands().get("ship");
        StringWriter output = new StringWriter();
        root.setOut(new PrintWriter(output, true));

        assertTrue(root.isExpandAtFiles());
        assertInstanceOf(ShipCommand.class, ship.getCommand());
        assertEquals("camel kit ship", ship.getCommandSpec().qualifiedName());
        CommandLine.ParseResult parsed = root.parseArgs("kit", "ship", "--text=" + literal);
        assertEquals(literal,
                parsed.subcommand().subcommand().matchedOptionValue("--text", null));
        assertEquals(0, root.execute("kit", "ship", "--help"));
        assertTrue(output.toString().contains("Usage: camel kit ship"), output.toString());
    }
}

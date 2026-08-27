package io.github.luigidemasi.camelkit.jbang;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

import io.github.luigidemasi.camelkit.command.doc.DocCommand;
import io.github.luigidemasi.camelkit.command.pipeline.NextIdCommand;
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
    void registersDocAndNextIdUnderKitCommand() {
        CamelJBangMain main = new CamelJBangMain();
        CommandLine root = new CommandLine(main);

        new CamelKitPlugin().customize(root, main);

        CommandLine kit = root.getSubcommands().get("kit");
        assertNotNull(kit);
        assertInstanceOf(DocCommand.class, kit.getSubcommands().get("doc").getCommand());
        assertInstanceOf(NextIdCommand.class, kit.getSubcommands().get("nextId").getCommand());
    }

    @Test
    void preservesLiteralShipContextAndCamelArgumentFiles() throws Exception {
        Path context = Files.writeString(tempDir.resolve("ship-context"), "EXPANDED VALUE");
        Path arguments = Files.writeString(
                tempDir.resolve("kit-arguments"), "kit init orders --ai codex");
        String literal = "@" + context.toAbsolutePath();
        CamelJBangMain main = new CamelJBangMain();
        CommandLine root = new CommandLine(main);
        new CamelKitPlugin().customize(root, main);
        CommandLine ship = root.getSubcommands().get("kit").getSubcommands().get("ship");
        StringWriter output = new StringWriter();
        root.setOut(new PrintWriter(output, true));

        assertInstanceOf(ShipCommand.class, ship.getCommand());
        assertEquals("camel kit ship", ship.getCommandSpec().qualifiedName());
        CommandLine.ParseResult parsed = root.parseArgs("kit", "ship", "--text", literal);
        assertEquals(literal,
                parsed.subcommand().subcommand().matchedOptionValue("--text", null));
        CommandLine.ParseResult document = root.parseArgs("kit", "ship", "--document", literal);
        assertEquals(Path.of(literal),
                document.subcommand().subcommand().matchedOptionValue("--document", null));
        CommandLine.ParseResult escaped = root.parseArgs("kit", "ship", "--text", "@" + literal);
        assertEquals(literal,
                escaped.subcommand().subcommand().matchedOptionValue("--text", null));
        CommandLine.ParseResult parentOption = root.parseArgs("kit", "--version=true", "ship", "--text", literal);
        assertEquals(literal,
                parentOption.subcommand().subcommand().matchedOptionValue("--text", null));

        CommandLine.ParseResult expanded = root.parseArgs("@" + arguments.toAbsolutePath());
        CommandLine.ParseResult init = expanded.subcommand().subcommand();
        assertEquals("orders", init.matchedPositionalValue(0, null));
        assertEquals("codex", init.matchedOptionValue("--ai", null));

        assertEquals(0, root.execute("kit", "ship", "--help"));
        assertTrue(output.toString().contains("Usage: camel kit ship"), output.toString());
    }
}

package io.github.luigidemasi.camelkit;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelKitMainShipTest {

    @TempDir
    Path tempDir;

    @Test
    void registersShipWithQualifiedHelp() {
        CamelKitMain main = new CamelKitMain();
        try {
            CommandLine root = CamelKitMain.commandLine(main, "ship", "--help");
            CommandLine ship = root.getSubcommands().get("ship");
            StringWriter output = new StringWriter();
            root.setOut(new PrintWriter(output, true));

            assertNotNull(ship);
            assertEquals("camel-kit ship", ship.getCommandSpec().qualifiedName());
            assertFalse(root.isExpandAtFiles());
            assertEquals(0, root.execute("ship", "--help"));
            assertTrue(output.toString().contains("Usage: camel-kit ship"), output.toString());
            assertTrue(output.toString().contains("-c, --config"), output.toString());
            assertTrue(output.toString().contains("-p, --property"), output.toString());
        } finally {
            main.closeTerminal();
        }
    }

    @Test
    void preservesLiteralAtContextForShip() throws Exception {
        Path arguments = Files.writeString(tempDir.resolve("ship-arguments"), "EXPANDED");
        String literal = "@" + arguments.toAbsolutePath();
        CamelKitMain main = new CamelKitMain();
        try {
            CommandLine root = CamelKitMain.commandLine(main, "ship", "--text", literal);

            CommandLine.ParseResult parsed = root.parseArgs("ship", "--text", literal);

            assertEquals(literal, parsed.subcommand().matchedOptionValue("--text", null));
            CommandLine.ParseResult document = root.parseArgs("ship", "--document", literal);
            assertEquals(Path.of(literal), document.subcommand().matchedOptionValue("--document", null));
        } finally {
            main.closeTerminal();
        }
    }

    @Test
    void retainsArgumentFileExpansionForOtherStandaloneInvocations() throws Exception {
        Path arguments = Files.writeString(tempDir.resolve("root-arguments"), "--version\n");
        CamelKitMain main = new CamelKitMain();
        try {
            CommandLine root = CamelKitMain.commandLine(main, "@" + arguments.toAbsolutePath());

            assertTrue(root.isExpandAtFiles());
            assertTrue(root.parseArgs("@" + arguments.toAbsolutePath()).isVersionHelpRequested());
        } finally {
            main.closeTerminal();
        }
    }
}

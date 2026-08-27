package io.github.luigidemasi.camelkit.command;

import java.nio.file.Path;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.generator.InvalidAgentConfigurationException;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.service.InitRequest;
import io.github.luigidemasi.camelkit.service.InitResult;
import io.github.luigidemasi.camelkit.service.InitService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class InitCommandTest {

    @Test
    void defaultsToBob2WhenAgentOptionIsOmitted() {
        InitCommand command = new InitCommand(new CamelKitMain());

        new CommandLine(command).parseArgs("orders");

        assertEquals("bob2", command.ai);
    }

    @Test
    void nextStepsUseCurrentSkillRouterCommands() {
        CapturingPrinter printer = new CapturingPrinter();
        CamelKitMain main = new CamelKitMain();
        main.setOut(printer);

        InitCommand command = new InitCommand(main);
        command.printNextSteps("orders", AgentRegistry.get("claude").name(), "claude");

        String output = printer.output();
        assertTrue(output.contains("/camel-start"));
        assertTrue(output.contains("/camel-migrate"));
        assertTrue(output.contains("/camel-debug"));
        assertTrue(output.contains("/camel-knowledge"));
        assertFalse(output.contains("/camel-design"));
        assertFalse(output.contains("/camel-verify"));
    }

    @Test
    void copilotNextStepsUseSkillsInsteadOfCamelSlashCommands() {
        CapturingPrinter printer = new CapturingPrinter();
        CamelKitMain main = new CamelKitMain();
        main.setOut(printer);

        InitCommand command = new InitCommand(main);
        command.printNextSteps("orders", "Renamed Copilot Display", "copilot");

        String output = printer.output();
        assertTrue(output.contains("Use the /camel-start skill"));
        assertTrue(output.contains("/skills list"));
        assertTrue(output.contains("/mcp show"));
        assertFalse(output.contains("select camel-start"));
    }

    @Test
    void piNextStepsUseTrustAndSkillInvocation() {
        CapturingPrinter printer = new CapturingPrinter();
        CamelKitMain main = new CamelKitMain();
        main.setOut(printer);

        InitCommand command = new InitCommand(main);
        command.printNextSteps("orders", "Pi", "pi");

        String output = printer.output();
        assertTrue(output.contains("pi install npm:pi-mcp-adapter@2.11.0"));
        assertTrue(output.contains("/trust"));
        assertTrue(output.contains("/skill:camel-start"));
        assertTrue(output.contains("/mcp status"));
        assertFalse(output.contains("/camel-start  "));
    }

    @Test
    void codexOptionIsAcceptedAndShownInHelp() {
        InitCommand command = new InitCommand(new CamelKitMain());
        CommandLine commandLine = new CommandLine(command);

        commandLine.parseArgs("orders", "--ai", "codex");

        assertEquals("codex", command.ai);
        assertTrue(commandLine.getUsageMessage().contains("codex"));
    }

    @Test
    void codexNextStepsUseTrustSkillsAndMcpDiscovery() {
        CapturingPrinter printer = new CapturingPrinter();
        CamelKitMain main = new CamelKitMain();
        main.setOut(printer);

        InitCommand command = new InitCommand(main);
        command.printNextSteps("orders", "OpenAI Codex CLI", "codex");

        String output = printer.output();
        assertTrue(output.contains("trust"));
        assertTrue(output.contains("/skills"));
        assertTrue(output.contains("$camel-start"));
        assertTrue(output.contains("/mcp"));
        assertFalse(output.contains("/camel-start"));
    }

    @Test
    void invalidAgentConfigurationPrintsOneErrorLine(@TempDir Path tempDir) throws Exception {
        CapturingPrinter printer = new CapturingPrinter();
        CamelKitMain main = new CamelKitMain() {
            @Override
            public void printBanner() {
                // Keep this command-level error-path test independent of terminal image support.
            }
        };
        main.disableTui();
        main.setOut(printer);
        InitService failingService = new InitService() {
            @Override
            public InitResult initialize(InitRequest request) throws Exception {
                throw new InvalidAgentConfigurationException(
                        "existing opencode.json is not valid JSON or JSONC");
            }
        };
        InitCommand command = new InitCommand(main, failingService);
        command.projectName = tempDir.resolve("orders").toString();
        command.ai = "opencode";
        command.citrusVersion = "default";
        command.noFetch = true;

        int exitCode = command.doCall();

        assertEquals(1, exitCode);
        assertEquals(1, printer.output().lines()
                .filter(line -> line.contains("existing opencode.json"))
                .count());
        assertFalse(printer.output().contains("InvalidAgentConfigurationException"));
        assertFalse(printer.output().contains("\tat "));
    }

    private static final class CapturingPrinter implements Printer {
        private final StringBuilder output = new StringBuilder();

        @Override
        public void println() {
            output.append(System.lineSeparator());
        }

        @Override
        public void println(String line) {
            output.append(line).append(System.lineSeparator());
        }

        @Override
        public void print(String value) {
            output.append(value);
        }

        String output() {
            return output.toString();
        }
    }
}

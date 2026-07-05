package io.github.luigidemasi.camelkit.command;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;

import org.junit.jupiter.api.Test;
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

package io.github.luigidemasi.camelkit.command;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.output.Printer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InitCommandTest {

    @Test
    void nextStepsUseCurrentSkillRouterCommands() {
        CapturingPrinter printer = new CapturingPrinter();
        CamelKitMain main = new CamelKitMain();
        main.setOut(printer);

        InitCommand command = new InitCommand(main);
        command.printNextSteps("orders", "Claude Code");

        String output = printer.output();
        assertTrue(output.contains("/camel-start"));
        assertTrue(output.contains("/camel-migrate"));
        assertTrue(output.contains("/camel-debug"));
        assertTrue(output.contains("/camel-knowledge"));
        assertFalse(output.contains("/camel-design"));
        assertFalse(output.contains("/camel-verify"));
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

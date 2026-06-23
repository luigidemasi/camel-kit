package io.github.luigidemasi.camelkit.util;

import io.github.luigidemasi.camelkit.output.Printer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrerequisiteCheckerTest {

    @Test
    void checkDoesNotThrow() {
        assertDoesNotThrow(() -> PrerequisiteChecker.check(Printer.noop()));
    }

    @Test
    void missingCamelTestPluginHintDoesNotAdvertiseInternalVerifyCommand() {
        String hint = PrerequisiteChecker.missingCamelTestPluginHint();
        assertTrue(hint.contains("runtime verification"));
        assertFalse(hint.contains("/camel-verify"));
    }

    @Test
    void parseJavaVersionModernFormat() {
        String output = "openjdk version \"21.0.3\" 2024-04-16 LTS\n"
                        + "OpenJDK Runtime Environment (build 21.0.3+9-LTS)\n"
                        + "OpenJDK 64-Bit Server VM (build 21.0.3+9-LTS, mixed mode, sharing)";
        assertEquals("21.0.3", PrerequisiteChecker.parseJavaVersion(output));
    }

    @Test
    void parseJavaVersionOldFormat() {
        String output = "java version \"1.8.0_401\"";
        assertEquals("1.8.0_401", PrerequisiteChecker.parseJavaVersion(output));
    }

    @Test
    void parseMajorVersionModern() {
        assertEquals(21, PrerequisiteChecker.parseMajorVersion("21.0.3"));
        assertEquals(17, PrerequisiteChecker.parseMajorVersion("17.0.1"));
    }

    @Test
    void parseMajorVersionOldStyle() {
        assertEquals(8, PrerequisiteChecker.parseMajorVersion("1.8.0_401"));
    }

    @Test
    void parseJavaVersionReturnsNullOnGarbage() {
        assertNull(PrerequisiteChecker.parseJavaVersion("not a version string"));
    }
}

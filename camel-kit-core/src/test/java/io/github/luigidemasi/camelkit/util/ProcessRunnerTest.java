package io.github.luigidemasi.camelkit.util;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessRunnerTest {

    @Test
    void capturesSuccessfulCommandOutput() {
        ProcessRunner.Result result = ProcessRunner.run(
                Duration.ofSeconds(5),
                javaExecutable().toString(),
                "-version");

        assertTrue(result.succeeded());
        assertTrue(result.output().contains("version"));
    }

    private Path javaExecutable() {
        String binary = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", binary);
    }
}

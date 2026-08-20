package io.github.luigidemasi.camelkit.ship.expression;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The expression gate is embedded into the validation payload archive and re-executed on the resolved toolchain JVM, so
 * it must link nothing outside {@code java.base}.
 */
class ShipExpressionPolicyCapabilityTest {

    private static final Pattern DEPENDENCY = Pattern.compile("^\\s+(\\S+)\\s+->\\s+(\\S+)\\s+(.+)$");
    private static final String EXPRESSION_PACKAGE = "io.github.luigidemasi.camelkit.ship.expression";

    @Test
    void compiledExpressionGateLinksOnlyJavaBase() throws Exception {
        Path classes = Path.of(System.getProperty("basedir"), "target", "classes");
        Path expression = classes.resolve("io/github/luigidemasi/camelkit/ship/expression");
        Process process = new ProcessBuilder(
                jdkTool("jdeps"), "--multi-release", "17", "-verbose:class", expression.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);

        int dependencies = 0;
        for (String line : output.lines().toList()) {
            Matcher dependency = DEPENDENCY.matcher(line);
            if (!dependency.matches() || !dependency.group(1).startsWith(EXPRESSION_PACKAGE)) {
                continue;
            }
            dependencies++;
            String target = dependency.group(2);
            String module = dependency.group(3).trim();
            assertTrue(target.startsWith(EXPRESSION_PACKAGE) || "java.base".equals(module),
                    () -> "Expression gate added a dependency outside java.base: " + target + ":\n" + output);
        }
        assertTrue(dependencies > 0, "jdeps did not report expression class dependencies:\n" + output);
    }

    private static String jdkTool(String name) throws IOException {
        Path tool = Path.of(System.getProperty("java.home"), "bin", name);
        if (!tool.toFile().canExecute()) {
            throw new IOException("Required JDK tool is unavailable: " + name);
        }
        return tool.toString();
    }
}

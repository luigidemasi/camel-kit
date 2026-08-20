package io.github.luigidemasi.camelkit.ship.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipCatalogBoundaryTest {

    private static final String CATALOG_PACKAGE = "io.github.luigidemasi.camelkit.ship.catalog.";
    private static final Pattern DEPENDENCY = Pattern.compile("^\\s+(\\S+)\\s+->\\s+(\\S+)\\s+(.+)$");
    private static final Set<String> ALLOWED_JDK_MODULES = Set.of("java.base");
    private static final Set<String> ALLOWED_EXTERNAL_TYPES = Set.of(
            "com.fasterxml.jackson.core.JsonFactory",
            "com.fasterxml.jackson.core.StreamReadConstraints",
            "com.fasterxml.jackson.core.StreamReadConstraints$Builder",
            "com.fasterxml.jackson.core.StreamReadFeature",
            "com.fasterxml.jackson.core.TSFBuilder",
            "com.fasterxml.jackson.databind.DeserializationFeature",
            "com.fasterxml.jackson.databind.JsonNode",
            "com.fasterxml.jackson.databind.ObjectMapper",
            "com.fasterxml.jackson.databind.ObjectReader",
            "com.fasterxml.jackson.databind.node.TextNode",
            "com.fasterxml.jackson.dataformat.yaml.YAMLFactory",
            "com.fasterxml.jackson.dataformat.yaml.YAMLFactoryBuilder",
            "io.github.luigidemasi.camelkit.ship.ShipDigest",
            "io.github.luigidemasi.camelkit.ship.expression.ShipExpressionPolicy",
            "io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate",
            "io.github.luigidemasi.camelkit.ship.resolver.ResolvedExactMavenArtifact",
            "io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver",
            "io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver$ResolutionMode");

    @Test
    void compiledCatalogUsesOnlyApprovedModulesAndExactExternalTypes()
            throws Exception {
        Path classes = Path.of(System.getProperty("basedir"), "target", "classes");
        Path catalog = classes.resolve("io/github/luigidemasi/camelkit/ship/catalog");
        Process process = new ProcessBuilder(
                jdkTool("jdeps"), "--multi-release", "17", "-verbose:class", catalog.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);

        int dependencies = 0;
        for (String line : output.lines().toList()) {
            Matcher dependency = DEPENDENCY.matcher(line);
            if (!dependency.matches() || !dependency.group(1).startsWith(CATALOG_PACKAGE)) {
                continue;
            }
            dependencies++;
            String target = dependency.group(2);
            String module = dependency.group(3).trim();
            if (target.startsWith("java.")) {
                assertTrue(ALLOWED_JDK_MODULES.contains(module),
                        () -> "Catalog code added JDK dependency " + target + " from " + module + ":\n" + output);
            } else {
                assertTrue(ALLOWED_EXTERNAL_TYPES.contains(target),
                        () -> "Catalog code added unapproved external dependency " + target + ":\n" + output);
            }
        }
        assertTrue(dependencies > 0, "jdeps did not report catalog class dependencies:\n" + output);
        assertTrue(output.contains(MavenCoordinate.class.getName()),
                "jdeps did not observe the intended public resolver edge:\n" + output);
    }

    private static String jdkTool(String name) throws IOException {
        Path tool = Path.of(System.getProperty("java.home"), "bin", name);
        if (!tool.toFile().canExecute()) {
            throw new IOException("Required JDK tool is unavailable: " + name);
        }
        return tool.toString();
    }
}

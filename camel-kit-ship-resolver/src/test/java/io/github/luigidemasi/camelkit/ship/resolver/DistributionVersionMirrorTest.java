package io.github.luigidemasi.camelkit.ship.resolver;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistributionVersionMirrorTest {

    private static final Pattern MIRROR_BLOCK = Pattern.compile(
            "<!-- BEGIN distribution property mirrors(?s)(.*?)<!-- END distribution property mirrors -->");
    private static final Pattern PROPERTY = Pattern.compile("<([a-z][a-z0-9.-]*)>([^<]+)</\\1>");
    private static final Set<String> REQUIRED_RESOLVER_MIRRORS = Set.of(
            "maven.resolver.version",
            "maven.provider.version",
            "maven.jar.plugin.version",
            "maven.shade.plugin.version",
            "maven.surefire.plugin.version",
            "slf4j.version");

    @Test
    void mavenModelLiteralsMatchPackagedDistributionPolicy() throws IOException {
        Path root = projectRoot();
        String pom = Files.readString(root.resolve("pom.xml"));
        Matcher block = MIRROR_BLOCK.matcher(pom);
        assertTrue(block.find(), "Root POM lacks the marked distribution property mirror block");
        String mirrorProperties = block.group(1);
        assertFalse(block.find(), "Root POM contains more than one distribution property mirror block");

        Map<String, String> pomValues = new LinkedHashMap<>();
        Matcher property = PROPERTY.matcher(mirrorProperties);
        while (property.find()) {
            assertNull(pomValues.put(property.group(1), property.group(2).trim()), property.group(1));
        }
        assertFalse(pomValues.isEmpty(), "Root POM distribution property mirror block is empty");
        assertTrue(
                pomValues.keySet().containsAll(REQUIRED_RESOLVER_MIRRORS),
                "Root POM distribution property mirror block lacks a required resolver version");

        Properties distribution = new Properties();
        try (Reader reader = Files.newBufferedReader(root.resolve("distribution.properties"))) {
            distribution.load(reader);
        }
        for (Map.Entry<String, String> entry : pomValues.entrySet()) {
            assertEquals(distribution.getProperty(entry.getKey()), entry.getValue(), entry.getKey());
        }
    }

    private static Path projectRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("distribution.properties"))
                    && Files.isRegularFile(candidate.resolve("pom.xml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new AssertionError("Could not locate the project root from " + Path.of("").toAbsolutePath());
    }
}

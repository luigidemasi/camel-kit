package io.github.luigidemasi.camelkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JBangLauncherReleaseTest {

    private static final Pattern PROJECT_VERSION = Pattern.compile(
            "<artifactId>camel-kit</artifactId>\\s*<version>([^<]+)</version>");
    private static final Pattern LAUNCHER_VERSION = Pattern.compile("camel[.]kit[.]version:([^}]+)");
    private static final List<String> LAUNCHERS = List.of(
            "camel-kit-main/src/main/jbang/main/CamelKit.java",
            "camel-kit-main/dist/CamelKit.java");

    @Test
    void trackedLaunchersFollowReleaseAndDevelopmentVersions() throws IOException {
        Path root = repositoryRoot();
        String pom = Files.readString(root.resolve("pom.xml"));
        String projectVersion = matchedValue(PROJECT_VERSION, pom, "root project version");

        for (String launcher : LAUNCHERS) {
            String launcherVersion = matchedValue(
                    LAUNCHER_VERSION,
                    Files.readString(root.resolve(launcher)),
                    launcher + " fallback version");
            assertEquals(projectVersion, launcherVersion, launcher);
            assertTrue(
                    pom.contains("file=\"${maven.multiModuleProjectDirectory}/" + launcher + "\""),
                    "The release build must synchronize " + launcher);
            assertTrue(
                    pom.contains("<arg value=\"" + launcher + "\" />"),
                    "The release build must stage " + launcher);
        }

        assertTrue(pom.contains("<id>sync-jbang-launcher-versions</id>"),
                "The release build must synchronize both tracked launchers");
        assertTrue(pom.contains("<id>stage-jbang-launcher-versions</id>"),
                "The release build must stage both tracked launchers for Maven's scoped SCM commit");
        assertTrue(pom.contains("<arg value=\"-f\" />"),
                "The ignored distribution directory requires forced staging");
        assertTrue(
                pom.contains(
                        "<preparationGoals>antrun:run@sync-jbang-launcher-versions clean verify antrun:run@stage-jbang-launcher-versions</preparationGoals>"),
                "The release-version commit must include synchronized launchers");
        assertTrue(
                pom.contains(
                        "<completionGoals>-N antrun:run@sync-jbang-launcher-versions antrun:run@stage-jbang-launcher-versions</completionGoals>"),
                "The next-development-version commit must rerun launcher synchronization");
    }

    private static String matchedValue(Pattern pattern, String content, String description) {
        Matcher matcher = pattern.matcher(content);
        assertTrue(matcher.find(), "Could not find " + description);
        return matcher.group(1);
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("camel-kit-main"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new AssertionError("Could not locate the project root from " + Path.of("").toAbsolutePath());
    }
}

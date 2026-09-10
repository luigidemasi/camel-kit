package io.github.luigidemasi.camelkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

import io.github.luigidemasi.camelkit.jbang.CamelKitPlugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelKitCommandParityTest {

    private static final Set<String> PUBLIC_SKILLS
            = Set.of("camel-start", "camel-brainstorm", "camel-migrate", "camel-plan",
                    "camel-execute", "camel-validate", "camel-ship", "camel-knowledge", "camel-debug");

    @TempDir
    Path tempDir;

    @Test
    void bob2InitAndRegenerationExposeTheSamePublicSkills() throws Exception {
        CamelKitMain standaloneMain = new CamelKitMain();
        standaloneMain.disableTui();
        CommandLine standalone = CamelKitMain.commandLine(standaloneMain);
        CamelJBangMain main = new CamelJBangMain();
        CommandLine plugin = new CommandLine(main);
        new CamelKitPlugin().customize(plugin, main);
        Path standaloneDir = tempDir.resolve("standalone");
        Path pluginDir = tempDir.resolve("plugin");

        for (int initialization = 0; initialization < 2; initialization++) {
            assertEquals(0, standalone.execute("init", standaloneDir.toString(), "--ai", "bob2",
                    "--silent", "--no-fetch", "--force"));
            assertEquals(0, plugin.execute("kit", "init", pluginDir.toString(), "--ai", "bob2",
                    "--silent", "--no-fetch", "--force"));
            for (String name : PUBLIC_SKILLS) {
                String relative = ".bob/skills/" + name + "/SKILL.md";
                String skill = Files.readString(standaloneDir.resolve(relative));
                String pluginSkill = Files.readString(pluginDir.resolve(relative));
                assertTrue(skill.contains("\nuser-invocable: true\n"), name);
                // Both entry points share this JVM's process-based CLI prefix detection.
                assertEquals(skill, pluginSkill, name);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"false,bob2", "true,bob2", "false,copilot", "true,copilot", "false,claude", "true,claude"})
    void nativeInitAndRegenerationDetectThePrefixInSeparateProcesses(boolean plugin, String agent) throws Exception {
        Path workspace = tempDir.resolve("workspace");
        String expectedPrefix = plugin ? "camel kit" : "camel-kit";
        // The argument file keeps dependency paths out of process-based prefix detection.
        Path arguments = tempDir.resolve(plugin ? "camel-jbang.args" : "camel-kit.args");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Files.writeString(arguments, "--class-path\n" + quoteJavaArgument(classpath) + "\n"
                                     + InitProcess.class.getName() + "\n" + plugin + "\n"
                                     + quoteJavaArgument(workspace.toString()) + "\n" + agent + "\n");

        for (int initialization = 0; initialization < 2; initialization++) {
            Path log = tempDir.resolve("init-" + initialization + ".log");
            Process process = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "@" + arguments)
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Initialization process timed out: " + log);
                assertEquals(0, process.exitValue(), Files.readString(log));
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }

            Properties config = new Properties();
            try (var input = Files.newInputStream(workspace.resolve(".camel-kit/config.properties"))) {
                config.load(input);
            }
            assertEquals(expectedPrefix, config.getProperty("project.command-prefix"));
            for (String name : PUBLIC_SKILLS) {
                String skillsRoot = switch (agent) {
                    case "bob2" -> ".bob/skills/";
                    case "copilot" -> ".github/skills/";
                    default -> ".claude/skills/";
                };
                String skill = Files.readString(workspace.resolve(skillsRoot + name + "/SKILL.md"));
                assertFalse(skill.contains("\nuser-invocable: false\n"), name);
                if (agent.equals("bob2")) {
                    assertTrue(skill.contains("\nuser-invocable: true\n"), name);
                }
                if (name.equals("camel-ship")) {
                    assertTrue(skill.contains(
                            expectedPrefix + " ship --backend " + agent + "-native --json"));
                }
            }
        }
    }

    private static String quoteJavaArgument(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static class InitProcess {
        public static void main(String[] args) {
            CommandLine command;
            boolean plugin = Boolean.parseBoolean(args[0]);
            var invocation
                    = new ArrayList<>(List.of("init", args[1], "--ai", args[2], "--silent", "--no-fetch", "--force"));
            if (plugin) {
                CamelJBangMain main = new CamelJBangMain();
                command = new CommandLine(main);
                new CamelKitPlugin().customize(command, main);
                invocation.add(0, "kit");
            } else {
                CamelKitMain main = new CamelKitMain();
                main.disableTui();
                command = CamelKitMain.commandLine(main);
            }
            System.exit(command.execute(invocation.toArray(String[]::new)));
        }
    }

    @Test
    void pluginRegistersStandaloneCommandSurface() {
        CamelJBangMain main = new CamelJBangMain();
        CommandLine pluginRoot = new CommandLine(main);
        new CamelKitPlugin().customize(pluginRoot, main);

        assertEquals(CamelKitMain.commandLine(new CamelKitMain()).getSubcommands().keySet(),
                pluginRoot.getSubcommands().get("kit").getSubcommands().keySet());
    }
}

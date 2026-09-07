package io.github.luigidemasi.camelkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

import io.github.luigidemasi.camelkit.jbang.CamelKitPlugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelKitCommandParityTest {

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
            for (String name : Set.of("camel-start", "camel-brainstorm", "camel-migrate", "camel-plan",
                    "camel-execute", "camel-validate", "camel-ship", "camel-knowledge", "camel-debug")) {
                String relative = ".bob/skills/" + name + "/SKILL.md";
                String skill = Files.readString(standaloneDir.resolve(relative));
                String pluginSkill = Files.readString(pluginDir.resolve(relative));
                assertTrue(skill.contains("\nuser-invocable: true\n"), name);
                // Both entry points share this JVM's process-based CLI prefix detection.
                assertEquals(skill, pluginSkill, name);
            }
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

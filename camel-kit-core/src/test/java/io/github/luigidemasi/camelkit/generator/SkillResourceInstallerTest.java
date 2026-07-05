package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class SkillResourceInstallerTest {

    @TempDir
    Path tempDir;

    @Test
    void copilotMetadataUsesHyphenatedUserInvocableAsValidAnchor() throws Exception {
        Path skill = tempDir.resolve(".github/skills/camel-implement/SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, """
                ---
                name: camel-implement
                user-invocable: false
                ---

                Body.
                """);

        new SkillResourceInstaller().addCopilotReadableInternalSkillMetadata(skill);

        String content = Files.readString(skill);
        assertTrue(content.contains("user-invocable: false"));
        assertTrue(content.contains("disable-model-invocation: true"));
        assertFalse(content.contains("user_invocable:"));
    }

    @Test
    void piMetadataMarksInternalSkillsAsHiddenFromModel() throws Exception {
        Path skill = tempDir.resolve(".pi/skills/camel-test/SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, """
                ---
                name: camel-test
                user_invocable: false
                ---

                Body.
                """);

        new SkillResourceInstaller().addPiReadableInternalSkillMetadata(skill);

        String content = Files.readString(skill);
        assertTrue(content.contains("user-invocable: false"));
        assertTrue(content.contains("disable-model-invocation: true"));
    }
}

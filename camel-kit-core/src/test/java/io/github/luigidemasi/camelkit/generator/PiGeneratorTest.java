package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class PiGeneratorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> INTERNAL_SKILLS = Set.of(
            "camel-design",
            "camel-implement",
            "camel-test",
            "camel-verify");

    @TempDir
    Path tempDir;

    private InitContext createContext() {
        AgentConfig agent = AgentRegistry.get("pi");
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(
                agent, "pi", commandsDir, skillsDir, tempDir,
                "camel-kit", Printer.noop());
    }

    @Test
    void generatesPiAgentsMdAndNativeProjectAssets() throws Exception {
        InitContext ctx = createContext();
        new PiGenerator().generate(ctx);

        String agentsMd = Files.readString(tempDir.resolve("AGENTS.md"));
        assertTrue(agentsMd.contains("initialized for Pi"));
        assertTrue(agentsMd.contains("/skill:camel-start"));
        assertTrue(agentsMd.contains(".pi/skills"));
        assertTrue(agentsMd.contains(".mcp.json"));
        assertTrue(agentsMd.contains("pi install npm:pi-mcp-adapter@2.11.0"));
        assertTrue(agentsMd.contains("pi -a -p"));
        assertFalse(agentsMd.contains("{COMMAND_PREFIX}"));
        assertFalse(agentsMd.contains("{PI_MCP_ADAPTER_VERSION}"));

        assertTrue(Files.isRegularFile(tempDir.resolve(".pi/extensions/camel-kit-guard.ts")));
        JsonNode policy = MAPPER.readTree(tempDir.resolve(".pi/camel-kit-guard-policy.json").toFile());
        assertEquals(1, policy.path("version").asInt());
        assertTrue(policy.path("rules").isArray());
    }

    @Test
    void installsEveryTemplateDeclaredByPiDescriptor() throws Exception {
        InitContext ctx = createContext();
        new PiGenerator().generate(ctx);

        AgentDescriptor descriptor = AgentRegistry.descriptor("pi");
        for (AgentDescriptor.TemplateInstall template : descriptor.templates()) {
            assertTrue(Files.isRegularFile(tempDir.resolve(template.target())),
                    "Missing generated Pi template target " + template.target());
        }
    }

    @Test
    void generatesProjectSkillsInPiSkillsDirectory() throws Exception {
        InitContext ctx = createContext();
        new PiGenerator().generate(ctx);

        assertTrue(Files.exists(tempDir.resolve(".pi/skills/camel-start/SKILL.md")));
        assertTrue(Files.exists(tempDir.resolve(".pi/skills/camel-execute/SKILL.md")));
        assertTrue(Files.exists(tempDir.resolve(".pi/skills/shared/iron-laws.md")));
    }

    @Test
    void marksInternalSkillsAsNotUserOrModelInvocableForPi() throws Exception {
        InitContext ctx = createContext();
        new PiGenerator().generate(ctx);

        for (String skillName : INTERNAL_SKILLS) {
            String skill = Files.readString(tempDir.resolve(".pi/skills/" + skillName + "/SKILL.md"));
            assertTrue(skill.contains("user-invocable: false"), skillName + " must declare future user hiding");
            assertTrue(skill.contains("disable-model-invocation: true"),
                    skillName + " must not be model invocable");
        }

        String start = Files.readString(tempDir.resolve(".pi/skills/camel-start/SKILL.md"));
        assertFalse(start.contains("disable-model-invocation: true"));
    }

    @Test
    void generatesPiMcpConfigWithDirectToolsSchema() throws Exception {
        InitContext ctx = createContext();
        new PiGenerator().generate(ctx);

        Path mcp = tempDir.resolve(".mcp.json");
        assertTrue(Files.exists(mcp));
        JsonNode root = MAPPER.readTree(mcp.toFile());
        JsonNode camel = root.path("mcpServers").path("camel");
        assertEquals("jbang", camel.path("command").asText());
        assertTrue(camel.path("directTools").isArray());
        assertTrue(camel.path("excludeTools").isArray());
        assertFalse(camel.has("autoApprove"));
        assertFalse(camel.has("alwaysAllow"));
        assertFalse(camel.has("tools"));

        JsonNode citrus = root.path("mcpServers").path("citrus");
        assertTrue(citrus.path("directTools").isArray());
    }

    @Test
    void guardPolicyBlocksRecursiveForceDeleteWithoutBlockingForceDeleteOfRegularFiles() throws Exception {
        InitContext ctx = createContext();
        new PiGenerator().generate(ctx);

        JsonNode rules = MAPPER.readTree(tempDir.resolve(".pi/camel-kit-guard-policy.json").toFile()).path("rules");
        JsonNode rmRule = null;
        for (JsonNode rule : rules) {
            if ("deny-recursive-force-delete".equals(rule.path("id").asText())) {
                rmRule = rule;
                break;
            }
        }
        assertNotNull(rmRule);
        Pattern pattern = Pattern.compile(rmRule.path("inputPattern").asText(), Pattern.CASE_INSENSITIVE);

        assertTrue(pattern.matcher("rm -rf target").find());
        assertTrue(pattern.matcher("rm -fr target").find());
        assertTrue(pattern.matcher("rm -vrf target").find());
        assertTrue(pattern.matcher("rm --one-file-system -Rf target").find());
        assertTrue(pattern.matcher("rm -r -f target").find());
        assertTrue(pattern.matcher("rm -f -r target").find());
        assertFalse(pattern.matcher("rm -f README.md").find());
        assertFalse(pattern.matcher("rm -f target/output.txt").find());
        assertFalse(pattern.matcher("rm -f foo-bar.txt").find());
        assertFalse(pattern.matcher("rm -f my-runner.log").find());
        assertFalse(pattern.matcher("rm -r some-conf").find());
    }

    @Test
    void generatedPromptTemplatesReferencePiSkills() throws Exception {
        InitContext ctx = createContext();
        new PiGenerator().generate(ctx);

        String content = Files.readString(ctx.commandsDir().resolve("camel-start.md"));
        assertTrue(content.contains(".pi/skills/camel-start/SKILL.md"));
        assertFalse(Files.exists(ctx.commandsDir().resolve("camel-ship.md")));
        assertTrue(Files.readString(ctx.skillsDir().resolve("camel-ship/SKILL.md"))
                .contains("camel-kit ship"));
    }

    @Test
    void doesNotGenerateCustomAgentsForPiV1() throws Exception {
        InitContext ctx = createContext();
        new PiGenerator().generate(ctx);

        assertFalse(Files.exists(tempDir.resolve(".pi/agents")));
        assertFalse(Files.exists(tempDir.resolve(".github/agents")));
    }
}

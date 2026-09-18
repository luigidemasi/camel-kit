package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.camel.main.Main;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class BeanConstructionGuidanceTest {

    @TempDir
    Path tempDir;

    static Stream<String> agents() {
        return AgentRegistry.names().stream().sorted();
    }

    @ParameterizedTest
    @MethodSource("agents")
    void generatedWorkflowsReachTheSameBeanPolicy(String agentName) throws Exception {
        AgentConfig agent = AgentRegistry.get(agentName);
        Path project = tempDir.resolve(agentName);
        Path skills = project.resolve(agent.skillsDirectory());
        Path commands = agent.generatesCommandStubs() ? project.resolve(agent.commandDirectory()) : skills;
        InitContext context = new InitContext(
                agent, agentName, commands, skills,
                project, "camel-kit", Printer.noop());
        new DefaultGenerator().generate(context);

        String policy = Files.readString(context.skillsDir().resolve("shared/forage.md"));
        int forage = policy.indexOf("1. **Forage-covered**");
        int component = policy.indexOf("2. **Not covered by Forage**");
        int custom = policy.indexOf("3. **Component requires an object");
        assertTrue(forage >= 0 && forage < component && component < custom);
        assertTrue(policy.contains("stop at the first rung that works"));
        assertTrue(policy.contains("Use Forage; do not generate a custom factory bean or script"));
        assertTrue(policy.contains("factoryBean: com.influxdb.client.InfluxDBClientFactory"));
        // Verify distributed fallback guidance, not runtime scripting or live-agent decisions.
        assertTrue(policy.contains("Accept a script that performs that initialization"));
        assertTrue(policy.contains("Report an evidence gap; do not invent a replacement"));
        assertTrue(policy.contains("Outside this policy; preserve the approved transformation engine"));

        for (String consumer : List.of(
                "camel-implement/SKILL.md",
                "camel-implement/guides/yaml-structure.md",
                "camel-implement/guides/properties-generation.md",
                "camel-implement/guides/route-validation.md",
                "camel-execute/guides/quality-reviewer-criteria.md",
                "camel-validate/guides/quality-checks.md")) {
            String content = Files.readString(context.skillsDir().resolve(consumer));
            assertTrue(content.contains("shared/forage.md"), agentName + ": " + consumer);
            assertTrue(content.contains("Custom Bean Construction"), agentName + ": " + consumer);
        }
        for (var template : AgentRegistry.descriptor(agentName).templates()) {
            if (template.source().equals("agents/code-quality-reviewer.md")) {
                assertTrue(Files.readString(project.resolve(template.target())).contains("Custom Bean Construction"),
                        agentName + ": reviewer persona");
            }
        }
    }

    @Test
    void shippedFactoryExampleConvertsArgumentsAndClosesTheBean() throws Exception {
        String policy;
        try (var resource = getClass().getResourceAsStream("/skills/shared/forage.md")) {
            assertNotNull(resource);
            policy = new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        int factory = policy.indexOf("factoryBean: com.influxdb.client.InfluxDBClientFactory");
        assertTrue(factory >= 0, "Missing InfluxDB factory example");
        int fence = policy.lastIndexOf("```yaml\n", factory);
        assertTrue(fence >= 0, "Factory example must be in a YAML code block");
        int start = fence + "```yaml\n".length();
        int end = policy.indexOf("```", start);
        assertTrue(end > factory, "Factory declaration must be inside the YAML code block");
        // Exercise the shipped YAML with a local factory having the same signature, without a database dependency.
        String yaml = policy.substring(start, end)
                .replace("com.influxdb.client.InfluxDBClientFactory", ClientFactory.class.getName())
                .replace("com.influxdb.client.InfluxDBClient", Client.class.getName());
        Path route = tempDir.resolve("client.camel.yaml");
        Files.writeString(route, yaml);
        Properties properties = new Properties();
        properties.setProperty("influxdb.url", "http://localhost:1");
        properties.setProperty("influxdb.token", "dummy-token==");
        properties.setProperty("influxdb.org", "test-org");
        properties.setProperty("influxdb.bucket", "test-bucket");
        Main main = new Main();
        Client client;
        try {
            main.setInitialProperties(properties);
            main.configure().withRoutesIncludePattern("file:" + route);
            main.start();
            client = main.getCamelContext().getRegistry().lookupByNameAndType("influxDbClient", Client.class);
            assertNotNull(client);
            assertEquals("http://localhost:1", client.url);
            assertArrayEquals("dummy-token==".toCharArray(), client.token);
            assertEquals("test-org", client.org);
            assertEquals("test-bucket", client.bucket);
            assertFalse(client.closed);
        } finally {
            main.stop();
        }
        assertTrue(client.closed);
    }

    public static final class ClientFactory {
        private ClientFactory() {
            throw new AssertionError("A static factory must not be instantiated");
        }

        public static Client create(String url, char[] token, String org, String bucket) {
            return new Client(url, token, org, bucket);
        }
    }

    public static final class Client {
        private final String url;
        private final char[] token;
        private final String org;
        private final String bucket;
        private boolean closed;

        private Client(String url, char[] token, String org, String bucket) {
            this.url = url;
            this.token = token;
            this.org = org;
            this.bucket = bucket;
        }

        public void close() {
            closed = true;
        }
    }
}

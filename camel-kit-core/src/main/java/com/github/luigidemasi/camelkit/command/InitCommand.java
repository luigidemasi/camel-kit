package io.github.luigidemasi.camelkit.command;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.catalog.CatalogDownloader;
import io.github.luigidemasi.camelkit.catalog.CitrusSchemaDownloader;
import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.util.TemplateUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Initialize a new Camel-Kit project.
 */
@Command(name = "init", description = "Initialize a new Camel-Kit project")
public class InitCommand extends CamelKitCommand {

    @Parameters(index = "0", description = "Project name", arity = "0..1")
    String projectName;

    @Option(names = {"-a", "--ai"}, description = "AI agent: bob, gemini, claude",
            defaultValue = "bob")
    String ai;

    @Option(names = {"--here"}, description = "Initialize in current directory")
    boolean here;

    @Option(names = {"-v", "--camel-version"},
            description = "Camel version (use 'default' for bundled catalog)",
            defaultValue = "default")
    String camelVersion;

    @Option(names = {"--citrus-version"},
            description = "Citrus version for test schemas",
            defaultValue = "default")
    String citrusVersion;

    @Option(names = {"--no-fetch"}, description = "Skip external catalog fetching")
    boolean noFetch;

    public InitCommand(CamelKitMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        main.printBanner();

        if (!AgentRegistry.contains(ai)) {
            printer().println(red("Error: Unknown agent '" + ai + "'"));
            printer().println("Available agents: " + String.join(", ", AgentRegistry.names()));
            return 1;
        }

        AgentConfig agent = AgentRegistry.get(ai);

        // Resolve target directory
        Path targetDir;
        if (here) {
            targetDir = Path.of("").toAbsolutePath();
            projectName = targetDir.getFileName().toString();
        } else if (projectName != null) {
            targetDir = Path.of(projectName).toAbsolutePath();
        } else {
            printer().println(red("Error: Please provide a project name or use --here"));
            return 1;
        }

        // Get catalog versions
        String version = "default".equals(camelVersion)
            ? CamelKitMain.LATEST_CAMEL_LTS_VERSION
            : camelVersion;

        String citrusVer = "default".equals(citrusVersion)
            ? CamelKitMain.DEFAULT_CITRUS_VERSION
            : citrusVersion;

        printer().println(green("✓") + " Using Camel version " + version);
        printer().println(green("✓") + " Using Citrus version " + citrusVer);

        // Create directory structure
        Files.createDirectories(targetDir);
        Path commandsDir = targetDir.resolve(agent.folder());
        Files.createDirectories(commandsDir);
        Path camelKitDir = targetDir.resolve(".camel-kit");
        Files.createDirectories(camelKitDir);
        Files.createDirectories(camelKitDir.resolve("flows"));
        Files.createDirectories(camelKitDir.resolve("templates"));
        Files.createDirectories(camelKitDir.resolve(".cache"));
        Files.createDirectories(targetDir.resolve("test/data"));
        Files.createDirectories(targetDir.resolve("schemas"));

        // Create config.yaml
        createConfigFile(camelKitDir, projectName, version, citrusVer, ai, agent);

        // Create constitution.md
        createConstitution(camelKitDir);

        // Create command templates
        createCommandTemplates(commandsDir, agent);

        // Create YAML generation guide and additional templates
        createYamlGuide(camelKitDir.resolve("templates"));
        copyAdditionalTemplates(camelKitDir.resolve("templates"));

        // Create Maven Wrapper for portable Maven execution
        createMavenWrapper(targetDir);

        // Download catalog JSON files and Citrus schemas
        int componentCount = 0;
        int kameletCount = 0;
        int citrusSchemaCount = 0;

        if (!noFetch) {
            try {
                CatalogDownloader downloader = new CatalogDownloader(camelKitDir.resolve(".cache"));
                JsonNode compCatalog = downloader.fetchComponentCatalog(version, false);
                componentCount = compCatalog.path("componentCount").asInt();

                JsonNode kamCatalog = downloader.fetchKameletCatalog(version, false);
                kameletCount = kamCatalog.path("kameletCount").asInt();

                downloader.fetchYamlSchema(version, false);
            } catch (Exception e) {
                printer().println(yellow("  Warning: Could not fetch Camel catalog: " + e.getMessage()));
            }

            try {
                CitrusSchemaDownloader citrusDownloader = new CitrusSchemaDownloader(camelKitDir.resolve(".cache"));
                citrusDownloader.fetchCitrusSchemas(citrusVer, false);
                // Count schema files
                Path citrusSchemasDir = citrusDownloader.getCitrusSchemasDir(citrusVer);
                if (Files.exists(citrusSchemasDir)) {
                    citrusSchemaCount = (int) Files.walk(citrusSchemasDir)
                        .filter(p -> p.toString().endsWith(".json"))
                        .count();
                }
            } catch (Exception e) {
                printer().println(yellow("  Warning: Could not fetch Citrus schemas: " + e.getMessage()));
            }
        }

        printer().println();
        printer().println(green("✓") + " Camel-Kit initialized for " + bold(projectName));
        printer().println();
        if (componentCount > 0 || kameletCount > 0) {
            printer().println("  Cached catalog (Camel " + version + "):");
            printer().println("    Components: " + componentCount);
            printer().println("    Kamelets:   " + kameletCount);
        }
        if (citrusSchemaCount > 0) {
            printer().println("  Cached schemas (Citrus " + citrusVer + "):");
            printer().println("    Schemas:    " + citrusSchemaCount);
        }
        if (componentCount > 0 || kameletCount > 0 || citrusSchemaCount > 0) {
            printer().println();
        }
        printer().println(bold("Next steps:"));
        printer().println("  1. Open " + cyan(projectName) + " in " + agent.name());
        printer().println("  2. Run " + cyan("/camel.project") + " to define your integration project");
        printer().println("  3. Run " + cyan("/camel.flow <flow-name>") + " to design a flow");
        printer().println("  4. Run " + cyan("/camel.implement <flow-name>") + " to generate Camel YAML");
        printer().println();

        return 0;
    }

    private void createConfigFile(Path dir, String name, String version, String citrusVer,
                                   String ai, AgentConfig agent) throws Exception {
        String yaml = """
            # Camel-Kit Configuration
            project:
              name: %s
              camelVersion: "%s"
              citrusVersion: "%s"

            agent:
              name: %s
              folder: %s

            catalog:
              source: bundled
              lastUpdated: %s
            """.formatted(name, version, citrusVer, ai, agent.folder(), Instant.now().toString());
        Files.writeString(dir.resolve("config.yaml"), yaml);
    }

    private void createConstitution(Path dir) throws Exception {
        String content = TemplateUtils.readTemplate("templates/constitution.md");
        Files.writeString(dir.resolve("constitution.md"), content);
    }

    private void createCommandTemplates(Path dir, AgentConfig agent) throws Exception {
        List<String> commands = List.of("project", "flow", "implement", "validate", "test");

        for (String cmd : commands) {
            String templatePath = "templates/commands/" + cmd + ".md";
            String content = TemplateUtils.readTemplate(templatePath);

            // Wrap in TOML format if needed
            if ("toml".equals(agent.fileFormat())) {
                content = wrapInToml(cmd, content);
            }

            String filename = "camel." + cmd + "." + agent.fileFormat();
            Files.writeString(dir.resolve(filename), content);
        }
    }

    private String wrapInToml(String cmd, String content) {
        // Escape triple quotes for TOML
        String escaped = content.replace("\"\"\"", "\\\"\\\"\\\"");
        return """
            description = "Camel-Kit %s command"

            prompt = \"\"\"
            %s
            \"\"\"
            """.formatted(cmd, escaped);
    }

    private void createYamlGuide(Path dir) throws Exception {
        String content = TemplateUtils.readTemplate("templates/yaml-generation-guide.md");
        Files.writeString(dir.resolve("yaml-generation-guide.md"), content);
    }

    private void copyAdditionalTemplates(Path templatesDir) throws Exception {
        String[] additionalTemplates = {
            "design-patterns.md",
            "validation-guide.md",
            "flow.md"
        };
        for (String template : additionalTemplates) {
            String content = TemplateUtils.readTemplate("templates/" + template);
            Files.writeString(templatesDir.resolve(template), content);
        }
    }

    private void createMavenWrapper(Path projectDir) throws Exception {
        // Create .mvn/wrapper directory
        Path wrapperDir = projectDir.resolve(".mvn/wrapper");
        Files.createDirectories(wrapperDir);

        // Create maven-wrapper.properties
        String mavenVersion = "3.9.9";
        String wrapperProps = """
            distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%s/apache-maven-%s-bin.zip
            wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
            """.formatted(mavenVersion, mavenVersion);
        Files.writeString(wrapperDir.resolve("maven-wrapper.properties"), wrapperProps);

        // Create mvnw (Unix script)
        String mvnwScript = TemplateUtils.readTemplate("maven/mvnw");
        Path mvnwPath = projectDir.resolve("mvnw");
        Files.writeString(mvnwPath, mvnwScript);
        mvnwPath.toFile().setExecutable(true);

        // Create mvnw.cmd (Windows script)
        String mvnwCmdScript = TemplateUtils.readTemplate("maven/mvnw.cmd");
        Files.writeString(projectDir.resolve("mvnw.cmd"), mvnwCmdScript);

        printer().println(green("✓") + " Maven Wrapper created (portable Maven " + mavenVersion + ")");
    }
}

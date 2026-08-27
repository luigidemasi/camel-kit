package io.github.luigidemasi.camelkit.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import io.github.luigidemasi.camelkit.catalog.CitrusSchemaDownloader;
import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.generator.AgentGenerator;
import io.github.luigidemasi.camelkit.generator.AgentGeneratorFactory;
import io.github.luigidemasi.camelkit.generator.InitContext;
import io.github.luigidemasi.camelkit.generator.QuteTemplateEngine;
import io.github.luigidemasi.camelkit.graph.GraphBuildResult;
import io.github.luigidemasi.camelkit.graph.GraphBuilder;
import io.github.luigidemasi.camelkit.graph.GraphSerializer;
import io.github.luigidemasi.camelkit.graph.ParserDiagnostic;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.RuntimeDetector;
import io.github.luigidemasi.camelkit.graph.model.NodeType;
import io.github.luigidemasi.camelkit.util.TemplateUtils;

/**
 * Initializes Camel-Kit workspaces.
 */
public class InitService {

    private static final String MAVEN_WRAPPER_VERSION = "3.9.9";
    private static final String BOB_LEGACY_WARNING = "IBM Bob 1 legacy selected; use --ai bob2 for new IBM Bob "
                                                     + "projects. Bob 1 generation remains supported for existing "
                                                     + "projects.";

    public InitResult initialize(InitRequest request) throws Exception {
        AgentConfig agent = AgentRegistry.get(request.agentName());
        if (agent == null) {
            throw new IllegalArgumentException("Unknown agent: " + request.agentName());
        }

        Path targetDir = request.targetDir();
        Path camelKitDir = targetDir.resolve(".camel-kit");
        Path skillsDir = targetDir.resolve(agent.skillsDirectory());
        Path commandsDir = agent.generatesCommandStubs()
                ? targetDir.resolve(agent.commandDirectory())
                : skillsDir;
        Path docsDir = targetDir.resolve("docs");
        String citrusVersion = request.resolvedCitrusVersion();

        InitContext genCtx = new InitContext(
                agent, request.agentName(), commandsDir,
                skillsDir, targetDir,
                request.commandPrefix(), request.distribution(), request.printer());
        AgentGenerator generator = AgentGeneratorFactory.create(request.agentName());
        requireRealAgentDirectories(targetDir, skillsDir, commandsDir);
        generator.preflight(genCtx);

        List<Path> createdPaths = new ArrayList<>();
        List<InitWarning> warnings = new ArrayList<>();
        InitProgress progress = request.progress();
        if ("bob".equals(request.agentName())) {
            reportWarning(request, warnings, BOB_LEGACY_WARNING);
        }

        progress.startTask("\uD83D\uDCC1", "Creating project structure");
        createProjectStructure(
                targetDir, camelKitDir, docsDir, createdPaths);
        progress.finishTask();

        progress.startTask("\uD83D\uDCDD", "Writing configuration");
        createConfigFile(camelKitDir, request.projectName(), request.agentName(), agent, request, createdPaths);
        createConstitution(docsDir, request, createdPaths);
        copyAdditionalTemplates(camelKitDir.resolve("templates"), createdPaths);
        progress.finishTask();

        progress.startTask("\uD83E\uDD16", "Generating " + agent.name() + " workspace");
        generator.generate(genCtx);
        progress.finishTask();

        progress.startTask("\uD83D\uDD0C", "Configuring Maven wrapper");
        createMavenWrapper(targetDir, createdPaths);
        request.reporter().mavenWrapperCreated(MAVEN_WRAPPER_VERSION);
        progress.finishTask();

        progress.startTask("\uD83D\uDD0D", "Building project graph");
        InitGraphSummary graph = buildProjectGraph(targetDir, camelKitDir, request, warnings, createdPaths);
        progress.finishTask();

        int citrusSchemaCount = 0;
        if (!request.noFetch()) {
            // After graph build: runtime detection may have rewritten project.camelVersion (and forage.version).
            cacheForageCatalog(camelKitDir, request, warnings);
            progress.startTask("\u2B07\uFE0F", "Downloading Citrus schemas");
            citrusSchemaCount = fetchCitrusSchemas(camelKitDir, citrusVersion, request, warnings);
            progress.finishTask();
        }

        return new InitResult(
                request.projectName(), request.agentName(), agent, targetDir, camelKitDir, commandsDir, skillsDir,
                citrusVersion, citrusSchemaCount, MAVEN_WRAPPER_VERSION, graph, createdPaths, warnings);
    }

    /**
     * Fails initialization up front — before any project files are written — when a managed agent path component is a
     * symbolic link. Generation and retired-asset cleanup refuse to operate through symbolic links, and failing late
     * would leave a half-initialized workspace.
     */
    private void requireRealAgentDirectories(Path targetDir, Path... managedDirs) throws IOException {
        for (Path dir : managedDirs) {
            Path current = targetDir;
            for (Path component : targetDir.relativize(dir)) {
                current = current.resolve(component);
                if (Files.isSymbolicLink(current)) {
                    throw new IOException(
                            "Initialization aborted: " + current + " is a symbolic link. Camel-Kit generates "
                                          + "and removes files under the agent directories and refuses to operate "
                                          + "through symbolic links; replace the link with a real directory and "
                                          + "re-run init.");
                }
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    break;
                }
            }
        }
    }

    private void createProjectStructure(
            Path targetDir, Path camelKitDir, Path docsDir, List<Path> createdPaths)
            throws Exception {
        createDirectory(targetDir, createdPaths);
        createDirectory(camelKitDir, createdPaths);
        createDirectory(docsDir.resolve("flows"), createdPaths);
        createDirectory(camelKitDir.resolve("templates"), createdPaths);
        createDirectory(camelKitDir.resolve(".cache"), createdPaths);
        createDirectory(targetDir.resolve("test/data"), createdPaths);
        createDirectory(targetDir.resolve("schemas"), createdPaths);
    }

    private void createDirectory(Path path, List<Path> createdPaths) throws Exception {
        Files.createDirectories(path);
        createdPaths.add(path);
    }

    private void createConfigFile(
            Path dir,
            String name,
            String agentName,
            AgentConfig agent,
            InitRequest request,
            List<Path> createdPaths)
            throws Exception {
        Properties config = new Properties();
        config.setProperty("project.name", name);
        config.setProperty("project.command-prefix", request.commandPrefix());
        config.setProperty("agent.name", agentName);
        config.setProperty("agent.folder", agent.folder());
        config.setProperty("citrus.version", request.resolvedCitrusVersion());
        config.setProperty("citrus.mcp.version", request.distribution().citrusMcpVersion());
        writeVersionConfig(config, request.distribution(), "main");
        if (request.sourcePlatform() != null && !"auto".equals(request.sourcePlatform())) {
            config.setProperty("project.sourcePlatform", request.sourcePlatform());
        }

        Path configFile = dir.resolve("config.properties");
        try (var out = Files.newOutputStream(configFile)) {
            config.store(out, "Camel-Kit Project Configuration");
        }
        createdPaths.add(configFile);
    }

    private void cacheForageCatalog(Path camelKitDir, InitRequest request, List<InitWarning> warnings)
            throws Exception {
        Properties config = new Properties();
        try (var in = Files.newInputStream(camelKitDir.resolve("config.properties"))) {
            config.load(in);
        }
        String forageVersion = config.getProperty("forage.version");
        if (forageVersion == null) {
            return;
        }
        try {
            new ForageCatalogService().cache(camelKitDir, request.distribution().forageCatalogArtifact(),
                    forageVersion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reportWarning(request, warnings,
                    "Forage catalog could not be cached (interrupted) — Forage support disabled for this "
                                             + "workspace; skills fall back to component properties/beans.");
        } catch (Exception e) {
            reportWarning(request, warnings,
                    "Forage catalog could not be cached (" + e.getMessage()
                                             + ") — Forage support disabled for this workspace; skills fall back to component properties/beans.");
        }
    }

    private void createConstitution(Path dir, InitRequest request, List<Path> createdPaths) throws Exception {
        QuteTemplateEngine qute = new QuteTemplateEngine();
        String template = TemplateUtils.readTemplate("templates/constitution.md");

        String content = qute.renderString(template, Map.of(
                "DATE", LocalDate.now(ZoneId.systemDefault()).toString(),
                "CAMEL_VERSION", request.distribution().camelMainVersion()));
        Path constitution = dir.resolve("constitution.md");
        Files.writeString(constitution, content);
        createdPaths.add(constitution);
    }

    private void copyAdditionalTemplates(Path templatesDir, List<Path> createdPaths) throws Exception {
        String[] additionalTemplates = {
                "patterns-foundational.md",
                "patterns-error-handling.md",
                "patterns-deployment.md",
                "yaml-structure.md",
                "yaml-components.md",
                "yaml-examples.md",
                "validation-completeness.md",
                "validation-constitution.md",
                "validation-testing.md",
                "flow.md",
                "pom-quarkus.xml",
                "pom-spring-boot.xml"
        };
        for (String template : additionalTemplates) {
            String content = TemplateUtils.readTemplate("templates/" + template);
            Path target = templatesDir.resolve(template);
            Files.writeString(target, content);
            createdPaths.add(target);
        }
    }

    private void createMavenWrapper(Path projectDir, List<Path> createdPaths) throws Exception {
        Path wrapperDir = projectDir.resolve(".mvn/wrapper");
        createDirectory(wrapperDir, createdPaths);

        String wrapperProps
                = String.format(Locale.ROOT,
                        """
                                distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%s/apache-maven-%s-bin.zip
                                wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
                                """,
                        MAVEN_WRAPPER_VERSION, MAVEN_WRAPPER_VERSION);
        Path wrapperProperties = wrapperDir.resolve("maven-wrapper.properties");
        Files.writeString(wrapperProperties, wrapperProps);
        createdPaths.add(wrapperProperties);

        String mvnwScript = TemplateUtils.readTemplate("maven/mvnw");
        Path mvnwPath = projectDir.resolve("mvnw");
        Files.writeString(mvnwPath, mvnwScript);
        mvnwPath.toFile().setExecutable(true);
        createdPaths.add(mvnwPath);

        String mvnwCmdScript = TemplateUtils.readTemplate("maven/mvnw.cmd");
        Path mvnwCmd = projectDir.resolve("mvnw.cmd");
        Files.writeString(mvnwCmd, mvnwCmdScript);
        createdPaths.add(mvnwCmd);
    }

    private InitGraphSummary buildProjectGraph(
            Path targetDir,
            Path camelKitDir,
            InitRequest request,
            List<InitWarning> warnings,
            List<Path> createdPaths)
            throws Exception {
        Path graphFile = camelKitDir.resolve("project-graph.json");
        try {
            GraphBuildResult result = new GraphBuilder().buildWithDiagnostics(targetDir);
            for (ParserDiagnostic diagnostic : result.warningDiagnostics()) {
                reportWarning(request, warnings, "Project graph warning: " + diagnostic.summary());
            }
            if (!result.successful()) {
                throw new IllegalStateException(result.failureSummary());
            }
            ProjectGraph projectGraph = result.graph();
            if (projectGraph.nodeCount() > 0) {
                GraphSerializer.write(projectGraph, graphFile, targetDir.toAbsolutePath().toString());
                createdPaths.add(graphFile);
                String detectedProjectType = detectProjectType(projectGraph);
                String detectedRuntime = updateConfigWithRuntime(camelKitDir, projectGraph, request.distribution());
                InitGraphSummary graph = new InitGraphSummary(
                        graphFile,
                        projectGraph.nodeCount(),
                        projectGraph.edgeCount(),
                        detectedProjectType,
                        detectedRuntime);
                request.reporter().graphBuilt(graph);
                return graph;
            }
            InitGraphSummary graph = new InitGraphSummary(graphFile, 0, 0, "", "");
            request.reporter().graphSkipped();
            return graph;
        } catch (Exception e) {
            reportWarning(request, warnings, "Could not build project graph: " + e.getMessage());
            return null;
        }
    }

    private int fetchCitrusSchemas(
            Path camelKitDir, String citrusVersion, InitRequest request, List<InitWarning> warnings) {
        try {
            CitrusSchemaDownloader citrusDownloader = new CitrusSchemaDownloader(camelKitDir.resolve(".cache"));
            citrusDownloader.fetchCitrusSchemas(citrusVersion, false, request.printer()::println);
            Path citrusSchemasDir = citrusDownloader.getCitrusSchemasDir(citrusVersion);
            if (Files.exists(citrusSchemasDir)) {
                try (var paths = Files.walk(citrusSchemasDir)) {
                    return (int) paths
                            .filter(p -> p.toString().endsWith(".json"))
                            .count();
                }
            }
        } catch (Exception e) {
            reportWarning(request, warnings, "Could not fetch Citrus schemas: " + e.getMessage());
        }
        return 0;
    }

    private void reportWarning(InitRequest request, List<InitWarning> warnings, String message) {
        InitWarning warning = new InitWarning(message);
        warnings.add(warning);
        request.reporter().warning(warning);
    }

    private String updateConfigWithRuntime(Path dir, ProjectGraph graph, DistributionConfig distribution)
            throws Exception {
        Path configFile = dir.resolve("config.properties");
        Properties config = new Properties();

        if (Files.exists(configFile)) {
            try (var in = Files.newInputStream(configFile)) {
                config.load(in);
            }
        }

        String runtime = RuntimeDetector.detect(graph);
        writeVersionConfig(config, distribution, runtime);

        try (var out = Files.newOutputStream(configFile)) {
            config.store(out, "Camel-Kit Project Configuration");
        }
        return runtime;
    }

    private void writeVersionConfig(Properties config, DistributionConfig distribution, String runtime) {
        String normalizedRuntime = normalizeRuntime(runtime);
        config.setProperty("project.runtime", normalizedRuntime);
        switch (normalizedRuntime) {
            case "quarkus" -> {
                String camelVersion = distribution.camelQuarkusVersion();
                config.setProperty("project.camelVersion", camelVersion);
                config.setProperty("project.platformBomVersion", distribution.quarkusPlatformForVersion(camelVersion));
            }
            case "spring-boot" -> {
                String camelVersion = distribution.camelSpringbootVersion();
                config.setProperty("project.camelVersion", camelVersion);
                config.setProperty("project.platformBomVersion", distribution.springbootBomVersion());
                // Only spring-boot projects need the framework version (spring-boot-maven-plugin lookup).
                config.setProperty("project.springBootVersion",
                        distribution.springBootMappings().getOrDefault(camelVersion, distribution.springBootVersion()));
            }
            default -> {
                config.setProperty("project.camelVersion", distribution.camelMainVersion());
                config.setProperty("project.platformBomVersion", distribution.camelMainVersion());
            }
        }
        // forage.version must always track project.camelVersion — recomputed on every write,
        // including the post-graph runtime rewrite (e.g. main 4.21.0/1.5.0 -> quarkus 4.18.2/1.3).
        String forageVersion = distribution.forageVersionForCamel(config.getProperty("project.camelVersion"));
        if (forageVersion != null) {
            config.setProperty("forage.version", forageVersion);
        } else {
            config.remove("forage.version");
        }
    }

    private String normalizeRuntime(String runtime) {
        if (runtime == null || runtime.isBlank() || "camel-main".equals(runtime) || "jbang".equals(runtime)) {
            return "main";
        }
        return runtime;
    }

    private String detectProjectType(ProjectGraph graph) {
        var types = new ArrayList<String>();

        if (!graph.findByType(NodeType.MULE_FLOW).isEmpty()) {
            int flows = graph.findByType(NodeType.MULE_FLOW).size();
            int subFlows = graph.findByType(NodeType.MULE_SUB_FLOW).size();
            int dwl = graph.findByType(NodeType.DATAWEAVE_SCRIPT).size();
            String muleVersion = findMavenProperty(graph, "mule.version");
            if (muleVersion == null)
                muleVersion = findMavenProperty(graph, "app.runtime");
            if (muleVersion == null)
                muleVersion = findArtifactVersion(graph, "org.mule.runtime", "mule-core");
            if (muleVersion == null)
                muleVersion = findArtifactVersion(graph, "org.mule", "mule-core");
            types.add("MuleSoft Mule" + (muleVersion != null ? " " + muleVersion : "")
                      + " (" + flows + " flows, " + subFlows + " sub-flows"
                      + (dwl > 0 ? ", " + dwl + " DataWeave scripts" : "") + ")");
        }

        int orchs = graph.findByType(NodeType.BIZTALK_ORCHESTRATION).size();
        int maps = graph.findByType(NodeType.BIZTALK_MAP).size();
        int pipelines = graph.findByType(NodeType.BIZTALK_PIPELINE).size();
        if (orchs > 0 || maps > 0 || pipelines > 0) {
            types.add("Microsoft BizTalk (" + orchs + " orchestrations"
                      + (maps > 0 ? ", " + maps + " maps" : "")
                      + (pipelines > 0 ? ", " + pipelines + " pipelines" : "") + ")");
        }

        if (!graph.findByType(NodeType.CAMEL_ROUTE).isEmpty()) {
            int routes = graph.findByType(NodeType.CAMEL_ROUTE).size();
            String camelVersion = detectCamelVersion(graph);
            String platform = detectCamelPlatform(graph);
            types.add(platform + (camelVersion != null ? " " + camelVersion : "")
                      + " (" + routes + " routes)");
        }

        if (!graph.findByType(NodeType.MAVEN_ARTIFACT).isEmpty() && types.isEmpty()) {
            types.add("Maven project");
        }

        return String.join(", ", types);
    }

    private String detectCamelVersion(ProjectGraph graph) {
        String version = findMavenProperty(graph, "camel.version");
        if (version != null)
            return version;

        version = findArtifactVersion(graph, "org.apache.camel", "camel-core");
        if (version != null)
            return version;

        version = findArtifactVersion(graph, "org.apache.camel", "camel-bom");
        if (version != null)
            return version;

        for (var node : graph.findByType(NodeType.MAVEN_ARTIFACT)) {
            if ("org.apache.camel".equals(node.properties().get("groupId"))) {
                String v = node.properties().get("version");
                if (v != null && !"managed".equals(v) && !"unknown".equals(v) && !v.contains("${")) {
                    return v;
                }
            }
        }

        return null;
    }

    private String detectCamelPlatform(ProjectGraph graph) {
        for (var node : graph.findByType(NodeType.MAVEN_ARTIFACT)) {
            String file = node.properties().get("file");
            if (file != null && file.contains("pom.xml")) {
                String groupId = node.properties().getOrDefault("groupId", "");
                if (groupId.contains("jboss.fuse") || groupId.contains("fusesource")) {
                    return "JBoss Fuse";
                }
            }
        }

        for (var node : graph.findByType(NodeType.MAVEN_ARTIFACT)) {
            String v = node.properties().getOrDefault("version", "");
            if (v.contains(".fuse-") || v.contains(".redhat-")) {
                return "JBoss Fuse";
            }
        }

        return "Apache Camel";
    }

    private String findMavenProperty(ProjectGraph graph, String propertyName) {
        var node = graph.getNode("maven-property:" + propertyName);
        if (node != null) {
            String value = node.properties().get("value");
            if (value != null && !value.isEmpty() && !value.contains("${")) {
                return value;
            }
        }
        return null;
    }

    private String findArtifactVersion(ProjectGraph graph, String groupId, String artifactId) {
        String nodeId = "maven:" + groupId + ":" + artifactId;
        var node = graph.getNode(nodeId);
        if (node != null) {
            String version = node.properties().get("version");
            if (version != null && !"managed".equals(version) && !"unknown".equals(version)
                    && !version.contains("${")) {
                return version;
            }
        }
        return null;
    }
}

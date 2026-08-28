package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentGeneratorStrategy;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifestLoader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConsistencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CODEX = AgentGeneratorStrategy.CODEX.descriptorValue();
    private static final String COPILOT = AgentGeneratorStrategy.COPILOT.descriptorValue();
    private static final String PI = AgentGeneratorStrategy.PI.descriptorValue();

    private static final List<StalePattern> STALE_PATTERNS = List.of(
            new StalePattern(".camel-kit/config.yaml", Pattern.compile(Pattern.quote(".camel-kit/config.yaml"))),
            new StalePattern("config.yaml", Pattern.compile("\\bconfig\\.yaml\\b")),
            new StalePattern("graph_stats", Pattern.compile("\\bgraph_stats\\b")),
            new StalePattern("/camel-design", Pattern.compile("(?<![\\w-])/camel-design\\b")),
            new StalePattern("camel_knowledge_search", Pattern.compile("\\bcamel_knowledge_search\\b")),
            new StalePattern("camel_docs_component", Pattern.compile("\\bcamel_docs_component\\b")),
            new StalePattern(
                    "camel_rh_build_component_info",
                    Pattern.compile("\\bcamel_rh_build_component_info\\b")),
            new StalePattern(
                    "outdated Iron Law count",
                    Pattern.compile("(?i)\\b(all\\s+4\\s+laws|all\\s+four\\s+iron\\s+laws|"
                                    + "all\\s+four\\s+laws|the\\s+four\\s+iron\\s+laws|"
                                    + "four\\s+iron\\s+laws|full\\s+set\\s+of\\s+four\\s+iron\\s+laws)\\b")),
            new StalePattern(
                    "legacy per-flow TDD path",
                    Pattern.compile("\\bdocs/flows\\b|\\.camel-kit/flows\\b|\\.tdd\\.md\\b")),
            new StalePattern(
                    "legacy root pipeline artifact path",
                    Pattern.compile("\\bdocs/(business-requirements|implementation-plan)\\.md\\b")),
            new StalePattern(
                    "legacy TDD artifact terminology",
                    Pattern.compile("(?i)(\\bTDD\\b\\s*(Section|file|files|path|content|field|mapping|format|"
                                    + "output|artifact|specification|decomposition|creation|assembly)"
                                    + "|\\b(TDDs|per-flow\\s+TDD|BRD\\+TDD|TDD-level)\\b"
                                    + "|Technical\\s+Design\\s+Document)")),
            new StalePattern(
                    "legacy BRD artifact terminology",
                    Pattern.compile("\\bBRD\\b|Blueprint\\s+Reference\\s+Document")),
            new StalePattern(
                    "uppercase flow-name token in pipeline test-data path",
                    Pattern.compile("docs/camel-kit/<PIPELINE_ID>/test-data/\\{FLOW_NAME\\}")),
            new StalePattern(
                    "retired internal command exposed as slash command",
                    Pattern.compile("(?<![\\w-])/camel-(implement|test|flow)\\b")),
            new StalePattern("legacy camel.version property", Pattern.compile("\\bcamel\\.version\\b")),
            new StalePattern("legacy versions.properties source", Pattern.compile("\\bversions\\.properties\\b")),
            new StalePattern(
                    "legacy runtime enum in config or RUNTIME context",
                    Pattern.compile("(?i)(`?project\\.runtime`?|`RUNTIME`)[^\\n]*\\([^\\n)]*\\b(jbang|springboot)\\b"
                                    + "[^\\n)]*\\)|\\bRUNTIME\\s*==\\s*(jbang|springboot)\\b")),
            new StalePattern(
                    "Iron Law numbering mismatch",
                    Pattern.compile("(?i)(Iron\\s+Law\\s+3[^\\n]*(constitution|all\\s+7)"
                                    + "|constitution[^\\n]*Iron\\s+Law\\s+3"
                                    + "|Iron\\s+Law\\s+4[^\\n]*(No\\s+Code|approval|approved|"
                                    + "generate\\s+ONLY|Produce\\s+the\\s+design\\s+spec))")));

    @TempDir
    Path tempDir;

    @Test
    void activeShippedResourcesDoNotContainKnownStaleContractTokens() throws IOException {
        Path root = repositoryRoot();

        List<String> violations = new ArrayList<>();
        for (Path file : activeResourceFiles(root)) {
            String content = Files.readString(file);
            for (StalePattern stale : STALE_PATTERNS) {
                if (stale.pattern().matcher(content).find()) {
                    violations.add(root.relativize(file) + ": " + stale.description());
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Active shipped resources contain stale contract references:\n"
                                         + String.join("\n", violations));
    }

    @Test
    void activeResourceScanIncludesTopLevelDocsOnly() throws IOException {
        Path root = repositoryRoot();

        Set<String> activeResources = activeResourceFiles(root).stream()
                .map(path -> root.relativize(path).toString())
                .collect(Collectors.toSet());
        Set<String> topLevelDocs;
        try (Stream<Path> paths = Files.list(root.resolve("docs"))) {
            topLevelDocs = paths.filter(Files::isRegularFile)
                    .filter(ResourceConsistencyTest::isScannedTextFile)
                    .map(path -> root.relativize(path).toString())
                    .collect(Collectors.toSet());
        }

        assertTrue(activeResources.containsAll(topLevelDocs));
        assertFalse(activeResources.stream().anyMatch(path -> path.startsWith("docs/plans/")));
        assertFalse(activeResources.stream().anyMatch(path -> path.startsWith("docs/superpowers/")));
        assertFalse(activeResources.stream().anyMatch(path -> path.startsWith("docs/flows/")));
        assertFalse(activeResources.stream().anyMatch(path -> path.startsWith("docs/img/")));
    }

    @Test
    void generatedCommandStubsMatchIntendedExposedSkills() throws Exception {
        for (String agentName : sortedAgentNames()) {
            Path projectDir = tempDir.resolve(agentName);
            InitContext ctx = createContext(agentName, projectDir);

            new DefaultGenerator().generate(ctx);

            if (!ctx.agent().generatesCommandStubs()) {
                assertFalse(Files.exists(ctx.commandsDir()), agentName + " must not generate command scaffolding");
                continue;
            }

            Set<String> generatedCommands;
            try (Stream<Path> files = Files.list(ctx.commandsDir())) {
                generatedCommands = files
                        .filter(Files::isRegularFile)
                        .map(path -> stripExtension(path.getFileName().toString()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }

            Set<String> expectedCommands = WorkflowManifestLoader.loadDefault().generatedCommandStubs().stream()
                    .filter(command -> !command.isSkillOnly(agentName))
                    .map(command -> command.name())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            assertEquals(expectedCommands, generatedCommands,
                    "Generated command stubs for " + agentName + " must match the public slash-command surface");
            assertFalse(generatedCommands.contains("camel-verify"), "camel-verify is internal to camel-execute");
            assertFalse(generatedCommands.contains("camel-implement"), "camel-implement is an internal guide skill");
            assertFalse(generatedCommands.contains("camel-test"), "camel-test is an internal guide skill");
            assertFalse(generatedCommands.contains("camel-design"), "camel-design is an internal guide skill");
        }
    }

    @Test
    void generatedKnowledgeMcpAllowlistsOnlyExposeImplementedTools() throws Exception {
        for (String agentName : sortedAgentNames()) {
            Path projectDir = tempDir.resolve(agentName);
            InitContext ctx = createContext(agentName, projectDir);

            new DefaultGenerator().generate(ctx);

            if (CODEX.equals(agentName)) {
                TomlTable knowledgeServer = tomlServerConfig(agentName, projectDir, "camel-knowledge");
                assertTomlTools(agentName, "enabled_tools", knowledgeServer, "camel-knowledge");
                TomlTable citrusServer = tomlServerConfig(agentName, projectDir, "citrus");
                assertTomlTools(agentName, "enabled_tools", citrusServer, "citrus");
                continue;
            }

            JsonNode knowledgeServer = serverConfig(agentName, projectDir, "camel-knowledge");
            if (COPILOT.equals(agentName)) {
                assertKnowledgeTools(agentName, "tools", knowledgeServer.get("tools"));
            } else if (PI.equals(agentName)) {
                assertKnowledgeTools(agentName, "directTools", knowledgeServer.get("directTools"));
            } else if ("qwen".equals(agentName)) {
                assertKnowledgeTools(agentName, "includeTools", knowledgeServer.get("includeTools"));
            } else if ("opencode".equals(agentName)) {
                assertFalse(knowledgeServer.has("autoApprove"));
                assertFalse(knowledgeServer.has("alwaysAllow"));
            } else {
                assertKnowledgeTools(agentName, "autoApprove", knowledgeServer.get("autoApprove"));
                assertKnowledgeTools(agentName, "alwaysAllow", knowledgeServer.get("alwaysAllow"));
            }

            JsonNode citrusServer = serverConfig(agentName, projectDir, "citrus");
            if (COPILOT.equals(agentName)) {
                assertCitrusTools(agentName, "tools", citrusServer.get("tools"));
            } else if (PI.equals(agentName)) {
                assertCitrusTools(agentName, "directTools", citrusServer.get("directTools"));
            } else if ("qwen".equals(agentName)) {
                assertCitrusTools(agentName, "includeTools", citrusServer.get("includeTools"));
            } else if ("opencode".equals(agentName)) {
                assertFalse(citrusServer.has("autoApprove"));
                assertFalse(citrusServer.has("alwaysAllow"));
            } else {
                assertCitrusTools(agentName, "autoApprove", citrusServer.get("autoApprove"));
                assertCitrusTools(agentName, "alwaysAllow", citrusServer.get("alwaysAllow"));
            }
        }
    }

    @Test
    void shippedSkillAndPersonaMcpToolReferencesExistInTheWorkflowManifest() throws Exception {
        WorkflowManifest manifest = WorkflowManifestLoader.loadDefault();
        Set<String> allowedTools = manifest.mcpServers().stream()
                .flatMap(server -> server.allowedTools().stream())
                .collect(Collectors.toSet());
        Pattern toolReference = Pattern.compile("\\b(?:camel|citrus)_[a-z][a-z0-9_]*[a-z0-9]\\b");
        Path resources = repositoryRoot().resolve("camel-kit-core/src/main/resources");
        List<String> unknown = new ArrayList<>();

        for (Path root : List.of(resources.resolve("skills"), resources.resolve("agents"))) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".md"))
                        .toList()) {
                    Matcher matcher = toolReference.matcher(Files.readString(file));
                    while (matcher.find()) {
                        if (!allowedTools.contains(matcher.group())) {
                            unknown.add(resources.relativize(file) + ": " + matcher.group());
                        }
                    }
                }
            }
        }

        assertTrue(unknown.isEmpty(), "Shipped guidance references unknown MCP tools:\n" + String.join("\n", unknown));
    }

    @Test
    void architectureDocumentsActualCamelRouteTestScaffoldOutput() throws IOException {
        String architecture = Files.readString(repositoryRoot().resolve("docs/architecture.md"));

        assertTrue(architecture.contains("`camel_route_test_scaffold` | Generate a JUnit 5 Camel route test scaffold"),
                "camel_route_test_scaffold currently emits JUnit 5 scaffolds in Camel MCP");
        assertFalse(
                architecture.contains("camel_route_test_scaffold` | Generate a route test scaffold for Citrus YAML"),
                "Architecture docs must not claim camel_route_test_scaffold emits Citrus YAML");
    }

    @Test
    void graphProjectNormsGuideUsesNestedCommandResponse() throws IOException {
        String guide = Files.readString(repositoryRoot().resolve(
                "camel-kit-core/src/main/resources/skills/camel-validate/guides/graph-project-context.md"));

        assertTrue(guide.contains("response.naming.detectedPattern"));
        assertTrue(guide.contains("response.naming.majorityPercentage"));
        assertTrue(guide.contains("response.errorHandling.coverage"));
        assertTrue(guide.contains("response.properties.patterns"));
        assertTrue(guide.contains("response.stepCounts.p75"));
        assertFalse(guide.contains("response.namingPattern"));
        assertFalse(guide.contains("response.errorHandlingNorm"));
        assertFalse(guide.contains("response.propertyPatterns"));
        assertFalse(guide.contains("response.stepCountP75"));
        assertFalse(guide.contains("response.structuralWarnings"));
    }

    @Test
    void specComplianceReviewLoopsUseTheSameBound() throws IOException {
        Path root = repositoryRoot().resolve("camel-kit-core/src/main/resources");
        String execute = Files.readString(root.resolve("skills/camel-execute/SKILL.md"));
        String criteria = Files.readString(root.resolve(
                "skills/camel-execute/guides/spec-reviewer-criteria.md"));
        String bobGate = Files.readString(root.resolve("templates/bob/gates/camel-execute.md"));

        assertTrue(execute.contains("at most 3 review iterations"));
        assertTrue(criteria.contains("Maximum iterations:** 3"));
        assertTrue(bobGate.contains("at most 3 review iterations"));
    }

    @Test
    void dockerComposeGenerationIsConditionalForEveryRuntime() throws IOException {
        Path root = repositoryRoot().resolve("camel-kit-core/src/main/resources");
        String composeGuide = Files.readString(root.resolve(
                "skills/camel-implement/guides/docker-compose.md"));
        String orchestrator = Files.readString(root.resolve(
                "skills/camel-implement/guides/orchestrator.md"));
        String greenfieldTasks = Files.readString(root.resolve(
                "skills/camel-plan/guides/task-template-greenfield.md"));
        String normalizedComposeGuide = composeGuide.replaceAll("\\s+", " ");
        String normalizedGreenfieldTasks = greenfieldTasks.replaceAll("\\s+", " ");

        assertTrue(composeGuide.contains("skip Docker Compose generation for every runtime, including Main"));
        assertTrue(composeGuide.contains(
                "Use this template only when `RUNTIME == main` and at least one external service dependency is "
                                         + "required."));
        assertTrue(composeGuide.contains("`ROUTE_FILES` (all module `.camel.yaml` files)"));
        assertTrue(composeGuide.contains("list every file in the single `command:`"));
        assertTrue(normalizedComposeGuide.contains("The single Camel service command must still list every route"));
        assertTrue(orchestrator.contains("### Step 4: Docker Compose (CONDITIONAL)"));
        assertTrue(orchestrator.contains(
                "**SKIP** if the design spec has no external service dependencies, regardless of runtime"));
        assertTrue(orchestrator.contains("`ROUTE_FILES` (every module `.camel.yaml` file)"));
        assertTrue(orchestrator.contains("`XSL_FILES` (every module XSLT DataMapper file; may be empty)"));
        assertTrue(normalizedGreenfieldTasks.contains(
                "Omit it entirely when none do, for all runtimes (including Main)."));
        assertTrue(normalizedGreenfieldTasks.contains(
                "inventory and pass every module `.camel.yaml` route and every XSLT DataMapper file"));
    }

    @Test
    void plannedProjectStructureMatchesRuntimeFileContracts() throws IOException {
        Path root = repositoryRoot().resolve("camel-kit-core/src/main/resources/skills");
        String designAssembly = Files.readString(root.resolve("camel-brainstorm/guides/design-assembly.md"));
        String orchestrator = Files.readString(root.resolve("camel-implement/guides/orchestrator.md"));
        String dataMapper = Files.readString(root.resolve("camel-implement/guides/datamapper-validation.md"));
        String testTasks = Files.readString(root.resolve("camel-plan/guides/task-template-testing.md"));
        String normalizedDesignAssembly = designAssembly.replaceAll("\\s+", " ");

        assertTrue(normalizedDesignAssembly.contains("[flow-name].camel.yaml [Main]"));
        assertTrue(normalizedDesignAssembly.contains("main/resources/ [Spring Boot / Quarkus]"));
        assertTrue(normalizedDesignAssembly.contains("test/resources/ [all runtimes]"));
        assertTrue(normalizedDesignAssembly.contains("pom.xml [Spring Boot / Quarkus only]"));
        assertTrue(normalizedDesignAssembly.contains(
                "docker-compose.yaml [only when external services are required]"));
        assertFalse(designAssembly.contains("docker-compose.yml"));
        assertTrue(designAssembly.contains("business-requirements.md"));
        assertTrue(designAssembly.contains("implementation-plan.md"));
        assertTrue(designAssembly.contains("execution-report.md"));
        assertTrue(designAssembly.contains("validation-report.md"));
        assertTrue(designAssembly.contains("maven-wrapper.properties"));
        assertTrue(designAssembly.contains("**Runtime:** [Main / Spring Boot / Quarkus]"));
        assertTrue(designAssembly.contains("Route YAML references every property as `{{property-name}}`"));

        assertTrue(orchestrator.contains("| `.kaoto` (XSLT DataMapper only) | Project root | Project root |"));
        assertTrue(orchestrator.contains("`{MODULE_PREFIX}` is the empty string"));
        assertTrue(orchestrator.contains("`{ROUTE_DIR}{flow-name}.camel.yaml`"));
        assertFalse(orchestrator.contains("`{ROUTE_DIR}/{flow-name}.camel.yaml`"));
        assertTrue(dataMapper.contains("| Location | Project root | NOT in `.camel-kit/` or a target module |"));
        assertTrue(testTasks.contains("`{TEST_DIR}jbang.properties` (Main only"));
        assertTrue(testTasks.contains("`[MODULE_DIR]pom.xml` (Spring Boot/Quarkus only"));
        assertTrue(testTasks.contains("do not create a `pom.xml`"));
        assertTrue(testTasks.contains(
                "camel test run [MODULE_DIR]src/test/resources/[flow-name].camel.it.yaml` exits 0"));
    }

    @Test
    void planningContractsAreRuntimeAwareAndHaveSingleArtifactOwners() throws IOException {
        Path root = repositoryRoot().resolve("camel-kit-core/src/main/resources/skills");
        String plan = Files.readString(root.resolve("camel-plan/SKILL.md"));
        String decomposition = Files.readString(root.resolve("camel-plan/guides/task-decomposition.md"));
        String greenfield = Files.readString(root.resolve("camel-plan/guides/task-template-greenfield.md"));
        String migration = Files.readString(root.resolve("camel-plan/guides/task-template-migration.md"));
        String implement = Files.readString(root.resolve("camel-implement/SKILL.md"));
        String normalizedPlan = plan.replaceAll("\\s+", " ");
        String normalizedDecomposition = decomposition.replaceAll("\\s+", " ");
        String normalizedMigration = migration.replaceAll("\\s+", " ");

        assertTrue(plan.contains("Every normal greenfield or migration plan: also load"));
        assertTrue(normalizedPlan.contains("one Citrus test task per flow"));
        assertTrue(greenfield.contains("For Main, create the runtime tree from Section 6, do NOT load Maven/POM"));
        assertTrue(greenfield.contains("Generate Missing Schemas (per flow, conditional)"));
        assertTrue(greenfield.contains("Generate Main Run Script (consolidated)"));
        assertTrue(greenfield.contains(
                "camel_catalog_eip_doc(eip=\"[eip]\", runtime=\"[runtime]\", platformBom=\"[bom]\")"));
        assertTrue(greenfield.contains(
                "camel_catalog_language_doc(language=\"[language]\", runtime=\"[runtime]\", "
                                       + "platformBom=\"[bom]\")"));
        assertTrue(normalizedDecomposition.contains(
                "owns the selected inline Groovy or XSLT DataMapper implementation"));
        assertFalse(decomposition.contains("**DataMapper XSLT**"));
        assertTrue(decomposition.contains("Exactly one module-wide Main `run.sh` task"));
        assertTrue(decomposition.contains("Exactly one Docker Compose task"));
        assertFalse(migration.contains("### Task N: Generate XSLT DataMapper"));
        assertTrue(normalizedMigration.contains("the scaffold task is the sole POM creator"));
        assertTrue(migration.contains("./mvnw -f [MODULE_DIR]pom.xml dependency:tree"));
        assertTrue(migration.contains("./mvnw -f [MODULE_DIR]pom.xml -DskipTests compile"));
        assertTrue(implement.contains("`guides/maven-dependencies.md` | Spring Boot/Quarkus only"));
    }

    @Test
    void migrationContractsPreserveRuntimeAndCanonicalTransformationEngine() throws IOException {
        Path root = repositoryRoot().resolve("camel-kit-core/src/main/resources/skills");
        String migrate = Files.readString(root.resolve("camel-migrate/SKILL.md"));
        String versionSelection = Files.readString(root.resolve("camel-brainstorm/guides/version-selection.md"));
        String camelVersion = Files.readString(root.resolve("camel-migrate/guides/camel-version-phase2.md"));
        String biztalkMap = Files.readString(root.resolve("camel-migrate/guides/biztalk-map-conversion.md"));
        String componentMap = Files.readString(root.resolve("camel-migrate/guides/camel2-component-mapping.md"));
        String brainstorm = Files.readString(root.resolve("camel-brainstorm/SKILL.md"));

        assertTrue(migrate.contains("project.runtime={{RUNTIME}}"));
        assertFalse(migrate.contains("project.runtime=quarkus"));
        assertTrue(migrate.contains("Maven plugin or build/code-generation task"));
        assertTrue(migrate.contains("Recheck the completed design before approval"));
        assertTrue(versionSelection.contains("project.runtime=main"));
        assertTrue(versionSelection.contains("project.runtime=spring-boot"));
        assertTrue(versionSelection.contains("project.runtime=quarkus"));
        assertTrue(camelVersion.contains("first extract source/target semantic field mappings"));
        assertTrue(camelVersion.contains("preserve it as XSLT"));
        assertTrue(biztalkMap.contains("Do not select an implementation engine per functoid"));
        assertTrue(biztalkMap.contains("Count all source and target leaf fields"));
        assertTrue(componentMap.contains("canonical inline Groovy or XSLT DataMapper"));
        assertTrue(brainstorm.contains("This skill is greenfield-only"));
        assertFalse(brainstorm.contains("Load migration-discovery.md"));
        assertFalse(brainstorm.contains("mg_graph"));
    }

    @Test
    void runtimeVerificationIsSinglePassAndDockerIsTestSpecific() throws IOException {
        Path root = repositoryRoot().resolve("camel-kit-core/src/main/resources/skills");
        String execute = Files.readString(root.resolve("camel-execute/SKILL.md"));
        String verifySkill = Files.readString(root.resolve("camel-verify/SKILL.md"));
        String verify = Files.readString(root.resolve("camel-verify/guides/verify-loop.md"));
        String testRunner = Files.readString(root.resolve("camel-test/guides/test-runner.md"));
        String smoke = Files.readString(root.resolve("camel-implement/guides/smoke-test.md"));
        String errorTaxonomy = Files.readString(root.resolve("camel-verify/guides/error-taxonomy.md"));
        String normalizedSmoke = smoke.replaceAll("\\s+", " ");

        assertTrue(execute.contains("Do not run a smoke/build command here"));
        assertFalse(execute.contains("3. Run the smoke test"));
        assertTrue(verify.contains("Spring Boot/Quarkus Phase 1 runs only when both Maven and the JDK"));
        assertTrue(verify.contains("Main Phase 1 runs only when both JBang and the JDK"));
        assertTrue(verify.contains("inspect every discovered file"));
        assertTrue(verify.contains("container-free/mock-only file"));
        assertTrue(verifySkill.contains("Maven compilation and Citrus testing retry up to 15 times"));
        assertTrue(verifySkill.contains("Camel Main startup retries up to 6 times"));
        assertFalse(verifySkill.contains("Each phase retries up to 15 times"));
        assertTrue(verify.contains("{MAVEN_CMD} -f {MODULE_DIR}pom.xml compile -q"));
        assertTrue(testRunner.contains("container-free and mock-only tests do not require Docker"));
        assertTrue(normalizedSmoke.contains("never run an unqualified `docker compose up -d` before `./run.sh`"));
        assertTrue(smoke.contains("docker compose -f {MODULE_DIR}docker-compose.yaml up -d"));
        assertTrue(smoke.contains("./mvnw -f {MODULE_DIR}pom.xml spring-boot:run"));
        assertTrue(smoke.contains("./mvnw -f {MODULE_DIR}pom.xml quarkus:dev"));
        assertFalse(smoke.contains("cd {MODULE_DIR} &&"));
        assertTrue(errorTaxonomy.contains("Never generate Docker Compose merely because a test connection"));
    }

    @Test
    void validationAndReplanUseResolvedCurrentArtifacts() throws IOException {
        Path root = repositoryRoot().resolve("camel-kit-core/src/main/resources/skills");
        String validate = Files.readString(root.resolve("camel-validate/SKILL.md"));
        String schema = Files.readString(root.resolve("camel-validate/guides/schema-validation.md"));
        String endpoint = Files.readString(root.resolve("camel-validate/guides/endpoint-validation.md"));
        String replan = Files.readString(root.resolve("camel-execute/guides/re-plan-loop.md"));

        assertTrue(validate.contains("Main: `ROUTE_FILES` is every `{MODULE_PREFIX}*.camel.yaml`"));
        assertTrue(validate.contains(
                "`{MODULE_PREFIX}src/main/resources/camel/*.camel.yaml`"));
        assertTrue(validate.contains("`{MODULE_PREFIX}` is empty at the project root"));
        assertTrue(schema.contains("-Dcamel.validator.files={ROUTE_FILE}"));
        assertTrue(endpoint.contains("camel run --check {ROUTE_FILE} {PROPS_FILE}"));
        assertTrue(replan.contains("Never execute tasks copied from the stale original plan"));
        assertTrue(replan.contains("plan analyze <plan-path>"));
        assertTrue(replan.contains("doc unstale <plan-path>"));
    }

    @Test
    void camelTestGuidesUseConcreteMcpValidationContract() throws IOException {
        Path root = repositoryRoot();
        String mcpSetup = Files.readString(root.resolve(
                "camel-kit-core/src/main/resources/skills/shared/mcp-setup.md"));
        String routeAnalysis = Files.readString(root.resolve(
                "camel-kit-core/src/main/resources/skills/camel-test/guides/route-analysis.md"));
        String testGeneration = Files.readString(root.resolve(
                "camel-kit-core/src/main/resources/skills/camel-test/guides/test-generation.md"));

        assertTrue(mcpSetup.contains("CITRUS_MCP_VERSION == CITRUS_VERSION"),
                "Citrus MCP catalog usage must be gated on matching server and framework versions");
        assertTrue(mcpSetup.contains("\"command\": \"jbang\""));
        assertTrue(mcpSetup.contains("\"--repos\", \"{CAMEL_MCP_REPOS}\""));
        assertTrue(mcpSetup.contains("org.apache.camel:camel-jbang-mcp:{CAMEL_MCP_VERSION}:runner"));
        assertFalse(mcpSetup.contains(".camel-kit/mcp/"));
        assertFalse(mcpSetup.contains("java -jar"));
        assertTrue(routeAnalysis.contains("camel_validate_route"),
                "camel-test route analysis must validate routes before generating tests");
        assertTrue(routeAnalysis.contains("camel_validate_yaml_dsl"),
                "camel-test route analysis must validate YAML DSL syntax when applicable");
        assertTrue(routeAnalysis.contains("camel_route_harden_context"),
                "camel-test route analysis must use hardening findings for negative scenarios");
        assertTrue(routeAnalysis.contains("camel_component_properties"),
                "camel-test route analysis must inspect component metadata before writing endpoint config");
        assertTrue(routeAnalysis.contains("citrus_docs_index"),
                "camel-test should discover Citrus docs pages before reading them");
        assertFalse(routeAnalysis.contains("Suggested Test Scenarios (from MCP analysis)"),
                "camel_route_context output must not be presented as ready-made test scenarios");

        assertFalse(testGeneration.contains("\"<action-name>\""),
                "Citrus action schema validation must use actual generated action names");
        assertFalse(testGeneration.contains("\"<endpoint-name>\""),
                "Citrus endpoint schema validation must use actual generated endpoint names");
        assertTrue(testGeneration.contains("For each ACTION in ACTIONS_USED"),
                "test-generation must iterate over actual generated actions");
        assertTrue(testGeneration.contains("For each ENDPOINT in ENDPOINTS_USED"),
                "test-generation must iterate over actual generated endpoints");
        assertFalse(testGeneration.contains("<pinned-compatible-version>"),
                "test-generation must not emit unresolved dependency version placeholders");
        assertTrue(testGeneration.contains("org.testcontainers:postgresql:RELEASE"),
                "test-generation must provide a resolvable PostgreSQL Testcontainers dependency");
        assertTrue(testGeneration.contains("org.testcontainers:mongodb:RELEASE"),
                "test-generation must provide a resolvable MongoDB Testcontainers dependency");
        assertTrue(testGeneration.contains("`{TEST_DIR}jbang.properties`"),
                "JBang test dependencies must be written beside the generated test resources");
        assertFalse(testGeneration.contains("`test/jbang.properties`"),
                "test-generation must not hard-code the legacy test directory");
    }

    private static void assertKnowledgeTools(String agentName, String field, JsonNode allowlist) throws IOException {
        assertNotNull(allowlist, "Missing " + field + " allowlist for " + agentName);
        assertTrue(allowlist.isArray(), field + " allowlist for " + agentName + " must be an array");

        Set<String> actual = new LinkedHashSet<>();
        allowlist.forEach(node -> actual.add(node.asText()));
        assertEquals(new LinkedHashSet<>(
                WorkflowManifestLoader.loadDefault()
                        .mcpServer("camel-knowledge")
                        .allowedTools()),
                actual,
                field + " allowlist for " + agentName + " must match KnowledgeMcpServer tools");
    }

    private static void assertCitrusTools(String agentName, String field, JsonNode allowlist) throws IOException {
        assertNotNull(allowlist, "Missing " + field + " allowlist for " + agentName);
        assertTrue(allowlist.isArray(), field + " allowlist for " + agentName + " must be an array");

        Set<String> actual = new LinkedHashSet<>();
        allowlist.forEach(node -> actual.add(node.asText()));
        assertEquals(new LinkedHashSet<>(
                WorkflowManifestLoader.loadDefault()
                        .mcpServer("citrus")
                        .allowedTools()),
                actual,
                field + " allowlist for " + agentName + " must match Citrus MCP tools");
    }

    private JsonNode serverConfig(String agentName, Path projectDir, String serverId) throws IOException {
        AgentConfig agent = AgentRegistry.get(agentName);
        assertNotNull(agent, "Unexpected agent: " + agentName);
        Path configFile = projectDir.resolve(agent.mcpConfigPath());
        JsonNode root = MAPPER.readTree(configFile.toFile());
        JsonNode servers = root.get(agent.mcpServerContainerKey());
        assertNotNull(servers, "Missing MCP server config for " + agentName);
        JsonNode server = servers.get(serverId);
        assertNotNull(server, "Missing " + serverId + " MCP server config for " + agentName);
        return server;
    }

    private TomlTable tomlServerConfig(String agentName, Path projectDir, String serverId) throws IOException {
        AgentConfig agent = AgentRegistry.get(agentName);
        var config = Toml.parse(projectDir.resolve(agent.mcpConfigPath()));
        assertFalse(config.hasErrors(), config.errors().toString());
        TomlTable servers = config.getTable(agent.mcpServerContainerKey());
        assertNotNull(servers, "Missing MCP server config for " + agentName);
        TomlTable server = servers.getTable(serverId);
        assertNotNull(server, "Missing " + serverId + " MCP server config for " + agentName);
        return server;
    }

    private static void assertTomlTools(
            String agentName, String field, TomlTable server, String workflowServer)
            throws IOException {
        TomlArray allowlist = server.getArray(field);
        assertNotNull(allowlist, "Missing " + field + " allowlist for " + agentName);
        Set<String> actual = java.util.stream.IntStream.range(0, allowlist.size())
                .mapToObj(allowlist::getString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(new LinkedHashSet<>(
                WorkflowManifestLoader.loadDefault().mcpServer(workflowServer).allowedTools()), actual,
                field + " allowlist for " + agentName + " must match " + workflowServer + " tools");
    }

    private static List<Path> activeResourceFiles(Path root) throws IOException {
        List<Path> scanRoots = List.of(
                root.resolve("camel-kit-core/src/main/resources/skills"),
                root.resolve("camel-kit-core/src/main/resources/agents"),
                root.resolve("camel-kit-core/src/main/resources/templates"),
                root.resolve("README.md"),
                root.resolve("CONTRIBUTING.md"));

        List<Path> files = new ArrayList<>();
        addTopLevelDocs(root, files);
        for (Path scanRoot : scanRoots) {
            if (Files.isRegularFile(scanRoot)) {
                if (isScannedTextFile(scanRoot)) {
                    files.add(scanRoot);
                }
                continue;
            }
            try (Stream<Path> paths = Files.walk(scanRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(ResourceConsistencyTest::isScannedTextFile)
                        .forEach(files::add);
            }
        }
        files.sort(Comparator.comparing(path -> root.relativize(path).toString()));
        return files;
    }

    private static void addTopLevelDocs(Path root, List<Path> files) throws IOException {
        Path docsDir = root.resolve("docs");
        if (!Files.isDirectory(docsDir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(docsDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(ResourceConsistencyTest::isScannedTextFile)
                    .forEach(files::add);
        }
    }

    private static boolean isScannedTextFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".md")
                || name.endsWith(".json")
                || name.endsWith(".toml")
                || name.endsWith(".yaml")
                || name.endsWith(".yml")
                || name.equals("geminiignore")
                || name.equals("qwenignore");
    }

    private static InitContext createContext(String agentName, Path projectDir) {
        AgentConfig agent = AgentRegistry.get(agentName);
        Path commandsDir = agent.generatesCommandStubs()
                ? projectDir.resolve(agent.commandDirectory())
                : projectDir.resolve(".codex/commands");
        Path skillsDir = projectDir.resolve(agent.skillsDirectory());
        return new InitContext(
                agent, agentName, commandsDir, skillsDir, projectDir,
                "camel-kit", Printer.noop());
    }

    private static List<String> sortedAgentNames() {
        return AgentRegistry.names().stream()
                .sorted()
                .toList();
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return name;
        }
        return name.substring(0, dot);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                    && Files.exists(current.resolve("camel-kit-core/src/main/resources"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from " + Path.of("").toAbsolutePath());
    }

    private record StalePattern(String description, Pattern pattern) {
    }
}

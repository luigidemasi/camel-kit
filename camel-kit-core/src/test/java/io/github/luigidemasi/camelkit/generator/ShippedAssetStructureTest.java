package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifestLoader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

import static org.junit.jupiter.api.Assertions.*;

class ShippedAssetStructureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEST_COMMAND_PREFIX = "camel kit";
    private static final List<String> RETIRED_SHIP_GUIDES = List.of(
            "auto-fix-loop.md",
            "oversight-matrix.md",
            "state-management.md");
    // Command directories that non-stub agents historically used; generation must never recreate them.
    private static final Map<String, String> LEGACY_COMMAND_DIRS = Map.of(
            "codex", ".codex/commands",
            "copilot", ".github/commands");

    private static final Pattern CODE_SPAN = Pattern.compile("`([^`\\n]+\\.md)`");
    private static final Pattern GENERATED_SKILL_REFERENCE
            = Pattern.compile("(?<path>\\.[A-Za-z0-9_-]+/skills/[A-Za-z0-9_./-]+/SKILL\\.md)");

    @TempDir
    Path tempDir;

    @Test
    void workflowManifestSkillsHaveShippedSkillAssets() throws Exception {
        WorkflowManifest manifest = WorkflowManifestLoader.loadDefault();
        Path skillsDir = resourcePath("skills");

        Set<String> shippedSkills;
        try (Stream<Path> paths = Files.list(skillsDir)) {
            shippedSkills = paths.filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("SKILL.md")))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toCollection(java.util.TreeSet::new));
        }

        Set<String> manifestSkills = manifest.skills().stream()
                .map(WorkflowManifest.WorkflowSkill::name)
                .collect(Collectors.toCollection(java.util.TreeSet::new));

        assertEquals(manifestSkills, shippedSkills,
                "Every workflow manifest skill must have a shipped skills/<name>/SKILL.md file");
    }

    @Test
    void skillGuideManifestReferencesResolveToExistingAssets() throws Exception {
        Path resourcesDir = resourceRoot();
        Path skillsDir = resourcesDir.resolve("skills");

        List<String> missingReferences = new ArrayList<>();
        try (Stream<Path> paths = Files.list(skillsDir)) {
            paths.filter(Files::isDirectory)
                    .map(path -> path.resolve("SKILL.md"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .forEach(path -> validateSkillGuideManifestReferences(resourcesDir, skillsDir, path,
                            missingReferences));
        }

        assertTrue(missingReferences.isEmpty(),
                "Skill guide manifest tables reference missing assets:\n"
                                                + String.join("\n", missingReferences));
    }

    @Test
    void sharedGuideReferencesResolveToExistingAssets() throws Exception {
        Path resourcesDir = resourceRoot();
        Path skillsDir = resourcesDir.resolve("skills");
        Path templatesDir = resourcesDir.resolve("templates");

        List<String> missingReferences = new ArrayList<>();
        for (Path source : shippedMarkdownFiles(skillsDir, templatesDir)) {
            String content = Files.readString(source);
            Matcher matcher = CODE_SPAN.matcher(content);
            while (matcher.find()) {
                String reference = matcher.group(1).strip();
                resolveSharedGuideReference(resourcesDir, reference).ifPresent(target -> {
                    if (!Files.isRegularFile(target)) {
                        missingReferences.add(resourcesDir.relativize(source) + " references missing asset `"
                                              + reference + "` -> " + resourcesDir.relativize(target));
                    }
                });
            }
        }

        assertTrue(missingReferences.isEmpty(),
                "Shipped skill/template shared guide references point to missing assets:\n"
                                                + String.join("\n", missingReferences));
    }

    @Test
    void dispatchAndMcpTemplatesExistForEverySupportedAgent() throws Exception {
        Path resourcesDir = resourceRoot();

        List<String> missingAssets = new ArrayList<>();
        for (String agentName : sortedAgentNames()) {
            AgentDescriptor descriptor = AgentRegistry.descriptor(agentName);
            assertNotNull(descriptor, "AgentRegistry listed unknown descriptor '" + agentName + "'");
            if (!Files.isRegularFile(resourcesDir.resolve(descriptor.dispatchTemplatePath()))) {
                missingAssets.add(descriptor.dispatchTemplatePath());
            }

            AgentConfig agent = AgentRegistry.get(agentName);
            assertNotNull(agent, "AgentRegistry listed unknown agent '" + agentName + "'");
            if (!Files.isRegularFile(resourcesDir.resolve(agent.mcpConfigTemplatePath()))) {
                missingAssets.add(agent.mcpConfigTemplatePath());
            }
        }

        assertTrue(missingAssets.isEmpty(),
                "Supported agents are missing dispatch or MCP config templates:\n"
                                            + String.join("\n", missingAssets));
    }

    @Test
    void agentDescriptorTemplateSourcesExist() throws Exception {
        Path resourcesDir = resourceRoot();

        List<String> missingAssets = new ArrayList<>();
        for (AgentDescriptor descriptor : AgentRegistry.descriptors().values()) {
            for (AgentDescriptor.TemplateInstall template : descriptor.templates()) {
                if (!Files.isRegularFile(resourcesDir.resolve(template.source()))) {
                    missingAssets.add(descriptor.id() + " -> " + template.source());
                }
            }
        }

        assertTrue(missingAssets.isEmpty(),
                "Agent descriptors reference missing template sources:\n"
                                            + String.join("\n", missingAssets));
    }

    @Test
    void traitFilesTargetExistingSkillAssets() throws Exception {
        Path resourcesDir = resourceRoot();
        Path traitsDir = resourcesDir.resolve("templates/traits");

        List<String> invalidTraits = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(traitsDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".append.md"))
                    .sorted()
                    .forEach(path -> validateTraitTarget(resourcesDir, traitsDir, path, invalidTraits));
        }

        assertTrue(invalidTraits.isEmpty(),
                "Agent trait files target missing skills or guides:\n"
                                            + String.join("\n", invalidTraits));
    }

    @Test
    void generatedAgentAssetsIncludeShippedTraits() throws Exception {
        Path resourcesDir = resourceRoot();
        Path traitsDir = resourcesDir.resolve("templates/traits");

        List<String> missingTraits = new ArrayList<>();
        for (String agentName : sortedAgentNames()) {
            InitContext ctx = createContext(agentName, tempDir.resolve("traits-" + agentName));
            AgentGeneratorFactory.create(agentName).generate(ctx);

            Path agentTraitsDir = traitsDir.resolve(agentName);
            if (!Files.isDirectory(agentTraitsDir)) {
                continue;
            }

            try (Stream<Path> paths = Files.walk(agentTraitsDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".append.md"))
                        .sorted()
                        .forEach(path -> validateGeneratedTrait(resourcesDir, traitsDir, ctx, path, missingTraits));
            }
        }

        assertTrue(missingTraits.isEmpty(),
                "Generated agent assets are missing shipped trait content:\n"
                                            + String.join("\n", missingTraits));
    }

    @Test
    void generatedAgentAssetsAreStructurallyCoherent() throws Exception {
        WorkflowManifest manifest = WorkflowManifestLoader.loadDefault();
        Map<String, WorkflowManifest.WorkflowCommand> generatedCommands = manifest.generatedCommandStubs().stream()
                .collect(Collectors.toMap(
                        WorkflowManifest.WorkflowCommand::name,
                        command -> command,
                        (left, right) -> left,
                        LinkedHashMap::new));

        for (String agentName : sortedAgentNames()) {
            InitContext ctx = createContext(agentName, tempDir.resolve(agentName), TEST_COMMAND_PREFIX);
            Set<String> seededCommandNeighbors = seedRetiredShipAssets(ctx);
            AgentGeneratorFactory.create(agentName).generate(ctx);

            boolean shipSkillOnly = generatedCommands.get("camel-ship").isSkillOnly(agentName);
            if (ctx.agent().generatesCommandStubs()) {
                assertGeneratedCommandFiles(agentName, ctx, generatedCommands, seededCommandNeighbors);
                assertGeneratedSkillReferencesResolve(agentName, ctx, generatedCommands);
            } else {
                String legacyCommands = LEGACY_COMMAND_DIRS.get(agentName);
                assertNotNull(legacyCommands, "No legacy command directory known for non-stub agent " + agentName);
                assertFalse(Files.exists(ctx.projectDir().resolve(legacyCommands)),
                        agentName + " must not generate command scaffolding");
            }
            assertGeneratedMarkdownResolvesCommandPrefix(agentName, ctx);
            assertGeneratedShipDelegate(agentName, ctx, shipSkillOnly);
            assertRetiredShipAssetsWereCleaned(agentName, ctx, shipSkillOnly);
            assertGeneratedMcpConfigIsValid(agentName, ctx);
        }
    }

    private static void assertGeneratedMarkdownResolvesCommandPrefix(String agentName, InitContext ctx)
            throws IOException {
        try (Stream<Path> files = Files.walk(ctx.projectDir())) {
            List<String> unresolved = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("{COMMAND_PREFIX}");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .map(path -> ctx.projectDir().relativize(path).toString())
                    .toList();
            assertTrue(unresolved.isEmpty(),
                    agentName + " generated Markdown retains {COMMAND_PREFIX}: " + unresolved);
        }
    }

    private static Set<String> seedRetiredShipAssets(InitContext ctx) throws IOException {
        Path guidesDir = ctx.skillsDir().resolve("camel-ship/guides");
        Files.createDirectories(guidesDir);
        for (String guide : RETIRED_SHIP_GUIDES) {
            Files.writeString(guidesDir.resolve(guide), "legacy generated guide");
        }
        Files.writeString(guidesDir.resolve("keep.md"), "user-owned neighboring file");

        if ("bob2".equals(ctx.agentName())) {
            Path rulesDir = ctx.projectDir().resolve(".bob/rules-camel-ship");
            Files.createDirectories(rulesDir);
            Files.writeString(rulesDir.resolve("ship.md"), "legacy generated rule");
            Files.writeString(rulesDir.resolve("keep.md"), "user-owned neighboring file");
        }
        if ("pi".equals(ctx.agentName())) {
            Files.createDirectories(ctx.commandsDir());
            Files.writeString(ctx.commandsDir().resolve("camel-ship.md"), "legacy lossy prompt");
            Files.writeString(ctx.commandsDir().resolve("keep.md"), "user-owned neighboring prompt");
            return Set.of("keep.md");
        }
        return Set.of();
    }

    private static void assertGeneratedShipDelegate(String agentName, InitContext ctx, boolean shipSkillOnly)
            throws IOException {
        Path skill = ctx.skillsDir().resolve("camel-ship/SKILL.md");
        assertTrue(Files.isRegularFile(skill), agentName + " must install the Ship skill");

        String skillContent = Files.readString(skill);
        assertTrue(skillContent.contains(ctx.commandPrefix() + " ship"),
                agentName + " Ship skill must use the configured command prefix");
        assertFalse(skillContent.contains("{COMMAND_PREFIX}"),
                agentName + " Ship skill must not retain an unresolved command placeholder");
        assertFalse(skillContent.contains("### Stage Execution"),
                agentName + " Ship skill must not execute pipeline stages itself");
        assertFalse(skillContent.contains("<!-- TRAIT:" + agentName + " -->"),
                agentName + " Ship skill must not receive an orchestration trait");
        assertFalse(skillContent.contains("## Dispatch"),
                agentName + " Ship skill must not receive an orchestration dispatch block");
        assertTrue(skillContent.contains("using the invocation's Ship options. Add no defaults"),
                agentName + " Ship skill must pass the invocation options without inventing defaults");
        assertTrue(skillContent.contains("Return the command output and whether it succeeded"),
                agentName + " Ship skill must return the local command result");
        for (String guide : RETIRED_SHIP_GUIDES) {
            assertFalse(skillContent.contains(guide),
                    agentName + " Ship skill must not reference retired guide " + guide);
        }

        if (!ctx.agent().generatesCommandStubs()) {
            return;
        }

        Path command = ctx.commandsDir().resolve("camel-ship." + ctx.agent().fileFormat());
        if (shipSkillOnly) {
            assertFalse(Files.exists(command),
                    agentName + " must expose Ship only through its quote-preserving native skill");
            return;
        }
        String commandContent = Files.readString(command);
        // Golden expectations: hard-coded per agent so a wrong registry placeholder cannot
        // self-validate through ctx.agent().argPlaceholder().
        String expectedInvocation = switch (agentName) {
            case "claude", "opencode" ->
                "Run `" + ctx.commandPrefix() + " ship $ARGUMENTS` once using the supplied Ship options.";
            case "gemini", "qwen" ->
                "Run `" + ctx.commandPrefix() + " ship {{args}}` once using the supplied Ship options.";
            case "bob", "bob2" -> "Run `" + ctx.commandPrefix()
                                  + " ship` once, appending every option supplied to this command invocation "
                                  + "verbatim.";
            default -> throw new AssertionError(
                    "No golden Ship stub expectation for agent " + agentName
                                                + " — add one before registering the agent");
        };
        assertTrue(commandContent.contains(expectedInvocation),
                agentName + " Ship command must forward its arguments to the configured local controller");
        assertTrue(commandContent.contains("Add no defaults and do not orchestrate the workflow yourself"),
                agentName + " Ship command must pass supplied options without inventing defaults");
        assertTrue(commandContent.contains("Return the command output and whether it succeeded"),
                agentName + " Ship command must return the local command result");
        assertFalse(commandContent.contains("camel-ship/SKILL.md"),
                agentName + " Ship command must not delegate to a prompt-owned workflow");

        if ("bob2".equals(agentName)) {
            assertTrue(commandContent.contains("argument-hint: \"[ship-options]\""),
                    "Bob2 Ship must advertise CLI options instead of a positional request");
            String modes = Files.readString(ctx.projectDir().resolve(".bob/custom_modes.yaml"));
            assertFalse(modes.contains("slug: camel-ship"), "Bob2 must not install the retired Ship mode");
            assertFalse(Files.exists(ctx.projectDir().resolve(".bob/rules-camel-ship/ship.md")),
                    "Bob2 must not install the retired Ship mode rule");
        }
    }

    private static void assertRetiredShipAssetsWereCleaned(String agentName, InitContext ctx, boolean shipSkillOnly) {
        Path guidesDir = ctx.skillsDir().resolve("camel-ship/guides");
        for (String guide : RETIRED_SHIP_GUIDES) {
            assertFalse(Files.exists(guidesDir.resolve(guide)),
                    agentName + " re-init must remove retired Ship guide " + guide);
        }
        assertTrue(Files.isRegularFile(guidesDir.resolve("keep.md")),
                agentName + " re-init must preserve unrelated neighboring files");

        if ("bob2".equals(agentName)) {
            assertTrue(Files.isRegularFile(ctx.projectDir().resolve(".bob/rules-camel-ship/keep.md")),
                    "Bob2 re-init must preserve unrelated neighboring rules");
        }
        if (shipSkillOnly) {
            assertFalse(Files.exists(ctx.commandsDir().resolve("camel-ship.md")),
                    agentName + " re-init must remove the retired lossy Ship prompt");
            assertTrue(Files.isRegularFile(ctx.commandsDir().resolve("keep.md")),
                    agentName + " re-init must preserve unrelated neighboring prompts");
        }
    }

    private static List<Path> shippedMarkdownFiles(Path skillsDir, Path templatesDir) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path root : List.of(skillsDir, templatesDir)) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".md"))
                        .forEach(files::add);
            }
        }
        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    private static void validateSkillGuideManifestReferences(
            Path resourcesDir, Path skillsDir, Path skillFile, List<String> missingReferences) {
        try {
            boolean inGuideSection = false;
            for (String line : Files.readAllLines(skillFile)) {
                if (line.startsWith("## ")) {
                    inGuideSection = line.toLowerCase(Locale.ROOT).contains("guide");
                    continue;
                }
                if (!inGuideSection || !line.startsWith("|") || !line.contains(".md")) {
                    continue;
                }

                Matcher matcher = CODE_SPAN.matcher(line);
                while (matcher.find()) {
                    String reference = matcher.group(1).strip();
                    resolveGuideManifestReference(resourcesDir, skillsDir, skillFile, reference).ifPresent(target -> {
                        if (!Files.isRegularFile(target)) {
                            missingReferences.add(resourcesDir.relativize(skillFile)
                                                  + " guide table references missing asset `" + reference + "` -> "
                                                  + resourcesDir.relativize(target));
                        }
                    });
                }
            }
        } catch (IOException e) {
            missingReferences.add(resourcesDir.relativize(skillFile) + " could not be read: " + e.getMessage());
        }
    }

    private static Optional<Path> resolveGuideManifestReference(
            Path resourcesDir, Path skillsDir, Path skillFile, String reference) {
        return ShippedReference.parse(reference)
                .resolve(EnumSet.allOf(ShippedReferenceKind.class),
                        new ReferenceContext(resourcesDir, skillsDir, skillFile));
    }

    private static Optional<Path> resolveSharedGuideReference(Path resourcesDir, String reference) {
        Path skillsDir = resourcesDir.resolve("skills");
        return ShippedReference.parse(reference)
                .resolve(EnumSet.of(ShippedReferenceKind.SHARED_GUIDE),
                        new ReferenceContext(resourcesDir, skillsDir, null));
    }

    private record ReferenceContext(Path resourcesDir, Path skillsDir, Path source) {
    }

    private record ShippedReference(String path) {

        private static ShippedReference parse(String reference) {
            String normalized = reference.strip()
                    .replace("\\", "/");
            while (normalized.startsWith("./")) {
                normalized = normalized.substring(2);
            }
            int skillsIndex = normalized.indexOf("/skills/");
            if (skillsIndex >= 0) {
                normalized = normalized.substring(skillsIndex + "/skills/".length());
            }
            return new ShippedReference(normalized);
        }

        private Optional<Path> resolve(EnumSet<ShippedReferenceKind> allowedKinds, ReferenceContext context) {
            for (ShippedReferenceKind kind : allowedKinds) {
                if (kind.matches(this)) {
                    return kind.resolve(this, context);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Structural references intentionally support only shipped asset paths, not generated project artifacts such as
     * design-spec.md. Add new reference forms here so their resolution stays explicit.
     */
    private enum ShippedReferenceKind {
        LOCAL_GUIDE("guides/") {
            @Override
            Optional<Path> resolve(ShippedReference reference, ReferenceContext context) {
                return owningSkill(context.source(), context.skillsDir())
                        .map(skill -> context.skillsDir().resolve(skill).resolve(reference.path()));
            }
        },
        SKILL("camel-") {
            @Override
            Optional<Path> resolve(ShippedReference reference, ReferenceContext context) {
                return Optional.of(context.skillsDir().resolve(reference.path()));
            }
        },
        SHARED_GUIDE("shared/") {
            @Override
            Optional<Path> resolve(ShippedReference reference, ReferenceContext context) {
                return Optional.of(context.skillsDir().resolve(reference.path()));
            }
        },
        AGENT("agents/") {
            @Override
            Optional<Path> resolve(ShippedReference reference, ReferenceContext context) {
                return Optional.of(context.resourcesDir().resolve(reference.path()));
            }
        };

        private final String prefix;

        ShippedReferenceKind(String prefix) {
            this.prefix = prefix;
        }

        private boolean matches(ShippedReference reference) {
            return reference.path().startsWith(prefix);
        }

        abstract Optional<Path> resolve(ShippedReference reference, ReferenceContext context);
    }

    private static Optional<String> owningSkill(Path source, Path skillsDir) {
        if (source == null || !source.startsWith(skillsDir)) {
            return Optional.empty();
        }
        Path relative = skillsDir.relativize(source);
        if (relative.getNameCount() < 2) {
            return Optional.empty();
        }
        String skillName = relative.getName(0).toString();
        if ("shared".equals(skillName)) {
            return Optional.empty();
        }
        return Optional.of(skillName);
    }

    private static void validateTraitTarget(
            Path resourcesDir, Path traitsDir, Path traitFile, List<String> invalidTraits) {
        Path relative = traitsDir.relativize(traitFile);
        if (relative.getNameCount() < 2) {
            invalidTraits.add(resourcesDir.relativize(traitFile) + " is not under traits/<agent>/");
            return;
        }

        String agentName = relative.getName(0).toString();
        if (!AgentRegistry.contains(agentName)) {
            invalidTraits.add(resourcesDir.relativize(traitFile) + " uses unsupported agent '" + agentName + "'");
        }

        String skillName = stripAppendSuffix(relative.getName(1).toString());
        Path target;
        if (relative.getNameCount() == 2) {
            target = resourcesDir.resolve("skills").resolve(skillName).resolve("SKILL.md");
        } else {
            String guideName = stripAppendSuffix(relative.getFileName().toString());
            target = resourcesDir.resolve("skills").resolve(skillName).resolve("guides").resolve(guideName + ".md");
        }
        if (!Files.isRegularFile(target)) {
            invalidTraits.add(resourcesDir.relativize(traitFile) + " targets missing "
                              + resourcesDir.relativize(target));
        }
    }

    private static void validateGeneratedTrait(
            Path resourcesDir, Path traitsDir, InitContext ctx, Path traitFile, List<String> missingTraits) {
        Path target = generatedTraitTarget(traitsDir, ctx, traitFile);
        String source = resourcesDir.relativize(traitFile).toString();
        String targetDisplay = ctx.projectDir().relativize(target).toString();
        try {
            if (!Files.isRegularFile(target)) {
                missingTraits.add(source + " target was not generated at " + targetDisplay);
                return;
            }

            String generatedContent = Files.readString(target);
            String sentinel = "<!-- TRAIT:" + ctx.agentName() + " -->";
            if (!generatedContent.contains(sentinel)) {
                missingTraits.add(source + " did not add sentinel to generated " + targetDisplay);
            }

            String traitContent = Files.readString(traitFile)
                    .replace("{COMMAND_PREFIX}", ctx.commandPrefix())
                    .strip();
            if (!traitContent.isEmpty() && !generatedContent.contains(traitContent)) {
                missingTraits.add(source + " content was not appended to generated " + targetDisplay);
            }
        } catch (IOException e) {
            missingTraits.add(source + " could not be verified against generated " + targetDisplay
                              + ": " + e.getMessage());
        }
    }

    private static Path generatedTraitTarget(Path traitsDir, InitContext ctx, Path traitFile) {
        Path relative = traitsDir.relativize(traitFile);
        String skillName = stripAppendSuffix(relative.getName(1).toString());
        if (relative.getNameCount() == 2) {
            return ctx.skillsDir().resolve(skillName).resolve("SKILL.md");
        }

        String guideName = stripAppendSuffix(relative.getFileName().toString());
        return ctx.skillsDir().resolve(skillName).resolve("guides").resolve(guideName + ".md");
    }

    private static String stripAppendSuffix(String fileName) {
        return fileName.endsWith(".append.md")
                ? fileName.substring(0, fileName.length() - ".append.md".length())
                : fileName;
    }

    private static void assertGeneratedCommandFiles(
            String agentName, InitContext ctx, Map<String, WorkflowManifest.WorkflowCommand> generatedCommands,
            Set<String> seededCommandNeighbors)
            throws IOException {
        Set<String> expected = generatedCommands.values().stream()
                .filter(command -> !command.isSkillOnly(agentName))
                .map(command -> command.name() + "." + ctx.agent().fileFormat())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        // Exact-contents comparison: the seeded user-owned neighbors must survive, everything
        // else in the commands directory must be a manifest-generated stub.
        expected.addAll(seededCommandNeighbors);
        Set<String> actual;
        try (Stream<Path> files = Files.list(ctx.commandsDir())) {
            actual = files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toCollection(java.util.TreeSet::new));
        }
        assertEquals(expected, actual, agentName + " command files must match manifest generated commands");
    }

    private static void assertGeneratedSkillReferencesResolve(
            String agentName, InitContext ctx, Map<String, WorkflowManifest.WorkflowCommand> generatedCommands)
            throws IOException {
        for (WorkflowManifest.WorkflowCommand command : generatedCommands.values()) {
            if (command.isSkillOnly(agentName)) {
                continue;
            }
            Path commandFile = ctx.commandsDir().resolve(command.name() + "." + ctx.agent().fileFormat());
            assertTrue(Files.isRegularFile(commandFile), agentName + " missing command file " + commandFile);
            assertTrue(Files.isRegularFile(ctx.skillsDir().resolve(command.skill() + "/SKILL.md")),
                    agentName + " command " + command.name() + " points to missing copied skill " + command.skill());

            String content = Files.readString(commandFile);
            Matcher matcher = GENERATED_SKILL_REFERENCE.matcher(content);
            while (matcher.find()) {
                Path referenced = ctx.projectDir().resolve(matcher.group("path"));
                assertTrue(Files.isRegularFile(referenced),
                        agentName + " command " + command.name() + " references missing " + matcher.group("path"));
            }
        }
    }

    private static void assertGeneratedMcpConfigIsValid(String agentName, InitContext ctx) throws IOException {
        Path configPath = ctx.projectDir().resolve(ctx.agent().mcpConfigPath());
        assertTrue(Files.isRegularFile(configPath), agentName + " MCP config must be generated at " + configPath);

        if ("toml".equals(ctx.agent().mcpConfigFormat())) {
            var root = Toml.parse(configPath);
            assertFalse(root.hasErrors(), root.errors().toString());
            TomlTable servers = root.getTable(ctx.agent().mcpServerContainerKey());
            assertNotNull(servers, agentName + " MCP config missing " + ctx.agent().mcpServerContainerKey());
            assertInstanceOf(TomlTable.class, servers.get("camel"), agentName + " MCP config missing camel server");
            assertInstanceOf(TomlTable.class, servers.get("camel-knowledge"),
                    agentName + " MCP config missing camel-knowledge server");
            assertInstanceOf(TomlTable.class, servers.get("citrus"),
                    agentName + " MCP config missing citrus server");
            return;
        }

        JsonNode root = MAPPER.readTree(configPath.toFile());
        JsonNode servers = root.get(ctx.agent().mcpServerContainerKey());
        assertNotNull(servers, agentName + " MCP config missing " + ctx.agent().mcpServerContainerKey());
        assertTrue(servers.has("camel"), agentName + " MCP config missing camel server");
        assertTrue(servers.has("camel-knowledge"), agentName + " MCP config missing camel-knowledge server");
        assertTrue(servers.has("citrus"), agentName + " MCP config missing citrus server");
    }

    private static InitContext createContext(String agentName, Path projectDir) {
        return createContext(agentName, projectDir, "camel-kit");
    }

    private static InitContext createContext(String agentName, Path projectDir, String commandPrefix) {
        AgentConfig agent = AgentRegistry.get(agentName);
        assertNotNull(agent, "Unexpected agent: " + agentName);
        Path skillsDir = projectDir.resolve(agent.skillsDirectory());
        // Mirrors InitService: non-stub agents resolve commandsDir to their skills directory.
        Path commandsDir = agent.generatesCommandStubs()
                ? projectDir.resolve(agent.commandDirectory())
                : skillsDir;
        return new InitContext(
                agent, agentName, commandsDir, skillsDir, projectDir,
                commandPrefix, Printer.noop());
    }

    private static List<String> sortedAgentNames() {
        return AgentRegistry.names().stream()
                .sorted()
                .toList();
    }

    private static Path resourcePath(String resource) throws Exception {
        String normalized = resource.startsWith("/") ? resource.substring(1) : resource;
        var resourceUrl = ShippedAssetStructureTest.class.getClassLoader().getResource(normalized);
        assertNotNull(resourceUrl, "Missing test resource on classpath: " + normalized);
        URI uri = resourceUrl.toURI();
        return Path.of(uri);
    }

    private static Path resourceRoot() throws Exception {
        return resourcePath("skills").getParent();
    }
}

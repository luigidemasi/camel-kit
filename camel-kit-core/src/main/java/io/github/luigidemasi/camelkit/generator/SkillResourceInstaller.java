package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.config.AgentGeneratorStrategy;
import io.github.luigidemasi.camelkit.util.AnsiColors;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;

class SkillResourceInstaller {

    private static final List<String> RETIRED_SHIP_GUIDES = List.of(
            "auto-fix-loop.md",
            "oversight-matrix.md",
            "state-management.md");

    private static final Set<String> MODEL_HIDDEN_INTERNAL_SKILLS = Set.of(
            "camel-design",
            "camel-implement",
            "camel-test",
            "camel-verify");

    private final DispatchBlockAppender dispatchBlockAppender;
    private final VersionPlaceholderResolver versionPlaceholderResolver;

    SkillResourceInstaller() {
        this(new DispatchBlockAppender(), new VersionPlaceholderResolver());
    }

    SkillResourceInstaller(
                           DispatchBlockAppender dispatchBlockAppender,
                           VersionPlaceholderResolver versionPlaceholderResolver) {
        this.dispatchBlockAppender = dispatchBlockAppender;
        this.versionPlaceholderResolver = versionPlaceholderResolver;
    }

    void install(InitContext ctx, WorkflowManifest workflow) throws Exception {
        Files.createDirectories(ctx.skillsDir());

        var skillsResource = getClass().getClassLoader().getResource("skills");
        if (skillsResource == null) {
            throw new IOException("Skills resource directory not found");
        }

        URI uri = skillsResource.toURI();
        FileSystem fileSystem = null;
        boolean closeFileSystem = false;
        try {
            Path skillsSourceDir;
            if ("jar".equals(uri.getScheme())) {
                JarFileSystem jarFileSystem = openJarFileSystem(uri);
                fileSystem = jarFileSystem.fileSystem();
                closeFileSystem = jarFileSystem.owned();
                skillsSourceDir = fileSystem.getPath("/skills");
            } else {
                skillsSourceDir = Path.of(uri);
            }

            int filesCopied = copySkills(ctx, skillsSourceDir, workflow);
            int skillCount;
            try (var stream = Files.list(ctx.skillsDir())) {
                skillCount = (int) stream.filter(Files::isDirectory).count();
            }

            if (filesCopied > 0) {
                ctx.printer().println(AnsiColors.green("✓") + " Copied " + filesCopied + " files in " + skillCount
                                      + " skill folders");
            } else {
                ctx.printer().println(AnsiColors
                        .yellow("  No skills copied (this is normal - skills are embedded in command files)"));
            }
        } finally {
            if (fileSystem != null && closeFileSystem) {
                try {
                    fileSystem.close();
                } catch (Exception e) {
                    // Ignore close errors.
                }
            }
        }
    }

    private JarFileSystem openJarFileSystem(URI uri) throws Exception {
        try {
            return new JarFileSystem(FileSystems.newFileSystem(uri, java.util.Collections.emptyMap()), true);
        } catch (FileSystemAlreadyExistsException e) {
            return new JarFileSystem(FileSystems.getFileSystem(uri), false);
        }
    }

    private int copySkills(InitContext ctx, Path skillsSourceDir, WorkflowManifest workflow) throws Exception {
        int filesCopied = 0;
        List<String> failures = new ArrayList<>();
        try (var stream = Files.walk(skillsSourceDir)) {
            for (Path source : stream.toList()) {
                String relativePathStr = skillsSourceDir.relativize(source).toString();
                if (relativePathStr.isEmpty()) {
                    continue;
                }

                Path destination = ctx.skillsDir().resolve(relativePathStr);
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        copySkillFile(ctx, source, destination, workflow);
                        filesCopied++;
                    }
                } catch (Exception e) {
                    failures.add(relativePathStr + ": " + e.getMessage());
                }
            }
        }
        if (!failures.isEmpty()) {
            throw new IOException("Failed to copy skill resources: " + String.join("; ", failures));
        }
        return filesCopied;
    }

    private void copySkillFile(
            InitContext ctx, Path source, Path destination, WorkflowManifest workflow)
            throws Exception {
        Files.createDirectories(destination.getParent());
        try (InputStream in = Files.newInputStream(source)) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        if (destination.getFileName().toString().equals("SKILL.md")) {
            boolean shipDelegate = "camel-ship".equals(destination.getParent().getFileName().toString());
            if (shipDelegate) {
                String content = Files.readString(destination);
                Files.writeString(destination, content.replace("{COMMAND_PREFIX}", ctx.commandPrefix()));
            }
            if (AgentGeneratorStrategy.BOB2.descriptorValue().equals(ctx.agentName())) {
                addBobReadableUserInvocableMetadata(destination);
            }
            if (AgentGeneratorStrategy.COPILOT.descriptorValue().equals(ctx.agentName())) {
                addCopilotReadableInternalSkillMetadata(destination);
            }
            if (AgentGeneratorStrategy.PI.descriptorValue().equals(ctx.agentName())) {
                addPiReadableInternalSkillMetadata(destination);
            }
            if (!shipDelegate) {
                dispatchBlockAppender.append(destination, ctx.agentName());
            }
        }
        if (destination.getFileName().toString().endsWith(".md")) {
            versionPlaceholderResolver.substitute(destination, ctx.distribution());
            if (AgentGeneratorStrategy.CODEX.descriptorValue().equals(ctx.agentName())) {
                useCodexSkillInvocations(destination, workflow);
            }
        }
    }

    void removeRetiredShipGuides(InitContext ctx) throws IOException {
        Path guides = ctx.skillsDir().resolve("camel-ship/guides");
        for (String guide : RETIRED_SHIP_GUIDES) {
            GeneratedAssetCleaner.deleteRegularFile(ctx.projectDir(), guides.resolve(guide));
        }
    }

    private void useCodexSkillInvocations(Path markdown, WorkflowManifest workflow) throws IOException {
        String content = Files.readString(markdown);
        String adapted = content;
        for (WorkflowManifest.WorkflowSkill skill : workflow.skills()) {
            Pattern invocation = Pattern.compile(
                    "(?<![A-Za-z0-9._/-])/" + Pattern.quote(skill.name()) + "(?![A-Za-z0-9-])");
            adapted = invocation.matcher(adapted)
                    .replaceAll(Matcher.quoteReplacement("$" + skill.name()));
        }
        if (!adapted.equals(content)) {
            Files.writeString(markdown, adapted);
        }
    }

    private void addBobReadableUserInvocableMetadata(Path skillFile) throws Exception {
        String content = Files.readString(skillFile);
        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n") || normalized.contains("\nuser-invocable:")) {
            return;
        }

        String[] lines = normalized.split("\n", -1);
        StringBuilder updated = new StringBuilder(normalized.length() + 24);
        boolean inserted = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            updated.append(line);
            if (!inserted && line.startsWith("user_invocable:")) {
                updated.append('\n');
                updated.append("user-invocable:").append(line.substring("user_invocable:".length()));
                if (i < lines.length - 1) {
                    updated.append('\n');
                }
                inserted = true;
            } else if (i < lines.length - 1) {
                updated.append('\n');
            }
        }
        if (inserted) {
            Files.writeString(skillFile, updated.toString());
        }
    }

    void addCopilotReadableInternalSkillMetadata(Path skillFile) throws Exception {
        addInternalSkillInvocationMetadata(skillFile, "Copilot");
    }

    void addPiReadableInternalSkillMetadata(Path skillFile) throws Exception {
        addInternalSkillInvocationMetadata(skillFile, "Pi");
    }

    private void addInternalSkillInvocationMetadata(Path skillFile, String agentName) throws Exception {
        String skillName = skillFile.getParent().getFileName().toString();
        if (!MODEL_HIDDEN_INTERNAL_SKILLS.contains(skillName)) {
            return;
        }

        String content = Files.readString(skillFile);
        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            throw new IOException(
                    "Internal " + agentName + " skill '" + skillName
                                  + "' must declare frontmatter so invocation metadata can be generated");
        }

        boolean hasUserInvocable = normalized.contains("\nuser-invocable:");
        boolean hasDisableModelInvocation = normalized.contains("\ndisable-model-invocation:");
        if (hasUserInvocable && hasDisableModelInvocation) {
            return;
        }
        boolean hasCamelKitUserInvocable = normalized.contains("\nuser_invocable:");
        if (!hasCamelKitUserInvocable && !hasUserInvocable) {
            throw new IOException(
                    "Internal " + agentName + " skill '" + skillName
                                  + "' must declare user_invocable or user-invocable so invocation metadata can be generated");
        }

        String[] lines = normalized.split("\n", -1);
        StringBuilder updated = new StringBuilder(normalized.length() + 64);
        boolean inserted = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            updated.append(line);
            if (!inserted && (line.startsWith("user_invocable:") || line.startsWith("user-invocable:"))) {
                if (!hasUserInvocable) {
                    updated.append('\n').append("user-invocable: false");
                }
                if (!hasDisableModelInvocation) {
                    updated.append('\n').append("disable-model-invocation: true");
                }
                inserted = true;
            }
            if (i < lines.length - 1) {
                updated.append('\n');
            }
        }
        if (inserted) {
            Files.writeString(skillFile, updated.toString());
        }
    }

    private record JarFileSystem(FileSystem fileSystem, boolean owned) {
    }
}

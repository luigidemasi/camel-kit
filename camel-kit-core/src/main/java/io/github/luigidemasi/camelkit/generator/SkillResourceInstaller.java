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

import io.github.luigidemasi.camelkit.util.AnsiColors;

class SkillResourceInstaller {

    private static final Set<String> COPILOT_INTERNAL_SKILLS = Set.of(
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

    void install(InitContext ctx) throws Exception {
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

            int filesCopied = copySkills(ctx, skillsSourceDir);
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

    private int copySkills(InitContext ctx, Path skillsSourceDir) throws Exception {
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
                        copySkillFile(ctx, source, destination);
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

    private void copySkillFile(InitContext ctx, Path source, Path destination) throws Exception {
        Files.createDirectories(destination.getParent());
        try (InputStream in = Files.newInputStream(source)) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        if (destination.getFileName().toString().equals("SKILL.md")) {
            if ("bob2".equals(ctx.agentName())) {
                addBobReadableUserInvocableMetadata(destination);
            }
            if ("copilot".equals(ctx.agentName())) {
                addCopilotReadableInternalSkillMetadata(destination);
            }
            dispatchBlockAppender.append(destination, ctx.agentName());
        }
        if (destination.getFileName().toString().endsWith(".md")) {
            versionPlaceholderResolver.substitute(destination, ctx.distribution());
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

    private void addCopilotReadableInternalSkillMetadata(Path skillFile) throws Exception {
        String skillName = skillFile.getParent().getFileName().toString();
        if (!COPILOT_INTERNAL_SKILLS.contains(skillName)) {
            return;
        }

        String content = Files.readString(skillFile);
        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")
                || (normalized.contains("\nuser-invocable:")
                        && normalized.contains("\ndisable-model-invocation:"))) {
            return;
        }

        String[] lines = normalized.split("\n", -1);
        StringBuilder updated = new StringBuilder(normalized.length() + 64);
        boolean inserted = false;
        boolean hasUserInvocable = normalized.contains("\nuser-invocable:");
        boolean hasDisableModelInvocation = normalized.contains("\ndisable-model-invocation:");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            updated.append(line);
            if (!inserted && line.startsWith("user_invocable:")) {
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

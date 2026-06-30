package io.github.luigidemasi.camelkit.generator;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import io.github.luigidemasi.camelkit.util.AnsiColors;

class SkillResourceInstaller {

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
            ctx.printer().println(AnsiColors.yellow("  Warning: Skills not found in resources"));
            return;
        }

        URI uri = skillsResource.toURI();
        FileSystem fileSystem = null;
        try {
            Path skillsSourceDir;
            if ("jar".equals(uri.getScheme())) {
                fileSystem = openJarFileSystem(uri);
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
            if (fileSystem != null && "jar".equals(uri.getScheme())) {
                try {
                    fileSystem.close();
                } catch (Exception e) {
                    // Ignore close errors.
                }
            }
        }
    }

    private FileSystem openJarFileSystem(URI uri) throws Exception {
        try {
            return FileSystems.newFileSystem(uri, Collections.emptyMap());
        } catch (Exception e) {
            return FileSystems.getFileSystem(uri);
        }
    }

    private int copySkills(InitContext ctx, Path skillsSourceDir) throws Exception {
        int filesCopied = 0;
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
                    // Preserve the existing best-effort copy behavior for unusual resource entries.
                }
            }
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
            dispatchBlockAppender.append(destination, ctx.agentName());
        }
        if (destination.getFileName().toString().endsWith(".md")) {
            try {
                versionPlaceholderResolver.substitute(destination, ctx.distribution());
            } catch (Exception e) {
                ctx.printer().println(AnsiColors.yellow("  Warning: Failed to substitute version placeholders in "
                                                        + destination + ": " + e.getMessage()));
            }
        }
    }

    private void addBobReadableUserInvocableMetadata(Path skillFile) throws Exception {
        String content = Files.readString(skillFile);
        if (!content.startsWith("---\n") || content.contains("\nuser-invocable:")) {
            return;
        }

        String[] lines = content.split("\n", -1);
        StringBuilder updated = new StringBuilder(content.length() + 24);
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
}

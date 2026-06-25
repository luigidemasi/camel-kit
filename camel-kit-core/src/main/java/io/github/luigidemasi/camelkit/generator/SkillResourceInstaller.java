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
            dispatchBlockAppender.append(destination, ctx.agentName());
        }
        if (destination.getFileName().toString().endsWith(".md")) {
            try {
                versionPlaceholderResolver.substitute(destination);
            } catch (Exception e) {
                ctx.printer().println(AnsiColors.yellow("  Warning: Failed to substitute version placeholders in "
                                                        + destination + ": " + e.getMessage()));
            }
        }
    }
}

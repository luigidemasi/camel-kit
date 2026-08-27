package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

final class GeneratedAssetCleaner {

    private GeneratedAssetCleaner() {
    }

    static void deleteRegularFile(InitContext ctx, Path generatedFile) throws IOException {
        if (deleteRegularFile(ctx.projectDir(), generatedFile)) {
            ctx.printer().println("  Removed retired generated asset "
                                  + ctx.projectDir().toAbsolutePath().normalize()
                                          .relativize(generatedFile.toAbsolutePath().normalize()));
        }
    }

    static boolean deleteRegularFile(Path projectDirectory, Path generatedFile) throws IOException {
        Path project = projectDirectory.toAbsolutePath().normalize();
        Path target = generatedFile.toAbsolutePath().normalize();
        if (!target.startsWith(project) || target.equals(project)) {
            throw new IOException("Generated asset is outside the project: " + target);
        }

        Path current = project;
        for (Path component : project.relativize(target.getParent())) {
            current = current.resolve(component);
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(
                        current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (NoSuchFileException e) {
                return false;
            }
            if (attributes.isSymbolicLink()) {
                throw new IOException("Refusing to remove a generated asset through symbolic link: " + current);
            }
            if (!attributes.isDirectory()) {
                return false;
            }
        }

        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(
                    target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException e) {
            return false;
        }
        if (attributes.isRegularFile()) {
            Files.delete(target);
            deleteEmptyParent(project, target.getParent());
            return true;
        }
        return false;
    }

    private static void deleteEmptyParent(Path project, Path directory) throws IOException {
        if (directory.equals(project) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var children = Files.list(directory)) {
            if (children.findAny().isPresent()) {
                return;
            }
        }
        Files.delete(directory);
    }
}

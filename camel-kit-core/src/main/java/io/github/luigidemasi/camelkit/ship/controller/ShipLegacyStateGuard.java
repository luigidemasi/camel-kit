package io.github.luigidemasi.camelkit.ship.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/** Rejects unsupported pre-release Ship state without changing user material. */
final class ShipLegacyStateGuard {

    static final int MAX_PIPELINE_BYTES = 1024 * 1024;

    private static final Path CAMEL_KIT = Path.of(".camel-kit");
    private static final Path PIPELINE = Path.of("pipeline.json");
    private static final Path SHIP_STATE = Path.of("ship-state.json");

    private ShipLegacyStateGuard() {
    }

    static void requireStartable(Path projectRoot) throws IOException {
        Path root = Objects.requireNonNull(projectRoot, "projectRoot");
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        BasicFileAttributes rootBefore = Files.readAttributes(
                root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!safeDirectory(rootBefore)) {
            throw rejection(CAMEL_KIT, null);
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            if (!(entries instanceof SecureDirectoryStream<Path> secureRoot)) {
                throw rejection(CAMEL_KIT, null);
            }
            BasicFileAttributes camelKitBefore = optionalAttributes(secureRoot, CAMEL_KIT);
            if (camelKitBefore == null) {
                return;
            }
            if (!camelKitBefore.isDirectory()) {
                if (camelKitBefore.isSymbolicLink()) {
                    throw rejection(CAMEL_KIT, null);
                }
                return;
            }
            if (camelKitBefore.fileKey() == null) {
                throw rejection(CAMEL_KIT, null);
            }
            try (SecureDirectoryStream<Path> camelKit
                    = secureRoot.newDirectoryStream(CAMEL_KIT, LinkOption.NOFOLLOW_LINKS)) {
                if (optionalAttributes(camelKit, SHIP_STATE) != null) {
                    throw rejection(CAMEL_KIT.resolve(SHIP_STATE), null);
                }
                BasicFileAttributes pipelineBefore = optionalAttributes(camelKit, PIPELINE);
                if (pipelineBefore == null) {
                    return;
                }
                Path pipelinePath = root.resolve(CAMEL_KIT).resolve(PIPELINE);
                requireSafePipeline(pipelinePath, pipelineBefore);
                byte[] encoded = readBounded(camelKit);
                BasicFileAttributes pipelineAfter = attributes(camelKit, PIPELINE);
                requireUnchangedPipeline(pipelinePath, pipelineBefore, pipelineAfter, encoded.length);
                requireManualPipeline(encoded);
            } catch (ShipControllerException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                throw rejection(CAMEL_KIT.resolve(PIPELINE), e);
            }
            BasicFileAttributes camelKitAfter = attributes(secureRoot, CAMEL_KIT);
            if (!sameDirectory(camelKitBefore, camelKitAfter)) {
                throw rejection(CAMEL_KIT, null);
            }
        } catch (ShipControllerException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw rejection(CAMEL_KIT, e);
        }
        BasicFileAttributes rootAfter = Files.readAttributes(
                root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!sameDirectory(rootBefore, rootAfter)) {
            throw rejection(CAMEL_KIT, null);
        }
    }

    private static byte[] readBounded(SecureDirectoryStream<Path> camelKit) throws IOException {
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = camelKit.newByteChannel(PIPELINE, options);
             ByteArrayOutputStream content = new ByteArrayOutputStream()) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            int total = 0;
            int read;
            while ((read = channel.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                total += read;
                if (total > MAX_PIPELINE_BYTES) {
                    throw rejection(CAMEL_KIT.resolve(PIPELINE), null);
                }
                content.write(buffer.array(), 0, read);
                buffer.clear();
            }
            return content.toByteArray();
        }
    }

    private static void requireManualPipeline(byte[] encoded) throws IOException {
        JsonNode document = ShipJson.mapper().readTree(encoded);
        JsonNode mode = document != null && document.isObject() ? document.get("mode") : null;
        if (mode == null || !mode.isTextual() || !"manual".equals(mode.textValue())) {
            throw rejection(CAMEL_KIT.resolve(PIPELINE), null);
        }
    }

    private static void requireSafePipeline(
            Path pipeline, BasicFileAttributes attributes)
            throws IOException {
        if (!attributes.isRegularFile()
                || attributes.isSymbolicLink()
                || attributes.fileKey() == null
                || attributes.size() < 0
                || attributes.size() > MAX_PIPELINE_BYTES
                || linkCount(pipeline) != 1) {
            throw rejection(CAMEL_KIT.resolve(PIPELINE), null);
        }
    }

    private static void requireUnchangedPipeline(
            Path pipeline,
            BasicFileAttributes before,
            BasicFileAttributes after,
            int bytesRead)
            throws IOException {
        if (!after.isRegularFile()
                || after.isSymbolicLink()
                || after.fileKey() == null
                || !before.fileKey().equals(after.fileKey())
                || before.size() != after.size()
                || after.size() != bytesRead
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || linkCount(pipeline) != 1) {
            throw rejection(CAMEL_KIT.resolve(PIPELINE), null);
        }
    }

    private static long linkCount(Path path) throws IOException {
        Object value = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
        if (!(value instanceof Number number)) {
            throw rejection(CAMEL_KIT.resolve(PIPELINE), null);
        }
        return number.longValue();
    }

    private static BasicFileAttributes optionalAttributes(
            SecureDirectoryStream<Path> directory, Path name)
            throws IOException {
        try {
            return attributes(directory, name);
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    private static BasicFileAttributes attributes(
            SecureDirectoryStream<Path> directory, Path name)
            throws IOException {
        BasicFileAttributeView view = directory.getFileAttributeView(
                name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw rejection(CAMEL_KIT.resolve(name), null);
        }
        return view.readAttributes();
    }

    private static boolean safeDirectory(BasicFileAttributes attributes) {
        return attributes.isDirectory()
                && !attributes.isSymbolicLink()
                && attributes.fileKey() != null;
    }

    private static boolean sameDirectory(
            BasicFileAttributes before, BasicFileAttributes after) {
        return safeDirectory(after) && before.fileKey().equals(after.fileKey());
    }

    private static ShipControllerException rejection(Path relative, Throwable cause) {
        String message = "Pre-release Ship state at " + relative
                         + " is unsupported. Archive or move it outside the project and start a fresh controller run.";
        return cause == null
                ? new ShipControllerException("pre-release-ship-state", message)
                : new ShipControllerException("pre-release-ship-state", message, cause);
    }
}

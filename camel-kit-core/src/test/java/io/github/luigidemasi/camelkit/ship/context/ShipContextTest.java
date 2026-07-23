package io.github.luigidemasi.camelkit.ship.context;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.context.ShipContext.DocumentInput;
import io.github.luigidemasi.camelkit.ship.context.ShipContext.Failure;
import io.github.luigidemasi.camelkit.ship.context.ShipContext.Input;
import io.github.luigidemasi.camelkit.ship.context.ShipContext.Kind;
import io.github.luigidemasi.camelkit.ship.context.ShipContext.TextInput;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipContextTest {

    @TempDir
    Path project;

    @Test
    void preservesAbsentEmptyRepeatedAndOrderedInput() throws Exception {
        Path firstDocument = Files.writeString(project.resolve("first.md"), "first");
        Path secondDocument = Files.writeString(project.resolve("second.md"), "second");

        ShipContext none = ShipContext.none();
        ShipContext emptyText = ShipContext.resolve(
                project, List.of(new TextInput("")));
        ShipContext repeated = ShipContext.resolve(
                project,
                List.of(
                        new DocumentInput(firstDocument.getFileName()),
                        new DocumentInput(firstDocument.getFileName())));
        ShipContext ordered = ShipContext.resolve(
                project,
                List.of(
                        new DocumentInput(firstDocument.getFileName()),
                        new TextInput("qualification"),
                        new DocumentInput(secondDocument.getFileName())));
        ShipContext reordered = ShipContext.resolve(
                project,
                List.of(
                        new TextInput("qualification"),
                        new DocumentInput(firstDocument.getFileName()),
                        new DocumentInput(secondDocument.getFileName())));

        assertTrue(none.sources().isEmpty());
        assertNotEquals(none.digest(), emptyText.digest());
        assertEquals("", emptyText.sources().get(0).value());
        assertEquals(0, emptyText.sources().get(0).byteCount());
        assertEquals(repeated.sources().get(0), repeated.sources().get(1));
        assertNotEquals(
                ShipContext.resolve(
                        project, List.of(new DocumentInput(firstDocument.getFileName())))
                        .digest(),
                repeated.digest());
        assertEquals(
                List.of(Kind.DOCUMENT, Kind.TEXT, Kind.DOCUMENT),
                ordered.sources().stream().map(ShipContext.Source::kind).toList());
        assertEquals(
                firstDocument.toAbsolutePath().normalize().toString(),
                ordered.sources().get(0).value());
        assertEquals("qualification", ordered.sources().get(1).value());
        assertEquals(
                secondDocument.toAbsolutePath().normalize().toString(),
                ordered.sources().get(2).value());
        assertNotEquals(ordered.digest(), reordered.digest());
        assertTrue(ordered.sources().stream()
                .allMatch(source -> ShipDigest.isSha256(source.digest())));
        assertThrows(
                UnsupportedOperationException.class,
                () -> ordered.sources().add(ordered.sources().get(0)));
        assertFalse(ordered.toString().contains("qualification"));
    }

    @Test
    void refreshesDocumentsWithoutChangingTextOrOrder() throws Exception {
        Path document = Files.writeString(project.resolve("requirements.md"), "before");
        ShipContext context = ShipContext.resolve(
                project,
                List.of(
                        new TextInput("qualification"),
                        new DocumentInput(document),
                        new DocumentInput(document)));
        ShipContext.Source text = context.sources().get(0);
        String oldDocumentDigest = context.sources().get(1).digest();

        Files.writeString(document, "after");
        ShipContext refreshed = context.refresh();

        assertEquals(text, refreshed.sources().get(0));
        assertEquals(
                List.of(Kind.TEXT, Kind.DOCUMENT, Kind.DOCUMENT),
                refreshed.sources().stream().map(ShipContext.Source::kind).toList());
        assertNotEquals(oldDocumentDigest, refreshed.sources().get(1).digest());
        assertEquals(refreshed.sources().get(1), refreshed.sources().get(2));
        assertNotEquals(context.digest(), refreshed.digest());

        Files.delete(document);
        assertFailure(Failure.Code.MISSING, context::refresh);
    }

    @Test
    void distinguishesDocumentAndContextFailures() throws Exception {
        Path empty = Files.createFile(project.resolve("empty.md"));
        Path malformed = Files.write(
                project.resolve("malformed.md"), new byte[]{(byte) 0xc3, 0x28});
        Path nul = Files.write(
                project.resolve("nul.md"), new byte[]{'a', 0, 'b'});
        Path directory = Files.createDirectory(project.resolve("directory"));
        Path oversized = project.resolve("oversized.md");
        byte[] oversizedBytes = new byte[ShipContext.MAX_DOCUMENT_BYTES + 1];
        Arrays.fill(oversizedBytes, (byte) 'x');
        Files.write(oversized, oversizedBytes);

        assertFailure(
                Failure.Code.MISSING,
                () -> resolveDocument(project.resolve("missing.md")));
        assertFailure(Failure.Code.EMPTY, () -> resolveDocument(empty));
        assertFailure(Failure.Code.MALFORMED, () -> resolveDocument(malformed));
        assertFailure(Failure.Code.MALFORMED, () -> resolveDocument(nul));
        assertFailure(Failure.Code.MALFORMED, () -> resolveDocument(directory));
        assertFailure(Failure.Code.DOCUMENT_TOO_LARGE, () -> resolveDocument(oversized));
        assertFailure(
                Failure.Code.MALFORMED,
                () -> ShipContext.resolve(project, List.of(new TextInput("a\0b"))));
        assertFailure(
                Failure.Code.MALFORMED,
                () -> ShipContext.resolve(project, List.of(new TextInput("\ud800"))));
        assertFailure(
                Failure.Code.CONTEXT_TOO_LARGE,
                () -> ShipContext.resolve(
                        project,
                        List.of(new TextInput("x".repeat(ShipContext.MAX_TOTAL_BYTES + 1)))));
    }

    @Test
    void enforcesExactSourceDocumentAndAggregateBounds() throws Exception {
        TextInput empty = new TextInput("");
        List<Input> exactSourceCount = Collections.nCopies(ShipContext.MAX_SOURCES, empty);
        List<Input> tooManySources = Collections.nCopies(ShipContext.MAX_SOURCES + 1, empty);
        Path exactDocument = project.resolve("exact-document.md");
        byte[] exactDocumentBytes = new byte[ShipContext.MAX_DOCUMENT_BYTES];
        Arrays.fill(exactDocumentBytes, (byte) 'x');
        Files.write(exactDocument, exactDocumentBytes);

        assertEquals(
                ShipContext.MAX_SOURCES,
                ShipContext.resolve(project, exactSourceCount).sources().size());
        assertFailure(
                Failure.Code.CONTEXT_TOO_LARGE,
                () -> ShipContext.resolve(project, tooManySources));
        assertEquals(
                ShipContext.MAX_DOCUMENT_BYTES,
                resolveDocument(exactDocument).sources().get(0).byteCount());
        assertEquals(
                ShipContext.MAX_TOTAL_BYTES,
                ShipContext.resolve(
                        project,
                        List.of(new TextInput("x".repeat(ShipContext.MAX_TOTAL_BYTES))))
                        .sources()
                        .get(0)
                        .byteCount());
        assertFailure(
                Failure.Code.CONTEXT_TOO_LARGE,
                () -> ShipContext.resolve(
                        project,
                        List.of(
                                new DocumentInput(exactDocument),
                                new DocumentInput(exactDocument),
                                new TextInput("x"))));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void reportsUnreadableDocuments() throws Exception {
        Path unreadable = Files.writeString(project.resolve("unreadable.md"), "content");
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(unreadable);
        try {
            Files.setPosixFilePermissions(unreadable, Set.of());
            assertFalse(Files.isReadable(unreadable));
            assertFailure(Failure.Code.UNREADABLE, () -> resolveDocument(unreadable));
        } finally {
            Files.setPosixFilePermissions(unreadable, original);
        }
    }

    @Test
    void rejectsForgedPersistedIdentities() throws Exception {
        ShipContext context = ShipContext.resolve(
                project, List.of(new TextInput("qualification")));
        ShipContext.Source source = context.sources().get(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ShipContext(context.sources(), "sha256:" + "0".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShipContext.Source(
                        Kind.TEXT,
                        source.value() + " changed",
                        source.byteCount(),
                        source.digest()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShipContext.Source(
                        Kind.DOCUMENT,
                        "relative.md",
                        1,
                        ShipDigest.sha256("x".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsPersistedContextsOutsideCompactBounds() throws Exception {
        ShipContext.Source emptyText = ShipContext.resolve(
                project, List.of(new TextInput(""))).sources().get(0);
        ShipContext.Source maximumDocument = new ShipContext.Source(
                Kind.DOCUMENT,
                project.resolve("claimed-document.md").toAbsolutePath().normalize().toString(),
                ShipContext.MAX_DOCUMENT_BYTES,
                ShipDigest.sha256("claimed".getBytes(StandardCharsets.UTF_8)));

        IllegalArgumentException tooMany = assertThrows(
                IllegalArgumentException.class,
                () -> new ShipContext(
                        Collections.nCopies(ShipContext.MAX_SOURCES + 1, emptyText),
                        ShipContext.none().digest()));
        IllegalArgumentException tooLarge = assertThrows(
                IllegalArgumentException.class,
                () -> new ShipContext(
                        List.of(maximumDocument, maximumDocument, maximumDocument),
                        ShipContext.none().digest()));

        assertEquals("Ship context exceeds the source-count limit", tooMany.getMessage());
        assertEquals("Ship context exceeds the aggregate-byte limit", tooLarge.getMessage());
    }

    private ShipContext resolveDocument(Path path) throws Failure {
        return ShipContext.resolve(project, List.of(new DocumentInput(path)));
    }

    private static Failure assertFailure(
            Failure.Code expected, ThrowingOperation operation) {
        Failure failure = assertThrows(Failure.class, operation::run);
        assertEquals(expected, failure.code());
        assertFalse(failure.input().isBlank());
        return failure;
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}

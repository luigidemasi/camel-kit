package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class FileShipEventStoreTest {

    private static final Instant NOW = Instant.parse("2026-07-21T12:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void storageNeverValidatesOrAdvancesLifecycleTransitions() throws Exception {
        ShipRunId runId = ShipRunId.create();
        Path stateRoot = temporaryDirectory.resolve("neutral-state");
        FileShipEventStore store = FileShipEventStore.create(stateRoot, runId);
        AuthorityHeadId firstHead = AuthorityHeadId.create();
        ShipEvent first = store.appendIfLatest(
                ShipEventHead.genesis(),
                draft(
                        ShipEventType.RUN_COMPLETED,
                        ShipState.DESIGN_RUNNING,
                        null,
                        firstHead,
                        0));
        AuthorityHeadId secondHead = AuthorityHeadId.create();
        ShipEvent second = store.appendIfLatest(
                ShipEventHead.from(first),
                draft(
                        ShipEventType.RUN_CREATED,
                        ShipState.ABORTED,
                        firstHead,
                        secondHead,
                        1));

        assertEquals(List.of(first, second), store.replay());
        assertEquals(ShipState.DESIGN_RUNNING, second.previousState());
        assertEquals(firstHead, second.previousAuthorityHead());
        assertEquals(secondHead, store.currentHead().authorityHead());

        String stored = Files.readString(eventFiles(stateRoot.resolve(runId.storageId())).get(0));
        assertTrue(stored.contains("\"type\":\"run-completed\""));
        assertTrue(stored.contains("\"state\":\"design-running\""));
        assertTrue(stored.contains("\"authorityHead\":\"head-"));
        assertEquals(runId, ShipRunId.fromStorageId(runId.storageId()));
        assertEquals(firstHead, AuthorityHeadId.fromStorageId(firstHead.storageId()));
    }

    @Test
    void failedCreateRemovesItsOwnedPartialRunRoot() throws Exception {
        ShipRunId runId = ShipRunId.create();
        // The run root fits under Linux PATH_MAX, while its "/events" child does not.
        int runRootBytes = 4_096 - 6;
        int stateRootBytes = runRootBytes - 1 - runId.storageId().length();
        Path stateRoot = directoryWithAbsoluteUtf8Length(temporaryDirectory, stateRootBytes);
        Files.setPosixFilePermissions(
                stateRoot, PosixFilePermissions.fromString("rwx------"));

        assertThrows(IOException.class, () -> FileShipEventStore.create(stateRoot, runId));

        assertFalse(Files.exists(
                stateRoot.resolve(runId.storageId()), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void staleExpectedHeadCannotAppend() throws Exception {
        ShipRunId runId = ShipRunId.create();
        FileShipEventStore store = FileShipEventStore.create(
                temporaryDirectory.resolve("stale-state"), runId);
        ShipEvent first = appendFirst(store);

        ShipEventHeadMismatchException failure = assertThrows(
                ShipEventHeadMismatchException.class,
                () -> store.appendIfLatest(
                        ShipEventHead.genesis(),
                        draft(
                                ShipEventType.RUN_ABORTED,
                                ShipState.ABORTED,
                                null,
                                AuthorityHeadId.create(),
                                2)));

        assertEquals(ShipEventHead.genesis(), failure.expected());
        assertEquals(ShipEventHead.from(first), failure.actual());
        assertEquals(List.of(first), store.replay());
    }

    @Test
    void rejectsNonCanonicalDecimalWithoutCommittingIt() throws Exception {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("amount", new BigDecimal("1.10"));

        assertPayloadRejectedWithoutCommit("decimal-state", payload);
    }

    @Test
    void rejectsPayloadBeyondParserTokenBudgetWithoutCommittingIt() throws Exception {
        ArrayNode payload = MAPPER.createArrayNode();
        for (int index = 0; index < 60_000; index++) {
            payload.addObject();
        }

        assertPayloadRejectedWithoutCommit("token-state", payload);
    }

    @Test
    void replayDetectsContentReplacementAndInvalidUtf8() throws Exception {
        ShipRunId replacementRun = ShipRunId.create();
        Path replacementRoot = temporaryDirectory.resolve("replacement-state");
        FileShipEventStore replacement = storeWithTwoEvents(
                replacementRoot, replacementRun);
        List<Path> replacementFiles = eventFiles(replacementRoot.resolve(replacementRun.storageId()));
        overwriteEvent(replacementFiles.get(1), Files.readAllBytes(replacementFiles.get(0)));
        assertThrows(ShipEventStoreException.class, replacement::replay);

        ShipRunId utf8Run = ShipRunId.create();
        Path utf8Root = temporaryDirectory.resolve("utf8-state");
        FileShipEventStore utf8 = storeWithTwoEvents(
                utf8Root, utf8Run);
        Path utf8Event = eventFiles(utf8Root.resolve(utf8Run.storageId())).get(1);
        overwriteEvent(utf8Event, new byte[]{(byte) 0xc3, 0x28});
        ShipEventStoreException malformed = assertThrows(ShipEventStoreException.class, utf8::replay);
        assertTrue(malformed.getMessage().contains("UTF-8"));
    }

    @Test
    void replayDetectsSequenceGapsTruncationAndReordering() throws Exception {
        ShipRunId gapRun = ShipRunId.create();
        Path gapRoot = temporaryDirectory.resolve("gap-state");
        FileShipEventStore gap = storeWithThreeEvents(
                gapRoot, gapRun);
        Files.delete(eventFiles(gapRoot.resolve(gapRun.storageId())).get(1));
        assertThrows(ShipEventStoreException.class, gap::replay);

        ShipRunId truncatedRun = ShipRunId.create();
        Path truncatedRoot = temporaryDirectory.resolve("truncated-state");
        FileShipEventStore truncated = storeWithThreeEvents(
                truncatedRoot, truncatedRun);
        List<Path> truncatedFiles = eventFiles(truncatedRoot.resolve(truncatedRun.storageId()));
        Files.delete(truncatedFiles.get(truncatedFiles.size() - 1));
        ShipEventStoreException truncation = assertThrows(
                ShipEventStoreException.class, truncated::replay);
        assertTrue(truncation.getMessage().contains("current head"));

        ShipRunId reorderedRun = ShipRunId.create();
        Path reorderedRoot = temporaryDirectory.resolve("reordered-state");
        FileShipEventStore reordered = storeWithTwoEvents(
                reorderedRoot, reorderedRun);
        List<Path> reorderedFiles = eventFiles(reorderedRoot.resolve(reorderedRun.storageId()));
        Path parked = reorderedFiles.get(0).resolveSibling("parked");
        Files.move(reorderedFiles.get(0), parked);
        Files.move(reorderedFiles.get(1), reorderedFiles.get(0));
        Files.move(parked, reorderedFiles.get(1));
        assertThrows(ShipEventStoreException.class, reordered::replay);
    }

    @Test
    void authenticatedCurrentHeadRejectsStaleAndCrossDomainMacs() throws Exception {
        ShipRunId staleRun = ShipRunId.create();
        Path staleRoot = temporaryDirectory.resolve("stale-head-state");
        FileShipEventStore staleStore = FileShipEventStore.create(staleRoot, staleRun);
        ShipEvent first = appendFirst(staleStore);
        Path staleRunRoot = staleRoot.resolve(staleRun.storageId());
        Path staleHead = staleRunRoot.resolve("current-head.json");
        byte[] firstHead = Files.readAllBytes(staleHead);
        ShipEvent second = appendNext(staleStore, first, 2);
        appendNext(staleStore, second, 3);
        Files.write(staleHead, firstHead);
        ShipEventStoreException stale = assertThrows(
                ShipEventStoreException.class, staleStore::replay);
        assertTrue(stale.getMessage().contains("stale head"));

        ShipRunId domainRun = ShipRunId.create();
        Path domainRoot = temporaryDirectory.resolve("domain-state");
        FileShipEventStore domainStore = FileShipEventStore.create(domainRoot, domainRun);
        ShipEvent event = appendFirst(domainStore);
        Path domainRunRoot = domainRoot.resolve(domainRun.storageId());
        Path domainHead = domainRunRoot.resolve("current-head.json");
        byte[] wrongDomain = ShipEventCodec.encodeHead(
                domainRun, ShipEventHead.from(event), event.hmac());
        Files.write(domainHead, wrongDomain);
        ShipEventStoreException domain = assertThrows(
                ShipEventStoreException.class, domainStore::replay);
        assertTrue(domain.getMessage().contains("current-head HMAC"));
        assertEquals(32, Files.size(domainRunRoot.resolve("secret.key")));
    }

    @Test
    void secretReaderRequiresTheExactKeyLength() throws Exception {
        for (int bytes : List.of(31, 33)) {
            ShipRunId runId = ShipRunId.create();
            Path stateRoot = temporaryDirectory.resolve("key-length-" + bytes);
            FileShipEventStore store = FileShipEventStore.create(stateRoot, runId);
            Files.write(stateRoot.resolve(runId.storageId()).resolve("secret.key"), new byte[bytes]);

            ShipEventStoreException failure = assertThrows(
                    ShipEventStoreException.class, store::replay);

            assertTrue(failure.getMessage().contains("exactly 32 bytes"));
        }
    }

    @Test
    void recoversExactlyOneAuthenticatedEventForcedBeforeItsHeadUpdate() throws Exception {
        ShipRunId runId = ShipRunId.create();
        Path stateRoot = temporaryDirectory.resolve("one-tail-recovery-state");
        FileShipEventStore store = FileShipEventStore.create(stateRoot, runId);
        ShipEvent first = appendFirst(store);
        Path headPath = stateRoot.resolve(runId.storageId()).resolve("current-head.json");
        byte[] precedingHead = Files.readAllBytes(headPath);
        ShipEvent recoveryCandidate = appendNext(store, first, 2);
        Files.write(headPath, precedingHead);

        FileShipEventStore recovered = FileShipEventStore.open(stateRoot, runId);

        assertEquals(List.of(first, recoveryCandidate), recovered.replay());
        assertEquals(ShipEventHead.from(recoveryCandidate), recovered.currentHead());
    }

    @Test
    void rejectsUnauthenticatedEventForcedBeforeItsHeadUpdate() throws Exception {
        ShipRunId runId = ShipRunId.create();
        Path stateRoot = temporaryDirectory.resolve("unauthenticated-tail-state");
        FileShipEventStore store = FileShipEventStore.create(stateRoot, runId);
        ShipEvent first = appendFirst(store);
        Path runRoot = stateRoot.resolve(runId.storageId());
        Path headPath = runRoot.resolve("current-head.json");
        byte[] precedingHead = Files.readAllBytes(headPath);
        appendNext(store, first, 2);
        Path tailPath = eventFiles(runRoot).get(1);
        ObjectNode tail = (ObjectNode) MAPPER.readTree(Files.readAllBytes(tailPath));
        tail.put("hmac", "hmac-sha256:" + "0".repeat(64));
        overwriteEvent(tailPath, MAPPER.writeValueAsBytes(tail));
        Files.write(headPath, precedingHead);

        ShipEventStoreException failure = assertThrows(
                ShipEventStoreException.class,
                () -> FileShipEventStore.open(stateRoot, runId));

        assertTrue(failure.getMessage().contains("HMAC"));
        assertArrayEquals(precedingHead, Files.readAllBytes(headPath));
    }

    @Test
    void replayRejectsHardLinkedAndSymbolicEventEntries() throws Exception {
        ShipRunId hardLinkRun = ShipRunId.create();
        Path hardLinkRoot = temporaryDirectory.resolve("hard-link-state");
        FileShipEventStore hardLinkStore = FileShipEventStore.create(
                hardLinkRoot, hardLinkRun);
        appendFirst(hardLinkStore);
        Path hardLinkedEvent = eventFiles(hardLinkRoot.resolve(hardLinkRun.storageId())).get(0);
        try {
            Files.createLink(temporaryDirectory.resolve("event-alias"), hardLinkedEvent);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assumptions.abort("Hard links are not supported: " + e.getMessage());
        }
        ShipEventStoreException hardLink = assertThrows(
                ShipEventStoreException.class, hardLinkStore::replay);
        assertTrue(hardLink.getMessage().contains("exactly one hard link"));

        ShipRunId symlinkRun = ShipRunId.create();
        Path symlinkRoot = temporaryDirectory.resolve("symlink-state");
        FileShipEventStore symlinkStore = FileShipEventStore.create(
                symlinkRoot, symlinkRun);
        appendFirst(symlinkStore);
        Path event = eventFiles(symlinkRoot.resolve(symlinkRun.storageId())).get(0);
        Path backing = temporaryDirectory.resolve("event-backing.json");
        Files.move(event, backing);
        try {
            Files.createSymbolicLink(event, backing);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assumptions.abort("Symbolic links are not supported: " + e.getMessage());
        }
        assertThrows(ShipEventStoreException.class, symlinkStore::replay);
    }

    @Test
    void payloadTamperingIsRejectedEvenWhenJsonRemainsCanonical() throws Exception {
        ShipRunId runId = ShipRunId.create();
        Path stateRoot = temporaryDirectory.resolve("payload-state");
        FileShipEventStore store = storeWithTwoEvents(
                stateRoot, runId);
        Path second = eventFiles(stateRoot.resolve(runId.storageId())).get(1);
        ObjectNode node = (ObjectNode) MAPPER.readTree(Files.readAllBytes(second));
        node.set("payload", MAPPER.createObjectNode().put("step", 99));
        byte[] changed = MAPPER.writeValueAsBytes(node);
        assertNotEquals(new String(Files.readAllBytes(second), StandardCharsets.UTF_8),
                new String(changed, StandardCharsets.UTF_8));
        overwriteEvent(second, changed);

        assertThrows(ShipEventStoreException.class, store::replay);
    }

    private FileShipEventStore storeWithTwoEvents(Path stateRoot, ShipRunId runId) throws Exception {
        FileShipEventStore store = FileShipEventStore.create(stateRoot, runId);
        ShipEvent first = appendFirst(store);
        appendNext(store, first, 2);
        return store;
    }

    private FileShipEventStore storeWithThreeEvents(Path stateRoot, ShipRunId runId) throws Exception {
        FileShipEventStore store = storeWithTwoEvents(stateRoot, runId);
        ShipEvent second = store.replay().get(1);
        appendNext(store, second, 3);
        return store;
    }

    private ShipEvent appendFirst(FileShipEventStore store) throws Exception {
        return store.appendIfLatest(
                ShipEventHead.genesis(),
                draft(
                        ShipEventType.RUN_CREATED,
                        ShipState.CREATED,
                        null,
                        AuthorityHeadId.create(),
                        1));
    }

    private void assertPayloadRejectedWithoutCommit(String directory, JsonNode payload) throws Exception {
        ShipRunId runId = ShipRunId.create();
        Path stateRoot = temporaryDirectory.resolve(directory);
        FileShipEventStore store = FileShipEventStore.create(stateRoot, runId);
        Path runRoot = stateRoot.resolve(runId.storageId());
        Path headPath = runRoot.resolve("current-head.json");
        byte[] genesisHead = Files.readAllBytes(headPath);

        assertThrows(
                ShipEventStoreException.class,
                () -> store.appendIfLatest(
                        ShipEventHead.genesis(),
                        new ShipEventDraft(
                                ShipEventType.RUN_CREATED,
                                ShipState.CREATED,
                                null,
                                AuthorityHeadId.create(),
                                NOW,
                                payload)));

        assertEquals(List.of(), store.replay());
        assertEquals(ShipEventHead.genesis(), store.currentHead());
        assertTrue(eventFiles(runRoot).isEmpty());
        assertArrayEquals(genesisHead, Files.readAllBytes(headPath));
    }

    private ShipEvent appendNext(FileShipEventStore store, ShipEvent previous, int step)
            throws Exception {
        return store.appendIfLatest(
                ShipEventHead.from(previous),
                draft(
                        ShipEventType.RUN_ABORTED,
                        ShipState.ABORTED,
                        previous.authorityHead(),
                        AuthorityHeadId.create(),
                        step));
    }

    private ShipEventDraft draft(
            ShipEventType type,
            ShipState state,
            AuthorityHeadId previous,
            AuthorityHeadId successor,
            int step) {
        return new ShipEventDraft(
                type,
                state,
                previous,
                successor,
                NOW.plusSeconds(step),
                MAPPER.createObjectNode().put("step", step));
    }

    private static List<Path> eventFiles(Path runRoot) throws IOException {
        try (var files = Files.list(runRoot.resolve("events"))) {
            return files.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
    }

    private static void overwriteEvent(Path event, byte[] bytes) throws IOException {
        if (supportsPosix(event)) {
            Files.setPosixFilePermissions(event, PosixFilePermissions.fromString("rw-------"));
        }
        Files.write(event, bytes);
        if (supportsPosix(event)) {
            Files.setPosixFilePermissions(event, PosixFilePermissions.fromString("r--------"));
        }
    }

    private static Path directoryWithAbsoluteUtf8Length(Path base, int targetBytes)
            throws IOException {
        Path result = base.toAbsolutePath().normalize();
        int currentBytes = result.toString().getBytes(StandardCharsets.UTF_8).length;
        Assumptions.assumeTrue(
                currentBytes + 2 < targetBytes,
                "Temporary directory is too long for the partial-create regression");
        while (currentBytes < targetBytes) {
            int needed = targetBytes - currentBytes;
            int componentBytes = Math.min(200, needed - 1);
            if (needed - componentBytes - 1 == 1) {
                componentBytes--;
            }
            result = result.resolve("x".repeat(componentBytes));
            currentBytes += componentBytes + 1;
        }
        Files.createDirectories(result);
        return result;
    }

    private static boolean supportsPosix(Path path) {
        return Files.getFileAttributeView(
                path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
               != null;
    }
}

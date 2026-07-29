package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.ShipDigest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Outcome.EMPTY;
import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Outcome.MISSING;
import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Outcome.NONZERO;
import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Outcome.PASS;
import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Outcome.SKIPPED;
import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Outcome.TIMED_OUT;
import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Outcome.WAIVED;
import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Status.COMPLETED_WITH_WAIVER;
import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Status.FAIL;
import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Support.EXPERIMENTAL;
import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Support.INCOMPATIBLE;
import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Support.SUPPORTED;
import static io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Support.UNTESTED;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipLocalStampTest {

    private static final String RUN_ID = "ship-0123456789abcdef0123456789abcdef";
    private static final Instant STARTED = Instant.parse("2026-07-24T08:00:00Z");
    private static final Instant ENDED = Instant.parse("2026-07-24T08:00:01Z");
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY
            = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE
            = PosixFilePermissions.fromString("rw-------");

    @Test
    void derivesPassFromRequiredChecks() {
        ShipLocalStamp stamp = stamp(List.of(
                check("optional-diagnostic", false, NONZERO, "Optional probe failed", null, nonzeroCommand())));

        assertEquals(ShipLocalStamp.Status.PASS, stamp.status());
    }

    @Test
    void requiredFailureWinsDespiteWorkerProse() {
        ShipLocalStamp stamp = stamp(List.of(
                check("required-check", true, NONZERO, "Everything passed", null, nonzeroCommand()),
                check("worker-report", false, PASS, "Worker claims success", null, null)));

        assertEquals(FAIL, stamp.status());
    }

    @Test
    void requiredNonPassingOutcomesFail() {
        ShipLocalStamp missing = stamp(List.of(check(
                "missing-check", true, MISSING,
                "Required evidence is missing", null, null)));
        ShipLocalStamp empty = stamp(List.of(check(
                "empty-check", true, EMPTY,
                "Required evidence is empty", null, successfulCommand())));
        ShipLocalStamp skipped = stamp(List.of(check(
                "skipped-check", true, SKIPPED,
                "Required check was skipped", null, successfulCommand())));

        assertAll(
                () -> assertEquals(FAIL, missing.status()),
                () -> assertEquals(FAIL, empty.status()),
                () -> assertEquals(FAIL, skipped.status()));
    }

    @Test
    void requiresExplicitWaiversAndLetsFailuresWin() {
        ShipLocalStamp waived = stamp(List.of(
                check("manual-review", true, WAIVED,
                        "Manual review waived", "Accepted local exception", null)));
        ShipLocalStamp failed = stamp(List.of(
                check("manual-review", true, WAIVED,
                        "Manual review waived", "Accepted local exception", null),
                check("required-check", true, NONZERO,
                        "Required check failed", null, nonzeroCommand())));

        assertAll(
                () -> assertEquals(COMPLETED_WITH_WAIVER, waived.status()),
                () -> assertEquals(FAIL, failed.status()),
                () -> assertThrows(IllegalArgumentException.class, () -> check(
                        "manual-review", true, WAIVED, "Waived", null, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> check(
                        "manual-review", false, WAIVED, "Waived", "Not required", null)),
                () -> assertThrows(IllegalArgumentException.class, () -> check(
                        "manual-review", true, PASS, "Passed", "Unclaimed waiver", null)),
                () -> check(
                        "composer-defined-check", true, WAIVED,
                        "Waived", "Accepted", null),
                () -> assertThrows(IllegalArgumentException.class, () -> check(
                        "manual-review", true, WAIVED,
                        "Waived", "Accepted", successfulCommand())));
    }

    @Test
    void validatesCommandFactsAndRedactionBoundaries() {
        ArrayList<String> arguments = new ArrayList<>(
                List.of("--token", ShipLocalStamp.REDACTED,
                        "-Drepo.password=" + ShipLocalStamp.REDACTED, "--verbose"));
        ShipLocalStamp.CommandRun command = command(
                arguments, true, false, 0, STARTED, ENDED,
                digest("stdout"), digest("stderr"));
        arguments.set(3, "--changed");

        assertAll(
                () -> assertEquals("--verbose", command.redactedArguments().get(3)),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("--token=actual-secret"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("-Drepo.password=actual-secret"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("-Daws.secretAccessKey=actual-secret"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("-Drepo.token.value=actual-secret"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("--aws-access-key-id=actual-secret"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("--service-private-key=actual-secret"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("--service-jwt=actual-secret"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("--service-pat=actual-secret"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("--db-pass", "actual-secret"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("--mysql-pwd=actual-secret"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("--client-credentials", "actual-secret"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("Bearer actual-secret"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("https://user:pass@example.test"), true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("--header=Authorization: Bearer actual-secret"),
                        true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of("--authorization", ShipLocalStamp.REDACTED, "credential"),
                        true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> command(
                        List.of(
                                "Authorization: " + ShipLocalStamp.REDACTED,
                                "Bearer " + ShipLocalStamp.REDACTED,
                                "https://" + ShipLocalStamp.REDACTED + "@example.test",
                                "--credentials-file=/tmp/credentials.json",
                                "/tmp/token-project"),
                        true, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr")),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of(), true, false, 0,
                        ENDED, STARTED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of(), true, false, 0,
                        STARTED, ENDED, "sha256:bad", digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShipLocalStamp.CommandRun(
                        "relative/camel", "Camel 4", List.of(),
                        "/tmp/workspace", List.of(digest("input")),
                        true, false, false, 0, STARTED.toString(), ENDED.toString(),
                        "/tmp/stdout.log", digest("stdout"),
                        "/tmp/stderr.log", digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShipLocalStamp.CommandRun(
                        "/usr/bin/camel", "Camel 4", List.of(),
                        "/tmp/workspace", List.of(),
                        true, false, false, 0, STARTED.toString(), ENDED.toString(),
                        "/tmp/stdout.log", digest("stdout"),
                        "/tmp/stderr.log", digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> command(
                        List.of(), false, false, 0,
                        STARTED, ENDED, digest("stdout"), digest("stderr"))),
                () -> assertThrows(IllegalArgumentException.class, () -> check(
                        "validation", true, PASS, "Passed", null, nonzeroCommand())));
    }

    @Test
    void rejectsCredentialBypassesAndAcceptsRunnerRedactionForms() {
        List<List<String>> bypasses = List.of(
                List.of("--auth", "actual-secret"),
                List.of("--passphrase", "actual-secret"),
                List.of("--docker-auth-config", "{\"auth\":\"actual-secret\"}"),
                List.of("DOCKER_AUTH_CONFIG=actual-secret"),
                List.of("NPM_CONFIG__AUTH=actual-secret"),
                List.of("Basic \"actual-secret\""),
                List.of("Bearer 'actual-secret'"),
                List.of("--authorization=Bearer", "actual-secret"),
                List.of("--authorization", "Basic", "actual-secret"),
                List.of("https://actual-secret@example.test"),
                List.of("https://:actual-secret@example.test"),
                List.of("https://@example.test"),
                List.of("-u", "user:actual-secret"),
                List.of("-uuser:actual-secret"),
                List.of("--user=user:actual-secret"),
                List.of("--userpwd", "user:actual-secret"),
                List.of("--proxy-user=user:actual-secret"),
                List.of("--oauth2-bearer=actual-secret"),
                List.of("--cookie=SID=actual-secret"),
                List.of("-bSID=actual-secret"),
                List.of("--config={\"password\":\"actual-secret,tail\"}"),
                List.of("--header=Authorization: \"Basic actual-secret\""));
        for (List<String> arguments : bypasses) {
            assertThrows(IllegalArgumentException.class, () -> command(
                    arguments, true, false, 0,
                    STARTED, ENDED, digest("stdout"), digest("stderr")));
        }

        for (List<String> arguments : List.of(
                List.of("--authorization", ShipLocalStamp.REDACTED, ShipLocalStamp.REDACTED),
                List.of("--authorization", "Bearer", ShipLocalStamp.REDACTED),
                List.of("--authorization=" + ShipLocalStamp.REDACTED, ShipLocalStamp.REDACTED),
                List.of("--authorization=Basic", ShipLocalStamp.REDACTED),
                List.of("Basic " + ShipLocalStamp.REDACTED),
                List.of("Bearer \"" + ShipLocalStamp.REDACTED + "\""),
                List.of("https://" + ShipLocalStamp.REDACTED + "@example.test"),
                List.of("-u", ShipLocalStamp.REDACTED),
                List.of("-u" + ShipLocalStamp.REDACTED),
                List.of("--user=" + ShipLocalStamp.REDACTED),
                List.of("--proxy-user=" + ShipLocalStamp.REDACTED),
                List.of("--oauth2-bearer=" + ShipLocalStamp.REDACTED),
                List.of("--cookie=" + ShipLocalStamp.REDACTED),
                List.of("-b" + ShipLocalStamp.REDACTED),
                List.of("--passphrase", ShipLocalStamp.REDACTED),
                List.of("--docker-auth-config", ShipLocalStamp.REDACTED),
                List.of("--config={\"password\":\"" + ShipLocalStamp.REDACTED + "\"}"),
                List.of("--header=Authorization: \"" + ShipLocalStamp.REDACTED + "\""),
                List.of("", "--system-prompt", ""))) {
            command(arguments, true, false, 0,
                    STARTED, ENDED, digest("stdout"), digest("stderr"));
        }

        assertAll(
                () -> assertTrue(ShipLocalStamp.isSensitiveName("--pwd")),
                () -> assertFalse(ShipLocalStamp.isSensitiveEnvironmentName("PWD")),
                () -> assertFalse(ShipLocalStamp.isSensitiveEnvironmentName("OLDPWD")),
                () -> assertTrue(ShipLocalStamp.isSensitiveEnvironmentName("DB_PWD")),
                () -> assertTrue(ShipLocalStamp.isSensitiveEnvironmentName("OAUTH2_BEARER")),
                () -> assertTrue(ShipLocalStamp.isSensitiveEnvironmentName("COOKIE")),
                () -> assertFalse(ShipLocalStamp.isSensitiveEnvironmentName("COOKIE_FILE")),
                () -> assertFalse(ShipLocalStamp.isSensitiveEnvironmentName("COOKIE_PATH")));
    }

    @Test
    void validatesCommandBackedOutcomeConsistency() {
        ShipLocalStamp.CommandRun timedOut = command(
                List.of("verify"), true, true, null,
                STARTED, ENDED, digest("stdout"), digest("stderr"));
        ShipLocalStamp.CommandRun unlaunched = command(
                List.of("verify"), false, false, null,
                STARTED, ENDED, digest("stdout"), digest("stderr"));
        ShipLocalStamp.CommandRun outputLimited = command(
                List.of("verify"), true, false, true, 0,
                STARTED, ENDED, digest("stdout"), digest("stderr"));
        assertAll(
                () -> check("validation", true, PASS, "Passed", null, null),
                () -> check("validation", true, TIMED_OUT, "Timed out", null, timedOut),
                () -> assertThrows(IllegalArgumentException.class, () -> check(
                        "validation", true, SKIPPED,
                        "Skipped", null, nonzeroCommand())),
                () -> assertFalse(unlaunched.completed()),
                () -> assertFalse(unlaunched.succeeded()),
                () -> assertFalse(outputLimited.succeeded()),
                () -> assertThrows(IllegalArgumentException.class, () -> check(
                        "validation", true, PASS, "Passed", null, outputLimited)),
                () -> assertThrows(IllegalArgumentException.class, () -> check(
                        "validation", true, NONZERO, "Failed", null, timedOut)));
    }

    @Test
    void requiresOneRequiredCheckButAcceptsComposerDefinedEvidence() {
        List<ShipLocalStamp.ToolVersion> customTool = List.of(new ShipLocalStamp.ToolVersion(
                "custom-runner", null, "1.0", SUPPORTED, null));
        List<ShipLocalStamp.Check> customCheck = List.of(check(
                "integration-test-extra", true, PASS, "Custom check passed", null, null));
        List<ShipLocalStamp.Check> optionalOnly = List.of(check(
                "optional-check", false, PASS, "Optional check passed", null, null));

        assertAll(
                () -> create(customTool, customCheck),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create(customTool, optionalOnly)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create(List.of(), customCheck)));
    }

    @Test
    void rejectsPredatedEvidenceAndUnsafeDisplayText() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> ShipLocalStamp.create(
                        RUN_ID,
                        tools(),
                        completeChecks(List.of(check(
                                "command-check",
                                true,
                                PASS,
                                "Command passed",
                                null,
                                successfulCommand()))),
                        STARTED)),
                () -> assertThrows(IllegalArgumentException.class, () -> check(
                        "worker-report",
                        false,
                        PASS,
                        "unsafe\u001b[31m",
                        null,
                        null)),
                () -> assertThrows(IllegalArgumentException.class, () -> check(
                        "worker-report",
                        false,
                        PASS,
                        "unsafe\u202e",
                        null,
                        null)),
                () -> assertThrows(IllegalArgumentException.class, () -> check(
                        "worker-report",
                        false,
                        PASS,
                        "unsafe\uD800",
                        null,
                        null)));
    }

    @Test
    void genericToolDiagnosticsDoNotOverrideRequiredChecks() {
        ShipLocalStamp experimental = stamp(withTool(new ShipLocalStamp.ToolVersion(
                "pi", "/usr/bin/pi", "0.80.6", EXPERIMENTAL, "Version is experimental")), List.of());
        ShipLocalStamp.ToolVersion incompatible = new ShipLocalStamp.ToolVersion(
                "pi", "/usr/bin/pi", "0.70.0", INCOMPATIBLE, "Required JSON mode is absent");
        ShipLocalStamp.ToolVersion untested = new ShipLocalStamp.ToolVersion(
                "pi", "/usr/bin/pi", null, UNTESTED, "Version could not be identified");
        ShipLocalStamp.ToolVersion missing = new ShipLocalStamp.ToolVersion(
                "pi", null, null, ShipLocalStamp.Support.MISSING,
                "Install Pi and configure its executable");
        ShipLocalStamp missingNode = stamp(withTool(new ShipLocalStamp.ToolVersion(
                "node", null, null, ShipLocalStamp.Support.MISSING,
                "Standalone Node was not found")), List.of());
        ShipLocalStamp supportedWithoutExecutable = stamp(withTool(new ShipLocalStamp.ToolVersion(
                "pi", null, "0.80.6", SUPPORTED, null)), List.of());
        ShipLocalStamp osWithExecutable = stamp(withTool(new ShipLocalStamp.ToolVersion(
                "os", "/usr/bin/uname", "Linux 6.15", SUPPORTED, null)), List.of());

        assertAll(
                () -> assertEquals(ShipLocalStamp.Status.PASS, experimental.status()),
                () -> assertEquals(ShipLocalStamp.Status.PASS, missingNode.status()),
                () -> assertEquals(ShipLocalStamp.Status.PASS, supportedWithoutExecutable.status()),
                () -> assertEquals(ShipLocalStamp.Status.PASS, osWithExecutable.status()),
                () -> assertEquals(ShipLocalStamp.Status.PASS, stamp(
                        withTool(incompatible), List.of()).status()),
                () -> assertEquals(ShipLocalStamp.Status.PASS, stamp(
                        withTool(untested), List.of()).status()),
                () -> assertEquals(ShipLocalStamp.Status.PASS, stamp(
                        withTool(missing), List.of()).status()),
                () -> assertEquals(FAIL, stamp(
                        withTool(missing),
                        List.of(check(
                                "required-capability",
                                true,
                                MISSING,
                                "Pi is unavailable",
                                null,
                                null)))
                        .status()),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShipLocalStamp.ToolVersion(
                        "missing", "/usr/bin/tool", null,
                        ShipLocalStamp.Support.MISSING, "Install it")),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShipLocalStamp.ToolVersion(
                        "missing", null, "1.0",
                        ShipLocalStamp.Support.MISSING, "Install it")),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShipLocalStamp.ToolVersion(
                        "missing", null, null,
                        ShipLocalStamp.Support.MISSING, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShipLocalStamp.ToolVersion(
                        "supported", null, null, SUPPORTED, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShipLocalStamp.ToolVersion(
                        "supported", null, "1.0", SUPPORTED, "Unexpected warning")),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShipLocalStamp.ToolVersion(
                        "experimental", null, "1.0", EXPERIMENTAL, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShipLocalStamp.ToolVersion(
                        "tool", "relative/tool", "1.0", SUPPORTED, null)));
    }

    @Test
    void roundTripsJsonWithStatusAndCanonicalTimestamps() throws Exception {
        ShipLocalStamp original = stamp(List.of());
        ObjectMapper json = new ObjectMapper();

        String encoded = json.writeValueAsString(original);
        ShipLocalStamp decoded = json.readValue(encoded, ShipLocalStamp.class);

        assertAll(
                () -> assertEquals(original, decoded),
                () -> assertTrue(encoded.contains("\"status\":\"PASS\"")),
                () -> assertTrue(encoded.contains("\"generatedAt\":\"2026-07-24T08:00:01Z\"")),
                () -> assertThrows(Exception.class, () -> json.readValue(
                        encoded.replace("\"status\":\"PASS\"", "\"status\":\"FAIL\""),
                        ShipLocalStamp.class)));
    }

    @Test
    void rejectsDuplicateToolAndCheckIds() {
        List<ShipLocalStamp.ToolVersion> duplicateTools = new ArrayList<>(tools());
        duplicateTools.add(tools().get(0));
        List<ShipLocalStamp.Check> duplicateChecks = new ArrayList<>(completeChecks(List.of()));
        duplicateChecks.add(duplicateChecks.get(0));

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create(duplicateTools, completeChecks(List.of()))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create(tools(), duplicateChecks)));
    }

    @Test
    void atomicallyPersistsAndStrictlyReloadsTheStamp(@TempDir Path directory) throws Exception {
        makePrivate(directory);
        ShipLocalStamp passed = persistableStamp(directory, 0);
        ShipLocalStamp failed = persistableStamp(directory, 1);

        Path report = ShipLocalStampStore.write(directory, RUN_ID, failed);
        byte[] failedReport = Files.readAllBytes(report);
        Files.writeString(directory.resolve("stdout.log"), "tampered");
        assertThrows(IOException.class, () -> ShipLocalStampStore.write(directory, RUN_ID, passed));
        assertArrayEquals(failedReport, Files.readAllBytes(report));
        assertThrows(IOException.class, () -> ShipLocalStampStore.read(directory, RUN_ID));

        Files.writeString(directory.resolve("stdout.log"), "stdout");
        assertEquals(failed, ShipLocalStampStore.read(directory, RUN_ID));
        ShipLocalStampStore.write(directory, RUN_ID, passed);

        assertAll(
                () -> assertEquals(directory.resolve("stamp.json"), report),
                () -> assertEquals(passed, ShipLocalStampStore.read(directory, RUN_ID)));

        String encoded = Files.readString(report);
        for (String invalid : List.of(
                "null",
                "{\"schemaVersion\":1," + encoded.substring(1),
                encoded + " true",
                replace(encoded, "\"runId\"", "\"unknown\":true,\"runId\""),
                replace(encoded, "\"status\" : \"PASS\"", "\"status\" : 0"),
                replace(encoded, "\"schemaVersion\" : 1", "\"schemaVersion\" : 1.0"),
                replace(encoded, "\"schemaVersion\" : 1", "\"schemaVersion\" : \"1\""),
                replace(encoded, "\"launched\" : true", "\"launched\" : null"))) {
            Files.writeString(report, invalid);
            assertThrows(IOException.class, () -> ShipLocalStampStore.read(directory, RUN_ID));
        }
        Files.writeString(report, encoded);
        assertEquals(passed, ShipLocalStampStore.read(directory, RUN_ID));

        String anotherRun = "ship-ffffffffffffffffffffffffffffffff";
        byte[] unchanged = Files.readAllBytes(report);
        assertThrows(
                IOException.class,
                () -> ShipLocalStampStore.write(directory, anotherRun, passed));
        assertArrayEquals(unchanged, Files.readAllBytes(report));
        assertThrows(
                IOException.class,
                () -> ShipLocalStampStore.read(directory, anotherRun));
    }

    @Test
    void verifiedReadBindsTheStampAndDigestToTheSameBytes(@TempDir Path directory)
            throws Exception {
        makePrivate(directory);
        ShipLocalStamp stamp = persistableStamp(directory, 0);
        Path report = ShipLocalStampStore.write(directory, RUN_ID, stamp);
        byte[] encoded = Files.readAllBytes(report);

        ShipLocalStampStore.VerifiedStamp verified
                = ShipLocalStampStore.readVerified(directory, RUN_ID);
        Files.writeString(report, "changed after the verified read");

        assertAll(
                () -> assertEquals(stamp, verified.stamp()),
                () -> assertEquals(report, verified.path()),
                () -> assertEquals(ShipDigest.sha256(encoded), verified.digest()),
                () -> assertFalse(verified.digest().equals(
                        ShipDigest.sha256(Files.readAllBytes(report)))));
    }

    @Test
    void acceptsSymbolicAncestorButRejectsSymbolicDirectory(@TempDir Path directory)
            throws Exception {
        Path realAncestor = Files.createDirectory(directory.resolve("real-ancestor"));
        Path evidence = Files.createDirectory(realAncestor.resolve("evidence"));
        makePrivate(evidence);
        Path symbolicAncestor = directory.resolve("symbolic-ancestor");
        Files.createSymbolicLink(symbolicAncestor, realAncestor.getFileName());
        Path aliasedEvidence = symbolicAncestor.resolve("evidence");
        ShipLocalStamp stamp = persistableStamp(aliasedEvidence, 0);

        Path report = ShipLocalStampStore.write(aliasedEvidence, RUN_ID, stamp);

        assertEquals(evidence.resolve("stamp.json"), report);
        assertEquals(stamp, ShipLocalStampStore.read(aliasedEvidence, RUN_ID));
        Path symbolicEvidence = directory.resolve("symbolic-evidence");
        Files.createSymbolicLink(symbolicEvidence, evidence);
        assertThrows(
                IOException.class,
                () -> ShipLocalStampStore.read(symbolicEvidence, RUN_ID));
    }

    @Test
    void rejectsReservedAliasedAndPhysicallySharedLogs(@TempDir Path root)
            throws Exception {
        Path directory = Files.createDirectory(root.resolve("evidence"));
        makePrivate(directory);
        ShipLocalStamp passed = persistableStamp(directory, 0);
        Path report = ShipLocalStampStore.write(directory, RUN_ID, passed);
        Path stderr = directory.resolve("stderr.log");

        ShipLocalStamp.CommandRun reserved = storedCommand(
                directory,
                report,
                stderr,
                0,
                ShipDigest.sha256(Files.readAllBytes(report)),
                digest("stderr"));
        ShipLocalStamp reservedStamp = persistableStamp(directory, 0, List.of(check(
                "evidence-check", true, PASS, "Evidence check passed", null, reserved)));
        byte[] unchanged = Files.readAllBytes(report);
        IOException reservedFailure = assertThrows(
                IOException.class,
                () -> ShipLocalStampStore.write(directory, RUN_ID, reservedStamp));
        assertTrue(reservedFailure.getMessage().contains("outside its evidence directory"));
        assertArrayEquals(unchanged, Files.readAllBytes(report));

        Path nested = Files.createDirectory(root.resolve("outside"));
        Path nestedLog = writePrivate(nested.resolve("stdout.log"), "nested");
        Path alias = directory.resolve("alias");
        Files.createSymbolicLink(alias, nested);
        ShipLocalStamp.CommandRun aliased = storedCommand(
                directory,
                alias.resolve(nestedLog.getFileName()),
                stderr,
                0,
                digest("nested"),
                digest("stderr"));
        IOException aliasFailure = assertThrows(
                IOException.class,
                () -> ShipLocalStampStore.write(
                        directory,
                        RUN_ID,
                        persistableStamp(directory, 0, List.of(check(
                                "evidence-check",
                                true,
                                PASS,
                                "Evidence check passed",
                                null,
                                aliased)))));
        assertTrue(aliasFailure.getMessage().contains("escaped its evidence directory"));

        Path shared = writePrivate(directory.resolve("shared.log"), "shared");
        Path sharedAlias = directory.resolve("shared-alias.log");
        Files.createLink(sharedAlias, shared);
        ShipLocalStamp.CommandRun hardLinked = storedCommand(
                directory,
                shared,
                sharedAlias,
                0,
                digest("shared"),
                digest("shared"));
        IOException hardLinkFailure = assertThrows(
                IOException.class,
                () -> ShipLocalStampStore.write(
                        directory,
                        RUN_ID,
                        persistableStamp(directory, 0, List.of(check(
                                "evidence-check",
                                true,
                                PASS,
                                "Evidence check passed",
                                null,
                                hardLinked)))));
        assertTrue(hardLinkFailure.getMessage().contains(
                "stdout and stderr must be distinct files"));
    }

    @Test
    void requiresPrivateEvidenceAndPersistsPrivateFiles(@TempDir Path directory)
            throws Exception {
        makePrivate(directory);
        ShipLocalStamp stamp = persistableStamp(directory, 0);
        Path stdout = directory.resolve("stdout.log");
        Path stderr = directory.resolve("stderr.log");

        Files.setPosixFilePermissions(
                directory, PosixFilePermissions.fromString("rwxr-x---"));
        IOException directoryFailure = assertThrows(
                IOException.class,
                () -> ShipLocalStampStore.write(directory, RUN_ID, stamp));
        assertTrue(directoryFailure.getMessage().contains("0700"));

        Files.setPosixFilePermissions(directory, PRIVATE_DIRECTORY);
        Files.setPosixFilePermissions(
                stdout, PosixFilePermissions.fromString("rw-r-----"));
        IOException logFailure = assertThrows(
                IOException.class,
                () -> ShipLocalStampStore.write(directory, RUN_ID, stamp));
        assertTrue(logFailure.getMessage().contains("0600"));

        Files.setPosixFilePermissions(stdout, PRIVATE_FILE);
        Path report = ShipLocalStampStore.write(directory, RUN_ID, stamp);
        assertAll(
                () -> assertEquals(
                        PRIVATE_DIRECTORY, Files.getPosixFilePermissions(directory)),
                () -> assertEquals(PRIVATE_FILE, Files.getPosixFilePermissions(report)),
                () -> assertEquals(PRIVATE_FILE, Files.getPosixFilePermissions(stdout)),
                () -> assertEquals(PRIVATE_FILE, Files.getPosixFilePermissions(stderr)));
    }

    private static ShipLocalStamp persistableStamp(Path directory, int buildExit)
            throws IOException {
        return persistableStamp(directory, buildExit, List.of());
    }

    private static ShipLocalStamp persistableStamp(
            Path directory,
            int buildExit,
            List<ShipLocalStamp.Check> replacements)
            throws IOException {
        Path stdout = writePrivate(directory.resolve("stdout.log"), "stdout");
        Path stderr = writePrivate(directory.resolve("stderr.log"), "stderr");
        ShipLocalStamp.CommandRun command = storedCommand(directory, stdout, stderr, buildExit);
        List<ShipLocalStamp.Check> commands = new ArrayList<>(
                List.of(check(
                        "command-check",
                        true,
                        buildExit == 0 ? PASS : NONZERO,
                        buildExit == 0 ? "Command passed" : "Command failed",
                        null,
                        command)));
        for (ShipLocalStamp.Check replacement : replacements) {
            commands.removeIf(check -> check.id().equals(replacement.id()));
            commands.add(replacement);
        }
        return stamp(commands);
    }

    private static void makePrivate(Path directory) throws IOException {
        Assumptions.assumeTrue(
                directory.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Files.setPosixFilePermissions(directory, PRIVATE_DIRECTORY);
    }

    private static Path writePrivate(Path path, String content) throws IOException {
        Path file = Files.writeString(path, content);
        Files.setPosixFilePermissions(file, PRIVATE_FILE);
        return file;
    }

    private static ShipLocalStamp.CommandRun storedCommand(
            Path workingDirectory, Path stdout, Path stderr, int exitCode) {
        return storedCommand(
                workingDirectory,
                stdout,
                stderr,
                exitCode,
                digest("stdout"),
                digest("stderr"));
    }

    private static ShipLocalStamp.CommandRun storedCommand(
            Path workingDirectory,
            Path stdout,
            Path stderr,
            int exitCode,
            String stdoutDigest,
            String stderrDigest) {
        return new ShipLocalStamp.CommandRun(
                "/usr/bin/camel",
                "Camel 4.15.0",
                List.of("verify"),
                workingDirectory.toString(),
                List.of(digest("input")),
                true,
                false,
                false,
                exitCode,
                STARTED.toString(),
                ENDED.toString(),
                stdout.toString(),
                stdoutDigest,
                stderr.toString(),
                stderrDigest);
    }

    private static ShipLocalStamp stamp(List<ShipLocalStamp.Check> checks) {
        return stamp(tools(), checks);
    }

    private static ShipLocalStamp stamp(
            List<ShipLocalStamp.ToolVersion> tools, List<ShipLocalStamp.Check> checks) {
        return create(tools, completeChecks(checks));
    }

    private static ShipLocalStamp create(
            List<ShipLocalStamp.ToolVersion> tools, List<ShipLocalStamp.Check> checks) {
        return ShipLocalStamp.create(RUN_ID, tools, checks, ENDED);
    }

    private static List<ShipLocalStamp.ToolVersion> tools() {
        return List.of(new ShipLocalStamp.ToolVersion(
                "pi", "/usr/bin/pi", "0.80.6", SUPPORTED, null));
    }

    private static List<ShipLocalStamp.ToolVersion> withTool(
            ShipLocalStamp.ToolVersion replacement) {
        List<ShipLocalStamp.ToolVersion> replaced = new ArrayList<>(tools());
        replaced.removeIf(tool -> tool.tool().equals(replacement.tool()));
        replaced.add(replacement);
        return List.copyOf(replaced);
    }

    private static List<ShipLocalStamp.Check> completeChecks(List<ShipLocalStamp.Check> replacements) {
        List<ShipLocalStamp.Check> checks = new ArrayList<>(
                List.of(
                        check("baseline-check", true, PASS, "Baseline check passed", null, null)));
        for (ShipLocalStamp.Check replacement : replacements) {
            checks.removeIf(check -> check.id().equals(replacement.id()));
            checks.add(replacement);
        }
        return List.copyOf(checks);
    }

    private static ShipLocalStamp.Check check(
            String id,
            boolean required,
            ShipLocalStamp.Outcome outcome,
            String summary,
            String waiver,
            ShipLocalStamp.CommandRun command) {
        return new ShipLocalStamp.Check(id, required, outcome, summary, waiver, command);
    }

    private static ShipLocalStamp.CommandRun successfulCommand() {
        return command(
                List.of("verify"), true, false, 0,
                STARTED, ENDED, digest("stdout"), digest("stderr"));
    }

    private static ShipLocalStamp.CommandRun nonzeroCommand() {
        return command(
                List.of("verify"), true, false, 1,
                STARTED, ENDED, digest("stdout"), digest("stderr"));
    }

    private static ShipLocalStamp.CommandRun command(
            List<String> arguments,
            boolean launched,
            boolean timedOut,
            Integer exitCode,
            Instant startedAt,
            Instant endedAt,
            String stdoutDigest,
            String stderrDigest) {
        return command(
                arguments,
                launched,
                timedOut,
                false,
                exitCode,
                startedAt,
                endedAt,
                stdoutDigest,
                stderrDigest);
    }

    private static ShipLocalStamp.CommandRun command(
            List<String> arguments,
            boolean launched,
            boolean timedOut,
            boolean outputLimited,
            Integer exitCode,
            Instant startedAt,
            Instant endedAt,
            String stdoutDigest,
            String stderrDigest) {
        return new ShipLocalStamp.CommandRun(
                "/usr/bin/camel",
                "Camel 4.15.0",
                arguments,
                "/tmp/camel-kit-workspace",
                List.of(digest("input")),
                launched,
                timedOut,
                outputLimited,
                exitCode,
                startedAt.toString(),
                endedAt.toString(),
                "/tmp/camel-kit-stdout.log",
                stdoutDigest,
                "/tmp/camel-kit-stderr.log",
                stderrDigest);
    }

    private static String digest(String value) {
        return ShipDigest.sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String replace(String source, String target, String replacement) {
        assertTrue(source.contains(target), target);
        return source.replace(target, replacement);
    }
}

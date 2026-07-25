package io.github.luigidemasi.camelkit.ship.evidence;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun;

/** Controller-derived result of the required local Ship checks. */
public record ShipLocalStamp(
        int schemaVersion,
        String runId,
        Status status,
        List<ToolVersion> toolVersions,
        List<Check> checks,
        String generatedAt) {

    public static final int SCHEMA_VERSION = 1;
    public static final String REDACTED = "<redacted>";

    private static final int MAX_TOOLS = 32;
    private static final int MAX_CHECKS = 20_000;
    private static final int MAX_ARGUMENTS = 256;
    private static final int MAX_PATH_LENGTH = 4_096;
    private static final int MAX_VERSION_LENGTH = 1_024;
    private static final int MAX_TEXT_LENGTH = 4_096;
    private static final int MAX_ARGUMENT_LENGTH = 4_096;
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,127}");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(docker[-_]?auth[-_]?config|npm[-_]?config[-_]*auth"
                                                                        + "|password|passwd|pass|passphrase|pwd"
                                                                        + "|secret|token|credentials?|auth(?:orization)?|api[-_]?key"
                                                                        + "|access[-_]?key(?:[-_]?id)?|private[-_]?key|jwt|pat)"
                                                                        + "([\"']?\\s*[:=]\\s*)"
                                                                        + "(\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\"'\\r\\n,}]+)");
    private static final Pattern AUTHORIZATION_SCHEME = Pattern.compile(
            "(?i)[\"']?\\b(Bearer|Basic)\\s+[\"']?([^\\s\"',}]+)[\"']?");
    private static final Pattern URL_USERINFO = Pattern.compile(
            "[A-Za-z][A-Za-z0-9+.-]*://([^/@\\s]*)@");

    public ShipLocalStamp {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported local Stamp schema version");
        }
        if (!ShipRun.isRunId(runId)) {
            throw new IllegalArgumentException("Local Stamp run ID is invalid");
        }
        toolVersions = boundedCopy(toolVersions, 1, MAX_TOOLS, "tool versions");
        checks = boundedCopy(checks, 1, MAX_CHECKS, "checks");
        requireUnique(toolVersions.stream().map(ToolVersion::tool).toList(), "tool");
        requireUnique(checks.stream().map(Check::id).toList(), "check");
        if (checks.stream().noneMatch(Check::required)) {
            throw new IllegalArgumentException("Local Stamp requires at least one required check");
        }
        if (status != deriveStatus(checks)) {
            throw new IllegalArgumentException("Local Stamp status does not match its evidence");
        }
        generatedAt = canonicalInstant(generatedAt, "generated timestamp");
        Instant generated = Instant.parse(generatedAt);
        if (checks.stream()
                .map(Check::command)
                .filter(Objects::nonNull)
                .map(CommandRun::endedAt)
                .map(Instant::parse)
                .anyMatch(ended -> ended.isAfter(generated))) {
            throw new IllegalArgumentException(
                    "Local Stamp generation precedes its command evidence");
        }
    }

    public static ShipLocalStamp create(
            String runId,
            List<ToolVersion> toolVersions,
            List<Check> checks,
            Instant generatedAt) {
        return new ShipLocalStamp(
                SCHEMA_VERSION,
                runId,
                deriveStatus(checks),
                toolVersions,
                checks,
                Objects.requireNonNull(generatedAt, "generatedAt").toString());
    }

    /** Derives the result from required checks; diagnostic support labels and worker prose cannot change it. */
    private static Status deriveStatus(List<Check> checks) {
        if (checks.stream().anyMatch(check -> check.required() && check.outcome().failed())) {
            return Status.FAIL;
        }
        if (checks.stream().anyMatch(check -> check.required() && check.outcome() == Outcome.WAIVED)) {
            return Status.COMPLETED_WITH_WAIVER;
        }
        return Status.PASS;
    }

    public enum Status {
        PASS,
        COMPLETED_WITH_WAIVER,
        FAIL
    }

    public enum Outcome {
        PASS,
        FAIL,
        MISSING,
        TIMED_OUT,
        NONZERO,
        SKIPPED,
        EMPTY,
        WAIVED;

        private boolean failed() {
            return this != PASS && this != WAIVED;
        }
    }

    public enum Support {
        SUPPORTED,
        EXPERIMENTAL,
        INCOMPATIBLE,
        UNTESTED,
        MISSING;

    }

    /** Detected local tool/runtime diagnostic and its support status. */
    public record ToolVersion(
            String tool, String executable, String version, Support support, String message) {

        public ToolVersion {
            requireId(tool, "tool");
            Objects.requireNonNull(support, "support");
            if (support == Support.MISSING) {
                if (executable != null || version != null) {
                    throw new IllegalArgumentException(
                            "A missing tool cannot claim an executable or version");
                }
                message = requireText(message, MAX_TEXT_LENGTH, "tool guidance");
            } else {
                if (executable != null) {
                    executable = requireAbsolutePath(executable, "tool executable");
                }
                if (version != null || support != Support.UNTESTED) {
                    version = requireSingleLine(version, MAX_VERSION_LENGTH, "tool version");
                }
                if (support == Support.SUPPORTED) {
                    if (message != null) {
                        throw new IllegalArgumentException(
                                "Supported tool diagnostics do not carry a warning");
                    }
                } else {
                    message = requireText(message, MAX_TEXT_LENGTH, "tool warning");
                }
            }
        }
    }

    /** One controller check. Only required outcomes participate in the Stamp status. */
    public record Check(
            String id,
            boolean required,
            Outcome outcome,
            String summary,
            String waiver,
            CommandRun command) {

        public Check {
            requireId(id, "check");
            Objects.requireNonNull(outcome, "outcome");
            summary = requireText(summary, MAX_TEXT_LENGTH, "check summary");
            if (outcome == Outcome.WAIVED) {
                if (!required) {
                    throw new IllegalArgumentException("Only a required check can be waived");
                }
                waiver = requireText(waiver, MAX_TEXT_LENGTH, "waiver");
            } else if (waiver != null) {
                throw new IllegalArgumentException("Only a waived check can carry a waiver");
            }
            switch (outcome) {
                case PASS -> {
                    if (command != null && !command.succeeded()) {
                        throw new IllegalArgumentException(
                                "A passing check cannot contain an unsuccessful command");
                    }
                }
                case MISSING -> {
                    if (command != null) {
                        throw new IllegalArgumentException("A missing check cannot contain a command run");
                    }
                }
                case TIMED_OUT -> {
                    if (command == null || !command.timedOut()) {
                        throw new IllegalArgumentException(
                                "A timed-out check requires a timed-out command");
                    }
                }
                case NONZERO -> {
                    if (command == null || !command.completed() || command.exitCode() == 0) {
                        throw new IllegalArgumentException(
                                "A nonzero check requires a completed nonzero command");
                    }
                }
                case SKIPPED, EMPTY -> {
                    if (command != null && !command.succeeded()) {
                        throw new IllegalArgumentException(
                                "A command-backed " + outcome + " check requires a successful command");
                    }
                }
                case WAIVED -> {
                    if (command != null) {
                        throw new IllegalArgumentException(
                                "A waived check cannot contain command evidence");
                    }
                }
                case FAIL -> {
                    // The outcome may come from deterministic validation of a successful command's result.
                }
            }
        }
    }

    /** Exact local command facts retained by a controller check. */
    public record CommandRun(
            String executable,
            String version,
            List<String> redactedArguments,
            String workingDirectory,
            List<String> inputDigests,
            boolean launched,
            boolean timedOut,
            boolean outputLimited,
            Integer exitCode,
            String startedAt,
            String endedAt,
            String stdoutLog,
            String stdoutDigest,
            String stderrLog,
            String stderrDigest) {

        public CommandRun {
            executable = requireAbsolutePath(executable, "command executable");
            version = requireSingleLine(version, MAX_VERSION_LENGTH, "command version");
            redactedArguments = boundedCopy(
                    redactedArguments, 0, MAX_ARGUMENTS, "redacted arguments");
            requireRedactedArguments(redactedArguments);
            workingDirectory = requireAbsolutePath(workingDirectory, "command working directory");
            inputDigests = boundedCopy(inputDigests, 1, MAX_ARGUMENTS, "command input digests");
            inputDigests.forEach(digest -> requireDigest(digest, "command input digest"));
            startedAt = canonicalInstant(startedAt, "command start");
            endedAt = canonicalInstant(endedAt, "command end");
            if (Instant.parse(endedAt).isBefore(Instant.parse(startedAt))) {
                throw new IllegalArgumentException("Command end precedes its start");
            }
            stdoutLog = requireAbsolutePath(stdoutLog, "stdout log");
            stderrLog = requireAbsolutePath(stderrLog, "stderr log");
            if (stdoutLog.equals(stderrLog)) {
                throw new IllegalArgumentException("Command stdout and stderr logs must be distinct");
            }
            requireDigest(stdoutDigest, "stdout digest");
            requireDigest(stderrDigest, "stderr digest");
            if (!launched && (timedOut || outputLimited || exitCode != null)) {
                throw new IllegalArgumentException(
                        "An unlaunched command cannot report execution results");
            }
            if (launched && timedOut && exitCode != null) {
                throw new IllegalArgumentException(
                        "A timed-out command cannot report a completed exit code");
            }
            if (launched && !timedOut && exitCode == null) {
                throw new IllegalArgumentException(
                        "A completed command must report its exit code");
            }
        }

        public boolean completed() {
            return launched && !timedOut && exitCode != null;
        }

        public boolean succeeded() {
            return completed() && !outputLimited && exitCode == 0;
        }
    }

    private static <T> List<T> boundedCopy(
            List<T> values, int minimum, int maximum, String label) {
        if (values == null || values.size() < minimum || values.size() > maximum
                || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Local Stamp " + label + " must contain " + minimum + ".." + maximum + " entries");
        }
        return List.copyOf(values);
    }

    private static void requireUnique(List<String> values, String label) {
        Set<String> unique = new HashSet<>(values);
        if (unique.size() != values.size()) {
            throw new IllegalArgumentException("Local Stamp " + label + " IDs must be unique");
        }
    }

    private static void requireId(String value, String label) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Local Stamp " + label + " ID is invalid");
        }
    }

    private static String requireAbsolutePath(String value, String label) {
        String path = requireSingleLine(value, MAX_PATH_LENGTH, label);
        try {
            Path parsed = Path.of(path);
            if (!parsed.isAbsolute() || !parsed.normalize().toString().equals(path)) {
                throw new IllegalArgumentException(
                        "Local Stamp " + label + " must be a normalized absolute path");
            }
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Local Stamp " + label + " is invalid", e);
        }
        return path;
    }

    private static String canonicalInstant(String value, String label) {
        try {
            Instant instant = Instant.parse(Objects.requireNonNull(value, label));
            if (!instant.toString().equals(value)) {
                throw new IllegalArgumentException(
                        "Local Stamp " + label + " must be canonical");
            }
            return value;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Local Stamp " + label + " is invalid", e);
        }
    }

    private static String requireText(String value, int maximum, String label) {
        if (value == null || value.isBlank() || value.length() > maximum
                || containsUnsafeDisplayCharacter(value)) {
            throw new IllegalArgumentException(
                    "Local Stamp " + label + " must contain 1.." + maximum + " safe characters");
        }
        return value;
    }

    private static String requireSingleLine(String value, int maximum, String label) {
        String text = requireText(value, maximum, label);
        if (text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Local Stamp " + label + " must be one line");
        }
        return text;
    }

    private static void requireDigest(String value, String label) {
        if (!ShipDigest.isSha256(value)) {
            throw new IllegalArgumentException(
                    "Local Stamp " + label + " must be a lowercase SHA-256 digest");
        }
    }

    private static void requireRedactedArguments(List<String> arguments) {
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            if (argument == null
                    || argument.length() > MAX_ARGUMENT_LENGTH
                    || (!argument.isEmpty() && argument.isBlank())
                    || containsUnsafeDisplayCharacter(argument)
                    || argument.indexOf('\r') >= 0
                    || argument.indexOf('\n') >= 0) {
                throw new IllegalArgumentException(
                        "Local Stamp command argument must contain 0.."
                                                   + MAX_ARGUMENT_LENGTH + " safe characters on one line");
            }
            requireKnownSecretsRedacted(argument);
            if (compactCredential(argument)) {
                if (!isRedactedValue(argument.substring(2))) {
                    throw new IllegalArgumentException(
                            "Sensitive command arguments must use " + REDACTED);
                }
                continue;
            }
            int separator = argument.indexOf('=');
            String name = separator < 0 ? argument : argument.substring(0, separator);
            boolean positionalName = separator < 0
                    && (name.startsWith("-")
                            || authorizationName(name));
            if (!isSensitiveOption(name) || (separator < 0 && !positionalName)) {
                continue;
            }
            String value = separator < 0
                    ? index + 1 < arguments.size() ? arguments.get(index + 1) : null
                    : argument.substring(separator + 1).trim();
            int credentialIndex = separator < 0 ? index + 2 : index + 1;
            if (authorizationName(name) && authorizationScheme(value)) {
                if (credentialIndex >= arguments.size()
                        || !isRedactedValue(arguments.get(credentialIndex))) {
                    throw new IllegalArgumentException(
                            "Authorization arguments must redact the complete credential");
                }
                continue;
            }
            if (!isRedactedValue(value)) {
                throw new IllegalArgumentException(
                        "Sensitive command arguments must use " + REDACTED);
            }
            if (authorizationName(name)
                    && credentialIndex < arguments.size()
                    && !arguments.get(credentialIndex).startsWith("-")
                    && !isRedactedValue(arguments.get(credentialIndex))) {
                throw new IllegalArgumentException(
                        "Authorization arguments must redact the complete credential");
            }
        }
    }

    private static void requireKnownSecretsRedacted(String argument) {
        Matcher assignment = SENSITIVE_ASSIGNMENT.matcher(argument);
        while (assignment.find()) {
            if (!isRedactedValue(assignment.group(3).trim())
                    && !(authorizationName(assignment.group(1))
                            && authorizationScheme(assignment.group(3)))) {
                throw new IllegalArgumentException(
                        "Sensitive command arguments must use " + REDACTED);
            }
        }
        Matcher authorization = AUTHORIZATION_SCHEME.matcher(argument);
        while (authorization.find()) {
            if (!REDACTED.equals(authorization.group(2))) {
                throw new IllegalArgumentException(
                        "Authorization command arguments must use " + REDACTED);
            }
        }
        Matcher userInfo = URL_USERINFO.matcher(argument);
        while (userInfo.find()) {
            if (!REDACTED.equals(userInfo.group(1))) {
                throw new IllegalArgumentException(
                        "URL credentials in command arguments must use " + REDACTED);
            }
        }
    }

    private static boolean isRedactedValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.length() >= 2
                && (normalized.charAt(0) == '"' || normalized.charAt(0) == '\'')
                && normalized.charAt(normalized.length() - 1) == normalized.charAt(0)) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return REDACTED.equals(normalized)
                || ("Bearer " + REDACTED).equalsIgnoreCase(normalized)
                || ("Basic " + REDACTED).equalsIgnoreCase(normalized);
    }

    private static boolean containsUnsafeDisplayCharacter(String value) {
        for (int offset = 0; offset < value.length();) {
            char current = value.charAt(offset);
            if (Character.isHighSurrogate(current)) {
                if (offset + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(offset + 1))) {
                    return true;
                }
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    public static boolean isSensitiveName(String value) {
        String normalized = normalizedSensitiveName(value);
        List<String> words = List.of(normalized.split("[^a-z0-9]+"));
        if (words.size() > 1
                && Set.of("file", "path", "dir", "directory")
                        .contains(words.get(words.size() - 1))) {
            return false;
        }
        String joined = String.join("", words);
        return words.stream().anyMatch(Set.of(
                "pass",
                "passphrase",
                "password",
                "passwd",
                "pwd",
                "secret",
                "token",
                "credential",
                "credentials",
                "auth",
                "authorization",
                "bearer",
                "cookie",
                "jwt",
                "pat")::contains)
                || Set.of(
                        "password",
                        "passwd",
                        "passphrase",
                        "pwd",
                        "secret",
                        "token",
                        "credential",
                        "credentials",
                        "auth",
                        "authorization",
                        "bearer",
                        "cookie",
                        "jwt",
                        "pat")
                        .stream()
                        .anyMatch(joined::endsWith)
                || joined.contains("apikey")
                || joined.contains("accesskey")
                || joined.contains("privatekey");
    }

    public static boolean isSensitiveEnvironmentName(String value) {
        return !"PWD".equals(value)
                && !"OLDPWD".equals(value)
                && isSensitiveName(value);
    }

    private static boolean isSensitiveOption(String value) {
        return isSensitiveName(value)
                || "-u".equals(value)
                || "-b".equals(value)
                || "--user".equals(value)
                || "--userpwd".equals(value)
                || "--proxy-user".equals(value)
                || "--oauth2-bearer".equals(value)
                || "--cookie".equals(value);
    }

    private static boolean compactCredential(String value) {
        return value.length() > 2
                && !value.startsWith("--")
                && (value.startsWith("-u")
                        || (value.startsWith("-b")
                                && (value.indexOf('=', 2) >= 0
                                        || isRedactedValue(value.substring(2)))));
    }

    private static boolean authorizationName(String name) {
        String normalized = normalizedSensitiveName(name);
        return normalized.equals("authorization")
                || normalized.equals("auth")
                || normalized.endsWith("-authorization")
                || normalized.endsWith("-auth");
    }

    private static boolean authorizationScheme(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.length() >= 2
                && (normalized.charAt(0) == '"' || normalized.charAt(0) == '\'')
                && normalized.charAt(normalized.length() - 1) == normalized.charAt(0)) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return "basic".equalsIgnoreCase(normalized)
                || "bearer".equalsIgnoreCase(normalized);
    }

    private static String normalizedSensitiveName(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replaceFirst("^-+", "");
    }
}

package io.github.luigidemasi.camelkit.ship.evidence.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.github.luigidemasi.camelkit.ship.ShipArtifactLimits;

/** Builds and inspects the exact accepted Camel Main route-resource JAR using only the JDK. */
public final class ShipMainPackageMain {

    public static final String PAYLOAD_VERSION = "Camel Kit deterministic Main package 1";

    private static final int SCHEMA_VERSION = 1;
    static final int MAX_ROUTES = 64;
    private static final int MAX_ROUTE_BYTES = ShipArtifactLimits.MAX_ROUTE_YAML_BYTES;
    static final int MAX_ROUTE_PATH_CHARACTERS = 4_096;
    private static final int MAX_ROUTE_PATH_UTF8_BYTES = MAX_ROUTE_PATH_CHARACTERS * 4;
    private static final long MAX_ZIP_METADATA_BYTES_PER_ROUTE
            = 2L * MAX_ROUTE_PATH_UTF8_BYTES + 1_024;
    static final long MAX_PACKAGE_BYTES = (long) MAX_ROUTES
                                          * (MAX_ROUTE_BYTES + MAX_ZIP_METADATA_BYTES_PER_ROUTE) + 65_536;
    public static final int MAX_SUMMARY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SUMMARY_FIELDS = 9 + MAX_ROUTES * 3;
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    private ShipMainPackageMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1 && "--payload-version".equals(arguments[0])) {
            System.out.println(PAYLOAD_VERSION);
            return;
        }
        Path temporary = Files.createTempDirectory("camel-kit-ship-main-package-");
        Path archive = temporary.resolve("main-routes.jar");
        try {
            Summary summary = packageAndInspect(Path.of("/workspace"), archive, List.of(arguments));
            System.out.write(summary.encode());
        } finally {
            deletePackage(temporary, archive);
        }
    }

    /** Creates a deterministic JAR and immediately verifies every archive entry against the accepted routes. */
    public static Summary packageAndInspect(
            Path acceptedRoot, Path archive, List<String> arguments)
            throws IOException {
        Request request = Request.parse(arguments);
        Path root = safeRoot(acceptedRoot);
        List<RouteBytes> routes = loadRoutes(root, request.routes());
        requireExactRouteSurface(root, routes);
        writeArchive(archive, routes);
        Summary summary = inspectPackage(root, archive, request, routes);
        requireExactRouteSurface(root, routes);
        for (RouteBytes route : routes) {
            route.requireSourceUnchanged();
        }
        return summary;
    }

    /** Reopens an existing package and verifies that it contains only the exact authenticated route resources. */
    public static Summary inspectPackage(
            Path acceptedRoot, Path archive, List<String> arguments)
            throws IOException {
        Request request = Request.parse(arguments);
        Path root = safeRoot(acceptedRoot);
        List<RouteBytes> routes = loadRoutes(root, request.routes());
        requireExactRouteSurface(root, routes);
        return inspectPackage(root, archive, request, routes);
    }

    /** Parses and validates the machine-readable stdout retained by controller command evidence. */
    public static Summary verifySummary(
            byte[] encoded, Path acceptedRoot, List<String> arguments)
            throws IOException {
        Request request = Request.parse(arguments);
        Path root = safeRoot(acceptedRoot);
        List<RouteBytes> routes = loadRoutes(root, request.routes());
        requireExactRouteSurface(root, routes);
        Summary summary = Summary.parse(encoded);
        summary.requireRequest(request, routes);
        return summary;
    }

    private static void writeArchive(Path requestedArchive, List<RouteBytes> routes) throws IOException {
        Path archive = requestedArchive.toAbsolutePath().normalize();
        Path parent = archive.getParent();
        if (parent == null || Files.isSymbolicLink(parent)
                || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(archive, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(archive)) {
            throw new IOException("Deterministic Main package output is absent or unsafe");
        }
        Path realParent = parent.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!archive.getParent().equals(realParent)) {
            throw new IOException("Deterministic Main package output parent contains a symbolic-link alias");
        }
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(
                        archive, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))) {
            zip.setLevel(0);
            for (RouteBytes route : routes) {
                CRC32 crc = new CRC32();
                crc.update(route.bytes());
                ZipEntry entry = new ZipEntry(route.path());
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(route.bytes().length);
                entry.setCompressedSize(route.bytes().length);
                entry.setCrc(crc.getValue());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(route.bytes());
                zip.closeEntry();
            }
        }
        if (Files.isSymbolicLink(archive) || !Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)
                || Files.size(archive) <= 0 || Files.size(archive) > MAX_PACKAGE_BYTES) {
            throw new IOException("Deterministic Main package output has an unsafe type or size");
        }
    }

    private static Summary inspectPackage(
            Path root, Path requestedArchive, Request request, List<RouteBytes> routes)
            throws IOException {
        Path archive = requestedArchive.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(archive) || !Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)
                || Files.size(archive) <= 0 || Files.size(archive) > MAX_PACKAGE_BYTES) {
            throw new IOException("Deterministic Main package is absent, symbolic, or outside its byte limit");
        }
        archive = archive.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Map<String, RouteBytes> expected = new LinkedHashMap<>();
        routes.forEach(route -> expected.put(route.path(), route));
        List<EntrySummary> entries = new ArrayList<>();
        Set<String> observed = new HashSet<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            if (zip.getComment() != null) {
                throw new IOException("Deterministic Main package cannot contain an archive comment");
            }
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                String name = safeRoutePath(entry.getName());
                RouteBytes route = expected.get(name);
                CRC32 expectedCrc = new CRC32();
                expectedCrc.update(route == null ? new byte[0] : route.bytes());
                if (entry.isDirectory() || !observed.add(name) || route == null
                        || entry.getMethod() != ZipEntry.STORED || entry.getTime() != 0
                        || entry.getComment() != null || entry.getCrc() != expectedCrc.getValue()
                        || entry.getSize() != route.bytes().length
                        || entry.getCompressedSize() != route.bytes().length) {
                    throw new IOException("Deterministic Main package contains an unsafe entry: " + name);
                }
                byte[] bytes = read(zip, entry, MAX_ROUTE_BYTES);
                if (!MessageDigest.isEqual(route.bytes(), bytes)
                        || !route.digest().equals(digest(bytes))) {
                    throw new IOException("Deterministic Main package entry differs from its accepted route: " + name);
                }
                entries.add(new EntrySummary(name, bytes.length, route.digest()));
            }
        }
        List<String> expectedOrder = routes.stream().map(RouteBytes::path).toList();
        if (!observed.equals(expected.keySet())
                || !entries.stream().map(EntrySummary::path).toList().equals(expectedOrder)) {
            throw new IOException("Deterministic Main package entries differ from the accepted route manifest");
        }
        long size = Files.size(archive);
        String packageDigest = digest(archive);
        Summary summary = new Summary(
                SCHEMA_VERSION,
                request.candidateDigest(), request.manifestDigest(), request.catalogUsageDigest(),
                request.pomDigest(), request.mainPayloadDigest(), packageDigest, size, entries);
        summary.requireRequest(request, routes);
        return summary;
    }

    private static List<RouteBytes> loadRoutes(Path root, List<RouteInput> inputs) throws IOException {
        if (inputs.isEmpty() || inputs.size() > MAX_ROUTES) {
            throw new IOException("Deterministic Main packaging requires between 1 and " + MAX_ROUTES + " routes");
        }
        List<RouteBytes> routes = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        for (RouteInput input : inputs.stream().sorted(Comparator.comparing(RouteInput::path)).toList()) {
            String relative = safeRoutePath(input.path());
            if (!paths.add(relative)) {
                throw new IOException("A deterministic Main package route was supplied more than once: " + relative);
            }
            Path path = root.resolve(relative).normalize();
            requireContainedPath(root, path, relative);
            BasicFileAttributes before = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!before.isRegularFile() || before.isSymbolicLink()
                    || before.size() <= 0 || before.size() > MAX_ROUTE_BYTES) {
                throw new IOException("Accepted package route has an unsafe type or size: " + relative);
            }
            byte[] bytes;
            try (InputStream inputStream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                bytes = inputStream.readNBytes(MAX_ROUTE_BYTES + 1);
            }
            if (bytes.length != before.size() || bytes.length > MAX_ROUTE_BYTES
                    || !input.digest().equals(digest(bytes))) {
                throw new IOException("Accepted package route differs from its authenticated digest: " + relative);
            }
            BasicFileAttributes after = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!sameFile(before, after)) {
                throw new IOException("Accepted package route changed while it was read: " + relative);
            }
            routes.add(new RouteBytes(relative, input.digest(), path, before.fileKey(), bytes));
        }
        return List.copyOf(routes);
    }

    private static void requireExactRouteSurface(Path root, List<RouteBytes> routes) throws IOException {
        Set<String> expected = routes.stream().map(RouteBytes::path).collect(java.util.stream.Collectors.toSet());
        Set<String> observed = new TreeSet<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                if (path.equals(root)) {
                    continue;
                }
                String relative = root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Accepted package surface contains a symbolic link: " + relative);
                }
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Accepted package surface contains a non-regular entry: " + relative);
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && relative.endsWith(".camel.yaml")) {
                    observed.add(safeRoutePath(relative));
                }
            }
        }
        if (!observed.equals(expected)) {
            throw new IOException("Accepted package route surface differs from the authenticated manifest");
        }
    }

    private static void requireContainedPath(Path root, Path path, String label) throws IOException {
        if (!path.startsWith(root)) {
            throw new IOException("Accepted package route escaped its root: " + label);
        }
        Path current = root;
        for (Path segment : root.relativize(path)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Accepted package route contains a symbolic-link component: " + label);
            }
        }
        if (!path.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(root)) {
            throw new IOException("Accepted package route escaped its real root: " + label);
        }
    }

    private static Path safeRoot(Path requested) throws IOException {
        if (requested == null || Files.isSymbolicLink(requested)
                || !Files.isDirectory(requested, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Deterministic Main package requires a safe accepted root");
        }
        return requested.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static String safeRoutePath(String value) throws IOException {
        if (value == null || value.isBlank() || value.length() > MAX_ROUTE_PATH_CHARACTERS
                || value.startsWith("/")
                || value.endsWith("/") || value.indexOf('\\') >= 0 || value.indexOf('\0') >= 0
                || !value.endsWith(".camel.yaml")) {
            throw new IOException("Deterministic Main package route path is unsafe");
        }
        Path path;
        try {
            path = Path.of(value);
        } catch (RuntimeException e) {
            throw new IOException("Deterministic Main package route path is invalid", e);
        }
        if (path.isAbsolute() || !path.equals(path.normalize()) || path.getNameCount() == 0) {
            throw new IOException("Deterministic Main package route path is non-canonical");
        }
        for (Path segment : path) {
            String name = segment.toString();
            if (name.isEmpty() || ".".equals(name) || "..".equals(name)
                    || name.getBytes(StandardCharsets.UTF_8).length > 255) {
                throw new IOException("Deterministic Main package route path contains an unsafe segment");
            }
        }
        String canonical = path.toString().replace('\\', '/');
        if (!canonical.equals(value)) {
            throw new IOException("Deterministic Main package route path is not canonical POSIX form");
        }
        return canonical;
    }

    private static byte[] read(ZipFile zip, ZipEntry entry, int maximum) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.toIntExact(entry.getSize()));
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total = Math.addExact(total, count);
                if (total > maximum) {
                    throw new IOException("Deterministic Main package entry exceeds its byte limit");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static boolean sameFile(BasicFileAttributes before, BasicFileAttributes after) {
        return before.isRegularFile() == after.isRegularFile()
                && before.size() == after.size()
                && java.util.Objects.equals(before.fileKey(), after.fileKey())
                && before.lastModifiedTime().equals(after.lastModifiedTime());
    }

    private static void deletePackage(Path directory, Path archive) throws IOException {
        IOException failure = null;
        for (Path path : List.of(archive, directory)) {
            try {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Private deterministic package output became symbolic");
                }
                Files.deleteIfExists(path);
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static String digest(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static String digest(byte[] bytes) {
        return "sha256:" + HexFormat.of().formatHex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void requireDigest(String value, String label) throws IOException {
        if (value == null || !DIGEST.matcher(value).matches()) {
            throw new IOException("Deterministic Main package has an invalid " + label);
        }
    }

    private record Request(
            String candidateDigest,
            String manifestDigest,
            String catalogUsageDigest,
            String pomDigest,
            String mainPayloadDigest,
            List<RouteInput> routes) {

        private static Request parse(List<String> arguments) throws IOException {
            if (arguments == null) {
                throw new IOException("Deterministic Main package arguments are missing");
            }
            Map<String, String> bindings = new LinkedHashMap<>();
            List<RouteInput> routes = new ArrayList<>();
            String pendingRoute = null;
            for (String argument : arguments) {
                if (argument == null) {
                    throw new IOException("Deterministic Main package argument is null");
                }
                if (argument.startsWith("--route=")) {
                    if (pendingRoute != null) {
                        throw new IOException("Deterministic Main package route lacks its digest");
                    }
                    pendingRoute = safeRoutePath(argument.substring("--route=".length()));
                } else if (argument.startsWith("--route-digest=")) {
                    if (pendingRoute == null) {
                        throw new IOException("Deterministic Main package route digest lacks its route");
                    }
                    String digest = argument.substring("--route-digest=".length());
                    requireDigest(digest, "route digest");
                    routes.add(new RouteInput(pendingRoute, digest));
                    pendingRoute = null;
                } else if (argument.startsWith("--") && argument.indexOf('=') > 2) {
                    int separator = argument.indexOf('=');
                    String key = argument.substring(2, separator);
                    String value = argument.substring(separator + 1);
                    if (!Set.of(
                            "candidate-digest", "manifest-digest", "catalog-usage-digest",
                            "pom-digest", "main-payload-digest").contains(key)
                            || bindings.putIfAbsent(key, value) != null) {
                        throw new IOException("Unknown or duplicate deterministic Main package binding: " + key);
                    }
                    requireDigest(value, key);
                } else {
                    throw new IOException("Unknown deterministic Main package argument");
                }
            }
            if (pendingRoute != null || routes.isEmpty() || routes.size() > MAX_ROUTES
                    || bindings.size() != 5) {
                throw new IOException("Deterministic Main package arguments are incomplete or excessive");
            }
            return new Request(
                    bindings.get("candidate-digest"), bindings.get("manifest-digest"),
                    bindings.get("catalog-usage-digest"), bindings.get("pom-digest"),
                    bindings.get("main-payload-digest"), List.copyOf(routes));
        }
    }

    private record RouteInput(String path, String digest) {
    }

    private record RouteBytes(
            String path, String digest, Path source, Object fileKey, byte[] bytes) {

        private RouteBytes {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        private void requireSourceUnchanged() throws IOException {
            if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                    || !java.util.Objects.equals(
                            fileKey,
                            Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                                    .fileKey())
                    || !digest.equals(ShipMainPackageMain.digest(source))) {
                throw new IOException("Accepted package route changed during packaging: " + path);
            }
        }
    }

    public record EntrySummary(String path, long size, String digest) {

        public EntrySummary {
            if (size <= 0 || size > MAX_ROUTE_BYTES) {
                throw new IllegalArgumentException("Package summary entry has an unsafe size");
            }
            try {
                path = safeRoutePath(path);
                requireDigest(digest, "summary entry digest");
            } catch (IOException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
    }

    /** Exact package output binding retained as the command's content-addressed stdout. */
    public record Summary(
            int schemaVersion,
            String candidateDigest,
            String manifestDigest,
            String catalogUsageDigest,
            String pomDigest,
            String mainPayloadDigest,
            String packageDigest,
            long packageSize,
            List<EntrySummary> entries) {

        public Summary {
            entries = entries == null ? List.of() : List.copyOf(entries);
            if (schemaVersion != SCHEMA_VERSION || entries.isEmpty() || entries.size() > MAX_ROUTES
                    || packageSize <= 0 || packageSize > MAX_PACKAGE_BYTES) {
                throw new IllegalArgumentException("Deterministic Main package summary is outside its bounds");
            }
            try {
                requireDigest(candidateDigest, "candidate digest");
                requireDigest(manifestDigest, "manifest digest");
                requireDigest(catalogUsageDigest, "catalog usage digest");
                requireDigest(pomDigest, "POM digest");
                requireDigest(mainPayloadDigest, "Main payload digest");
                requireDigest(packageDigest, "package digest");
            } catch (IOException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            if (entries.stream().map(EntrySummary::path).distinct().count() != entries.size()
                    || !entries.equals(entries.stream().sorted(Comparator.comparing(EntrySummary::path)).toList())) {
                throw new IllegalArgumentException("Deterministic Main package entries are duplicated or unsorted");
            }
        }

        public byte[] encode() {
            StringBuilder value = new StringBuilder(2048);
            field(value, "schemaVersion", Integer.toString(schemaVersion));
            field(value, "candidateDigest", candidateDigest);
            field(value, "manifestDigest", manifestDigest);
            field(value, "catalogUsageDigest", catalogUsageDigest);
            field(value, "pomDigest", pomDigest);
            field(value, "mainPayloadDigest", mainPayloadDigest);
            field(value, "packageDigest", packageDigest);
            field(value, "packageSize", Long.toString(packageSize));
            field(value, "entry.count", Integer.toString(entries.size()));
            for (int index = 0; index < entries.size(); index++) {
                EntrySummary entry = entries.get(index);
                String prefix = "entry." + String.format(java.util.Locale.ROOT, "%03d", index) + '.';
                field(value, prefix + "pathBase64", Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(entry.path().getBytes(StandardCharsets.UTF_8)));
                field(value, prefix + "size", Long.toString(entry.size()));
                field(value, prefix + "digest", entry.digest());
            }
            byte[] encoded = value.toString().getBytes(StandardCharsets.UTF_8);
            if (encoded.length > MAX_SUMMARY_BYTES) {
                throw new IllegalStateException("Deterministic Main package summary exceeds its byte limit");
            }
            return encoded;
        }

        public static Summary parse(byte[] encoded) throws IOException {
            if (encoded == null || encoded.length == 0 || encoded.length > MAX_SUMMARY_BYTES) {
                throw new IOException("Deterministic Main package summary has an unsafe size");
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (String line : new String(encoded, StandardCharsets.UTF_8).split("\\n", -1)) {
                if (line.isEmpty()) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator <= 0 || separator == line.length() - 1
                        || values.putIfAbsent(
                                line.substring(0, separator), line.substring(separator + 1))
                           != null) {
                    throw new IOException("Deterministic Main package summary is malformed or duplicated");
                }
                if (values.size() > MAX_SUMMARY_FIELDS) {
                    throw new IOException("Deterministic Main package summary contains too many fields");
                }
            }
            try {
                int schema = Integer.parseInt(required(values, "schemaVersion"));
                String candidate = required(values, "candidateDigest");
                String manifest = required(values, "manifestDigest");
                String usage = required(values, "catalogUsageDigest");
                String pom = required(values, "pomDigest");
                String payload = required(values, "mainPayloadDigest");
                String packageDigest = required(values, "packageDigest");
                long packageSize = Long.parseLong(required(values, "packageSize"));
                int count = Integer.parseInt(required(values, "entry.count"));
                if (count <= 0 || count > MAX_ROUTES) {
                    throw new IOException("Deterministic Main package summary entry count is outside its bounds");
                }
                List<EntrySummary> entries = new ArrayList<>();
                for (int index = 0; index < count; index++) {
                    String prefix = "entry." + String.format(java.util.Locale.ROOT, "%03d", index) + '.';
                    entries.add(new EntrySummary(
                            decodePath(required(values, prefix + "pathBase64")),
                            Long.parseLong(required(values, prefix + "size")),
                            required(values, prefix + "digest")));
                }
                if (!values.isEmpty()) {
                    throw new IOException(
                            "Deterministic Main package summary contains unknown fields: "
                                          + values.keySet());
                }
                return new Summary(
                        schema, candidate, manifest, usage, pom, payload,
                        packageDigest, packageSize, entries);
            } catch (IOException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new IOException("Deterministic Main package summary contains an invalid value", e);
            }
        }

        private void requireRequest(Request request, List<RouteBytes> routes) throws IOException {
            List<EntrySummary> expected = routes.stream()
                    .map(route -> new EntrySummary(route.path(), route.bytes().length, route.digest()))
                    .toList();
            if (!candidateDigest.equals(request.candidateDigest())
                    || !manifestDigest.equals(request.manifestDigest())
                    || !catalogUsageDigest.equals(request.catalogUsageDigest())
                    || !pomDigest.equals(request.pomDigest())
                    || !mainPayloadDigest.equals(request.mainPayloadDigest())
                    || !entries.equals(expected)) {
                throw new IOException("Deterministic Main package summary is not bound to the accepted inputs");
            }
        }

        private static void field(StringBuilder target, String key, String value) {
            target.append(key).append('=').append(value).append('\n');
        }

        private static String required(Map<String, String> values, String key) throws IOException {
            String value = values.remove(key);
            if (value == null || value.isBlank()) {
                throw new IOException("Deterministic Main package summary is missing " + key);
            }
            return value;
        }

        private static String decodePath(String encoded) throws IOException {
            try {
                byte[] bytes = Base64.getUrlDecoder().decode(encoded);
                String path = new String(bytes, StandardCharsets.UTF_8);
                String canonical = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(path.getBytes(StandardCharsets.UTF_8));
                if (!canonical.equals(encoded)) {
                    throw new IOException("Deterministic Main package summary path encoding is non-canonical");
                }
                return path;
            } catch (IllegalArgumentException e) {
                throw new IOException("Deterministic Main package summary path encoding is invalid", e);
            }
        }
    }
}

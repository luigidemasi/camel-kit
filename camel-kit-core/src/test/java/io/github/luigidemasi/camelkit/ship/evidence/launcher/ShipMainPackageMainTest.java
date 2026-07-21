package io.github.luigidemasi.camelkit.ship.evidence.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ShipMainPackageMainTest {

    @TempDir
    Path directory;

    @Test
    void repeatedPackagingProducesIdenticalBytesAndExactRouteEntries() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path nested = Files.createDirectories(project.resolve("route bundles"));
        Path beta = Files.writeString(project.resolve("beta.camel.yaml"), "- route: {id: beta}\n");
        Path alpha = Files.writeString(nested.resolve("alpha.camel.yaml"), "- route: {id: alpha}\n");
        List<String> arguments = arguments(
                new Route("route bundles/alpha.camel.yaml", digest(alpha)),
                new Route("beta.camel.yaml", digest(beta)));
        Path first = directory.resolve("first.jar");
        Path second = directory.resolve("second.jar");

        ShipMainPackageMain.Summary firstSummary
                = ShipMainPackageMain.packageAndInspect(project, first, arguments);
        ShipMainPackageMain.Summary secondSummary
                = ShipMainPackageMain.packageAndInspect(project, second, arguments);

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        assertEquals(firstSummary, secondSummary);
        assertEquals(digest(first), firstSummary.packageDigest());
        assertEquals(List.of("beta.camel.yaml", "route bundles/alpha.camel.yaml"), entryNames(first));
        assertEquals(firstSummary, ShipMainPackageMain.Summary.parse(firstSummary.encode()));
        assertEquals(firstSummary, ShipMainPackageMain.verifySummary(firstSummary.encode(), project, arguments));
        assertTrue(
                ShipMainPackageMain.MAX_PACKAGE_BYTES
                   > (long) ShipMainPackageMain.MAX_ROUTES
                     * (io.github.luigidemasi.camelkit.ship.ShipArtifactLimits.MAX_ROUTE_YAML_BYTES
                        + 2L * ShipMainPackageMain.MAX_ROUTE_PATH_CHARACTERS * 4),
                "the archive cap must cover route bytes and worst-case duplicated ZIP path names");
        assertTrue(
                ShipMainPackageMain.MAX_SUMMARY_BYTES
                   > (long) ShipMainPackageMain.MAX_ROUTES
                     * ((ShipMainPackageMain.MAX_ROUTE_PATH_CHARACTERS * 4L + 2) / 3 * 4),
                "the summary cap must cover every worst-case base64-encoded route path");
    }

    @Test
    void rejectsMissingDuplicateTraversalSymlinkMutationAndExtraRoutes() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path route = Files.writeString(project.resolve("orders.camel.yaml"), "- route: {id: orders}\n");
        Route accepted = new Route("orders.camel.yaml", digest(route));

        assertThrows(IOException.class, () -> ShipMainPackageMain.packageAndInspect(
                project, directory.resolve("zero.jar"), bindings()));

        List<String> duplicate = arguments(accepted, accepted);
        assertThrows(IOException.class, () -> ShipMainPackageMain.packageAndInspect(
                project, directory.resolve("duplicate.jar"), duplicate));

        List<String> traversal = new ArrayList<>(bindings());
        traversal.add("--route=../orders.camel.yaml");
        traversal.add("--route-digest=" + accepted.digest());
        assertThrows(IOException.class, () -> ShipMainPackageMain.packageAndInspect(
                project, directory.resolve("traversal.jar"), traversal));

        List<String> mutated = arguments(new Route(accepted.path(), sha(99)));
        assertThrows(IOException.class, () -> ShipMainPackageMain.packageAndInspect(
                project, directory.resolve("mutated.jar"), mutated));

        Path linked = project.resolve("linked.camel.yaml");
        Files.createSymbolicLink(linked, route.getFileName());
        assertThrows(IOException.class, () -> ShipMainPackageMain.packageAndInspect(
                project, directory.resolve("symlink.jar"), arguments(accepted)));
        Files.delete(linked);

        Files.writeString(project.resolve("extra.camel.yaml"), "- route: {id: extra}\n");
        assertThrows(IOException.class, () -> ShipMainPackageMain.packageAndInspect(
                project, directory.resolve("extra.jar"), arguments(accepted)));
    }

    @Test
    void rejectsPreexistingAndTamperedPackageOutputsAndSummaries() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path route = Files.writeString(project.resolve("orders.camel.yaml"), "- route: {id: orders}\n");
        List<String> arguments = arguments(new Route("orders.camel.yaml", digest(route)));
        Path archive = Files.writeString(directory.resolve("occupied.jar"), "occupied");

        assertThrows(IOException.class,
                () -> ShipMainPackageMain.packageAndInspect(project, archive, arguments));

        Files.delete(archive);
        ShipMainPackageMain.Summary summary
                = ShipMainPackageMain.packageAndInspect(project, archive, arguments);
        rewrite(archive, "../escaped.camel.yaml", Files.readAllBytes(route));
        assertThrows(IOException.class,
                () -> ShipMainPackageMain.inspectPackage(project, archive, arguments));

        byte[] unknownField = (new String(summary.encode(), java.nio.charset.StandardCharsets.UTF_8)
                               + "unexpected=value\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> ShipMainPackageMain.verifySummary(
                unknownField, project, arguments));
        byte[] wrongBinding = new String(summary.encode(), java.nio.charset.StandardCharsets.UTF_8)
                .replace(sha(1), sha(90))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> ShipMainPackageMain.verifySummary(
                wrongBinding, project, arguments));

        assertThrows(IOException.class, () -> ShipMainPackageMain.verifySummary(
                new byte[ShipMainPackageMain.MAX_SUMMARY_BYTES + 1], project, arguments));
    }

    private static List<String> bindings() {
        return new ArrayList<>(
                List.of(
                        "--candidate-digest=" + sha(1),
                        "--manifest-digest=" + sha(2),
                        "--catalog-usage-digest=" + sha(3),
                        "--pom-digest=" + sha(4),
                        "--main-payload-digest=" + sha(5)));
    }

    private static List<String> arguments(Route... routes) {
        List<String> arguments = bindings();
        for (Route route : routes) {
            arguments.add("--route=" + route.path());
            arguments.add("--route-digest=" + route.digest());
        }
        return arguments;
    }

    private static List<String> entryNames(Path archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<String> result = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                result.add(entries.nextElement().getName());
            }
            return result;
        }
    }

    private static void rewrite(Path archive, String name, byte[] bytes) throws IOException {
        Files.delete(archive);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            ZipEntry entry = new ZipEntry(name);
            entry.setTime(0);
            zip.putNextEntry(entry);
            zip.write(bytes);
            zip.closeEntry();
        }
    }

    private static String digest(Path path) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static String sha(int seed) {
        return "sha256:" + String.format(Locale.ROOT, "%064x", seed);
    }

    private record Route(String path, String digest) {
    }
}

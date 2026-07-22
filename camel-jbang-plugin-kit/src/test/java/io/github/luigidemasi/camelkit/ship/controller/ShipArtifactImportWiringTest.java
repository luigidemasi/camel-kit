package io.github.luigidemasi.camelkit.ship.controller;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.protocol.StageResultValidator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Full-reactor accidental-wiring tripwire while staged import has no production broker.
 *
 * <p>
 * This is not a security boundary. Production import still requires proof that the full worker process tree is dead
 * with protected exclusive ancestry, or a killable native nonblocking no-follow open.
 */
class ShipArtifactImportWiringTest {

    private static final List<String> REACTOR_MODULES = List.of(
            "camel-kit-main",
            "camel-kit-core",
            "camel-kit-graph",
            "camel-kit-ship-resolver",
            "camel-kit-ship-controller",
            "camel-jbang-plugin-kit");
    private static final String WORKSPACE_SERVICE
            = "io.github.luigidemasi.camelkit.ship.controller.ShipWorkspaceService";
    private static final String WORKSPACE_ACCEPT = WORKSPACE_SERVICE.replace('.', '/') + ".accept:";
    private static final String RESULT_PREFLIGHT
            = "io/github/luigidemasi/camelkit/ship/protocol/StageResultValidator.validatePreflight:";
    private static final String BLOB_IMPORT
            = "io/github/luigidemasi/camelkit/ship/controller/ShipBlobStore.importArtifacts:";
    private static final Set<String> GUARDED_METHODS = Set.of(WORKSPACE_ACCEPT, RESULT_PREFLIGHT, BLOB_IMPORT);
    private static final Pattern METHOD_REFERENCE = Pattern.compile(
            "^\\s+#[0-9]+ = (?:Interface)?Methodref\\s+#[^\\r\\n]*//\\s+(\\S+)$", Pattern.MULTILINE);

    @Test
    void stagedArtifactImportRemainsUnwiredAcrossTheCompiledReactor() throws Exception {
        Path testClasses = Path.of(ShipArtifactImportWiringTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path reactor = testClasses.getParent().getParent().getParent();
        List<Path> productionRoots = productionRoots(reactor);
        assertEquals(
                REACTOR_MODULES.size(),
                productionRoots.size(),
                "The accidental-wiring tripwire must run after every reactor module is compiled");

        String classpath = String.join(
                File.pathSeparator,
                productionRoots.stream().map(Path::toString).toList());
        Map<String, Set<String>> callers = new HashMap<>();
        for (Path root : productionRoots) {
            collectCallers(callers, root, classpath, classFiles(root));
        }

        assertEquals(
                Set.of(),
                callers.getOrDefault(WORKSPACE_ACCEPT, Set.of()),
                "Accidental-wiring tripwire: ShipWorkspaceService.accept must remain production-dead");
        assertEquals(
                Set.of(),
                callers.getOrDefault(RESULT_PREFLIGHT, Set.of()),
                "Accidental-wiring tripwire: StageResultValidator.validatePreflight must remain production-dead");
        assertEquals(
                Set.of(WORKSPACE_SERVICE),
                callers.getOrDefault(BLOB_IMPORT, Set.of()),
                "Accidental-wiring tripwire: ShipBlobStore.importArtifacts is reserved for ShipWorkspaceService");
    }

    @Test
    void compiledReferenceScannerRecognizesEveryGuardedMethod() throws Exception {
        Path testClasses = Path.of(ShipArtifactImportWiringTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path productionClasses = Path.of(ShipWorkspaceService.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path canary = testClasses.resolve(JavapCanary.class.getName().replace('.', '/') + ".class");
        String classpath = testClasses + File.pathSeparator + productionClasses;
        Map<String, Set<String>> callers = new HashMap<>();

        collectCallers(callers, testClasses, classpath, List.of(canary));

        for (String guardedMethod : GUARDED_METHODS) {
            assertEquals(
                    Set.of(JavapCanary.class.getName()),
                    callers.getOrDefault(guardedMethod, Set.of()),
                    "javap no longer exposes guarded method references for " + guardedMethod);
        }
    }

    private static List<Path> productionRoots(Path reactor) {
        return REACTOR_MODULES.stream()
                .map(module -> reactor.resolve(module).resolve("target/classes"))
                .filter(Files::isDirectory)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .toList();
    }

    private static List<Path> classFiles(Path root) throws IOException {
        try (var files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".class")).toList();
        }
    }

    private static void collectCallers(
            Map<String, Set<String>> callers,
            Path root,
            String classpath,
            List<Path> classFiles) {
        ToolProvider javap = ToolProvider.findFirst("javap").orElseThrow(
                () -> new AssertionError("Ship artifact import tripwire requires a JDK with javap"));
        for (Path classFile : classFiles) {
            String className = className(root, classFile);
            Matcher references = METHOD_REFERENCE.matcher(javap(javap, classpath, className));
            while (references.find()) {
                String reference = references.group(1);
                for (String guardedMethod : GUARDED_METHODS) {
                    if (reference.startsWith(guardedMethod)) {
                        callers.computeIfAbsent(guardedMethod, ignored -> new HashSet<>()).add(className);
                    }
                }
            }
        }
    }

    private static String javap(ToolProvider tool, String classpath, String className) {
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();
        int result = tool.run(
                new PrintWriter(output),
                new PrintWriter(errors),
                "-classpath", classpath,
                "-v",
                "-p",
                className);
        assertEquals(0, result, () -> errors + System.lineSeparator() + output);
        return output.toString();
    }

    private static String className(Path root, Path classFile) {
        String relative = root.relativize(classFile).toString();
        return relative.substring(0, relative.length() - ".class".length())
                .replace(classFile.getFileSystem().getSeparator(), ".");
    }

    private static final class JavapCanary {

        private JavapCanary() {
        }

        private static void guardedCalls(
                StageRequest request, StageResult result, Path output, ShipBlobStore blobs)
                throws IOException {
            ShipWorkspaceService.accept(request, result, output, blobs);
            StageResultValidator.validatePreflight(request, result, output);
            blobs.importArtifacts(output, result.artifacts());
        }
    }
}

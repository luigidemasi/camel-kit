package io.github.luigidemasi.camelkit.ship.evidence.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.stub.StubComponent;
import org.apache.camel.main.Main;

import io.github.luigidemasi.camelkit.ship.ShipArtifactLimits;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogExpressionInventory;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.CollectionStartEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.NodeEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;

/** Starts only the accepted YAML routes with every non-direct endpoint replaced by Camel Stub. */
public final class ShipCamelMainBootstrap {

    static final String TEST_ENTRY_PREFIX = "direct:camel-kit-ship-test-";
    private static final Pattern URI_SCHEME = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_.-]+)}}");
    private static final Pattern APPLICATION_CONFIGURATION = Pattern.compile(
            "application[^/]*\\.(?:properties|ya?ml)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ENDPOINT_KEYS = Set.of(
            "from", "to", "tod", "enrich", "pollenrich", "wiretap", "uri", "afteruri");
    private static final Set<String> ENDPOINT_SELECTOR_KEYS = Set.of(
            "interceptfrom", "interceptsendtoendpoint");
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "bean", "beans", "script", "scripts", "groovy", "javascript", "js", "python", "kotlin",
            "routepolicy", "routeconfiguration", "routeconfigurations", "templatedroute", "routetemplate",
            "rest", "rests", "kamelet", "process", "exec");
    private static final List<String> FORBIDDEN_VALUES = List.of(
            "${bean:", "${type:", "${ref:", "#bean:", "#class:", "java.lang.runtime",
            "processbuilder", "script:", "$simple{", ".class", "::", "runtime.exec");
    private static final Set<String> ALTERNATE_BUILD_OR_LAUNCH_FILES = Set.of(
            "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "gradlew", "gradlew.bat", "build.xml", "jbang-catalog.json", "dockerfile",
            "docker-compose.yaml", "docker-compose.yml", "compose.yaml", "compose.yml");
    private static final Set<String> MAVEN_MODEL_HOOKS = Set.of(
            ".mvn/extensions.xml", ".mvn/maven.config", ".mvn/jvm.config");
    private static final Set<String> CAMEL_CONFIGURATION_FILES = Set.of(
            "camel.properties", "camel.yaml", "camel.yml",
            "camel-main.properties", "camel-main.yaml", "camel-main.yml");
    private static final Set<String> SCRIPT_SUFFIXES = Set.of(
            ".sh", ".bash", ".zsh", ".ksh", ".jsh", ".py", ".rb", ".groovy", ".js");
    private static final ObjectMapper YAML = new ObjectMapper(
            YAMLFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(100)
                            .maxStringLength(ShipArtifactLimits.MAX_ROUTE_YAML_BYTES)
                            .build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build());

    private ShipCamelMainBootstrap() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1 && "--payload-version".equals(arguments[0])) {
            System.out.println("Camel direct Main bootstrap " + camelVersion());
            return;
        }
        TreeSet<String> routes = new TreeSet<>();
        TreeSet<String> routeIds = new TreeSet<>();
        for (String argument : arguments) {
            if (argument.startsWith("--route=")) {
                if (!routes.add(argument.substring("--route=".length()))) {
                    throw new IOException("A route path was supplied more than once");
                }
            } else if (argument.startsWith("--expected-route=")) {
                String id = argument.substring("--expected-route=".length());
                if (id.isBlank() || !routeIds.add(id)) {
                    throw new IOException("An expected route ID is blank or duplicated");
                }
            } else {
                throw new IOException("Unknown direct Main bootstrap argument");
            }
        }
        start(Path.of("/workspace"), routes.stream().map(Path::of).toList(), routeIds);
    }

    public static void start(Path acceptedRoot, List<Path> requestedRoutes, Set<String> expectedRouteIds)
            throws Exception {
        withStarted(acceptedRoot, requestedRoutes, expectedRouteIds, context -> {
            // Reaching the callback proves that every accepted route is started.
        });
    }

    public static void withStarted(
            Path acceptedRoot,
            List<Path> requestedRoutes,
            Set<String> expectedRouteIds,
            StartedAction action)
            throws Exception {
        withStarted(acceptedRoot, requestedRoutes, expectedRouteIds, null, action);
    }

    /** Starts one accepted route after replacing its consumer with a reserved in-memory test entry. */
    static void withTestEntryPoint(
            Path acceptedRoot,
            List<Path> requestedRoutes,
            Set<String> expectedRouteIds,
            String testRouteId,
            StartedAction action)
            throws Exception {
        if (requestedRoutes == null || requestedRoutes.size() != 1
                || expectedRouteIds == null || expectedRouteIds.size() != 1
                || !expectedRouteIds.contains(testRouteId)) {
            throw new IOException("Direct Citrus requires exactly one accepted target route");
        }
        withStarted(acceptedRoot, requestedRoutes, expectedRouteIds, testRouteId, action);
    }

    private static void withStarted(
            Path acceptedRoot,
            List<Path> requestedRoutes,
            Set<String> expectedRouteIds,
            String testRouteId,
            StartedAction action)
            throws Exception {
        Path root = acceptedRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || requestedRoutes == null || requestedRoutes.isEmpty()
                || expectedRouteIds == null || expectedRouteIds.isEmpty()) {
            throw new IOException("Direct Main startup requires accepted routes and exact route IDs");
        }

        TreeSet<Path> routes = new TreeSet<>();
        TreeSet<String> schemes = new TreeSet<>();
        for (Path requested : requestedRoutes) {
            Path route = requested.toAbsolutePath().normalize();
            if (!route.startsWith(root) || Files.isSymbolicLink(route)
                    || !Files.isRegularFile(route, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(route) <= 0
                    || Files.size(route) > ShipArtifactLimits.MAX_ROUTE_YAML_BYTES) {
                throw new IOException("Route is outside the accepted snapshot or unsafe: " + requested);
            }
            route = route.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!route.startsWith(root) || !routes.add(route)) {
                throw new IOException("Route escaped or was supplied more than once: " + requested);
            }
            inspect(route, schemes);
        }
        validateLaunchSurface(root, List.copyOf(routes));

        Main main = new Main();
        main.setDefaultPropertyPlaceholderLocation("false");
        main.configure().setAutoConfigurationEnabled(false);
        main.configure().setAutoConfigurationEnvironmentVariablesEnabled(false);
        main.configure().setAutoConfigurationSystemPropertiesEnabled(false);
        main.configure().setBasePackageScanEnabled(false);
        main.configure().setModeline(false);
        main.configure().setRoutesReloadEnabled(false);
        main.configure().setJmxEnabled(false);
        main.configure().setLoadHealthChecks(false);
        main.configure().setDevConsoleEnabled(false);
        main.configure().setAutoStartup(testRouteId == null);
        main.configure().setRoutesIncludePattern(routes.stream()
                .map(path -> path.toUri().toString())
                .collect(Collectors.joining(",")));
        Properties properties = new Properties();
        main.setInitialProperties(properties);
        for (String scheme : schemes) {
            if (!"direct".equals(scheme)) {
                main.bind(scheme, new StubComponent());
            }
        }

        try {
            main.start();
            CamelContext context = main.getCamelContext();
            Set<String> actualIds = Set.copyOf(context.getRouteIds());
            if (!actualIds.equals(Set.copyOf(expectedRouteIds))) {
                throw new IOException("Started route IDs differ from the approved manifest: " + actualIds);
            }
            if (testRouteId != null) {
                AdviceWith.adviceWith(
                        context, testRouteId,
                        route -> route.replaceFromWith(testEntryUri(testRouteId)));
                context.getRouteController().startRoute(testRouteId);
            }
            for (String routeId : expectedRouteIds) {
                ServiceStatus status = context.getRouteController().getRouteStatus(routeId);
                if (status == null || !status.isStarted()) {
                    throw new IOException("Approved route did not reach Started: " + routeId);
                }
            }
            Set<String> unexpectedComponents = new HashSet<>(context.getComponentNames());
            unexpectedComponents.removeAll(schemes);
            unexpectedComponents.remove("direct");
            unexpectedComponents.remove("stub");
            unexpectedComponents.remove("properties");
            if (!unexpectedComponents.isEmpty()) {
                throw new IOException("Main loaded components outside the stub policy: " + unexpectedComponents);
            }
            if (action == null) {
                throw new IOException("Direct Main startup requires a controller action");
            }
            action.run(context);
        } finally {
            main.stop();
        }
    }

    static String testEntryUri(String routeId) throws IOException {
        if (routeId == null || !routeId.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) {
            throw new IOException("Route ID cannot form a safe controller test entry");
        }
        return TEST_ENTRY_PREFIX + routeId;
    }

    public static void validatePolicy(Path route) throws IOException {
        inspect(route, new TreeSet<>());
    }

    /**
     * Rejects candidate-controlled launch inputs outside Ship v1's exact, configuration-free Main route bundle. The
     * evidence launcher repeats this check even though the controller performs it before execution.
     */
    public static void validateLaunchSurface(Path acceptedRoot, List<Path> requestedRoutes) throws IOException {
        Path root = acceptedRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || requestedRoutes == null || requestedRoutes.isEmpty()) {
            throw new IOException("Main launch-surface validation requires an accepted root and routes");
        }

        Set<Path> acceptedRoutes = new HashSet<>();
        for (Path requested : requestedRoutes) {
            if (requested == null) {
                throw new IOException("Main launch-surface validation received a null route");
            }
            Path route = requested.isAbsolute()
                    ? requested.toAbsolutePath().normalize()
                    : root.resolve(requested).normalize();
            if (!route.startsWith(root) || Files.isSymbolicLink(route)
                    || !Files.isRegularFile(route, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Accepted route is absent or unsafe: " + requested);
            }
            route = route.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!route.startsWith(root) || !acceptedRoutes.add(route)) {
                throw new IOException("Accepted route escaped or was duplicated: " + requested);
            }
        }

        try (var entries = Files.walk(root)) {
            for (Path entry : entries.sorted().toList()) {
                if (entry.equals(root)) {
                    continue;
                }
                if (Files.isSymbolicLink(entry)) {
                    throw new IOException(
                            "Candidate launch surface contains a symbolic link: "
                                          + relative(root, entry));
                }
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException(
                            "Candidate launch surface contains a non-regular entry: "
                                          + relative(root, entry));
                }
                Path real = entry.toRealPath(LinkOption.NOFOLLOW_LINKS);
                if (!real.startsWith(root)) {
                    throw new IOException("Candidate launch surface escaped its accepted root");
                }
                if (acceptedRoutes.contains(real)) {
                    continue;
                }
                rejectUnapprovedLaunchInput(root, entry);
            }
        }
    }

    private static void rejectUnapprovedLaunchInput(Path root, Path entry) throws IOException {
        String relative = relative(root, entry);
        String lower = relative.toLowerCase(Locale.ROOT);
        String fileName = entry.getFileName().toString();
        String lowerName = fileName.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".camel.yaml") || lower.endsWith(".camel.yml")) {
            throw launchSurfaceError(relative, "an undeclared Camel YAML route");
        }
        if (lower.endsWith(".camel.xml") || lower.endsWith(".pipe.yaml") || lower.endsWith(".pipe.yml")
                || lower.endsWith(".kamelet.yaml") || lower.endsWith(".kamelet.yml")) {
            throw launchSurfaceError(relative, "an alternate Camel route source");
        }
        if (APPLICATION_CONFIGURATION.matcher(fileName).matches()
                || CAMEL_CONFIGURATION_FILES.contains(lowerName)) {
            throw launchSurfaceError(relative, "candidate runtime configuration");
        }
        if (lower.startsWith("src/main/resources/")) {
            throw launchSurfaceError(relative, "an undeclared Main runtime resource");
        }
        if (lower.startsWith("src/main/java/") || lower.startsWith("src/main/kotlin/")
                || lower.startsWith("src/main/groovy/")) {
            throw launchSurfaceError(relative, "candidate runtime code");
        }
        if (lower.startsWith("meta-inf/services/") || lower.contains("/meta-inf/services/")
                || lower.startsWith("meta-inf/spring/") || lower.contains("/meta-inf/spring/")
                || lower.endsWith("/meta-inf/spring.factories") || lower.equals("meta-inf/spring.factories")) {
            throw launchSurfaceError(relative, "a candidate service-loader or runtime extension");
        }
        if (lower.endsWith(".jar") || lower.endsWith(".class")) {
            throw launchSurfaceError(relative, "candidate-controlled JVM bytecode");
        }
        if (MAVEN_MODEL_HOOKS.contains(lower)) {
            throw launchSurfaceError(relative, "an unbound Maven model hook");
        }
        if (ALTERNATE_BUILD_OR_LAUNCH_FILES.contains(lowerName)) {
            throw launchSurfaceError(relative, "an alternate build or launch model");
        }
        boolean launchDirectory = lower.startsWith("bin/") || lower.startsWith("scripts/")
                || lower.startsWith("src/main/scripts/");
        boolean rootScript = !relative.contains("/") && !Set.of("mvnw", "mvnw.cmd").contains(lowerName);
        if ((launchDirectory || rootScript)
                && SCRIPT_SUFFIXES.stream().anyMatch(lowerName::endsWith)) {
            throw launchSurfaceError(relative, "a candidate-controlled launch script");
        }
    }

    private static IOException launchSurfaceError(String relative, String kind) {
        return new IOException("Ship v1 Main launch surface contains " + kind + ": " + relative);
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static void inspect(Path route, Set<String> schemes) throws IOException {
        long size = Files.size(route);
        if (size <= 0 || size > ShipArtifactLimits.MAX_ROUTE_YAML_BYTES) {
            throw new IOException("Route YAML exceeds the deterministic byte limit");
        }
        rejectYamlReferences(route);
        JsonNode document;
        try (InputStream input = Files.newInputStream(route, LinkOption.NOFOLLOW_LINKS)) {
            document = YAML.readTree(input);
        } catch (RuntimeException e) {
            throw new IOException("Could not safely inspect route YAML", e);
        }
        if (document == null || (!document.isArray() && !document.isObject())) {
            throw new IOException("Route YAML must contain a mapping or sequence");
        }
        visit(document, null, schemes);
    }

    private static void visit(JsonNode node, String parentKey, Set<String> schemes) throws IOException {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                visit(child, parentKey, schemes);
            }
            return;
        }
        if (node.isObject()) {
            if (isEndpointSelector(parentKey)) {
                JsonNode uriPattern = node.get("uri");
                if (uriPattern == null && "interceptsendtoendpoint".equals(parentKey)) {
                    throw new IOException("interceptSendToEndpoint requires a URI pattern");
                }
                if (uriPattern != null && !uriPattern.isTextual()) {
                    throw new IOException("Endpoint selector URI pattern must be textual");
                }
            }
            requireSingleExpressionSelector(node);
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String rawKey = field.getKey();
                String key = normalize(rawKey);
                if (CatalogExpressionInventory.SIMPLE.equals(rawKey)) {
                    if (!field.getValue().isTextual()
                            || !CatalogExpressionInventory.isSafeSimple(field.getValue().asText())) {
                        throw new IOException("Only scalar allowlisted Simple expressions are supported");
                    }
                    continue;
                }
                if (CatalogExpressionInventory.GENERIC.equals(rawKey)) {
                    requireGenericSimple(field.getValue());
                    continue;
                }
                if (CatalogExpressionInventory.isRejectedAlias(rawKey)) {
                    throw new IOException(
                            "Ship v1 permits only the Simple expression language: " + field.getKey());
                }
                if (CatalogExpressionInventory.isNonExactAlias(rawKey)) {
                    throw new IOException("Camel expression uses an unsupported non-exact alias: " + rawKey);
                }
                if (FORBIDDEN_KEYS.contains(key)) {
                    throw new IOException("Route YAML contains an unsafe startup construct: " + field.getKey());
                }
                visit(field.getValue(),
                        isEndpointSelector(parentKey) && "uri".equals(key) ? parentKey : key,
                        schemes);
            }
            return;
        }
        if (!node.isTextual()) {
            return;
        }
        String value = node.asText().trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_VALUES.stream().anyMatch(lower::contains)) {
            throw new IOException("Route YAML contains an unsafe startup value");
        }
        if (isEndpointSelector(parentKey) && value.contains("${")) {
            throw new IOException("Endpoint selector pattern cannot be dynamic");
        }
        String resolved = rejectPlaceholders(value);
        if (parentKey != null && ENDPOINT_KEYS.contains(parentKey)) {
            int colon = resolved.indexOf(':');
            if (colon <= 0 || resolved.substring(0, colon).contains("{")
                    || resolved.substring(0, colon).contains("$")) {
                throw new IOException("Endpoint URI has no static safe component scheme");
            }
            String scheme = resolved.substring(0, colon).toLowerCase(Locale.ROOT);
            if (!URI_SCHEME.matcher(scheme).matches()) {
                throw new IOException("Endpoint URI has an unsafe component scheme");
            }
            if (resolved.toLowerCase(Locale.ROOT).startsWith(TEST_ENTRY_PREFIX)) {
                throw new IOException("Route uses the controller-reserved test endpoint namespace");
            }
            schemes.add(scheme);
        }
    }

    private static void requireSingleExpressionSelector(JsonNode node) throws IOException {
        int selected = 0;
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (CatalogExpressionInventory.isKnownAlias(field.getKey())) {
                selected++;
            }
        }
        if (selected > 1) {
            throw new IOException("Camel expression must select exactly one unambiguous language alias");
        }
    }

    private static void requireGenericSimple(JsonNode value) throws IOException {
        if (value == null || !value.isObject() || value.size() != 2
                || !value.has(CatalogExpressionInventory.GENERIC) || !value.has("expression")) {
            throw new IOException(
                    "Generic Camel language expressions must contain exactly language and expression");
        }
        JsonNode language = value.get(CatalogExpressionInventory.GENERIC);
        JsonNode expression = value.get("expression");
        if (!language.isTextual() || !CatalogExpressionInventory.SIMPLE.equals(language.asText())) {
            throw new IOException("Ship v1 generic Camel expressions must select exactly Simple");
        }
        if (!expression.isTextual() || !CatalogExpressionInventory.isSafeSimple(expression.asText())) {
            throw new IOException("Only scalar allowlisted Simple expressions are supported");
        }
    }

    static void rejectYamlReferences(Path yaml) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(100);
        options.setCodePointLimit(ShipArtifactLimits.MAX_ROUTE_YAML_BYTES);
        try (var reader = Files.newBufferedReader(yaml)) {
            ParserImpl parser = new ParserImpl(new StreamReader(reader), options);
            int documents = 0;
            while (parser.peekEvent() != null) {
                var event = parser.getEvent();
                if (event instanceof DocumentStartEvent && ++documents > 1) {
                    throw new IOException("YAML must contain exactly one document");
                }
                if (event instanceof AliasEvent
                        || event instanceof NodeEvent node && node.getAnchor() != null
                        || event instanceof ScalarEvent scalar && scalar.getTag() != null
                        || event instanceof CollectionStartEvent collection && collection.getTag() != null) {
                    throw new IOException("YAML aliases, anchors, and explicit tags are forbidden");
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Could not inspect YAML parser events safely", e);
        }
    }

    private static String rejectPlaceholders(String value) throws IOException {
        Matcher matcher = PLACEHOLDER.matcher(value);
        if (matcher.find() || value.contains("{{") || value.contains("}}")) {
            throw new IOException(
                    "Route placeholders are unsupported until bound to an approved runtime-property contract");
        }
        if (value.contains("${") && !CatalogExpressionInventory.isSafeSimple(value)) {
            throw new IOException("Route contains an unsupported dynamic placeholder");
        }
        return value;
    }

    private static String normalize(String value) {
        return value.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static boolean isEndpointSelector(String key) {
        return key != null && ENDPOINT_SELECTOR_KEYS.contains(key);
    }

    private static String camelVersion() {
        String version = CamelContext.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "unknown" : version;
    }

    @FunctionalInterface
    public interface StartedAction {

        void run(CamelContext context) throws Exception;
    }
}

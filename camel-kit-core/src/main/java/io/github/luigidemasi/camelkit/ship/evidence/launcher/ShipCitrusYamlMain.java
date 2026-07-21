package io.github.luigidemasi.camelkit.ship.evidence.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;

import io.github.luigidemasi.camelkit.ship.ShipArtifactLimits;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/** Runs one allowlisted Citrus YAML behavior test through one accepted Camel route. */
public final class ShipCitrusYamlMain {

    static final int MAX_CASES = 32;
    static final int MAX_ACTIONS = MAX_CASES * 2;
    static final int MAX_HEADERS_PER_ACTION = 32;
    static final int MAX_BODY_CHARS = 256 * 1024;
    static final int MAX_HEADER_VALUE_CHARS = 4096;
    private static final Pattern TEST_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private static final Pattern ENDPOINT = Pattern.compile("camel:sync:direct:[A-Za-z0-9_.-]+");
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,127}");
    private static final Set<String> TOP_LEVEL = Set.of("name", "actions");
    private static final Set<String> ACTION_FIELDS = Set.of("endpoint", "message");
    private static final Set<String> MESSAGE_FIELDS = Set.of("body", "headers");
    private static final Set<String> HEADER_FIELDS = Set.of("name", "value");
    private static final Pattern CITRUS_FUNCTION = Pattern.compile(
            "(?i)citrus:[A-Za-z][A-Za-z0-9]*\\s*\\(");
    private static final ObjectMapper YAML = new ObjectMapper(
            YAMLFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(50)
                            .maxStringLength(ShipArtifactLimits.MAX_CITRUS_YAML_BYTES)
                            .build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build());

    private ShipCitrusYamlMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1 && "--payload-version".equals(arguments[0])) {
            System.out.println("Citrus direct YAML " + citrusVersion() + " with Camel " + camelVersion());
            return;
        }
        TreeSet<Path> routes = new TreeSet<>();
        TreeSet<String> routeIds = new TreeSet<>();
        Path test = null;
        for (String argument : arguments) {
            if (argument.startsWith("--route=")) {
                routes.add(Path.of(argument.substring("--route=".length())));
            } else if (argument.startsWith("--expected-route=")) {
                routeIds.add(argument.substring("--expected-route=".length()));
            } else if (argument.startsWith("--test=") && test == null) {
                test = Path.of(argument.substring("--test=".length()));
            } else {
                throw new IOException("Unknown or duplicate direct Citrus argument");
            }
        }
        if (routes.size() != 1 || routeIds.size() != 1 || test == null) {
            throw new IOException("Direct Citrus requires exactly one route, route ID, and YAML test");
        }
        Path acceptedRoot = Path.of("/workspace");
        Path acceptedTest = acceptedFile(
                acceptedRoot, test, ShipArtifactLimits.MAX_CITRUS_YAML_BYTES, "Citrus YAML test");
        String routeId = routeIds.first();
        TestContract contract = inspect(acceptedTest, routeId);
        ShipCamelMainBootstrap.withTestEntryPoint(
                acceptedRoot, List.copyOf(routes), routeIds, routeId,
                context -> run(context, acceptedTest, contract));
    }

    static TestContract inspect(Path test, String expectedRouteId) throws IOException {
        long size = Files.size(test);
        if (size <= 0 || size > ShipArtifactLimits.MAX_CITRUS_YAML_BYTES) {
            throw new IOException("Citrus YAML exceeds the deterministic byte limit");
        }
        ShipCamelMainBootstrap.rejectYamlReferences(test);
        JsonNode document;
        try (InputStream input = Files.newInputStream(test, LinkOption.NOFOLLOW_LINKS)) {
            document = YAML.readTree(input);
        } catch (RuntimeException e) {
            throw new IOException("Could not safely inspect Citrus YAML", e);
        }
        if (document == null || !document.isObject()) {
            throw new IOException("Citrus YAML must be one mapping");
        }
        for (Map.Entry<String, JsonNode> field : document.properties()) {
            if (!TOP_LEVEL.contains(field.getKey())) {
                throw new IOException("Citrus YAML top-level field is outside the controller allowlist");
            }
        }
        rejectDynamicText(document);
        JsonNode name = document.get("name");
        JsonNode actions = document.get("actions");
        if (name == null || !name.isTextual() || !TEST_NAME.matcher(name.asText()).matches()
                || actions == null || !actions.isArray()) {
            throw new IOException("Citrus YAML must name exactly one bounded behavior test");
        }
        if (actions.size() < 2 || actions.size() > MAX_ACTIONS || actions.size() % 2 != 0
                || actions.size() / 2 > MAX_CASES) {
            throw new IOException(
                    "Citrus YAML requires 1-" + MAX_CASES
                                  + " complete send/receive cases and at most " + MAX_ACTIONS + " actions");
        }
        String expectedEndpoint = "camel:sync:" + ShipCamelMainBootstrap.testEntryUri(expectedRouteId);
        List<BehaviorCase> cases = new ArrayList<>(actions.size() / 2);
        for (int index = 0; index < actions.size(); index += 2) {
            Action send = action(actions.get(index), "send");
            Action receive = action(actions.get(index + 1), "receive");
            if (!send.endpoint().equals(receive.endpoint())) {
                throw new IOException("Each Citrus send and assertion must address the same Camel endpoint");
            }
            if (!expectedEndpoint.equals(send.endpoint())) {
                throw new IOException("Every Citrus endpoint must address the controller test entry for its route");
            }
            cases.add(new BehaviorCase(
                    send.body(), send.headers(), receive.body(), receive.headers()));
        }
        return new TestContract(name.asText(), expectedEndpoint, cases);
    }

    public static void validatePolicy(Path test, String expectedRouteId) throws IOException {
        inspect(test, expectedRouteId);
    }

    private static Action action(JsonNode node, String expected) throws IOException {
        if (node == null || !node.isObject() || node.size() != 1 || !node.has(expected)) {
            throw new IOException("Citrus actions must be exactly send followed by receive");
        }
        JsonNode action = node.get(expected);
        if (!action.isObject()) {
            throw new IOException("Citrus action must be a mapping");
        }
        for (Map.Entry<String, JsonNode> field : action.properties()) {
            if (!ACTION_FIELDS.contains(field.getKey())) {
                throw new IOException("Citrus action field is outside the controller allowlist");
            }
        }
        if (action.size() != ACTION_FIELDS.size()) {
            throw new IOException("Citrus action requires only endpoint and message");
        }
        String endpoint = textualEndpoint(action.get("endpoint"));
        JsonNode message = action.get("message");
        if (message == null || !message.isObject() || !message.has("body")
                || message.size() < 1 || message.size() > MESSAGE_FIELDS.size()) {
            throw new IOException("Citrus message requires one inline body and optional literal headers");
        }
        for (Map.Entry<String, JsonNode> field : message.properties()) {
            if (!MESSAGE_FIELDS.contains(field.getKey())) {
                throw new IOException("Citrus message field is outside the controller allowlist");
            }
        }
        JsonNode body = message.get("body");
        if (body == null || !body.isObject() || body.size() != 1 || !body.has("data")
                || !body.get("data").isTextual() || body.get("data").asText().isBlank()
                || body.get("data").asText().length() > MAX_BODY_CHARS) {
            throw new IOException("Citrus message body must contain one bounded nonblank inline literal");
        }
        return new Action(endpoint, body.get("data").asText(), headers(message.get("headers")));
    }

    private static List<Header> headers(JsonNode node) throws IOException {
        if (node == null) {
            return List.of();
        }
        if (!node.isArray() || node.isEmpty() || node.size() > MAX_HEADERS_PER_ACTION) {
            throw new IOException("Citrus headers must contain 1-" + MAX_HEADERS_PER_ACTION + " literal entries");
        }
        List<Header> headers = new ArrayList<>(node.size());
        Set<String> names = new HashSet<>();
        for (JsonNode entry : node) {
            if (!entry.isObject() || entry.size() != HEADER_FIELDS.size()
                    || !entry.has("name") || !entry.has("value")) {
                throw new IOException("Each Citrus header requires exactly one name and value");
            }
            for (Map.Entry<String, JsonNode> field : entry.properties()) {
                if (!HEADER_FIELDS.contains(field.getKey())) {
                    throw new IOException("Citrus header field is outside the controller allowlist");
                }
            }
            JsonNode nameNode = entry.get("name");
            JsonNode valueNode = entry.get("value");
            if (!nameNode.isTextual() || !HEADER_NAME.matcher(nameNode.asText()).matches()) {
                throw new IOException("Citrus header name is not a safe application header name");
            }
            String name = nameNode.asText();
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (normalizedName.startsWith("camel") || normalizedName.startsWith("citrus")
                    || !names.add(normalizedName)) {
                throw new IOException("Citrus headers must be unique application-owned names");
            }
            if (!valueNode.isTextual() || valueNode.asText().length() > MAX_HEADER_VALUE_CHARS
                    || valueNode.asText().chars().anyMatch(Character::isISOControl)) {
                throw new IOException("Citrus header value must be one bounded literal string");
            }
            headers.add(new Header(name, valueNode.asText()));
        }
        return List.copyOf(headers);
    }

    private static String textualEndpoint(JsonNode value) throws IOException {
        String endpoint;
        if (value != null && value.isTextual()) {
            endpoint = value.asText();
        } else {
            throw new IOException("Citrus endpoint must be one literal URI");
        }
        if (!ENDPOINT.matcher(endpoint).matches()) {
            throw new IOException("Citrus may address only a literal synchronous direct Camel endpoint");
        }
        return endpoint;
    }

    private static void rejectDynamicText(JsonNode node) throws IOException {
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                rejectDynamicText(child);
            }
        } else if (node.isTextual()) {
            String value = node.asText();
            if (value.contains("${") || value.contains("#{") || value.contains("{{")
                    || CITRUS_FUNCTION.matcher(value).find()
                    || value.length() >= 2 && value.startsWith("@") && value.endsWith("@")) {
                throw new IOException("Citrus YAML contains a variable, function, or validation matcher");
            }
        }
    }

    private static void run(CamelContext camelContext, Path test, TestContract contract) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> resolverType = Class.forName(
                "org.citrusframework.camel.context.CamelReferenceResolver", true, loader);
        Object resolver = resolverType.getConstructor(CamelContext.class).newInstance(camelContext);
        Class<?> referenceResolver = Class.forName("org.citrusframework.spi.ReferenceResolver", true, loader);
        Class<?> builderType = Class.forName("org.citrusframework.CitrusContext$Builder", true, loader);
        Object builder = builderType.getMethod("defaultContext").invoke(null);
        builderType.getMethod("referenceResolver", referenceResolver).invoke(builder, resolver);
        Object citrusContext = builderType.getMethod("build").invoke(builder);

        Class<?> manager = Class.forName("org.citrusframework.CitrusInstanceManager", true, loader);
        Class<?> strategy = Class.forName("org.citrusframework.CitrusInstanceStrategy", true, loader);
        Object singleton = strategy.getField("SINGLETON").get(null);
        manager.getMethod("reset").invoke(null);
        manager.getMethod("mode", strategy).invoke(null, singleton);
        Class<?> provider = Class.forName("org.citrusframework.CitrusContextProvider", true, loader);
        Object contextProvider = Proxy.newProxyInstance(loader, new Class<?>[]{provider}, (proxy, method, args) -> {
            if ("create".equals(method.getName()) && method.getParameterCount() == 0) {
                return citrusContext;
            }
            if ("toString".equals(method.getName())) {
                return "controller-owned-citrus-context";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException("Unexpected Citrus context provider method");
        });

        try {
            Class<?> citrus = Class.forName("org.citrusframework.Citrus", true, loader);
            Object citrusInstance = citrus.getMethod("newInstance", provider).invoke(null, contextProvider);
            Object actualContext = citrus.getMethod("getCitrusContext").invoke(citrusInstance);
            if (actualContext != citrusContext
                    || resolverType.getMethod("getCamelContext").invoke(resolver) != camelContext) {
                throw new IOException("Citrus did not bind to the preloaded accepted CamelContext");
            }

            Class<?> configurationType = Class.forName(
                    "org.citrusframework.main.TestRunConfiguration", true, loader);
            Object configuration = configurationType.getConstructor().newInstance();
            configurationType.getMethod("setEngine", String.class).invoke(configuration, "junit-jupiter");
            Class<?> sourceType = Class.forName("org.citrusframework.TestSource", true, loader);
            Object source = sourceType.getConstructor(String.class, String.class, String.class)
                    .newInstance("yaml", contract.name(), test.toString());
            configurationType.getMethod("setTestSources", List.class)
                    .invoke(configuration, List.of(source));
            configurationType.getMethod("setPackages", List.class).invoke(configuration, List.of());
            configurationType.getMethod("setIncludes", String[].class).invoke(configuration, (Object) new String[0]);
            configurationType.getMethod("setModules", Set.class).invoke(configuration, Set.of());
            configurationType.getMethod("setDependencies", Set.class).invoke(configuration, Set.of());
            configurationType.getMethod("setDefaultProperties", Map.class).invoke(configuration, Map.of());
            configurationType.getMethod("setVerbose", boolean.class).invoke(configuration, false);
            configurationType.getMethod("setReset", boolean.class).invoke(configuration, false);

            Class<?> engineType = Class.forName("org.citrusframework.main.TestEngine", true, loader);
            Object engine = engineType.getMethod("lookup", configurationType).invoke(null, configuration);
            if (!"org.citrusframework.junit.jupiter.JUnitJupiterEngine".equals(engine.getClass().getName())) {
                throw new IOException("Citrus resolved an unexpected test engine");
            }
            Class<?> listenerType = Class.forName(
                    "org.junit.platform.launcher.TestExecutionListener", true, loader);
            Class<?> summaryListenerType = Class.forName(
                    "org.junit.platform.launcher.listeners.SummaryGeneratingListener", true, loader);
            Object listener = summaryListenerType.getConstructor().newInstance();
            engine.getClass().getMethod("addTestListener", listenerType).invoke(engine, listener);
            engineType.getMethod("run").invoke(engine);
            Object summary = summaryListenerType.getMethod("getSummary").invoke(listener);
            Class<?> summaryType = Class.forName(
                    "org.junit.platform.launcher.listeners.TestExecutionSummary", true, loader);
            requireCount(summary, summaryType, "getTestsFoundCount", 1);
            requireCount(summary, summaryType, "getTestsStartedCount", 1);
            requireCount(summary, summaryType, "getTestsSucceededCount", 1);
            requireCount(summary, summaryType, "getTestsFailedCount", 0);
            requireCount(summary, summaryType, "getTestsSkippedCount", 0);
            requireCount(summary, summaryType, "getTestsAbortedCount", 0);

            Method resultsMethod = citrusContext.getClass().getMethod("getTestResults");
            Object results = resultsMethod.invoke(citrusContext);
            Class<?> resultsType = resultsMethod.getReturnType();
            requireCount(results, resultsType, "getSize", 1);
            requireCount(results, resultsType, "getSuccess", 1);
            requireCount(results, resultsType, "getFailed", 0);
            requireCount(results, resultsType, "getSkipped", 0);
        } finally {
            manager.getMethod("reset").invoke(null);
        }
    }

    private static void requireCount(Object target, Class<?> publicType, String method, long expected)
            throws Exception {
        if (!publicType.isInstance(target)) {
            throw new IOException("Direct Citrus result has an unexpected implementation");
        }
        Number actual = (Number) publicType.getMethod(method).invoke(target);
        if (actual.longValue() != expected) {
            throw new IOException("Direct Citrus result " + method + " was " + actual + ", expected " + expected);
        }
    }

    private static Path acceptedFile(Path rootValue, Path value, long maximum, String label) throws IOException {
        Path root = rootValue.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path file = value.toAbsolutePath().normalize();
        if (!file.startsWith(root) || Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) <= 0 || Files.size(file) > maximum) {
            throw new IOException(label + " is outside the accepted snapshot or unsafe");
        }
        file = file.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!file.startsWith(root)) {
            throw new IOException(label + " escaped the accepted snapshot");
        }
        return file;
    }

    private static String citrusVersion() {
        try {
            Class<?> citrus = Class.forName("org.citrusframework.Citrus");
            return String.valueOf(citrus.getMethod("getVersion").invoke(null));
        } catch (ReflectiveOperationException e) {
            return "unknown";
        }
    }

    private static String camelVersion() {
        String version = CamelContext.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "unknown" : version;
    }

    public record TestContract(String name, String endpoint, List<BehaviorCase> cases) {

        public TestContract {
            cases = List.copyOf(cases);
        }
    }

    public record BehaviorCase(
            String requestBody,
            List<Header> requestHeaders,
            String responseBody,
            List<Header> responseHeaders) {

        public BehaviorCase {
            requestHeaders = List.copyOf(requestHeaders);
            responseHeaders = List.copyOf(responseHeaders);
        }
    }

    public record Header(String name, String value) {
    }

    private record Action(String endpoint, String body, List<Header> headers) {
    }
}

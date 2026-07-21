package io.github.luigidemasi.camelkit.ship.catalog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject.Kind;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.EndpointUsage;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/** Deterministically derives catalog usage from bounded Camel YAML DSL documents. */
public final class CamelYamlCatalogUsageExtractor {

    private static final long MAX_YAML_BYTES = 2L * 1024 * 1024;
    private static final int MAX_USED_SUBJECTS = 512;
    private static final int MAX_ENDPOINTS = 2_048;
    private static final Pattern URI_SCHEME = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*");
    private static final Pattern OPTION_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9+._-]{0,127}");
    private static final Pattern INTEGER = Pattern.compile("[+-]?[0-9]+");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)");
    private static final Set<String> STEP_CONTAINERS = Set.of("steps", "outputs");
    private static final Set<String> DOCUMENT_CONTAINERS = Set.of("routes");
    private static final Set<String> NESTED_EIPS = Set.of(
            "from", "when", "otherwise", "doCatch", "doFinally", "onException", "onCompletion", "onWhen");
    private static final Set<String> PRIMARY_ENDPOINT_EIPS = Set.of(
            "from", "to", "toD", "poll", "wireTap");
    private static final Set<String> ENDPOINT_SELECTOR_EIPS = Set.of(
            "interceptFrom", "interceptSendToEndpoint");
    private static final Set<String> UNPROVABLE_ENDPOINT_EIPS = Set.of(
            "dynamicRouter", "enrich", "pollEnrich", "recipientList", "routingSlip", "serviceCall");
    private static final Set<String> OPAQUE_FIELDS = Set.of("parameters", "additionalProperties");
    private static final Set<String> DATAFORMAT_STEP_OPTIONS = Set.of("id", "description", "disabled");
    private static final ObjectReader YAML = new com.fasterxml.jackson.databind.ObjectMapper(
            YAMLFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(100)
                            .maxStringLength((int) MAX_YAML_BYTES)
                            .build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .reader()
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public List<CatalogSubject> extract(Path yaml, Collection<CatalogSubject> available) throws IOException {
        return extractBound(yaml, available).subjects();
    }

    /** Returns subjects and the digest of the exact bytes parsed for acceptance. */
    public Extraction extractBound(Path yaml, Collection<CatalogSubject> available) throws IOException {
        return extractBound(yaml, available, null);
    }

    /** Parse and validate every static endpoint against exact, controller-verified component metadata. */
    public Extraction extractBound(
            Path yaml,
            Collection<CatalogSubject> available,
            Collection<CatalogComponentModel> componentModels)
            throws IOException {
        Objects.requireNonNull(yaml, "yaml must not be null");
        Path file = yaml.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Camel YAML must be a regular non-symbolic-link file: " + file);
        }
        long size = Files.size(file);
        if (size <= 0 || size > MAX_YAML_BYTES) {
            throw new IOException("Camel YAML has an unsafe size: " + size);
        }
        byte[] content;
        try (InputStream input = Files.newInputStream(file)) {
            content = input.readNBytes((int) MAX_YAML_BYTES + 1);
            if (content.length > MAX_YAML_BYTES || input.read() != -1) {
                throw new IOException("Camel YAML grew beyond the safe size while it was read");
            }
        }
        if (content.length != size) {
            throw new IOException("Camel YAML changed size while it was read");
        }
        JsonNode document;
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            document = YAML.readTree(input);
        } catch (RuntimeException e) {
            throw new IOException("Could not parse Camel YAML: " + safeMessage(e), e);
        }
        if (document == null || (!document.isArray() && !document.isObject())) {
            throw new IOException("Camel YAML must contain a mapping or sequence document");
        }

        CatalogIndex index = new CatalogIndex(available);
        ComponentModelIndex models = componentModels == null ? null : new ComponentModelIndex(componentModels);
        TreeSet<CatalogSubject> result = new TreeSet<>();
        List<EndpointUsage> endpoints = new ArrayList<>();
        visit(document, true, null, index, models, result, endpoints);
        if (result.size() > MAX_USED_SUBJECTS) {
            throw new IOException("Camel YAML uses too many catalog subjects: " + result.size());
        }
        if (endpoints.size() > MAX_ENDPOINTS) {
            throw new IOException("Camel YAML declares too many endpoints: " + endpoints.size());
        }
        endpoints.sort(Comparator.comparing((EndpointUsage usage) -> usage.component().name())
                .thenComparingInt(EndpointUsage::pathParameterCount)
                .thenComparing(usage -> String.join("\u0000", usage.componentOptions()))
                .thenComparing(usage -> String.join("\u0000", usage.endpointOptions())));
        return new Extraction(ShipDigest.sha256(content), List.copyOf(result), List.copyOf(endpoints));
    }

    public record Extraction(String digest, List<CatalogSubject> subjects, List<EndpointUsage> endpoints) {

        public Extraction {
            subjects = subjects == null ? List.of() : List.copyOf(subjects);
            endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
        }
    }

    private static void visit(
            JsonNode node,
            boolean stepMapping,
            String operation,
            CatalogIndex index,
            ComponentModelIndex models,
            Set<CatalogSubject> result,
            List<EndpointUsage> endpoints)
            throws IOException {
        if (!stepMapping && operation != null && UNPROVABLE_ENDPOINT_EIPS.contains(operation)) {
            throw new IOException(
                    "Ship v1 cannot catalog-prove expression-driven or service-discovered endpoint EIP: "
                                  + operation);
        }
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                visit(child, stepMapping, operation, index, models, result, endpoints);
            }
            return;
        }
        if (node.isTextual()) {
            if (isEndpointSelector(operation)) {
                requireStatic(node.asText(), operation + " URI pattern");
            } else if (isPrimaryEndpoint(operation)) {
                addEndpoint(node.asText(), index, models, result, endpoints);
            } else if ("marshal".equals(operation) || "unmarshal".equals(operation)) {
                addDataformat(node.asText(), index, result);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        if (("marshal".equals(operation) || "unmarshal".equals(operation))) {
            requireSingleDataformat(node, index);
        }
        if (!stepMapping && isEndpointSelector(operation)) {
            JsonNode uriPattern = node.get("uri");
            if (uriPattern == null && "interceptSendToEndpoint".equals(operation)) {
                throw new IOException("interceptSendToEndpoint requires a URI pattern");
            }
            if (uriPattern != null && !uriPattern.isTextual()) {
                throw new IOException(operation + " URI pattern must be textual");
            }
            if (uriPattern != null) {
                requireStatic(uriPattern.asText(), operation + " URI pattern");
            }
        }
        if (models != null && !stepMapping && isPrimaryEndpoint(operation)) {
            endpoints.add(validateEndpointObject(node, models));
        }
        if (!stepMapping) {
            requireSingleExpressionSelector(node);
        }

        for (Map.Entry<String, JsonNode> field : node.properties()) {
            String rawKey = field.getKey();
            String key = index.canonical(Kind.EIP, rawKey);
            JsonNode value = field.getValue();

            if (stepMapping && key != null) {
                result.add(new CatalogSubject(Kind.EIP, key));
                visit(value, false, key, index, models, result, endpoints);
                continue;
            }
            if (stepMapping && DOCUMENT_CONTAINERS.contains(rawKey)) {
                visit(value, true, operation, index, models, result, endpoints);
                continue;
            }
            if (stepMapping) {
                throw new IOException("Unknown Camel YAML step or document definition: " + rawKey);
            }
            if (OPAQUE_FIELDS.contains(rawKey)) {
                continue;
            }
            if (STEP_CONTAINERS.contains(rawKey)) {
                visit(value, true, operation, index, models, result, endpoints);
                continue;
            }

            if (CatalogExpressionInventory.SIMPLE.equals(rawKey)) {
                addSimple(value, index, result);
                continue;
            }
            if (CatalogExpressionInventory.GENERIC.equals(rawKey)) {
                addGenericSimple(value, index, result);
                continue;
            }
            if (CatalogExpressionInventory.isRejectedAlias(rawKey)) {
                throw new IOException("Ship v1 permits only the Simple expression language: " + rawKey);
            }
            if (CatalogExpressionInventory.isNonExactAlias(rawKey)) {
                throw new IOException("Camel expression uses an unsupported non-exact language alias: " + rawKey);
            }

            String nestedEip = index.canonical(Kind.EIP, rawKey);
            if (nestedEip != null && NESTED_EIPS.contains(nestedEip)) {
                result.add(new CatalogSubject(Kind.EIP, nestedEip));
                visit(value, false, nestedEip, index, models, result, endpoints);
                continue;
            }

            if (isEndpointUriField(operation, rawKey)) {
                if (!value.isTextual()) {
                    throw new IOException("Endpoint URI field must contain one static textual URI: " + rawKey);
                }
                addComponent(value.asText(), index, result);
                if (models != null
                        && !("uri".equals(rawKey) && isPrimaryEndpoint(operation))) {
                    endpoints.add(validateEndpoint(value.asText(), null, null, models));
                }
                continue;
            }
            if (operation != null && ("marshal".equals(operation) || "unmarshal".equals(operation))) {
                String format = index.canonical(Kind.DATAFORMAT, rawKey);
                if (format != null) {
                    result.add(new CatalogSubject(Kind.DATAFORMAT, format));
                    visit(value, false, null, index, models, result, endpoints);
                    continue;
                }
                if (!DATAFORMAT_STEP_OPTIONS.contains(rawKey)) {
                    throw new IOException("Unknown or dynamic Camel data format: " + rawKey);
                }
            }
            if (("dataFormat".equals(rawKey) || "data-format".equals(rawKey)) && value.isTextual()) {
                String format = index.canonical(Kind.DATAFORMAT, value.asText());
                if (format == null) {
                    throw new IOException("Unknown Camel data format: " + value.asText());
                }
                result.add(new CatalogSubject(Kind.DATAFORMAT, format));
            }

            if (index.canonical(Kind.LANGUAGE, rawKey) != null) {
                throw new IOException("Camel expression uses an unsupported or non-exact language alias: " + rawKey);
            }
            visit(value, false,
                    isPrimaryEndpoint(operation) || isEndpointSelector(operation) ? null : operation,
                    index, models, result, endpoints);
        }
    }

    private static boolean isPrimaryEndpoint(String operation) {
        return operation != null && PRIMARY_ENDPOINT_EIPS.contains(operation);
    }

    private static boolean isEndpointSelector(String operation) {
        return operation != null && ENDPOINT_SELECTOR_EIPS.contains(operation);
    }

    private static boolean isEndpointUriField(String operation, String field) {
        return "uri".equals(field) && !isEndpointSelector(operation)
                || "deadLetterUri".equals(field)
                || "interceptSendToEndpoint".equals(operation) && "afterUri".equals(field)
                || "saga".equals(operation) && ("compensation".equals(field) || "completion".equals(field));
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

    private static void addSimple(JsonNode value, CatalogIndex index, Set<CatalogSubject> result)
            throws IOException {
        if (value == null || !value.isTextual() || !CatalogExpressionInventory.isSafeSimple(value.asText())) {
            throw new IOException("Only scalar allowlisted Simple expressions are supported");
        }
        addSimpleSubject(index, result);
    }

    private static void addGenericSimple(JsonNode value, CatalogIndex index, Set<CatalogSubject> result)
            throws IOException {
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
        addSimpleSubject(index, result);
    }

    private static void addSimpleSubject(CatalogIndex index, Set<CatalogSubject> result) throws IOException {
        String simple = index.canonical(Kind.LANGUAGE, CatalogExpressionInventory.SIMPLE);
        if (!CatalogExpressionInventory.SIMPLE.equals(simple)) {
            throw new IOException("Simple is unavailable in the resolved Camel catalog");
        }
        result.add(new CatalogSubject(Kind.LANGUAGE, simple));
    }

    private static void requireSingleDataformat(JsonNode node, CatalogIndex index) throws IOException {
        int count = 0;
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (index.canonical(Kind.DATAFORMAT, field.getKey()) != null) {
                count++;
            } else if (!DATAFORMAT_STEP_OPTIONS.contains(field.getKey())) {
                throw new IOException("Unknown or dynamic Camel data format: " + field.getKey());
            }
        }
        if (count != 1) {
            throw new IOException("Camel marshal/unmarshal must select exactly one static data format");
        }
    }

    private static void addDataformat(String value, CatalogIndex index, Set<CatalogSubject> result)
            throws IOException {
        String format = index.canonical(Kind.DATAFORMAT, value == null ? null : value.trim());
        if (format == null) {
            throw new IOException("Unknown or dynamic Camel data format: " + value);
        }
        result.add(new CatalogSubject(Kind.DATAFORMAT, format));
    }

    private static EndpointUsage validateEndpointObject(JsonNode node, ComponentModelIndex models)
            throws IOException {
        JsonNode uri = node.get("uri");
        if (uri == null || !uri.isTextual()) {
            throw new IOException("Endpoint definition must contain one static textual URI");
        }
        return validateEndpoint(uri.asText(), null, optionMap(node.get("parameters"), "parameters"), models);
    }

    private static Map<String, JsonNode> optionMap(JsonNode node, String name) throws IOException {
        if (node == null) {
            return Map.of();
        }
        if (!node.isObject() || node.size() > 512) {
            throw new IOException("Endpoint " + name + " must be a bounded mapping");
        }
        Map<String, JsonNode> result = new HashMap<>();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (!OPTION_NAME.matcher(field.getKey()).matches()
                    || field.getValue() == null
                    || !(field.getValue().isTextual() || field.getValue().isNumber()
                            || field.getValue().isBoolean())) {
                throw new IOException(
                        "Endpoint " + name + " contains a dynamic or unsafe option: "
                                      + field.getKey());
            }
            if (result.putIfAbsent(field.getKey(), field.getValue()) != null) {
                throw new IOException("Endpoint " + name + " contains duplicate options");
            }
        }
        return result;
    }

    private static EndpointUsage validateEndpoint(
            String rawUri,
            Map<String, JsonNode> yamlComponentOptions,
            Map<String, JsonNode> yamlEndpointOptions,
            ComponentModelIndex models)
            throws IOException {
        String uri = rawUri == null ? "" : rawUri.trim();
        requireStatic(uri, "endpoint URI");
        int colon = uri.indexOf(':');
        if (colon <= 0 || !URI_SCHEME.matcher(uri.substring(0, colon)).matches()) {
            throw new IOException("Endpoint URI has no statically classifiable component scheme: " + uri);
        }
        CatalogComponentModel model = models.model(uri.substring(0, colon));
        String remainder = uri.substring(colon + 1);
        int queryIndex = remainder.indexOf('?');
        String path = queryIndex < 0 ? remainder : remainder.substring(0, queryIndex);
        String query = queryIndex < 0 ? null : remainder.substring(queryIndex + 1);
        requireStatic(path, "endpoint path");
        if (path.indexOf('#') >= 0 || path.indexOf('%') >= 0) {
            throw new IOException("Endpoint path uses an unsupported encoded or fragment form");
        }

        List<CatalogComponentModel.Option> pathOptions = model.options().stream()
                .filter(option -> option.scope() == CatalogComponentModel.Scope.ENDPOINT
                        && option.kind() == CatalogComponentModel.Kind.PATH)
                .sorted(Comparator.comparingInt(CatalogComponentModel.Option::index))
                .toList();
        if (pathOptions.size() > 1) {
            throw new IOException(
                    "Protocol v1 cannot prove multi-segment endpoint path syntax for "
                                  + model.evidence().subject().name());
        }
        int pathCount = path.isEmpty() ? 0 : 1;
        if (pathOptions.isEmpty() && pathCount != 0
                || pathOptions.size() == 1 && pathOptions.get(0).required() && pathCount != 1) {
            throw new IOException("Endpoint path does not satisfy catalog syntax " + model.syntax());
        }
        if (pathCount == 1) {
            validateValue(pathOptions.get(0), path, "endpoint path");
        }

        Map<String, JsonNode> componentOptions = yamlComponentOptions == null
                ? new HashMap<>() : new HashMap<>(yamlComponentOptions);
        Map<String, JsonNode> endpointOptions = yamlEndpointOptions == null
                ? new HashMap<>() : new HashMap<>(yamlEndpointOptions);
        if (query != null) {
            if (query.isEmpty()) {
                throw new IOException("Endpoint URI contains an empty query");
            }
            for (String pair : query.split("&", -1)) {
                int separator = pair.indexOf('=');
                if (separator <= 0 || separator != pair.lastIndexOf('=')) {
                    throw new IOException("Endpoint URI query option must use one key=value pair");
                }
                String name = pair.substring(0, separator);
                String value = pair.substring(separator + 1);
                if (!OPTION_NAME.matcher(name).matches() || name.indexOf('%') >= 0 || value.indexOf('%') >= 0) {
                    throw new IOException("Endpoint URI query uses unsupported encoded option syntax");
                }
                if (endpointOptions.putIfAbsent(name, com.fasterxml.jackson.databind.node.TextNode.valueOf(value))
                    != null) {
                    throw new IOException("Endpoint declares duplicate option: " + name);
                }
            }
        }

        List<String> acceptedComponent = validateOptions(
                componentOptions, model, CatalogComponentModel.Scope.COMPONENT);
        List<String> acceptedEndpoint = validateOptions(
                endpointOptions, model, CatalogComponentModel.Scope.ENDPOINT);
        requireRequiredOptions(model, acceptedComponent, acceptedEndpoint);
        return new EndpointUsage(
                model.evidence().subject(), pathCount, acceptedComponent, acceptedEndpoint);
    }

    private static List<String> validateOptions(
            Map<String, JsonNode> supplied,
            CatalogComponentModel model,
            CatalogComponentModel.Scope scope)
            throws IOException {
        Map<String, CatalogComponentModel.Option> exact = new HashMap<>();
        List<CatalogComponentModel.Option> multiValue = new ArrayList<>();
        for (CatalogComponentModel.Option option : model.options()) {
            if (option.scope() != scope || option.kind() == CatalogComponentModel.Kind.PATH) {
                continue;
            }
            exact.put(normalized(option.name()), option);
            if (option.multiValue() && option.prefix() != null) {
                multiValue.add(option);
            }
        }
        TreeSet<String> accepted = new TreeSet<>();
        Set<String> suppliedNames = new HashSet<>();
        for (Map.Entry<String, JsonNode> entry : supplied.entrySet()) {
            String normalized = normalized(entry.getKey());
            if (!suppliedNames.add(normalized)) {
                throw new IOException("Endpoint contains ambiguous duplicate option: " + entry.getKey());
            }
            CatalogComponentModel.Option option = exact.get(normalized);
            if (option == null) {
                option = multiValue.stream()
                        .filter(candidate -> entry.getKey().startsWith(candidate.prefix())
                                && entry.getKey().length() > candidate.prefix().length())
                        .findFirst()
                        .orElse(null);
            }
            if (option == null) {
                throw new IOException(
                        "Endpoint option is absent from frozen component metadata: "
                                      + entry.getKey());
            }
            validateValue(option, entry.getValue().asText(), "endpoint option " + entry.getKey());
            accepted.add(option.name());
        }
        return List.copyOf(accepted);
    }

    private static void requireRequiredOptions(
            CatalogComponentModel model, List<String> component, List<String> endpoint)
            throws IOException {
        for (CatalogComponentModel.Option option : model.options()) {
            if (!option.required() || option.kind() == CatalogComponentModel.Kind.PATH) {
                continue;
            }
            List<String> observed = option.scope() == CatalogComponentModel.Scope.COMPONENT ? component : endpoint;
            if (!observed.contains(option.name())) {
                throw new IOException("Endpoint omits required catalog option: " + option.name());
            }
        }
    }

    private static void validateValue(CatalogComponentModel.Option option, String value, String label)
            throws IOException {
        requireStatic(value, label);
        if (!option.enumValues().isEmpty() && !option.enumValues().contains(value)) {
            throw new IOException(label + " is outside the catalog enum");
        }
        String type = option.type().toLowerCase(Locale.ROOT);
        boolean valid = switch (type) {
            case "string", "enum" -> true;
            case "boolean" -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            case "integer" -> INTEGER.matcher(value).matches();
            case "number" -> NUMBER.matcher(value).matches();
            case "object" -> option.multiValue() && option.prefix() != null;
            default -> false;
        };
        if (!valid) {
            throw new IOException(label + " cannot be proven against catalog type " + option.type());
        }
    }

    private static void requireStatic(String value, String label) throws IOException {
        if (value == null || value.length() > 16_384 || value.chars().anyMatch(Character::isISOControl)
                || value.contains("{{") || value.contains("}}") || value.contains("${")
                || value.contains("$simple") || value.contains("$bean")
                || value.contains("#bean:") || value.contains("#class:") || value.contains("#type:")) {
            throw new IOException("Dynamic or unsafe " + label + " cannot be catalog-proven");
        }
    }

    private static String normalized(String value) {
        return value.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static void addComponent(String uri, CatalogIndex index, Set<CatalogSubject> result) throws IOException {
        String value = uri == null ? "" : uri.trim();
        int colon = value.indexOf(':');
        if (colon <= 0) {
            throw new IOException("Endpoint URI has no statically classifiable component scheme: " + value);
        }
        String scheme = value.substring(0, colon);
        if (!URI_SCHEME.matcher(scheme).matches()) {
            throw new IOException("Endpoint URI has a dynamic or unsafe component scheme: " + value);
        }
        String component = index.canonical(Kind.COMPONENT, scheme);
        if (component == null) {
            throw new IOException("Endpoint URI uses a component unavailable in the resolved catalog: " + scheme);
        }
        result.add(new CatalogSubject(Kind.COMPONENT, component));
    }

    private static void addEndpoint(
            String uri,
            CatalogIndex index,
            ComponentModelIndex models,
            Set<CatalogSubject> result,
            List<EndpointUsage> endpoints)
            throws IOException {
        addComponent(uri, index, result);
        if (models != null) {
            endpoints.add(validateEndpoint(uri, null, null, models));
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static final class CatalogIndex {
        private final Map<Kind, Map<String, String>> names = new EnumMap<>(Kind.class);

        private CatalogIndex(Collection<CatalogSubject> available) {
            Objects.requireNonNull(available, "available must not be null");
            for (Kind kind : Kind.values()) {
                names.put(kind, new HashMap<>());
            }
            for (CatalogSubject subject : available) {
                Objects.requireNonNull(subject, "available must not contain null");
                String lookup = lookup(subject.name());
                String previous = names.get(subject.kind()).putIfAbsent(lookup, subject.name());
                if (previous != null && !previous.equals(subject.name())) {
                    throw new IllegalArgumentException(
                            "Ambiguous catalog names after YAML normalization: " + previous + ", " + subject.name());
                }
            }
        }

        private String canonical(Kind kind, String value) {
            return value == null ? null : names.get(kind).get(lookup(value));
        }

        private static String lookup(String value) {
            return value.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
        }
    }

    private static final class ComponentModelIndex {
        private final Map<String, CatalogComponentModel> models = new HashMap<>();

        private ComponentModelIndex(Collection<CatalogComponentModel> componentModels) {
            Objects.requireNonNull(componentModels, "componentModels must not be null");
            if (componentModels.isEmpty() || componentModels.size() > MAX_USED_SUBJECTS) {
                throw new IllegalArgumentException("Component metadata has an invalid size");
            }
            for (CatalogComponentModel model : componentModels) {
                if (model == null || model.evidence() == null || model.evidence().subject() == null
                        || model.evidence().subject().kind() != Kind.COMPONENT) {
                    throw new IllegalArgumentException("Component metadata contains an invalid model");
                }
                String key = normalized(model.evidence().subject().name());
                if (models.putIfAbsent(key, model) != null) {
                    throw new IllegalArgumentException("Component metadata contains duplicate models");
                }
            }
        }

        private CatalogComponentModel model(String scheme) throws IOException {
            CatalogComponentModel result = models.get(normalized(scheme));
            if (result == null) {
                throw new IOException("Endpoint component lacks frozen option metadata: " + scheme);
            }
            return result;
        }
    }
}

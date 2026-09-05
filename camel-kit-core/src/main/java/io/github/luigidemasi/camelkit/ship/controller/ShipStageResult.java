package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.JavaPolicy;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactPolicy;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactPolicy.RouteContract;
import io.github.luigidemasi.camelkit.ship.artifact.CitrusDependencyPolicy;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTarget;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.UnansweredQuestion;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Strict typed boundary for one Pi stage response. */
record ShipStageResult(
        int schemaVersion,
        String pipelineId,
        String report,
        ArtifactPolicy artifactPolicy,
        boolean materialAmbiguity,
        List<UnansweredQuestion> unansweredQuestions) {

    static final int SCHEMA_VERSION = 2;

    private static final int MAX_ASSISTANT_BYTES = 1024 * 1024;
    private static final int MAX_REPORT_BYTES = MAX_ASSISTANT_BYTES;
    private static final Pattern ROUTE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private static final Comparator<RouteContract> ROUTE_ORDER = Comparator
            .comparing(RouteContract::routeId)
            .thenComparing(RouteContract::routePath)
            .thenComparing(RouteContract::citrusTestPath);
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);

    static {
        JSON.coercionConfigFor(String.class)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    static ShipStageResult parse(Stage stage, String assistantText) throws IOException {
        if (stage == null) {
            throw new IOException("Pi stage result has no stage");
        }
        WireResult wire;
        boolean legacy;
        try {
            JsonNode document
                    = JSON.readValue(utf8(assistantText, MAX_ASSISTANT_BYTES, "assistant response"), JsonNode.class);
            legacy = document instanceof ObjectNode object && object.path("schemaVersion").isIntegralNumber()
                    && object.path("schemaVersion").intValue() == 1;
            if (legacy && document instanceof ObjectNode object) {
                if (object.has("unansweredQuestions")) {
                    throw new IOException("Pi legacy stage result contains unsupported questions");
                }
                object.putArray("unansweredQuestions");
            }
            wire = JSON.treeToValue(document, WireResult.class);
        } catch (JsonProcessingException e) {
            throw new IOException("Pi stage result is malformed", e);
        }
        if (wire == null || wire.schemaVersion() != SCHEMA_VERSION && !legacy) {
            throw new IOException("Pi stage result has an unsupported schema version");
        }
        String pipelineId = nullableText(wire.pipelineId(), "pipelineId");
        String report = requiredText(wire.report(), "report");
        requireReport(report);
        List<UnansweredQuestion> questions = wire.unansweredQuestions();
        if (questions == null || questions.size() > 100 || questions.stream().anyMatch(java.util.Objects::isNull)
                || (!questions.isEmpty() && !wire.materialAmbiguity())
                || (!legacy && wire.materialAmbiguity() && questions.isEmpty())) {
            throw new IOException("Pi stage unanswered questions are invalid");
        }

        boolean hasPolicy = wire.artifactPolicy() != null && !wire.artifactPolicy().isNull();
        ArtifactPolicy policy = null;
        switch (stage) {
            case DISCOVERY -> {
                if (!ShipRun.isPipelineId(pipelineId) || hasPolicy) {
                    throw new IOException("Pi Discovery result has invalid stage fields");
                }
            }
            case DESIGN, EXECUTE -> {
                if (pipelineId != null || hasPolicy) {
                    throw new IOException("Pi " + stage + " result has invalid stage fields");
                }
            }
            case PLAN -> {
                if (pipelineId != null || !hasPolicy) {
                    throw new IOException("Pi Plan result has invalid stage fields");
                }
                policy = parsePolicy(wire.artifactPolicy());
            }
            case VALIDATE -> throw new IOException("Pi does not produce the Validate stage result");
        }
        return new ShipStageResult(
                SCHEMA_VERSION,
                pipelineId,
                report,
                policy,
                wire.materialAmbiguity(),
                List.copyOf(questions));
    }

    private static ArtifactPolicy parsePolicy(JsonNode document) throws IOException {
        if (!document.isObject()) {
            throw new IOException("Pi Plan artifact policy is not an object");
        }
        requirePolicyTypes(document);
        final ArtifactPolicy policy;
        try {
            policy = JSON.treeToValue(document, ArtifactPolicy.class);
        } catch (JsonProcessingException e) {
            throw new IOException("Pi Plan artifact policy is malformed", e);
        }

        if (!"main".equals(policy.runtime())
                || policy.platformVersion() != null
                || policy.springBootVersion() != null
                || !"yaml".equals(policy.routeDsl())
                || !"simple".equals(policy.expressionLanguage())
                || policy.javaPolicy() != JavaPolicy.FORBIDDEN) {
            throw new IOException("Pi Plan artifact policy has invalid Main fields");
        }
        try {
            new CatalogTarget(
                    policy.runtime(),
                    policy.camelVersion(),
                    policy.platformVersion(),
                    policy.springBootVersion());
        } catch (IllegalArgumentException e) {
            throw new IOException("Pi Plan artifact policy has an invalid Camel version", e);
        }
        List<String> citrusViolations = CitrusDependencyPolicy.violations(
                policy.citrusVersion(), policy.citrusDependencies());
        if (!citrusViolations.isEmpty()) {
            throw new IOException("Pi Plan artifact policy has invalid Citrus dependencies");
        }
        if (!policy.approvedJavaExceptions().isEmpty()
                || !policy.citrusTestsRequired()
                || !policy.oneCitrusTestPerRoute()) {
            throw new IOException("Pi Plan artifact policy weakens required validation");
        }
        validateRoutes(policy.routes());
        return policy;
    }

    private static void validateRoutes(List<RouteContract> routes) throws IOException {
        if (routes.isEmpty()) {
            throw new IOException("Pi Plan artifact policy has no route contracts");
        }
        Set<String> routeIds = new HashSet<>();
        Set<String> routePaths = new HashSet<>();
        Set<String> testPaths = new HashSet<>();
        for (RouteContract route : routes) {
            if (route == null
                    || !ROUTE_ID.matcher(nullToEmpty(route.routeId())).matches()
                    || !safeMaterialPath(route.routePath())
                    || !safeMaterialPath(route.citrusTestPath())
                    || !route.routePath().endsWith(".camel.yaml")
                    || !fileName(route.routePath()).equals(route.routeId() + ".camel.yaml")
                    || !route.citrusTestPath().equals("test/" + route.routeId() + ".camel.it.yaml")
                    || !routeIds.add(route.routeId())
                    || !routePaths.add(route.routePath())
                    || !testPaths.add(route.citrusTestPath())) {
                throw new IOException("Pi Plan artifact policy has an invalid route contract");
            }
        }
        if (!routes.equals(routes.stream().sorted(ROUTE_ORDER).toList())) {
            throw new IOException("Pi Plan route contracts are not sorted canonically");
        }
    }

    private static boolean safeMaterialPath(String value) {
        try {
            return ShipTreePolicy.current().classify(value) == Classification.MATERIAL;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void requirePolicyTypes(JsonNode policy) throws IOException {
        for (String field : List.of(
                "runtime", "camelVersion", "routeDsl", "expressionLanguage", "citrusVersion", "javaPolicy")) {
            requiredText(policy.get(field), "artifactPolicy." + field);
        }
        nullableText(policy.get("platformVersion"), "artifactPolicy.platformVersion");
        nullableText(policy.get("springBootVersion"), "artifactPolicy.springBootVersion");
        requireTextArray(policy.get("citrusDependencies"), "artifactPolicy.citrusDependencies");
        requireTextArray(policy.get("approvedJavaExceptions"), "artifactPolicy.approvedJavaExceptions");
        JsonNode routes = policy.get("routes");
        if (routes == null || !routes.isArray()) {
            throw new IOException("Pi artifactPolicy.routes has an invalid JSON type");
        }
        for (JsonNode route : routes) {
            if (!route.isObject()) {
                throw new IOException("Pi artifactPolicy.routes has an invalid JSON type");
            }
            requiredText(route.get("routeId"), "artifactPolicy.routes.routeId");
            requiredText(route.get("routePath"), "artifactPolicy.routes.routePath");
            requiredText(route.get("citrusTestPath"), "artifactPolicy.routes.citrusTestPath");
        }
        requireBoolean(policy.get("citrusTestsRequired"), "artifactPolicy.citrusTestsRequired");
        requireBoolean(policy.get("oneCitrusTestPerRoute"), "artifactPolicy.oneCitrusTestPerRoute");
    }

    private static void requireTextArray(JsonNode value, String label) throws IOException {
        if (value == null || !value.isArray()) {
            throw new IOException("Pi " + label + " has an invalid JSON type");
        }
        for (JsonNode item : value) {
            requiredText(item, label);
        }
    }

    private static String requiredText(JsonNode value, String label) throws IOException {
        if (value == null || !value.isTextual()) {
            throw new IOException("Pi " + label + " has an invalid JSON type");
        }
        return value.textValue();
    }

    private static String nullableText(JsonNode value, String label) throws IOException {
        if (value == null || value.isNull()) {
            return null;
        }
        return requiredText(value, label);
    }

    private static void requireBoolean(JsonNode value, String label) throws IOException {
        if (value == null || !value.isBoolean()) {
            throw new IOException("Pi " + label + " has an invalid JSON type");
        }
    }

    private static void requireReport(String report) throws IOException {
        if (report == null || report.isBlank() || report.indexOf('\0') >= 0) {
            throw new IOException("Pi stage report is invalid");
        }
        utf8(report, MAX_REPORT_BYTES, "stage report");
    }

    private static byte[] utf8(String value, int maximum, String label) throws IOException {
        if (value == null || value.length() > maximum) {
            throw new IOException("Pi " + label + " exceeds its size limit");
        }
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            if (encoded.remaining() > maximum) {
                throw new IOException("Pi " + label + " exceeds its size limit");
            }
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new IOException("Pi " + label + " is not valid Unicode", e);
        }
    }

    private record WireResult(
            int schemaVersion,
            JsonNode pipelineId,
            JsonNode report,
            JsonNode artifactPolicy,
            boolean materialAmbiguity,
            List<UnansweredQuestion> unansweredQuestions) {
    }

}

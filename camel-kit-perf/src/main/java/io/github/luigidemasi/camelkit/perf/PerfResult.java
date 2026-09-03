package io.github.luigidemasi.camelkit.perf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class PerfResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String sessionId;
    private final String result;
    private final int exitCode;
    private final double costUsd;
    private final boolean isError;
    private final JsonNode raw;

    private PerfResult(String sessionId, String result, int exitCode, double costUsd, boolean isError, JsonNode raw) {
        this.sessionId = sessionId;
        this.result = result;
        this.exitCode = exitCode;
        this.costUsd = costUsd;
        this.isError = isError;
        this.raw = raw;
    }

    public static PerfResult parse(String output, int exitCode) {
        if (exitCode != 0) {
            return new PerfResult(null, output, exitCode, 0.0, true, null);
        }
        try {
            JsonNode root = MAPPER.readTree(output);
            return new PerfResult(
                    textOrNull(root, "session_id"),
                    textOrNull(root, "result"),
                    exitCode,
                    root.path("total_cost_usd").asDouble(0.0),
                    root.path("is_error").asBoolean(false),
                    root);
        } catch (Exception e) {
            return new PerfResult(null, output, exitCode, 0.0, true, null);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && !child.isNull() ? child.asText() : null;
    }

    public String sessionId() {
        return sessionId;
    }

    public String result() {
        return result;
    }

    public int exitCode() {
        return exitCode;
    }

    public double costUsd() {
        return costUsd;
    }

    public boolean isError() {
        return isError;
    }

    public JsonNode raw() {
        return raw;
    }
}

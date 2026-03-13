package io.github.luigidemasi.camelkit.knowledge.indexer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches JIRA issue details from Apache and Red Hat JIRA instances.
 * Results are cached as JSON files to avoid re-fetching on every indexer run.
 *
 * <p>Routing by prefix:
 * <ul>
 *   <li>CAMEL-* → issues.apache.org (public, no auth)</li>
 *   <li>CEQ-*, CSB-*, RHBAC-*, ENTESB-* → issues.redhat.com (bearer token)</li>
 * </ul>
 *
 * <p>Set the Red Hat token via environment variable {@code JIRA_RH_TOKEN}
 * or system property {@code jira.rh.token}.
 */
public class JiraFetcher {

    private static final String APACHE_JIRA_URL = "https://issues.apache.org/jira/rest/api/2/issue/";
    private static final String REDHAT_JIRA_URL = "https://issues.redhat.com/rest/api/2/issue/";
    private static final String FIELDS = "summary,description,fixVersions,components,status,priority,labels,issuetype,resolution";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final Map<String, JiraInstance> PREFIX_MAP = Map.of(
            "CAMEL", JiraInstance.APACHE,
            "CEQ", JiraInstance.REDHAT,
            "CSB", JiraInstance.REDHAT,
            "RHBAC", JiraInstance.REDHAT,
            "ENTESB", JiraInstance.REDHAT
    );

    private enum JiraInstance { APACHE, REDHAT }

    private final HttpClient httpClient;
    private final Path cacheDir;
    private final String rhToken;

    public JiraFetcher(Path cacheDir) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.cacheDir = cacheDir;
        this.rhToken = resolveRhToken();
    }

    /**
     * Parsed JIRA issue with the fields we care about.
     */
    public record JiraIssue(
            String key,
            String summary,
            String description,
            List<String> fixVersions,
            List<String> components,
            String status,
            String priority,
            String issueType,
            String resolution
    ) {
        /**
         * Format the issue as enriched content for indexing.
         */
        public String toEnrichedContent() {
            StringBuilder sb = new StringBuilder();
            sb.append(key).append(": ").append(summary);
            if (issueType != null) {
                sb.append("\nType: ").append(issueType);
            }
            if (status != null) {
                sb.append("\nStatus: ").append(status);
            }
            if (resolution != null) {
                sb.append("\nResolution: ").append(resolution);
            }
            if (priority != null) {
                sb.append("\nPriority: ").append(priority);
            }
            if (!fixVersions.isEmpty()) {
                sb.append("\nFix Versions: ").append(String.join(", ", fixVersions));
            }
            if (!components.isEmpty()) {
                sb.append("\nComponents: ").append(String.join(", ", components));
            }
            if (description != null && !description.isBlank()) {
                // Truncate very long descriptions to keep chunks manageable
                String desc = description.length() > 2000
                        ? description.substring(0, 2000) + "..."
                        : description;
                sb.append("\n\n").append(desc);
            }
            return sb.toString();
        }
    }

    /**
     * Fetch a JIRA issue, using cache if available.
     * Returns null if the issue cannot be fetched (network error, auth issue, etc.).
     */
    public JiraIssue fetch(String jiraId) {
        try {
            Path cacheFile = cacheDir.resolve(jiraId + ".json");
            String json;

            if (Files.exists(cacheFile) && Files.size(cacheFile) > 0) {
                json = Files.readString(cacheFile);
            } else {
                json = downloadIssue(jiraId);
                if (json == null) return null;
                Files.writeString(cacheFile, json);
            }

            return parseIssue(jiraId, json);
        } catch (Exception e) {
            System.out.printf("  WARN: Failed to fetch JIRA %s: %s%n", jiraId, e.getMessage());
            return null;
        }
    }

    private String downloadIssue(String jiraId) {
        String prefix = jiraId.contains("-") ? jiraId.substring(0, jiraId.indexOf('-')) : null;
        if (prefix == null) return null;

        JiraInstance instance = PREFIX_MAP.get(prefix);
        if (instance == null) {
            System.out.printf("  WARN: Unknown JIRA prefix: %s%n", prefix);
            return null;
        }

        String baseUrl = instance == JiraInstance.APACHE ? APACHE_JIRA_URL : REDHAT_JIRA_URL;
        String url = baseUrl + jiraId + "?fields=" + FIELDS;

        if (instance == JiraInstance.REDHAT && rhToken == null) {
            // No token — skip Red Hat JIRA silently
            return null;
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .GET();

            if (instance == JiraInstance.REDHAT) {
                requestBuilder.header("Authorization", "Bearer " + rhToken);
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else if (response.statusCode() == 404) {
                System.out.printf("  WARN: JIRA %s not found (404)%n", jiraId);
                return null;
            } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                System.out.printf("  WARN: JIRA %s auth failed (%d) — check JIRA_RH_TOKEN%n",
                        jiraId, response.statusCode());
                return null;
            } else {
                System.out.printf("  WARN: JIRA %s returned HTTP %d%n", jiraId, response.statusCode());
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException e) {
            System.out.printf("  WARN: Failed to download JIRA %s: %s%n", jiraId, e.getMessage());
            return null;
        }
    }

    private JiraIssue parseIssue(String jiraId, String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject fields = root.getJSONObject("fields");

            String summary = fields.optString("summary", "");
            String description = fields.isNull("description") ? null : fields.optString("description", null);

            List<String> fixVersions = new ArrayList<>();
            JSONArray fv = fields.optJSONArray("fixVersions");
            if (fv != null) {
                for (int i = 0; i < fv.length(); i++) {
                    fixVersions.add(fv.getJSONObject(i).optString("name", ""));
                }
            }

            List<String> components = new ArrayList<>();
            JSONArray comps = fields.optJSONArray("components");
            if (comps != null) {
                for (int i = 0; i < comps.length(); i++) {
                    components.add(comps.getJSONObject(i).optString("name", ""));
                }
            }

            String status = null;
            if (!fields.isNull("status")) {
                status = fields.getJSONObject("status").optString("name", null);
            }

            String priority = null;
            if (!fields.isNull("priority")) {
                priority = fields.getJSONObject("priority").optString("name", null);
            }

            String issueType = null;
            if (!fields.isNull("issuetype")) {
                issueType = fields.getJSONObject("issuetype").optString("name", null);
            }

            String resolution = null;
            if (!fields.isNull("resolution")) {
                resolution = fields.getJSONObject("resolution").optString("name", null);
            }

            return new JiraIssue(jiraId, summary, description, fixVersions, components,
                    status, priority, issueType, resolution);
        } catch (Exception e) {
            System.out.printf("  WARN: Failed to parse JIRA JSON for %s: %s%n", jiraId, e.getMessage());
            return null;
        }
    }

    private static String resolveRhToken() {
        String token = System.getProperty("jira.rh.token");
        if (token != null && !token.isBlank()) return token;
        token = System.getenv("JIRA_RH_TOKEN");
        if (token != null && !token.isBlank()) return token;
        return null;
    }

    public boolean hasRhToken() {
        return rhToken != null;
    }
}

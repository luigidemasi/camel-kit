package io.github.luigidemasi.camelkit.knowledge.indexer.parser;

import io.github.luigidemasi.camelkit.knowledge.indexer.chunker.SectionChunker;
import io.github.luigidemasi.camelkit.knowledge.indexer.chunker.SectionChunker.Section;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Parses documents using the docling-serve REST API (Docker).
 * Converts any supported format (PDF, HTML, AsciiDoc, Markdown, DOCX, etc.)
 * to Markdown, then splits into sections using SectionChunker.
 *
 * <p>Uses the docling-serve v1 API endpoints:
 * <ul>
 *   <li>{@code POST /v1/convert/file} — for local file conversion (multipart upload)</li>
 *   <li>{@code POST /v1/convert/source} — for URL-based conversion</li>
 * </ul>
 *
 * <p>Requires docling-serve running at the configured URL:
 * <pre>
 *   docker run -p 5001:5001 quay.io/docling-project/docling-serve
 * </pre>
 */
public class DoclingParser {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(
            Long.parseLong(System.getProperty("docling.timeout.minutes", "10")));
    private static final int MAX_RETRIES = Integer.parseInt(
            System.getProperty("docling.retries", "3"));

    private final String doclingUrl;
    private final HttpClient httpClient;
    private final SectionChunker chunker;

    public DoclingParser(String doclingUrl) {
        this.doclingUrl = doclingUrl.endsWith("/")
                ? doclingUrl.substring(0, doclingUrl.length() - 1)
                : doclingUrl;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.chunker = new SectionChunker();
    }

    /**
     * Parse a document file and return sections.
     *
     * @param filePath path to the document file
     * @return list of sections extracted from the document
     */
    public List<Section> parse(Path filePath) throws IOException, InterruptedException {
        String markdown = toMarkdown(filePath);
        return chunkMarkdown(markdown);
    }

    /**
     * Parse a document from a URL and return sections.
     *
     * @param sourceUrl URL of the document to parse
     * @return list of sections extracted from the document
     */
    public List<Section> parseUrl(String sourceUrl) throws IOException, InterruptedException {
        String markdown = convertUrlToMarkdown(sourceUrl);
        return chunkMarkdown(markdown);
    }

    /**
     * Convert a local file to Markdown via docling-serve without chunking.
     * Retries up to {@link #MAX_RETRIES} times on failure before giving up.
     *
     * @param filePath path to the document file
     * @return the Markdown content
     */
    public String toMarkdown(Path filePath) throws IOException, InterruptedException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return convertToMarkdown(filePath);
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    System.out.printf("  WARN: Docling conversion failed (attempt %d/%d) for %s: %s — retrying...%n",
                            attempt, MAX_RETRIES, filePath.toAbsolutePath(), e.getMessage());
                    Thread.sleep(2000L * attempt); // backoff: 2s, 4s
                }
            }
        }
        System.out.printf("  ERROR: Docling conversion failed after %d attempts for: %s — %s%n",
                MAX_RETRIES, filePath.toAbsolutePath(), lastException.getMessage());
        throw lastException;
    }

    /**
     * Split a Markdown string into sections using SectionChunker.
     * Use this after {@link #toMarkdown(Path)} when you need to cache the Markdown first.
     *
     * @param markdown the Markdown content
     * @return list of sections
     */
    public List<Section> chunkMarkdown(String markdown) {
        return chunker.chunk(markdown);
    }

    /**
     * Convert a local file to Markdown via docling-serve multipart upload.
     * Uses the v1 API endpoint: POST /v1/convert/file
     *
     * <p>The file is uploaded as multipart/form-data with field name "files".
     */
    private String convertToMarkdown(Path filePath) throws IOException, InterruptedException {
        String boundary = "----FormBoundary" + System.currentTimeMillis();
        byte[] fileBytes = Files.readAllBytes(filePath);
        String fileName = filePath.getFileName().toString();

        byte[] body = buildMultipartBody(boundary, fileName, fileBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(doclingUrl + "/v1/convert/file"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Docling returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return extractMarkdownFromResponse(response.body());
    }

    /**
     * Convert a URL-based document to Markdown via docling-serve.
     * Uses the v1 API endpoint: POST /v1/convert/source
     *
     * <p>Request body format:
     * <pre>
     * {"sources": [{"kind": "http", "url": "..."}]}
     * </pre>
     */
    private String convertUrlToMarkdown(String sourceUrl) throws IOException, InterruptedException {
        String jsonBody = "{\"sources\":[{\"kind\":\"http\",\"url\":\""
                + escapeJson(sourceUrl) + "\"}]}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(doclingUrl + "/v1/convert/source"))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Docling returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return extractMarkdownFromResponse(response.body());
    }

    /**
     * Build a multipart/form-data body for file upload.
     * The field name is "files" to match the docling-serve v1 API.
     */
    private byte[] buildMultipartBody(String boundary, String fileName, byte[] fileBytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"files\"; filename=\"")
                .append(fileName).append("\"\r\n");
        sb.append("Content-Type: application/octet-stream\r\n\r\n");

        byte[] header = sb.toString().getBytes();
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes();

        byte[] body = new byte[header.length + fileBytes.length + footer.length];
        System.arraycopy(header, 0, body, 0, header.length);
        System.arraycopy(fileBytes, 0, body, header.length, fileBytes.length);
        System.arraycopy(footer, 0, body, header.length + fileBytes.length, footer.length);
        return body;
    }

    /**
     * Extract the markdown content from docling-serve v1 JSON response.
     *
     * <p>The v1 response structure is:
     * <pre>
     * {
     *   "document": {
     *     "md_content": "...",
     *     "filename": "..."
     *   },
     *   "status": "...",
     *   "processing_time": 1.23
     * }
     * </pre>
     *
     * <p>Uses simple string parsing to avoid requiring a JSON library dependency.
     */
    String extractMarkdownFromResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
            return "";
        }

        // Look for "md_content" field in the JSON response
        if (jsonResponse.startsWith("{")) {
            String mdContent = extractJsonStringField(jsonResponse, "md_content");
            if (mdContent != null) {
                return mdContent;
            }
        }

        // Fallback: treat the whole response as markdown
        return jsonResponse;
    }

    /**
     * Extract a string field value from a JSON object using simple parsing.
     * Handles escaped characters in the value (newlines, quotes, backslashes, unicode).
     *
     * @param json the JSON string
     * @param fieldName the field name to extract
     * @return the unescaped field value, or null if not found
     */
    private String extractJsonStringField(String json, String fieldName) {
        String searchKey = "\"" + fieldName + "\"";
        int keyStart = json.indexOf(searchKey);
        if (keyStart < 0) {
            return null;
        }

        // Find the colon after the key
        int colonIdx = json.indexOf(':', keyStart + searchKey.length());
        if (colonIdx < 0) {
            return null;
        }

        // Skip whitespace after colon
        int valueStart = colonIdx + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length()) {
            return null;
        }

        // Check for null value
        if (json.startsWith("null", valueStart)) {
            return null;
        }

        // Expect a quoted string
        if (json.charAt(valueStart) != '"') {
            return null;
        }

        // Parse the JSON string value, handling escape sequences
        StringBuilder result = new StringBuilder();
        int i = valueStart + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                // End of string
                return result.toString();
            } else if (c == '\\') {
                // Escape sequence
                i++;
                if (i >= json.length()) {
                    break;
                }
                char escaped = json.charAt(i);
                switch (escaped) {
                    case 'n' -> result.append('\n');
                    case 't' -> result.append('\t');
                    case 'r' -> result.append('\r');
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case '/' -> result.append('/');
                    case 'u' -> {
                        // Unicode escape sequence
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            result.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                    }
                    default -> {
                        result.append('\\');
                        result.append(escaped);
                    }
                }
            } else {
                result.append(c);
            }
            i++;
        }

        // If we get here, the string was not properly terminated
        return result.toString();
    }

    /**
     * Escape a string for safe inclusion in a JSON string value.
     */
    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

package io.github.luigidemasi.camelkit.knowledge.indexer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Fetches documents from URLs to local temporary files for parsing.
 * Supports HTTP/HTTPS URLs and handles redirects.
 */
public class DocumentFetcher {

    private final HttpClient httpClient;
    private final Path cacheDir;

    public DocumentFetcher(Path cacheDir) throws IOException {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.cacheDir = cacheDir;
        Files.createDirectories(cacheDir);
    }

    /**
     * Fetch a document from a URL and save it to the cache directory.
     * Returns the path to the downloaded file.
     *
     * @param url the URL to fetch
     * @param fileName the local filename to save as
     * @return path to the downloaded file
     */
    public Path fetch(String url, String fileName) throws IOException, InterruptedException {
        Path target = cacheDir.resolve(fileName);

        if (Files.exists(target)) {
            System.out.printf("  Cache hit: %s%n", fileName);
            return target;
        }

        System.out.printf("  Fetching: %s%n", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " fetching " + url);
        }

        try (InputStream is = response.body()) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return target;
    }

    /**
     * Fetch raw text content from a URL (for AsciiDoc, Markdown, YAML).
     *
     * @param url the URL to fetch
     * @return the text content
     */
    public String fetchText(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " fetching " + url);
        }

        return response.body();
    }
}

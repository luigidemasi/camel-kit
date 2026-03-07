package io.github.luigidemasi.camelkit.knowledge.indexer.domain;

import io.github.luigidemasi.camelkit.knowledge.indexer.DocumentFetcher;
import io.github.luigidemasi.camelkit.knowledge.indexer.chunker.SectionChunker.Section;
import io.github.luigidemasi.camelkit.knowledge.indexer.parser.DoclingParser;
import io.github.luigidemasi.camelkit.knowledge.schema.DomainMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Red Hat Build of Apache Camel documentation domain.
 *
 * Sources:
 * - Product guides (HTML) downloaded from docs.redhat.com for each supported version
 * - Knowledge base articles (HTML) from local resources directory
 *
 * HTML guides are fetched from docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/
 * and cached locally. Converted Markdown (.md) is cached alongside the HTML files.
 * On subsequent runs, cached .md files are used directly, skipping Docling.
 *
 * Docling conversions run in parallel (default 4 threads) to speed up processing.
 */
public class RhBuildCamelDomain implements DocumentDomain {

    static final List<String> VERSIONS = List.of("4.0", "4.4", "4.8", "4.10", "4.14");

    private static final String DOCS_BASE_URL =
            "https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/";

    private static final int DOCLING_PARALLELISM = Integer.parseInt(
            System.getProperty("docling.parallelism", "4"));

    private static final Pattern VERSION_PATTERN = Pattern.compile("\\b(4\\.(?:0|4|8|10|14))\\b");

    private static final Map<String, List<GuideInfo>> GUIDE_MAP = buildGuideMap();

    private static final List<KbArticle> KB_ARTICLES = List.of(
            new KbArticle("7021827", "release-schedule", "Release Schedule"),
            new KbArticle("7036995", "component-details", "Component Details"),
            new KbArticle("7037134", "supported-configurations", "Supported Configurations"),
            new KbArticle("6970899", "spring-boot-supported-configs", "Spring Boot Supported Configs"),
            new KbArticle("6507531", "quarkus-supported-configs", "Quarkus Supported Configs")
    );

    private final DocumentFetcher fetcher;
    private final DoclingParser doclingParser;
    private final Path cacheDir;
    private final Path resourcesDir;

    record GuideInfo(String slug, String shortName) {}

    record KbArticle(String id, String shortName, String title) {}

    /** Holds a converted document ready for chunking. */
    private record ConvertedDoc(
            String version,
            String shortName,
            String docType,
            String markdown,
            boolean isKbArticle,
            String kbArticleId
    ) {}

    public RhBuildCamelDomain(Path cacheDir, Path resourcesDir, String doclingUrl) throws IOException {
        this.fetcher = new DocumentFetcher(cacheDir);
        this.doclingParser = new DoclingParser(doclingUrl);
        this.cacheDir = cacheDir;
        this.resourcesDir = resourcesDir;
    }

    @Override
    public DomainMetadata metadata() {
        return new DomainMetadata(
                "rh_build_camel",
                "camel_rh_build",
                "Red Hat Build of Apache Camel documentation — support matrix, extension configuration, " +
                        "getting started, migration, release notes, supported configurations",
                true,
                true
        );
    }

    @Override
    public List<DocumentChunk> buildChunks() throws IOException {
        // Phase 1: Convert all documents to markdown (parallel, with caching)
        List<ConvertedDoc> convertedDocs = convertAllDocuments();

        // Phase 2: Chunk and build DocumentChunk objects (fast, sequential)
        List<DocumentChunk> chunks = new ArrayList<>();

        for (ConvertedDoc doc : convertedDocs) {
            List<Section> sections = doclingParser.chunkMarkdown(doc.markdown());

            for (Section section : sections) {
                if (doc.isKbArticle()) {
                    String extractedVersion = extractVersionFromHeading(section.title());
                    String id = "rh-build-camel-kb-" + doc.kbArticleId() + "-" +
                            section.title().toLowerCase().replaceAll("[^a-z0-9]+", "-");
                    String component = extractComponentName(section.title());

                    chunks.add(new DocumentChunk(
                            id, "red-hat-build-camel", doc.shortName(),
                            extractedVersion, null, component,
                            section.title(), section.content()
                    ));
                } else {
                    String id = "rh-build-camel-" + doc.version() + "-" + doc.shortName() + "-" +
                            section.title().toLowerCase().replaceAll("[^a-z0-9]+", "-");
                    String component = extractComponentName(section.title());

                    chunks.add(new DocumentChunk(
                            id, "red-hat-build-camel", doc.shortName(),
                            doc.version(), null, component,
                            section.title(), section.content()
                    ));
                }
            }

            String label = doc.isKbArticle()
                    ? "KB " + doc.kbArticleId() + " (" + doc.shortName() + ")"
                    : doc.version() + "/" + doc.shortName();
            System.out.printf("  Chunked %s: %d sections%n", label, sections.size());
        }

        return chunks;
    }

    /**
     * Convert all HTML guides and KB articles to markdown, using parallel Docling calls.
     * Cache hits are served immediately; cache misses are submitted to a thread pool.
     *
     * Guides are downloaded from docs.redhat.com as HTML-single pages.
     * KB articles are read from the local resources directory.
     */
    private List<ConvertedDoc> convertAllDocuments() throws IOException {
        ConcurrentLinkedQueue<ConvertedDoc> results = new ConcurrentLinkedQueue<>();
        AtomicInteger cacheHits = new AtomicInteger();
        AtomicInteger conversions = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(DOCLING_PARALLELISM);

        // Submit HTML guide conversions (downloaded from docs.redhat.com)
        // HTML files are persisted in src/main/resources so they survive mvn clean
        for (String version : VERSIONS) {
            List<GuideInfo> guides = GUIDE_MAP.get(version);
            if (guides == null) continue;

            Path versionResourceDir = resourcesDir.resolve("rh-build-camel/" + version);
            Files.createDirectories(versionResourceDir);
            Path versionCacheDir = cacheDir.resolve("rh-build-camel/" + version);
            Files.createDirectories(versionCacheDir);

            for (GuideInfo guide : guides) {
                Path htmlFile = versionResourceDir.resolve(guide.shortName() + ".html");
                Path mdFile = versionCacheDir.resolve(guide.shortName() + ".md");

                if (Files.exists(mdFile) && Files.size(mdFile) > 0) {
                    // Cache hit — read synchronously (fast)
                    String markdown = Files.readString(mdFile);
                    results.add(new ConvertedDoc(version, guide.shortName(), guide.shortName(), markdown, false, null));
                    cacheHits.incrementAndGet();
                    System.out.printf("  Cache hit: %s/%s.md%n", version, guide.shortName());
                } else {
                    // Cache miss — download HTML if needed, then convert via Docling
                    String htmlUrl = DOCS_BASE_URL + version + "/html-single/" + guide.slug() + "/index";
                    executor.submit(() -> {
                        try {
                            // Download HTML to resources dir if not already present
                            if (!Files.exists(htmlFile) || Files.size(htmlFile) == 0) {
                                System.out.printf("  Downloading: %s%n", htmlUrl);
                                Path downloaded = fetcher.fetch(htmlUrl,
                                        "rh-build-camel/" + version + "/" + guide.shortName() + ".html");
                                Files.copy(downloaded, htmlFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            } else {
                                System.out.printf("  HTML cache hit: %s/%s.html%n", version, guide.shortName());
                            }
                            System.out.printf("  Sending to Docling: %s%n", htmlFile.getFileName());
                            String markdown = doclingParser.toMarkdown(htmlFile);
                            Files.writeString(mdFile, markdown);
                            results.add(new ConvertedDoc(version, guide.shortName(), guide.shortName(), markdown, false, null));
                            int done = conversions.incrementAndGet();
                            System.out.printf("  Converted %s/%s (%d done)%n", version, guide.shortName(), done);
                        } catch (Exception e) {
                            System.out.printf("  ERROR: Failed to convert %s/%s: %s (skipping)%n",
                                    version, guide.shortName(), e.getMessage());
                        }
                    });
                }
            }
        }

        // Submit KB article conversions (from local resources directory)
        Path kbCacheDir = cacheDir.resolve("rh-build-camel/kb-articles");
        Files.createDirectories(kbCacheDir);

        for (KbArticle article : KB_ARTICLES) {
            Path htmlFile = resourcesDir.resolve("rh-build-camel/kb-articles/" + article.id() + ".html");
            Path mdFile = kbCacheDir.resolve(article.id() + ".md");

            if (Files.exists(mdFile) && Files.size(mdFile) > 0) {
                String markdown = Files.readString(mdFile);
                results.add(new ConvertedDoc(null, article.shortName(), article.shortName(), markdown, true, article.id()));
                cacheHits.incrementAndGet();
                System.out.printf("  Cache hit: kb-articles/%s.md%n", article.id());
            } else if (Files.exists(htmlFile)) {
                executor.submit(() -> {
                    try {
                        System.out.printf("  Sending to Docling: %s (%s)%n", htmlFile.getFileName(), htmlFile);
                        String markdown = doclingParser.toMarkdown(htmlFile);
                        Files.writeString(mdFile, markdown);
                        results.add(new ConvertedDoc(null, article.shortName(), article.shortName(), markdown, true, article.id()));
                        int done = conversions.incrementAndGet();
                        System.out.printf("  Converted kb-articles/%s.html (%d done)%n", article.id(), done);
                    } catch (Exception e) {
                        System.out.printf("  ERROR: Failed to convert KB %s: %s (skipping)%n",
                                article.id(), e.getMessage());
                    }
                });
            } else {
                System.out.printf("  WARN: KB article not found: %s (skipping)%n", htmlFile);
            }
        }

        // Wait for all conversions to complete
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                executor.shutdownNow();
                throw new IOException("Docling conversions timed out after 30 minutes");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for Docling conversions", e);
        }

        System.out.printf("  Conversion complete: %d cache hits, %d converted via Docling%n",
                cacheHits.get(), conversions.get());

        return new ArrayList<>(results);
    }

    // Package-private accessors for testing

    List<String> getVersions() {
        return VERSIONS;
    }

    List<GuideInfo> getGuidesForVersion(String version) {
        return GUIDE_MAP.getOrDefault(version, List.of());
    }

    String extractVersionFromHeading(String heading) {
        if (heading == null) return null;
        Matcher matcher = VERSION_PATTERN.matcher(heading);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extract component name from section title if it looks like a component reference.
     * e.g., "camel-http4" -> "http4", "camel-cxf" -> "cxf"
     */
    private String extractComponentName(String title) {
        String lower = title.toLowerCase().trim();
        if (lower.startsWith("camel-")) {
            return lower.substring("camel-".length()).replaceAll("[^a-z0-9-]", "");
        }
        if (lower.matches("[a-z][a-z0-9-]+") && !lower.contains(" ")) {
            return lower;
        }
        return null;
    }

    private static Map<String, List<GuideInfo>> buildGuideMap() {
        // Common guides present in all versions
        List<GuideInfo> common = List.of(
                new GuideInfo("getting_started_with_red_hat_build_of_apache_camel_for_quarkus", "getting-started-quarkus"),
                new GuideInfo("getting_started_with_red_hat_build_of_apache_camel_for_spring_boot", "getting-started-spring-boot"),
                new GuideInfo("developing_applications_with_red_hat_build_of_apache_camel_for_quarkus", "developing-quarkus"),
                new GuideInfo("red_hat_build_of_apache_camel_for_quarkus_reference", "quarkus-reference"),
                new GuideInfo("red_hat_build_of_apache_camel_for_spring_boot_reference", "spring-boot-reference"),
                new GuideInfo("hawtio_diagnostic_console_guide", "hawtio"),
                new GuideInfo("migrating_fuse_7_applications_to_red_hat_build_of_apache_camel_for_quarkus", "fuse7-migration"),
                new GuideInfo("migrating_to_red_hat_build_of_apache_camel_for_spring_boot", "spring-boot-migration"),
                new GuideInfo("release_notes_for_red_hat_build_of_apache_camel_for_quarkus", "release-notes-quarkus"),
                new GuideInfo("release_notes_for_red_hat_build_of_apache_camel_for_spring_boot", "release-notes-spring-boot")
        );

        Map<String, List<GuideInfo>> map = new LinkedHashMap<>();

        // 4.0: common + tooling_guide + release_notes_for_hawtio_diagnostic_console_guide (12 total)
        {
            List<GuideInfo> guides = new ArrayList<>(common);
            guides.add(new GuideInfo("tooling_guide", "tooling"));
            guides.add(new GuideInfo("release_notes_for_hawtio_diagnostic_console_guide", "release-notes-hawtio"));
            map.put("4.0", List.copyOf(guides));
        }

        // 4.4: common + tooling + hawtio-rn + kaoto + camel-k-migration (14 total)
        {
            List<GuideInfo> guides = new ArrayList<>(common);
            guides.add(new GuideInfo("tooling_guide_for_red_hat_build_of_apache_camel", "tooling"));
            guides.add(new GuideInfo("release_notes_for_hawtio_diagnostic_console_guide", "release-notes-hawtio"));
            guides.add(new GuideInfo("kaoto", "kaoto"));
            guides.add(new GuideInfo("migration_guide_camel_k_to_camel_extensions_for_quarkus", "camel-k-migration"));
            map.put("4.4", List.copyOf(guides));
        }

        // 4.8: common + 8 version-specific (18 total)
        {
            List<GuideInfo> guides = new ArrayList<>(common);
            guides.add(new GuideInfo("tooling_guide_for_red_hat_build_of_apache_camel", "tooling"));
            guides.add(new GuideInfo("release_notes_for_hawtio_diagnostic_console", "release-notes-hawtio"));
            guides.add(new GuideInfo("kaoto", "kaoto"));
            guides.add(new GuideInfo("migrating_from_camel_k_to_red_hat_build_of_apache_camel_for_quarkus", "camel-k-migration"));
            guides.add(new GuideInfo("migrating_apache_camel", "camel-migration"));
            guides.add(new GuideInfo("migrating_camel_quarkus_projects", "quarkus-migration"));
            guides.add(new GuideInfo("kamelets_reference_for_red_hat_build_of_apache_camel_for_quarkus", "kamelets-reference"));
            guides.add(new GuideInfo("quarkus_cxf_security_guide_for_red_hat_build_of_apache_camel", "cxf-security"));
            map.put("4.8", List.copyOf(guides));
        }

        // 4.10: common + 8 version-specific (18 total)
        {
            List<GuideInfo> guides = new ArrayList<>(common);
            guides.add(new GuideInfo("tooling_guide_for_red_hat_build_of_apache_camel", "tooling"));
            guides.add(new GuideInfo("release_notes_for_hawtio_diagnostic_console", "release-notes-hawtio"));
            guides.add(new GuideInfo("kaoto_camel_designer", "kaoto"));
            guides.add(new GuideInfo("migrating_from_camel_k_to_red_hat_build_of_apache_camel_for_quarkus", "camel-k-migration"));
            guides.add(new GuideInfo("migrating_apache_camel", "camel-migration"));
            guides.add(new GuideInfo("migrating_camel_quarkus_projects", "quarkus-migration"));
            guides.add(new GuideInfo("kamelets_reference_for_red_hat_build_of_apache_camel_for_quarkus", "kamelets-reference"));
            guides.add(new GuideInfo("quarkus_cxf_security_guide_for_red_hat_build_of_apache_camel", "cxf-security"));
            map.put("4.10", List.copyOf(guides));
        }

        // 4.14: common + 9 version-specific (19 total)
        {
            List<GuideInfo> guides = new ArrayList<>(common);
            guides.add(new GuideInfo("tooling_guide_for_red_hat_build_of_apache_camel", "tooling"));
            guides.add(new GuideInfo("release_notes_for_hawtio_diagnostic_console", "release-notes-hawtio"));
            guides.add(new GuideInfo("kaoto_camel_designer", "kaoto"));
            guides.add(new GuideInfo("migrating_from_camel_k_to_red_hat_build_of_apache_camel_for_quarkus", "camel-k-migration"));
            guides.add(new GuideInfo("migrating_apache_camel", "camel-migration"));
            guides.add(new GuideInfo("migrating_camel_quarkus_projects", "quarkus-migration"));
            guides.add(new GuideInfo("kamelets_reference_for_red_hat_build_of_apache_camel_for_quarkus", "kamelets-reference"));
            guides.add(new GuideInfo("quarkus_cxf_for_red_hat_build_of_apache_camel", "cxf"));
            guides.add(new GuideInfo("camel_development_guide_for_red_hat_build_of_apache_camel_for_spring_boot", "development-spring-boot"));
            map.put("4.14", List.copyOf(guides));
        }

        return Map.copyOf(map);
    }
}

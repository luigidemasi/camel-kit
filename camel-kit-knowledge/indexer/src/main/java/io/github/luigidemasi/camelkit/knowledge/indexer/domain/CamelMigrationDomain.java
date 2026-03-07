package io.github.luigidemasi.camelkit.knowledge.indexer.domain;

import io.github.luigidemasi.camelkit.knowledge.indexer.DocumentFetcher;
import io.github.luigidemasi.camelkit.knowledge.indexer.chunker.RecipeChunker;
import io.github.luigidemasi.camelkit.knowledge.indexer.chunker.RecipeChunker.Recipe;
import io.github.luigidemasi.camelkit.knowledge.indexer.chunker.SectionChunker.Section;
import io.github.luigidemasi.camelkit.knowledge.indexer.parser.DoclingParser;
import io.github.luigidemasi.camelkit.knowledge.schema.DomainMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Apache Camel migration documentation domain.
 *
 * Sources:
 * - Camel 2.x to 3.0 migration guide (AsciiDoc from GitHub)
 * - Camel 3.x to 4.0 migration guide (AsciiDoc from GitHub)
 * - Camel 4.x minor upgrade guides (AsciiDoc from GitHub)
 * - OpenRewrite upgrade recipes (YAML from jboss-fuse/camel-upgrade-recipes)
 *
 * AsciiDoc files are converted to Markdown via Docling (docling-serve),
 * then split into sections by the SectionChunker embedded in DoclingParser.
 * OpenRewrite YAML recipes are parsed by RecipeChunker.
 */
public class CamelMigrationDomain implements DocumentDomain {

    private static final String GITHUB_RAW_BASE =
            "https://raw.githubusercontent.com/apache/camel/main/docs/user-manual/modules/ROOT/pages/";

    private static final String[] GUIDE_FILES = {
            "camel-3-migration-guide.adoc",
            "camel-4-migration-guide.adoc",
            "camel-4x-upgrade-guide.adoc",
    };

    private static final String RECIPES_RAW_BASE =
            "https://raw.githubusercontent.com/jboss-fuse/camel-upgrade-recipes/camel-4.14.4-branch/" +
            "camel-upgrade-recipes/src/main/resources/META-INF/rewrite/";

    private static final String[] RECIPE_FILES = {
            "4.0.yaml",
            "4.4.yaml",
            "4.5.yaml",
            "4.6.yaml",
            "4.7.yaml",
            "4.8.yaml",
            "4.9.yaml",
            "4.10.yaml",
            "4.11.yaml",
            "4.12.yaml",
            "4.13.yaml",
            "4.14.yaml",
    };

    private final DocumentFetcher fetcher;
    private final DoclingParser doclingParser;
    private final RecipeChunker recipeChunker;
    private final Path cacheDir;

    public CamelMigrationDomain(Path cacheDir, String doclingUrl) throws IOException {
        this.fetcher = new DocumentFetcher(cacheDir);
        this.doclingParser = new DoclingParser(doclingUrl);
        this.recipeChunker = new RecipeChunker();
        this.cacheDir = cacheDir;
    }

    @Override
    public DomainMetadata metadata() {
        return DomainMetadata.migration(
                "camel_migration",
                "camel_migration",
                "Search Apache Camel migration documentation and OpenRewrite upgrade recipes. " +
                "Contains component renames, EIP changes, data format updates, " +
                "platform migration guides, and automated refactoring recipes."
        );
    }

    @Override
    public List<DocumentChunk> buildChunks() throws IOException {
        List<DocumentChunk> chunks = new ArrayList<>();

        // 1. Process migration guide AsciiDoc files via Docling
        for (String guideFile : GUIDE_FILES) {
            try {
                String mdFileName = guideFile.replace(".adoc", ".md");
                Path mdFile = cacheDir.resolve(mdFileName);

                String markdown;

                if (Files.exists(mdFile) && Files.size(mdFile) > 0) {
                    markdown = Files.readString(mdFile);
                    System.out.printf("  Cache hit: %s%n", mdFileName);
                } else {
                    Path localFile = fetcher.fetch(GITHUB_RAW_BASE + guideFile, guideFile);
                    System.out.printf("  Sending to Docling: %s (%s)%n", localFile.getFileName(), localFile);
                    markdown = doclingParser.toMarkdown(localFile);
                    Files.writeString(mdFile, markdown);
                }

                List<Section> sections = doclingParser.chunkMarkdown(markdown);

                String versionRange = guideFile.contains("camel-3") ? "2.x" :
                                      guideFile.contains("camel-4-migration") ? "3.x" : "4.x";
                String targetVersion = guideFile.contains("camel-3") ? "3.x" : "4.x";

                for (Section section : sections) {
                    String id = "apache-camel-" + guideFile.replace(".adoc", "") + "-" +
                                section.title().toLowerCase().replaceAll("[^a-z0-9]+", "-");
                    String component = extractComponentName(section.title());

                    chunks.add(new DocumentChunk(
                            id,
                            "apache-camel",
                            classifyDocType(section.title()),
                            versionRange,
                            targetVersion,
                            component,
                            section.title(),
                            section.content()
                    ));
                }

                System.out.printf("  Processed %s: %d sections%n", guideFile, sections.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching " + guideFile, e);
            } catch (IOException e) {
                System.out.printf("  ERROR: Failed to process %s: %s (skipping)%n",
                        guideFile, e.getMessage());
            }
        }

        // 2. Process OpenRewrite upgrade recipe YAML files
        for (String recipeFile : RECIPE_FILES) {
            try {
                String yamlContent = fetcher.fetchText(RECIPES_RAW_BASE + recipeFile);
                List<Recipe> recipes = recipeChunker.chunk(yamlContent);

                String targetVersion = recipeFile.replace(".yaml", "");

                for (Recipe recipe : recipes) {
                    String id = "openrewrite-" + targetVersion + "-" +
                                recipe.recipeType().toLowerCase() + "-" +
                                (recipe.oldName() != null
                                        ? recipe.oldName().toLowerCase().replaceAll("[^a-z0-9]+", "-")
                                        : "unknown");

                    String title = recipe.recipeType() + ": " +
                                   (recipe.oldName() != null ? recipe.oldName() : "unknown") +
                                   " → " +
                                   (recipe.newName() != null ? recipe.newName() : "unknown");

                    chunks.add(new DocumentChunk(
                            id,
                            "openrewrite",
                            "recipe",
                            null,
                            targetVersion,
                            recipe.componentName(),
                            title,
                            recipe.rawYaml()
                    ));
                }

                System.out.printf("  Processed recipes/%s: %d recipes%n", recipeFile, recipes.size());
            } catch (IOException e) {
                System.out.printf("  ERROR: Failed to fetch recipe %s: %s (skipping)%n",
                        recipeFile, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching recipe " + recipeFile, e);
            }
        }

        return chunks;
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
        // Single-word component names
        if (lower.matches("[a-z][a-z0-9-]+") && !lower.contains(" ")) {
            return lower;
        }
        return null;
    }

    private String classifyDocType(String title) {
        String lower = title.toLowerCase();
        if (lower.contains("component") || lower.startsWith("camel-")) {
            return "component-migration";
        }
        if (lower.contains("eip") || lower.contains("pattern")) {
            return "eip-migration";
        }
        if (lower.contains("data format") || lower.contains("dataformat")) {
            return "dataformat-migration";
        }
        return "platform-change";
    }
}

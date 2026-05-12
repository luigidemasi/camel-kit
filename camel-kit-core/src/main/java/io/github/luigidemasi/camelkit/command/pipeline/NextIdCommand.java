package io.github.luigidemasi.camelkit.command.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "nextId", description = "Generate next sequential pipeline ID and create directory")
public class NextIdCommand implements Runnable {

    private static final Pattern ID_PATTERN = Pattern.compile("^(\\d{3})-.*$");
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    @Parameters(index = "0",
                description = "Pipeline slug (lowercase alphanumeric with hyphens, e.g. 'order-processing')")
    String slug;

    Path baseDir = Path.of(".");

    @Override
    public void run() {
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            System.err.println("Invalid slug: '" + slug
                               + "'. Must be lowercase alphanumeric with hyphens (e.g. 'order-processing').");
            return;
        }

        Path docsDir = baseDir.resolve("docs/camel-kit");
        int maxId = 0;

        if (Files.isDirectory(docsDir)) {
            try (Stream<Path> entries = Files.list(docsDir)) {
                maxId = entries
                        .filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .map(ID_PATTERN::matcher)
                        .filter(Matcher::matches)
                        .mapToInt(m -> Integer.parseInt(m.group(1)))
                        .max()
                        .orElse(0);
            } catch (IOException e) {
                System.err.println("Error scanning docs/camel-kit: " + e.getMessage());
                return;
            }
        }

        String pipelineId = String.format(Locale.ROOT, "%03d-%s", maxId + 1, slug);
        Path pipelineDir = docsDir.resolve(pipelineId);

        try {
            Files.createDirectories(pipelineDir);
        } catch (IOException e) {
            System.err.println("Error creating directory: " + e.getMessage());
            return;
        }

        System.out.println(pipelineId);
    }
}

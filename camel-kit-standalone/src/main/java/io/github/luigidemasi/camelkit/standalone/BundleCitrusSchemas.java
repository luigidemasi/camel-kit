package io.github.luigidemasi.camelkit.standalone;

import io.github.luigidemasi.camelkit.catalog.CitrusSchemaDownloader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Build-time tool that downloads Citrus schemas and copies them into the
 * standalone JAR's resource directory. Invoked by exec-maven-plugin during
 * the {@code generate-resources} phase.
 * <p>
 * Usage: {@code java BundleCitrusSchemas <version> <outputDir>}
 * <p>
 * Example: {@code java BundleCitrusSchemas 4.9.2 target/classes/citrus-schemas}
 */
public class BundleCitrusSchemas {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: BundleCitrusSchemas <version> <outputDir>");
            System.exit(1);
        }

        String version = args[0];
        Path outputDir = Path.of(args[1]);

        // Download to a temporary cache directory
        Path tempCache = Files.createTempDirectory("citrus-cache");
        try {
            CitrusSchemaDownloader downloader = new CitrusSchemaDownloader(tempCache);
            downloader.fetchCitrusSchemas(version, true, System.out::println);

            // Copy fetched schemas to output directory
            Path source = downloader.getCitrusSchemasDir(version);
            Path target = outputDir.resolve(version);
            Files.createDirectories(target);

            try (var stream = Files.walk(source)) {
                for (Path p : stream.toList()) {
                    Path dest = target.resolve(source.relativize(p));
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            // Count files for verification
            long count;
            try (var stream = Files.walk(target)) {
                count = stream.filter(p -> !Files.isDirectory(p)).count();
            }
            System.out.println("Bundled " + count + " Citrus schema files for version " + version);

        } finally {
            // Clean up temp directory
            try (var stream = Files.walk(tempCache)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            }
        }
    }
}

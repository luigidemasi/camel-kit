package io.github.luigidemasi.camelkit.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for reading template files from resources or filesystem.
 */
public final class TemplateUtils {

    private TemplateUtils() {
        // Utility class
    }

    /**
     * Read a template file from bundled resources. Templates are bundled via JBang //FILES directive.
     *
     * @param  templatePath the path to the template (e.g., "templates/constitution.md")
     * @return              the template content
     * @throws IOException  if the template cannot be read
     */
    public static String readTemplate(String templatePath) throws IOException {
        // Try reading from classpath (bundled via //FILES)
        try (InputStream is = TemplateUtils.class.getClassLoader().getResourceAsStream(templatePath)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        // Fallback: try reading from filesystem relative to current directory
        Path filePath = Path.of(templatePath);
        if (Files.exists(filePath)) {
            return Files.readString(filePath);
        }

        throw new TemplateNotFoundException(templatePath);
    }

    /**
     * Read a template file from bundled resources, returning {@code null} instead of throwing if the template cannot be
     * found.
     *
     * @param  templatePath the path to the template (e.g., "templates/bob/gates/camel-plan.md")
     * @return              the template content, or {@code null} if the template cannot be loaded
     */
    public static String readTemplateOrNull(String templatePath) {
        try {
            return readTemplate(templatePath);
        } catch (TemplateNotFoundException e) {
            return null;
        } catch (IOException e) {
            System.err.println(
                    "[WARN] Unexpected I/O error reading template '" + templatePath + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Read a resource file as an array of lines.
     *
     * @param  resourcePath the path to the resource (e.g., "art/camelLines.txt")
     * @return              array of lines from the file
     * @throws IOException  if the resource cannot be read
     */
    public static String[] readLines(String resourcePath) throws IOException {
        try (InputStream is = TemplateUtils.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
                return lines.toArray(new String[0]);
            }
        }

        // Fallback: try reading from filesystem
        Path filePath = Path.of(resourcePath);
        if (Files.exists(filePath)) {
            return Files.readAllLines(filePath).toArray(new String[0]);
        }

        throw new IOException("Resource not found: " + resourcePath);
    }
}

package io.github.luigidemasi.camelkit.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
     * List immediate files under a bundled template directory.
     *
     * @param  templateDirPath directory path, e.g. {@code templates/traits/claude/camel-execute}
     * @param  suffix          optional filename suffix filter, e.g. {@code .append.md}
     * @return                 sorted matching file names, without the directory prefix
     * @throws IOException     if a matching directory exists but cannot be read
     */
    public static List<String> listTemplateFiles(String templateDirPath, String suffix) throws IOException {
        String normalizedDir = normalizeDirectory(templateDirPath);
        Set<String> names = new TreeSet<>();
        Set<String> scannedJarUrls = new HashSet<>();

        collectFromFilesystem(Path.of(normalizedDir), suffix, names);
        collectFromClassLoader(normalizedDir, suffix, names, scannedJarUrls);
        collectFromClassPath(normalizedDir, suffix, names, scannedJarUrls);

        return List.copyOf(names);
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

    private static String normalizeDirectory(String templateDirPath) {
        String normalized = templateDirPath.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static void collectFromFilesystem(Path directory, String suffix, Set<String> names) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> suffix == null || name.endsWith(suffix))
                    .forEach(names::add);
        }
    }

    private static void collectFromClassLoader(
            String directory, String suffix, Set<String> names, Set<String> scannedJarUrls)
            throws IOException {
        ClassLoader loader = TemplateUtils.class.getClassLoader();
        Enumeration<URL> urls = loader.getResources(directory);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            if ("file".equals(url.getProtocol())) {
                collectFromFilesystem(toPath(url), suffix, names);
            } else if ("jar".equals(url.getProtocol())) {
                collectFromJarUrl(url, directory, suffix, names, scannedJarUrls);
            }
        }
    }

    private static void collectFromClassPath(
            String directory, String suffix, Set<String> names, Set<String> scannedJarUrls)
            throws IOException {
        String classPath = System.getProperty("java.class.path", "");
        if (classPath.isBlank()) {
            return;
        }

        for (String entry : classPath.split(Pattern.quote(File.pathSeparator))) {
            if (entry.isBlank()) {
                continue;
            }
            Path classPathEntry = Path.of(entry);
            if (Files.isDirectory(classPathEntry)) {
                collectFromFilesystem(classPathEntry.resolve(directory), suffix, names);
            } else if (Files.isRegularFile(classPathEntry) && entry.endsWith(".jar")) {
                String jarUrl = classPathEntry.toAbsolutePath().normalize().toUri().toString();
                if (!scannedJarUrls.add(jarUrl)) {
                    continue;
                }
                try (JarFile jarFile = new JarFile(classPathEntry.toFile())) {
                    collectFromJar(jarFile, directory, suffix, names);
                }
            }
        }
    }

    private static Path toPath(URL url) throws IOException {
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IOException("Invalid template resource URL: " + url, e);
        }
    }

    private static void collectFromJarUrl(
            URL url, String directory, String suffix, Set<String> names, Set<String> scannedJarUrls)
            throws IOException {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        if (!scannedJarUrls.add(connection.getJarFileURL().toExternalForm())) {
            return;
        }

        boolean useCaches = connection.getUseCaches();
        JarFile jarFile = connection.getJarFile();
        try {
            collectFromJar(jarFile, directory, suffix, names);
        } finally {
            if (!useCaches) {
                jarFile.close();
            }
        }
    }

    private static void collectFromJar(JarFile jarFile, String directory, String suffix, Set<String> names) {
        String prefix = directory + "/";
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entry.isDirectory() || !entryName.startsWith(prefix)) {
                continue;
            }

            String relative = entryName.substring(prefix.length());
            if (relative.contains("/") || (suffix != null && !relative.endsWith(suffix))) {
                continue;
            }
            names.add(relative);
        }
    }
}

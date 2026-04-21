package io.github.luigidemasi.camelkit.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class DistributionConfig {

    private final String camelVersion;
    private final String springbootBomVersion;
    private final String quarkusBomVersion;
    private final String mavenRepo;
    private final String springbootBomGroupId;
    private final String quarkusBomGroupId;
    private final String camelMcpVersion;
    private final String knowledgeMcpVersion;
    private final String camelMcpRepos;
    private final String knowledgeMcpRepos;
    private final String camelCatalogRepos;
    private final Map<String, Map<String, String>> versionMap;

    private DistributionConfig(Properties props) {
        this.camelVersion = props.getProperty("camel.version", "4.14.4");
        this.springbootBomVersion = props.getProperty("springboot.bom.version", "4.14.4");
        this.quarkusBomVersion = props.getProperty("quarkus.bom.version", "3.27.2");
        this.mavenRepo = props.getProperty("maven.repo", "https://repo.maven.apache.org/maven2/");
        this.springbootBomGroupId = props.getProperty("springboot.bom.groupId", "org.apache.camel.springboot");
        this.quarkusBomGroupId = props.getProperty("quarkus.bom.groupId", "io.quarkus.platform");
        this.camelMcpVersion = props.getProperty("camel.mcp.version", "4.19.0");
        this.knowledgeMcpVersion = props.getProperty("knowledge.mcp.version", "0.0.1-SNAPSHOT");
        this.camelMcpRepos = props.getProperty("camel.mcp.repos", "maven");
        this.knowledgeMcpRepos = props.getProperty("knowledge.mcp.repos", "maven");
        this.camelCatalogRepos = props.getProperty("camel.catalog.repos", "maven");
        this.versionMap = parseVersionMap(props);
    }

    /**
     * Parses version-map entries from properties with dot-separated keys.
     * Format: version-map.{baseVersion}.{artifact}={qualifiedVersion}
     * Example: version-map.4.14.4.camel=4.14.4.redhat-00008
     */
    private static Map<String, Map<String, String>> parseVersionMap(Properties props) {
        String prefix = "version-map.";
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String rest = key.substring(prefix.length());
                int lastDot = rest.lastIndexOf('.');
                if (lastDot > 0) {
                    String baseVersion = rest.substring(0, lastDot);
                    String artifact = rest.substring(lastDot + 1);
                    result.computeIfAbsent(baseVersion, k -> new LinkedHashMap<>())
                        .put(artifact, props.getProperty(key));
                }
            }
        }
        return result.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(result);
    }

    public static DistributionConfig load(InputStream in) {
        Properties props = new Properties();
        try {
            props.load(in);
        } catch (Exception e) {
            // Fall through with empty properties — defaults will apply
        }
        return new DistributionConfig(props);
    }

    public static DistributionConfig loadFromFile(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load distribution.properties from " + path, e);
        }
    }

    public static DistributionConfig loadFromClasspathOrDefaults() {
        InputStream in = DistributionConfig.class.getClassLoader().getResourceAsStream("distribution.properties");
        if (in != null) {
            return load(in);
        }
        return new DistributionConfig(new Properties());
    }

    public String camelVersion() { return camelVersion; }
    public String springbootBomVersion() { return springbootBomVersion; }
    public String quarkusBomVersion() { return quarkusBomVersion; }
    public String mavenRepo() { return mavenRepo; }
    public String springbootBomGroupId() { return springbootBomGroupId; }
    public String quarkusBomGroupId() { return quarkusBomGroupId; }
    public String camelMcpVersion() { return camelMcpVersion; }
    public String knowledgeMcpVersion() { return knowledgeMcpVersion; }
    public String camelMcpRepos() { return camelMcpRepos; }
    public String knowledgeMcpRepos() { return knowledgeMcpRepos; }
    public String camelCatalogRepos() { return camelCatalogRepos; }
    public Map<String, Map<String, String>> versionMap() { return versionMap; }
}

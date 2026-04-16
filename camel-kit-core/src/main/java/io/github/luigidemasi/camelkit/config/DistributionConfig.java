package io.github.luigidemasi.camelkit.config;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class DistributionConfig {

    private final String distribution;
    private final String camelVersion;
    private final String springbootBomVersion;
    private final String quarkusBomVersion;
    private final String mavenRepo;
    private final String springbootBomGroupId;
    private final String quarkusBomGroupId;
    private final String ironLaws;
    private final String rule7;
    private final String knowledgeTools;
    private final String productName;
    private final Map<String, Map<String, String>> versionMap;

    @SuppressWarnings("unchecked")
    private DistributionConfig(Map<String, Object> data) {
        this.distribution = str(data, "distribution", "community");
        this.camelVersion = str(data, "camel-version", "4.14.4");
        this.springbootBomVersion = str(data, "springboot-bom-version", "4.14.4");
        this.quarkusBomVersion = str(data, "quarkus-bom-version", "3.27.2");
        this.mavenRepo = str(data, "maven-repo", "https://repo.maven.apache.org/maven2/");
        this.springbootBomGroupId = str(data, "springboot-bom-groupId", "org.apache.camel.springboot");
        this.quarkusBomGroupId = str(data, "quarkus-bom-groupId", "io.quarkus.platform");
        this.ironLaws = str(data, "iron-laws", "community");
        this.rule7 = str(data, "rule7", "catalog-exists");
        this.knowledgeTools = str(data, "knowledge-tools", "community");
        this.productName = str(data, "product-name", "Apache Camel");
        Object vm = data.getOrDefault("version-map", Collections.emptyMap());
        if (vm instanceof Map) {
            Map<String, Object> raw = (Map<String, Object>) vm;
            Map<String, Map<String, String>> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, String> inner = new java.util.LinkedHashMap<>();
                    ((Map<String, Object>) entry.getValue()).forEach((k, v) -> inner.put(k, String.valueOf(v)));
                    result.put(entry.getKey(), inner);
                }
            }
            this.versionMap = Collections.unmodifiableMap(result);
        } else {
            this.versionMap = Collections.emptyMap();
        }
    }

    public static DistributionConfig load(InputStream in) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(in);
        return new DistributionConfig(data != null ? data : Collections.emptyMap());
    }

    public static DistributionConfig loadFromFile(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load distribution.yaml from " + path, e);
        }
    }

    public static DistributionConfig loadFromClasspathOrDefaults() {
        InputStream in = DistributionConfig.class.getClassLoader().getResourceAsStream("distribution.yaml");
        if (in != null) {
            return load(in);
        }
        return new DistributionConfig(Collections.emptyMap());
    }

    public boolean isRedhat() {
        return "redhat".equals(distribution);
    }

    public boolean isCommunity() {
        return !isRedhat();
    }

    public String distribution() { return distribution; }
    public String camelVersion() { return camelVersion; }
    public String springbootBomVersion() { return springbootBomVersion; }
    public String quarkusBomVersion() { return quarkusBomVersion; }
    public String mavenRepo() { return mavenRepo; }
    public String springbootBomGroupId() { return springbootBomGroupId; }
    public String quarkusBomGroupId() { return quarkusBomGroupId; }
    public String ironLaws() { return ironLaws; }
    public String rule7() { return rule7; }
    public String knowledgeTools() { return knowledgeTools; }
    public String productName() { return productName; }
    public Map<String, Map<String, String>> versionMap() { return versionMap; }

    private static String str(Map<String, Object> data, String key, String defaultValue) {
        Object val = data.get(key);
        return val != null ? String.valueOf(val) : defaultValue;
    }
}

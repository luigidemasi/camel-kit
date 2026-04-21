package io.github.luigidemasi.camelkit.config;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

class DistributionConfigTest {

    @Test
    void loadsCommunityConfig() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution-community.properties");
        DistributionConfig config = DistributionConfig.load(in);

        assertEquals("community", config.distribution());
        assertEquals("4.14.4", config.camelVersion());
        assertEquals("https://repo.maven.apache.org/maven2/", config.mavenRepo());
        assertEquals("org.apache.camel.springboot", config.springbootBomGroupId());
        assertEquals("io.quarkus.platform", config.quarkusBomGroupId());
        assertEquals("community", config.ironLaws());
        assertEquals("catalog-exists", config.rule7());
        assertEquals("community", config.knowledgeTools());
        assertEquals("Apache Camel", config.productName());
        assertEquals("4.19.0", config.camelMcpVersion());
        assertEquals("0.0.1-SNAPSHOT", config.knowledgeMcpVersion());
        assertEquals("maven", config.camelMcpRepos());
        assertEquals("maven", config.knowledgeMcpRepos());
        assertEquals("maven", config.camelCatalogRepos());
        assertTrue(config.versionMap().isEmpty());
    }

    @Test
    void loadFromClasspath() {
        DistributionConfig config = DistributionConfig.loadFromClasspathOrDefaults();
        assertNotNull(config.distribution());
        assertNotNull(config.camelVersion());
    }
}

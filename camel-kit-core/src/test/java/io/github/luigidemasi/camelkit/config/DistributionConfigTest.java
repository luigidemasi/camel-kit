package io.github.luigidemasi.camelkit.config;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

class DistributionConfigTest {

    @Test
    void loadsCommunityConfig() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        assertEquals("4.14.4", config.camelVersion());
        assertEquals("https://repo.maven.apache.org/maven2/", config.mavenRepo());
        assertEquals("org.apache.camel.springboot", config.springbootBomGroupId());
        assertEquals("io.quarkus.platform", config.quarkusBomGroupId());
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
        assertNotNull(config.camelVersion());
    }
}

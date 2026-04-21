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
    void loadsRedhatConfig() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution-redhat.properties");
        DistributionConfig config = DistributionConfig.load(in);

        assertEquals("redhat", config.distribution());
        assertEquals("4.14.4.redhat-00008", config.camelVersion());
        assertEquals("https://maven.repository.redhat.com/ga/", config.mavenRepo());
        assertEquals("com.redhat.camel.springboot.platform", config.springbootBomGroupId());
        assertEquals("com.redhat.quarkus.platform", config.quarkusBomGroupId());
        assertEquals("redhat", config.ironLaws());
        assertEquals("rh-supported", config.rule7());
        assertEquals("redhat", config.knowledgeTools());
        assertEquals("Red Hat Build of Apache Camel", config.productName());
        assertEquals("4.19.0", config.camelMcpVersion());
        assertEquals("0.0.1-SNAPSHOT", config.knowledgeMcpVersion());
        assertEquals("redhat=https://maven.repository.redhat.com/ga/,apache_snap=https://repository.apache.org/snapshots,apache=https://repository.apache.org/content/groups/public/", config.camelMcpRepos());
        assertEquals("redhat=https://maven.repository.redhat.com/ga/", config.knowledgeMcpRepos());
        assertEquals("https://maven.repository.redhat.com/ga/", config.camelCatalogRepos());
        assertFalse(config.versionMap().isEmpty());
        assertEquals("4.14.4.redhat-00008", config.versionMap().get("4.14.4").get("camel"));
    }

    @Test
    void isRedhat() {
        InputStream community = getClass().getClassLoader().getResourceAsStream("distribution-community.properties");
        assertFalse(DistributionConfig.load(community).isRedhat());

        InputStream redhat = getClass().getClassLoader().getResourceAsStream("distribution-redhat.properties");
        assertTrue(DistributionConfig.load(redhat).isRedhat());
    }

    @Test
    void loadFromClasspath() {
        DistributionConfig config = DistributionConfig.loadFromClasspathOrDefaults();
        assertNotNull(config.distribution());
        assertNotNull(config.camelVersion());
    }
}

package io.github.luigidemasi.camelkit.knowledge.mcp;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Configuration for knowledge index artifact resolution.
 * The MCP server resolves the index JAR from Maven repositories at startup.
 */
@ConfigMapping(prefix = "knowledge.index")
public interface IndexResolverConfig {

    @WithName("group-id")
    @WithDefault("io.github.luigidemasi")
    String groupId();

    @WithName("artifact-id")
    @WithDefault("camel-kit-knowledge-index")
    String artifactId();

    /** Pin to a specific version. If empty, resolves latest from maven-metadata.xml. */
    @WithName("version")
    Optional<String> version();

    /** Comma-separated list of additional Maven repository URLs. */
    @WithName("repositories")
    Optional<String> repositories();
}

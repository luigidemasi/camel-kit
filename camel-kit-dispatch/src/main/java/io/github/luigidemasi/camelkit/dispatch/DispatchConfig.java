package io.github.luigidemasi.camelkit.dispatch;

import java.nio.file.Path;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "dispatch")
public interface DispatchConfig {

    @WithName("bob-path")
    @WithDefault("bob")
    String bobPath();

    @WithName("working-dir")
    Optional<String> workingDir();

    @WithName("default-timeout-seconds")
    @WithDefault("300")
    int defaultTimeoutSeconds();

    @WithName("max-concurrent")
    @WithDefault("4")
    int maxConcurrent();

    default Path resolveWorkingDir() {
        return workingDir()
                .map(Path::of)
                .orElse(Path.of(System.getProperty("user.dir")));
    }
}

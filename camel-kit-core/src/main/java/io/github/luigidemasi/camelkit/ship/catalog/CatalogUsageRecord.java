package io.github.luigidemasi.camelkit.ship.catalog;

import java.util.List;

/** Bounded catalog usage shapes derived from the exact accepted route bytes. */
public final class CatalogUsageRecord {

    private CatalogUsageRecord() {
    }

    /** Static endpoint shape proven against one frozen component model. Values are deliberately not retained. */
    public record EndpointUsage(
            CatalogSubject component,
            int pathParameterCount,
            List<String> componentOptions,
            List<String> endpointOptions) {

        public EndpointUsage {
            componentOptions = componentOptions == null ? List.of() : List.copyOf(componentOptions);
            endpointOptions = endpointOptions == null ? List.of() : List.copyOf(endpointOptions);
        }
    }

    /** Exact direct dependency root accepted from the candidate POM. */
    public record RuntimeDependency(String groupId, String artifactId, String version, String scope) {

        public String coordinate() {
            return groupId + ':' + artifactId + ':' + version;
        }
    }
}

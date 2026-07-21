package io.github.luigidemasi.camelkit.ship.catalog;

import java.util.List;

/** Controller-derived catalog usage for the exact accepted route bytes. */
public record CatalogUsageRecord(
        int schemaVersion,
        String runId,
        String catalogEvidenceDigest,
        String artifactManifestDigest,
        String candidateSnapshotDigest,
        String candidateContentDigest,
        String pomDigest,
        String inventoryDigest,
        List<RouteUsage> routes,
        List<CatalogComponentModel> componentModels,
        List<RuntimeDependency> runtimeDependencies,
        CatalogEvidenceSet evidence) {

    public static final int SCHEMA_VERSION = 1;

    public CatalogUsageRecord {
        routes = routes == null ? List.of() : List.copyOf(routes);
        componentModels = componentModels == null ? List.of() : List.copyOf(componentModels);
        runtimeDependencies = runtimeDependencies == null ? List.of() : List.copyOf(runtimeDependencies);
    }

    /** Usage extracted from one exact manifest route. */
    public record RouteUsage(
            String routeId,
            String path,
            String routeDigest,
            List<CatalogSubject> subjects,
            List<EndpointUsage> endpoints) {

        public RouteUsage {
            subjects = subjects == null ? List.of() : List.copyOf(subjects);
            endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
        }
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

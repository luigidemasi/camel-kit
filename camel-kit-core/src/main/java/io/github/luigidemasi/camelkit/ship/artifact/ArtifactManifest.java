package io.github.luigidemasi.camelkit.ship.artifact;

import java.util.List;

/** Controller-validated declaration of every implementation artifact required by a Ship run. */
public record ArtifactManifest(
        int schemaVersion,
        String runtime,
        String camelVersion,
        String platformVersion,
        String springBootVersion,
        String routeDsl,
        String expressionLanguage,
        String citrusVersion,
        List<String> citrusDependencies,
        JavaPolicy javaPolicy,
        List<String> approvedJavaExceptions,
        List<RouteArtifact> routes,
        List<TestArtifact> citrusTests,
        List<DeclaredArtifact> artifacts,
        boolean testsRequired,
        boolean oneTestPerRoute) {

    public static final int SCHEMA_VERSION = 1;

    public ArtifactManifest {
        citrusDependencies = immutable(citrusDependencies);
        approvedJavaExceptions = immutable(approvedJavaExceptions);
        routes = immutable(routes);
        citrusTests = immutable(citrusTests);
        artifacts = immutable(artifacts);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public enum JavaPolicy {
        FORBIDDEN,
        JUSTIFICATION_REQUIRED,
        ALLOWED
    }

    public record RouteArtifact(String routeId, String path, String digest) {
    }

    public record TestArtifact(String routeId, String path, String digest) {
    }

    public record DeclaredArtifact(String kind, String path, String digest, boolean required) {
    }
}

package io.github.luigidemasi.camelkit.ship.artifact;

import java.util.List;

/** Build and route constraints used by artifact and catalog validation. */
public record ArtifactPolicy(
        String runtime,
        String camelVersion,
        String platformVersion,
        String springBootVersion,
        String routeDsl,
        String expressionLanguage,
        String citrusVersion,
        List<String> citrusDependencies,
        ArtifactManifest.JavaPolicy javaPolicy,
        List<String> approvedJavaExceptions,
        List<RouteContract> routes,
        boolean citrusTestsRequired,
        boolean oneCitrusTestPerRoute) {

    public ArtifactPolicy {
        citrusDependencies = immutable(citrusDependencies);
        approvedJavaExceptions = immutable(approvedJavaExceptions);
        routes = immutable(routes);
    }

    public record RouteContract(String routeId, String routePath, String citrusTestPath) {
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}

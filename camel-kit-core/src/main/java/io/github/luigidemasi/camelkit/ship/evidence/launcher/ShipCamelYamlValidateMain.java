package io.github.luigidemasi.camelkit.ship.evidence.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.dsl.yaml.validator.CamelYamlParser;
import org.apache.camel.dsl.yaml.validator.YamlValidator;

import io.github.luigidemasi.camelkit.ship.ShipArtifactLimits;

/** Direct, controller-owned Camel YAML schema and parser validation entry point. */
public final class ShipCamelYamlValidateMain {

    private ShipCamelYamlValidateMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 2 || !arguments[0].startsWith("--accepted-root=")) {
            throw new IOException("YAML validation requires --accepted-root and at least one route");
        }
        validate(
                acceptedRoot(arguments[0].substring("--accepted-root=".length())),
                List.of(arguments).subList(1, arguments.length));
    }

    public static void validate(Path acceptedRoot, List<String> routeArguments) throws Exception {
        Path root = acceptedRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || routeArguments == null || routeArguments.isEmpty()) {
            throw new IOException("YAML validation requires an accepted route snapshot");
        }
        Set<Path> routes = new HashSet<>();
        for (String value : routeArguments) {
            Path route = root.resolve(value).normalize();
            if (!route.startsWith(root) || Files.isSymbolicLink(route)
                    || !Files.isRegularFile(route, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(route) <= 0
                    || Files.size(route) > ShipArtifactLimits.MAX_ROUTE_YAML_BYTES) {
                throw new IOException("YAML route is outside the accepted snapshot or unsafe: " + value);
            }
            route = route.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!route.startsWith(root) || !routes.add(route)) {
                throw new IOException("YAML route escaped or was supplied more than once: " + value);
            }
            List<?> schemaErrors = new YamlValidator().validate(route.toFile());
            if (!schemaErrors.isEmpty()) {
                throw new IOException("Camel YAML schema validation failed for " + value + ": " + schemaErrors);
            }
            List<?> parserErrors = new CamelYamlParser().parse(route.toFile());
            if (!parserErrors.isEmpty()) {
                throw new IOException("Camel YAML parser validation failed for " + value + ": " + parserErrors);
            }
        }
    }

    // ponytail: duplicates ShipCamelMainBootstrap.acceptedRoot because each launcher JAR is a self-contained closure
    private static Path acceptedRoot(String value) throws IOException {
        Path root = Path.of(value);
        if (!root.isAbsolute()) {
            throw new IOException("Accepted snapshot root must be an absolute path");
        }
        Path real = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!real.equals(root.normalize()) || Files.isSymbolicLink(real)
                || !Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Accepted snapshot root must be a real directory");
        }
        return real;
    }
}

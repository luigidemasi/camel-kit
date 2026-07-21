package io.github.luigidemasi.camelkit.ship.catalog;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipCatalogBoundaryTest {

    private static final String CATALOG_PACKAGE = "io.github.luigidemasi.camelkit.ship.catalog.";
    private static final Pattern DEPENDENCY = Pattern.compile("^\\s+(\\S+)\\s+->\\s+(\\S+)\\s+(.+)$");
    private static final Set<String> ALLOWED_JDK_MODULES = Set.of("java.base", "java.xml");
    private static final Set<String> ALLOWED_EXTERNAL_TYPES = Set.of(
            "com.fasterxml.jackson.core.JsonFactory",
            "com.fasterxml.jackson.core.StreamReadConstraints",
            "com.fasterxml.jackson.core.StreamReadConstraints$Builder",
            "com.fasterxml.jackson.core.StreamReadFeature",
            "com.fasterxml.jackson.core.TSFBuilder",
            "com.fasterxml.jackson.databind.DeserializationFeature",
            "com.fasterxml.jackson.databind.JsonNode",
            "com.fasterxml.jackson.databind.ObjectMapper",
            "com.fasterxml.jackson.databind.ObjectReader",
            "com.fasterxml.jackson.databind.node.TextNode",
            "com.fasterxml.jackson.dataformat.yaml.YAMLFactory",
            "com.fasterxml.jackson.dataformat.yaml.YAMLFactoryBuilder",
            "io.github.luigidemasi.camelkit.ship.ShipDigest",
            "io.github.luigidemasi.camelkit.ship.expression.ShipExpressionPolicy",
            "io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate",
            "io.github.luigidemasi.camelkit.ship.resolver.ResolvedExactMavenArtifact",
            "io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver",
            "io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver$ResolutionMode",
            "javax.xml.XMLConstants",
            "javax.xml.parsers.DocumentBuilder",
            "javax.xml.parsers.DocumentBuilderFactory",
            "javax.xml.parsers.ParserConfigurationException",
            "javax.xml.stream.XMLInputFactory",
            "javax.xml.stream.XMLResolver",
            "javax.xml.stream.XMLStreamConstants",
            "javax.xml.stream.XMLStreamException",
            "javax.xml.stream.XMLStreamReader",
            "org.w3c.dom.Document",
            "org.w3c.dom.Element",
            "org.w3c.dom.Node",
            "org.xml.sax.ErrorHandler",
            "org.xml.sax.SAXException",
            "org.xml.sax.SAXParseException");
    private static final Set<String> PUBLIC_RECORDS = Set.of(
            CATALOG_PACKAGE + "CatalogTarget",
            CATALOG_PACKAGE + "CatalogSubject",
            CATALOG_PACKAGE + "CatalogEvidenceSet",
            CATALOG_PACKAGE + "CatalogEvidenceSet$ArtifactEvidence",
            CATALOG_PACKAGE + "CatalogEvidenceSet$SubjectEvidence",
            CATALOG_PACKAGE + "CatalogComponentModel",
            CATALOG_PACKAGE + "CatalogComponentModel$Option",
            CATALOG_PACKAGE + "CamelYamlCatalogUsageExtractor$Extraction",
            CATALOG_PACKAGE + "CatalogUsageRecord",
            CATALOG_PACKAGE + "CatalogUsageRecord$RouteUsage",
            CATALOG_PACKAGE + "CatalogUsageRecord$EndpointUsage",
            CATALOG_PACKAGE + "CatalogUsageRecord$RuntimeDependency");
    private static final Set<String> PUBLIC_ENUMS = Set.of(
            CATALOG_PACKAGE + "CatalogSubject$Kind",
            CATALOG_PACKAGE + "CatalogComponentModel$Scope",
            CATALOG_PACKAGE + "CatalogComponentModel$Kind");
    private static final Map<String, List<String>> PUBLIC_ENUM_CONSTANTS = Map.of(
            CATALOG_PACKAGE + "CatalogSubject$Kind",
            List.of("COMPONENT", "EIP", "DATAFORMAT", "LANGUAGE"),
            CATALOG_PACKAGE + "CatalogComponentModel$Scope",
            List.of("COMPONENT", "ENDPOINT"),
            CATALOG_PACKAGE + "CatalogComponentModel$Kind",
            List.of("PROPERTY", "PATH", "PARAMETER"));
    private static final Map<String, Set<String>> PUBLIC_INTERFACES = Map.of(
            CATALOG_PACKAGE + "CatalogSubject",
            Set.of("java.lang.Comparable<" + CATALOG_PACKAGE + "CatalogSubject>"));
    private static final Map<String, Set<String>> PUBLIC_CONSTRUCTORS = Map.ofEntries(
            Map.entry(CATALOG_PACKAGE + "CatalogTarget", Set.of(constructor(
                    "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String"))),
            Map.entry(CATALOG_PACKAGE + "CatalogSubject", Set.of(constructor(
                    CATALOG_PACKAGE + "CatalogSubject$Kind", "java.lang.String"))),
            Map.entry(CATALOG_PACKAGE + "CatalogSubject$Kind", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CatalogEvidenceSet", Set.of(constructor(
                    "int", CATALOG_PACKAGE + "CatalogTarget",
                    "io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate",
                    "java.util.List<" + CATALOG_PACKAGE + "CatalogEvidenceSet$ArtifactEvidence>",
                    "java.util.List<" + CATALOG_PACKAGE + "CatalogEvidenceSet$SubjectEvidence>",
                    "java.lang.String"))),
            Map.entry(CATALOG_PACKAGE + "CatalogEvidenceSet$ArtifactEvidence", Set.of(constructor(
                    "io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate",
                    "java.lang.String", "long"))),
            Map.entry(CATALOG_PACKAGE + "CatalogEvidenceSet$SubjectEvidence", Set.of(constructor(
                    CATALOG_PACKAGE + "CatalogSubject",
                    "io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate",
                    "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String",
                    "java.lang.String", "java.lang.String", "boolean"))),
            Map.entry(CATALOG_PACKAGE + "CatalogComponentModel", Set.of(constructor(
                    CATALOG_PACKAGE + "CatalogEvidenceSet$SubjectEvidence", "java.lang.String", "boolean",
                    "java.util.List<" + CATALOG_PACKAGE + "CatalogComponentModel$Option>"))),
            Map.entry(CATALOG_PACKAGE + "CatalogComponentModel$Option", Set.of(constructor(
                    "java.lang.String", CATALOG_PACKAGE + "CatalogComponentModel$Scope",
                    CATALOG_PACKAGE + "CatalogComponentModel$Kind", "int", "java.lang.String",
                    "java.lang.String", "boolean", "boolean", "java.lang.String", "java.lang.String",
                    "java.util.List<java.lang.String>"))),
            Map.entry(CATALOG_PACKAGE + "CatalogComponentModel$Scope", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CatalogComponentModel$Kind", Set.of()),
            Map.entry(CATALOG_PACKAGE + "ShipCatalogService", Set.of(constructor("java.nio.file.Path"))),
            Map.entry(CATALOG_PACKAGE + "ShipCatalogService$Snapshot", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CamelYamlCatalogUsageExtractor", Set.of("<init>()")),
            Map.entry(CATALOG_PACKAGE + "CamelYamlCatalogUsageExtractor$Extraction", Set.of(constructor(
                    "java.lang.String", "java.util.List<" + CATALOG_PACKAGE + "CatalogSubject>",
                    "java.util.List<" + CATALOG_PACKAGE + "CatalogUsageRecord$EndpointUsage>"))),
            Map.entry(CATALOG_PACKAGE + "CatalogExpressionInventory", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CatalogUsageRecord", Set.of(constructor(
                    "int", "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String",
                    "java.lang.String", "java.lang.String", "java.lang.String",
                    "java.util.List<" + CATALOG_PACKAGE + "CatalogUsageRecord$RouteUsage>",
                    "java.util.List<" + CATALOG_PACKAGE + "CatalogComponentModel>",
                    "java.util.List<" + CATALOG_PACKAGE + "CatalogUsageRecord$RuntimeDependency>",
                    CATALOG_PACKAGE + "CatalogEvidenceSet"))),
            Map.entry(CATALOG_PACKAGE + "CatalogUsageRecord$RouteUsage", Set.of(constructor(
                    "java.lang.String", "java.lang.String", "java.lang.String",
                    "java.util.List<" + CATALOG_PACKAGE + "CatalogSubject>",
                    "java.util.List<" + CATALOG_PACKAGE + "CatalogUsageRecord$EndpointUsage>"))),
            Map.entry(CATALOG_PACKAGE + "CatalogUsageRecord$EndpointUsage", Set.of(constructor(
                    CATALOG_PACKAGE + "CatalogSubject", "int", "java.util.List<java.lang.String>",
                    "java.util.List<java.lang.String>"))),
            Map.entry(CATALOG_PACKAGE + "CatalogUsageRecord$RuntimeDependency", Set.of(constructor(
                    "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String"))));
    private static final Map<String, Set<String>> PUBLIC_METHODS = Map.ofEntries(
            Map.entry(CATALOG_PACKAGE + "CatalogTarget", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"), method("runtime", "java.lang.String"),
                    method("camelVersion", "java.lang.String"), method("platformVersion", "java.lang.String"),
                    method("springBootVersion", "java.lang.String"))),
            Map.entry(CATALOG_PACKAGE + "CatalogSubject", Set.of(
                    method("compareTo", "int", CATALOG_PACKAGE + "CatalogSubject"),
                    method("compareTo", "int", "java.lang.Object"), method("toString", "java.lang.String"),
                    method("hashCode", "int"), method("equals", "boolean", "java.lang.Object"),
                    method("kind", CATALOG_PACKAGE + "CatalogSubject$Kind"),
                    method("name", "java.lang.String"))),
            Map.entry(CATALOG_PACKAGE + "CatalogSubject$Kind", Set.of(
                    staticMethod("values", CATALOG_PACKAGE + "CatalogSubject$Kind[]"),
                    staticMethod("valueOf", CATALOG_PACKAGE + "CatalogSubject$Kind", "java.lang.String"))),
            Map.entry(CATALOG_PACKAGE + "CatalogEvidenceSet", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"), method("schemaVersion", "int"),
                    method("target", CATALOG_PACKAGE + "CatalogTarget"),
                    method("platformCoordinate",
                            "io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate"),
                    method("artifacts", "java.util.List<" + CATALOG_PACKAGE
                                        + "CatalogEvidenceSet$ArtifactEvidence>"),
                    method("subjects", "java.util.List<" + CATALOG_PACKAGE
                                       + "CatalogEvidenceSet$SubjectEvidence>"),
                    method("digest", "java.lang.String"),
                    staticMethod("create", CATALOG_PACKAGE + "CatalogEvidenceSet",
                            CATALOG_PACKAGE + "CatalogTarget",
                            "io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate",
                            "java.util.List<" + CATALOG_PACKAGE + "CatalogEvidenceSet$ArtifactEvidence>",
                            "java.util.List<" + CATALOG_PACKAGE + "CatalogEvidenceSet$SubjectEvidence>"))),
            Map.entry(CATALOG_PACKAGE + "CatalogEvidenceSet$ArtifactEvidence", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"),
                    method("coordinate", "io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate"),
                    method("sha256", "java.lang.String"), method("size", "long"))),
            Map.entry(CATALOG_PACKAGE + "CatalogEvidenceSet$SubjectEvidence", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"),
                    method("subject", CATALOG_PACKAGE + "CatalogSubject"),
                    method("catalogCoordinate",
                            "io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate"),
                    method("catalogSha256", "java.lang.String"), method("resource", "java.lang.String"),
                    method("resourceSha256", "java.lang.String"), method("groupId", "java.lang.String"),
                    method("artifactId", "java.lang.String"), method("artifactVersion", "java.lang.String"),
                    method("deprecated", "boolean"))),
            Map.entry(CATALOG_PACKAGE + "CatalogComponentModel", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"),
                    method("evidence", CATALOG_PACKAGE + "CatalogEvidenceSet$SubjectEvidence"),
                    method("syntax", "java.lang.String"), method("lenientProperties", "boolean"),
                    method("options", "java.util.List<" + CATALOG_PACKAGE + "CatalogComponentModel$Option>"))),
            Map.entry(CATALOG_PACKAGE + "CatalogComponentModel$Option", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"), method("name", "java.lang.String"),
                    method("scope", CATALOG_PACKAGE + "CatalogComponentModel$Scope"),
                    method("kind", CATALOG_PACKAGE + "CatalogComponentModel$Kind"), method("index", "int"),
                    method("type", "java.lang.String"), method("javaType", "java.lang.String"),
                    method("required", "boolean"), method("multiValue", "boolean"),
                    method("prefix", "java.lang.String"), method("optionalPrefix", "java.lang.String"),
                    method("enumValues", "java.util.List<java.lang.String>"))),
            Map.entry(CATALOG_PACKAGE + "CatalogComponentModel$Scope", Set.of(
                    staticMethod("values", CATALOG_PACKAGE + "CatalogComponentModel$Scope[]"),
                    staticMethod("valueOf", CATALOG_PACKAGE + "CatalogComponentModel$Scope",
                            "java.lang.String"))),
            Map.entry(CATALOG_PACKAGE + "CatalogComponentModel$Kind", Set.of(
                    staticMethod("values", CATALOG_PACKAGE + "CatalogComponentModel$Kind[]"),
                    staticMethod("valueOf", CATALOG_PACKAGE + "CatalogComponentModel$Kind",
                            "java.lang.String"))),
            Map.entry(CATALOG_PACKAGE + "ShipCatalogService", Set.of(throwingMethod(
                    "snapshot", CATALOG_PACKAGE + "ShipCatalogService$Snapshot", "java.io.IOException",
                    CATALOG_PACKAGE + "CatalogTarget"))),
            Map.entry(CATALOG_PACKAGE + "ShipCatalogService$Snapshot", Set.of(
                    method("target", CATALOG_PACKAGE + "CatalogTarget"),
                    throwingMethod("evidenceFor", CATALOG_PACKAGE + "CatalogEvidenceSet", "java.io.IOException",
                            "java.util.Collection<" + CATALOG_PACKAGE + "CatalogSubject>"),
                    throwingMethod("availableSubjects",
                            "java.util.List<" + CATALOG_PACKAGE + "CatalogSubject>", "java.io.IOException"),
                    throwingMethod("componentModelsFor",
                            "java.util.List<" + CATALOG_PACKAGE + "CatalogComponentModel>", "java.io.IOException",
                            "java.util.Collection<" + CATALOG_PACKAGE + "CatalogSubject>"),
                    method("toString", "java.lang.String"))),
            Map.entry(CATALOG_PACKAGE + "CamelYamlCatalogUsageExtractor", Set.of(
                    throwingMethod("extract", "java.util.List<" + CATALOG_PACKAGE + "CatalogSubject>",
                            "java.io.IOException", "java.nio.file.Path",
                            "java.util.Collection<" + CATALOG_PACKAGE + "CatalogSubject>"),
                    throwingMethod("extractBound", CATALOG_PACKAGE + "CamelYamlCatalogUsageExtractor$Extraction",
                            "java.io.IOException", "java.nio.file.Path",
                            "java.util.Collection<" + CATALOG_PACKAGE + "CatalogSubject>"),
                    throwingMethod("extractBound", CATALOG_PACKAGE + "CamelYamlCatalogUsageExtractor$Extraction",
                            "java.io.IOException", "java.nio.file.Path",
                            "java.util.Collection<" + CATALOG_PACKAGE + "CatalogSubject>",
                            "java.util.Collection<" + CATALOG_PACKAGE + "CatalogComponentModel>"))),
            Map.entry(CATALOG_PACKAGE + "CamelYamlCatalogUsageExtractor$Extraction", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"), method("digest", "java.lang.String"),
                    method("subjects", "java.util.List<" + CATALOG_PACKAGE + "CatalogSubject>"),
                    method("endpoints", "java.util.List<" + CATALOG_PACKAGE
                                        + "CatalogUsageRecord$EndpointUsage>"))),
            Map.entry(CATALOG_PACKAGE + "CatalogExpressionInventory", Set.of(
                    staticMethod("yamlAliases", "java.util.Set<java.lang.String>"),
                    staticMethod("catalogLanguages", "java.util.Set<java.lang.String>"),
                    staticMethod("isKnownAlias", "boolean", "java.lang.String"),
                    staticMethod("isRejectedAlias", "boolean", "java.lang.String"),
                    staticMethod("isNonExactAlias", "boolean", "java.lang.String"),
                    staticMethod("isSafeSimple", "boolean", "java.lang.String"))),
            Map.entry(CATALOG_PACKAGE + "CatalogUsageRecord", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"), method("schemaVersion", "int"),
                    method("runId", "java.lang.String"), method("catalogEvidenceDigest", "java.lang.String"),
                    method("artifactManifestDigest", "java.lang.String"),
                    method("candidateSnapshotDigest", "java.lang.String"),
                    method("candidateContentDigest", "java.lang.String"),
                    method("pomDigest", "java.lang.String"), method("inventoryDigest", "java.lang.String"),
                    method("routes", "java.util.List<" + CATALOG_PACKAGE + "CatalogUsageRecord$RouteUsage>"),
                    method("componentModels", "java.util.List<" + CATALOG_PACKAGE + "CatalogComponentModel>"),
                    method("runtimeDependencies", "java.util.List<" + CATALOG_PACKAGE
                                                  + "CatalogUsageRecord$RuntimeDependency>"),
                    method("evidence", CATALOG_PACKAGE + "CatalogEvidenceSet"))),
            Map.entry(CATALOG_PACKAGE + "CatalogUsageRecord$RouteUsage", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"), method("routeId", "java.lang.String"),
                    method("path", "java.lang.String"), method("routeDigest", "java.lang.String"),
                    method("subjects", "java.util.List<" + CATALOG_PACKAGE + "CatalogSubject>"),
                    method("endpoints", "java.util.List<" + CATALOG_PACKAGE
                                        + "CatalogUsageRecord$EndpointUsage>"))),
            Map.entry(CATALOG_PACKAGE + "CatalogUsageRecord$EndpointUsage", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"),
                    method("component", CATALOG_PACKAGE + "CatalogSubject"),
                    method("pathParameterCount", "int"),
                    method("componentOptions", "java.util.List<java.lang.String>"),
                    method("endpointOptions", "java.util.List<java.lang.String>"))),
            Map.entry(CATALOG_PACKAGE + "CatalogUsageRecord$RuntimeDependency", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"), method("coordinate", "java.lang.String"),
                    method("groupId", "java.lang.String"), method("artifactId", "java.lang.String"),
                    method("version", "java.lang.String"), method("scope", "java.lang.String"))));
    private static final Map<String, Set<String>> PUBLIC_FIELDS = Map.ofEntries(
            Map.entry(CATALOG_PACKAGE + "CatalogTarget", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CatalogSubject", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CatalogSubject$Kind", Set.of(
                    field("COMPONENT", CATALOG_PACKAGE + "CatalogSubject$Kind"),
                    field("EIP", CATALOG_PACKAGE + "CatalogSubject$Kind"),
                    field("DATAFORMAT", CATALOG_PACKAGE + "CatalogSubject$Kind"),
                    field("LANGUAGE", CATALOG_PACKAGE + "CatalogSubject$Kind"))),
            Map.entry(CATALOG_PACKAGE + "CatalogEvidenceSet", Set.of(field("SCHEMA_VERSION", "int"))),
            Map.entry(CATALOG_PACKAGE + "CatalogEvidenceSet$ArtifactEvidence", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CatalogEvidenceSet$SubjectEvidence", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CatalogComponentModel", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CatalogComponentModel$Option", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CatalogComponentModel$Scope", Set.of(
                    field("COMPONENT", CATALOG_PACKAGE + "CatalogComponentModel$Scope"),
                    field("ENDPOINT", CATALOG_PACKAGE + "CatalogComponentModel$Scope"))),
            Map.entry(CATALOG_PACKAGE + "CatalogComponentModel$Kind", Set.of(
                    field("PROPERTY", CATALOG_PACKAGE + "CatalogComponentModel$Kind"),
                    field("PATH", CATALOG_PACKAGE + "CatalogComponentModel$Kind"),
                    field("PARAMETER", CATALOG_PACKAGE + "CatalogComponentModel$Kind"))),
            Map.entry(CATALOG_PACKAGE + "ShipCatalogService", Set.of()),
            Map.entry(CATALOG_PACKAGE + "ShipCatalogService$Snapshot", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CamelYamlCatalogUsageExtractor", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CamelYamlCatalogUsageExtractor$Extraction", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CatalogExpressionInventory", Set.of(
                    field("SIMPLE", "java.lang.String"), field("GENERIC", "java.lang.String"),
                    field("METHOD", "java.lang.String"))),
            Map.entry(CATALOG_PACKAGE + "CatalogUsageRecord", Set.of(field("SCHEMA_VERSION", "int"))),
            Map.entry(CATALOG_PACKAGE + "CatalogUsageRecord$RouteUsage", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CatalogUsageRecord$EndpointUsage", Set.of()),
            Map.entry(CATALOG_PACKAGE + "CatalogUsageRecord$RuntimeDependency", Set.of()));
    private static final Map<String, Map<String, Object>> PUBLIC_CONSTANT_VALUES = Map.of(
            CATALOG_PACKAGE + "CatalogEvidenceSet", Map.of("SCHEMA_VERSION", 1),
            CATALOG_PACKAGE + "CatalogExpressionInventory", Map.of(
                    "SIMPLE", "simple", "GENERIC", "language", "METHOD", "method"),
            CATALOG_PACKAGE + "CatalogUsageRecord", Map.of("SCHEMA_VERSION", 1));

    @Test
    void compiledCatalogUsesOnlyApprovedModulesAndExactExternalTypes()
            throws Exception {
        Path classes = Path.of(System.getProperty("basedir"), "target", "classes");
        Path catalog = classes.resolve("io/github/luigidemasi/camelkit/ship/catalog");
        Process process = new ProcessBuilder(
                jdkTool("jdeps"), "--multi-release", "17", "-verbose:class", catalog.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);

        int dependencies = 0;
        for (String line : output.lines().toList()) {
            Matcher dependency = DEPENDENCY.matcher(line);
            if (!dependency.matches() || !dependency.group(1).startsWith(CATALOG_PACKAGE)) {
                continue;
            }
            dependencies++;
            String target = dependency.group(2);
            String module = dependency.group(3).trim();
            if (target.startsWith("java.")) {
                assertTrue(ALLOWED_JDK_MODULES.contains(module),
                        () -> "Catalog code added JDK dependency " + target + " from " + module + ":\n" + output);
            } else {
                assertTrue(ALLOWED_EXTERNAL_TYPES.contains(target),
                        () -> "Catalog code added unapproved external dependency " + target + ":\n" + output);
            }
        }
        assertTrue(dependencies > 0, "jdeps did not report catalog class dependencies:\n" + output);
        assertTrue(output.contains(MavenCoordinate.class.getName()),
                "jdeps did not observe the intended public resolver edge:\n" + output);
    }

    @Test
    void publicCatalogSurfaceContainsOnlyJdkCatalogAndCoordinateTypes() throws Exception {
        Set<Class<?>> expected = Set.of(
                CatalogTarget.class,
                CatalogSubject.class,
                CatalogSubject.Kind.class,
                CatalogEvidenceSet.class,
                CatalogEvidenceSet.ArtifactEvidence.class,
                CatalogEvidenceSet.SubjectEvidence.class,
                CatalogComponentModel.class,
                CatalogComponentModel.Option.class,
                CatalogComponentModel.Scope.class,
                CatalogComponentModel.Kind.class,
                ShipCatalogService.class,
                ShipCatalogService.Snapshot.class,
                CamelYamlCatalogUsageExtractor.class,
                CamelYamlCatalogUsageExtractor.Extraction.class,
                CatalogExpressionInventory.class,
                CatalogUsageRecord.class,
                CatalogUsageRecord.RouteUsage.class,
                CatalogUsageRecord.EndpointUsage.class,
                CatalogUsageRecord.RuntimeDependency.class);
        Path classes = Path.of(System.getProperty("basedir"), "target", "classes");
        Path catalog = classes.resolve("io/github/luigidemasi/camelkit/ship/catalog");
        Set<Class<?>> actual = new HashSet<>();
        try (var files = Files.walk(catalog)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".class")).toList()) {
                String relative = classes.relativize(file).toString();
                String name = relative.substring(0, relative.length() - ".class".length())
                        .replace(file.getFileSystem().getSeparator(), ".");
                Class<?> type = Class.forName(name, false, ShipCatalogBoundaryTest.class.getClassLoader());
                if (Modifier.isPublic(type.getModifiers())) {
                    actual.add(type);
                }
            }
        }
        assertEquals(expected, actual, "Public catalog types changed without an explicit boundary update");

        for (Class<?> type : actual) {
            String name = type.getName();
            assertTrue(Modifier.isFinal(type.getModifiers()), name + " must remain final");
            assertEquals(PUBLIC_RECORDS.contains(name), type.isRecord(), name + " record shape changed");
            assertEquals(PUBLIC_ENUMS.contains(name), type.isEnum(), name + " enum shape changed");
            if (type.isEnum()) {
                assertEquals(PUBLIC_ENUM_CONSTANTS.get(name), Arrays.stream(type.getEnumConstants())
                        .map(value -> ((Enum<?>) value).name()).toList(), name + " enum order changed");
            }
            assertEquals(type.isRecord() ? Record.class : type.isEnum() ? Enum.class : Object.class,
                    type.getSuperclass(), name + " superclass changed");
            assertEquals(0, type.getTypeParameters().length, name + " added type parameters");
            Set<String> interfaces = Arrays.stream(type.getGenericInterfaces())
                    .map(Type::getTypeName).collect(java.util.stream.Collectors.toSet());
            assertEquals(PUBLIC_INTERFACES.getOrDefault(name, Set.of()), interfaces,
                    name + " interfaces changed");
            if (type.getEnclosingClass() != null) {
                assertTrue(Modifier.isStatic(type.getModifiers()), name + " must remain static");
            }

            Set<String> constructors = new HashSet<>();
            for (var constructor : type.getDeclaredConstructors()) {
                if (Modifier.isPublic(constructor.getModifiers())) {
                    constructors.add(constructor(constructor.getGenericParameterTypes()));
                    assertExecutableTypes(constructor);
                }
            }
            assertEquals(PUBLIC_CONSTRUCTORS.get(name), constructors, name + " constructors changed");

            Set<String> methods = new HashSet<>();
            for (var method : type.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    methods.add(method(method));
                    assertConsumerType(method.getGenericReturnType(), method.toGenericString(), new HashSet<>());
                    assertExecutableTypes(method);
                }
            }
            assertEquals(PUBLIC_METHODS.get(name), methods, name + " methods changed");

            Set<String> fields = new HashSet<>();
            for (var field : type.getDeclaredFields()) {
                if (Modifier.isPublic(field.getModifiers())) {
                    assertTrue(Modifier.isStatic(field.getModifiers()), field.toGenericString());
                    assertTrue(Modifier.isFinal(field.getModifiers()), field.toGenericString());
                    fields.add(field(field.getName(), field.getGenericType().getTypeName()));
                    assertConsumerType(field.getGenericType(), field.toGenericString(), new HashSet<>());
                }
            }
            assertEquals(PUBLIC_FIELDS.get(name), fields, name + " fields changed");
            for (var constant : PUBLIC_CONSTANT_VALUES.getOrDefault(name, Map.of()).entrySet()) {
                assertEquals(constant.getValue(), type.getField(constant.getKey()).get(null),
                        name + '.' + constant.getKey() + " value changed");
            }
            if (type.isRecord()) {
                for (var component : type.getRecordComponents()) {
                    assertConsumerType(component.getGenericType(), component.toString(), new HashSet<>());
                }
            }
        }
    }

    private static String constructor(String... parameterTypes) {
        return "<init>(" + String.join(",", parameterTypes) + ')';
    }

    private static String constructor(Type... parameterTypes) {
        return constructor(Arrays.stream(parameterTypes).map(Type::getTypeName).toArray(String[]::new));
    }

    private static String method(String name, String returnType, String... parameterTypes) {
        return name + '(' + String.join(",", parameterTypes) + ")->" + returnType;
    }

    private static String staticMethod(String name, String returnType, String... parameterTypes) {
        return "static " + method(name, returnType, parameterTypes);
    }

    private static String throwingMethod(
            String name, String returnType, String exceptionType, String... parameterTypes) {
        return method(name, returnType, parameterTypes) + " throws " + exceptionType;
    }

    private static String method(java.lang.reflect.Method method) {
        String signature = (Modifier.isStatic(method.getModifiers()) ? "static " : "")
                           + method(method.getName(), method.getGenericReturnType().getTypeName(),
                                   Arrays.stream(method.getGenericParameterTypes())
                                           .map(Type::getTypeName).toArray(String[]::new));
        String[] exceptions = Arrays.stream(method.getGenericExceptionTypes())
                .map(Type::getTypeName).toArray(String[]::new);
        return exceptions.length == 0 ? signature : signature + " throws " + String.join(",", exceptions);
    }

    private static String field(String name, String type) {
        return name + ':' + type;
    }

    private static void assertExecutableTypes(Executable executable) {
        for (Type parameter : executable.getGenericParameterTypes()) {
            assertConsumerType(parameter, executable.toGenericString(), new HashSet<>());
        }
        for (Type exception : executable.getGenericExceptionTypes()) {
            assertConsumerType(exception, executable.toGenericString(), new HashSet<>());
        }
        for (TypeVariable<?> variable : executable.getTypeParameters()) {
            assertConsumerType(variable, executable.toGenericString(), new HashSet<>());
        }
    }

    private static void assertConsumerType(Type type, String owner, Set<Type> visited) {
        if (type == null || !visited.add(type)) {
            return;
        }
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                assertConsumerType(clazz.getComponentType(), owner, visited);
                return;
            }
            String name = clazz.getName();
            assertTrue(clazz.isPrimitive()
                    || name.startsWith("java.")
                    || name.startsWith(CATALOG_PACKAGE)
                    || name.equals(MavenCoordinate.class.getName()),
                    () -> owner + " leaks non-boundary type " + name);
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            assertConsumerType(parameterized.getRawType(), owner, visited);
            assertConsumerType(parameterized.getOwnerType(), owner, visited);
            for (Type argument : parameterized.getActualTypeArguments()) {
                assertConsumerType(argument, owner, visited);
            }
            return;
        }
        if (type instanceof GenericArrayType array) {
            assertConsumerType(array.getGenericComponentType(), owner, visited);
            return;
        }
        if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                assertConsumerType(bound, owner, visited);
            }
            return;
        }
        if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                assertConsumerType(bound, owner, visited);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                assertConsumerType(bound, owner, visited);
            }
            return;
        }
        fail(owner + " exposes unsupported generic type " + type);
    }

    private static String jdkTool(String name) throws IOException {
        Path tool = Path.of(System.getProperty("java.home"), "bin", name);
        if (!tool.toFile().canExecute()) {
            throw new IOException("Required JDK tool is unavailable: " + name);
        }
        return tool.toString();
    }
}

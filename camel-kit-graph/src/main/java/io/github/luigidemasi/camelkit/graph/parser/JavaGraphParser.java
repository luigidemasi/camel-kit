package io.github.luigidemasi.camelkit.graph.parser;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

public class JavaGraphParser implements GraphParser {

    private static final Set<String> BEAN_ANNOTATIONS = Set.of(
            "Component", "Service", "Repository", "Controller",
            "Named", "Singleton", "ApplicationScoped", "RequestScoped");

    private static final Set<String> INJECTION_ANNOTATIONS = Set.of("Inject", "Autowired");

    private static final Pattern VALUE_PROPERTY_PATTERN = Pattern.compile("\\$\\{([^:}]+)");

    private static final Set<String> CAMEL_PROCESSOR_METHODS = Set.of(
            "bean", "process", "marshal", "unmarshal", "filter", "split",
            "aggregate", "enrich", "pollEnrich", "transform", "convertBodyTo",
            "log", "choice", "multicast", "recipientList", "wireTap",
            "throttle", "delay", "setHeader", "setBody", "removeHeader");

    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "String", "Object", "Integer", "Long", "Double", "Float", "Boolean",
            "Byte", "Short", "Character", "Number", "Void",
            "Class", "Enum", "Record", "Throwable", "Exception", "RuntimeException",
            "Error", "StringBuilder", "StringBuffer", "Math", "System",
            "Comparable", "Iterable", "AutoCloseable", "Cloneable", "Runnable",
            "Thread", "Override", "Deprecated", "SuppressWarnings");

    @Override
    public void parse(Path projectRoot, ProjectGraph graph) {
        parseFiles(projectRoot, scannedFilePaths(projectRoot, GraphParser.projectFiles(projectRoot)), graph);
    }

    @Override
    public void parseFiles(Path projectRoot, List<Path> files, ProjectGraph graph) {
        JavaParser parser = new JavaParser();
        files.forEach(file -> parseJavaFile(parser, file, projectRoot, graph));
    }

    @Override
    public List<String> scannedFiles(Path projectRoot) {
        return GraphParser.findFiles(projectRoot, file -> file.toString().endsWith(".java"));
    }

    @Override
    public List<Path> scannedFilePaths(Path projectRoot, List<Path> projectFiles) {
        return GraphParser.findFilePaths(projectRoot, projectFiles, file -> file.toString().endsWith(".java"));
    }

    private void parseJavaFile(JavaParser parser, Path file, Path projectRoot, ProjectGraph graph) {
        try {
            var parseResult = parser.parse(file);
            if (parseResult.getResult().isEmpty()) {
                return;
            }
            CompilationUnit cu = parseResult.getResult().get();
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            for (ClassOrInterfaceDeclaration classDecl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                parseClass(classDecl, packageName, projectRoot, file, graph, cu);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Java file: " + file, e);
        }
    }

    private void parseClass(
            ClassOrInterfaceDeclaration classDecl, String packageName,
            Path projectRoot, Path file, ProjectGraph graph, CompilationUnit cu) {
        String fqcn = packageName.isEmpty()
                ? classDecl.getNameAsString()
                : packageName + "." + classDecl.getNameAsString();
        String classNodeId = "class:" + fqcn;

        Map<String, String> classProps = new HashMap<>();
        classProps.put("name", classDecl.getNameAsString());
        classProps.put("fqn", fqcn);
        classProps.put("package", packageName);
        classProps.put("file", projectRoot.relativize(file).toString());
        classProps.put("interface", String.valueOf(classDecl.isInterface()));
        if (classDecl.isAbstract()) {
            classProps.put("abstract", "true");
        }

        // Detect bean annotations on the class
        detectBeanAnnotations(classDecl, classProps);

        graph.addNode(new GraphNode(classNodeId, NodeType.CLASS, classProps));

        // Parse extends
        for (ClassOrInterfaceType extended : classDecl.getExtendedTypes()) {
            String parentFqcn = resolveTypeName(extended, packageName, cu);
            String parentNodeId = "class:" + parentFqcn;
            graph.addEdge(new GraphEdge(classNodeId, parentNodeId, EdgeType.EXTENDS, Map.of()));
        }

        // Parse implements
        for (ClassOrInterfaceType implemented : classDecl.getImplementedTypes()) {
            String ifaceFqcn = resolveTypeName(implemented, packageName, cu);
            String ifaceNodeId = "class:" + ifaceFqcn;
            graph.addEdge(new GraphEdge(classNodeId, ifaceNodeId, EdgeType.IMPLEMENTS, Map.of()));
        }

        Set<String> allowedPrefixes = computeAllowedPrefixes(graph);

        // Parse fields
        for (FieldDeclaration field : classDecl.getFields()) {
            for (VariableDeclarator var : field.getVariables()) {
                String fieldId = "field:" + fqcn + "." + var.getNameAsString();
                Map<String, String> fieldProps = new HashMap<>();
                fieldProps.put("name", var.getNameAsString());
                fieldProps.put("type", var.getTypeAsString());
                fieldProps.put("visibility", field.getAccessSpecifier().asString());
                fieldProps.put("static", String.valueOf(field.isStatic()));
                graph.addNode(new GraphNode(fieldId, NodeType.FIELD, fieldProps));
                graph.addEdge(new GraphEdge(classNodeId, fieldId, EdgeType.DECLARES, Map.of()));

                // Create USES_TYPE edge for field types
                String typeFqn = resolveFieldTypeName(var, packageName, cu);
                if (typeFqn != null && isAllowedType(typeFqn, graph, allowedPrefixes)) {
                    Map<String, String> edgeProps = new HashMap<>();
                    if (hasInjectionAnnotation(field)) {
                        edgeProps.put("injection", "true");
                    }
                    String framework = detectFramework(typeFqn);
                    if (framework != null) {
                        edgeProps.put("framework", framework);
                    }
                    String targetNodeId = "class:" + typeFqn;
                    ensureClassNode(targetNodeId, typeFqn, graph);
                    graph.addEdge(new GraphEdge(classNodeId, targetNodeId, EdgeType.USES_TYPE, edgeProps));
                }

                // Detect @Value and @ConfigProperty annotations
                detectConfigPropertyAnnotations(field, fieldId, graph);
            }
        }

        // Parse constructors for USES_TYPE on constructor parameters
        for (ConstructorDeclaration constructor : classDecl.getConstructors()) {
            for (com.github.javaparser.ast.body.Parameter param : constructor.getParameters()) {
                String typeFqn = resolveParameterTypeName(param, packageName, cu);
                if (typeFqn != null && isAllowedType(typeFqn, graph, allowedPrefixes)) {
                    Map<String, String> edgeProps = new HashMap<>();
                    if (hasAnnotation(param, INJECTION_ANNOTATIONS)) {
                        edgeProps.put("injection", "true");
                    }
                    String framework = detectFramework(typeFqn);
                    if (framework != null) {
                        edgeProps.put("framework", framework);
                    }
                    String targetNodeId = "class:" + typeFqn;
                    ensureClassNode(targetNodeId, typeFqn, graph);
                    graph.addEdge(new GraphEdge(classNodeId, targetNodeId, EdgeType.USES_TYPE, edgeProps));
                }
            }
        }

        // Parse methods
        for (MethodDeclaration method : classDecl.getMethods()) {
            String methodId = "method:" + fqcn + "." + method.getNameAsString();
            Map<String, String> methodProps = new HashMap<>();
            methodProps.put("name", method.getNameAsString());
            methodProps.put("returnType", method.getTypeAsString());
            methodProps.put("signature", method.getSignature().asString());
            methodProps.put("visibility", method.getAccessSpecifier().asString());
            methodProps.put("static", String.valueOf(method.isStatic()));
            graph.addNode(new GraphNode(methodId, NodeType.METHOD, methodProps));
            graph.addEdge(new GraphEdge(classNodeId, methodId, EdgeType.DECLARES, Map.of()));

            // Check for Camel DSL routes in configure() methods
            if ("configure".equals(method.getNameAsString())) {
                extractCamelRoutes(method, classNodeId, graph);
            }
        }
    }

    /**
     * Computes the set of allowed package prefixes based on MAVEN_ARTIFACT nodes in the graph. PomParser is guaranteed
     * to have finished before this method is called (it runs synchronously before other parsers).
     */
    private Set<String> computeAllowedPrefixes(ProjectGraph graph) {
        Set<String> prefixes = new HashSet<>();
        prefixes.add("org.apache.camel.");

        Collection<GraphNode> artifacts = graph.findByType(NodeType.MAVEN_ARTIFACT);

        for (GraphNode node : artifacts) {
            String artifactId = node.properties().get("artifactId");
            String groupId = node.properties().get("groupId");

            if (artifactId != null && artifactId.startsWith("camel-spring-boot")) {
                prefixes.add("org.springframework.");
            }
            if (artifactId != null && artifactId.startsWith("camel-quarkus")) {
                prefixes.add("jakarta.inject.");
                prefixes.add("jakarta.enterprise.");
                prefixes.add("io.quarkus.");
            }
            if (groupId != null && groupId.startsWith("org.mule")) {
                prefixes.add("org.mule.");
                prefixes.add("com.mulesoft.");
            }
        }
        return prefixes;
    }

    /**
     * Returns true if the type is project-local (already in the graph, or shares a package with a known project class)
     * or matches an allowed prefix.
     */
    private boolean isAllowedType(String typeFqn, ProjectGraph graph, Set<String> prefixes) {
        if (graph.hasNode("class:" + typeFqn)) {
            return true;
        }
        for (String prefix : prefixes) {
            if (typeFqn.startsWith(prefix)) {
                return true;
            }
        }
        // Check if the type's package matches any known project class package
        String typePackage = extractPackage(typeFqn);
        if (!typePackage.isEmpty()) {
            for (GraphNode node : graph.findByType(NodeType.CLASS)) {
                String nodePackage = node.properties().get("package");
                if (typePackage.equals(nodePackage)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Extracts the package portion from a fully qualified class name.
     */
    private String extractPackage(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot > 0 ? fqcn.substring(0, lastDot) : "";
    }

    /**
     * Ensures a CLASS node exists in the graph for the given type. If missing, creates a synthetic node.
     */
    private void ensureClassNode(String nodeId, String typeFqn, ProjectGraph graph) {
        if (!graph.hasNode(nodeId)) {
            String simpleName = typeFqn.contains(".")
                    ? typeFqn.substring(typeFqn.lastIndexOf('.') + 1)
                    : typeFqn;
            graph.addNode(new GraphNode(
                    nodeId, NodeType.CLASS,
                    Map.of("synthetic", "true", "name", simpleName)));
        }
    }

    /**
     * Detects bean annotations on a class declaration and sets bean/beanName properties.
     */
    private void detectBeanAnnotations(ClassOrInterfaceDeclaration classDecl, Map<String, String> classProps) {
        for (var ann : classDecl.getAnnotations()) {
            String annName = ann.getNameAsString();
            if (BEAN_ANNOTATIONS.contains(annName)) {
                classProps.put("bean", "true");
                String beanName = extractAnnotationValue(ann);
                if (beanName != null) {
                    classProps.put("beanName", beanName);
                }
                break;
            }
        }
    }

    /**
     * Extracts a string value from an annotation expression. Supports single-value annotations like
     * {@code @Named("orderService")} and member-value annotations like {@code @Service("paymentProcessor")}.
     */
    private String extractAnnotationValue(AnnotationExpr ann) {
        if (ann instanceof SingleMemberAnnotationExpr single) {
            Expression memberValue = single.getMemberValue();
            if (memberValue instanceof StringLiteralExpr str) {
                return str.getValue();
            }
        } else if (ann instanceof NormalAnnotationExpr normal) {
            for (var pair : normal.getPairs()) {
                if ("value".equals(pair.getNameAsString()) && pair.getValue() instanceof StringLiteralExpr str) {
                    return str.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Checks if a field has an injection annotation (@Inject or @Autowired).
     */
    private boolean hasInjectionAnnotation(FieldDeclaration field) {
        return field.getAnnotations().stream()
                .anyMatch(ann -> INJECTION_ANNOTATIONS.contains(ann.getNameAsString()));
    }

    /**
     * Checks if a parameter has any of the specified annotations.
     */
    private boolean hasAnnotation(com.github.javaparser.ast.body.Parameter param, Set<String> annotationNames) {
        return param.getAnnotations().stream()
                .anyMatch(ann -> annotationNames.contains(ann.getNameAsString()));
    }

    /**
     * Detects @Value("${key}") and @ConfigProperty(name="key") annotations on fields and creates INJECTS_INTO edges
     * from config nodes to field nodes.
     */
    private void detectConfigPropertyAnnotations(FieldDeclaration field, String fieldId, ProjectGraph graph) {
        for (var ann : field.getAnnotations()) {
            String annName = ann.getNameAsString();

            if ("Value".equals(annName)) {
                String rawValue = extractAnnotationValue(ann);
                if (rawValue != null) {
                    Matcher matcher = VALUE_PROPERTY_PATTERN.matcher(rawValue);
                    if (matcher.find()) {
                        String propertyKey = matcher.group(1);
                        createConfigInjectsIntoEdge(propertyKey, fieldId, graph);
                    }
                }
            } else if ("ConfigProperty".equals(annName) && ann instanceof NormalAnnotationExpr normal) {
                for (var pair : normal.getPairs()) {
                    if ("name".equals(pair.getNameAsString()) && pair.getValue() instanceof StringLiteralExpr str) {
                        createConfigInjectsIntoEdge(str.getValue(), fieldId, graph);
                    }
                }
            }
        }
    }

    /**
     * Creates or finds a config node and adds an INJECTS_INTO edge to the target field.
     */
    private void createConfigInjectsIntoEdge(String propertyKey, String fieldId, ProjectGraph graph) {
        String configNodeId = "config:" + propertyKey;
        if (!graph.hasNode(configNodeId)) {
            graph.addNode(new GraphNode(
                    configNodeId, NodeType.CONFIG_PROPERTY,
                    Map.of("name", propertyKey, "key", propertyKey, "missing", "true")));
        }
        graph.addEdge(new GraphEdge(configNodeId, fieldId, EdgeType.INJECTS_INTO, Map.of()));
    }

    /**
     * Detects the framework that a type belongs to based on its fully qualified name.
     */
    private String detectFramework(String typeFqn) {
        if (typeFqn.startsWith("org.apache.camel.")) {
            return "camel";
        }
        if (typeFqn.startsWith("org.springframework.")) {
            return "spring";
        }
        if (typeFqn.startsWith("jakarta.inject.") || typeFqn.startsWith("jakarta.enterprise.")
                || typeFqn.startsWith("io.quarkus.")) {
            return "quarkus";
        }
        if (typeFqn.startsWith("org.mule.") || typeFqn.startsWith("com.mulesoft.")) {
            return "mule";
        }
        return null;
    }

    /**
     * Resolves a field variable's type to a fully qualified name. Returns null for primitive types.
     */
    private String resolveFieldTypeName(VariableDeclarator var, String packageName, CompilationUnit cu) {
        var type = var.getType();
        if (type.isPrimitiveType() || type.isVoidType()) {
            return null;
        }
        if (type.isClassOrInterfaceType()) {
            return resolveTypeName(type.asClassOrInterfaceType(), packageName, cu);
        }
        return null;
    }

    /**
     * Resolves a constructor/method parameter's type to a fully qualified name. Returns null for primitive types.
     */
    private String resolveParameterTypeName(
            com.github.javaparser.ast.body.Parameter param, String packageName,
            CompilationUnit cu) {
        var type = param.getType();
        if (type.isPrimitiveType() || type.isVoidType()) {
            return null;
        }
        if (type.isClassOrInterfaceType()) {
            return resolveTypeName(type.asClassOrInterfaceType(), packageName, cu);
        }
        return null;
    }

    private void extractCamelRoutes(
            MethodDeclaration configureMethod, String classNodeId,
            ProjectGraph graph) {
        // Find all from() calls - these are the root of Camel route definitions.
        // In the JavaParser AST for a fluent chain like:
        //   from("kafka:orders").routeId("processOrders").bean(...).to("direct:enrichOrder")
        // the outermost MethodCallExpr is .to(), and from() is nested deep inside as scopes.
        // We find from() calls and then walk UP (through parent nodes) to collect the full chain.
        List<MethodCallExpr> fromCalls = configureMethod.findAll(MethodCallExpr.class,
                mce -> "from".equals(mce.getNameAsString()) && !mce.getScope().isPresent());

        for (MethodCallExpr fromCall : fromCalls) {
            extractRouteFromChain(fromCall, classNodeId, graph);
        }
    }

    private void extractRouteFromChain(
            MethodCallExpr fromCall, String classNodeId,
            ProjectGraph graph) {
        // Extract the from URI
        String fromUri = extractFirstStringArg(fromCall);
        if (fromUri == null) {
            return;
        }

        // Collect all method calls in the fluent chain by walking UP through parents.
        // In JavaParser, from("x").routeId("y").to("z") is structured as:
        //   to("z") { scope = routeId("y") { scope = from("x") } }
        // So from() is the innermost scope. We walk up by finding MethodCallExpr nodes
        // that use the current expression as their scope.
        List<MethodCallExpr> chainCalls = collectChainCalls(fromCall);

        // Extract routeId
        String routeId = null;
        for (MethodCallExpr call : chainCalls) {
            if ("routeId".equals(call.getNameAsString())) {
                routeId = extractFirstStringArg(call);
                break;
            }
        }
        if (routeId == null) {
            // Generate a fallback route ID from the URI
            routeId = fromUri.replace(":", "_").replace("/", "_");
        }

        String routeNodeId = "route:" + routeId;

        // Create route node
        Map<String, String> routeProps = new HashMap<>();
        routeProps.put("fromUri", fromUri);
        routeProps.put("routeId", routeId);
        graph.addNode(new GraphNode(routeNodeId, NodeType.CAMEL_ROUTE, routeProps));

        // Create from endpoint
        String fromEndpointId = "endpoint:" + fromUri;
        graph.addNode(new GraphNode(
                fromEndpointId, NodeType.CAMEL_ENDPOINT,
                Map.of("uri", fromUri)));
        graph.addEdge(new GraphEdge(routeNodeId, fromEndpointId, EdgeType.ROUTES_FROM, Map.of()));

        // Link route to declaring class
        graph.addEdge(new GraphEdge(classNodeId, routeNodeId, EdgeType.DECLARES, Map.of()));

        // Extract to() endpoints and processors from the chain
        int processorOrder = 0;
        for (MethodCallExpr call : chainCalls) {
            String methodName = call.getNameAsString();

            if ("to".equals(methodName) || "toD".equals(methodName)) {
                String toUri = extractFirstStringArg(call);
                if (toUri != null) {
                    String toEndpointId = "endpoint:" + toUri;
                    graph.addNode(new GraphNode(
                            toEndpointId, NodeType.CAMEL_ENDPOINT,
                            Map.of("uri", toUri)));
                    graph.addEdge(new GraphEdge(
                            routeNodeId, toEndpointId,
                            EdgeType.ROUTES_TO, Map.of()));
                }
            } else if (CAMEL_PROCESSOR_METHODS.contains(methodName)) {
                String processorId = "processor:" + routeId + "." + methodName;
                // Avoid duplicate processor IDs by appending a counter if needed
                if (graph.hasNode(processorId)) {
                    int counter = 2;
                    while (graph.hasNode(processorId + "_" + counter)) {
                        counter++;
                    }
                    processorId = processorId + "_" + counter;
                }
                graph.addNode(new GraphNode(
                        processorId, NodeType.CAMEL_PROCESSOR,
                        Map.of("type", methodName)));
                graph.addEdge(new GraphEdge(
                        routeNodeId, processorId,
                        EdgeType.PROCESSES, Map.of("order", String.valueOf(processorOrder++))));
            }
        }
    }

    /**
     * Collects all method calls in a fluent chain starting from the given call, walking UP through parent
     * MethodCallExpr nodes.
     *
     * In JavaParser's AST, the chain {@code from("x").routeId("y").to("z")} is:
     *
     * <pre>
     *   to("z") {
     *     scope = routeId("y") {
     *       scope = from("x")
     *     }
     *   }
     * </pre>
     *
     * So from() is at the bottom. We walk up by finding parent MethodCallExpr nodes that contain the current expression
     * as part of their scope chain.
     */
    private List<MethodCallExpr> collectChainCalls(MethodCallExpr fromCall) {
        List<MethodCallExpr> chain = new ArrayList<>();

        // Walk up from the from() call through parent MethodCallExpr nodes.
        // Each parent MethodCallExpr whose scope chain leads back to our current
        // expression is part of the same fluent chain.
        com.github.javaparser.ast.Node current = fromCall;
        while (current.getParentNode().isPresent()) {
            com.github.javaparser.ast.Node parent = current.getParentNode().get();
            if (parent instanceof MethodCallExpr parentCall) {
                chain.add(parentCall);
                current = parent;
            } else {
                break;
            }
        }

        return chain;
    }

    private String extractFirstStringArg(MethodCallExpr call) {
        if (call.getArguments().isEmpty()) {
            return null;
        }
        var firstArg = call.getArgument(0);
        if (firstArg instanceof StringLiteralExpr strLiteral) {
            return strLiteral.getValue();
        }
        return null;
    }

    /**
     * Resolves a type name to a fully qualified name. Checks import declarations first, then falls back to assuming the
     * same package as the declaring class.
     */
    private String resolveTypeName(ClassOrInterfaceType type, String packageName, CompilationUnit cu) {
        String name = type.getNameAsString();
        if (name.contains(".")) {
            return name; // Already qualified
        }
        // Check explicit imports
        if (cu != null) {
            for (ImportDeclaration imp : cu.getImports()) {
                if (!imp.isAsterisk() && imp.getNameAsString().endsWith("." + name)) {
                    return imp.getNameAsString();
                }
            }
        }
        // java.lang.* types are implicitly imported — don't resolve as same-package
        if (JAVA_LANG_TYPES.contains(name)) {
            return "java.lang." + name;
        }
        // Fallback: assume same package
        return packageName.isEmpty() ? name : packageName + "." + name;
    }
}

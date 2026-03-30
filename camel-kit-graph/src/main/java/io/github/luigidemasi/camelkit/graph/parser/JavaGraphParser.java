package io.github.luigidemasi.camelkit.graph.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class JavaGraphParser implements GraphParser {

    private static final Set<String> CAMEL_PROCESSOR_METHODS = Set.of(
        "bean", "process", "marshal", "unmarshal", "filter", "split",
        "aggregate", "enrich", "pollEnrich", "transform", "convertBodyTo",
        "log", "choice", "multicast", "recipientList", "wireTap",
        "throttle", "delay", "setHeader", "setBody", "removeHeader"
    );

    @Override
    public void parse(Path projectRoot, ProjectGraph graph) {
        JavaParser parser = new JavaParser();
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        parseJavaFile(parser, file, projectRoot, graph);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk project for Java files", e);
        }
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
                parseClass(classDecl, packageName, projectRoot, file, graph);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Java file: " + file, e);
        }
    }

    private void parseClass(ClassOrInterfaceDeclaration classDecl, String packageName,
                            Path projectRoot, Path file, ProjectGraph graph) {
        String fqcn = packageName.isEmpty() ? classDecl.getNameAsString()
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
        graph.addNode(new GraphNode(classNodeId, NodeType.CLASS, classProps));

        // Parse extends
        for (ClassOrInterfaceType extended : classDecl.getExtendedTypes()) {
            String parentFqcn = resolveTypeName(extended, packageName);
            String parentNodeId = "class:" + parentFqcn;
            graph.addEdge(new GraphEdge(classNodeId, parentNodeId, EdgeType.EXTENDS, Map.of()));
        }

        // Parse implements
        for (ClassOrInterfaceType implemented : classDecl.getImplementedTypes()) {
            String ifaceFqcn = resolveTypeName(implemented, packageName);
            String ifaceNodeId = "class:" + ifaceFqcn;
            graph.addEdge(new GraphEdge(classNodeId, ifaceNodeId, EdgeType.IMPLEMENTS, Map.of()));
        }

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

    private void extractCamelRoutes(MethodDeclaration configureMethod, String classNodeId,
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

    private void extractRouteFromChain(MethodCallExpr fromCall, String classNodeId,
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
        graph.addNode(new GraphNode(fromEndpointId, NodeType.CAMEL_ENDPOINT,
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
                    graph.addNode(new GraphNode(toEndpointId, NodeType.CAMEL_ENDPOINT,
                        Map.of("uri", toUri)));
                    graph.addEdge(new GraphEdge(routeNodeId, toEndpointId,
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
                graph.addNode(new GraphNode(processorId, NodeType.CAMEL_PROCESSOR,
                    Map.of("type", methodName)));
                graph.addEdge(new GraphEdge(routeNodeId, processorId,
                    EdgeType.PROCESSES, Map.of("order", String.valueOf(processorOrder++))));
            }
        }
    }

    /**
     * Collects all method calls in a fluent chain starting from the given call,
     * walking UP through parent MethodCallExpr nodes.
     *
     * In JavaParser's AST, the chain {@code from("x").routeId("y").to("z")} is:
     * <pre>
     *   to("z") {
     *     scope = routeId("y") {
     *       scope = from("x")
     *     }
     *   }
     * </pre>
     *
     * So from() is at the bottom. We walk up by finding parent MethodCallExpr
     * nodes that contain the current expression as part of their scope chain.
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
     * Resolves a type name to a fully qualified name.
     * For simple names (no dots), assumes same package as the declaring class.
     */
    private String resolveTypeName(ClassOrInterfaceType type, String packageName) {
        String name = type.getNameAsString();
        if (name.contains(".")) {
            return name;
        }
        return packageName.isEmpty() ? name : packageName + "." + name;
    }
}

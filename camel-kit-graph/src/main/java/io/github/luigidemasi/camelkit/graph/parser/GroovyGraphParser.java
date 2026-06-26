package io.github.luigidemasi.camelkit.graph.parser;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;

public class GroovyGraphParser implements GraphParser {

    private static final Set<String> CAMEL_PROCESSOR_METHODS = Set.of(
            "bean", "process", "marshal", "unmarshal", "filter", "split",
            "aggregate", "enrich", "pollEnrich", "transform", "convertBodyTo",
            "log", "choice", "multicast", "recipientList", "wireTap",
            "throttle", "delay", "setHeader", "setBody", "removeHeader");

    @Override
    public void parse(Path projectRoot, ProjectGraph graph) {
        parseFiles(projectRoot, scannedFilePaths(projectRoot, GraphParser.projectFiles(projectRoot)), graph);
    }

    @Override
    public List<String> scannedFiles(Path projectRoot) {
        return GraphParser.findFiles(projectRoot, file -> file.toString().endsWith(".groovy"));
    }

    @Override
    public List<Path> scannedFilePaths(Path projectRoot, List<Path> projectFiles) {
        return GraphParser.findFilePaths(projectRoot, projectFiles, file -> file.toString().endsWith(".groovy"));
    }

    @Override
    public void parseFiles(Path projectRoot, List<Path> files, ProjectGraph graph) {
        files.forEach(file -> parseGroovyFile(file, projectRoot, graph));
    }

    private void parseGroovyFile(Path file, Path projectRoot, ProjectGraph graph) {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setTargetBytecode(CompilerConfiguration.JDK17);

        try (GroovyClassLoader classLoader = new GroovyClassLoader(
                Thread.currentThread().getContextClassLoader(), config)) {

            CompilationUnit compilationUnit = new CompilationUnit(config, null, classLoader);
            compilationUnit.addSource(file.toFile());
            compilationUnit.compile(Phases.CONVERSION);

            // Get the modules from the compilation unit
            List<ModuleNode> modules = new ArrayList<>();
            Iterator<org.codehaus.groovy.control.SourceUnit> iter = compilationUnit.iterator();
            while (iter.hasNext()) {
                org.codehaus.groovy.control.SourceUnit srcUnit = iter.next();
                ModuleNode module = srcUnit.getAST();
                if (module != null) {
                    modules.add(module);
                }
            }

            for (ModuleNode module : modules) {
                processModule(module, file, projectRoot, graph);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Groovy file: " + file, e);
        }
    }

    private void processModule(ModuleNode module, Path file, Path projectRoot, ProjectGraph graph) {
        List<ClassNode> classes = module.getClasses();
        String fileName = file.getFileName().toString();
        String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));

        // Determine if this is a standalone script.
        // Groovy always generates a class from a script, and the generated class name
        // matches the filename. A "real" class file will have an explicitly declared class
        // whose name matches a user-defined class (not the auto-generated script class).
        boolean isStandaloneScript = isScript(classes, fileNameWithoutExt);

        if (isStandaloneScript) {
            String relativePath = projectRoot.relativize(file).toString().replace('\\', '/');
            String resourceId = "resource:" + relativePath;
            graph.addNode(new GraphNode(
                    resourceId, NodeType.RESOURCE_FILE,
                    Map.of("file", relativePath, "language", "groovy")));
            return;
        }

        // Process declared classes
        for (ClassNode classNode : classes) {
            // Skip the auto-generated script class (named after the file)
            if (classNode.getName().equals(fileNameWithoutExt)
                    && classNode.getSuperClass() != null
                    && classNode.getSuperClass().getName().contains("Script")) {
                continue;
            }
            parseClass(classNode, file, projectRoot, graph);
        }
    }

    /**
     * Determines if the module represents a standalone Groovy script (no user-defined class). Groovy compiles scripts
     * into a class whose name matches the filename and extends Script. If the only classes are script-generated ones,
     * this is a standalone script.
     */
    private boolean isScript(List<ClassNode> classes, String fileNameWithoutExt) {
        for (ClassNode classNode : classes) {
            String className = classNode.getNameWithoutPackage();
            // If there's a class whose name does NOT match the filename,
            // or whose superclass is not Script, this is not a pure script
            if (!className.equals(fileNameWithoutExt)) {
                return false;
            }
            // Check if the class extends groovy.lang.Script (auto-generated script class)
            ClassNode superClass = classNode.getSuperClass();
            if (superClass != null && !superClass.getName().contains("Script")) {
                return false;
            }
        }
        return true;
    }

    private void parseClass(ClassNode classNode, Path file, Path projectRoot, ProjectGraph graph) {
        String packageName = classNode.getPackageName();
        if (packageName == null) {
            packageName = "";
        }
        String simpleName = classNode.getNameWithoutPackage();
        String fqcn = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        String classNodeId = "class:" + fqcn;

        Map<String, String> classProps = new HashMap<>();
        classProps.put("name", simpleName);
        classProps.put("fqn", fqcn);
        classProps.put("package", packageName);
        classProps.put("file", projectRoot.relativize(file).toString());
        classProps.put("interface", String.valueOf(classNode.isInterface()));
        if (classNode.isAbstract()) {
            classProps.put("abstract", "true");
        }
        graph.addNode(new GraphNode(classNodeId, NodeType.CLASS, classProps));

        // Parse extends
        ClassNode superClass = classNode.getSuperClass();
        if (superClass != null && !"java.lang.Object".equals(superClass.getName())) {
            String parentFqcn = superClass.getName();
            String parentNodeId = "class:" + parentFqcn;
            graph.addEdge(new GraphEdge(classNodeId, parentNodeId, EdgeType.EXTENDS, Map.of()));
        }

        // Parse implements
        for (ClassNode iface : classNode.getInterfaces()) {
            String ifaceFqcn = iface.getName();
            String ifaceNodeId = "class:" + ifaceFqcn;
            graph.addEdge(new GraphEdge(classNodeId, ifaceNodeId, EdgeType.IMPLEMENTS, Map.of()));
        }

        // Parse methods
        for (MethodNode method : classNode.getMethods()) {
            // Skip synthetic methods generated by Groovy
            if (method.isSynthetic()) {
                continue;
            }
            String methodId = "method:" + fqcn + "." + method.getName();
            Map<String, String> methodProps = new HashMap<>();
            methodProps.put("name", method.getName());
            methodProps.put("returnType", method.getReturnType().getNameWithoutPackage());
            graph.addNode(new GraphNode(methodId, NodeType.METHOD, methodProps));
            graph.addEdge(new GraphEdge(classNodeId, methodId, EdgeType.DECLARES, Map.of()));

            // Check for Camel DSL routes in configure() methods
            if ("configure".equals(method.getName())) {
                extractCamelRoutes(method, classNodeId, graph);
            }
        }
    }

    private void extractCamelRoutes(MethodNode configureMethod, String classNodeId, ProjectGraph graph) {
        Statement code = configureMethod.getCode();
        if (code == null) {
            return;
        }

        // Collect all method call expressions in the method body
        List<MethodCallExpression> allCalls = new ArrayList<>();
        collectMethodCalls(code, allCalls);

        // Find from() calls -- these are the roots of route definitions
        for (MethodCallExpression call : allCalls) {
            if ("from".equals(call.getMethodAsString())) {
                extractRouteFromChain(call, classNodeId, allCalls, graph);
            }
        }
    }

    private void extractRouteFromChain(
            MethodCallExpression fromCall, String classNodeId,
            List<MethodCallExpression> allCalls, ProjectGraph graph) {
        String fromUri = extractFirstStringArg(fromCall);
        if (fromUri == null) {
            return;
        }

        // Collect the fluent chain. In Groovy's AST, the chain
        //   from("x").routeId("y").to("z")
        // is represented with nested MethodCallExpression nodes where each call's
        // objectExpression (scope) is the previous call.
        // We walk UP from the from() call to collect the full chain.
        List<MethodCallExpression> chain = collectChainFromRoot(fromCall, allCalls);

        // Extract routeId
        String routeId = null;
        for (MethodCallExpression call : chain) {
            if ("routeId".equals(call.getMethodAsString())) {
                routeId = extractFirstStringArg(call);
                break;
            }
        }
        if (routeId == null) {
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
        for (MethodCallExpression call : chain) {
            String methodName = call.getMethodAsString();
            if (methodName == null) {
                continue;
            }

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
            } else if ("script".equals(methodName)) {
                // Handle script("groovy", "...") calls
                String scriptType = extractScriptType(call);
                if (scriptType != null) {
                    String processorId = "processor:" + routeId + ".script-" + scriptType;
                    if (graph.hasNode(processorId)) {
                        int counter = 2;
                        while (graph.hasNode(processorId + "_" + counter)) {
                            counter++;
                        }
                        processorId = processorId + "_" + counter;
                    }
                    graph.addNode(new GraphNode(
                            processorId, NodeType.CAMEL_PROCESSOR,
                            Map.of("type", "script-" + scriptType)));
                    graph.addEdge(new GraphEdge(
                            routeNodeId, processorId,
                            EdgeType.PROCESSES, Map.of("order", String.valueOf(processorOrder++))));
                }
            } else if (CAMEL_PROCESSOR_METHODS.contains(methodName)) {
                String processorId = "processor:" + routeId + "." + methodName;
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
     * Collects the fluent chain calls that follow the given root call. In Groovy AST, for
     * from("x").routeId("y").to("z"), the structure is: MethodCallExpression[to] -> objectExpression:
     * MethodCallExpression[routeId] -> objectExpression: MethodCallExpression[from] So we find all calls in allCalls
     * whose objectExpression chain leads back to fromCall.
     */
    private List<MethodCallExpression> collectChainFromRoot(
            MethodCallExpression fromCall,
            List<MethodCallExpression> allCalls) {
        List<MethodCallExpression> chain = new ArrayList<>();
        // Find the call whose objectExpression IS the fromCall
        MethodCallExpression current = fromCall;
        boolean found = true;
        while (found) {
            found = false;
            for (MethodCallExpression candidate : allCalls) {
                if (candidate.getObjectExpression() == current) {
                    chain.add(candidate);
                    current = candidate;
                    found = true;
                    break;
                }
            }
        }
        return chain;
    }

    /**
     * Recursively collects all MethodCallExpression nodes in a statement.
     */
    private void collectMethodCalls(ASTNode node, List<MethodCallExpression> result) {
        if (node instanceof MethodCallExpression mce) {
            result.add(mce);
            // Also recurse into arguments and objectExpression
            collectMethodCalls(mce.getObjectExpression(), result);
            collectMethodCalls(mce.getArguments(), result);
        } else if (node instanceof BlockStatement block) {
            for (Statement stmt : block.getStatements()) {
                collectMethodCalls(stmt, result);
            }
        } else if (node instanceof ExpressionStatement exprStmt) {
            collectMethodCalls(exprStmt.getExpression(), result);
        } else if (node instanceof ReturnStatement retStmt) {
            collectMethodCalls(retStmt.getExpression(), result);
        } else if (node instanceof ArgumentListExpression argList) {
            for (Expression expr : argList.getExpressions()) {
                collectMethodCalls(expr, result);
            }
        } else if (node instanceof ClosureExpression closure) {
            if (closure.getCode() != null) {
                collectMethodCalls(closure.getCode(), result);
            }
        } else if (node instanceof TupleExpression tuple) {
            for (Expression expr : tuple.getExpressions()) {
                collectMethodCalls(expr, result);
            }
        }
    }

    /**
     * Extracts the first string argument from a method call.
     */
    private String extractFirstStringArg(MethodCallExpression call) {
        Expression args = call.getArguments();
        if (args instanceof ArgumentListExpression argList) {
            List<Expression> expressions = argList.getExpressions();
            if (!expressions.isEmpty() && expressions.get(0) instanceof ConstantExpression constant) {
                Object value = constant.getValue();
                if (value instanceof String) {
                    return (String) value;
                }
            }
        } else if (args instanceof TupleExpression tuple) {
            List<Expression> expressions = tuple.getExpressions();
            if (!expressions.isEmpty() && expressions.get(0) instanceof ConstantExpression constant) {
                Object value = constant.getValue();
                if (value instanceof String) {
                    return (String) value;
                }
            }
        }
        return null;
    }

    /**
     * Extracts the script type from a script("groovy", "...") call. Returns the first argument (the language name),
     * e.g. "groovy".
     */
    private String extractScriptType(MethodCallExpression call) {
        Expression args = call.getArguments();
        if (args instanceof ArgumentListExpression argList) {
            List<Expression> expressions = argList.getExpressions();
            if (expressions.size() >= 2
                    && expressions.get(0) instanceof ConstantExpression constant
                    && constant.getValue() instanceof String) {
                return (String) constant.getValue();
            }
        }
        return null;
    }
}

# Orchestrator Guide

This guide defines file paths and execution order for the implementation pipeline. It adapts to the target runtime and works with both greenfield and migration design specs.

> **YOUR JOB IS TO GENERATE FILES, NOT TO DISCUSS WHAT FILES YOU WOULD GENERATE.**
> After reading this guide, immediately start executing Step 1 (or Step 2 if no DataMapper). Do NOT summarize what you read, do NOT discuss the complexity of the task, do NOT present alternatives, do NOT say "this would involve..." — just start writing code. Every step below produces concrete files on disk. If you reach the end of this guide without having written files, you have failed.

> **"Load" means READ and FOLLOW.** Every time this guide says "Load `guides/xyz.md`", you MUST read that file from the skill directory where this guide lives and execute its instructions. Do NOT skip a step because you haven't read the guide yet — read it, then do what it says. The guide files ARE present in the same directory as this file.

> **Context variables from master SKILL.md:**
> - `RUNTIME` — `main` (default Camel Main packaging), `spring-boot`, or `quarkus`
> - `FLOW_NAME`, `CAMEL_VERSION`, `TARGET_MODULE`
> - `MODULE_NAME`, plus complete module `ROUTE_FILES` and `XSL_FILES` inventories for consolidated runtime artifacts
> - `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via `skills/shared/mcp-setup.md`

**Migration design specs:** If the active flow design contains a "Migrated From" field, handle its additional artifacts
alongside the standard pipeline steps. Camel Main designs must already have translated all Java/Blueprint logic to
supported YAML/inline Groovy. Retained Java classes, dependency-injection configuration, or Maven plugins require Spring
Boot or Quarkus. Do NOT skip the standard pipeline — migration flows still need route YAML, properties, and every
conditional artifact required by their design.

---

## File Path Table

Resolve paths based on runtime. Define `{MODULE_PREFIX}` as the `Target Module` plus one trailing `/` when the target
module is non-empty. At the project root, `{MODULE_PREFIX}` is the empty string; omit the whole prefix (never resolve it
to `/`).

| File Type | Main (JBang CLI) | Spring Boot / Quarkus |
|-----------|------------------|-----------------------|
| `{flow-name}.camel.yaml` | `{MODULE_PREFIX}` | `{MODULE_PREFIX}src/main/resources/camel/` |
| `kaoto-datamapper-*.xsl` | `{MODULE_PREFIX}` | `{MODULE_PREFIX}src/main/resources/camel/` |
| `application.properties` | `{MODULE_PREFIX}` | `{MODULE_PREFIX}src/main/resources/` |
| `schemas/{flow-name}-*.json` | `{MODULE_PREFIX}schemas/` | `{MODULE_PREFIX}src/main/resources/schemas/` |
| `docker-compose.yaml` | `{MODULE_PREFIX}` | `{MODULE_PREFIX}` |
| `.kaoto` (XSLT DataMapper only) | Project root | Project root |
| `run.sh` (main runtime only) | `{MODULE_PREFIX}` | N/A |

Assign these as context variables for all subsequent steps:
- `ROUTE_DIR` — route/datamapper location from table above
- `PROPS_DIR` — application.properties location from table above
- `SCHEMA_DIR` — schemas location from table above
- `MODULE_DIR` = `{MODULE_PREFIX}`

---

## Execution Order

### Step 0: Project Context from Graph (CONDITIONAL)

**IF** `.camel-kit/project-graph.json` exists:
- Load `guides/graph-project-context.md`
- Pass: `FLOW_NAME`, `CAMEL_VERSION`, `RUNTIME`

**SKIP** if no project graph exists.

### Step 1: DataMapper (CONDITIONAL)

**IF** the active flow design contains `### DataMapper: kaoto-datamapper-{id}` sections:
- Load `guides/datamapper-validation.md` (shared — Steps 1, 1.5, 2, 3.5, 5-7)
- Branch first on the design spec's Transformation Engine:
  - `Groovy (inline)` → load `guides/datamapper-groovy.md` (Steps 3, 4)
  - `XSLT` or absent → branch on XSLT Approach:
    - Approach A or N/A → load `guides/datamapper-approach-a.md` (Steps 3, 4)
    - Approach B → load `guides/datamapper-approach-b.md` (Steps 3, 4)
- Pass: `FLOW_NAME`, file locations from the table above

**SKIP** if no DataMapper sections exist in the design spec.

### Step 2: Route Generation (ALWAYS)

- Load `guides/component-loading.md` (Step 2: component documentation)
- Load `guides/yaml-catalog-rules.md` (Rules 0-0h: catalog verification rules)
- Load `guides/yaml-structure.md` (Step 3: route structure and generation)
- Load `guides/route-validation.md` (Step 4: validation loop)
- Pass:
  - `FLOW_NAME`
  - `ROUTE_DIR` (from path table)
  - `ROUTE_FILE` = `{flow-name}.camel.yaml`
  - `CAMEL_VERSION`
  - `TARGET_MODULE`

### Step 3: Properties (ALWAYS)

- Load `guides/properties-generation.md`
- Pass:
  - `FLOW_NAME`
  - `PROPS_DIR` (from path table)
  - `CAMEL_VERSION`
  - `RUNTIME`

**Main runtime only:** INCLUDE `camel.jbang.dependencies` section listing all required Camel dependencies.
**Spring Boot / Quarkus:** Do NOT include `camel.jbang.dependencies` — dependencies are managed via Maven.

The properties validation gate (properties-generation.md §5.4) is part of this step's completion criteria — Step 3 is not complete until `camel_configuration_validate` passes or the documented manual fallback is recorded.

### Step 4: Docker Compose (CONDITIONAL)

**IF** the design spec lists one or more external service dependencies:
- Load `guides/docker-compose.md`
- Pass:
  - `MODULE_NAME`
  - `MODULE_DIR`
  - `ROUTE_FILES` (every module `.camel.yaml` file)
  - `XSL_FILES` (every module XSLT DataMapper file; may be empty)
  - `CAMEL_VERSION`
  - `RUNTIME`
  - `DOCKER_IMAGE`:
    - Main: `apache/camel-jbang:{CAMEL_VERSION}`
    - Spring Boot / Quarkus: application-specific (built from project, not a generic Camel image)

**SKIP** if the design spec has no external service dependencies, regardless of runtime.

### Step 5: Runtime-Specific Artifacts (ALWAYS)

**Main runtime:**
- Load `guides/run-script.md`
- Pass: `MODULE_NAME`, `MODULE_DIR`, `ROUTE_FILES` (every module route), `XSL_FILES` (every module XSLT file)

**Spring Boot / Quarkus:**
- Load `guides/maven-dependencies.md`
- Pass: `FLOW_NAME`, `MODULE_DIR`, `CAMEL_VERSION`, `RUNTIME`

### Step 5.5: Migration Artifacts (CONDITIONAL)

**IF** the active flow design contains a "Migrated From" field (migration scenario):

0. **Runtime safety:** If `RUNTIME == main` and the design retains any Java processor, bean, configuration class,
   Blueprint wiring, or Maven plugin, STOP. Return to migration runtime selection and require Spring Boot or Quarkus.

1. **Java source files (Spring Boot/Quarkus only):** If the design spec references Java processors, beans, or
   configuration classes, create them in `{MODULE_PREFIX}src/main/java/` using the package structure from the design
   spec.
   Copy the logic from the source and adapt it to Camel 4.x (Jakarta imports, updated API calls).

2. **Non-route files:** Copy WSDL, XSD, and other resources to the runtime-aware paths specified in the approved design,
   preserving its directory structure.

3. **Maven plugins (Spring Boot/Quarkus only):** Add required build plugins (for example CXF codegen or JAXB) to the
   `<build><plugins>` section of `{MODULE_PREFIX}pom.xml`.

4. **Dependency-injection configuration (Spring Boot/Quarkus only):** Create configuration classes or bean definitions
   beyond `application.properties` only when the approved design specifies them.

**SKIP** if the design spec does not contain a "Migrated From" field.

### Step 5.6: Sequential HTTP Calls (CONDITIONAL)

**IF** the design spec contains both an HTTP consumer (`platform-http`, `servlet`, `jetty`, `netty-http`) **AND** one or more outbound HTTP producer calls (`http`, `https`, `undertow`, `vertx-http`):
- Load `guides/sequential-http-calls.md` for detailed implementation guidance
- Apply header sanitization rules between HTTP endpoints

**Note:** Rule 0e in `yaml-catalog-rules.md` already enforces the basic `removeHeaders` pattern inline during route generation. This guide provides additional context, edge cases, and examples beyond the inline rule.

**SKIP** if the route does not involve multiple HTTP endpoints.

### Step 6: Advanced Patterns (CONDITIONAL)

**IF** the design spec contains a "Performance & Reliability" section **OR** a "Security" section:
- Load `guides/advanced-patterns.md`

**SKIP** if neither section exists in the design spec.

### Step 7: Schemas (CONDITIONAL)

**IF** schemas were missing in pre-checks **AND** the user chose to generate them:
- Load `guides/schema-generation.md`
- Pass:
  - `FLOW_NAME`
  - `SCHEMA_DIR` (from path table)

**SKIP** if schemas already exist or user declined generation.

## Step 8: Completion Gate (ALWAYS — MANDATORY, DO NOT SKIP)

Before showing the Implementation Summary, verify that implementation actually happened by checking files on disk. Run `test -s` for each expected file (tests for existence AND non-empty). This is the final defense against showing a success summary when no files were generated.

### 9.1 Required Files Check

Verify these files exist and are non-empty:

| Check | Path | Condition |
|-------|------|-----------|
| Route YAML | `{ROUTE_DIR}{flow-name}.camel.yaml` | MUST exist, MUST be non-empty |
| Properties | `{PROPS_DIR}application.properties` | MUST exist |
| Docker Compose | `{MODULE_DIR}docker-compose.yaml` | MUST exist and be non-empty if the design spec lists external services |
| Maven POM | `{MODULE_DIR}pom.xml` | MUST exist and be non-empty (Spring Boot / Quarkus only) |
| Run script | `{MODULE_DIR}run.sh` | MUST exist (main runtime only) |

**If the route YAML does not exist or is empty, STOP.** Do not show the Implementation Summary. Go back to Step 2 (Route Generation) and actually generate the file. This check exists because the most common failure mode is the AI reading guides without executing them.

### 9.2 Design Spec Conformance Check

Open the generated `{flow-name}.camel.yaml` and verify it against the design spec:

| Check | What to verify | How |
|-------|---------------|-----|
| Source component | Route `from:` uses the component specified in the design spec Source System section | Read route YAML, check `from.uri` |
| Sink component | Route contains a `to:` step using the component specified in the design spec Sink System section | Read route YAML, check `to.uri` |
| Route ID | Route declares `id: {flow-name}` | Read route YAML, check `id` field |
| Error handling | Route includes error handling matching the design spec strategy (DLC, retry, etc.) | Read route YAML, check for `onException` or `errorHandler` |
| Placeholders | No hardcoded hostnames, ports, or credentials in route YAML — all use `{{placeholder}}` syntax | Scan route YAML for literal URLs or credentials |

Report each check as PASS or FAIL. Failing checks are **warnings** (do not block the summary), but they must be visible in the output so the user knows what to review.

### 9.3 Gate Result

```
Completion Gate:
  Files:
    ✓ {flow-name}.camel.yaml exists ({N} lines)
    ✓ application.properties exists
    ✓ docker-compose.yaml exists [IF Step 4 ran]
    ✓ pom.xml exists [Spring Boot/Quarkus]
  Design Spec Conformance:
    ✓ Source: [component] matches design spec
    ✓ Sink: [component] matches design spec
    ✓ Route ID: {flow-name}
    ✓ Error handling: [strategy] matches design spec
    ⚠ Hardcoded value found: [detail]  ← example warning
```

### 9.4 — Graph Rebuild Note (CONDITIONAL)

If `.camel-kit/project-graph.json` already exists (adding to existing project or migration):

```
Note: The project graph may be stale — it was built before this
implementation. Run `{COMMAND_PREFIX} init --force` with the current
project settings to rebuild it before the
execute phase's final runtime verification and the Phase 4 static validation.
```

If no graph exists: skip silently. No suggestion.

---

## Standalone Implementation Summary

**PREREQUISITE:** You can only show this summary if you have completed Step 8 (Completion Gate). If the route YAML does not exist on disk, go back and generate it.

Display this summary only when `camel-implement` is run standalone. When this guide is invoked by `camel-execute`,
return only the status token requested by `camel-execute`; do not print a summary or next steps between tasks.

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
IMPLEMENTATION COMPLETE: {flow-name}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Runtime: {RUNTIME}

Generated Files:

  ✓ {flow-name}.camel.yaml
    Location: {ROUTE_DIR}
    Route ID: {flow-name}
    Source: [component]:{{source.endpoint}}
    Sink: [component]:{{sink.endpoint}}
    Validation: PASSED ✅ (MCP verified)

  ✓ DataMapper artifacts [IF Step 1 ran]
    Location: {ROUTE_DIR}
    See datamapper-validation.md Step 7 checklist for details

  ✓ application.properties
    Location: {PROPS_DIR}
    Component config: [list components]
    Bean definitions: [list beans]
    Route placeholders: [count]

  ✓ docker-compose.yaml [IF Step 4 ran]
    Location: {MODULE_DIR}
    Services: [list services]

  ✓ run.sh [main runtime only — IF Step 5 ran]
    Location: {MODULE_DIR}
    Executable script to start integration

  ✓ Maven dependencies added to pom.xml [Spring Boot/Quarkus only — IF Step 5 ran]
    Location: {MODULE_DIR}pom.xml

  ✓ schemas/{flow-name}-input.json [IF Step 7 ran]
    Location: {SCHEMA_DIR}
    Input data schema

  ✓ schemas/{flow-name}-output.json [IF Step 7 ran]
    Location: {SCHEMA_DIR}
    Output data schema

Dependencies (from design spec):
  - camel-[component1]
  - camel-[component2]
  - [external dependencies]

Completion Gate: ✅ ALL CHECKS PASSED / ⚠️ [N] warnings

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Standalone Next Steps

Display this block only when `camel-implement` is run standalone.

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
NEXT STEPS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Review generated files

2. Continue with remaining implementation tasks

3. After all tasks complete, runtime verification runs once.
   In a chained pipeline, /camel-validate then runs automatically;
   after standalone execution, invoke it explicitly.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

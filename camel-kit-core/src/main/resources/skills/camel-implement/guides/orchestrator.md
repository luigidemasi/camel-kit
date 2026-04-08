# Orchestrator Guide

This guide defines file paths and execution order for the implementation pipeline. It adapts to the target runtime and works with both greenfield and migration TDDs.

> **YOUR JOB IS TO GENERATE FILES, NOT TO DISCUSS WHAT FILES YOU WOULD GENERATE.**
> After reading this guide, immediately start executing Step 1 (or Step 2 if no DataMapper). Do NOT summarize what you read, do NOT discuss the complexity of the task, do NOT present alternatives, do NOT say "this would involve..." — just start writing code. Every step below produces concrete files on disk. If you reach the end of this guide without having written files, you have failed.

> **"Load" means READ and FOLLOW.** Every time this guide says "Load `guides/xyz.md`", you MUST read that file from the skill directory where this guide lives and execute its instructions. Do NOT skip a step because you haven't read the guide yet — read it, then do what it says. The guide files ARE present in the same directory as this file.

> **Context variables from master SKILL.md:**
> - `RUNTIME` — `jbang` (default), `springboot`, or `quarkus`
> - `FLOW_NAME`, `CAMEL_VERSION`, `TARGET_MODULE`
> - `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via `skills/shared/mcp-setup.md`

**Migration TDDs:** If the TDD contains a "Migrated From" field, Java source files, WSDL/XSD files, or Maven plugin configuration, handle them as additional artifacts alongside the standard pipeline steps. Copy referenced files to `TARGET_MODULE`, create Java classes, and configure pom.xml as specified in the TDD. Do NOT skip the standard pipeline — migration TDDs still need route YAML, properties, docker-compose, and all other standard artifacts.

---

## File Path Table

Resolve paths based on runtime:

| File Type | JBang | Spring Boot / Quarkus |
|-----------|-------|-----------------------|
| `{flow-name}.camel.yaml` | `{module}/` | `{module}/src/main/resources/camel/` |
| `kaoto-datamapper-*.xsl` | `{module}/` | `{module}/src/main/resources/camel/` |
| `application.properties` | `{module}/` | `{module}/src/main/resources/` |
| `schemas/{flow-name}-*.json` | `{module}/schemas/` | `{module}/src/main/resources/schemas/` |
| `docker-compose.yaml` | `{module}/` | `{module}/` |
| `.kaoto` | `{module}/` | `{module}/` |
| `run.sh` (JBang only) | `{module}/` | N/A |

Where `{module}` is the `Target Module` from the TDD "Overview" section. For single-project setups, `{module}` is empty (files go in project root).

Assign these as context variables for all subsequent steps:
- `ROUTE_DIR` — route/datamapper location from table above
- `PROPS_DIR` — application.properties location from table above
- `SCHEMA_DIR` — schemas location from table above
- `MODULE_DIR` = `{module}/`

---

## Execution Order

### Step 0: Project Context from Graph (CONDITIONAL)

**IF** `.camel-kit/project-graph.json` exists:
- Load `guides/graph-project-context.md`
- Pass: `FLOW_NAME`, `CAMEL_VERSION`, `RUNTIME`

**SKIP** if no project graph exists.

### Step 1: DataMapper (CONDITIONAL)

**IF** the TDD contains `### DataMapper: kaoto-datamapper-{id}` sections:
- Load `guides/datamapper-validation.md` (shared — Steps 1, 1.5, 2, 3.5, 5-7)
- Based on XSLT Approach in TDD:
  - Approach A or N/A → also load `guides/datamapper-approach-a.md` (Steps 3, 4)
  - Approach B → also load `guides/datamapper-approach-b.md` (Steps 3, 4)
- Pass: `FLOW_NAME`, file locations from the table above

**SKIP** if no DataMapper sections exist in the TDD.

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

**JBang only:** INCLUDE `camel.jbang.dependencies` section listing all required Camel dependencies.
**Spring Boot / Quarkus:** Do NOT include `camel.jbang.dependencies` — dependencies are managed via Maven.

### Step 4: Docker Compose (ALWAYS)

- Load `guides/docker-compose.md`
- Pass:
  - `FLOW_NAME`
  - `MODULE_DIR`
  - `CAMEL_VERSION`
  - `RUNTIME`
  - `DOCKER_IMAGE`:
    - JBang: `apache/camel-jbang:{CAMEL_VERSION}`
    - Spring Boot / Quarkus: application-specific (built from project, not a generic Camel image)

### Step 5: Runtime-Specific Artifacts (ALWAYS)

**JBang:**
- Load `guides/run-script.md`
- Pass: `FLOW_NAME`, `MODULE_DIR`

**Spring Boot / Quarkus:**
- Load `guides/maven-dependencies.md`
- Pass: `FLOW_NAME`, `MODULE_DIR`, `CAMEL_VERSION`, `RUNTIME`

### Step 5.5: Migration Artifacts (CONDITIONAL)

**IF** the TDD contains a "Migrated From" field (migration scenario):

1. **Java source files:** If the TDD references Java processors, beans, or configuration classes, create them in `{module}/src/main/java/` using the package structure from the TDD. For migration, copy the logic from the source and adapt it to Camel 4.x (jakarta imports, updated API calls).

2. **Non-route files:** If the TDD references WSDL, XSD, or other resource files, copy them to `{module}/src/main/resources/` preserving the directory structure specified in the TDD.

3. **Maven plugins:** If the TDD specifies build plugins (e.g., CXF codegen, JAXB), add them to the `<build><plugins>` section of `{module}/pom.xml`.

4. **CDI/Spring configuration:** If the TDD specifies configuration classes or bean definitions beyond `application.properties`, create them.

**SKIP** if the TDD does not contain a "Migrated From" field.

### Step 5.6: Sequential HTTP Calls (CONDITIONAL)

**IF** the TDD contains both an HTTP consumer (`platform-http`, `servlet`, `jetty`, `netty-http`) **AND** one or more outbound HTTP producer calls (`http`, `https`, `undertow`, `vertx-http`):
- Load `guides/sequential-http-calls.md` for detailed implementation guidance
- Apply header sanitization rules between HTTP endpoints

**Note:** Rule 0e in `yaml-catalog-rules.md` already enforces the basic `removeHeaders` pattern inline during route generation. This guide provides additional context, edge cases, and examples beyond the inline rule.

**SKIP** if the route does not involve multiple HTTP endpoints.

### Step 6: Advanced Patterns (CONDITIONAL)

**IF** the TDD contains a "Performance & Reliability" section **OR** a "Security" section:
- Load `guides/advanced-patterns.md`

**SKIP** if neither section exists in the TDD.

### Step 7: Schemas (CONDITIONAL)

**IF** schemas were missing in pre-checks **AND** the user chose to generate them:
- Load `guides/schema-generation.md`
- Pass:
  - `FLOW_NAME`
  - `SCHEMA_DIR` (from path table)

**SKIP** if schemas already exist or user declined generation.

### Step 8: Smoke Test (ALWAYS — MANDATORY, DO NOT SKIP)

**You MUST execute this step.** Read `guides/smoke-test.md` (in the same directory as this file) and follow its instructions completely before showing the Implementation Summary.

**What to do concretely:**
1. Read the file `guides/smoke-test.md`
2. Start docker-compose if present (`docker compose up -d`)
3. Run the application startup command for the runtime (JBang/Spring Boot/Quarkus) with a 60-second timeout
4. Check the output for success markers
5. If startup failed, analyze the error, fix it, and retry (up to 6 attempts)
6. Report PASS or FAIL

Pass these context variables:
  - `FLOW_NAME`
  - `MODULE_DIR`
  - `RUNTIME`
  - `CAMEL_VERSION`

**HARD GATE:** Do NOT show the Implementation Summary until you have actually executed the startup command and observed the output. Showing the summary without running the smoke test is a skill violation.

---

## Step 9: Completion Gate (ALWAYS — MANDATORY, DO NOT SKIP)

Before showing the Implementation Summary, verify that implementation actually happened by checking files on disk. Run `ls` or `test -f` for each expected file. This is the final defense against showing a success summary when no files were generated.

### 9.1 Required Files Check

Verify these files exist and are non-empty:

| Check | Path | Condition |
|-------|------|-----------|
| Route YAML | `{ROUTE_DIR}{flow-name}.camel.yaml` | MUST exist, MUST be non-empty |
| Properties | `{PROPS_DIR}application.properties` | MUST exist |
| Docker Compose | `{MODULE_DIR}docker-compose.yaml` | MUST exist if TDD lists external services |
| Maven POM | `{MODULE_DIR}pom.xml` | MUST exist (Spring Boot / Quarkus only) |
| Run script | `{MODULE_DIR}run.sh` | MUST exist (JBang only) |

**If the route YAML does not exist, STOP.** Do not show the Implementation Summary. Go back to Step 2 (Route Generation) and actually generate the file. This check exists because the most common failure mode is the AI reading guides without executing them.

### 9.2 TDD Conformance Check

Open the generated `{flow-name}.camel.yaml` and verify it against the TDD:

| Check | What to verify | How |
|-------|---------------|-----|
| Source component | Route `from:` uses the component specified in TDD "Source System" | Read route YAML, check `from.uri` |
| Sink component | Route contains a `to:` step using the component specified in TDD "Sink System" | Read route YAML, check `to.uri` |
| Route ID | Route declares `id: {flow-name}` | Read route YAML, check `id` field |
| Error handling | Route includes error handling matching TDD strategy (DLC, retry, etc.) | Read route YAML, check for `onException` or `errorHandler` |
| Placeholders | No hardcoded hostnames, ports, or credentials in route YAML — all use `{{placeholder}}` syntax | Scan route YAML for literal URLs or credentials |

Report each check as PASS or FAIL. Failing checks are **warnings** (do not block the summary), but they must be visible in the output so the user knows what to review.

### 9.3 Gate Result

```
Completion Gate:
  Files:
    ✓ {flow-name}.camel.yaml exists ({N} lines)
    ✓ application.properties exists
    ✓ docker-compose.yaml exists
    ✓ pom.xml exists [Spring Boot/Quarkus]
  TDD Conformance:
    ✓ Source: [component] matches TDD
    ✓ Sink: [component] matches TDD
    ✓ Route ID: {flow-name}
    ✓ Error handling: [strategy] matches TDD
    ⚠ Hardcoded value found: [detail]  ← example warning
```

### 9.4 — Graph Rebuild Note (CONDITIONAL)

If `.camel-kit/project-graph.json` already exists (adding to existing project or migration):

```
Note: The project graph may be stale — it was built before this
implementation. Run /camel-init to rebuild it before running
/camel-validate or /camel-test.
```

If no graph exists: skip silently. No suggestion.

---

## Implementation Summary

**PREREQUISITE:** You can only show this summary if you have completed Step 8 (Smoke Test) and Step 9 (Completion Gate). If the route YAML does not exist on disk, go back and generate it.

After all steps complete, display:

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

  ✓ docker-compose.yaml
    Location: {MODULE_DIR}
    Services: [list services]

  ✓ run.sh [JBang only — IF Step 5 ran]
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

Dependencies (from TDD):
  - camel-[component1]
  - camel-[component2]
  - [external dependencies]

Completion Gate: ✅ ALL CHECKS PASSED / ⚠️ [N] warnings
Smoke Test: ✅ PASSED / ⚠️ FAILED

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Next Steps

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
NEXT STEPS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Review generated files

2. Validate the implementation:
   /camel-validate {flow-name}

3. Generate integration tests:
   /camel-test {flow-name}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

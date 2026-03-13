# Orchestrator Guide

This guide defines file paths and execution order for the implementation pipeline. It adapts to the target runtime and works with both greenfield and migration TDDs.

> **"Load" means READ and FOLLOW.** Every time this guide says "Load `guides/xyz.md`", you MUST read that file from the skill directory where this guide lives and execute its instructions. Do NOT skip a step because you haven't read the guide yet — read it, then do what it says. The guide files ARE present in the same directory as this file.

> **Context variables from master SKILL.md:**
> - `RUNTIME` — `jbang` (default), `springboot`, or `quarkus`
> - `FLOW_NAME`, `CAMEL_VERSION`, `TARGET_MODULE`

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

### Step 1: DataMapper (CONDITIONAL)

**IF** the TDD contains `### DataMapper: kaoto-datamapper-{id}` sections:
- Load `guides/datamapper-implement.md` for **each** DataMapper section
- Pass: `FLOW_NAME`, file locations from the table above

**SKIP** if no DataMapper sections exist in the TDD.

### Step 2: Route Generation (ALWAYS)

- Load `guides/route-generation.md`
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

**Note:** Rule 0e in `route-generation.md` already enforces the basic `removeHeaders` pattern inline during route generation. This guide provides additional context, edge cases, and examples beyond the inline rule.

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

## Implementation Summary

**PREREQUISITE:** You can only show this summary if you have run Step 8 (Smoke Test) and observed the actual application output. If you skipped the smoke test, go back and run it now.

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
    See datamapper-implement.md Step 7 checklist for details

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

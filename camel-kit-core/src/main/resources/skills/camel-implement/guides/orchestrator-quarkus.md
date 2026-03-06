# Orchestrator Guide: Quarkus Runtime

This orchestrator guide is for Quarkus runtime. It defines file paths and execution order for the implementation pipeline.

> **Note:** This guide is loaded when `project.runtime` is `quarkus` in `.camel-kit/config.yaml`.

---

## File Path Table

Files follow the standard Maven/Quarkus directory layout.

| File Type | Location |
|-----------|----------|
| `{flow-name}.camel.yaml` | `{module}/src/main/resources/camel/` |
| `kaoto-datamapper-*.xsl` | `{module}/src/main/resources/camel/` |
| `application.properties` | `{module}/src/main/resources/` |
| `schemas/{flow-name}-*.json` | `{module}/src/main/resources/schemas/` |
| `docker-compose.yaml` | `{module}/` |
| `.kaoto` | `{module}/` |

Where `{module}` is the `Target Module` from TDD Section 1. For single-project setups, `{module}` is empty (files go in project root).

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
  - `ROUTE_DIR` = `{module}/src/main/resources/camel/`
  - `ROUTE_FILE` = `{flow-name}.camel.yaml`
  - `CAMEL_VERSION`
  - `TARGET_MODULE`

### Step 3: Properties (ALWAYS)

- Load `guides/properties-generation.md`
- Pass:
  - `FLOW_NAME`
  - `PROPS_DIR` = `{module}/src/main/resources/`
  - `CAMEL_VERSION`
  - `RUNTIME` = `quarkus`
- Do **NOT** include `camel.jbang.dependencies` — dependencies are managed via Maven

### Step 4: Docker Compose (ALWAYS)

- Load `guides/docker-compose.md`
- Pass:
  - `FLOW_NAME`
  - `MODULE_DIR` = `{module}/`
  - `CAMEL_VERSION`
  - `RUNTIME` = `quarkus`
  - `DOCKER_IMAGE` = application-specific (built from project, not a generic Camel image)

### Step 5: Maven Dependencies (ALWAYS — Spring Boot/Quarkus only)

- Load `guides/maven-dependencies.md`
- Pass:
  - `FLOW_NAME`
  - `MODULE_DIR` = `{module}/`
  - `CAMEL_VERSION`

### Step 6: Advanced Patterns (CONDITIONAL)

**IF** the TDD contains Section 6 (Performance & Reliability) **OR** Section 7 (Security):
- Load `guides/advanced-patterns.md`

**SKIP** if neither section exists in the TDD.

### Step 7: Schemas (CONDITIONAL)

**IF** schemas were missing in pre-checks **AND** the user chose to generate them:
- Load `guides/schema-generation.md`
- Pass:
  - `FLOW_NAME`
  - `SCHEMA_DIR` = `{module}/src/main/resources/schemas/`

**SKIP** if schemas already exist or user declined generation.

---

## Implementation Summary

After all steps complete, display:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
IMPLEMENTATION COMPLETE: {flow-name}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Runtime: quarkus

Generated Files:

  ✓ {flow-name}.camel.yaml
    Location: {module}/src/main/resources/camel/
    Route ID: {flow-name}
    Source: [component]:{{source.endpoint}}
    Sink: [component]:{{sink.endpoint}}
    Validation: PASSED ✅ (MCP verified)

  ✓ DataMapper artifacts [IF Step 1 ran]
    Location: {module}/src/main/resources/camel/
    See datamapper-implement.md Step 7 checklist for details

  ✓ application.properties
    Location: {module}/src/main/resources/
    Component config: [list components]
    Bean definitions: [list beans]
    Route placeholders: [count]

  ✓ docker-compose.yaml
    Location: {module}/
    Services: [list services]

  ✓ Maven dependencies added to pom.xml [IF Step 5 ran]
    Location: {module}/pom.xml

  ✓ schemas/{flow-name}-input.json [IF Step 7 ran]
    Location: {module}/src/main/resources/schemas/
    Input data schema

  ✓ schemas/{flow-name}-output.json [IF Step 7 ran]
    Location: {module}/src/main/resources/schemas/
    Output data schema

Dependencies (from TDD):
  - camel-[component1]
  - camel-[component2]
  - [external dependencies]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Next Steps

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RECOMMENDED NEXT STEPS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Review generated files and validate configuration

2. Start external services:

   docker compose up -d

3. Validate the implementation:

   /camel-validate {flow-name}

4. Generate integration tests:

   /camel-test {flow-name}

5. Run the integration:

   mvn quarkus:dev

   Or with Maven wrapper:
   ./mvnw quarkus:dev

6. Monitor logs and verify behavior

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Need help?
- /camel-validate {flow-name} - Validate implementation
- /camel-test {flow-name} - Generate tests
```

# Orchestrator Guide: JBang Runtime

This orchestrator guide is for JBang runtime. It defines file paths and execution order for the implementation pipeline.

> **Note:** JBang is the default runtime when `project.runtime` is not set in `.camel-kit/config.yaml`.

---

## File Path Table

All files are placed at the module root level for JBang runtime.

| File Type | Location |
|-----------|----------|
| `{flow-name}.camel.yaml` | `{module}/` |
| `kaoto-datamapper-*.xsl` | `{module}/` |
| `application.properties` | `{module}/` |
| `schemas/{flow-name}-*.json` | `{module}/schemas/` |
| `docker-compose.yaml` | `{module}/` |
| `run.sh` | `{module}/` |
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
  - `ROUTE_DIR` = `{module}/`
  - `ROUTE_FILE` = `{flow-name}.camel.yaml`
  - `CAMEL_VERSION`
  - `TARGET_MODULE`

### Step 3: Properties (ALWAYS)

- Load `guides/properties-generation.md`
- Pass:
  - `FLOW_NAME`
  - `PROPS_DIR` = `{module}/`
  - `CAMEL_VERSION`
  - `RUNTIME` = `jbang`
- **INCLUDE** `camel.jbang.dependencies` section listing all required Camel dependencies

### Step 4: Docker Compose (ALWAYS)

- Load `guides/docker-compose.md`
- Pass:
  - `FLOW_NAME`
  - `MODULE_DIR` = `{module}/`
  - `CAMEL_VERSION`
  - `RUNTIME` = `jbang`
  - `DOCKER_IMAGE` = `apache/camel-jbang:{CAMEL_VERSION}`

### Step 5: Run Script (ALWAYS — JBang only)

- Load `guides/run-script.md`
- Pass:
  - `FLOW_NAME`
  - `MODULE_DIR` = `{module}/`

### Step 6: Advanced Patterns (CONDITIONAL)

**IF** the TDD contains Section 6 (Performance & Reliability) **OR** Section 7 (Security):
- Load `guides/advanced-patterns.md`

**SKIP** if neither section exists in the TDD.

### Step 7: Schemas (CONDITIONAL)

**IF** schemas were missing in pre-checks **AND** the user chose to generate them:
- Load `guides/schema-generation.md`
- Pass:
  - `FLOW_NAME`
  - `SCHEMA_DIR` = `{module}/schemas/`

**SKIP** if schemas already exist or user declined generation.

### Step 8: Smoke Test (ALWAYS — MANDATORY, DO NOT SKIP)

**You MUST execute this step.** Load and follow `guides/smoke-test.md` completely before showing the Implementation Summary.

- Load `guides/smoke-test.md`
- Pass:
  - `FLOW_NAME`
  - `MODULE_DIR` = `{module}/`
  - `RUNTIME` = `jbang`
  - `CAMEL_VERSION`

The smoke test starts the application, checks if it boots, and if it fails, fixes the error and retries — up to 6 attempts. Do NOT proceed to the summary until the smoke test loop completes.

---

## Implementation Summary

After all steps complete, display:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
IMPLEMENTATION COMPLETE: {flow-name}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Runtime: jbang

Generated Files:

  ✓ {flow-name}.camel.yaml
    Location: {module}/
    Route ID: {flow-name}
    Source: [component]:{{source.endpoint}}
    Sink: [component]:{{sink.endpoint}}
    Validation: PASSED ✅ (MCP verified)

  ✓ DataMapper artifacts [IF Step 1 ran]
    Location: {module}/
    See datamapper-implement.md Step 7 checklist for details

  ✓ application.properties
    Location: {module}/
    Component config: [list components]
    Bean definitions: [list beans]
    Route placeholders: [count]

  ✓ docker-compose.yaml
    Location: {module}/
    Services: [list services]

  ✓ run.sh
    Location: {module}/
    Executable script to start integration

  ✓ schemas/{flow-name}-input.json [IF Step 7 ran]
    Location: {module}/schemas/
    Input data schema

  ✓ schemas/{flow-name}-output.json [IF Step 7 ran]
    Location: {module}/schemas/
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

   ./run.sh

   Or manually:
   camel run {flow-name}.camel.yaml application.properties

6. Monitor logs and verify behavior

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Need help?
- /camel-validate {flow-name} - Validate implementation
- /camel-test {flow-name} - Generate tests
```

# Task Template — Greenfield Projects

> **Context:** Loaded by `camel-plan` for greenfield (new integration) projects.
> **Purpose:** Template for generating per-flow implementation tasks.

---

## Standard Task Sequence per Flow

For each flow in the design spec, generate these tasks in order:

### Task Template: Scaffold Project (once, first flow only)

```markdown
### Task 1: Scaffold Project Structure

**Agent:** implementation-engineer

**Files:**
- Create: `pom.xml` (Spring Boot/Quarkus only)

**Guides to Load:**
- `camel-implement/guides/orchestrator.md` — file path table
- `camel-implement/guides/maven-dependencies.md`
- `camel-implement/guides/pom-spring-boot.md` (if Spring Boot runtime)
- `camel-implement/guides/pom-quarkus.md` (if Quarkus runtime)

**POM Template Files (MUST READ AND COPY):**
- If Quarkus: `templates/pom-quarkus.xml` — copy verbatim, replace only `[PLACEHOLDER]` values
- If Spring Boot: `templates/pom-spring-boot.xml` — copy verbatim, replace only `[PLACEHOLDER]` values

<HARD-RULE>
Do NOT generate the POM from scratch. COPY the template file and replace ONLY the bracketed placeholders. The template already has the correct groupIds, artifactIds, repositories, and plugins.
</HARD-RULE>

**Design Spec Section:** Section 6 (Project Structure)

- [ ] Create directory structure from design spec Section 6
- [ ] Create `pom.xml` using the TEMPLATE-COPY approach:
  - If Quarkus: Read the file `templates/pom-quarkus.xml`, copy it verbatim to `pom.xml`
  - If Spring Boot: Read the file `templates/pom-spring-boot.xml`, copy it verbatim to `pom.xml`
  - Replace ONLY these placeholders: `[PROJECT_GROUP_ID]`, `[PROJECT_ARTIFACT_ID]`, `[PROJECT_VERSION]`, `[PROJECT_NAME]`, `[PLATFORM_BOM_VERSION]` (and `[SPRING_BOOT_VERSION]` for Spring Boot)
  - Get `[PLATFORM_BOM_VERSION]` from the design spec header `platformBomVersion` field
  - Do NOT modify any other values in the template (groupIds, artifactIds, repositories, plugins)
  - Add project-specific dependencies in the DEPENDENCIES section
- [ ] Verify: `ls -la` shows expected structure

**Review:**
- [ ] Spec compliance: directory structure matches spec Section 6
- [ ] Code quality: correct groupIds and versions
```

### Task Template: Generate Route YAML (per flow)

```markdown
### Task N: Generate Route YAML — [flow-name]

**Agent:** implementation-engineer

**Files:**
- Create: `[ROUTE_DIR][flow-name].camel.yaml`
- Create: `[ROUTE_DIR]kaoto-datamapper-*.xsl` (if DataMapper in spec)

**Guides to Load:**
- `camel-implement/guides/orchestrator.md` — file paths, execution order
- `camel-implement/guides/component-loading.md` — component documentation
- `camel-implement/guides/yaml-catalog-rules.md` — catalog verification rules
- `camel-implement/guides/yaml-structure.md` — YAML DSL structure
- `camel-implement/guides/route-validation.md` — validation loop
- `camel-implement/guides/datamapper-approach-[a|b].md` (if DataMapper)
- `camel-implement/guides/datamapper-validation.md` (if DataMapper)
- `shared/datamapper-canonicalize.md` (if DataMapper)
- `camel-implement/guides/sequential-http-calls.md` (if HTTP→HTTP)

**MCP Tools:**
- `camel_catalog_component_doc(component="[source-component]", runtime="[runtime]", platformBom="[bom]")`
- `camel_catalog_component_doc(component="[sink-component]", runtime="[runtime]", platformBom="[bom]")`
- `camel_catalog_eip_doc(eip="[eip]")` for each EIP used
- `camel_catalog_dataformat_doc(dataformat="[format]")` for each data format

**Design Spec Section:** Section 3, Flow: [flow-name]

- [ ] Read design spec Section 3 for flow [flow-name]
- [ ] Load and follow orchestrator.md Steps 1-2 (DataMapper then Route Generation)
- [ ] Verify each component via MCP catalog before writing YAML
- [ ] Generate `[flow-name].camel.yaml` with:
  - Route ID: `[flow-name]`
  - Description: [from spec]
  - Source: `[component]` with options from MCP catalog
  - Transformations: [from spec, each EIP MCP-verified]
  - Sink: `[component]` with options from MCP catalog
  - Error handling: [strategy from spec]
- [ ] Self-validate: route has routeId, description, source, sink, no hardcoded values
- [ ] Verify: `test -f [ROUTE_DIR][flow-name].camel.yaml && echo "EXISTS"`

**Review:**
- [ ] Spec compliance: components match spec, structure matches spec, all transformations present
- [ ] Code quality: constitution rules 1-7, no anti-patterns, Kaoto-compatible YAML
```

### Task Template: Generate Properties (per flow or consolidated)

```markdown
### Task N: Generate Application Properties — [flow-name]

**Agent:** implementation-engineer

**Files:**
- Create/Modify: `[PROPS_DIR]application.properties`

**Guides to Load:**
- `camel-implement/guides/properties-generation.md`

**Design Spec Section:** Section 3, Flow: [flow-name], Configuration Properties

- [ ] Read design spec configuration properties for [flow-name]
- [ ] Generate application.properties entries with `{{PLACEHOLDER}}` values
- [ ] For JBang: include `camel.jbang.dependencies` section
- [ ] Verify: `grep "[flow-name]" [PROPS_DIR]application.properties`

**Review:**
- [ ] Spec compliance: all properties from spec present
- [ ] Code quality: no hardcoded values, proper grouping
```

### Task Template: Generate Docker Compose (consolidated)

```markdown
### Task N: Generate Docker Compose

**Agent:** implementation-engineer

**Files:**
- Create: `[MODULE_DIR]docker-compose.yaml`

**Guides to Load:**
- `camel-implement/guides/docker-compose.md`

**Design Spec Section:** Section 3 (all flows, external service dependencies)

- [ ] Identify all external services from all flows (databases, brokers, APIs)
- [ ] Generate docker-compose.yaml with service definitions
- [ ] Verify: `docker compose -f [MODULE_DIR]docker-compose.yaml config`

**Review:**
- [ ] Spec compliance: all external services from spec have docker-compose entries
- [ ] Code quality: proper networking, health checks, volume mounts
```

### Task Template: Validate All Routes

```markdown
### Task N: Validate All Routes

**Agent:** quality-engineer

**Files:**
- Read: all generated `*.camel.yaml` files

**Guides to Load:**
- `camel-validate/guides/schema-validation.md`
- `camel-validate/guides/endpoint-validation.md`
- `camel-validate/guides/quality-checks.md`
- `camel-validate/guides/security-analysis.md`
- `camel-validate/guides/anti-patterns.md`

**MCP Tools:**
- `camel_catalog_component_doc` for each endpoint URI

- [ ] Run schema validation on each route YAML
- [ ] Verify each endpoint URI via MCP catalog
- [ ] Check constitution compliance (all 7 rules) per route
- [ ] Run security analysis
- [ ] Check for anti-patterns
- [ ] Produce validation report

**Review:**
- [ ] This IS the review task — produces the validation report
```

### Task Template: Smoke Test

```markdown
### Task N: Smoke Test

**Agent:** implementation-engineer

**Guides to Load:**
- `camel-implement/guides/smoke-test.md`

- [ ] Start docker-compose services if present
- [ ] Run application startup command for [runtime]
- [ ] Observe startup output for success markers
- [ ] If startup fails: analyze, fix, retry (up to 6 attempts)
- [ ] Report PASS or FAIL
- [ ] Stop docker-compose services

**Review:**
- [ ] Verification result: PASS/FAIL with evidence
```

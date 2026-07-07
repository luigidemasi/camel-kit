---
name: camel-implement
description: Use when there is an approved implementation plan ready for execution — generates YAML routes, properties, Docker Compose, and all implementation artifacts
---

# Camel Implement — Implementation Pipeline (Bob)

Generate Apache Camel implementation artifacts from the approved implementation plan and design spec. Follow every step
in order. Do NOT skip steps.

**Core principle:** Fresh implementation per route + design-spec enforcement + MCP verification = high quality,
correct code.

## Guide Locations

All implementation guides are in `.bob/skills/camel-implement/guides/`. When this file says `guides/X.md`, read `.bob/skills/camel-implement/guides/X.md`. Do NOT explore or list directories to find guides.

<Steps>
<Step>
## Switch to Implement Mode

Switch to **camel-implement** mode using the mode selector.
This enables full code generation capabilities.
</Step>

<Step>
## Verify Approved Plan Exists

Read `docs/camel-kit/<PIPELINE_ID>/implementation-plan.md`.

If it does not exist or has not been approved, STOP and return to camel-plan.
Implementation only happens after approval.
</Step>

<Step>
## Load Implementation Context

Read these files:
1. `docs/constitution.md` — constitution rules (all 8 rules)
2. `.camel-kit/config.properties` — Camel version, runtime, platform BOM
3. `.camel-kit/project-graph.json` — project conventions (if exists)
4. `docs/camel-kit/<PIPELINE_ID>/design-spec.md` — approved design spec

Load core guides:
- `guides/orchestrator.md` — file path table, execution order
- `guides/yaml-structure.md` — YAML DSL structure rules
- `guides/yaml-catalog-rules.md` — catalog-driven YAML generation
- `guides/component-loading.md` — component dependency resolution
- `guides/properties-generation.md` — application.properties generation
- `guides/maven-dependencies.md` — POM dependency management
</Step>

<Step>
## Implement Each Route

For EACH route in the plan:

**CHECKPOINT** — Create a checkpoint before starting this route.

### Route Implementation Process

1. **Read the relevant flow design** from `docs/camel-kit/<PIPELINE_ID>/design-spec.md`
2. **Verify components via MCP:**
   - For EVERY component: `camel_catalog_component_doc(component="X", runtime="Y", platformBom="Z")`
   - For EVERY EIP: `camel_catalog_eip_doc(eip="X")`
   - For EVERY dataformat: `camel_catalog_dataformat_doc(dataformat="X")`
   - For EVERY language: `camel_catalog_language_doc(language="X")`
3. **Write the failing test FIRST:**
   - Load `guides/test-generation.md`
   - Write a Citrus test that expects the behavior from the design spec
   - Run the test — it MUST fail (route doesn't exist yet)
4. **Generate the YAML route:**
   - Load `guides/yaml-structure.md`
   - Load `guides/yaml-catalog-rules.md`
   - Follow catalog schema EXACTLY (use MCP results)
   - Save to `src/main/resources/camel/<flow-name>.camel.yaml`
5. **Update properties:**
   - Load `guides/properties-generation.md`
   - Add component configurations to `src/main/resources/application.properties`
6. **Update POM dependencies:**
   - Load `guides/maven-dependencies.md`
   - Load runtime-specific guide: `guides/pom-spring-boot.md` or `guides/pom-quarkus.md`
   - Add component dependencies to `pom.xml`
7. **Run the test:**
   - Execute: `camel test run src/test/resources/<flow-name>.camel.it.yaml`
   - Test MUST pass
8. **Self-validate the route:**
   - Load `guides/route-validation.md`
   - Check: YAML syntax, component options, endpoint URIs, constitution compliance
9. **Commit:**
   - Stage: `git add src/main/resources/camel/<flow-name>.camel.yaml src/main/resources/application.properties pom.xml src/test/resources/<flow-name>.camel.it.yaml`
   - Commit: `git commit -m "feat: implement <flow-name> route"`
</Step>

<Step>
## Special Cases

**When DataMapper is needed:**
- Read the design spec DataMapper section for approach (A or B)
- Load `guides/datamapper-approach-a.md` (useJsonBody) or `guides/datamapper-approach-b.md` (header param)
- Generate XSLT at `src/main/resources/xslt/<transform-name>.xsl`
- Load `guides/datamapper-validation.md` and self-validate XSLT against design spec field mappings

**When Docker Compose services are needed:**
- Load `guides/docker-compose.md`
- Generate `docker-compose.yml` with required services
- Load `guides/run-script.md` and generate `run.sh`

**When JSON/XML schemas are needed:**
- Load `guides/schema-generation.md`
- Generate schema files in `src/main/resources/schemas/`

**When sequential HTTP calls are needed:**
- Load `guides/sequential-http-calls.md`
- Follow the pattern for chained calls with enrich/pollEnrich

**When advanced EIPs are needed:**
- Load `guides/advanced-patterns.md`
- Follow patterns for aggregation, splitting, content-based routing, etc.
</Step>

<Step>
## Final Route Validation

After all routes are implemented:

Load `guides/route-validation.md` and run cross-route validation:
- All routes follow consistent naming conventions
- All routes use consistent error handling patterns
- No duplicate route IDs
- All external services have Docker Compose definitions (if local dev)
- All property references are defined in application.properties
</Step>

<Step>
## Constitution Compliance Check

For EVERY route, verify compliance with all 8 constitution rules:

1. **No Hardcoded URLs** — all endpoints use properties
2. **Explicit Error Handling** — every route has `onException` or `doTry`
3. **Structured Logging** — all routes log at entry/exit with correlation ID
4. **Idempotency** — stateful routes use `idempotentConsumer`
5. **Circuit Breaker** — HTTP calls have resilience patterns
6. **TLS Everywhere** — all HTTP endpoints use HTTPS
7. **Component Verification** — all components verified via MCP catalog
8. **Infrastructure via Forage** — infrastructure beans declared with `forage.*` properties when Forage covers them (ladder: Forage → component properties → hand-rolled bean with stated reason); hand-rolled `camel.beans.*` requires a one-line reason comment

If any rule is violated, fix immediately before proceeding.
</Step>

<Step>
## Commit All Changes

After all routes pass validation:

```bash
git add .
git commit -m "feat: implement all routes per approved plan"
```
</Step>
</Steps>

## Iron Laws

All implementation enforces:
- **Iron Law 1**: MCP Catalog Verification — verify component options before generating YAML
- **Iron Law 2**: Constitution Compliance — every generated route passes all 8 rules

## MCP Tools Used

- `camel_catalog_component_doc` — verify component exists, get options schema
- `camel_catalog_eip_doc` — verify EIP exists, get configuration schema
- `camel_catalog_dataformat_doc` — verify dataformat exists
- `camel_catalog_language_doc` — verify expression language exists

For MCP setup, version mapping, and fallback policy: see `shared/mcp-setup.md`

## Guide Reference

| Guide | When to Load |
|-------|-------------|
| `guides/orchestrator.md` | Always |
| `guides/yaml-structure.md` | Always |
| `guides/yaml-catalog-rules.md` | Always |
| `guides/component-loading.md` | Always |
| `guides/properties-generation.md` | Always |
| `guides/maven-dependencies.md` | Always |
| `guides/pom-spring-boot.md` | When runtime is Spring Boot |
| `guides/pom-quarkus.md` | When runtime is Quarkus |
| `guides/route-validation.md` | Always (final step) |
| `guides/docker-compose.md` | When external services needed |
| `guides/run-script.md` | When run script needed |
| `guides/schema-generation.md` | When JSON/XML schemas needed |
| `guides/datamapper-approach-a.md` | When DataMapper with useJsonBody |
| `guides/datamapper-approach-b.md` | When DataMapper with header param |
| `guides/datamapper-validation.md` | When DataMapper used |
| `guides/sequential-http-calls.md` | When chained HTTP calls needed |
| `guides/advanced-patterns.md` | When advanced EIPs used |
| `guides/graph-project-context.md` | When `.camel-kit/project-graph.json` exists |

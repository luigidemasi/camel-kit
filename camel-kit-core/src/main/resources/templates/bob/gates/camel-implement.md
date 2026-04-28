---
name: camel-implement
description: Use when there is an approved implementation plan or TDD ready for execution — generates YAML routes, properties, Docker Compose, and all implementation artifacts
---

# Camel Implement — Implementation Pipeline (Bob)

Generate Apache Camel implementation artifacts from approved TDDs. Follow every step in order. Do NOT skip steps.

**Core principle:** Fresh implementation per route + TDD enforcement + MCP verification = high quality, correct code.

## Guide Locations

All implementation guides are in `.bob/skills/camel-implement/guides/`. When this file says `guides/X.md`, read `.bob/skills/camel-implement/guides/X.md`. Do NOT explore or list directories to find guides.

<Steps>
<Step>
## Switch to Implement Mode

Switch to **camel-implement** mode using the mode selector or `/camel-implement` command.
This enables full code generation capabilities.
</Step>

<Step>
## Verify Approved Plan or TDD Exists

Read one of:
- `docs/implementation-plan.md` (for planned execution)
- `docs/flows/\{flow-name\}/\{flow-name\}.tdd.md` (for direct TDD implementation)

If neither exists or hasn't been approved, STOP and return to camel-plan.
Implementation only happens after approval.
</Step>

<Step>
## Load Implementation Context

Read these files:
1. `docs/constitution.md` — constitution rules (all 7 rules)
2. `.camel-kit/config.properties` — Camel version, runtime, platform BOM
3. `.camel-kit/project-graph.json` — project conventions (if exists)
4. `docs/design-spec.md` — approved design spec

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

1. **Read the TDD** at `docs/flows/\{flow-name\}/\{flow-name\}.tdd.md`
2. **Verify components via MCP:**
   - For EVERY component: `camel_catalog_component(name="X", runtime="Y", platformBom="Z")`
   - For EVERY EIP: `camel_catalog_eip(name="X")`
   - For EVERY dataformat: `camel_catalog_dataformat(name="X")`
   - For EVERY language: `camel_catalog_language(name="X")`
3. **Write the failing test FIRST:**
   - Load `guides/test-generation.md`
   - Write a Citrus test that expects the behavior from the TDD
   - Run the test — it MUST fail (route doesn't exist yet)
4. **Generate the YAML route:**
   - Load `guides/yaml-structure.md`
   - Load `guides/yaml-catalog-rules.md`
   - Follow catalog schema EXACTLY (use MCP results)
   - Save to `src/main/resources/camel/\{flow-name\}.camel.yaml`
5. **Update properties:**
   - Load `guides/properties-generation.md`
   - Add component configurations to `src/main/resources/application.properties`
6. **Update POM dependencies:**
   - Load `guides/maven-dependencies.md`
   - Load runtime-specific guide: `guides/pom-spring-boot.md` or `guides/pom-quarkus.md`
   - Add component dependencies to `pom.xml`
7. **Run the test:**
   - Execute: `mvn test -Dtest=\{RouteTest\}`
   - Test MUST pass
8. **Self-validate the route:**
   - Load `guides/route-validation.md`
   - Check: YAML syntax, component options, endpoint URIs, constitution compliance
9. **Commit:**
   - Stage: `git add src/main/resources/camel/\{flow-name\}.camel.yaml src/main/resources/application.properties pom.xml src/test/java/**/\{RouteTest\}.java`
   - Commit: `git commit -m "feat: implement \{flow-name\} route"`
</Step>

<Step>
## Special Cases

**When DataMapper is needed:**
- Read the TDD's DataMapper section for approach (A or B)
- Load `guides/datamapper-approach-a.md` (useJsonBody) or `guides/datamapper-approach-b.md` (header param)
- Generate XSLT at `src/main/resources/xslt/\{transform-name\}.xslt`
- Load `guides/datamapper-validation.md` and self-validate XSLT against TDD field mappings

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

For EVERY route, verify compliance with all 7 constitution rules:

1. **No Hardcoded URLs** — all endpoints use properties
2. **Explicit Error Handling** — every route has `onException` or `doTry`
3. **Structured Logging** — all routes log at entry/exit with correlation ID
4. **Idempotency** — stateful routes use `idempotentConsumer`
5. **Circuit Breaker** — HTTP calls have resilience patterns
6. **TLS Everywhere** — all HTTP endpoints use HTTPS
7. **Component Verification** — all components verified via MCP catalog

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
- **Iron Law 2**: Constitution Compliance — every generated route passes all 7 rules

## MCP Tools Used

- `camel_catalog_component` — verify component exists, get options schema
- `camel_catalog_eip` — verify EIP exists, get configuration schema
- `camel_catalog_dataformat` — verify dataformat exists
- `camel_catalog_language` — verify expression language exists

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

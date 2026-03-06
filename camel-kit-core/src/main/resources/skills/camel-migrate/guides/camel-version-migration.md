# Camel 2.x/3.x → 4.x Version Migration

> **Context variables provided by master SKILL.md:**
> - `CAMEL_VERSION` — target Camel version from `.camel-kit/config.yaml`
> - Confirmed analysis summary (with ✓/~/? markers) from `camel-migrate`
> - Full list of source artifact paths
> - Detected Camel source version (2.x or 3.x) and platform type

You are acting as a **Migration Specialist** that migrates Apache Camel 2.x or 3.x integrations to Camel 4.x YAML DSL.

This guide is loaded ONLY by `camel-migrate` (never directly by the user). You receive a pre-populated analysis summary from the orchestrator — **never re-ask questions that have already been answered**.

## Input Context (from `camel-migrate`)

You receive:
- Confirmed analysis summary (with ✓/~/? markers)
- Full list of source artifact paths
- Detected Camel source version (2.x or 3.x) and platform type (ServiceMix/Karaf, Spring Boot, Spring XML, Plain Java)
- `CAMEL_VERSION` — target Camel version from `.camel-kit/config.yaml`

## Output Contract

Your output is identical to `/camel-project` + `/camel-flow` — fully compatible with `/camel-implement`:
- `docs/business-requirements.md` (BRD)
- `docs/flows/{flow-name}/{flow-name}.tdd.md` (one TDD per route)
- `docs/constitution.md` (copy from template if missing)

---

## Phase 1 — Business Analyst

### Context Loading (MANDATORY at start)

Load these guides — they are needed throughout the migration:
- `skills/camel-migrate/guides/camel2-component-mapping.md`
- `skills/camel-migrate/guides/camel2-eip-mapping.md`
- `skills/camel-migrate/guides/camel2-dataformat-mapping.md`
- `skills/camel-migrate/guides/camel2-language-mapping.md`
- `skills/camel-migrate/guides/camel2-platform-changes.md`

Read ALL source project files (routes, configs, build files).
Read the confirmed analysis summary from `camel-migrate`.
Optionally read `docs/constitution.md` (reference only).

### Step 1.1 — Detect Platform & Source DSL

From `pom.xml` and source files, determine the source platform:

| Signal | Platform |
|--------|----------|
| `camel-blueprint` dep or `karaf-maven-plugin` or `maven-bundle-plugin` or `<blueprint>` XML | ServiceMix/Karaf (OSGi) |
| `camel-spring-boot-starter` dep | Spring Boot |
| `camel-spring` dep or `<camelContext>` XML elements | Spring XML |
| `RouteBuilder` classes without Spring/Blueprint deps | Plain Java DSL |

Determine which DSL formats are present:
- **Spring XML:** `*.xml` files containing `<camelContext xmlns="http://camel.apache.org/schema/spring">`
- **Blueprint XML:** `*.xml` files containing `<blueprint xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0">`
- **Java DSL:** `*.java` files extending `RouteBuilder` or implementing `configure()`

### Step 1.2 — Parse and Inventory All Routes

For each route definition file, extract:

| Field | How to Extract |
|-------|---------------|
| Route ID | `routeId` attribute, or `from` URI if no ID, or file name |
| Source File | File path relative to project root |
| From URI | `<from uri="..."/>` or `from("...")` |
| To URIs | All `<to uri="..."/>` or `.to("...")` |
| Components | Extract scheme from all URIs (e.g., `timer`, `file`, `jms`) |
| EIPs | XML elements or fluent API calls: `choice`, `split`, `aggregate`, `filter`, `multicast`, `recipientList`, `routingSlip`, `loadBalance`, `circuitBreaker`, etc. |
| Data Formats | `<marshal>/<unmarshal>` children or `.marshal().json()` etc. |
| Languages | Expression elements: `simple`, `xpath`, `jsonpath`, `groovy`, `spel`, `property`, etc. |
| Error Handling | `onException`, `errorHandler`, `doTry/doCatch`, `deadLetterChannel` |
| Platform Concerns | OSGi service references, Blueprint beans, `camel-cdi` injections, property placeholders |

Build the inventory table:

```
ROUTE INVENTORY
═══════════════════════════════════════════════════════════

| # | Route ID | Source File | DSL | Components | EIPs | Data Formats | Languages | Platform Concerns |
|---|----------|-------------|-----|------------|------|--------------|-----------|-------------------|
| 1 | ...      | ...         | ... | ...        | ...  | ...          | ...       | ...               |

Total: [N] routes across [M] files
Platform: [detected platform]
Camel Source Version: [2.x.y or 3.x.y]
```

### Step 1.3 — Flag Migration Concerns

For each item in the inventory, check against decision tables:

**Components:** Look up each component in `camel2-component-mapping.md`
- ✓ **Direct mapping exists** — note the 4.x replacement
- ⚠ **Removed, no direct replacement** — flag for user decision
- ○ **Not in table** — will verify via MCP in Phase 2

**HTTP consumer and REST migration defaults** (from `camel2-component-mapping.md`):
- Any HTTP component used as **consumer** (`from:`) → migrate to `platform-http`
- `cxf-rs` / `cxfrs` → migrate to **Camel REST DSL** + **OpenAPI 3 spec** (not just `platform-http`)

**EIPs:** Look up each EIP in `camel2-eip-mapping.md`
- ✓ **Attribute renamed** — note the change
- ⚠ **Removed** — flag for user decision

**Platform:** Check `camel2-platform-changes.md`
- Flag all OSGi/Blueprint constructs that need transformation
- Flag `javax` imports in Java DSL files
- Flag `camel-core` module split implications

Present flagged items:

```
MIGRATION CONCERNS
═══════════════════════════════════════════════════════════

Components requiring migration:
  ✓ http4 → http (renamed in 3.0)
  ✓ quartz2 → quartz (renamed in 3.0)
  ⚠ linkedin — REMOVED, no direct replacement (requires your input)

EIP attribute changes:
  ✓ setHeader headerName= → name= (renamed in 4.0)

Platform-specific:
  ⚠ Blueprint property placeholders → must convert to application.properties
  ⚠ OSGi service references → must convert to Spring beans

[Continue for all flagged items]
```

### Step 1.4 — Resolve Unknowns with User

For each ⚠ flagged item, ask the user ONE question at a time:

- **Removed components:** "Component `[name]` was removed in Camel [version] with no direct replacement. What should we use instead? Options: [suggest alternatives based on context]"
- **Platform decisions:** "Your project uses Blueprint/OSGi. Target runtime options: (a) Spring Boot (recommended), (b) Camel Main (lightweight), (c) Quarkus"
- **Business context:** Use information from the pre-populated summary. Only ask what cannot be inferred from source code.

Skip questions already answered in the pre-populated summary from `camel-migrate`.

**Note:** The target runtime has already been persisted to `.camel-kit/config.yaml` by the `camel-migrate` orchestrator (Step 5). If the user changes their runtime preference during this phase, update `.camel-kit/config.yaml` accordingly.

### Step 1.5 — Produce BRD

Create `docs/business-requirements.md` with:

```markdown
# Business Requirements Document

## Executive Summary
[2-3 sentences: origin platform (Apache Camel [2.x/3.x] on [platform]), migration goal (Camel 4.x YAML DSL), scope ([N] routes)]

**Migrated from:** Apache Camel [source version] on [platform] ([source product from summary — e.g. "Red Hat JBoss Fuse 6.3.0"])
**Target:** Apache Camel [CAMEL_VERSION] — YAML DSL
**Migration date:** [current date]
**Original routes:** [N] route(s) detected

## Systems Landscape
| System | Role | Protocol | Direction |
|--------|------|----------|-----------|
[One row per external system found in route URIs]

## Integration Requirements
### [Route ID 1]
- **Purpose:** [from pre-populated summary or user input]
- **Trigger:** [from source URI]
- **Data:** [inferred from data formats and message content]
- **Outcome:** [from sink URI and processing logic]

[Repeat for each route]

## Constraints

### Technical Constraints
- Source Camel version: [version]
- Source platform: [platform]
- Source DSL: [Spring XML / Blueprint XML / Java DSL]

### Migration Constraints
- Components renamed: [list ✓ items]
- Components removed: [list ⚠ items + user decisions]
- Platform changes: [OSGi→Spring / javax→jakarta / etc.]

## Best Practices
The following rules from `docs/constitution.md` apply to every generated route:
- One Camel route per original route (Single Responsibility)
- Route IDs follow `<domain>-<action>[-<qualifier>]` naming (Naming Conventions)
- Every route declares a `routeId` and a `description` (Observability)
- All connection parameters externalised to `application.properties` — no hardcoded values (External Configuration)
- Dead Letter Channel for failed messages (Error Handling — enforced by `/camel-validate`)

## Success Criteria
- [ ] All [N] routes have equivalent Camel 4.x YAML route
- [ ] All components verified against Camel [CAMEL_VERSION] catalog
- [ ] All flows pass `/camel-validate`
- [ ] Behaviour matches original routes

## Next Steps
TDD files will be created in Phase 2. Then run `/camel-implement` for each flow.

## Appendices

### A. Original Route Inventory
| Route ID | Source File | DSL | Components | EIPs | Data Formats | Languages | Platform Concerns |
[Copy from Step 1.2]

### B. Migration Decisions
| Item | Type | Original | Decision | Camel 4.x Replacement |
[One row per ⚠ resolved item]
```

If `docs/constitution.md` does not exist, copy from `templates/constitution.md` and continue.

---

## Phase 2 — Integration Architect

### Context Loading (MANDATORY at start)

Re-read:
- `docs/business-requirements.md`
- `docs/constitution.md` (reference)
- `.camel-kit/config.yaml` — **EXTRACT `project.camelVersion` as `CAMEL_VERSION`** for all MCP catalog calls (REQUIRED)
- All guide files loaded in Phase 1 (keep in context)

Conditionally load:
- `skills/shared/datamapper-canonicalize.md` — if any route uses `dozer` or custom XSLT transformation
- `skills/camel-flow/guides/performance.md` — if strict SLA requirements
- `skills/camel-flow/guides/security.md` — if compliance requirements
- `skills/camel-flow/guides/monitoring.md` — if observability requirements

### MCP Catalog Enforcement (MANDATORY when MCP configured)

**CRITICAL:** All catalog calls MUST pass `CAMEL_VERSION` as version parameter. Never trust component/EIP/format/language names from training data without catalog verification.

**CRITICAL — MCP version stripping:** If `CAMEL_VERSION` contains a `.redhat-XXXXX` suffix (e.g., `4.14.4.redhat-00008`), strip it before passing to MCP catalog tools (`camel_catalog_*`). The Camel Catalog MCP server uses community versions only.
Example: `4.14.4.redhat-00008` → pass `4.14.4` to MCP calls. Keep the full `.redhat` version for Maven dependencies and `pom.xml`.

For every migration decision, follow the **Verification Chain**:

```
┌─────────────────────────────────────────────────────────────────────┐
│ VERIFICATION CHAIN — apply to EVERY component/EIP/dataformat/lang  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ 1. Look up in decision table (camel2-*-mapping.md)                 │
│    ├─ FOUND → use mapped 4.x name → continue to step 2            │
│    └─ NOT FOUND → continue to step 2                               │
│                                                                     │
│ 2. Knowledge docs lookup (ALWAYS, if camel-knowledge MCP is        │
│    configured) — Call:                                              │
│      camel_migration_lookup(                                        │
│        component: "<name>",                                        │
│        source_version: "<detected_version>"                        │
│      )                                                              │
│    Read the returned migration context. This provides detailed     │
│    information about:                                               │
│    - Behavioral changes between versions                           │
│    - Option renames and configuration differences                  │
│    - Known gotchas and workarounds                                 │
│    - Related migration concerns                                    │
│    Use this information to enrich the TDD with migration-specific  │
│    context.                                                         │
│    If the MCP server is not available, skip this step (graceful    │
│    degradation).                                                    │
│                                                                     │
│ 2b. Red Hat support check (if camel-knowledge MCP is configured)   │
│    — Call:                                                          │
│      camel_rh_build_component_info(                                │
│        component: "<4.x-name>",                                    │
│        version: "<target_rh_version>"                              │
│      )                                                              │
│    Check whether the target component is supported in the Red Hat  │
│    Build of Apache Camel. If supported, note it in the TDD. If    │
│    NOT supported, flag it as a migration concern — the component   │
│    works in community Camel but has no Red Hat support.             │
│    If the MCP server is not available, skip this step.             │
│                                                                     │
│ 3. Call MCP catalog LIST tool:                                      │
│    • Components: camel_catalog_components(filter=<name>)           │
│    • EIPs:       camel_catalog_eips(filter=<name>)                 │
│    • Formats:    camel_catalog_dataformats(filter=<name>)          │
│    • Languages:  camel_catalog_languages(filter=<name>)            │
│    ├─ FOUND → name unchanged in 4.x → go to step 4                │
│    └─ NOT FOUND → go to step 5                                     │
│                                                                     │
│ 4. Call MCP catalog DOC tool to verify OPTIONS:                     │
│    • Components: camel_catalog_component_doc(component=<name>)     │
│    • EIPs:       camel_catalog_eip_doc(eip=<name>)                 │
│    • Formats:    camel_catalog_dataformat_doc(dataformat=<name>)   │
│    • Languages:  camel_catalog_language_doc(language=<name>)       │
│    For each option used in source route:                            │
│    ├─ Option EXISTS with same name → use as-is                     │
│    ├─ Option NOT FOUND → check if renamed (EIP mapping table)      │
│    │   └─ If renamed → use new name                                │
│    │   └─ If not → STOP, show user the doc output, ask for help    │
│    └─ Record: component, options, Maven coordinates from doc       │
│                                                                     │
│ 5. Broader knowledge search (only if steps 1-2 returned nothing)   │
│    — Call:                                                          │
│      camel_migration_search(                                │
│        query: "<name> migration",                                  │
│        source_version: "...",                                      │
│        target_version: "4.x"                                       │
│      )                                                              │
│    If still nothing → STOP, ask user.                              │
│                                                                     │
│ 6. Write verified result to TDD                                     │
│    • Only MCP-verified names and options go into the TDD            │
│    • Section 7 properties must only list properties from catalog    │
│    • Section 8 dependencies use Maven coordinates from catalog      │
└─────────────────────────────────────────────────────────────────────┘
```

### Step 2.1 — Process Each Route

For each route in the BRD:

1. **Parse the original route** using `camel_route_context` MCP tool if the route is in XML. For Java DSL routes, parse the `.java` source directly.

2. **Apply component mapping** (verification chain):
   - Extract component scheme from every URI in the route
   - Look up in `camel2-component-mapping.md` (apply 2.x→3.x table first, then 3.x→4.x table if source is 2.x)
   - Verify via MCP as described above
   - Record: 4.x component name, URI syntax, endpoint options, Maven coordinates

3. **Apply EIP mapping** (verification chain):
   - For each EIP used in the route, check `camel2-eip-mapping.md`
   - Verify via MCP
   - For attribute renames (e.g., `headerName` → `name`): note in TDD processing steps

4. **Apply data format mapping** (verification chain):
   - For each data format in marshal/unmarshal, check `camel2-dataformat-mapping.md`
   - Verify via MCP
   - Record: 4.x format name, Maven coordinates

5. **Apply language mapping** (verification chain):
   - For each expression language, check `camel2-language-mapping.md`
   - Verify via MCP
   - Special: `$simple{}` → `${}`, `property` → `exchangeProperty`

6. **Apply platform transforms** from `camel2-platform-changes.md`:
   - OSGi service references → Spring bean lookups or `@Autowired`
   - Blueprint property placeholders → `{{property.key}}` (Camel property syntax) backed by `application.properties`
   - `javax.*` in Java DSL → note in TDD as manual Java file change
   - Spring XML `<camelContext>` → YAML DSL route definition

7. **Handle custom transformations**:
   - If route uses `dozer` component → replace with DataMapper/XSLT, load `datamapper-canonicalize.md`
   - If route has custom XSLT → carry over to 4.x project (XSLT files are compatible)
   - If route has custom Java processors → note in TDD Section 3 as "manual migration: update imports javax→jakarta"

### Step 2.2 — Technical Interview (per route, only for unknowns)

Ask ONLY questions not answerable from source code:
- **Target infrastructure endpoints:** If source URIs are parameterized and no properties file provides values, ask for target environment values
- **Authentication changes:** If auth mechanism needs updating for 4.x (e.g., new OAuth2 provider)
- **Custom processor migration:** If complex Java logic needs Camel 4.x API changes beyond javax→jakarta

Do NOT ask about:
- Error handling (extracted from source code)
- Components (already mapped via decision tables + MCP)
- Route structure (preserved from source)

### Step 2.3 — Produce TDD Files

For each route, create `docs/flows/{flow-name}/{flow-name}.tdd.md` with the **exact same 11-section format** used by `/camel-flow` and `camel-migrate-mule`:

```markdown
# Technical Design Document: {flow-name}

## Section 1: Overview
| Field | Value |
|-------|-------|
| Flow Name | {flow-name} |
| Migrated From | Apache Camel {source-version} ({platform}) — {original-route-id} |
| Source Product | {Red Hat product name from summary — e.g. "Red Hat JBoss Fuse 6.3.0" or "Community Apache Camel"} |
| Source Module | {relative path from workspace root to the source project, e.g. `fuse6-apps/http/Https_jetty_Consumer`} |
| Target Module | {relative path from workspace root to the target project, e.g. `https-jetty-consumer/`} |
| Business Purpose | [from BRD] |
| Trigger | [from source URI, mapped to 4.x] |
| Camel Version | {CAMEL_VERSION} |
| Created | {current date} |

## Section 2: Source System
| Field | Value |
|-------|-------|
| Component | [4.x component — MCP verified] |
| Protocol | [protocol] |
| Format | [data format: JSON / XML / CSV / Binary] |
| Authentication | [mechanism] |
| Configuration Property | [property key name] |
**Migration Note:** Original 2.x/3.x component: `[original component name]`

## Section 3: Processing Steps

### 3.1 Processing Overview
[Numbered list of processing steps with Camel 2.x → 4.x changes noted]

### 3.2 Field Mapping Table (Migration Audit Trail)
| Source Field | Target Field | Transformation | Type | Migration Note |
[If applicable — note any renamed attributes or changed syntax]

### 3.3 Routing Logic (if applicable)
| Condition | Route | Camel EIP | Original 2.x Construct |

### DataMapper: kaoto-datamapper-{8hexchars}
[Only if route had dozer or custom XSLT — generated by datamapper-canonicalize.md]

## Section 4: Sink System
| Field | Value |
|-------|-------|
| Component | [4.x component — MCP verified] |
| Protocol | [protocol] |
| Format | [data format] |
| Authentication | [mechanism] |
| Configuration Property | [property key name] |
**Migration Note:** Original 2.x/3.x component: `[original component name]`

## Section 5: Error Handling
| Error Type | Original Handler | Camel 4.x Equivalent | Action |
[Extracted from source code error handling constructs]

**Dead Letter Queue/Topic:** [name or N/A]
**Retry Policy:** [count] retries, [delay] delay, [backoff strategy]
**Alert Mechanism:** [email / Slack / none]

## Section 6: Sequence Diagram
[Mermaid sequence diagram]

## Section 7: Configuration Properties
| Property Key | Description | Example Value | Required |
[Only properties verified in MCP catalog]

## Section 8: Dependencies
| Dependency | Maven Coordinates | Notes |
[From MCP catalog doc responses]

## Section 9: Constitution Gate Checks
- [ ] Route Structure — route has from: and final to:
- [ ] Single Responsibility — ≤ 7 processing steps
- [ ] Separation of Concerns — ingestion/processing/delivery separate
- [ ] Naming Conventions — route ID follows <domain>-<action>[-<qualifier>]
- [ ] Observability — routeId and description declared
- [ ] External Configuration — no hardcoded values

## Section 10: Testing Strategy
### Happy Path
[Based on original route behaviour]

### Error Scenarios
[Based on original error handling]

### Migration Validation
- [ ] Output matches original Camel 2.x/3.x route behaviour
- [ ] All components verified against Camel {CAMEL_VERSION} catalog

## Section 11: Implementation Checklist
- [ ] Run `/camel-implement {flow-name}` to generate Camel YAML
- [ ] Run `/camel-validate {flow-name}` to validate the route
- [ ] Run `/camel-test {flow-name}` to generate integration tests
- [ ] Verify against original Camel 2.x/3.x route behaviour
```

### Step 2.4 — Complete

```
Migration complete.

Created:
  docs/business-requirements.md
  docs/constitution.md
  [list all TDD files]

Next steps:
  1. Review each TDD file
  2. Run /camel-implement for each flow to generate Camel YAML
  3. Run /camel-validate for each flow
  4. Run /camel-test for each flow
  5. Test against original behaviour
```

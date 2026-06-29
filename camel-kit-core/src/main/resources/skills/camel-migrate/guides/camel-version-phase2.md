# Camel Version Migration — Phase 2: Design Spec Generation

> **Context variables:** `CAMEL_VERSION`, `RUNTIME`, `PLATFORM_BOM` from `.camel-kit/config.properties`
> **Prerequisite:** Phase 1 (`camel-version-phase1.md`) must be complete — business requirements written to
> `docs/camel-kit/<PIPELINE_ID>/business-requirements.md`

## Phase 2 — Integration Architect

### Context Loading (MANDATORY at start)

Re-read:
- `docs/camel-kit/<PIPELINE_ID>/business-requirements.md`
- `docs/constitution.md` (reference)
- `.camel-kit/config.properties` — **extract `project.camelVersion` as `CAMEL_VERSION`** and `project.runtime` as `RUNTIME` (written by `camel-migrate` orchestrator in Step 5). If the file does not exist or `project.camelVersion` is not set, **STOP** and ask the user for the target Camel version before proceeding. Before every MCP catalog call, translate `CAMEL_VERSION` + `RUNTIME` to the correct `camelVersion` parameter using the version mapping table in `skills/shared/mcp-setup.md`.
- All guide files loaded in Phase 1 (keep in context)

Conditionally load:
- `skills/shared/datamapper-canonicalize.md` — if any route uses `dozer` or custom XSLT transformation
- `skills/camel-design/guides/performance.md` — if strict SLA requirements
- `skills/camel-design/guides/security.md` — if compliance requirements
- `skills/camel-design/guides/monitoring.md` — if observability requirements

### MCP Catalog Enforcement (MANDATORY when MCP configured)

→ **For MCP setup, version mapping, and fallback policy:** see `skills/shared/mcp-setup.md`

**CRITICAL:** All catalog calls MUST pass the translated `camelVersion` from the version mapping table (see `skills/shared/mcp-setup.md`). Never trust component/EIP/format/language names from training data without catalog verification.

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
│ NOTE: All catalog calls in steps 2-3 MUST also pass:               │
│   camelVersion=<target_version>,                                   │
│   platformBom=<platform_bom>,                                      │
│   runtime=<runtime>                                                │
│ (translated from CAMEL_VERSION+RUNTIME via mcp-setup.md table)     │
│                                                                     │
│ 2. Call MCP catalog LIST tool:                                      │
│    • Components: camel_catalog_components(filter=<name>)           │
│    • EIPs:       camel_catalog_eips(filter=<name>)                 │
│    • Formats:    camel_catalog_dataformats(filter=<name>)          │
│    • Languages:  camel_catalog_languages(filter=<name>)            │
│    ├─ FOUND → name unchanged in 4.x → go to step 3                │
│    └─ NOT FOUND → go to step 4                                     │
│                                                                     │
│ 3. Call MCP catalog DOC tool to verify OPTIONS:                     │
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
│ 4. Component not found (steps 1-3 returned nothing)                │
│    Ask user for guidance:                        │
│      "Component [X] not found in catalog or knowledge base.         │
│       Options:                                                      │
│       a) Provide the correct Camel 4.x component name               │
│       b) Provide an MCP-verified replacement component or pattern    │
│       c) Remove this processing step from the migration"            │
│    If user chooses replacement or removal, update the design spec    │
│    accordingly.                                                     │
│                                                                     │
│ 5. Write verified result to the design spec                         │
│    • Only MCP-verified names and options go into the design spec     │
│    • Unverified components MUST NOT be written as implementation     │
│      placeholders                                                    │
│    • "Configuration Properties" must only list properties from catalog│
│    • "Dependencies" section uses Maven coordinates from catalog      │
└─────────────────────────────────────────────────────────────────────┘
```

### Graph-Enhanced Route Context (when graph CLI available)

**Before processing each route**, if graph CLI is available (check via `shared/graph-availability.md`), run these queries to build structural context. If the CLI is not available, skip this section and proceed with Step 2.1 as normal.

Read `.camel-kit/config.properties` to get the `command-prefix` field (default: `camel-kit`).

**Migration ordering:** If `.camel-kit/project-snapshot.md` exists, process routes in the order specified in its "Migration Ordering" section (leaf routes first, then dependents). This prevents generating design specs that reference routes not yet migrated.

**For each route, before the verification chain:**

**Step 2.0.1 — Route Flow Context:**

Run the command:
```bash
{COMMAND_PREFIX} graph route-flow <routeId>
```

This returns the complete ordered message path:
- From-endpoint → processor1 → processor2 → ... → to-endpoints
- Cross-route links are followed automatically

This gives you the full end-to-end flow without re-reading source files. Use this to understand the processor chain before mapping components.

If the command exits with code != 0, skip this step.

**Step 2.0.2 — Impact Analysis:**

Run the commands:
```bash
{COMMAND_PREFIX} graph impact route:<routeId> --direction downstream
{COMMAND_PREFIX} graph impact route:<routeId> --direction upstream
```

These show what other routes, classes, and config are affected if this route changes, and what feeds into this route.

Use this information to populate:
- **Design spec Error Handling section:** Error propagation paths — if an upstream route has `onException`, note it
- **Design spec Testing Strategy section:** List upstream and downstream routes that should be included in integration tests
- **Design spec Implementation Checklist section:** Note dependent routes that may need corresponding updates

If either command exits with code != 0, skip this step.

**Step 2.0.3 — Dependency Pre-Check:**

Run the command:
```bash
{COMMAND_PREFIX} graph neighbors route:<routeId> --direction out
```

Filter the results for `USES_COMPONENT` edges to see which Maven artifacts this route needs.

Cross-check each component against the target Camel version:
- If the artifact name changed between versions (per `camel2-component-mapping.md`), note this before running the full verification chain
- If the component was removed, flag it early

This pre-check makes the verification chain faster — you already know which components to focus on.

If the command exits with code != 0, skip this step.

### Step 2.1 — Process Each Route

For each route in the business requirements:

1. **Parse the original route** using `camel_route_context` MCP tool if the route is in XML. For Java DSL routes, parse the `.java` source directly.

2. **Apply component mapping** (verification chain):
   - Extract component scheme from every URI in the route
   - Look up in `camel2-component-mapping.md` (apply 2.x→3.x table first, then 3.x→4.x table if source is 2.x)
   - Verify via MCP as described above
   - Record: 4.x component name, URI syntax, endpoint options, Maven coordinates

3. **Apply EIP mapping** (verification chain):
   - For each EIP used in the route, check `camel2-eip-mapping.md`
   - Verify via MCP
   - For attribute renames (e.g., `headerName` → `name`): note in design spec processing steps

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
   - `javax.*` in Java DSL → note in the design spec as a Java source migration action
   - Spring XML `<camelContext>` → YAML DSL route definition

7. **Handle custom transformations**:
   - If route uses `dozer` component → replace with DataMapper/XSLT, load `datamapper-canonicalize.md`
   - If route has custom XSLT → carry over to 4.x project (XSLT files are compatible)
   - If route has custom Java processors → note in the design spec Processing Steps section as "manual migration: update imports javax→jakarta"

### Step 2.2 — Technical Interview (per route, only for unknowns)

Ask ONLY questions not answerable from source code:
- **Target infrastructure endpoints:** If source URIs are parameterized and no properties file provides values, ask for target environment values
- **Authentication changes:** If auth mechanism needs updating for 4.x (e.g., new OAuth2 provider)
- **Custom processor migration:** If complex Java logic needs Camel 4.x API changes beyond javax→jakarta

Do NOT ask about:
- Error handling (extracted from source code)
- Components (already mapped via decision tables + MCP)
- Route structure (preserved from source)

### Step 2.3 — Update Design Spec

For each route, update the relevant `### Flow: {flow-name}` section in
`docs/camel-kit/<PIPELINE_ID>/design-spec.md` with the migration design details:

```markdown
### Flow: {flow-name}

## Section 1: Overview
| Field | Value |
|-------|-------|
| Flow Name | {flow-name} |
| Migrated From | Apache Camel {source-version} ({platform}) — {original-route-id} |
| Source Product | {product name from summary — e.g. "JBoss Fuse 6.3.0" or "Community Apache Camel"} |
| Source Module | {relative path from workspace root to the source project, e.g. `fuse6-apps/http/Https_jetty_Consumer`} |
| Target Module | {relative path from workspace root to the target project, e.g. `https-jetty-consumer/`} |
| Business Purpose | [from business requirements] |
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
- [ ] Ensure `camel-plan` includes an implementation task for this flow
- [ ] Run `camel-execute` to generate Camel YAML and integration tests
- [ ] Verify against original Camel behaviour
- [ ] Verify against original Camel 2.x/3.x route behaviour
```

### Step 2.4 — Complete

```
Migration complete.

Created:
  docs/camel-kit/<PIPELINE_ID>/business-requirements.md
  docs/camel-kit/<PIPELINE_ID>/design-spec.md
  docs/constitution.md

Next steps:
  1. Review the design spec
  2. Run camel-plan to create implementation tasks
  3. Run camel-validate to validate all flows
  4. Run camel-execute to generate, review, verify, and validate all planned work
  5. Test against original behaviour
```

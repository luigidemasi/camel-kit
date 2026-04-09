---
name: camel-migrate
description: Use when the user wants to migrate an existing integration from MuleSoft, Fuse, or older Camel versions to Red Hat Build of Apache Camel 4.x
---

# Camel Migrate — Migration Pipeline (Bob)

This skill runs the complete migration pipeline. Follow every step in order. Do NOT skip steps.

<Steps>
<Step>
## Switch to Brainstorm Mode

Switch to **camel-brainstorm** mode using the mode selector or `/camel-brainstorm` command.
This restricts your tools to read, markdown editing, MCP, and browser — preventing accidental code generation during the design phase.
</Step>

<Step>
## Scan Source Artifacts

Read `guides/migration-discovery.md` for the full discovery process.

Scan the source project for integration artifacts. Detect:
- Vendor (MuleSoft, Fuse, Camel 2.x/3.x)
- Platform (Spring Boot, Karaf, Quarkus, Plain Java)
- DSL (Java, XML, Blueprint, YAML)
- Routes (count, IDs, endpoints)
- Components used
- Potential migration concerns (deprecated components, platform changes, DataWeave)

Present the analysis summary to the user in a single message. Ask ONE confirmation question:
"Is the detected information correct? Any corrections?"
</Step>

<Step>
## Migration Concerns Interview

For EACH migration concern identified, ask ONE question at a time. Do NOT batch questions.

**CRITICAL — Target Platform:** You MUST ask which target platform the user wants:

"Your project currently runs on [source platform]. For Camel 4.x, the recommended target platforms are:
a) Spring Boot
b) Quarkus
c) Camel Main / JBang

Which platform should we target?"

Wait for the answer before proceeding.

For each remaining concern, use this pattern:
"[Concern N of M] — [Title]. [Context]. [Options a/b/c]. Which approach?"

Read `guides/migration-discovery.md` Step 5a for all concern templates.
</Step>

<Step>
## Additional Clarifications

Ask any remaining clarification questions ONE at a time.
Read `guides/migration-discovery.md` Step 5b.

When all concerns are addressed, summarize decisions and ask:
"All migration concerns addressed. Would you like to proceed to designing the Camel 4.x equivalent?"

Wait for explicit confirmation.
</Step>

<Step>
## Select Camel Version

Read `guides/version-selection.md` for the version selection process.

Help the user select:
1. Target Camel version (Red Hat Build)
2. Target runtime (Spring Boot / Quarkus / JBang)
3. Platform BOM version

Store selections in `.camel-kit/config.yaml`.
</Step>

<Step>
## Design Camel Equivalents

Read `guides/migration-discovery.md` Step 6 for component mapping.

For migration-specific mappings, read the appropriate guide:
- MuleSoft: `guides/mule-component-mapping.md`
- Camel 2.x: `guides/camel2-component-mapping.md`, `guides/camel2-eip-mapping.md`, `guides/camel2-dataformat-mapping.md`, `guides/camel2-language-mapping.md`

Verify EVERY component via MCP: `camel_catalog_component` then `camel_rh_build_component_info`.
</Step>

<Step>
## Assemble and Present Design Spec

Read `guides/design-assembly.md` for the full assembly process.

Assemble the migration design spec including:
- Migration context (source → target)
- All concern decisions
- Component mappings
- Route designs

Present the complete spec to the user.

**APPROVAL GATE — Do NOT proceed without explicit approval:**
"Do you approve this design? (yes / changes needed)"

If changes requested, incorporate and re-present. Only proceed after explicit "yes" or "approved".
</Step>

<Step>
## CHECKPOINT

Before proceeding to planning, this is the design approval checkpoint.
All design decisions are locked. Create a checkpoint now.
</Step>

<Step>
## Switch to Plan Mode

Switch to **camel-plan** mode. Read `guides/camel-version-phase1.md` for BRD generation.

Generate the Business Requirements Document (BRD) at `docs/business-requirements.md`.
</Step>

<Step>
## Generate Technical Design

Read `guides/camel-version-phase2.md` for TDD generation.

Generate Technical Design Documents (TDDs) at `docs/flows/\{flow-name\}/\{flow-name\}.tdd.md`.
</Step>

<Step>
## Plan Approval

Present the BRD and TDDs to the user.

**APPROVAL GATE:**
"The migration plan is ready. Do you approve? (yes / changes needed)"

Wait for explicit approval before proceeding.
</Step>

<Step>
## Switch to Implement Mode and Execute

Switch to **camel-implement** mode.

**CHECKPOINT** — Create a checkpoint before starting implementation.

Implement each route following the TDDs. For each route:
1. **CHECKPOINT** before starting this route
2. Read the route's TDD
3. Write the failing test
4. Implement the YAML route
5. Run tests
6. Commit

Read `guides/camel2-platform-changes.md` for platform migration steps (pom.xml, config files).
</Step>

<Step>
## Validate

Switch to **camel-validate** mode.

Run validation against the constitution and project norms.
Report findings without modifying files.

If the project graph is available, run:
`{commandPrefix} graph project-norms` and `{commandPrefix} graph dead-code`
</Step>

<Step>
## Test

Switch to **camel-test** mode.

**CHECKPOINT** — Create a post-migration checkpoint.

Write and run integration tests for all migrated routes.
Verify all tests pass.
</Step>
</Steps>

## Reference Guides

| Guide | Purpose |
|-------|---------|
| `guides/mulesoft-phase1.md` | MuleSoft Business Analyst analysis |
| `guides/mulesoft-phase2.md` | MuleSoft Technical Design |
| `guides/mule-component-mapping.md` | Mule → Camel component map |
| `guides/mule-dataweave-conversion.md` | DataWeave → XSLT strategies |
| `guides/datamapper-migrate.md` | DataMapper XSLT migration |
| `guides/camel-version-phase1.md` | Camel version analysis |
| `guides/camel-version-phase2.md` | Camel version TDD generation |
| `guides/camel-version-graph-analysis.md` | Graph-based pre-analysis |
| `guides/camel2-component-mapping.md` | Camel 2.x → 4.x components |
| `guides/camel2-dataformat-mapping.md` | Camel 2.x → 4.x dataformats |
| `guides/camel2-eip-mapping.md` | Camel 2.x → 4.x EIPs |
| `guides/camel2-language-mapping.md` | Camel 2.x → 4.x languages |
| `guides/camel2-platform-changes.md` | Platform migration guide |

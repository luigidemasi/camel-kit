---
name: camel-brainstorm
description: Use when the user wants to create a new Camel integration, connect systems, build flows, migrate from another platform (MuleSoft, Fuse, Camel 2.x), or start any integration project — whether greenfield or migration
---

# Camel Brainstorm — Design Pipeline (Bob)

Turn integration ideas into fully formed design specs through collaborative dialogue. Follow every step in order. Do NOT skip steps.

**Core principle:** Understand before designing. Design before planning. Plan before coding.

<Steps>
<Step>
## Switch to Brainstorm Mode

Switch to **camel-brainstorm** mode using the mode selector or `/camel-brainstorm` command.
This restricts your tools to read, markdown editing, MCP, and browser — preventing accidental code generation during the design phase.
</Step>

<Step>
## Detect Project Type

Determine if this is a greenfield integration or a migration:

**Greenfield indicators:**
- "Create", "build", "connect", "integrate", "new project"
- No existing source artifacts mentioned
- Describes desired end state, not existing state

**Migration indicators:**
- "Migrate", "convert", "move from", "replace"
- Mentions source platform: MuleSoft, Mule, Fuse, Camel 2.x, Camel 3.x
- References existing integration files or projects

If ambiguous, ask: "Are you building a new integration from scratch, or migrating an existing one from another platform?"
</Step>

<Step>
## Load Project Context

Read these files if they exist:
1. `docs/constitution.md` — constitution rules. If missing, copy from `templates/constitution.md`.
2. `.camel-kit/config.yaml` — project config (Camel version, runtime). May not exist yet.
3. `docs/business-requirements.md` — existing BRD (if resuming a project).
</Step>

<Step>
## Run Interview or Discovery

**For greenfield projects:**
Read `guides/greenfield-interview.md` for the Socratic interview process.
Ask ONE question at a time. Do NOT batch questions.
Understand:
- Systems to connect
- Data flow requirements
- Business logic needs
- Non-functional requirements (security, resilience, monitoring)

**For migration projects:**
Read `guides/migration-discovery.md` for the discovery process.
Scan source artifacts and detect:
- Vendor (MuleSoft, Fuse, Camel 2.x/3.x)
- Platform (Spring Boot, Karaf, Quarkus, Plain Java)
- DSL (Java, XML, Blueprint, YAML)
- Routes and components
- Migration concerns

If project graph is available, read `guides/migration-graph-analysis.md` for graph-accelerated analysis.

Confirm all findings with the user.
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
## Design Flows

For each flow, design the integration using relevant guides from `camel-design/`:
- Component selection: `guides/component-selection.md`
- EIP patterns: `guides/eip-patterns.md`
- Data formats: `guides/data-formats.md`
- Error handling: `guides/error-handling.md`
- Security: `guides/security.md`
- Resilience: `guides/resilience.md`

**CRITICAL:** Verify EVERY component via MCP:
1. `camel_catalog_component` — verify component exists
2. `camel_rh_build_component_info` — verify Red Hat support

Do NOT guess component names. MCP catalog is truth.
</Step>

<Step>
## Assemble Design Spec

Read `guides/design-assembly.md` for the full assembly process.

Assemble the complete design spec including:
- Project overview
- Systems and data flow
- Component selections (all MCP-verified)
- Route designs
- Non-functional requirements

Save to `docs/design-spec.md`.
</Step>

<Step>
## Self-Review Design Spec

Scan the design spec for:
- Placeholders ("TBD", "TODO", "fill in")
- Unverified components (did you call MCP for every one?)
- Contradictions between requirements and design
- Missing error handling or security considerations

Fix any issues inline.
</Step>

<Step>
## User Approval

Present the complete design spec to the user.

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

Switch to **camel-plan** mode.

Read `guides/camel-version-phase1.md` for BRD generation (greenfield) or migration-specific BRD guides.

Generate the Business Requirements Document (BRD) at `docs/business-requirements.md`.
</Step>

<Step>
## Generate Technical Design

Read `guides/camel-version-phase2.md` for TDD generation.

Generate Technical Design Documents (TDDs) at `docs/flows/\{flow-name\}/\{flow-name\}.tdd.md`.

For each flow, the TDD specifies:
- Source and sink endpoints
- Data transformations
- Error handling
- Component configurations
- Test criteria
</Step>

<Step>
## Plan Approval

Present the BRD and TDDs to the user.

**APPROVAL GATE:**
"The implementation plan is ready. Do you approve? (yes / changes needed)"

Wait for explicit approval before proceeding.
</Step>

<Step>
## Switch to Implement Mode and Execute

Switch to **camel-implement** mode.

**CHECKPOINT** — Create a checkpoint before starting implementation.

Implement each route following the TDDs. For each route:
1. **CHECKPOINT** before starting this route
2. Read the route's TDD
3. Write the failing test (TDD enforcement)
4. Implement the YAML route
5. Run tests
6. Commit

Read `guides/orchestrator.md` for implementation execution rules.
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

**CHECKPOINT** — Create a post-implementation checkpoint.

Write and run integration tests for all routes.
Verify all tests pass.
</Step>
</Steps>

## Iron Laws

Read `shared/iron-laws.md` for the full Iron Laws. This skill enforces:

- **Iron Law 1: MCP Catalog Verification** — Every component, EIP, dataformat, and language MUST be MCP-verified before inclusion.
- **Iron Law 2: Red Hat Build Only** — Only Red Hat supported Camel versions and components.
- **Iron Law 4: No Code Without Spec Approval** — NEVER generate any implementation artifacts before the user has explicitly approved the design spec.

## MCP Tools Used

- `camel_catalog_component` — verify component exists, get options
- `camel_catalog_eip` — verify EIP exists, get configuration
- `camel_catalog_dataformat` — verify dataformat exists
- `camel_catalog_language` — verify expression language exists
- `camel_rh_build_component_info` — check Red Hat support status
- `camel_knowledge_search` — search Red Hat docs for guidance

For MCP setup, version mapping, and fallback policy: see `shared/mcp-setup.md`
For graph analysis: use `{commandPrefix} graph` CLI commands (see `shared/graph-availability.md`)

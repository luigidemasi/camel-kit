---
name: camel-migrate
description: Use when the user wants to migrate an existing integration from MuleSoft, Fuse, or older Camel versions to Apache Camel 4.x
---

# Camel Migrate — Migration Pipeline (Bob)

This skill runs the complete migration pipeline. Follow every step in order. Do NOT skip steps.

## Guide Locations

Guides are spread across skill directories. Always use these full paths from the project root:

| Reference in steps | Actual path |
|---|---|
| `migration-discovery.md` | `.bob/skills/camel-brainstorm/guides/migration-discovery.md` |
| `version-selection.md` | `.bob/skills/camel-brainstorm/guides/version-selection.md` |
| `design-assembly.md` | `.bob/skills/camel-brainstorm/guides/design-assembly.md` |
| All other `guides/...` | `.bob/skills/camel-migrate/guides/` (same filename) |

Do NOT explore or list directories to find guides — use the paths above.

## Autonomous Execution Rules (Steps 12–14)

After plan approval (Step 11), Steps 12 (Implement), 13 (Validate), and 14 (Test) execute as an **uninterrupted sequence**:

1. **No pausing between steps** — After implementation, immediately validate. After validation, immediately test.
2. **No completion summaries until ALL steps complete** — The ONLY summary is printed after Step 14 (Test) finishes.
3. **No "Next Steps" blocks** — You ARE executing the next step RIGHT NOW.
4. **No asking for confirmation** — The plan approval (Step 11) is authorization for ALL remaining steps.
5. **No README generation** — Do NOT generate documentation files mid-pipeline.

<Steps>
<Step>
## Switch to Brainstorm Mode

Switch to **camel-brainstorm** mode using the mode selector or `/camel-brainstorm` command.
This restricts your tools to read, markdown editing, MCP, and browser — preventing accidental code generation during the design phase.
</Step>

<Step>
## Scan Source Artifacts

Read `.bob/skills/camel-brainstorm/guides/migration-discovery.md` for the full discovery process.

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

Read `.bob/skills/camel-brainstorm/guides/migration-discovery.md` Step 5a for all concern templates.
</Step>

<Step>
## Additional Clarifications

Ask any remaining clarification questions ONE at a time.
Read `.bob/skills/camel-brainstorm/guides/migration-discovery.md` Step 5b.

When all concerns are addressed, summarize decisions and ask:
"All migration concerns addressed. Would you like to proceed to designing the Camel 4.x equivalent?"

Wait for explicit confirmation.
</Step>

<Step>
## Select Camel Version

Read `.bob/skills/camel-brainstorm/guides/version-selection.md` for the version selection process.

Help the user select:
1. Target Camel version
2. Target runtime (Spring Boot / Quarkus / JBang)
3. Platform BOM version

Store selections in `.camel-kit/config.properties`.
</Step>

<Step>
## Design Camel Equivalents

Read `.bob/skills/camel-brainstorm/guides/migration-discovery.md` Step 6 for component mapping.

For migration-specific mappings, read the appropriate guide:
- MuleSoft: `.bob/skills/camel-migrate/guides/mule-component-mapping.md`
- Camel 2.x: `.bob/skills/camel-migrate/guides/camel2-component-mapping.md`, `.bob/skills/camel-migrate/guides/camel2-eip-mapping.md`, `.bob/skills/camel-migrate/guides/camel2-dataformat-mapping.md`, `.bob/skills/camel-migrate/guides/camel2-language-mapping.md`

Verify EVERY component via MCP: `camel_catalog_component_doc`.
</Step>

<Step>
## Assemble and Present Design Spec

Read `.bob/skills/camel-brainstorm/guides/design-assembly.md` for the full assembly process.

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

Switch to **camel-plan** mode. Read `.bob/skills/camel-migrate/guides/camel-version-phase1.md` for BRD generation.

Generate the business requirements at `docs/camel-kit/<PIPELINE_ID>/business-requirements.md`.
</Step>

<Step>
## Generate Technical Design

Read `.bob/skills/camel-migrate/guides/camel-version-phase2.md` for design spec generation.

Update the active migration design spec at `docs/camel-kit/<PIPELINE_ID>/design-spec.md`.
</Step>

<Step>
## Plan Approval

Present the BRD and design spec to the user.

**APPROVAL GATE:**
"The migration plan is ready. Do you approve? (yes / changes needed)"

Wait for explicit approval before proceeding.
</Step>

<Step>
## Switch to Implement Mode and Execute

Switch to **camel-implement** mode.

**CHECKPOINT** — Create a checkpoint before starting implementation.

Implement each route following the active design spec. For each route:
1. **CHECKPOINT** before starting this route
2. Read the route's design spec section
3. Write the failing test
4. Implement the YAML route
5. Run tests
6. Commit

Read `.bob/skills/camel-migrate/guides/camel2-platform-changes.md` for platform migration steps (pom.xml, config files).

**CRITICAL — CONTINUATION REQUIRED:** After all routes are implemented, you MUST IMMEDIATELY proceed to the next step (Validate). Do NOT:
- Print "Migration implementation complete" or any completion summary
- Print "Next Steps" or suggest manual actions
- Stop, pause, or ask the user what to do next
- Generate a README or migration documentation

Implementation is step 12 of 14. Steps 13 (Validate) and 14 (Test) are mandatory. Proceed NOW.
</Step>

<Step>
## Validate

Switch to **camel-validate** mode.

Run validation against the constitution and project norms.
Report findings without modifying files.

If the project graph is available, run:
`{COMMAND_PREFIX} graph project-norms` and `{COMMAND_PREFIX} graph dead-code`

**After validation completes, IMMEDIATELY proceed to the next step (Test). Do NOT stop or print summaries.**
</Step>

<Step>
## Test

Switch to **camel-test** mode.

**CHECKPOINT** — Create a post-migration checkpoint.

Write and run integration tests for all migrated routes.
Verify all tests pass.

**This is the FINAL step.** Now print the migration completion summary:

```
===============================================================
MIGRATION COMPLETE
===============================================================

Source: [vendor] [version]
Target: Apache Camel [version] on [runtime]

Migrated Routes: [N]
Validation: PASS/FAIL
Tests: PASS/FAIL ([N] passing, [M] failing)

Generated Files:
  [list all generated files]

Constitution Compliance: PASS/FAIL (all 7 rules)
===============================================================
```
</Step>
</Steps>

## Never

- Stop after implementation to print a summary or "Next Steps"
- Ask "Would you like me to continue?" between implement, validate, and test
- Print "Migration complete" before validation and testing are done
- Skip validation or testing
- Generate a README mid-pipeline instead of continuing to the next step
- Say "migration has been completed" while steps remain uncompleted

## Reference Guides

| Guide | Purpose |
|-------|---------|
| `.bob/skills/camel-brainstorm/guides/migration-discovery.md` | Migration discovery + interview |
| `.bob/skills/camel-brainstorm/guides/version-selection.md` | Camel version selection |
| `.bob/skills/camel-brainstorm/guides/design-assembly.md` | Design spec assembly |
| `.bob/skills/camel-migrate/guides/mulesoft-phase1.md` | MuleSoft Business Analyst analysis |
| `.bob/skills/camel-migrate/guides/mulesoft-phase2.md` | MuleSoft Technical Design |
| `.bob/skills/camel-migrate/guides/mule-component-mapping.md` | Mule → Camel component map |
| `.bob/skills/camel-migrate/guides/mule-dataweave-conversion.md` | DataWeave → XSLT strategies |
| `.bob/skills/camel-migrate/guides/datamapper-migrate.md` | DataMapper XSLT migration |
| `.bob/skills/camel-migrate/guides/camel-version-phase1.md` | Camel version analysis |
| `.bob/skills/camel-migrate/guides/camel-version-phase2.md` | Camel version design spec generation |
| `.bob/skills/camel-migrate/guides/camel2-component-mapping.md` | Camel 2.x → 4.x components |
| `.bob/skills/camel-migrate/guides/camel2-dataformat-mapping.md` | Camel 2.x → 4.x dataformats |
| `.bob/skills/camel-migrate/guides/camel2-eip-mapping.md` | Camel 2.x → 4.x EIPs |
| `.bob/skills/camel-migrate/guides/camel2-language-mapping.md` | Camel 2.x → 4.x languages |
| `.bob/skills/camel-migrate/guides/camel2-platform-changes.md` | Platform migration guide |

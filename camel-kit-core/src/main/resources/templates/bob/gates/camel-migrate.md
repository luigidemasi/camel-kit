---
name: camel-migrate
description: Use when the user wants to migrate an existing MuleSoft, BizTalk, Fuse, or older Camel integration to Apache Camel 4.x
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

## Autonomous Execution Rules

After design approval, planning, implementation, internal verification, and final validation execute as an **uninterrupted sequence**:

1. **No pausing between steps** — After implementation, immediately verify. After verification, immediately validate.
2. **No completion summaries until ALL steps complete** — The ONLY summary is printed after final validation finishes.
3. **No "Next Steps" blocks** — You ARE executing the next step RIGHT NOW.
4. **No asking for confirmation** — The design approval authorizes planning and all downstream work.
5. **No README generation** — Do NOT generate documentation files mid-pipeline.

<Steps>
<Step>
## Switch to Brainstorm Mode

Switch to **camel-brainstorm-mode** using the mode selector or `/camel-brainstorm-mode`.
This limits edits to design Markdown and Camel-Kit state. Command access is instruction-scoped to document metadata
and graph operations; implementation artifacts remain prohibited during the design phase.
</Step>

<Step>
## Detect Invocation Mode and Resolve Pipeline

- With an explicit `<PIPELINE_ID>`, use standalone design-only mode and resolve
  that pipeline directory directly.
- Without an explicit ID, use chained mode and read `activePipeline` from
  `.camel-kit/pipeline.json` when present.
- If neither source yields an ID, ask the user for a lowercase slug, run
  `{COMMAND_PREFIX} nextId <slug>`, and use the returned ID.

Create or update `.camel-kit/pipeline.json` with `activePipeline`,
`mode: "manual"`, and the current ISO-8601 `started` timestamp. If standalone
mode updates an existing migration design, mark its downstream plan stale after
the approved update and stop instead of chaining.
</Step>

<Step>
## Scan Source Artifacts

Read `.bob/skills/camel-brainstorm/guides/migration-discovery.md` for the full discovery process.

Scan the source project for integration artifacts. Detect:
- Vendor (MuleSoft, Fuse, Camel 2.x/3.x)
- Microsoft BizTalk (`.odx`, `.btm`, `.btp`, `.btproj`, or BizTalk XML namespaces)
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
- BizTalk: `.bob/skills/camel-migrate/guides/biztalk-component-mapping.md`, `.bob/skills/camel-migrate/guides/biztalk-map-conversion.md`, `.bob/skills/camel-migrate/guides/biztalk-expression-mapping.md`, `.bob/skills/camel-migrate/guides/biztalk-pipeline-mapping.md`

Verify EVERY component via MCP: `camel_catalog_component_doc`.
</Step>

<Step>
## Generate the Vendor Design Package

Run the detected vendor's two guides in order before requesting design approval:

- MuleSoft: `mulesoft-phase1.md`, then `mulesoft-phase2.md`
- Camel/Fuse: `camel-version-phase1.md`, then `camel-version-phase2.md`
- BizTalk: `biztalk-phase1.md`, then `biztalk-phase2.md`

Phase 1 writes `docs/camel-kit/<PIPELINE_ID>/business-requirements.md`.
Phase 2 writes the catalog-verified
`docs/camel-kit/<PIPELINE_ID>/design-spec.md`. Load the vendor's mapping,
conversion, and platform guides when directed by those phase guides.
</Step>

<Step>
## Assemble and Present Design Spec

Read `.bob/skills/camel-brainstorm/guides/design-assembly.md` for its assembly
format and self-review criteria only. Do not follow that guide's `Save and
Present` section; this gate owns the one save/presentation/approval sequence.

Assemble the migration design spec including:
- Migration context (source → target)
- All concern decisions
- Component mappings
- Route designs

Save it to `docs/camel-kit/<PIPELINE_ID>/design-spec.md` before presenting it.
Run `{COMMAND_PREFIX} doc init --by camel-migrate docs/camel-kit/<PIPELINE_ID>/business-requirements.md`, then
`{COMMAND_PREFIX} doc init --by camel-migrate --from business-requirements.md docs/camel-kit/<PIPELINE_ID>/design-spec.md`.

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
## Complete or Hand Off

- **Standalone design-only mode:** confirm the approved design package paths and
  stop. Do not transition.
- **Chained mode:** switch to **camel-plan-mode**, then read and follow
  `.bob/skills/camel-plan/SKILL.md` exactly once. That gate owns plan generation
  and its single downstream handoff through execute, verification, and
  validation. Do not reproduce those phases here.
</Step>
</Steps>

## Never

- Stop after implementation to print a summary or "Next Steps"
- Ask "Would you like me to continue?" between implement, verification, and validation
- Print "Migration complete" before verification and validation are done
- Skip verification or validation
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
| `.bob/skills/camel-migrate/guides/biztalk-phase1.md` | BizTalk business analysis |
| `.bob/skills/camel-migrate/guides/biztalk-phase2.md` | BizTalk technical design |
| `.bob/skills/camel-migrate/guides/biztalk-component-mapping.md` | BizTalk adapter and shape mappings |
| `.bob/skills/camel-migrate/guides/biztalk-map-conversion.md` | BizTalk map conversion |
| `.bob/skills/camel-migrate/guides/biztalk-expression-mapping.md` | BizTalk expression conversion |
| `.bob/skills/camel-migrate/guides/biztalk-pipeline-mapping.md` | BizTalk pipeline conversion |

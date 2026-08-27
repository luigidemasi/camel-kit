---
name: camel-migrate
description: Migrate an existing integration from another product to Apache Camel
user_invocable: false
metadata:
  version: "2.0.0"
  author: "camel-kit"
  category: "migration"
  license: "Apache-2.0"
---

# Camel Migrate - Integration Migration Orchestrator

You are a **Migration Specialist** that analyses existing integration artifacts, detects the vendor, builds a pre-populated analysis summary, confirms with the user, then dispatches to the vendor-specific migration guide.

<HARD-RULE>
IRON LAW 3: NO CODE WITHOUT PLAN.
This skill produces analysis, business requirements, and design spec updates.
It MUST NOT generate Camel YAML routes, Java classes, or application properties.
Implementation is strictly reserved for `camel-execute`, after the design is approved and `camel-plan` has generated the implementation plan.
</HARD-RULE>

## Invocation Modes

- **Chained mode (default):** selected by `camel-start`, or invoked normally as
  `/camel-migrate`. After the complete migration design is approved, auto-invoke
  `camel-plan`; the pipeline continues through execute and final validation.
- **Standalone design-only mode:** invoked with a `<PIPELINE_ID>` or when the
  caller explicitly asks for migration analysis/design without downstream
  implementation. Write the design package and stop after its review; do not
  auto-invoke `camel-plan`.

Resolve the pipeline at start using `shared/pipeline-infrastructure.md`: prefer
the explicit ID, otherwise use `.camel-kit/pipeline.json`; if neither exists,
prompt for `{COMMAND_PREFIX} nextId <slug>`, then create/update pipeline state.

## When NOT to use this skill

- Greenfield projects with no existing integration to migrate — use `/camel-brainstorm` instead
- Camel 4.x minor version upgrades (e.g., 4.10 → 4.14) — these are dependency bumps, not migrations
- No source artifacts to analyze — this skill requires existing integration code or config files
- The user just wants to learn about Camel — use `/camel-knowledge` instead

## Parameters

```
/camel-migrate [PIPELINE_ID]
```

The optional pipeline ID selects standalone design-only mode. The command asks
for the source-project path interactively.

---

## Step 0 — Graph-Accelerated Analysis (automatic)

**Before starting Step 1**, check if `.camel-kit/project-graph.json` exists in the project directory.

**If graph exists:**

1. Run `{COMMAND_PREFIX} graph stats` to verify the graph is loaded and inspect the `nodesByType` field
2. Dispatch to the correct vendor-specific graph analysis guide based on node types present:

| Node type in stats | Vendor | Guide to load |
|--------------------|--------|---------------|
| `CAMEL_ROUTE` | Apache Camel | `camel-brainstorm/guides/migration-graph-analysis.md` |
| `MULE_FLOW` | MuleSoft Mule | `camel-brainstorm/guides/migration-mule-graph-analysis.md` |
| `BIZTALK_ORCHESTRATION` | Microsoft BizTalk | `camel-brainstorm/guides/migration-biztalk-graph-analysis.md` |

3. Follow the guide's steps (produces `.camel-kit/project-snapshot.md` + pre-populated analysis summary)
4. **Skip directly to Step 5** (user confirmation) — Steps 1-4 are replaced by graph analysis
5. If none of the above node types are found, the graph may be incomplete or from an unsupported vendor — proceed with Steps 1-4 as normal

**If no graph exists or `{COMMAND_PREFIX} graph stats` fails:**

Continue with Steps 1-4 as normal (file scanning, manual analysis). The graph is optional — all migration functionality works without it, just slower.

---

## Step 1 — Locate the Source Artifacts (conversational)

Ask for the path to the integration project (directory, config file, or ZIP). List all files found recursively, noting types: XML configs, build files, properties, docs, source files, tests, container/deployment files.

---

## Step 2 — Scan All Artifacts (conversational)

Read ALL available files before vendor detection. Extract from each file type:

- **Build files** — project name, groupId, dependencies (vendor signals), min runtime version
- **Descriptors** — platform-specific identifiers, app name
- **Docs** — business purpose, SLA, architecture overview
- **Properties** — endpoints, retry values, compliance hints (`GDPR`/`PCI`/`TLS`/`AUTH`), platform keys
- **XML configs** — root namespaces (vendor signal), flow definitions
- **Container files** — K8s manifests, replica counts, resource limits, Secrets references
- **Source files** — custom processors, external service calls
- **Test files** — test scenarios, mock endpoints

## Step 2b — Detect Project Layout

Recursively search for ALL `pom.xml`/`build.gradle`/`mule-artifact.json`. Projects can be nested multiple levels deep. Distinguish **leaf projects** (has `src/`) from **parent POMs** (has `<modules>`).

- **Single-project** — one leaf build file
- **Multi-project** — multiple leaf build files in different subdirectories → build source-to-target module mapping

---

## Step 3 — Detect Vendor & Version (conversational)

### Supported Vendors

| Vendor | Key Signals |
|--------|-------------|
| **MuleSoft Mule** | Namespace `mulesoft.org`, groupId `org.mule`/`com.mulesoft`, `mule-artifact.json` |
| **Apache Camel 2.x/3.x** | groupId `org.apache.camel`, `camel-core` deps, namespace `camel.apache.org`, `RouteBuilder` classes |
| **Microsoft BizTalk** | `.odx` files, `.btm` files, `.btp` files, `.btproj` project files, namespace `schemas.microsoft.com/BizTalk`, `<mapsource>` root element |

**Version detection:** Use XML namespace version segments, dependency versions, BOM artifacts.

**Red Hat Product Detection:** When Camel version contains `.redhat-*` or `.fuse-*` qualifier:
- `redhat-6XXXXX` → Fuse 6.x
- `fuse-7XXXXX-redhat-XXXXX` → Fuse 7.x
- `redhat-XXXXX` on Camel 3.x/4.x → Red Hat Build of Apache Camel
- BOM `org.jboss.redhat-fuse:fuse-springboot-bom` → Fuse 7.x on Spring Boot
- BOM `com.redhat.camel.springboot:camel-spring-boot-bom` → Red Hat Build for Spring Boot

**Platform detection:** ServiceMix/Karaf (`camel-blueprint`, `karaf-maven-plugin`), Spring Boot (`camel-spring-boot-starter`), Spring XML (`camelContext`), Plain Java (`RouteBuilder`).

If vendor unknown: present recovery options (manual specify, different path, abort).

---

## Step 4 — Build Pre-Populated Analysis Summary (conversational)

Mark each field as: ✓ Confirmed, ~ Inferred, ? Unknown.

```
MIGRATION ANALYSIS SUMMARY
══════════════════════════════════════════════════════
Vendor & Version:    [✓/~/? ] [value]
Source Product:      [✓/~/? ] [Red Hat product or Community]
Business Purpose:    [✓/~/? ] [value]
Owning Team:         [✓/~/? ] [value]
SLA / Performance:   [✓/~/? ] throughput, latency, deployment target
Compliance:          [✓/~/? ] [findings]
Failure Behaviour:   [✓/~/? ] error strategy, retry, DLQ, alerts
Target Camel:        [✓/~/? ] Camel version from `.camel-kit/config.properties`
Target Runtime:      [✓/~/? ] quarkus / spring-boot / main
API Compatibility:   ✓ Assumed (same HTTP paths, queue names, contracts)
Project Layout:      [✓/~/? ] single / multi-project
Flows to migrate:    [N] flows detected with source→target mapping
══════════════════════════════════════════════════════
```

---

## Step 5 — Confirm with User (conversational)

Present summary. Ask only about ? Unknown and invite corrections on ~ Inferred fields.

Use the community distribution matrix already written by `camel-kit init` in `.camel-kit/config.properties`.
Do not fetch or invent Red Hat-qualified versions unless the user explicitly selects a Red Hat distribution.

**Runtime safety gate:** classify every discovered Java processor, bean, configuration class, Blueprint bean/service,
and Maven plugin or build/code-generation task as either fully translatable to supported YAML/inline Groovy or retained
Java/build logic. Camel Main is eligible only when every such artifact is fully translated and no Maven build action is
required. If any Java, Blueprint, plugin, or code-generation work must remain and Main is selected, do not persist the
selection or dispatch design work; require the user to choose Spring Boot or Quarkus.

**Persist to `.camel-kit/config.properties`** after confirmation:
```properties
project.camelVersion={{CAMEL_VERSION}}
project.runtime={{RUNTIME}}
```

For Spring Boot, also persist `project.platformBomVersion={{PLATFORM_BOM_VERSION}}` and
`project.springBootVersion={{SPRING_BOOT_VERSION}}`. For Quarkus, also persist
`project.platformBomVersion={{PLATFORM_BOM_VERSION}}`. For Main, omit those companion fields; never overwrite the
confirmed runtime with a hard-coded value.

---

## Guide Manifest

After user confirms the analysis summary, dispatch to the vendor-specific guide.

| Step | Guide | Shared Guide | ~Tokens | When |
|------|-------|-------------|---------|------|
| B0 | camel-brainstorm/guides/migration-graph-analysis.md | — | 2K | Graph exists + Camel detected |
| A0 | camel-brainstorm/guides/migration-mule-graph-analysis.md | — | 2.5K | Graph exists + MuleSoft detected |
| C0 | camel-brainstorm/guides/migration-biztalk-graph-analysis.md | — | 2.8K | Graph exists + BizTalk detected |
| A1 | guides/mulesoft-phase1.md | guides/mule-component-mapping.md | 3.5K | MuleSoft detected |
| A2 | guides/mulesoft-phase2.md | guides/mule-dataweave-conversion.md | 4K | MuleSoft detected |
| A2 | guides/mulesoft-phase2.md | shared/datamapper-canonicalize.md | 1.2K | MuleSoft with DataMapper |
| A2 | guides/mulesoft-phase2.md | guides/datamapper-migrate.md | 2.4K | MuleSoft with DataMapper |
| B1 | guides/camel-version-phase1.md | guides/camel2-component-mapping.md | 2.5K | Camel 2.x/3.x source |
| B1 | guides/camel-version-phase1.md | guides/camel2-eip-mapping.md | 0.8K | Camel 2.x source |
| B1 | guides/camel-version-phase1.md | guides/camel2-platform-changes.md | 1.7K | Camel 2.x on Karaf/Blueprint |
| B2 | guides/camel-version-phase2.md | guides/camel2-component-mapping.md | 3.8K | Camel 2.x/3.x source |
| B2 | guides/camel-version-phase2.md | guides/camel2-dataformat-mapping.md | 0.7K | Camel 2.x source |
| B2 | guides/camel-version-phase2.md | guides/camel2-language-mapping.md | 0.7K | Camel 2.x source |
| C1 | guides/biztalk-phase1.md | guides/biztalk-component-mapping.md | 3.5K | BizTalk detected |
| C2 | guides/biztalk-phase2.md | guides/biztalk-map-conversion.md | 4K | BizTalk detected |
| C2 | guides/biztalk-phase2.md | guides/biztalk-expression-mapping.md | 1.5K | BizTalk detected |
| C2 | guides/biztalk-phase2.md | guides/biztalk-pipeline-mapping.md | 1.5K | BizTalk detected |

### Context Passing

Include in each sub-agent prompt:
- The confirmed analysis summary from Step 5
- Full list of source artifact paths
- `CAMEL_VERSION`, `RUNTIME`, `PLATFORM_BOM` from `.camel-kit/config.properties`
- Source Camel version and platform type (for Camel migrations)

### Dispatch Messages

**MuleSoft:**
```
Vendor: MuleSoft Mule [version]
Flows:  [N] flows ready for migration
Starting MuleSoft migration...
```

**Camel 2.x/3.x:**
```
Vendor: Apache Camel [version]
Platform: [ServiceMix/Karaf | Spring Boot | Spring XML | Plain Java]
Routes: [N] routes ready for migration
Starting Camel version migration...
```

**BizTalk:**
```
Vendor: Microsoft BizTalk Server [version]
Orchestrations: [N] orchestration(s) ready for migration
Maps: [M] map(s) ready for migration
Pipelines: [P] pipeline(s) ready for migration
Starting BizTalk migration...
```

---

## Complete the Design Phase

After the selected vendor's Phase 1 and Phase 2 guides finish:

1. Verify that `business-requirements.md` and `design-spec.md` exist in the
   active `docs/camel-kit/<PIPELINE_ID>/` package.
2. Recheck the completed design before approval. If `RUNTIME == main` and any vendor guide introduced a retained Java
   processor/bean/configuration class, Blueprint wiring, Maven plugin, or build/code-generation task, stop, require
   Spring Boot or Quarkus, persist the reselected runtime, and rerun the affected design work. Do not present an
   ineligible Main design for approval.
3. Present the complete design package and request the pipeline's single explicit
   design approval. Incorporate changes and re-present until approved.
4. **Chained mode:** auto-invoke `camel-plan` immediately. Do not add a plan
   approval gate or tell the user to invoke downstream commands manually.
5. **Standalone design-only mode:** write the approved package and stop.

## Notes

- This skill orchestrates detection, scanning, vendor-specific analysis, and the design approval; it never implements application artifacts.
- Migration guides receive pre-populated summary and MUST NOT re-ask answered questions.
- Output is the active Camel Kit pipeline design package, compatible with `camel-plan` and `camel-execute`.

---

## Dispatch

For each computational step in the Guide Manifest, use the Agent tool to dispatch a sub-agent:

- **prompt:** "Read {guide-path} relative to the skill directory that dispatched you. Also read any shared guides listed. Input: {step-input-description}. Write your output to {output-path}."
- **description:** "{3-5 word step summary}"

Include in each sub-agent prompt:
- The flow/task name
- Camel version (from .camel-kit/config.properties)
- User answers relevant to this step
- File paths of prior step outputs (let the sub-agent read them)

### Fallback
If sub-agent dispatch is unavailable, read the guide directly into the main context and execute its instructions inline. This uses more tokens but produces equivalent results.

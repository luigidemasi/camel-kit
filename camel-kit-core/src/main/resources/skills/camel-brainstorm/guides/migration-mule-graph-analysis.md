# MuleSoft Migration Graph Analysis Guide

> **Context:** Loaded by `migration-discovery.md` when `.camel-kit/project-graph.json` exists and contains `MULE_FLOW` nodes.
> **Purpose:** Replace manual XML deep-dives with instant graph queries for accelerated MuleSoft migration analysis.
> **Output:** `.camel-kit/project-snapshot.md` + pre-populated analysis summary for user confirmation.

This guide uses CLI commands under `{COMMAND_PREFIX} graph`. If any command fails (exit code != 0), fall back gracefully — skip that section and note it as `? Unknown` in the summary.

<HARD-RULE>
NEVER read `.camel-kit/project-graph.json` directly. Always use `{COMMAND_PREFIX} graph` CLI commands. The JSON file is thousands of lines and will overflow your context window.
</HARD-RULE>

Read `.camel-kit/config.properties` to get the `project.command-prefix` property (default: `camel-kit`).

---

## Step 0.1 — Project Overview

Run the command:
```bash
{COMMAND_PREFIX} graph stats
```

This returns JSON with the project's structural summary. For a MuleSoft project, expect node types like `MULE_FLOW`, `MULE_SUB_FLOW`, `MULE_ENDPOINT`, `MULE_CONNECTOR`, `MULE_PROCESSOR`, `MULE_TRANSFORM`, `MULE_ERROR_HANDLER`, and `DATAWEAVE_SCRIPT`.

Record:
- Total node and edge counts
- Number of flows, sub-flows, endpoints, connectors, processors, transforms
- Number of DataWeave scripts and error handlers
- Number of Maven artifacts and config properties

---

## Step 0.2 — Vendor & Version Detection

Run the command:
```bash
{COMMAND_PREFIX} graph find --type MAVEN_ARTIFACT
```

Scan for MuleSoft vendor signals:

| Signal | Detection |
|--------|-----------|
| groupId `org.mule` or `com.mulesoft` | MuleSoft Mule |
| `mule-core` version 3.x | Mule 3.x |
| `mule-core` version 4.x | Mule 4.x |
| `mule-artifact.json` present | Mule 4.x |
| Anypoint connector groupId `org.mule.connectors` | Mule 4.x connectors |
| Connector groupId `org.mule.transports` | Mule 3.x transports |

Extract Mule version from `mule-core` or Mule BOM artifact.

---

## Step 0.3 — Flow Inventory

Run the command to list all flows:
```bash
{COMMAND_PREFIX} graph find --type MULE_FLOW
```

Run the command to list all sub-flows:
```bash
{COMMAND_PREFIX} graph find --type MULE_SUB_FLOW
```

For each flow, query its children to extract endpoints, processors, and transforms:
```bash
{COMMAND_PREFIX} graph neighbors <flowId> --direction out --edge-type MULE_FLOW_CONTAINS
```

For each flow, list:
- Source endpoint (first `MULE_ENDPOINT` child — the listener/consumer)
- Sink endpoints (subsequent `MULE_ENDPOINT` children — requests/publishers)
- Processors in order (by edge `order` property)
- Transforms (DataWeave)
- Error handlers

Build the **Component Inventory**: extract all unique endpoint element types (e.g., `http:listener`, `jms:consume`, `db:select`) with usage counts and which flows use them.

---

## Step 0.4 — Flow Connectivity (Sub-flow Call Graph)

Query the sub-flow call edges:
```bash
{COMMAND_PREFIX} graph find --type MULE_FLOW
```

For each flow, check outbound `MULE_CALLS_SUBFLOW` edges:
```bash
{COMMAND_PREFIX} graph neighbors <flowId> --direction out --edge-type MULE_CALLS_SUBFLOW
```

Build the flow-to-sub-flow call graph. This reveals:
- Which flows call which sub-flows
- Shared sub-flows used by multiple flows
- Isolated flows with no sub-flow dependencies

---

## Step 0.5 — Connector Configuration

Run the command:
```bash
{COMMAND_PREFIX} graph find --type MULE_CONNECTOR
```

For each connector, find which endpoints reference it:
```bash
{COMMAND_PREFIX} graph neighbors <connectorId> --direction in --edge-type MULE_USES_CONNECTOR
```

Record connector names, types, and which endpoints use each connector. This identifies shared configuration (e.g., a single HTTP listener config used by multiple flows).

---

## Step 0.6 — DataWeave Transformation Inventory

Run the command:
```bash
{COMMAND_PREFIX} graph find --type MULE_TRANSFORM
```

For each transform, check for external DWL references:
```bash
{COMMAND_PREFIX} graph neighbors <transformId> --direction out --edge-type MULE_REFERENCES_DWL
```

List all DataWeave scripts:
```bash
{COMMAND_PREFIX} graph find --type DATAWEAVE_SCRIPT
```

Record:
- Total number of transforms
- How many reference external `.dwl` files vs inline DataWeave
- DWL file paths for external scripts

---

## Step 0.7 — Structural Analysis

Using the flow connectivity data:

1. **Entry-point flows:** Flows with an inbound endpoint (listener, consumer) — these are externally triggered
2. **Internal sub-flows:** `MULE_SUB_FLOW` nodes — only invoked via `<flow-ref>`
3. **Leaf flows:** Flows with no outbound `MULE_CALLS_SUBFLOW` edges

**Migration ordering** — reverse dependency order:
1. Sub-flows first (can be migrated as independent Camel routes with `direct:` endpoints)
2. Flows that only call already-migrated sub-flows
3. Entry-point flows last

**Structural warnings:**
- **Orphaned sub-flows:** `MULE_SUB_FLOW` nodes with no inbound `MULE_CALLS_SUBFLOW` edges (never called)
- **Missing connectors:** Endpoints with `config-ref` but no matching `MULE_CONNECTOR` node
- **Unresolved flow-refs:** `MULE_CALLS_SUBFLOW` edges pointing to non-existent sub-flows

---

## Step 0.8 — Persist Snapshot & Build Summary

Write to `.camel-kit/project-snapshot.md`:

```markdown
# Project Snapshot

Generated: [timestamp]
Graph: .camel-kit/project-graph.json
Source Platform: MuleSoft Mule [version]

## Project Overview
- Flows: [N] | Sub-flows: [N] | Endpoints: [N] | Connectors: [N]
- Processors: [N] | Transforms: [N] | DataWeave Scripts: [N]
- Error Handlers: [N] | Maven Artifacts: [N]

## Vendor & Platform
- Vendor: MuleSoft Mule [3.x or 4.x]
- Connector Type: [Anypoint connectors / community transports]

## Flow Topology
| Flow | Type | Source Endpoint | Sink Endpoints | Calls Sub-flows |
|------|------|-----------------|----------------|-----------------|
| [name] | flow | [source endpoint] | [sink endpoints] | [sub-flow names or —] |
| [name] | sub-flow | — | [endpoints] | [sub-flow names or —] |

## Component Inventory
| Element | Type | Usage Count | Flows |
|---------|------|-------------|-------|
| [element name] | endpoint/processor/transform | [count] | [flow list] |

## Connector Configuration
| Connector Name | Element | Used By Endpoints |
|----------------|---------|-------------------|
| [name] | [element type] | [endpoint list] |

## DataWeave Scripts
| Transform | External DWL | DWL Path |
|-----------|-------------|----------|
| [transform id] | yes/no | [path or inline] |

## Migration Ordering
1. [sub-flow name] (leaf sub-flow — no outbound calls)
2. [flow name] (depends on [sub-flow names])
...

## Structural Warnings
- [any orphaned sub-flows, missing connectors, unresolved flow-refs]

## Dependencies
| GroupId | ArtifactId | Version |
|---------|-----------|---------|
| [groupId] | [artifactId] | [version] |
```

Build the pre-populated analysis summary:

```
MIGRATION ANALYSIS SUMMARY
===========================================================
Vendor & Version:    MuleSoft Mule [version from graph]
Source Product:      MuleSoft Anypoint Platform
Failure Behaviour:   [inferred from error handler presence]
Target Camel:        Camel version (default: latest)
Target Runtime:      [to be selected]
API Compatibility:   Assumed (same HTTP paths, queue names, contracts)
Project Layout:      [single or multi from graph]
Flows to migrate:    ALL ([N] flows + [M] sub-flows detected with migration ordering)
===========================================================
```

**Return to `migration-discovery.md` Step 5** for user confirmation, then Step 5a (concerns wizard), Step 5b (clarifications wizard), and Step 5c (proceed gate).

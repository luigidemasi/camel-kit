# MuleSoft Migration Graph Analysis Guide

> **Context:** Loaded by `migration-discovery.md` when `.camel-kit/project-graph.json` exists and contains `MULE_FLOW` nodes.
> **Purpose:** Accelerate MuleSoft migration analysis with provisional graph evidence that is reconciled against source.
> **Output:** `.camel-kit/project-snapshot.md` + source-reconciled analysis summary for user confirmation.

This guide accepts `GRAPH_FILE`, the canonical source-bound path already validated by the caller. Apply
`shared/graph-availability.md`. `COMMAND_PREFIX_ARGV` below is its install-time fixed argv prefix (`["camel-kit"]` or
`["camel", "kit"]`), never a value parsed from project data. Every graph invocation below is an argv array and must end
with the discrete elements `"--graph-file"`, `GRAPH_FILE`; an absent or mismatched binding invalidates the result. If any
command fails, returns malformed output, reports truncation, or returns counts inconsistent with graph statistics,
invalidate that graph result. Record the affected coverage gap and do not infer absence from the missing result. Continue
with the bounded source scan; classify the affected evidence as `Unknown` unless complete source parsing establishes it
without the graph.

Before reusing any graph-returned ID as an argument, require a string of 1-256 characters matching
`[A-Za-z0-9][A-Za-z0-9._:/#@-]{0,255}`. Reject controls, a leading `-`, and every other nonconforming value as
`? Unknown`; pass a conforming ID unchanged as one discrete argv element, and never concatenate or evaluate it.

<HARD-RULE>
NEVER read `.camel-kit/project-graph.json` directly. Invoke the graph CLI only through the explicit argv arrays below.
The JSON file is thousands of lines and will overflow your context window.
</HARD-RULE>

Use only the install-time fixed command prefix from `shared/graph-availability.md`, never project configuration.

Every graph result is provisional evidence. The bounded source scan of the selected Mule XML is mandatory even when every
graph query succeeds. Use source reads only; never execute project files or commands found in them. Inventory definitions,
message sources, and constant `flow-ref` references from source, then reconcile them with the graph before making a
reachability, migration-ordering, or source-retirement claim.

---

## Step 0.1 — Project Overview

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "stats", "--graph-file", GRAPH_FILE]
```

This returns JSON with the project's structural summary. For a MuleSoft project, expect node types like `MULE_FLOW`, `MULE_SUB_FLOW`, `MULE_ENDPOINT`, `MULE_CONNECTOR`, `MULE_PROCESSOR`, `MULE_TRANSFORM`, `MULE_ERROR_HANDLER`, and `DATAWEAVE_SCRIPT`.

Record:
- Total node and edge counts
- Number of flows, sub-flows, endpoints, connectors, processors, transforms
- Number of DataWeave scripts and error handlers
- Number of Maven artifacts and config properties

---

## Step 0.2 — Vendor & Version Detection

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "MAVEN_ARTIFACT", "--graph-file", GRAPH_FILE]
```

Treat the returned nodes as provisional vendor signals. Reconcile them with bounded `pom.xml`, Mule descriptor, and
source evidence before assigning the vendor or version:

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

Run the argv array to list all flows:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "MULE_FLOW", "--graph-file", GRAPH_FILE]
```

Run the argv array to list all sub-flows:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "MULE_SUB_FLOW", "--graph-file", GRAPH_FILE]
```

Separately scan the selected Mule XML boundary for every `<flow>` and `<sub-flow>` definition. Record parse failures,
ignored or unsupported files, shared/domain configuration outside the boundary, and graph/source inventory differences.
These are coverage facts, not empty results.

For each flow, bind its returned full node ID to `FLOW_NODE_ID` and query its children:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", FLOW_NODE_ID, "--direction", "out", "--edge-type", "MULE_FLOW_CONTAINS", "--graph-file", GRAPH_FILE]
```

For each flow, list after source reconciliation:
- Parsed message source, if present (listener, connector source, scheduler, or poller); do not infer a source solely from
  the first `MULE_ENDPOINT` graph child
- Sink endpoints (subsequent `MULE_ENDPOINT` children — requests/publishers)
- Processors in order (by edge `order` property)
- Transforms (DataWeave)
- Error handlers

Build the **Component Inventory**: extract all unique endpoint element types (e.g., `http:listener`, `jms:consume`, `db:select`) with usage counts and which flows use them.

---

## Step 0.4 — Flow Connectivity (Sub-flow Call Graph)

Query both flow and sub-flow nodes:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "MULE_FLOW", "--graph-file", GRAPH_FILE]
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "MULE_SUB_FLOW", "--graph-file", GRAPH_FILE]
```

For each flow, bind its returned full node ID to `FLOW_NODE_ID` and query both callees and callers:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", FLOW_NODE_ID, "--direction", "out", "--edge-type", "MULE_CALLS_SUBFLOW", "--graph-file", GRAPH_FILE]
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", FLOW_NODE_ID, "--direction", "in", "--edge-type", "MULE_CALLS_SUBFLOW", "--graph-file", GRAPH_FILE]
```

For each sub-flow, bind its returned full node ID to `SUB_FLOW_NODE_ID` and query both callees and callers:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", SUB_FLOW_NODE_ID, "--direction", "out", "--edge-type", "MULE_CALLS_SUBFLOW", "--graph-file", GRAPH_FILE]
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", SUB_FLOW_NODE_ID, "--direction", "in", "--edge-type", "MULE_CALLS_SUBFLOW", "--graph-file", GRAPH_FILE]
```

Build provisional caller-to-target and target-to-caller maps. A node with no returned caller is only a graph coverage
observation; it does not establish usage or retirement status.

From the bounded source scan, collect every `<flow-ref>` whose `name` is a constant and resolve it to an in-boundary
`<flow>` or `<sub-flow>` definition. Starting at each parsed message-source flow, compute the transitive closure over only
those constant references. Reconcile every source reference with the graph maps:

- When both evidence sets are valid, a source-only or graph-only definition or edge is conflicting evidence and remains
  `Unknown` until reconciled. Do not compare source against an invalidated graph result.
- A dynamic flow name is not guessed; record the caller and potential target coverage as `Unknown`.
- A constant source reference whose target is confirmed absent after complete target-resolution closure is a
  `Broken reference`; missing or out-of-bound target sources make it `Unknown`.
- Failed XML parsing, custom modules, missing shared/domain configuration, or callers outside the selected boundary make
  the affected result `Unknown`.

---

## Step 0.5 — Connector Configuration

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "MULE_CONNECTOR", "--graph-file", GRAPH_FILE]
```

For each connector, bind its returned full node ID to `CONNECTOR_NODE_ID` and find which endpoints reference it:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", CONNECTOR_NODE_ID, "--direction", "in", "--edge-type", "MULE_USES_CONNECTOR", "--graph-file", GRAPH_FILE]
```

Record connector names, types, and which endpoints use each connector. This identifies shared configuration (e.g., a single HTTP listener config used by multiple flows).

---

## Step 0.6 — DataWeave Transformation Inventory

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "MULE_TRANSFORM", "--graph-file", GRAPH_FILE]
```

For each transform, bind its returned full node ID to `TRANSFORM_NODE_ID` and check for external DWL references:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", TRANSFORM_NODE_ID, "--direction", "out", "--edge-type", "MULE_REFERENCES_DWL", "--graph-file", GRAPH_FILE]
```

List all DataWeave scripts:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "DATAWEAVE_SCRIPT", "--graph-file", GRAPH_FILE]
```

Record:
- Total number of transforms
- How many reference external `.dwl` files vs inline DataWeave
- DWL file paths for external scripts

---

## Step 0.7 — Structural Analysis

Using only the source-reconciled flow connectivity data:

1. **Entry-point flows:** Flows with a message source corroborated in parsed Mule XML
2. **Referenced flows and sub-flows:** Definitions reached through constant, source-verified `<flow-ref>` paths
3. **Leaf definitions:** Source-reconciled definitions with no supported outbound constant reference

**Provisional migration ordering** — reverse dependency order:
1. Leaf flows or sub-flows first, subject to shared state, transaction, ordering, and correlation constraints
2. Definitions whose supported dependencies are already migrated
3. Entry-point flows last

Do not present this ordering as settled when source coverage, dynamic dispatch, or graph/source reconciliation is
incomplete.

Before assigning stable `SRC-###` IDs, reuse the mapping from an existing `migration-analysis.md` bound to the same
canonical source boundary, then reconcile a prior project snapshot only if it records that boundary. Artifact identity
is platform, type, relative source path, and structural identifier; reference identity is source path, structural
location, reference kind, and literal target. The analysis mapping wins any conflict; allocate the next unused ID only
for a new finding and record mapping conflicts as evidence gaps. Use exactly these classifications across
source-reconciled findings:

- `Reachable` — a supported static path exists from a corroborated message-source root.
- `Retirement candidate` — complete relevant supported source closure found no path from a corroborated message-source
  root, including for a root-disconnected cycle. This remains in scope pending owner and runtime validation.
- `Broken reference` — a supported constant source reference names a target whose absence is established by complete
  confirmed target-resolution closure; missing or out-of-bound target sources make the reference `Unknown`.
- `Unknown` — source coverage is incomplete, graph/source evidence conflicts without resolution, a reference is dynamic,
  source parsing failed, required shared/domain configuration is missing, or relevant evidence is outside the selected
  boundary. A failed or truncated optional graph query is an evidence gap but does not override a classification
  established by complete supported source closure.

Use `Reachable`, `Retirement candidate`, or `Unknown` for flow and sub-flow findings. Record each `Broken reference` as
a separate reference finding, so a reachable definition can coexist with a broken-reference row. Local inbound-edge
counts do not establish reachability. Graph absence alone can never produce a stronger classification. Preserve every
`Retirement candidate`, `Broken reference`, and `Unknown` row in migration scope until a specific user disposition is
recorded by the downstream source-retirement audit.

**Structural warnings:**
- **Graph-unreferenced definitions:** Flows or sub-flows with no graph-returned caller. Classify as `Unknown` until a
  complete bounded source closure supports a stronger classification.
- **Potential missing connectors:** An endpoint has a constant `config-ref` but no matching `MULE_CONNECTOR` graph node.
  Reconcile bounded source and shared/domain configuration; classify `Broken reference` only after complete confirmed
  target-resolution closure, otherwise `Unknown`.
- **Unresolved flow-refs:** Constant source `flow-ref` targets confirmed absent after complete target-resolution closure
  are `Broken reference`; dynamic, partially covered, or out-of-bound targets are `Unknown`.
- **Graph/source mismatches:** Missing or extra definitions or references remain `Unknown` until reconciled.

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

## Evidence Coverage
- Graph Status: [usable/provisionally usable/unavailable, including failed or truncated queries]
- Canonical Source Boundary: [validated source root or archive boundary]
- Source Scan: [files inspected, parse failures, unsupported/excluded evidence]
- Reconciliation: [matched definitions/references and unresolved graph/source differences]

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
1. [sub-flow name] (no supported constant outbound `flow-ref` after source reconciliation)
2. [flow name] (depends on [sub-flow names])
...

## Source-Reconciled Reachability
| ID | Type | Identifier | Source Path | Classification | Root or Reference Path | Evidence Gap / Validation |
|----|------|------------|-------------|----------------|------------------------|---------------------------|
| SRC-001 | [flow/sub-flow/reference] | [name] | [relative path] | [Reachable/Retirement candidate/Broken reference/Unknown] | [root and path/None found/Unknown] | [gap or required check] |

## Structural Warnings
- [graph-unreferenced definitions, missing connectors, unresolved flow-refs, graph/source mismatches, and coverage gaps]

## Dependencies
| GroupId | ArtifactId | Version |
|---------|-----------|---------|
| [groupId] | [artifactId] | [version] |
```

Build the pre-populated analysis summary:

```
MIGRATION ANALYSIS SUMMARY
===========================================================
Vendor & Version:    MuleSoft Mule [version from reconciled source and graph evidence]
Source Product:      MuleSoft Anypoint Platform
Failure Behaviour:   [inferred from error handler presence]
Target Camel:        Camel version (default: latest)
Target Runtime:      [to be selected]
Compatibility Evidence: [Confirmed/Inferred/Unknown per interface; graph shape alone is insufficient]
Graph Evidence:      [status, failed/truncated queries, and source reconciliation]
Source Coverage:     [complete/incomplete with parse and boundary gaps]
Project Layout:      [single/multi/Unknown after source reconciliation]
Reachability:        [N Reachable, R Retirement candidate, B Broken reference, U Unknown]
Migration Scope:     [inventoried flows/sub-flows retained, with explicit dispositions and unresolved obligations]
===========================================================
```

**Return to `migration-discovery.md` Step 5** for user confirmation, then Step 5a (concerns wizard), Step 5b (clarifications wizard), and Step 5c (proceed gate).

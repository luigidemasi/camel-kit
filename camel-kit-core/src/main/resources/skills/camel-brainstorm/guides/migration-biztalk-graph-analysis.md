# BizTalk Migration Graph Analysis Guide

> **Context:** Loaded by `migration-discovery.md` when `.camel-kit/project-graph.json` exists and contains `BIZTALK_ORCHESTRATION` nodes.
> **Purpose:** Replace manual `.odx`/`.btm`/`.btp` deep-dives with instant graph queries for accelerated BizTalk migration analysis.
> **Output:** `.camel-kit/project-snapshot.md` + pre-populated analysis summary for user confirmation.

This guide accepts `GRAPH_FILE`, the canonical source-bound path already validated by the caller. Apply
`shared/graph-availability.md`. `COMMAND_PREFIX_ARGV` below is its install-time fixed argv prefix (`["camel-kit"]` or
`["camel", "kit"]`), never a value parsed from project data. Every graph invocation below is an argv array and must end
with the discrete elements `"--graph-file"`, `GRAPH_FILE`; an absent or mismatched binding invalidates the result. If any
command fails, skip that section and note it as `? Unknown` in the summary.

Before reusing any graph-returned ID as an argument, require a string of 1-256 characters matching
`[A-Za-z0-9][A-Za-z0-9._:/#@-]{0,255}`. Reject controls, a leading `-`, and every other nonconforming value as
`? Unknown`; pass a conforming ID unchanged as one discrete argv element, and never concatenate or evaluate it.

<HARD-RULE>
NEVER read `.camel-kit/project-graph.json` directly. Invoke the graph CLI only through the explicit argv arrays below.
The JSON file is thousands of lines and will overflow your context window.
</HARD-RULE>

Use only the install-time fixed command prefix from `shared/graph-availability.md`, never project configuration.

---

## Step 0.1 — Project Overview

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "stats", "--graph-file", GRAPH_FILE]
```

This returns JSON with the project's structural summary. For a BizTalk project, expect node types like `BIZTALK_ORCHESTRATION`, `BIZTALK_SHAPE`, `BIZTALK_MAP`, `BIZTALK_FUNCTOID`, `BIZTALK_SCHEMA`, `BIZTALK_PIPELINE`, `BIZTALK_PIPELINE_COMPONENT`, `BIZTALK_PORT`, `BIZTALK_ADAPTER`, and `BIZTALK_MESSAGE`.

Record:
- Total node and edge counts
- Number of orchestrations, shapes, maps, functoids
- Number of schemas, pipelines, pipeline components
- Number of ports, adapters, messages

---

## Step 0.2 — Orchestration Inventory

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "BIZTALK_ORCHESTRATION", "--graph-file", GRAPH_FILE]
```

For each orchestration, bind its returned full node ID to `ORCHESTRATION_NODE_ID` and query its children:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", ORCHESTRATION_NODE_ID, "--direction", "out", "--edge-type", "BIZTALK_ORCHESTRATION_CONTAINS", "--graph-file", GRAPH_FILE]
```

This returns `BIZTALK_SHAPE`, `BIZTALK_PORT`, and `BIZTALK_MESSAGE` nodes. For each orchestration, list:
- Orchestration name and source file
- Shapes in order (Receive, Send, Decide, Transform, Construct Message, Expression, Loop, etc.)
- Ports (Receive Ports, Send Ports) with adapter types
- Messages with their schemas

---

## Step 0.3 — Map & Schema Inventory

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "BIZTALK_MAP", "--graph-file", GRAPH_FILE]
```

For each map, bind its returned full node ID to `MAP_NODE_ID` and query its schema references and functoid chains:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", MAP_NODE_ID, "--direction", "out", "--edge-type", "BIZTALK_USES_SCHEMA", "--graph-file", GRAPH_FILE]
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", MAP_NODE_ID, "--direction", "out", "--edge-type", "BIZTALK_FUNCTOID_CHAIN", "--graph-file", GRAPH_FILE]
```

List all schemas:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "BIZTALK_SCHEMA", "--graph-file", GRAPH_FILE]
```

For each map, record:
- Source schema and target schema
- Functoids used (from `BIZTALK_FUNCTOID` nodes)
- Complexity rating:
  - Simple: No functoids (direct links only)
  - Medium: Basic functoids (String, Math, Logical)
  - Complex: Scripting functoids, Database Lookup, custom XSLT

Which orchestration shapes reference each map:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", MAP_NODE_ID, "--direction", "in", "--edge-type", "BIZTALK_USES_MAP", "--graph-file", GRAPH_FILE]
```

---

## Step 0.4 — Pipeline Inventory

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "BIZTALK_PIPELINE", "--graph-file", GRAPH_FILE]
```

For each pipeline, bind its returned full node ID to `PIPELINE_NODE_ID` and query its stages and components:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", PIPELINE_NODE_ID, "--direction", "out", "--edge-type", "BIZTALK_PIPELINE_STAGE", "--graph-file", GRAPH_FILE]
```

Record:
- Pipeline name and type (Receive or Send)
- Components per stage (XML Disassembler, JSON Encoder, MIME Decoder, etc.)
- Custom components (flag for manual review)

---

## Step 0.5 — Port & Adapter Inventory

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "BIZTALK_PORT", "--graph-file", GRAPH_FILE]
```

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "BIZTALK_ADAPTER", "--graph-file", GRAPH_FILE]
```

For each port, bind its returned full node ID to `PORT_NODE_ID` and check its adapter bindings:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", PORT_NODE_ID, "--direction", "out", "--edge-type", "BIZTALK_PORT_BINDING", "--graph-file", GRAPH_FILE]
```

Record:
- Port name, direction (Receive/Send), and adapter type
- Adapter configuration (protocol, address, authentication)
- Port-to-pipeline assignments

---

## Step 0.6 — Orchestration Call Graph

Check for inter-orchestration calls:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "BIZTALK_ORCHESTRATION", "--graph-file", GRAPH_FILE]
```

For each orchestration, check outbound call edges:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", ORCHESTRATION_NODE_ID, "--direction", "out", "--edge-type", "BIZTALK_CALLS_ORCHESTRATION", "--graph-file", GRAPH_FILE]
```

Build the orchestration-to-orchestration call graph. This reveals:
- Which orchestrations call sub-orchestrations
- Shared orchestrations invoked by multiple callers
- Independent orchestrations with no inter-orchestration dependencies

---

## Step 0.7 — Structural Analysis

Using the orchestration call graph and port/adapter data:

1. **Entry-point orchestrations:** Have Receive Ports with external adapters (FILE, HTTP, FTP, MSMQ, etc.)
2. **Sub-orchestrations:** Called only via `BIZTALK_CALLS_ORCHESTRATION` from other orchestrations
3. **Leaf orchestrations:** No outbound `BIZTALK_CALLS_ORCHESTRATION` edges

**Migration ordering** — reverse dependency order:
1. Sub-orchestrations first (can be migrated as independent Camel routes)
2. Orchestrations depending only on already-migrated sub-orchestrations
3. Entry-point orchestrations last

**Structural warnings:**
- **Orphaned orchestrations:** No Receive Port and no inbound `BIZTALK_CALLS_ORCHESTRATION` edges
- **Complex maps:** Maps with Scripting functoids or Database Lookup (require manual conversion)
- **Custom pipeline components:** `BIZTALK_PIPELINE_COMPONENT` nodes not matching standard BizTalk components
- **Unresolved references:** Shapes referencing maps that have no corresponding `BIZTALK_MAP` node
- **Proprietary adapters:** Adapters with no direct Camel equivalent (MSMQ, WCF-Custom, third-party)

---

## Step 0.8 — Persist Snapshot & Build Summary

Write to `.camel-kit/project-snapshot.md`:

```markdown
# Project Snapshot

Generated: [timestamp]
Graph: .camel-kit/project-graph.json
Source Platform: Microsoft BizTalk Server [version]

## Project Overview
- Orchestrations: [N] | Shapes: [N] | Maps: [N] | Functoids: [N]
- Schemas: [N] | Pipelines: [N] | Pipeline Components: [N]
- Ports: [N] | Adapters: [N] | Messages: [N]

## Orchestration Inventory
| Orchestration | File | Receive Ports | Send Ports | Shapes | Maps Used |
|---------------|------|---------------|------------|--------|-----------|
| [name] | [file] | [port names] | [port names] | [count] | [map names] |

## Orchestration Call Graph
| Orchestration | Calls | Called By |
|---------------|-------|----------|
| [name] | [sub-orch names or —] | [caller names or —] |

## Map Complexity Assessment
| Map Name | Source Schema | Target Schema | Functoid Count | Complexity | Manual Review |
|----------|--------------|---------------|----------------|------------|---------------|
| [name] | [schema] | [schema] | [count] | Simple/Medium/Complex | [yes/no] |

## Pipeline Inventory
| Pipeline | Type | Stages | Components | Custom |
|----------|------|--------|------------|--------|
| [name] | Receive/Send | [stage list] | [component list] | [yes/no] |

## Port & Adapter Inventory
| Port | Direction | Adapter Type | Protocol | Orchestration |
|------|-----------|-------------|----------|---------------|
| [name] | Receive/Send | [adapter] | [protocol] | [orch name] |

## Migration Ordering
1. [sub-orchestration name] (leaf — no outbound calls)
2. [orchestration name] (depends on [sub-orch names])
...

## Structural Warnings
- [any complex maps, custom pipelines, proprietary adapters, orphaned orchestrations]

## Dependencies
| GroupId | ArtifactId | Version |
|---------|-----------|---------|
| [groupId] | [artifactId] | [version] |
```

Build the pre-populated analysis summary:

```
MIGRATION ANALYSIS SUMMARY
===========================================================
Vendor & Version:    Microsoft BizTalk Server [version from graph]
Source Product:      Microsoft BizTalk Server
Orchestrations:      [N] orchestration(s) detected
Maps:                [M] map(s) detected ([X] complex)
Pipelines:           [P] pipeline(s) detected
Failure Behaviour:   [inferred from error handler shapes]
Target Camel:        Camel version (default: latest)
Target Runtime:      [to be selected]
API Compatibility:   Assumed (same HTTP paths, queue names, contracts)
Project Layout:      [single or multi from graph]
Flows to migrate:    ALL ([N] orchestrations with migration ordering)
===========================================================
```

**Return to `migration-discovery.md` Step 5** for user confirmation, then Step 5a (concerns wizard), Step 5b (clarifications wizard), and Step 5c (proceed gate).

# Migration Graph Analysis Guide

> **Context:** Loaded by `migration-discovery.md` when `.camel-kit/project-graph.json` exists.
> **Purpose:** Replace manual artifact scanning with instant graph queries for accelerated analysis.
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

This returns JSON with the project's structural summary:
```json
{
  "nodes": 123,
  "edges": 456,
  "nodesByType": {
    "CLASS": 50,
    "METHOD": 200,
    "CAMEL_ROUTE": 10,
    "CAMEL_ENDPOINT": 30,
    ...
  }
}
```

Record:
- Total node and edge counts
- Number of classes, methods, routes, endpoints, processors
- Number of Maven artifacts and config properties

---

## Step 0.2 — Vendor & Version Detection

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "MAVEN_ARTIFACT", "--graph-file", GRAPH_FILE]
```

This returns JSON with all Maven artifacts. Scan for vendor signals:

| Signal | Detection |
|--------|-----------|
| groupId `org.apache.camel` | Apache Camel (community) |
| version containing `redhat-6` | Fuse 6.x |
| version containing `fuse-7` or `redhat-` on Camel 2.x/3.x | Fuse 7.x |
| artifactId `camel-blueprint` | Platform: ServiceMix/Karaf |
| artifactId `camel-spring-boot-starter` | Platform: Spring Boot |
| artifactId matching `camel-quarkus-*` | Platform: Quarkus |
| BOM `org.jboss.redhat-fuse:fuse-springboot-bom` | Fuse 7.x on Spring Boot |

Extract source Camel version from `camel-core` or `camel-bom` artifact.

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "CONFIG_PROPERTY", "--graph-file", GRAPH_FILE]
```

Check for additional platform signals:
- `camel.springboot.*` → Spring Boot
- `quarkus.camel.*` → Quarkus

Determine DSL formats by checking node sources:
- `.java` files → Java DSL
- `.xml` files → Spring XML or Blueprint XML
- `.yaml` files → YAML DSL

---

## Step 0.3 — Route Inventory

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "route-topology", "--graph-file", GRAPH_FILE]
```

This returns the complete route-to-route connection map.

For each route, bind the returned full node ID to `ROUTE_NODE_ID` and run:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", ROUTE_NODE_ID, "--direction", "out", "--graph-file", GRAPH_FILE]
```

List:
- From-endpoint (source)
- Processors in order
- To-endpoints (sinks)

Build the **Component Inventory**: extract all unique endpoint schemes with usage counts and which routes use them.

---

## Step 0.4 — Dependency & Config Inventory

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "MAVEN_ARTIFACT", "--graph-file", GRAPH_FILE]
```

Record groupId, artifactId, version for the full dependency tree.

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "CONFIG_PROPERTY", "--graph-file", GRAPH_FILE]
```

Record all `camel.*` properties with values and endpoint bindings.

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "CLASS", "--graph-file", GRAPH_FILE]
```

Identify custom Java classes — processors, beans, type converters, RouteBuilder subclasses.

---

## Step 0.5 — Structural Analysis

Using the route topology:

1. **Entry-point routes:** No inbound `direct:`/`seda:` links AND an external consumer endpoint
2. **Internal routes:** Consumed only via `direct:`/`seda:` from other routes
3. **Leaf routes:** No outbound `direct:`/`seda:` links to other routes

For each entry-point route, pass its validated `ROUTE_NODE_ID`:
```text
[*COMMAND_PREFIX_ARGV, "graph", "impact", ROUTE_NODE_ID, "--direction", "downstream", "--graph-file", GRAPH_FILE]
```

**Migration ordering** — reverse dependency order:
1. Leaf routes first (can be migrated independently)
2. Routes depending only on already-migrated routes
3. Entry-point routes last

**Structural warnings:**
- **Orphaned routes:** No inbound connections + `timer:`/`scheduler:` consumer (flag for user)
- **Broken references:** `direct:`/`seda:` endpoints produced but never consumed, or vice versa
- **Missing dependencies:** Routes using schemes with no corresponding Maven artifact

---

## Step 0.6 — Persist Snapshot & Build Summary

Write to `.camel-kit/project-snapshot.md`:

```markdown
# Project Snapshot

Generated: [timestamp]
Graph: .camel-kit/project-graph.json

## Project Overview
- Classes: [N] | Methods: [N] | Routes: [N] | Endpoints: [N]
- Maven Artifacts: [N] | Config Properties: [N]

## Vendor & Platform
- Vendor: [detected vendor and version]
- Product: [product or Community]
- Platform: [Spring Boot / Karaf / Quarkus / Plain Java]
- DSL: [Java DSL (N routes), XML (N routes), YAML (N routes)]

## Route Topology
| Route | From | To | Links To |
|-------|------|----|----------|
| [routeId] | [from-endpoint] | [to-endpoints] | [linked routes or —] |

## Component Inventory
| Scheme | Usage Count | Routes |
|--------|-------------|--------|
| [scheme] | [count] | [route list] |

## Migration Ordering
1. [routeId] (leaf — no outbound route links)
2. [routeId] (depends on [N])
...

## Structural Warnings
- [any orphaned routes, broken references, missing deps]

## Dependencies
| GroupId | ArtifactId | Version | Camel Component |
|---------|-----------|---------|-----------------|
| [groupId] | [artifactId] | [version] | [scheme or —] |

## Configuration
| Property | Value | Binds To |
|----------|-------|----------|
| [key] | [value] | [endpoint or component] |
```

Build the pre-populated analysis summary:

```
MIGRATION ANALYSIS SUMMARY
===========================================================
Vendor & Version:    [detected from graph]
Source Product:      [product or Community]
Failure Behaviour:   [inferred from error handler patterns]
Target Camel:        Camel version (default: latest)
Target Runtime:      [inferred from platform detection]
Compatibility Evidence: [Confirmed/Inferred/Unknown per interface; graph shape alone is insufficient]
Project Layout:      [single or multi from graph]
Routes to migrate:   ALL ([N] routes detected with migration ordering)
===========================================================
```

**Return to `migration-discovery.md` Step 5** for user confirmation, then Step 5a (concerns wizard), Step 5b (clarifications wizard), and Step 5c (proceed gate).

# Migration Graph Analysis Guide

> **Context:** Loaded by `migration-discovery.md` when `.camel-kit/project-graph.json` exists.
> **Purpose:** Use graph queries as acceleration and evidence for the mandatory bounded source scan.
> **Output:** `.camel-kit/project-snapshot.md` + a source-reconciled analysis summary for user confirmation.

This guide accepts `GRAPH_FILE`, the canonical source-bound path already validated by the caller. Apply
`shared/graph-availability.md`. `COMMAND_PREFIX_ARGV` below is its install-time fixed argv prefix (`["camel-kit"]` or
`["camel", "kit"]`), never a value parsed from project data. Every graph invocation below is an argv array and must end
with the discrete elements `"--graph-file"`, `GRAPH_FILE`; an absent or mismatched binding invalidates the result. If any
command fails, returns malformed output, is exposed to truncation, or conflicts with graph statistics, invalidate only
that graph result and record the gap as `? Unknown`. Continue the mandatory bounded source scan for that section; never
skip source inventory because graph evidence failed.

Before reusing any graph-returned ID as an argument, require a string of 1-256 characters matching
`[A-Za-z0-9][A-Za-z0-9._:/#@-]{0,255}`. Reject controls, a leading `-`, and every other nonconforming value as
`? Unknown`; pass a conforming ID unchanged as one discrete argv element, and never concatenate or evaluate it.

<HARD-RULE>
NEVER read `.camel-kit/project-graph.json` directly. Invoke the graph CLI only through the explicit argv arrays below.
The JSON file is thousands of lines and will overflow your context window.
</HARD-RULE>

Use only the install-time fixed command prefix from `shared/graph-availability.md`, never project configuration.

Graph results never replace source inspection. `graph find` and `graph impact` return at most 50 results and do not
prove that their result sets are complete. `graph route-topology` emits only routes with an outgoing route-to-route
connection, so it can omit isolated routes, externally consumed routes with no route-to-route output, and leaf routes.
Record these limits as coverage gaps, scan the complete selected source boundary, and reconcile every graph finding
against the source. A graph/source mismatch, failed query, or uninspected source area is `Unknown`; absence from graph
output is never evidence that an artifact is unused.

---

## Step 0.1 — Project Overview

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "stats", "--graph-file", GRAPH_FILE]
```

This returns JSON with the graph's structural summary:
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

Record these as graph counts and reconcile them with the source inventory:
- Total node and edge counts
- Number of classes, methods, routes, endpoints, processors
- Number of Maven artifacts and config properties

---

## Step 0.2 — Vendor & Version Detection

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "MAVEN_ARTIFACT", "--graph-file", GRAPH_FILE]
```

This returns JSON with up to 50 Maven-artifact nodes. Reconcile the results with build files in the bounded source scan
before using them as vendor evidence. Scan for vendor signals:

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

Determine DSL formats from the bounded source scan, using matching graph node sources only as corroboration:
- `.java` files → Java DSL
- `.xml` files → Spring XML or Blueprint XML
- `.yaml` files → YAML DSL

---

## Step 0.3 — Route Inventory

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "route-topology", "--graph-file", GRAPH_FILE]
```

This returns a partial route-to-route connection map. It omits routes without outgoing route-to-route connections.
Build the authoritative route inventory by scanning the selected source boundary, then use the graph result to
accelerate connection discovery and record any graph/source discrepancies.

Because route-topology displays route IDs rather than reusable full node IDs, obtain graph route nodes with:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "CAMEL_ROUTE", "--graph-file", GRAPH_FILE]
```

This query is capped at 50. For each returned full node ID that can be reconciled to one source-inventoried route,
validate it as described above, bind it to `ROUTE_NODE_ID`, and run:
```text
[*COMMAND_PREFIX_ARGV, "graph", "neighbors", ROUTE_NODE_ID, "--direction", "out", "--graph-file", GRAPH_FILE]
```

For every source-inventoried route, list:
- From-endpoint (source)
- Processors in order
- To-endpoints (sinks)

Build the **Component Inventory** from source, using graph evidence where it agrees: extract all unique endpoint schemes
with usage counts and which routes use them. Mark unresolved or unsupported endpoint expressions `Unknown`.

---

## Step 0.4 — Dependency & Config Inventory

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "MAVEN_ARTIFACT", "--graph-file", GRAPH_FILE]
```

The query is capped at 50. Reconcile it with the bounded source build files and record groupId, artifactId, and version
only for dependencies found in that combined evidence. Record incomplete dependency coverage as `Unknown`.

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "CONFIG_PROPERTY", "--graph-file", GRAPH_FILE]
```

The query is capped at 50. Reconcile it with bounded source configuration files. Record discovered `camel.*` properties
with values and endpoint bindings; never infer that no other properties exist from the query result.

Run the argv array:
```text
[*COMMAND_PREFIX_ARGV, "graph", "find", "--type", "CLASS", "--graph-file", GRAPH_FILE]
```

The query is capped at 50. Reconcile it with the bounded source scan to identify custom Java classes — processors,
beans, type converters, and RouteBuilder subclasses. Unsupported or unparsed code remains `Unknown`.

---

## Step 0.5 — Structural Analysis

Using the source inventory reconciled with route-topology evidence:

1. **Entry-root routes:** A structurally parsed external consumer endpoint, including scheduled consumers such as
   `timer:`, `quartz:`, and `scheduler:`
2. **Internal routes:** Reached through a constant `direct:` or `seda:` endpoint name from another source-inventoried
   route
3. **Leaf routes:** No supported constant outbound `direct:`/`seda:` reference to another route

For each graph-returned entry-root route, pass its validated `ROUTE_NODE_ID`:
```text
[*COMMAND_PREFIX_ARGV, "graph", "impact", ROUTE_NODE_ID, "--direction", "downstream", "--graph-file", GRAPH_FILE]
```

The impact query is also capped at 50. Treat its output as an ordering hint only. A route's position in the graph does
not prove that it can be migrated independently or that a safe traffic seam exists. State, ordering, correlation,
transactions, dynamic endpoints, custom dispatch, and callers outside the boundary require separate evidence.

**Candidate dependency ordering** — source-reconciled reverse dependency hints:
1. Leaf routes before their supported static callers
2. Routes whose supported static dependencies are already represented
3. Entry-root routes after supported static downstream dependencies

Do not turn these hints into deployment or cutover instructions without the later migration-strategy analysis.

Before assigning stable `SRC-###` IDs, reuse the mapping from an existing `migration-analysis.md` bound to the same
canonical source boundary, then reconcile a prior project snapshot only if it records that boundary. Artifact identity
is platform, type, relative source path, and structural identifier; reference identity is source path, structural
location, reference kind, and literal target. The analysis mapping wins any conflict; allocate the next unused ID only
for a new finding and record mapping conflicts as evidence gaps. Use exactly these classifications across audit findings:

- `Reachable` — a supported static path exists from a corroborated entry root.
- `Retirement candidate` — complete relevant supported source closure found no path from a corroborated entry root,
  including for a root-disconnected cycle; owner and runtime validation are still required.
- `Broken reference` — a supported constant `direct:`/`seda:` reference names a target whose absence is established by
  complete confirmed target-resolution closure. Missing or out-of-bound target sources make the reference `Unknown`.
- `Unknown` — source coverage is incomplete, graph/source evidence conflicts without resolution, parsing failed, the
  reference is dynamic or custom, or relevant evidence lies outside the selected boundary. A failed or capped optional
  graph query is an evidence gap but does not override a classification established by complete supported source closure.

Use `Reachable`, `Retirement candidate`, or `Unknown` for route findings. Record each `Broken reference` as a separate
reference finding, so a reachable route can coexist with a broken-reference row.

Traverse only constant `direct:` and `seda:` route-to-route references. Do not guess dynamic endpoint targets. Keep
every `Retirement candidate`, `Broken reference`, and `Unknown` item in migration scope until a specific explicit user
disposition says otherwise.

**Structural warnings:**
- **Retirement candidates:** Source-inventoried routes not reached from a corroborated external or scheduled entry root
  after complete supported source traversal. They are candidates, never proof that a route is dead or safe to remove.
- **Broken references:** Constant `direct:`/`seda:` producer references whose target consumer is confirmed absent after
  complete target-resolution closure; partial or out-of-bound target coverage remains `Unknown`.
- **Unknowns:** Dynamic endpoints, custom or reflective dispatch, unresolved beans or services, failed source parsing,
  unresolved graph/source mismatches, incomplete source coverage, and callers outside the selected boundary. Record
  failed or capped graph queries separately even when source closure establishes an artifact classification.
- **Missing dependencies:** Routes using schemes with no corresponding dependency in the reconciled evidence; incomplete
  build coverage makes this `Unknown`, not proof of absence.

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

## Evidence Coverage
- Canonical Source Boundary: [validated source root or archive boundary]
- Source Scan: [inspected files, parse failures, unsupported or excluded evidence]
- Graph Queries: [successful queries, 50-result-cap exposure, failures]
- Reconciliation: [graph/source matches and mismatches]

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
1. [routeId] (leaf under supported static source traversal)
2. [routeId] (depends on [N])
...

## Structural Warnings
- [source-reconciled retirement candidates, broken references, unknowns, missing dependencies, and coverage gaps]

## Source-Retirement Candidate Findings
| ID | Type | Route or Reference | Classification | Entry Root or Reference Path | Evidence | Required Validation |
|----|------|--------------------|----------------|------------------------------|----------|---------------------|
| SRC-001 | [route/reference] | [routeId or producer -> target] | [Reachable/Retirement candidate/Broken reference/Unknown] | [root/path/None found/Unknown] | [source + corroborating graph evidence] | [owner/runtime check/TBD] |

## Dependencies
| GroupId | ArtifactId | Version | Camel Component |
|---------|-----------|---------|-----------------|
| [groupId] | [artifactId] | [version] | [scheme or —] |

## Configuration
| Property | Value | Binds To |
|----------|-------|----------|
| [key] | [value] | [endpoint or component] |
```

Build the source-reconciled analysis summary. Counts come from the bounded source inventory, not from capped graph
results. Do not use `ALL` unless complete source coverage is explicitly demonstrated:

```
MIGRATION ANALYSIS SUMMARY
===========================================================
Vendor & Version:    [reconciled source and graph evidence]
Source Product:      [product or Community]
Failure Behaviour:   [inferred from error handler patterns]
Target Camel:        Camel version (default: latest)
Target Runtime:      [inferred from platform detection]
Compatibility Evidence: [Confirmed/Inferred/Unknown per interface; graph shape alone is insufficient]
Project Layout:      [single or multi from reconciled source and graph evidence]
Source Routes:       [N confirmed in scope; coverage gaps or Unknown]
Migration Scope:     [reachable routes plus retained SRC candidate/broken/unknown IDs]
===========================================================
```

Never summarize a graph-only absence as dead, safe to remove, independent, or out of scope. If source coverage is
incomplete and no candidate is found, state: `No candidates identified in covered artifacts; overall result
inconclusive.`

**Return to `migration-discovery.md` Step 5** for user confirmation, then Step 5a (concerns wizard), Step 5b (clarifications wizard), and Step 5c (proceed gate).

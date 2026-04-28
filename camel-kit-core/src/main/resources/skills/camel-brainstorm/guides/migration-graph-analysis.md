# Migration Graph Analysis Guide

> **Context:** Loaded by `migration-discovery.md` when `.camel-kit/project-graph.json` exists.
> **Purpose:** Replace manual artifact scanning with instant graph queries for accelerated analysis.
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

Run the command:
```bash
{COMMAND_PREFIX} graph find --type MAVEN_ARTIFACT
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

Run the command:
```bash
{COMMAND_PREFIX} graph find --type CONFIG_PROPERTY
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

Run the command:
```bash
{COMMAND_PREFIX} graph route-topology
```

This returns the complete route-to-route connection map.

For each route, run:
```bash
{COMMAND_PREFIX} graph neighbors route:<routeId> --direction out
```

List:
- From-endpoint (source)
- Processors in order
- To-endpoints (sinks)

Build the **Component Inventory**: extract all unique endpoint schemes with usage counts and which routes use them.

---

## Step 0.4 — Dependency & Config Inventory

Run the command:
```bash
{COMMAND_PREFIX} graph find --type MAVEN_ARTIFACT
```

Record groupId, artifactId, version for the full dependency tree.

Run the command:
```bash
{COMMAND_PREFIX} graph find --type CONFIG_PROPERTY
```

Record all `camel.*` properties with values and endpoint bindings.

Run the command:
```bash
{COMMAND_PREFIX} graph find --type CLASS
```

Identify custom Java classes — processors, beans, type converters, RouteBuilder subclasses.

---

## Step 0.5 — Structural Analysis

Using the route topology:

1. **Entry-point routes:** No inbound `direct:`/`seda:` links AND an external consumer endpoint
2. **Internal routes:** Consumed only via `direct:`/`seda:` from other routes
3. **Leaf routes:** No outbound `direct:`/`seda:` links to other routes

For each entry-point route, run:
```bash
{COMMAND_PREFIX} graph impact route:<routeId> --direction downstream
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
API Compatibility:   Assumed (same HTTP paths, queue names, contracts)
Project Layout:      [single or multi from graph]
Routes to migrate:   ALL ([N] routes detected with migration ordering)
===========================================================
```

**Return to `migration-discovery.md` Step 5** for user confirmation, then Step 5a (concerns wizard), Step 5b (clarifications wizard), and Step 5c (proceed gate).

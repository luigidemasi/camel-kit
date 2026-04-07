# Migration Graph Analysis Guide

> **Context:** Loaded by `migration-discovery.md` when `.camel-kit/project-graph.json` exists.
> **Purpose:** Replace manual artifact scanning with instant graph queries for accelerated analysis.
> **Output:** `.camel-kit/project-snapshot.md` + pre-populated analysis summary for user confirmation.

This guide uses MCP tools from the `camel-graph` server. If any tool call fails, fall back gracefully — skip that section and note it as `? Unknown` in the summary.

---

## Step 0.1 — Project Overview

Call `graph_stats` to get the project's structural summary.

Record:
- Total node and edge counts
- Number of classes, methods, routes, endpoints, processors
- Number of Maven artifacts and config properties

---

## Step 0.2 — Vendor & Version Detection

Call `graph_find(type="MAVEN_ARTIFACT")` and scan artifacts for vendor signals:

| Signal | Detection |
|--------|-----------|
| groupId `org.apache.camel` | Apache Camel (community) |
| version containing `redhat-6` | Fuse 6.x |
| version containing `fuse-7` or `redhat-` on Camel 2.x/3.x | Fuse 7.x |
| version containing `redhat-` on Camel 3.x/4.x | Red Hat Build of Apache Camel |
| artifactId `camel-blueprint` | Platform: ServiceMix/Karaf |
| artifactId `camel-spring-boot-starter` | Platform: Spring Boot |
| artifactId matching `camel-quarkus-*` | Platform: Quarkus |
| BOM `org.jboss.redhat-fuse:fuse-springboot-bom` | Fuse 7.x on Spring Boot |
| BOM `com.redhat.camel.springboot:camel-spring-boot-bom` | Red Hat Build for Spring Boot |

Extract source Camel version from `camel-core` or `camel-bom` artifact.

Call `graph_find(type="CONFIG_PROPERTY")` for additional platform signals:
- `camel.springboot.*` → Spring Boot
- `quarkus.camel.*` → Quarkus

Determine DSL formats by checking node sources:
- `.java` files → Java DSL
- `.xml` files → Spring XML or Blueprint XML
- `.yaml` files → YAML DSL

---

## Step 0.3 — Route Inventory

Call `graph_route_topology` for the complete route-to-route connection map.

For each route, call `graph_neighbors(routeId, "out")` to list:
- From-endpoint (source)
- Processors in order
- To-endpoints (sinks)

Build the **Component Inventory**: extract all unique endpoint schemes with usage counts and which routes use them.

---

## Step 0.4 — Dependency & Config Inventory

Call `graph_find(type="MAVEN_ARTIFACT")` for the full dependency tree. Record groupId, artifactId, version.

Call `graph_find(type="CONFIG_PROPERTY")` for all `camel.*` properties with values and endpoint bindings.

Call `graph_find(type="CLASS")` to identify custom Java classes — processors, beans, type converters, RouteBuilder subclasses.

---

## Step 0.5 — Structural Analysis

Using the route topology:

1. **Entry-point routes:** No inbound `direct:`/`seda:` links AND an external consumer endpoint
2. **Internal routes:** Consumed only via `direct:`/`seda:` from other routes
3. **Leaf routes:** No outbound `direct:`/`seda:` links to other routes

Call `graph_impact(routeId, "downstream")` on each entry-point route.

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
- Product: [Red Hat product or Community]
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
Source Product:      [Red Hat product or Community]
Failure Behaviour:   [inferred from error handler patterns]
Target Camel:        Red Hat supported version (default: latest)
Target Runtime:      [inferred from platform detection]
API Compatibility:   Assumed (same HTTP paths, queue names, contracts)
Project Layout:      [single or multi from graph]
Routes to migrate:   ALL ([N] routes detected with migration ordering)
===========================================================
```

**Return to `migration-discovery.md` Step 5** for user confirmation, then Step 5a (concerns wizard), Step 5b (clarifications wizard), and Step 5c (proceed gate).

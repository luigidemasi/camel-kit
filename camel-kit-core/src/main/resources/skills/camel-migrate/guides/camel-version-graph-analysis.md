# Camel Version Migration — Phase 0: Graph-Based Pre-Analysis

> **Context:** Loaded by `camel-migrate` SKILL.md Step 0 when `.camel-kit/project-graph.json` exists.
> **Purpose:** Replace Steps 1-4 (file scanning, vendor detection, analysis summary) with instant graph queries.
> **Output:** `.camel-kit/project-snapshot.md` + pre-populated analysis summary for Step 5 confirmation.

This guide uses MCP tools from the `camel-graph` server. If any tool call fails, fall back gracefully — skip that section and note it as `? Unknown` in the summary.

---

## Step 0.1 — Project Overview

Call `graph_stats` to get the project's structural summary.

Record:
- Total node and edge counts
- Number of classes, methods, routes, endpoints, processors
- Number of Maven artifacts and config properties

This gives an immediate picture of project size and complexity.

---

## Step 0.2 — Vendor & Version Detection

Call `graph_find(type="MAVEN_ARTIFACT")` and scan the returned artifacts for vendor signals:

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

Extract the source Camel version from the `camel-core` or `camel-bom` artifact version.

Call `graph_find(type="CONFIG_PROPERTY")` and scan for additional platform signals:
- `camel.springboot.*` → Spring Boot
- `quarkus.camel.*` → Quarkus

Determine DSL formats by checking node sources:
- Routes from `.java` files → Java DSL
- Routes from `.xml` files → Spring XML or Blueprint XML
- Routes from `.yaml` files → YAML DSL
- Routes from `.groovy` files → Groovy DSL

---

## Step 0.3 — Route Inventory

Call `graph_route_topology` to get the complete route-to-route connection map.

For each route in the topology, call `graph_neighbors(routeId, "out")` to list:
- From-endpoint (source)
- Processors in order
- To-endpoints (sinks)

Build the **Component Inventory**: extract all unique endpoint schemes from the results (kafka, jdbc, direct, seda, http, etc.) with usage counts and which routes use them.

---

## Step 0.4 — Dependency & Config Inventory

Call `graph_find(type="MAVEN_ARTIFACT")` to list the full dependency tree. Record groupId, artifactId, and version for each.

Call `graph_find(type="CONFIG_PROPERTY")` to list all `camel.*` configuration properties with their values and which endpoints they bind to.

Call `graph_find(type="CLASS")` to identify custom Java classes — processors, beans, type converters, RouteBuilder subclasses.

---

## Step 0.5 — Structural Analysis

Using the route topology from Step 0.3:

1. **Entry-point routes:** Routes with no inbound `direct:`/`seda:` links AND an external consumer endpoint (kafka, http, timer, file, etc.)
2. **Internal routes:** Routes consumed only via `direct:`/`seda:` from other routes
3. **Leaf routes:** Routes with no outbound `direct:`/`seda:` links to other routes

Call `graph_impact(routeId, "downstream")` on each entry-point route to determine the full downstream chain.

**Migration ordering** — process routes in reverse dependency order:
1. Leaf routes first (no outbound route links — can be migrated independently)
2. Then routes that depend only on already-migrated routes
3. Entry-point routes last (they depend on everything downstream)

**Structural warnings:**
- **Orphaned routes:** Routes with no inbound connections AND a `timer:`/`scheduler:` consumer (may be intentional cleanup jobs — flag for user confirmation)
- **Broken references:** `direct:`/`seda:` endpoints that are produced but never consumed, or consumed but never produced
- **Missing dependencies:** Routes using component schemes with no corresponding Maven artifact

---

## Step 0.6 — Persist Snapshot & Build Summary

Write the structural snapshot to `.camel-kit/project-snapshot.md` with this format:

```
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
- [any orphaned routes, broken references, or missing deps]

## Dependencies
| GroupId | ArtifactId | Version | Camel Component |
|---------|-----------|---------|-----------------|
| [groupId] | [artifactId] | [version] | [scheme or —] |

## Configuration
| Property | Value | Binds To |
|----------|-------|----------|
| [key] | [value] | [endpoint or component] |
```

Build the **pre-populated analysis summary** using the same format as SKILL.md Step 4:

```
MIGRATION ANALYSIS SUMMARY
══════════════════════════════════════════════════════
Vendor & Version:    [✓/~] [detected from graph]
Source Product:      [✓/~] [Red Hat product or Community]
Business Purpose:    [? ] Unknown (not in graph — ask user)
Owning Team:         [? ] Unknown (not in graph — ask user)
SLA / Performance:   [? ] Unknown (not in graph — ask user)
Compliance:          [? ] Unknown (not in graph — ask user)
Failure Behaviour:   [~ ] [inferred from error handler patterns in routes]
Target Camel:        [✓ ] Red Hat supported version (default: latest)
Target Runtime:      [~ ] [inferred from platform detection]
API Compatibility:   ✓ Assumed (same HTTP paths, queue names, contracts)
Project Layout:      [✓ ] [single or multi from graph]
Flows to migrate:    [✓ ] [N] routes detected with migration ordering
══════════════════════════════════════════════════════
```

Fields that cannot be determined from the graph (Business Purpose, Owning Team, SLA, Compliance) are marked `?` — the user provides them in Step 5.

**Return to SKILL.md Step 5** for user confirmation.

---
name: camel-migrate
description: Use when the user wants to migrate an existing integration from MuleSoft, Fuse, or older Camel versions to Red Hat Build of Apache Camel 4.x
user_invocable: true
---

# Camel Migrate — Migration Entry Point

Shortcut into `camel-brainstorm` with project type pre-set to **migration**.

Invoke `camel-brainstorm/SKILL.md` with:
- Project type: **migration** (skip detection question)
- Load `camel-brainstorm/guides/migration-discovery.md` directly after context loading

## Reference Guides

This skill also serves as a reference for migration domain knowledge. Pipeline skills load these guides during migration tasks:

| Guide | Purpose |
|-------|---------|
| `guides/mulesoft-phase1.md` | MuleSoft Business Analyst analysis |
| `guides/mulesoft-phase2.md` | MuleSoft Technical Design |
| `guides/mule-component-mapping.md` | Mule → Camel component map |
| `guides/mule-dataweave-conversion.md` | DataWeave → XSLT strategies |
| `guides/datamapper-migrate.md` | DataMapper XSLT migration |
| `guides/camel-version-phase1.md` | Camel version analysis |
| `guides/camel-version-phase2.md` | Camel version TDD generation |
| `guides/camel-version-graph-analysis.md` | Graph-based pre-analysis |
| `guides/camel2-component-mapping.md` | Camel 2.x → 4.x components |
| `guides/camel2-dataformat-mapping.md` | Camel 2.x → 4.x dataformats |
| `guides/camel2-eip-mapping.md` | Camel 2.x → 4.x EIPs |
| `guides/camel2-language-mapping.md` | Camel 2.x → 4.x languages |
| `guides/camel2-platform-changes.md` | Platform migration guide |

---
name: camel-migrate-camel2
description: Migrate Apache Camel 2.x/3.x integrations to Camel 4.x YAML DSL
user-invocable: false
metadata:
  version: "2.0.0"
  author: "camel-kit"
  category: "migration"
  license: "Apache-2.0"
---

# Camel 2.x/3.x → 4.x Version Migration

You are an orchestrator that migrates Apache Camel 2.x or 3.x integrations to Camel 4.x YAML DSL. This sub-skill is invoked ONLY by `camel-migrate` — never directly by the user.

## Input Context (from `camel-migrate`)

You receive:
- Confirmed analysis summary (with markers)
- Full list of source artifact paths
- Detected Camel source version (2.x or 3.x) and platform type
- `CAMEL_VERSION`, `RUNTIME`, `PLATFORM_BOM` from `.camel-kit/config.yaml`

## Output Contract

Identical to `/camel-project` + `/camel-flow` — fully compatible with `/camel-implement`:
- `docs/business-requirements.md` (BRD)
- `docs/flows/{flow-name}/{flow-name}.tdd.md` (one TDD per route)
- `docs/constitution.md` (copy from template if missing)

---

## Guide Manifest

Dispatch sub-agents for each phase sequentially.

| Step | Guide | Shared Guide | ~Tokens | When |
|------|-------|-------------|---------|------|
| A | skills/camel-migrate/guides/camel-version-phase1.md | skills/camel-migrate/guides/camel2-component-mapping.md | 2.5K | Always |
| A | skills/camel-migrate/guides/camel-version-phase1.md | skills/camel-migrate/guides/camel2-eip-mapping.md | 0.8K | Always |
| A | skills/camel-migrate/guides/camel-version-phase1.md | skills/camel-migrate/guides/camel2-platform-changes.md | 1.7K | Karaf/Blueprint |
| B | skills/camel-migrate/guides/camel-version-phase2.md | skills/camel-migrate/guides/camel2-component-mapping.md | 3.8K | Always |
| B | skills/camel-migrate/guides/camel-version-phase2.md | skills/camel-migrate/guides/camel2-dataformat-mapping.md | 0.7K | Always |
| B | skills/camel-migrate/guides/camel-version-phase2.md | skills/camel-migrate/guides/camel2-language-mapping.md | 0.7K | Always |

### Context Passing

Include in each sub-agent prompt:
- Confirmed analysis summary
- Source artifact paths
- Camel source version and platform type
- `CAMEL_VERSION`, `RUNTIME`, `PLATFORM_BOM`

### Execution

1. Dispatch Phase 1 (Step A) — produces BRD
2. Dispatch Phase 2 (Step B) — produces TDD files

Both phases MUST run. Do NOT stop after Phase 1.

---
name: camel-start
description: Route to the right camel-kit skill for integration work.
user_invocable: true
---

# Camel Start — Skill Router

**Announce:** "Let me figure out the right camel-kit skill for your request."

This project uses camel-kit skills for all integration work. Use this decision tree to find the right one.

## Decision Tree

Answer these questions in order. The first "yes" is your destination.

| # | Question | If Yes | Slash Command |
|---|----------|--------|---------------|
| 1 | Is there existing code to migrate from MuleSoft, BizTalk, Fuse, or Camel 2.x/3.x? | Migration pipeline | `/camel-migrate` |
| 2 | Is there an approved design spec ready for task decomposition? | Plan it | `/camel-plan` |
| 3 | Is there an approved implementation plan ready for execution? | Execute it | `/camel-execute` |
| 4 | Are there generated routes that need quality validation? | Validate them | `/camel-validate` |
| 5 | Is something broken — build fails, startup errors, runtime exceptions — outside of a pipeline run? | Debug it | `/camel-debug` |
| 6 | None of the above — new integration, new feature, or unclear? | Design it | `/camel-brainstorm` |

### Mid-Pipeline Entry

Users may arrive partway through a pipeline:
- "I already have a design spec" → skip brainstorm, go to `/camel-plan`
- "The plan is approved, start building" → skip brainstorm+plan, go to `/camel-execute`
- "I have generated routes that need checking" → go to `/camel-validate`
- "It was working but now it's broken" → go to `/camel-debug` (standalone troubleshooting)

### Pipeline Overview

**Pipeline 1 — Greenfield / New Feature:**
```text
/camel-brainstorm → /camel-plan → /camel-execute → /camel-validate
```

**Pipeline 2 — Migration:**
```text
/camel-migrate → /camel-plan → /camel-execute → /camel-validate
```

Both pipelines share plan → execute → validate. Only the entry point differs.

Runtime verification (`camel-verify`) runs automatically as part of `/camel-execute` — it is not a standalone pipeline stage.

### Skill Tiers

| Tier | Skills | Role |
|------|--------|------|
| Meta | `camel-start` | Routes to the right pipeline |
| Tier 1 (Pipeline) | `camel-brainstorm`, `camel-migrate`, `camel-plan`, `camel-execute`, `camel-validate` | The 5 pipeline steps |
| Tier 2 (Power) | `camel-ship`, `camel-knowledge`, `camel-debug` | Standalone utilities |
| Internal | `camel-design`, `camel-implement`, `camel-test`, `camel-verify` | Guide libraries / subagent-only |

Tier 1 skills are the main pipeline stages — invoke them via the decision tree above. Tier 2 skills are standalone utilities available anytime. Internal skills are not user-invocable; they are dispatched as subagents by pipeline skills.

## When NOT to Use Each Skill

| Skill | Do NOT use for |
|-------|---------------|
| `/camel-brainstorm` | Quick property changes, version bumps, single-component additions, Camel questions, migrations from other platforms |
| `/camel-migrate` | Greenfield projects, Camel 4.x minor version upgrades, no source artifacts to analyze |
| `/camel-plan` | Ad-hoc changes, single-file edits, quick fixes, no approved design spec yet |
| `/camel-execute` | No approved plan, Camel questions, validation-only tasks, design work |
| `/camel-validate` | Runtime debugging (build failures, startup errors), generating routes, design work, fixing issues |
| `/camel-ship` | Single-file changes, quick fixes, exploratory work, only one pipeline stage needed |
| `/camel-debug` | Build/test failures during pipeline execution (use `/camel-execute` which dispatches `camel-verify`), quality validation, designing integrations |
| `/camel-knowledge` | Implementation tasks, code generation, route validation, design work |

## Also Available (Tier 2)

These standalone utilities are not part of the main pipelines but are accessible via slash command. They can be invoked at any point without affecting the active pipeline state of Tier 1 skills.

| Slash Command | Purpose |
|---|---|
| `/camel-ship` | Run the full pipeline autonomously with configurable oversight (`--ask always\|smart\|never`) |
| `/camel-knowledge` | Look up Apache Camel documentation, components, CVEs/security advisories, versions |
| `/camel-debug` | Ad-hoc troubleshooting for broken routes outside of a pipeline run |

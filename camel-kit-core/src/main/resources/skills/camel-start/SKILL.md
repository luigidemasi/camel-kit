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
| 5 | None of the above — new integration, new feature, or unclear? | Design it | `/camel-brainstorm` |

### Mid-Pipeline Entry

Users may arrive partway through a pipeline:
- "I already have a design spec" → skip brainstorm, go to `/camel-plan`
- "The plan is approved, start building" → skip brainstorm+plan, go to `/camel-execute`
- "I have generated routes that need checking" → go to `/camel-validate`

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

## When NOT to Use Each Skill

| Skill | Do NOT use for |
|-------|---------------|
| `/camel-brainstorm` | Quick property changes, version bumps, single-component additions, Camel questions |
| `/camel-migrate` | Greenfield projects, Camel 4.x minor version upgrades |
| `/camel-plan` | Ad-hoc changes, single-file edits, no design spec yet |
| `/camel-execute` | No approved plan, questions, validation-only tasks |
| `/camel-validate` | Runtime debugging (build failures, startup errors) — use `/camel-execute` which includes runtime verification |

## Also Available

These skills are not part of the main pipelines but are accessible via slash command:

| Slash Command | Purpose |
|---|---|
| `/camel-ship` | Run the full pipeline autonomously with configurable oversight (`--ask always\|smart\|never`) |
| `/camel-knowledge` | Look up Apache Camel documentation, components, CVEs, errata, versions |

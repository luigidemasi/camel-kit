---
name: executor
description: Executes a ready Camel-Kit implementation plan and coordinates bounded leaves.
mode: primary
permission:
  edit: allow
  bash:
    "*": allow
  task:
    "*": deny
    implementer: allow
    migrator: allow
    planner: allow
    researcher: allow
    reviewer: allow
    tester: allow
steps: 100
---

Read .opencode/skills/camel-execute/SKILL.md and follow those instructions exactly.

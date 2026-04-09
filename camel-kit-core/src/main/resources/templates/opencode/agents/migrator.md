---
name: migrator
mode: subagent
edit: allow
bash:
  "*": allow
  "rm -rf *": deny
task: deny
steps: 50
---

Read .opencode/skills/camel-migrate/SKILL.md and follow those instructions exactly.

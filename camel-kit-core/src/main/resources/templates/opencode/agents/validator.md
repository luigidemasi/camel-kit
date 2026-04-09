---
name: validator
mode: subagent
edit: deny
bash:
  "*": ask
  "mvn *": allow
  "camel-kit *": allow
task: deny
steps: 20
---

Read .opencode/skills/camel-validate/SKILL.md and follow those instructions exactly.

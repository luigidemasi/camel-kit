---
name: tester
description: Generates or updates tests for a bounded Camel task.
mode: subagent
permission:
  edit:
    "*": ask
    "src/test/**": allow
    "test/**": allow
  bash:
    "*": allow
    "rm -rf *": deny
  task: deny
steps: 40
---

Read .opencode/skills/camel-test/SKILL.md and follow those instructions exactly.

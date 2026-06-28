# Ship Mode Rules

- Keep pipeline orchestration in the parent Bob task.
- Use `switch_mode` for phase-level tool restrictions.
- Invoke Camel-Kit skills for stage behavior; do not duplicate the skill workflow in the mode rule.
- Use `spawn_subagent` only inside execute, validate, debug, or other stages whose shared skill calls for isolated work.
- Preserve oversight decisions from the shared `camel-ship` skill.

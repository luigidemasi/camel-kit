## Agent Optimization: IBM Bob 2

Use Bob 2 modes and skills together:

- Use `switch_mode` for phase-level tool restrictions when moving between brainstorm, plan, execute, and validate.
- Use `use_skill` for stage behavior instead of duplicating the shared skill instructions in the parent prompt.
- Use `spawn_subagent` inside execute, validate, and debug when the shared skill calls for isolated work.
- Keep pipeline state, oversight decisions, and stage transitions in the parent Bob task.
- Subagents must not spawn subagents.

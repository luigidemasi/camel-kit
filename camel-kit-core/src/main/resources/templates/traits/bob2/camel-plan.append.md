## Agent Optimization: IBM Bob 2

Switch to `camel-plan-mode` for planning.

- Use `spawn_subagent` with `name: "explore"` only for factual source search, dependency inventory, and migration artifact summaries.
- Keep implementation planning in the parent Bob task so task IDs, dependencies, and wave metadata stay coherent.
- Run `camel-kit plan analyze` after the plan exists to validate wave grouping for `/camel-execute`.

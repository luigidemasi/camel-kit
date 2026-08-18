## Agent Optimization: IBM Bob 2

Use Bob 2 Plan mode or the `camel-plan` custom mode for planning.

- Use `spawn_subagent` with `name: "explore"` for isolated codebase research, dependency discovery, and migration artifact summaries.
- Keep implementation planning in the parent Bob task so task IDs, dependencies, and wave metadata stay coherent.
- Run `camel-kit plan analyze` after the plan exists to validate wave grouping for `/camel-execute`.

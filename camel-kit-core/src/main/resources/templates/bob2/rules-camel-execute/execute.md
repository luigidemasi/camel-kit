# Execute Mode Rules

- The parent Bob task is the orchestrator for `/camel-execute`.
- Use `spawn_subagent` with `name: "explore"` for read-only discovery, spec review, quality review, and route analysis.
- Use `spawn_subagent` with `name: "general"` for implementation, test generation, and fix tasks.
- Spawn all independent tasks in the same wave in one turn so Bob runs them in parallel.
- Set `fork_context: true` only when the subagent needs prior decisions from the parent conversation.
- Subagents must not spawn subagents.

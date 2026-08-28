# Execute Mode Rules

- The parent Bob task is the orchestrator for `/camel-execute`.
- The parent coordinates separate fresh subagent calls for ACR Moderator Phase 1, selected critics, and Moderator Phase 2;
  each Moderator call is phase-limited because subagents cannot spawn subagents.
- Use `spawn_subagent` with `name: "explore"` only for factual source search, inventory, and discovery.
- Use `spawn_subagent` with `name: "camel-reviewer"` for the Catalog Researcher, phase-specific Moderator calls,
  every ACR critic lane, spec review, and code-quality review. Its read-and-MCP tool groups enforce non-mutation.
- Use `spawn_subagent` with `name: "camel-worker"` for implementation, test generation, fix, and verification tasks.
- Load the applicable full role from `.bob/personas/` and include it in every worker or reviewer prompt.
- Spawn all independent tasks in the same wave in one turn so Bob runs them in parallel.
- Set `fork_context: true` only when the subagent needs prior decisions from the parent conversation.
- Subagents must not spawn subagents.

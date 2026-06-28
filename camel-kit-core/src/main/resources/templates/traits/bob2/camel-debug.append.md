## Agent Optimization: IBM Bob 2

Use Bob 2 subagents to keep noisy diagnosis out of the parent context:

- `spawn_subagent` with `name: "explore"` for route analysis, MCP verification, and log classification.
- `spawn_subagent` with `name: "general"` only for approved fix tasks that need edit or execute access.
- Use `fork_context: true` only when the subagent needs earlier troubleshooting decisions.
- Subagents return summaries; the parent Bob task decides the diagnosis and fix sequence.

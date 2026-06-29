## Dispatch

For each computational step in the Guide Manifest, use Bob 2 native subagents when the step is self-contained and only a summary needs to return.

- Use `spawn_subagent` with `name: "explore"` for read-only research, route inspection, spec review, quality review, MCP verification summaries, and migration source discovery.
- Use `spawn_subagent` with `name: "general"` for implementation, test generation, build/test fixing, and other tasks that need edit or execute access.
- Include `fork_context: true` only when the subagent needs prior parent-conversation decisions, constraints, or user preferences. Keep it omitted for clean-context tasks.
- Multiple `spawn_subagent` calls made in one parent turn run in parallel. Spawn every independent task in the current wave in the same turn.
- The parent Bob task remains the orchestrator. Subagents must complete their focused task and return a summary; subagents must not spawn subagents.

Include in each subagent description:
- The flow/task name and task ID
- Camel version from `.camel-kit/config.properties`
- Relevant user decisions and design spec paths
- Required skill and guide paths
- Exact output paths and verification commands

### Fallback

If subagents are unavailable in the active Bob client, execute the work inline with the shared guides. This uses more context but preserves the workflow.

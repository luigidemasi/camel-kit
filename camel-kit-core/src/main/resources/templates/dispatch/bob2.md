## Dispatch

For each computational step in the Guide Manifest, use Bob 2 native subagents when the step is self-contained and only a summary needs to return.

- Use `spawn_subagent` with `name: "explore"` only for factual source search, inventory, and discovery. Its built-in
  raw prompt is not a review persona and has read tools only.
- Use `spawn_subagent` with `name: "camel-reviewer"` for the Catalog Researcher, Knowledge Researcher, ACR Moderator
  and critic phases, spec and quality review, validation reasoning, and other read-only judgment. This generated preset exposes only
  read and MCP groups, so mutation and command execution are unavailable.
- From orchestration modes that allow it, use `spawn_subagent` with `name: "camel-worker"` for implementation, test
  generation, fixes, verification, and other tasks that require edit or execute access. Restricted implement and test
  modes perform mutations inline; the test mode's path-scoped edit restriction remains enforced. Never switch modes
  or dispatch a broader worker to bypass the active parent-mode restrictions.
- Include `fork_context: true` only when the subagent needs prior parent-conversation decisions, constraints, or user preferences. Keep it omitted for clean-context tasks.
- Multiple `spawn_subagent` calls made in one parent turn run in parallel. Spawn every independent task in the current wave in the same turn.
- The parent Bob task remains the orchestrator. Subagents must complete their focused task and return a summary; subagents must not spawn subagents.

Include in each subagent description:
- The flow/task name and task ID
- The full applicable role text loaded from `.bob/personas/<role>.md`
- Camel version from `.camel-kit/config.properties`
- Relevant user decisions and design spec paths
- Required skill and guide paths
- Exact output paths and verification commands

### Fallback

If subagents are unavailable in the active Bob client, execute the work inline with the shared guides. This uses more context but preserves the workflow.

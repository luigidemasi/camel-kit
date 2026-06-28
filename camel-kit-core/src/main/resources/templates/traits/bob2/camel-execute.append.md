## Agent Optimization: IBM Bob 2

Use Bob 2 native subagents for execution while the parent Bob task remains the orchestrator.

1. Run `camel-kit plan analyze` on the approved implementation plan to identify waves.
2. For read-only discovery and review tasks, call `spawn_subagent` with `name: "explore"`.
3. For implementation, test generation, and fix tasks, call `spawn_subagent` with `name: "general"`.
4. Spawn every independent task in the current wave in the same parent turn so Bob runs them in parallel.
5. Use `fork_context: true` only when a subagent needs prior parent-conversation decisions; otherwise keep the subagent context clean.
6. Pass concise task context: task text, TDD path, design spec path, Camel version source, output paths, required guide paths, and verification commands.
7. Subagents must not spawn subagents. If a subagent reports that more isolated work is needed, the parent Bob task decides whether to spawn another subagent.
8. After each implementation subagent returns, the parent dispatches spec compliance and quality reviews as separate `explore` subagents before accepting the task.

If Bob refuses a spawn because the active mode disallows it, switch to `camel-execute` or execute that task inline with the shared guides.

## Agent Optimization: IBM Bob 2

Switch to `camel-execute-mode` before orchestrating the plan.

Use Bob 2 native subagents for execution while the parent Bob task remains the orchestrator.

1. Run `camel-kit plan analyze` on the ready implementation plan to identify waves.
2. Before implementation, load the full `.bob/personas/catalog-researcher.md` role and dispatch it as a fresh
   `camel-reviewer` subagent so its four MCP tools are available while edit and execute tools remain unavailable.
3. Use `spawn_subagent` with `name: "explore"` only for factual source search, inventory, and discovery, never for
   analysis, recommendations, or verdicts.
4. For implementation, test generation, fix, and verification tasks, load the task-selected role from
   `.bob/personas/` and call `spawn_subagent` with `name: "camel-worker"`, including that full role text.
5. Spawn every independent task in the current wave in the same parent turn so Bob runs them in parallel.
6. Use `fork_context: true` only when a subagent needs prior parent-conversation decisions; otherwise keep the subagent context clean.
7. Pass concise task context: task text, design spec section, Camel version source, output paths, required guide paths, and verification commands.
8. Subagents must not spawn subagents. If a subagent reports that more isolated work is needed, the parent Bob task decides whether to spawn another subagent.
9. After each implementation subagent returns, load `.bob/personas/acr-moderator.md` and dispatch a fresh
   `camel-reviewer` with its full text for Phase 1 only: select lanes and return critic prompts, but do not dispatch.
10. The parent loads the selected `.bob/personas/critic-*.md` files and spawns every critic lane together as fresh
    `camel-reviewer` subagents, including one full persona per prompt. The always-on Route Architecture critic can use
    its required MCP catalog calls, while every lane remains tool-enforced read-only.
11. Dispatch a new fresh `camel-reviewer` with the full `.bob/personas/acr-moderator.md` text for Phase 2 only,
    supplying all critic reports for synthesis and prohibiting further dispatch.
12. Load `.bob/personas/spec-compliance-reviewer.md` and dispatch its full role as a fresh `camel-reviewer`.
13. Only after spec compliance passes, load `.bob/personas/code-quality-reviewer.md` and dispatch its full role as a
    fresh `camel-reviewer`; mandatory MCP spot checks remain available while mutation and commands remain unavailable.

If Bob refuses a spawn because the active mode disallows it, switch to `camel-execute-mode` or execute that task inline with the shared guides.

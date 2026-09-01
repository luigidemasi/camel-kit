## Agent Optimization: IBM Bob 2

Switch to `camel-debug-mode` before starting the diagnostic workflow.

Use Bob 2 subagents to keep noisy diagnosis out of the parent context:

- `spawn_subagent` with `name: "camel-reviewer"` for route analysis, MCP verification, and log classification.
- `spawn_subagent` with `name: "camel-worker"` only for approved fix tasks that need edit or execute access.
- Never use `fork_context`. Start each subagent with clean context and pass only validated troubleshooting decisions and
  bounded diagnostic payloads through separate canonical JSON-string envelopes.
- Subagents return summaries; the parent Bob task decides the diagnosis and fix sequence.

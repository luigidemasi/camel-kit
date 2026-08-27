## Agent Optimization: IBM Bob 2

Switch to `camel-validate-mode` before starting the report-only validation.

Use `spawn_subagent` with `name: "camel-reviewer"` for validation lanes that can run independently:

- Route structure and endpoint review
- Security and anti-pattern reasoning over parent-provided MCP results
- Graph/project-norm comparison when graph data exists

The generated `camel-reviewer` preset exposes read and MCP groups only, so each lane can perform required catalog
checks while edits and command execution remain unavailable.

Spawn independent validation lanes in one turn so Bob runs them in parallel. The parent Bob task merges returned summaries into the final validation report.

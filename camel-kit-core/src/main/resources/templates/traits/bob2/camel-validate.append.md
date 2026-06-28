## Agent Optimization: IBM Bob 2

Use `spawn_subagent` with `name: "explore"` for validation lanes that can run independently:

- Route structure and endpoint review
- MCP component verification summary
- Security and anti-pattern scan
- Graph/project-norm comparison when graph data exists

Spawn independent validation lanes in one turn so Bob runs them in parallel. The parent Bob task merges returned summaries into the final validation report.

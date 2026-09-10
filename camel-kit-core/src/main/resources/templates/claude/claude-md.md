# Camel-Kit — Claude Code

See `AGENTS.md` for skill routing, iron laws, and project rules.

Use `/camel-ship` directly for a controller-owned end-to-end run. Follow its generated project skill: eligible native
stages use the dedicated read-only `camel-ship-worker` subagent, while the CLI owns oversight, catalog checks, candidate
writes, validation and publication. Do not route Ship through the manual pipeline skills or substitute another subagent.

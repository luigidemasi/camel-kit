# Camel-Kit Project

Start integration work with `$camel-start`. Use `/skills` to inspect or select any Camel-Kit skill explicitly.

## Laws (NEVER violate)

1. Verify all Camel components, EIPs, and data formats through the configured MCP servers before use.
2. Read and follow `docs/constitution.md`.
3. Do not implement without a user-approved design specification.
4. Read the Camel version only from `.camel-kit/config.properties`.
5. Run the application after implementation and report the verification evidence.

## Codex project resources

- Skills: `.agents/skills/`
- Custom agents: `.codex/agents/`
- MCP configuration: `.codex/config.toml`
- MCP status: `/mcp`

Codex loads project `.codex/` configuration and any project hooks only after the repository is trusted. Camel-Kit
does not generate hooks. Keep the active sandbox and approval policy in place; request the narrowest approval that
lets blocked work continue.

## CLI

Use `{COMMAND_PREFIX}` for Camel-Kit CLI commands (for example, `{COMMAND_PREFIX} graph stats`).

# Camel Kit Project

This repository is initialized for Pi.

For integration work, trust the project first, then invoke the entry skill:

```text
/trust
/skill:camel-start
```

`/skill:camel-start` routes to the correct Camel Kit workflow skill for greenfield design, migration, planning,
execution, validation, or debugging. Use the `camel-knowledge` skill directly for Camel documentation questions.

## Pi Assets

- Project skills live in `.pi/skills/`.
- Prompt templates live in `.pi/prompts/`.
- MCP servers are configured in `.mcp.json` through `pi-mcp-adapter`.
- Safety guardrails are configured by `.pi/extensions/camel-kit-guard.ts` and `.pi/camel-kit-guard-policy.json`.

Install the MCP adapter with:

```text
pi install npm:pi-mcp-adapter@{PI_MCP_ADAPTER_VERSION}
```

The tested Pi baseline is `{PI_VERSION}`. Headless checks must approve project trust explicitly, for example:

```text
pi -a -p "/skill:camel-start"
```

## Laws

1. Verify all Camel components, EIPs, data formats, and endpoint options through MCP before use.
2. Read and follow `docs/constitution.md`.
3. Do not implement until the design spec is user-approved and a task-based implementation plan exists.
4. Read Camel version values only from `.camel-kit/config.properties`.
5. Run the generated application or equivalent verification loop after implementation.

## CLI

Use `{COMMAND_PREFIX}` for Camel Kit commands, for example `{COMMAND_PREFIX} graph stats`.

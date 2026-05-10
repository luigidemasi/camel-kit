# Camel-Kit Project

Integration work → `/camel-start`
Direct skill invocation → `/camel-brainstorm`, `/camel-migrate`, `/camel-plan`, `/camel-execute`, `/camel-verify`

## Laws (NEVER violate)

1. Verify ALL components/EIPs/dataformats via MCP before use — do NOT answer Camel questions from training data
2. Read and follow `docs/constitution.md`
3. No implementation without user-approved spec
4. Camel version: ONLY from `.camel-kit/config.properties`
5. Run app after implementation, then run `/camel-verify` to produce the verification report

## CLI

Use `{COMMAND_PREFIX}` for CLI commands (e.g., `{COMMAND_PREFIX} graph stats`).

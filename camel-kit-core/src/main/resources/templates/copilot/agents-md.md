# Camel Kit Project

This repository is initialized for GitHub Copilot CLI.

Use `.github/copilot-instructions.md` as the primary instructions file. For integration work, start with the `/camel-start` project skill, then follow the routed Camel Kit skill. Direct skill names include `/camel-brainstorm`, `/camel-migrate`, `/camel-plan`, `/camel-execute`, `/camel-validate`, `/camel-ship`, `/camel-knowledge`, and `/camel-debug`.

## Laws

1. Verify all Camel components, EIPs, data formats, and endpoint options through MCP before use.
2. Read and follow `docs/constitution.md`.
3. Do not implement until the design spec is user-approved and a task-based plan exists.
4. Read Camel version values only from `.camel-kit/config.properties`.
5. Run the generated application or equivalent verification loop after implementation.

## CLI

Use `{COMMAND_PREFIX}` for Camel Kit commands, for example `{COMMAND_PREFIX} graph stats`.

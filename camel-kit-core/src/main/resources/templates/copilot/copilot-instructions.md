# Camel Kit Instructions for GitHub Copilot CLI

This repository is initialized for GitHub Copilot CLI with Camel Kit.

## Entry Point

For integration work, use the `/camel-start` project skill first. It routes to the correct Camel Kit skill:

- `camel-brainstorm` for new integration design.
- `camel-migrate` for MuleSoft, BizTalk, Fuse, Camel 2.x, or Camel 3.x migrations.
- `camel-plan` for approved design specs that need implementation plans.
- `camel-execute` for approved plans that need implementation and verification.
- `camel-validate` for route quality, correctness, security, and anti-pattern checks.
- `camel-debug` for ad-hoc build, startup, or runtime failures.
- `camel-knowledge` for Apache Camel documentation, component, CVE, errata, and version questions.

Use `/skills list` if you need to inspect project skills.

## Native Copilot Assets

- Project skills live in `.github/skills/`.
- Custom agents live in `.github/agents/`.
- MCP servers are configured in `.github/mcp.json`.
- Safety hooks are configured in `.github/hooks/`.

## Laws

1. Verify all Camel components, EIPs, data formats, and endpoint options through MCP before using them.
2. Read and follow `docs/constitution.md`.
3. Do not implement without a user-approved design spec or implementation plan.
4. Read Camel versions only from `.camel-kit/config.properties`.
5. After implementation, run the generated application or an equivalent verification loop and write the required report.

## CLI

Use `{COMMAND_PREFIX}` for Camel Kit CLI commands, for example `{COMMAND_PREFIX} graph stats`.

## MCP

Use the configured MCP servers before relying on model memory:

- `camel` for Camel catalog, route validation, hardening, and error diagnosis.
- `camel-knowledge` for Camel documentation, CVEs, release notes, errata, and issue lookup.
- `citrus` for Citrus action, endpoint, schema, and documentation lookups during test generation.

If Copilot CLI does not show the workspace servers immediately, first ensure this repository folder is trusted, then run `/mcp show` or `copilot mcp list --json`. If needed, run `/mcp reload` and continue.

## Safety

- Do not use `--allow-all`, `--allow-all-tools`, `--allow-all-paths`, `--allow-all-urls`, or `--yolo` unless the workspace is isolated in a disposable sandbox.
- Do not run `git push` from an automated Copilot session.
- Do not read secret files such as `.env`, `.npmrc`, private keys, or cloud credential files unless the user explicitly asks.
- Review shell commands that can delete, chmod, upload, exfiltrate, or alter repository history.

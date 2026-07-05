# ADR 0001: Add GitHub Copilot CLI as a Native Agent Target

## Status

Accepted

## Context

Camel-Kit supports multiple AI coding assistants by generating agent-specific instruction files, command references, skill directories, and MCP configuration. GitHub Copilot CLI has its own native repository customization surfaces:

- `.github/copilot-instructions.md`
- `.github/skills/`
- `.github/agents/*.agent.md`
- `.github/mcp.json`
- `.github/hooks/*.json`

Copilot CLI also supports permission prompts, allow/deny rules, local and cloud sandboxes, custom agents, project skills, and MCP servers. Its MCP schema uses `mcpServers` with per-server `tools` lists, not the `autoApprove` and `alwaysAllow` arrays used by several other agent targets.

## Decision

Camel-Kit adds `--ai copilot` as a first-class target.

The generated workspace uses GitHub-native locations:

- `.github/copilot-instructions.md` for project instructions.
- `.github/skills/` for copied Camel-Kit skills.
- `.github/agents/` for Copilot custom agents.
- `.github/mcp.json` for workspace MCP configuration.
- `.github/hooks/camel-kit-safety.json` for a conservative shell safety hook.

Camel-Kit still generates `.github/commands/` command reference files to keep the shared workflow manifest and doctor validation coherent, but Copilot users are instructed to start with the `camel-start` project skill rather than Camel-Kit slash commands.

Internal guide skills copied only for custom-agent use are annotated with Copilot-readable `user-invocable: false` and `disable-model-invocation: true` metadata. This prevents direct user or model selection of internal implementation, test, design, and verification guides while keeping those files available to generated custom agents.

## Consequences

- Copilot users get native skill and custom-agent discovery without a separate plugin install.
- The generated MCP config follows Copilot's documented `tools` schema.
- `camel-kit doctor` must validate both the legacy `autoApprove`/`alwaysAllow` schema and Copilot's `tools` schema. Copilot wildcard `tools` values are accepted with a least-privilege warning.
- User documentation must distinguish slash-command agents from Copilot's project-skill workflow.
- Workspace hooks are intentionally narrow so they add guardrails without replacing Copilot's own permission system.

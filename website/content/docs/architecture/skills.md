---
title: Skills Architecture
weight: 1
---

## What Are Skills

Camel Kit uses [Anthropic Agent Skills](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview) to provide structured workflows for designing, implementing, and validating Apache Camel integrations.

- **Command skills** (like `camel-flow`, `camel-implement`) are user-invocable slash commands that guide the AI agent through interactive workflows
- **Workflow skills** (like `camel-migrate-mule`) are internal skills loaded by commands to handle specific tasks
- **Shared skills** (like `datamapper-canonicalize`) provide reusable logic used across multiple workflows

Component-specific knowledge (documentation, configuration options, URI syntax) is **not bundled as skills**. Instead, it is fetched on-demand from the [Camel MCP server]({{< relref "mcp" >}}) at runtime. This avoids bloating the AI agent's context window with 396+ component catalogs and ensures documentation always matches the target Camel version.

## Skill Structure

Skills are stored in `camel-kit-core/src/main/resources/skills/` and copied to the AI agent's skills folder during `camel-kit init`:

```
{agent-folder}/skills/
├── camel-project/SKILL.md         # Define integration landscape
├── camel-flow/                    # Design a flow
│   ├── SKILL.md
│   └── guides/                    # Sub-guides loaded on-demand
├── camel-implement/               # Generate YAML DSL
│   ├── SKILL.md
│   └── guides/
├── camel-validate/                # Validate routes
│   ├── SKILL.md
│   └── guides/
├── camel-test/SKILL.md            # Generate tests
├── camel-migrate/SKILL.md         # Migration orchestrator
├── camel-migrate-mule/            # MuleSoft migration sub-skill
│   ├── SKILL.md
│   └── guides/
└── shared/                        # Shared utilities
    └── datamapper-canonicalize.md
```

Where `{agent-folder}` is:
- `.bob/skills/` for IBM Project Bob
- `.gemini/skills/` for Gemini CLI
- `.claude/skills/` for Claude Code

### SKILL.md Format

```markdown
---
name: camel-flow
description: Design a Camel integration flow
user-invocable: true
---

# /camel-flow

> Design an integration flow's requirements and technical design.

## Workflow
1. Step one...
2. Step two...

## MCP Integration
- `camel_catalog_components` — search components by category
- `camel_catalog_component_doc` — get component documentation
```

## How Component Knowledge Works

Instead of bundling 396+ component skill files, Camel-Kit relies on the **MCP server** for all component-specific knowledge:

| Need | MCP Tool | Token Cost |
|------|----------|------------|
| Search components by category | `camel_catalog_components` | ~100 tokens |
| Get component documentation | `camel_catalog_component_doc` | ~200 tokens |
| Get data format options | `camel_catalog_dataformat_doc` | ~200 tokens |
| Get EIP documentation | `camel_catalog_eip_doc` | ~200 tokens |
| Validate endpoint URIs | `camel_validate_route` | ~100 tokens |

**Example flow:**
1. User runs `/camel-flow order-processing`
2. User mentions "Kafka" and "SQL"
3. AI agent calls `camel_catalog_component_doc("kafka")` via MCP
4. AI agent calls `camel_catalog_component_doc("sql")` via MCP
5. Context contains only what's needed (~400 tokens), not all 396 component catalogs

## How Commands Use Skills

### /camel-flow
When user describes an integration:
1. AI agent reads `camel-flow/SKILL.md` for the workflow
2. Calls MCP `camel_catalog_components` to search for matching components
3. Calls MCP `camel_catalog_component_doc` to get component details
4. Loads sub-guides only when relevant
5. Produces a TDD spec file

### /camel-implement
When generating YAML:
1. AI agent reads `camel-implement/SKILL.md` for the workflow
2. Reads the TDD spec from `/camel-flow`
3. Calls MCP `camel_catalog_component_doc` for each component's options
4. Generates YAML DSL with correct syntax and parameters
5. Calls MCP `camel_validate_route` to validate the generated route

## Extending

### Adding a New Command Skill

1. Create a skill directory in `camel-kit-core/src/main/resources/skills/`:

```
skills/camel-yourcommand/
├── SKILL.md
└── guides/          # Optional sub-guides
```

2. Follow the SKILL.md format above
3. Add MCP tool usage instructions for component lookups
4. Register the command in `InitCommand.createCommandTemplates()`
5. Update `docs/commands.md`

### Adding a New Migration Vendor

1. Create a vendor sub-skill: `skills/camel-migrate-{vendor}/SKILL.md`
2. Add vendor-specific guides in `skills/camel-migrate-{vendor}/guides/`
3. Update the orchestrator (`skills/camel-migrate/SKILL.md`) with detection rules
4. Follow the two-phase pattern from `camel-migrate-mule/`

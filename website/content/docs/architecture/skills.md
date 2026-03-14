---
title: Skills Architecture
weight: 1
---

## What Are Skills

Camel Kit uses [Anthropic Agent Skills](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview) to provide component-specific documentation and configuration guidance. Instead of loading all 396 Camel component catalogs into context, skills are loaded on-demand only when needed.

- **Commands** (like `/camel-flow`, `/camel-implement`) ask interactive questions about the integration
- **Skills** (one per Camel component, 396 total) provide component knowledge: description, capabilities, usage patterns, URI syntax, parameters, Maven dependencies, and reference to the complete JSON schema

Skills are `user-invocable: false` — they are only loaded by commands when specific components are selected.

## Skill Structure

Each skill directory contains:

```
{agent-folder}/skills/camel-component-{name}/
├── SKILL.md          # Component documentation with YAML frontmatter
└── schema.json       # Complete component schema from Camel catalog
```

Where `{agent-folder}` is:
- `.bob/skills/` for IBM Project Bob
- `.gemini/skills/` for Gemini CLI
- `.claude/skills/` for Claude Code
- `.github/prompts/` for GitHub Copilot (file format: `.prompt.md`)
- `.cursor/commands/` for Cursor (file format: `.md`)

### SKILL.md Format

```markdown
---
name: camel-component-{name}
description: {component description}
user-invocable: false
---

# {Component Title}

{Component description}

## Component Information
- Scheme: {scheme}
- Syntax: {syntax}
- Type: Consumer/Producer/Both

## Usage Patterns
### Consumer (from)
- from:
    uri: {scheme}:name
    parameters:
      # See schema.json

## Configuration Reference
See [schema.json](schema.json) for complete options.

## Maven Dependency
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>{artifactId}</artifactId>
</dependency>
```

## Pre-Generated Skills

All 396 Camel component skills are **pre-generated** and bundled in the `camel-kit-core` module:

**Source location**: `camel-kit-core/src/main/resources/skills/`

During `camel-kit init`, skills are copied to the appropriate AI agent folder:
- `camel-kit init --ai bob` copies to `.bob/skills/`
- `camel-kit init --ai gemini` copies to `.gemini/skills/`
- `camel-kit init --ai claude` copies to `.claude/skills/`
- `camel-kit init --ai copilot` copies to `.github/prompts/`
- `camel-kit init --ai cursor` copies to `.cursor/commands/`

**Why pre-generated?**

1. **Zero setup** — Users get skills immediately during init
2. **Version locked** — Skills match the Camel version used by camel-kit
3. **Offline ready** — No need to download Camel catalog or clone repositories
4. **Consistent** — All users have identical component knowledge
5. **Single source** — One set of skills for all AI agents

## Progressive Disclosure

Skills use a three-level progressive disclosure model:

| Level | Content | Token Cost |
|-------|---------|------------|
| **Level 1 — Metadata** | YAML frontmatter with name and description | ~100 tokens |
| **Level 2 — Instructions** | SKILL.md content when skill is loaded | <5k tokens |
| **Level 3 — Resources** | schema.json loaded only when needed | Zero context cost |

**Example flow:**
1. User runs `/camel-flow order-processing`
2. Command asks questions, user mentions "Kafka" and "SQL"
3. Command loads ONLY the kafka and sql skills (2 of 396)
4. Context stays small, focused on relevant components

## How Commands Use Skills

### /camel-flow
When user selects a component:
1. Ask: "For [source], I suggest using: Component: kafka"
2. Reference: "For detailed documentation, see: .claude/skills/camel-component-kafka/SKILL.md"
3. Load skill: Read SKILL.md for usage patterns and capabilities

### /camel-implement
When generating YAML:
1. Identify components from flow design
2. Load skills: `.claude/skills/camel-component-{name}/SKILL.md`
3. Read schemas: `.claude/skills/camel-component-{name}/schema.json`
4. Generate YAML with correct syntax and parameters
5. Validate against Camel YAML DSL schema

## Examples

### Kafka Component

**Skill path**: `{agent-folder}/skills/camel-component-kafka/SKILL.md`

```yaml
name: camel-component-kafka
description: Send and receive messages to/from an Apache Kafka broker.
user-invocable: false
```

**Usage Pattern:**
```yaml
- from:
    uri: kafka:topic
    parameters:
      brokers: "{{kafka.brokers}}"
      groupId: "{{kafka.groupId}}"
```

### SQL Component

**Skill path**: `{agent-folder}/skills/camel-component-sql/SKILL.md`

```yaml
name: camel-component-sql
description: Perform SQL queries using JDBC.
user-invocable: false
```

**Usage Pattern:**
```yaml
- to:
    uri: "sql:INSERT INTO orders VALUES (:#id, :#amount)"
    parameters:
      dataSource: "#dataSource"
```

## Extending: Adding a New Skill

1. Create a skill directory in `camel-kit-core/src/main/resources/skills/`:

```
skills/camel-component-{name}/
├── SKILL.md
└── schema.json
```

2. Follow the SKILL.md format above
3. Add MCP tool usage instructions if applicable
4. Register the skill in the appropriate command templates

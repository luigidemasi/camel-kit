# Skills-Based Architecture

This document explains Camel Kit's skills-based architecture for providing component-specific knowledge to AI agents.

## Overview

Camel Kit uses [Anthropic Agent Skills](https://code.claude.com/docs/en/skills) to provide component-specific documentation and configuration guidance. Instead of loading all 396 Camel component catalogs into context, skills are loaded on-demand only when needed.

## Architecture

### Commands (Interactive Q&A)
Commands like `/camel-flow` and `/camel-implement` ask interactive questions about the integration:
- What is the goal of this flow?
- Where does data come from?
- What business rules apply?
- Which Camel components to use?

### Skills (Component Knowledge)
One skill per Camel component (396 total) provides:
- Component description and capabilities
- Usage patterns (consumer/producer)
- URI syntax and parameters
- Maven dependencies
- Reference to complete JSON schema

**Skills are `user-invocable: false`** - they are only loaded by commands when specific components are selected.

### Progressive Disclosure
1. User runs `/camel-flow order-processing`
2. Command asks questions, user mentions "Kafka" and "SQL"
3. Command loads ONLY the kafka and sql skills (2 of 396)
4. Context stays small, focused on relevant components

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
```yaml
- from:
    uri: {scheme}:name
    parameters:
      # See schema.json
```

### Producer (to)
```yaml
- to:
    uri: {scheme}:name
    parameters:
      # See schema.json
```

## Configuration Reference
See [schema.json](schema.json) for complete options.

## Maven Dependency
```xml
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>{artifactId}</artifactId>
</dependency>
```
```

## Pre-Generated Skills

All 396 Camel component skills are **pre-generated** and bundled in the `camel-kit-core` module:

**Source location**: `camel-kit-core/src/main/resources/skills/`

During `camel-kit init`, skills are copied to the appropriate AI agent folder:
- `camel-kit init --ai bob` → copies to `.bob/skills/`
- `camel-kit init --ai gemini` → copies to `.gemini/skills/`
- `camel-kit init --ai claude` → copies to `.claude/skills/`

Each skill is generated from the official Apache Camel component catalog and contains:
- SKILL.md with YAML frontmatter and component documentation
- schema.json with complete component configuration from Camel catalog

### Why Pre-Generated?

Skills are bundled in the JAR (not generated at runtime) because:
1. **Zero setup** - Users get skills immediately during init
2. **Version locked** - Skills match the Camel version used by camel-kit
3. **Offline ready** - No need to download Camel catalog or clone repositories
4. **Consistent** - All users have identical component knowledge
5. **Single source** - One set of skills for all AI agents

### Source Data

Skills were generated from Apache Camel catalog:
- **Component JSON**: `/path/to/camel/catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/components/{component}.json`
- Each component's metadata, parameters, and configuration options

## MCP Integration: Complementing Skills Architecture

Camel-Kit combines **bundled component skills** with the **Apache Camel MCP (Model Context Protocol) Server** for optimal efficiency:

### Skills vs MCP

| Approach | When Used | Token Cost | Best For |
|----------|-----------|------------|----------|
| **Bundled Skills** | Implementation phase | ~10k tokens (2-5 components) | Complete component schemas and detailed configuration |
| **MCP Server** | Flow design, validation, real-time queries | ~100 tokens per query | Component search, URI validation, security analysis |

### How They Work Together

**During Flow Design (`/camel-flow`)**:
1. MCP tool `camel_catalog_components` searches by category → **100 tokens**
2. User selects kafka component
3. MCP tool `camel_catalog_component_doc` retrieves basic info → **200 tokens**
4. No full skills loaded yet → **Total: 300 tokens**

**During Implementation (`/camel-implement`)**:
1. Load bundled kafka skill → **5k tokens** (full schema and patterns)
2. Generate YAML with complete configuration
3. MCP tool `camel_route_context` analyzes result → **100 tokens**
4. MCP tool `camel_validate_route` validates URIs → **100 tokens**
5. **Total: ~5.2k tokens** (vs 500k without skills/MCP)

**During Validation (`/camel-validate`)**:
1. MCP tool `camel_route_harden_context` runs 47 security checks → **300 tokens**
2. No skills loaded → **Total: 300 tokens**

### Combined Benefits

**Token Efficiency**:
- **Without skills or MCP**: ~500k tokens (all 396 component catalogs)
- **With skills only**: ~10k tokens (2-5 components loaded)
- **With skills + MCP**: ~6k tokens (skills for implementation, MCP for queries)
- **Overall savings**: 99% reduction in context usage

**Real-Time Capabilities**:
- **Skills**: Static bundled documentation (matches camel-kit version)
- **MCP**: Dynamic queries against live Camel catalog (matches your project version)
- **Combined**: Best of both worlds

**Use Case Optimization**:
- **Component search** → MCP (lightweight queries)
- **Implementation** → Skills (complete schemas)
- **Validation** → MCP (real-time checks)
- **Security analysis** → MCP (47 automated checks)

### MCP Server Configuration

During `camel-kit init`, MCP configurations are created:
- **Claude Code**: `.mcp.json`
- **IBM Bob**: `.bob/mcp.json`
- **Gemini CLI**: `.gemini/mcp.json`

AI assistants automatically use MCP tools when available. See [MCP Tools Reference](mcp-tools-reference.md) for complete documentation.

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

## Benefits

### Context Efficiency
- **Without skills or MCP**: Load all 396 components = ~500k tokens
- **With skills only**: Load only 2-5 components = ~10k tokens
- **With skills + MCP**: ~6k tokens (skills for implementation, MCP for queries)
- **Savings**: 99% reduction in context usage

### Real-Time Validation
- **MCP tools** provide instant validation without Maven
- **47 automated security checks** via `camel_route_harden_context`
- **URI validation** catches typos and configuration errors before runtime
- **Route analysis** extracts components and EIPs from generated code

### Accuracy
- Skills contain exact syntax, parameters, and types from Camel catalog
- MCP provides always-current documentation for your exact Camel version
- No hallucination of component options or URIs
- Schema validation ensures correctness

### Maintainability
- Skills are auto-generated from Camel catalog
- MCP server updates with Camel version in project
- Update by re-running SkillGenerator with new Camel version
- Commands reference skills, no hardcoded component knowledge

## Claude Code Skill Standard

Camel Kit follows the [Claude Code Skill Standard](https://code.claude.com/docs/en/skills):

### YAML Frontmatter
```yaml
---
name: camel-component-{name}
description: {one-line description}
user-invocable: false  # Only loaded by commands
---
```

### Three-Level Progressive Disclosure
1. **Level 1 - Metadata** (~100 tokens): YAML frontmatter with name and description
2. **Level 2 - Instructions** (<5k tokens): SKILL.md content when skill is loaded
3. **Level 3 - Resources** (zero context cost): schema.json loaded only when needed

## Examples

### Example 1: Kafka Component
**Skill path**: `{agent-folder}/skills/camel-component-kafka/SKILL.md`

**Metadata**:
```yaml
name: camel-component-kafka
description: Send and receive messages to/from an Apache Kafka broker.
user-invocable: false
```

**Usage Pattern**:
```yaml
- from:
    uri: kafka:topic
    parameters:
      brokers: "{{kafka.brokers}}"
      groupId: "{{kafka.groupId}}"
```

**Maven**:
```xml
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-kafka</artifactId>
</dependency>
```

### Example 2: SQL Component
**Skill path**: `{agent-folder}/skills/camel-component-sql/SKILL.md`

**Metadata**:
```yaml
name: camel-component-sql
description: Perform SQL queries using JDBC.
user-invocable: false
```

**Usage Pattern**:
```yaml
- to:
    uri: "sql:INSERT INTO orders VALUES (:#id, :#amount)"
    parameters:
      dataSource: "#dataSource"
```

**Maven**:
```xml
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-sql</artifactId>
</dependency>
```

## Future Enhancements

### Component Documentation Integration
Enhance skills with additional content from Camel AsciiDoc documentation:
- Code examples
- Common use cases
- Configuration best practices

### Skill Categories
Organize skills by category for easier discovery:
- Messaging (Kafka, JMS, AMQP)
- Database (SQL, JPA, MongoDB)
- Cloud (AWS, Azure, GCP)
- API (REST, GraphQL, gRPC)

### Skill Recommendations
Add command intelligence to suggest components:
- "For message queues, consider: kafka, jms, or amqp"
- Load relevant skills for comparison

## References

- [Anthropic Agent Skills](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview)
- [Claude Code Skills](https://code.claude.com/docs/en/skills)
- [Apache Camel Components](https://camel.apache.org/components/latest/)
- [Camel Component Catalog](https://camel.apache.org/manual/component-dsl.html)

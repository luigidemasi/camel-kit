# Camel-Kit Architecture Guide

This document describes Camel-Kit's internal architecture for contributors and extenders. For user-facing documentation, see the [User Guide](user-guide.md) and [Command Reference](commands.md).

## Table of Contents

- [Overview](#overview)
- [Skills Architecture](#skills-architecture)
  - [What Are Skills](#what-are-skills)
  - [Skill Structure](#skill-structure)
  - [Pre-Generated Skills](#pre-generated-skills)
  - [Progressive Disclosure](#progressive-disclosure)
  - [How Commands Use Skills](#how-commands-use-skills)
  - [Claude Code Skill Standard](#claude-code-skill-standard)
  - [Examples](#examples)
- [MCP Integration (Internal Details)](#mcp-integration-internal-details)
  - [Configuration](#configuration)
  - [Available MCP Tools](#available-mcp-tools)
  - [Tool Usage by Skill](#tool-usage-by-skill)
  - [Detailed Tool Reference](#detailed-tool-reference)
  - [Tool Invocation Flow](#tool-invocation-flow)
  - [Token Savings Statistics](#token-savings-statistics)
- [Skills + MCP: How They Work Together](#skills--mcp-how-they-work-together)
- [Extending Camel-Kit](#extending-camel-kit)
  - [Adding a New Skill](#adding-a-new-skill)
  - [Future Enhancements](#future-enhancements)
- [References](#references)

---

## Overview

Camel-Kit combines two mechanisms to give AI agents accurate, efficient access to the Apache Camel ecosystem:

1. **Skills** — bundled component documentation loaded on-demand during implementation (complete schemas, usage patterns)
2. **MCP Server** — real-time queries against the live Camel catalog during design and validation (lightweight, always current)

Together they achieve **99% reduction** in context usage compared to loading all 396 component catalogs.

---

## Skills Architecture

### What Are Skills

Camel Kit uses [Anthropic Agent Skills](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview) to provide component-specific documentation and configuration guidance. Instead of loading all 396 Camel component catalogs into context, skills are loaded on-demand only when needed.

- **Commands** (like `/camel-flow`, `/camel-implement`) ask interactive questions about the integration
- **Skills** (one per Camel component, 396 total) provide component knowledge: description, capabilities, usage patterns, URI syntax, parameters, Maven dependencies, and reference to the complete JSON schema

Skills are `user-invocable: false` — they are only loaded by commands when specific components are selected.

### Skill Structure

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

#### SKILL.md Format

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

### Producer (to)
- to:
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

### Pre-Generated Skills

All 396 Camel component skills are **pre-generated** and bundled in the `camel-kit-core` module:

**Source location**: `camel-kit-core/src/main/resources/skills/`

During `camel-kit init`, skills are copied to the appropriate AI agent folder:
- `camel-kit init --ai bob` → copies to `.bob/skills/`
- `camel-kit init --ai gemini` → copies to `.gemini/skills/`
- `camel-kit init --ai claude` → copies to `.claude/skills/`

Each skill is generated from the official Apache Camel component catalog and contains:
- SKILL.md with YAML frontmatter and component documentation
- schema.json with complete component configuration from Camel catalog

**Why pre-generated?**

1. **Zero setup** — Users get skills immediately during init
2. **Version locked** — Skills match the Camel version used by camel-kit
3. **Offline ready** — No need to download Camel catalog or clone repositories
4. **Consistent** — All users have identical component knowledge
5. **Single source** — One set of skills for all AI agents

Skills were generated from Apache Camel catalog component JSON files (`/path/to/camel/catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/components/{component}.json`).

### Progressive Disclosure

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

### How Commands Use Skills

#### /camel-flow
When user selects a component:
1. Ask: "For [source], I suggest using: Component: kafka"
2. Reference: "For detailed documentation, see: .claude/skills/camel-component-kafka/SKILL.md"
3. Load skill: Read SKILL.md for usage patterns and capabilities

#### /camel-implement
When generating YAML:
1. Identify components from flow design
2. Load skills: `.claude/skills/camel-component-{name}/SKILL.md`
3. Read schemas: `.claude/skills/camel-component-{name}/schema.json`
4. Generate YAML with correct syntax and parameters
5. Validate against Camel YAML DSL schema

### Claude Code Skill Standard

Camel Kit follows the [Claude Code Skill Standard](https://code.claude.com/docs/en/skills):

```yaml
---
name: camel-component-{name}
description: {one-line description}
user-invocable: false  # Only loaded by commands
---
```

### Examples

#### Kafka Component

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

#### SQL Component

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

---

## MCP Integration (Internal Details)

For the user-facing overview of MCP (what it does, benefits, how to configure it), see [User Guide — MCP Integration](user-guide.md#mcp-integration). This section documents the internal tool details for contributors.

### Configuration

The MCP server is configured in project-specific configuration files created during `camel-kit init`:

| AI Assistant | Config File |
|-------------|-------------|
| Claude Code | `.mcp.json` |
| IBM Bob | `.bob/mcp.json` |
| Gemini CLI | `.gemini/mcp.json` |

**Content:**
```json
{
  "mcpServers": {
    "camel": {
      "command": "jbang",
      "args": [
        "-Dquarkus.log.level=WARN",
        "org.apache.camel:camel-jbang-mcp:4.18.0:runner"
      ],
      "description": "Apache Camel MCP Server - Component catalog, validation, and security analysis"
    }
  }
}
```

**Created by:** `camel-kit-core/src/main/java/io/github/luigidemasi/camelkit/command/InitCommand.java` — `createMcpConfigs()` (Lines 320-376)

### Available MCP Tools

The Camel MCP server (camel-jbang-mcp:4.18.0) provides **15 tools** organized into 6 categories:

#### 1. Catalog Exploration (8 tools)

| Tool Name | Purpose |
|-----------|---------|
| `camel_catalog_components` | List Camel components with filtering by name, label, runtime |
| `camel_catalog_component_doc` | Get comprehensive component documentation |
| `camel_catalog_dataformats` | List data formats (JSON, XML, CSV, etc.) |
| `camel_catalog_dataformat_doc` | Get data format configuration options |
| `camel_catalog_languages` | List expression languages (Simple, JsonPath, XPath, JQ) |
| `camel_catalog_language_doc` | Get expression language documentation |
| `camel_catalog_eips` | List Enterprise Integration Patterns |
| `camel_catalog_eip_doc` | Get EIP documentation and configuration |

#### 2. Kamelet Catalog (2 tools)

| Tool Name | Purpose |
|-----------|---------|
| `camel_catalog_kamelets` | List available Kamelets with filtering |
| `camel_catalog_kamelet_doc` | Get Kamelet documentation and dependencies |

#### 3. Route Understanding (1 tool)

| Tool Name | Purpose |
|-----------|---------|
| `camel_route_context` | Extract components and EIPs from route (YAML/XML/Java) |

#### 4. Security Analysis (1 tool)

| Tool Name | Purpose |
|-----------|---------|
| `camel_route_harden_context` | Analyze routes for security concerns (47 checks) |

#### 5. Validation and Transformation (2 tools)

| Tool Name | Purpose |
|-----------|---------|
| `camel_validate_route` | Validate endpoint URIs against catalog schema |
| `camel_transform_route` | Convert routes between YAML and XML formats |

#### 6. Version Management (1 tool)

| Tool Name | Purpose |
|-----------|---------|
| `camel_version_list` | List Camel versions with LTS status and JDK requirements |

### Tool Usage by Skill

| Skill | Tools Used | Count |
|-------|------------|-------|
| **camel-project** | `camel_version_list` | 1 |
| **camel-flow** | `camel_catalog_components`, `camel_catalog_component_doc`, `camel_catalog_dataformats`, `camel_catalog_dataformat_doc`, `camel_catalog_eips`, `camel_catalog_eip_doc`, `camel_catalog_languages`, `camel_catalog_language_doc` | 8 |
| **camel-migrate** | Same as camel-flow (Phase 2) | 8 |
| **camel-implement** | `camel_catalog_component_doc`, `camel_catalog_dataformat_doc`, `camel_catalog_eip_doc`, `camel_catalog_language_doc`, `camel_route_context`, `camel_validate_route` | 6 |
| **camel-validate** | `camel_validate_route`, `camel_route_harden_context` | 2 |
| **camel-test** | `camel_route_context`, `camel_catalog_component_doc` | 2 |

### Detailed Tool Reference

#### camel_version_list

List available Camel versions with release dates, JDK requirements, and LTS status.

**Used in:** camel-project (SKILL.md Lines 183, 209)

**Parameters:**
```json
{}                          // Returns all versions
{ "version": "4.18.0" }    // Details for specific version
```

**Example output:**
```
Recent Versions:
  4.18.0 (LTS) - Released 2025-01-15 - JDK 17+ - Recommended
  4.17.0       - Released 2024-12-10 - JDK 17+
  4.16.0 (LTS) - Released 2024-11-05 - JDK 17+
```

**Fallback:** If MCP not available, suggests default version 4.18.0

---

#### camel_catalog_components

Search and list Camel components by category, name, or label.

**Used in:** camel-flow (SKILL.md Lines 169, 300)

**Parameters:**
```json
{
  "category": "messaging",
  "version": "4.18.0"
}
```

**Example output:**
```
Messaging Components:
  - kafka (Apache Kafka messaging)
  - amqp (AMQP 1.0 messaging)
  - jms (JMS messaging)
  - activemq (Apache ActiveMQ)
  - rabbitmq (RabbitMQ)
  - aws2-sqs (AWS Simple Queue Service)
```

**Fallback:** Prompts user to specify component manually

---

#### camel_catalog_component_doc

Retrieve comprehensive documentation for a specific component.

**Used in:** camel-flow, camel-implement, camel-test

**Invocations across skills:**
- **camel-flow** — after user selects source/sink component (Lines 180, 311)
- **camel-implement** — component options lookup and application.properties generation (Lines 177, 196)
- **camel-test** — test mock setup for Testcontainers (Line 177)

**Parameters:**
```json
{
  "name": "kafka",
  "version": "4.18.0"
}
```

**Example output:**
```
Component: kafka
Description: Send and receive messages from Apache Kafka

URI Syntax: kafka:topic

Component Options:
  - brokers (string): Kafka broker addresses
  - groupId (string): Consumer group ID

Endpoint Options:
  - topic (string, required): Topic name
  - autoOffsetReset (string): earliest, latest

Maven: org.apache.camel:camel-kafka:4.18.0
```

**Fallback:** Load from local component skills at `{agent.folder}/skills/camel-component-{name}/SKILL.md`

---

#### camel_validate_route

Validate Camel endpoint URIs against the catalog schema, catch typos and unknown options.

**Used in:** camel-implement, camel-validate

**Invocations across skills:**
- **camel-implement** — pre-validation of source/sink/DLQ URIs (Lines 390-420), complete route validation after generation (Line 867)
- **camel-validate** — URI validation phase for all endpoints (Lines 215-233)

**Parameters (single URI):**
```json
{
  "uri": "kafka:topic-name?brokers=localhost:9092",
  "version": "4.18.0"
}
```

**Parameters (full route):**
```json
{
  "route": "<entire YAML route content>",
  "version": "4.18.0"
}
```

**Example output (valid):**
```
Component: kafka (exists in catalog)
Path parameter: topic-name (valid)
Options: brokers (valid, type: string)
```

**Example output (typo):**
```
Component 'kafak' not found
Did you mean: 'kafka'?
Suggestion: Use 'kafka:test-topic'
```

---

#### camel_route_context

Given a Camel route (YAML, XML, or Java DSL), extracts all components and EIPs used.

**Used in:** camel-implement, camel-test

**Invocations across skills:**
- **camel-implement** — post-generation analysis to verify route structure (Line 855)
- **camel-test** — determine test strategy and mock requirements (Line 122)

**Parameters:**
```json
{
  "route": "<entire YAML content>",
  "version": "4.18.0"
}
```

**Example output:**
```
Components detected: [kafka, sql, http]
EIPs used: [unmarshal, validate, choice, filter]
Data formats: [json]
All components valid for Camel 4.18.0
```

---

#### camel_route_harden_context

Analyze routes for security concerns — 47 automated checks.

**Used in:** camel-validate (SKILL.md Line 471)

**Parameters:**
```json
{
  "route": "<entire YAML route>",
  "version": "4.18.0"
}
```

**Example output:**
```
Security Analysis (47 Checks):

Passed (45):
  - No hardcoded credentials
  - HTTPS used for external calls
  - Input validation present
  ...

Warnings (2):
  Line 12: HTTP instead of HTTPS
    Risk: Unencrypted communication
    Fix: Change http://api.example.com to https://api.example.com

  Line 24: Potential SQL injection risk
    Risk: Direct string concatenation in SQL
    Fix: Use parameterized queries with :#parameter

Score: 45/47 (95.7%)
Risk Level: LOW
```

**Security checks include:** credential exposure, encryption, authentication, input validation (SQL injection, XSS), data exposure in logs, compliance (GDPR, PCI-DSS, HIPAA), error handling (information disclosure).

### Tool Invocation Flow

The **AI agent** invokes MCP tools, NOT the Java code. Here's the complete flow:

```
1. Configuration Phase (during `camel-kit init`)
   ─────────────────────────────────────────────
   User runs: camel-kit init my-project --ai bob
     → InitCommand.java createMcpConfigs()
     → Creates .bob/mcp.json, .mcp.json, .gemini/mcp.json

2. Agent Reads Skill
   ──────────────────
   User runs: /camel-flow order-processing
     → Agent reads .bob/commands/camel-flow.md
     → Skill says: "If MCP available: Use camel_catalog_components"
     → Agent checks: .bob/mcp.json exists → MCP available

3. Agent Starts MCP Server
   ────────────────────────
   Agent executes: jbang -Dquarkus.log.level=WARN \
                     org.apache.camel:camel-jbang-mcp:4.18.0:runner
     → MCP Server starts (Quarkus application)
     → Exposes 15 tools via Model Context Protocol

4. Agent Invokes MCP Tool
   ───────────────────────
   Agent calls: camel_catalog_components { "category": "messaging", "version": "4.18.0" }
     → MCP Server queries Camel 4.18.0 catalog
     → Returns: ["kafka", "amqp", "jms", "rabbitmq", ...]

5. Agent Uses Results
   ──────────────────
   Agent presents: "I found these messaging components: kafka, amqp, jms..."

6. Graceful Fallback
   ──────────────────
   If .bob/mcp.json missing:
     → Skill detects MCP not available
     → Loads local component skills
     → Prompts user for component name
     → Continues with degraded functionality
```

### Token Savings Statistics

#### By Skill

| Skill | Without MCP | With MCP | Savings |
|-------|-------------|----------|---------|
| camel-project | N/A | N/A | 0% (version list is small) |
| camel-flow | ~3000 tokens | ~1200 tokens | **60%** |
| camel-implement | ~4000 tokens | ~1600 tokens | **60%** |
| camel-validate | ~5000 tokens | ~1500 tokens | **70%** |
| camel-test | ~2500 tokens | ~1250 tokens | **50%** |

**Average token savings:** ~60% across all skills

#### By Tool

| Tool | Skill Count | Total Invocations | Primary Use Case |
|------|-------------|-------------------|------------------|
| `camel_catalog_component_doc` | 3 | ~8 | Component documentation |
| `camel_validate_route` | 2 | ~8 | URI and route validation |
| `camel_catalog_components` | 1 | ~2 | Component discovery |
| `camel_route_context` | 2 | ~2 | Route analysis |
| `camel_route_harden_context` | 1 | ~1 | Security analysis |
| `camel_version_list` | 1 | ~2 | Version management |

**Total invocations per workflow:** ~23 MCP tool calls

---

## Skills + MCP: How They Work Together

| Use Case | Mechanism | Token Cost | Best For |
|----------|-----------|------------|----------|
| Component search | MCP | ~100 tokens per query | Flow design, quick lookups |
| Component docs | MCP | ~200 tokens per query | Design-time configuration |
| Implementation | Skills | ~5k tokens (2-5 components) | Complete schemas, full config |
| URI validation | MCP | ~100 tokens per call | Real-time correctness checks |
| Security analysis | MCP | ~300 tokens per call | 47 automated checks |

**Combined token efficiency:**

| Approach | Token Cost |
|----------|------------|
| Without skills or MCP | ~500k tokens (all 396 component catalogs) |
| With skills only | ~10k tokens (2-5 components loaded) |
| With skills + MCP | ~6k tokens (skills for implementation, MCP for queries) |
| **Overall savings** | **99% reduction** |

**Example: Full workflow for a Kafka-to-SQL integration**

| Phase | What happens | Tokens |
|-------|-------------|--------|
| Flow design (`/camel-flow`) | MCP `camel_catalog_components` searches by category, `camel_catalog_component_doc` retrieves basic info | ~300 |
| Implementation (`/camel-implement`) | Load bundled kafka + sql skills (full schemas), MCP `camel_route_context` + `camel_validate_route` | ~5,200 |
| Validation (`/camel-validate`) | MCP `camel_route_harden_context` runs 47 security checks | ~300 |
| **Total** | | **~5,800 tokens** |

---

## Extending Camel-Kit

### Adding a New Skill

1. Create a skill directory in `camel-kit-core/src/main/resources/skills/`:

```
skills/camel-component-{name}/
├── SKILL.md
└── schema.json
```

2. Follow the SKILL.md format (see [Skill Structure](#skill-structure))
3. Add MCP tool usage instructions if applicable
4. Register the skill in the appropriate command templates
5. Update `docs/commands.md` if it's a major command

### Future Enhancements

- **Component Documentation Integration** — enhance skills with content from Camel AsciiDoc documentation (code examples, common use cases, configuration best practices)
- **Skill Categories** — organize skills by category for easier discovery (Messaging, Database, Cloud, API)
- **Skill Recommendations** — add command intelligence to suggest components ("For message queues, consider: kafka, jms, or amqp")

---

## References

- [Anthropic Agent Skills](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview)
- [Claude Code Skills](https://code.claude.com/docs/en/skills)
- [Apache Camel Components](https://camel.apache.org/components/latest/)
- [Camel Component Catalog](https://camel.apache.org/manual/component-dsl.html)
- [Official Camel MCP Documentation](https://raw.githubusercontent.com/apache/camel-website/refs/heads/main/content/blog/2026/02/camel-jbang-mcp/index.md)
- [MCP Specification](https://modelcontextprotocol.io/)
- [Camel JBang Documentation](https://camel.apache.org/manual/camel-jbang.html)

---

**Last Updated:** 2026-03-02
**Camel Version:** 4.18.0
**MCP Server:** camel-jbang-mcp:4.18.0

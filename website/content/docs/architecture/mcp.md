---
title: MCP Integration
weight: 2
---

Camel-Kit integrates with the **Apache Camel MCP (Model Context Protocol) Server** to provide real-time catalog queries, route validation, and security analysis directly within your AI coding assistant.

## What is the Camel MCP Server?

The Camel MCP server (`camel-jbang-mcp`) exposes the complete Apache Camel catalog through the Model Context Protocol, allowing AI assistants to:

- **Query components** - Search and filter 300+ Camel components by category
- **Get documentation** - Retrieve always-current component docs for your exact Camel version
- **Validate routes** - Check endpoint URIs against the catalog schema, catch typos
- **Analyze security** - Run 47 automated security checks on routes
- **Extract context** - Analyze routes to identify components and EIPs used

## Benefits

- **60-70% token savings** - AI assistant queries MCP server instead of loading full catalog
- **Always current** - Documentation matches your exact Camel version
- **Better validation** - Catches typos and configuration errors before runtime
- **Security analysis** - Automated checks for credentials, encryption, authentication

## Configuration

The MCP server is automatically configured when you run `camel-kit init`:

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

## Available MCP Tools

The Camel MCP server provides **15 tools** organized into 6 categories:

### 1. Catalog Exploration (8 tools)

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

### 2. Kamelet Catalog (2 tools)

| Tool Name | Purpose |
|-----------|---------|
| `camel_catalog_kamelets` | List available Kamelets with filtering |
| `camel_catalog_kamelet_doc` | Get Kamelet documentation and dependencies |

### 3. Route Understanding (1 tool)

| Tool Name | Purpose |
|-----------|---------|
| `camel_route_context` | Extract components and EIPs from route (YAML/XML/Java) |

### 4. Security Analysis (1 tool)

| Tool Name | Purpose |
|-----------|---------|
| `camel_route_harden_context` | Analyze routes for security concerns (47 checks) |

### 5. Validation and Transformation (2 tools)

| Tool Name | Purpose |
|-----------|---------|
| `camel_validate_route` | Validate endpoint URIs against catalog schema |
| `camel_transform_route` | Convert routes between YAML and XML formats |

### 6. Version Management (1 tool)

| Tool Name | Purpose |
|-----------|---------|
| `camel_version_list` | List Camel versions with LTS status and JDK requirements |

## Tool Usage by Skill

| Skill | Tools Used | Count |
|-------|------------|-------|
| **camel-project** | `camel_version_list` | 1 |
| **camel-flow** | `camel_catalog_components`, `camel_catalog_component_doc`, `camel_catalog_dataformats`, `camel_catalog_dataformat_doc`, `camel_catalog_eips`, `camel_catalog_eip_doc`, `camel_catalog_languages`, `camel_catalog_language_doc` | 8 |
| **camel-migrate** | Same as camel-flow (Phase 2) | 8 |
| **camel-implement** | `camel_catalog_component_doc`, `camel_catalog_dataformat_doc`, `camel_catalog_eip_doc`, `camel_catalog_language_doc`, `camel_route_context`, `camel_validate_route` | 6 |
| **camel-validate** | `camel_validate_route`, `camel_route_harden_context` | 2 |
| **camel-test** | `camel_route_context`, `camel_catalog_component_doc` | 2 |

## Tool Invocation Flow

The **AI agent** invokes MCP tools, NOT the Java code. Here's the complete flow:

```
1. Configuration Phase (during `camel-kit init`)
   User runs: camel-kit init my-project --ai bob
     → InitCommand.java createMcpConfigs()
     → Creates .bob/mcp.json, .mcp.json, .gemini/mcp.json

2. Agent Reads Skill
   User runs: /camel-flow order-processing
     → Agent reads .bob/commands/camel-flow.md
     → Skill says: "If MCP available: Use camel_catalog_components"
     → Agent checks: .bob/mcp.json exists → MCP available

3. Agent Starts MCP Server
   Agent executes: jbang -Dquarkus.log.level=WARN \
                     org.apache.camel:camel-jbang-mcp:4.18.0:runner
     → MCP Server starts (Quarkus application)
     → Exposes 15 tools via Model Context Protocol

4. Agent Invokes MCP Tool
   Agent calls: camel_catalog_components { "category": "messaging" }
     → MCP Server queries Camel 4.18.0 catalog
     → Returns: ["kafka", "amqp", "jms", "rabbitmq", ...]

5. Agent Uses Results
   Agent presents: "I found these messaging components: kafka, amqp, jms..."

6. Graceful Fallback
   If .bob/mcp.json missing:
     → Skill detects MCP not available
     → Loads local component skills
     → Continues with degraded functionality
```

## Token Savings

### By Skill

| Skill | Without MCP | With MCP | Savings |
|-------|-------------|----------|---------|
| camel-flow | ~3000 tokens | ~1200 tokens | **60%** |
| camel-implement | ~4000 tokens | ~1600 tokens | **60%** |
| camel-validate | ~5000 tokens | ~1500 tokens | **70%** |
| camel-test | ~2500 tokens | ~1250 tokens | **50%** |

**Average token savings:** ~60% across all skills

### Skills + MCP Combined Efficiency

| Approach | Token Cost |
|----------|------------|
| Without skills or MCP | ~500k tokens (all 396 component catalogs) |
| With skills only | ~10k tokens (2-5 components loaded) |
| With skills + MCP | ~6k tokens (skills for implementation, MCP for queries) |
| **Overall savings** | **99% reduction** |

### Example: Full workflow for a Kafka-to-SQL integration

| Phase | What happens | Tokens |
|-------|-------------|--------|
| Flow design (`/camel-flow`) | MCP searches by category, retrieves basic info | ~300 |
| Implementation (`/camel-implement`) | Load bundled kafka + sql skills, MCP validates | ~5,200 |
| Validation (`/camel-validate`) | MCP runs 47 security checks | ~300 |
| **Total** | | **~5,800 tokens** |

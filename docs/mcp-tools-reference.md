# Camel MCP Server - Tools Usage Reference

This document provides a comprehensive reference of how and where the Camel MCP server tools are used across camel-kit skills.

---

## Table of Contents

1. [MCP Server Configuration](#mcp-server-configuration)
2. [Available MCP Tools](#available-mcp-tools)
3. [Tool Usage by Skill](#tool-usage-by-skill)
4. [Detailed Tool Reference](#detailed-tool-reference)
5. [Tool Invocation Flow](#tool-invocation-flow)

---

## MCP Server Configuration

### Configuration Files

The MCP server is configured in project-specific configuration files created during `camel kit init`:

**File:** `.mcp.json` (Claude Code)
**File:** `.bob/mcp.json` (IBM Bob)
**File:** `.gemini/mcp.json` (Gemini CLI)

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

**Created by:**
- **File:** `camel-kit-core/src/main/java/io/github/luigidemasi/camelkit/command/InitCommand.java`
- **Method:** `createMcpConfigs()`
- **Lines:** 320-376

---

## Available MCP Tools

The Camel MCP server (camel-jbang-mcp:4.18.0) provides **15 tools** organized into 6 categories:

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

---

## Tool Usage by Skill

### Summary Table

| Skill | Tools Used | Count | Files Modified |
|-------|------------|-------|----------------|
| **camel-project** | camel_version_list | 1 | Business Requirements |
| **camel-flow** | camel_catalog_components<br>camel_catalog_component_doc | 2 | Technical Design Document |
| **camel-implement** | camel_catalog_component_doc<br>camel_validate_route<br>camel_route_context | 3 | YAML Routes, Properties |
| **camel-validate** | camel_validate_route<br>camel_route_harden_context | 2 | Validation Reports |
| **camel-test** | camel_route_context<br>camel_catalog_component_doc | 2 | Test Suites |

**Total Unique Tools Used:** 6 out of 15 available (40%)

---

## Detailed Tool Reference

### 1. camel_version_list

**Description:** List available Camel versions with release dates, JDK requirements, and LTS status

**Used In:** camel-project

**File:** `camel-kit-core/src/main/resources/skills/camel-project/SKILL.md`

**Invocations:**

#### Invocation 1: Version Selection
- **Line:** 183
- **Context:** Question 6 - Camel Version Selection (with MCP available)
- **Purpose:** Display available versions for user to choose from
- **Parameters:**
  ```json
  {}  // No parameters - returns all versions
  ```
- **Expected Output:**
  ```
  Recent Versions:
    4.18.0 (LTS) - Released 2025-01-15 - JDK 17+ - ⭐ Recommended
    4.17.0       - Released 2024-12-10 - JDK 17+
    4.16.0 (LTS) - Released 2024-11-05 - JDK 17+
  ```

#### Invocation 2: Version Details
- **Line:** 209
- **Context:** After user selects a specific version
- **Purpose:** Verify version details and compatibility
- **Parameters:**
  ```json
  {
    "version": "4.18.0"
  }
  ```
- **Expected Output:**
  ```
  Version: 4.18.0
  LTS: Yes
  Release Date: 2025-01-15
  JDK Required: 17+
  Status: Supported
  ```

**Fallback:** If MCP not available, suggests default version 4.18.0 (Line 221)

---

### 2. camel_catalog_components

**Description:** Search and list Camel components by category, name, or label

**Used In:** camel-flow

**File:** `camel-kit-core/src/main/resources/skills/camel-flow/SKILL.md`

**Invocations:**

#### Invocation 1: Source Component Discovery
- **Line:** 169
- **Context:** Question 2 - Source System Selection
- **Purpose:** Find components matching user's source system description
- **Parameters:**
  ```json
  {
    "category": "messaging",  // or "database", "cloud", etc.
    "version": "4.18.0"
  }
  ```
- **Expected Output:**
  ```
  Messaging Components:
    - kafka (Apache Kafka messaging)
    - amqp (AMQP 1.0 messaging)
    - jms (JMS messaging)
    - activemq (Apache ActiveMQ)
    - rabbitmq (RabbitMQ)
    - aws2-sqs (AWS Simple Queue Service)
  ```

#### Invocation 2: Sink Component Discovery
- **Line:** 300
- **Context:** Question 3 - Sink System Selection
- **Purpose:** Find components matching user's target system description
- **Parameters:**
  ```json
  {
    "category": "database",
    "version": "4.18.0"
  }
  ```
- **Expected Output:**
  ```
  Database Components:
    - sql (SQL databases via JDBC)
    - jdbc (JDBC component)
    - mongodb (MongoDB)
    - cassandra (Apache Cassandra)
    - elasticsearch (Elasticsearch)
  ```

**Fallback:** If MCP not available, prompts user to specify component manually (Line 51)

---

### 3. camel_catalog_component_doc

**Description:** Retrieve comprehensive documentation for a specific component

**Used In:** camel-flow, camel-implement, camel-test

**File:** `camel-kit-core/src/main/resources/skills/camel-flow/SKILL.md`

**Invocations:**

#### Invocation 1: Source Component Documentation (camel-flow)
- **Line:** 180
- **Context:** After user selects source component
- **Purpose:** Get URI syntax, options, examples
- **Parameters:**
  ```json
  {
    "name": "kafka",
    "version": "4.18.0"
  }
  ```
- **Expected Output:**
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

#### Invocation 2: Sink Component Documentation (camel-flow)
- **Line:** 311
- **Context:** After user selects sink component
- **Purpose:** Get URI syntax and configuration options
- **Parameters:**
  ```json
  {
    "name": "sql",
    "version": "4.18.0"
  }
  ```

**File:** `camel-kit-core/src/main/resources/skills/camel-implement/SKILL.md`

#### Invocation 3: Component Options Lookup (camel-implement)
- **Line:** 177
- **Context:** Before generating component configuration
- **Purpose:** Get available options for component setup
- **Parameters:**
  ```json
  {
    "name": "kafka",
    "version": "4.18.0"
  }
  ```

#### Invocation 4: Detailed Configuration (camel-implement)
- **Line:** 196
- **Context:** Generating application.properties
- **Purpose:** Get component-level and endpoint-level options
- **Parameters:**
  ```json
  {
    "name": "kafka",
    "version": "4.18.0"
  }
  ```

**File:** `camel-kit-core/src/main/resources/skills/camel-test/SKILL.md`

#### Invocation 5: Test Mock Setup (camel-test)
- **Line:** 177
- **Context:** Setting up component mocks for testing
- **Purpose:** Get component details for Testcontainers/mocks
- **Parameters:**
  ```json
  {
    "name": "kafka",
    "version": "4.18.0"
  }
  ```

**Fallback:** Load from local component skills at `{agent.folder}/skills/camel-component-{name}/SKILL.md`

---

### 4. camel_validate_route

**Description:** Validate Camel endpoint URIs against the catalog schema, catch typos and unknown options

**Used In:** camel-implement, camel-validate

**File:** `camel-kit-core/src/main/resources/skills/camel-implement/SKILL.md`

**Invocations:**

#### Invocation 1: URI Pre-validation (Before Generation)
- **Line:** 390
- **Context:** Pre-validating URIs before writing YAML
- **Purpose:** Catch typos early in source URI
- **Parameters:**
  ```json
  {
    "uri": "kafka:topic-name?brokers=localhost:9092",
    "version": "4.18.0"
  }
  ```
- **Expected Output:**
  ```
  ✅ VALID
  Component: kafka (exists in catalog)
  Path parameter: topic-name (valid)
  Options: brokers (valid, type: string)
  ```

#### Invocation 2: URI Pre-validation (Sink)
- **Line:** 399
- **Context:** Validating sink URI
- **Parameters:**
  ```json
  {
    "uri": "sql:INSERT INTO orders VALUES (:#order)",
    "version": "4.18.0"
  }
  ```

#### Invocation 3: URI Pre-validation (Error Handler)
- **Line:** 408
- **Context:** Validating DLQ URI
- **Parameters:**
  ```json
  {
    "uri": "kafka:dlq-topic",
    "version": "4.18.0"
  }
  ```

#### Invocation 4: Typo Detection
- **Line:** 420
- **Context:** When typo is detected in component name
- **Parameters:**
  ```json
  {
    "uri": "kafak:test-topic",
    "version": "4.18.0"
  }
  ```
- **Expected Output:**
  ```
  ❌ ERROR
  Component 'kafak' not found
  Did you mean: 'kafka'?

  Suggestion: Use 'kafka:test-topic'
  ```

#### Invocation 5: Complete Route Validation (After Generation)
- **Line:** 867
- **Context:** Post-implementation validation
- **Purpose:** Validate entire generated route
- **Parameters:**
  ```json
  {
    "route": "<entire YAML route content>",
    "version": "4.18.0"
  }
  ```
- **Expected Output:**
  ```
  Validating: kafka:orders?brokers=localhost:9092
    ✅ Component 'kafka' exists
    ✅ All options valid
    ✅ Required parameters present

  Validating: sql:INSERT INTO orders
    ✅ Component 'sql' exists
    ✅ Query syntax valid
    ✅ DataSource reference valid

  ✅ All endpoint URIs valid (3/3 passed)
  ✅ No typos or unknown options
  ```

**File:** `camel-kit-core/src/main/resources/skills/camel-validate/SKILL.md`

#### Invocation 6: URI Validation Phase
- **Line:** 215
- **Context:** Phase 2 - URI Validation
- **Purpose:** Validate all endpoint URIs in the route
- **Parameters:**
  ```json
  {
    "uri": "kafka:orders?brokers={{kafka.brokers}}",
    "version": "4.18.0"
  }
  ```

#### Invocation 7: URI Validation (From URI)
- **Line:** 224
- **Context:** Validating source endpoint
- **Purpose:** Check from: URI
- **Parameters:** Each from URI in route

#### Invocation 8: URI Validation (To URIs)
- **Line:** 233
- **Context:** Validating target endpoints
- **Purpose:** Check all to: URIs in route
- **Parameters:** Each to URI in route

**Token Savings:** 70% vs embedding full catalog (Line 216)

---

### 5. camel_route_context

**Description:** Given a Camel route (YAML, XML, or Java DSL), extracts all components and EIPs used

**Used In:** camel-implement, camel-test

**File:** `camel-kit-core/src/main/resources/skills/camel-implement/SKILL.md`

**Invocations:**

#### Invocation 1: Post-Generation Analysis
- **Line:** 855
- **Context:** After route generation, Step 1 of validation
- **Purpose:** Analyze generated route structure
- **Parameters:**
  ```json
  {
    "route": "<entire YAML content>",
    "version": "4.18.0"
  }
  ```
- **Expected Output:**
  ```
  Components detected: [kafka, sql, http]
  EIPs used: [unmarshal, validate, choice, filter]
  Data formats: [json]
  ✅ All components valid for Camel 4.18.0
  ✅ All EIPs valid
  ✅ Component documentation retrieved
  ```

**File:** `camel-kit-core/src/main/resources/skills/camel-test/SKILL.md`

#### Invocation 2: Test Strategy Determination
- **Line:** 122
- **Context:** Analyzing route for test generation
- **Purpose:** Determine what to test and mock
- **Parameters:**
  ```json
  {
    "route": "<YAML route from file>",
    "version": "4.18.0"
  }
  ```
- **Expected Output:**
  ```
  Components: kafka, sql, http
  EIPs: unmarshal, validate, filter, choice
  Data Formats: json, xml

  Test Requirements:
    - Mock Kafka producer/consumer
    - Mock SQL database
    - Mock HTTP endpoint
    - Test unmarshal with sample JSON
    - Test validation rules
    - Test routing logic
  ```

**Benefit:** Generates appropriate test fixtures based on actual route content

---

### 6. camel_route_harden_context

**Description:** Analyze routes for security concerns - 47 automated checks

**Used In:** camel-validate

**File:** `camel-kit-core/src/main/resources/skills/camel-validate/SKILL.md`

**Invocations:**

#### Invocation 1: Security Analysis Phase
- **Line:** 471
- **Context:** Phase 4 - Security Analysis (MCP Enhanced)
- **Purpose:** Run comprehensive security checks
- **Parameters:**
  ```json
  {
    "route": "<entire YAML route>",
    "version": "4.18.0"
  }
  ```
- **Expected Output:**
  ```
  Security Analysis (47 Checks):

  ✅ Passed (45):
    - No hardcoded credentials
    - HTTPS used for external calls
    - Input validation present
    - Error handling configured
    - Logging sanitized
    ...

  ⚠️ Warnings (2):
    Line 12: HTTP instead of HTTPS
      Risk: Unencrypted communication
      Fix: Change http://api.example.com to https://api.example.com

    Line 24: Potential SQL injection risk
      Risk: Direct string concatenation in SQL
      Fix: Use parameterized queries with :#parameter

  Score: 45/47 (95.7%)
  Risk Level: LOW
  ```

**Security Checks Include:**
- Credential exposure (hardcoded passwords, API keys, tokens)
- Encryption (HTTP vs HTTPS, unencrypted protocols)
- Authentication (missing auth configuration)
- Input validation (SQL injection, XSS risks)
- Data exposure (sensitive data in logs)
- Compliance (GDPR, PCI-DSS, HIPAA concerns)
- Error handling (information disclosure)

**Token Savings:** 70% vs embedding full security ruleset (Line 472)

---

## Tool Invocation Flow

### How MCP Tools Are Invoked

The **AI agent** invokes MCP tools, NOT the Java code. Here's the complete flow:

#### 1. Configuration Phase (During `camel kit init`)

```
User runs: camel kit init my-project --ai bob

InitCommand.java (Line 120)
  ↓
createMcpConfigs() (Line 320-376)
  ↓
Creates:
  - .bob/mcp.json
  - .claude/mcp.json
  - .gemini/mcp.json

Content: JBang command to start MCP server
```

#### 2. Agent Reads Skill

```
User runs: /camel-flow order-processing

Bob reads: .bob/commands/camel-flow.md
  ↓
Skill says (Line 50):
  "If MCP available: Use camel_catalog_components"
  ↓
Bob checks: .bob/mcp.json exists → MCP available
```

#### 3. Agent Starts MCP Server

```
Bob executes:
  jbang -Dquarkus.log.level=WARN \
        org.apache.camel:camel-jbang-mcp:4.18.0:runner

MCP Server starts (Quarkus application)
  ↓
Exposes 15 tools via Model Context Protocol
  ↓
Ready to accept tool calls from agent
```

#### 4. Agent Invokes MCP Tool

```
Bob calls:
  Tool: camel_catalog_components
  Params: { "category": "messaging", "version": "4.18.0" }
  ↓
MCP Server:
  - Queries Camel 4.18.0 catalog
  - Filters by category="messaging"
  - Returns component list
  ↓
Result: ["kafka", "amqp", "jms", "rabbitmq", "aws2-sqs", ...]
  ↓
Bob uses results to help user select component
```

#### 5. Agent Uses Results

```
Bob presents to user:
  "I found these messaging components:
   - kafka (Apache Kafka)
   - amqp (AMQP 1.0)
   - jms (JMS)

   Which would you like to use?"
```

#### 6. Graceful Fallback

```
If .bob/mcp.json missing:
  ↓
Skill detects: MCP not available
  ↓
Uses fallback approach:
  - Loads local component skills
  - Prompts user for component name
  - Continues with degraded functionality
```

---

## MCP Tool Usage Statistics

### Tools Used Across Skills

| Tool | Skill Count | Total Invocations | Primary Use Case |
|------|-------------|-------------------|------------------|
| **camel_catalog_component_doc** | 3 | ~8 | Component documentation |
| **camel_validate_route** | 2 | ~8 | URI and route validation |
| **camel_catalog_components** | 1 | ~2 | Component discovery |
| **camel_route_context** | 2 | ~2 | Route analysis |
| **camel_route_harden_context** | 1 | ~1 | Security analysis |
| **camel_version_list** | 1 | ~2 | Version management |

**Total Invocations per Workflow:** ~23 MCP tool calls

### Token Savings by Skill

| Skill | Without MCP | With MCP | Savings |
|-------|-------------|----------|---------|
| camel-project | N/A | N/A | 0% (version list is small) |
| camel-flow | ~3000 tokens | ~1200 tokens | **60%** |
| camel-implement | ~4000 tokens | ~1600 tokens | **60%** |
| camel-validate | ~5000 tokens | ~1500 tokens | **70%** |
| camel-test | ~2500 tokens | ~1250 tokens | **50%** |

**Average Token Savings:** ~60% across all skills

---

## File Locations Summary

### Skills Files (Where MCP is used)

```
camel-kit-core/src/main/resources/skills/
├── camel-project/
│   └── SKILL.md                    # camel_version_list (Lines 183, 209)
├── camel-flow/
│   └── SKILL.md                    # camel_catalog_components (Lines 169, 300)
│                                   # camel_catalog_component_doc (Lines 180, 311)
├── camel-implement/
│   └── SKILL.md                    # camel_catalog_component_doc (Lines 177, 196)
│                                   # camel_validate_route (Lines 390, 399, 408, 420, 867)
│                                   # camel_route_context (Line 855)
├── camel-validate/
│   └── SKILL.md                    # camel_validate_route (Lines 215, 224, 233)
│                                   # camel_route_harden_context (Line 471)
└── camel-test/
    └── SKILL.md                    # camel_route_context (Line 122)
                                    # camel_catalog_component_doc (Line 177)
```

### Configuration Files (MCP setup)

```
camel-kit-core/src/main/java/io/github/luigidemasi/camelkit/command/
└── InitCommand.java                # createMcpConfigs() (Lines 320-376)

Generated project structure:
project/
├── .mcp.json                       # Claude Code MCP config
├── .bob/
│   └── mcp.json                    # IBM Bob MCP config
└── .gemini/
    └── mcp.json                    # Gemini CLI MCP config
```

### Documentation Files

```
camel-kit-core/src/main/resources/templates/mcp-configs/
├── MCP-SETUP.md                    # Setup guide for 3 agents
├── MCP-TESTING.md                  # Testing and troubleshooting
└── MCP-TOOLS-REFERENCE.md          # This file
```

---

## References

- **Official Camel MCP Documentation:** https://raw.githubusercontent.com/apache/camel-website/refs/heads/main/content/blog/2026/02/camel-jbang-mcp/index.md
- **JIRA Issue (YAML Validator):** CAMEL-22985
- **MCP Specification:** https://modelcontextprotocol.io/
- **Camel JBang Documentation:** https://camel.apache.org/manual/camel-jbang.html

---

**Last Updated:** 2026-02-21
**Camel Version:** 4.18.0
**MCP Server:** camel-jbang-mcp:4.18.0

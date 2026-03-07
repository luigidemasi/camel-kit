---
name: camel-flow
description: Design integration flows when user wants to create TDD, define data flow, specify source and sink systems, plan transformations, or architect message routing
user-invocable: true
metadata:
  version: "1.0.0"
  author: "camel-kit"
  category: "integration"
  license: "Apache-2.0"
---

# Camel Flow - Technical Design Document Creation

You are acting as a **Business Analyst and Integration Architect** helping the user design a specific integration flow.

## Role and Approach

- Bridge business requirements with technical implementation
- Ask clear technical questions about sources, sinks, and transformations
- Load detailed guides **only when needed** to save tokens
- Suggest appropriate Apache Camel components based on requirements
- Document technical decisions with clear rationale

## Parameters

```
/camel-flow <flow-name>
```

Example: `/camel-flow order-to-warehouse`

---

## Context Loading

**ALWAYS read at the start:**
1. `docs/business-requirements.md` - Business context (REQUIRED)
2. `docs/constitution.md` - Best practices. If missing, copy from `templates/constitution.md` and continue.
3. `.camel-kit/config.yaml` - **REQUIRED** — extract `project.camelVersion` and store it as `CAMEL_VERSION`. Every `camel_catalog_components` and `camel_catalog_component_doc` call MUST use this exact version. If the file does not exist, ask the user for the Camel version before proceeding.

**On-Demand Guides (load ONLY when needed):**
- `skills/camel-flow/guides/data-formats.md` - If user asks about format choice
- `skills/camel-flow/guides/integration-patterns.md` - If user unsure about pattern
- `skills/camel-flow/guides/eip-catalog.md` - If user unsure about transformations
- `skills/camel-flow/guides/performance.md` - If high throughput/latency mentioned
- `skills/camel-flow/guides/security.md` - If security/compliance mentioned
- `skills/camel-flow/guides/monitoring.md` - If observability needed

**Component selection (Questions 2 and 4) — MANDATORY:**
- **Primary:** Call `camel_catalog_components` + `camel_catalog_component_doc` directly (with `CAMEL_VERSION`). Do not suggest component names from training data before attempting the catalog call.
- **Fallback (tool call failed):** Load from `{skills.folder}/camel-component-[name]/SKILL.md`. Warn if no bundled skill exists.

---

## MCP Server Configuration (Recommended)

The Camel MCP server provides powerful catalog query capabilities:
- **Component Search** (`camel_catalog_components`) - Find components available in the project Camel version
- **Component Documentation** (`camel_catalog_component_doc`) - Full docs, options, and Maven coords for a specific component
- **Data Format List** (`camel_catalog_dataformats`) - All data formats available in the project Camel version
- **Data Format Documentation** (`camel_catalog_dataformat_doc`) - Full docs, options, and Maven coords for a specific data format
- **Language List** (`camel_catalog_languages`) - All expression languages available in the project Camel version
- **Language Documentation** (`camel_catalog_language_doc`) - Full docs, syntax, and Maven coords for a specific expression language
- **EIP List** (`camel_catalog_eips`) - All Enterprise Integration Patterns available in the project Camel version, filterable by category
- **EIP Documentation** (`camel_catalog_eip_doc`) - Full options and YAML DSL usage for a specific EIP

All catalog calls MUST pass `CAMEL_VERSION` (from `.camel-kit/config.yaml`) as the `version` parameter.

Always attempt MCP tool calls directly — do not check for `.mcp.json` or try to detect MCP availability upfront. If a tool call fails (tool not found, network error, timeout), fall back to the bundled component skill files.

**To enable MCP server**, add to `.mcp.json`:
```json
{
  "mcpServers": {
    "camel": {
      "command": "jbang",
      "args": [
        "--repos", "redhat=https://maven.repository.redhat.com/ga/",
        "-Dquarkus.log.level=WARN",
        "org.apache.camel:camel-jbang-mcp:LATEST:runner"
      ]
    }
  }
}
```

Use `LATEST` for the MCP server artifact (must resolve to ≥ 4.18.0). If `LATEST` fails to resolve, fall back to `4.18.0`. The MCP server is a development tool — it can serve catalog data for any Camel version regardless of its own version.

**CRITICAL — MCP version stripping:** If `CAMEL_VERSION` contains a `.redhat-XXXXX` suffix (e.g., `4.14.4.redhat-00008`), strip it before passing to MCP catalog tools (`camel_catalog_*`). The Camel Catalog MCP server uses community versions only.
Example: `4.14.4.redhat-00008` → pass `4.14.4` to MCP calls. Keep the full `.redhat` version for Maven dependencies and `pom.xml`.

---

## Check for Existing TDD

First, check if `docs/flows/{flow-name}/{flow-name}.tdd.md` exists.

If exists:
```
Found existing TDD for '{flow-name}'.

Would you like to:
1. Update existing TDD
2. Start fresh
3. Review and continue to implementation
```

---

## Interview Process

Ask **ONE question at a time**. Wait for response before proceeding.

### Introduction

```
Based on the Business Requirements Document:

Flow: {flow-name}
Purpose: [extract from BRD if available]

I'll ask technical questions to create the TDD.
```

---

### Step 0 — Target Runtime

Before starting the design interview, check if `project.runtime` is set in `.camel-kit/config.yaml`. If not, ask:

```
What is the target runtime for this integration?

(a) Camel JBang (lightweight, flat project structure) — recommended for prototyping
(b) Spring Boot (Maven layout: src/main/resources/)
(c) Quarkus (Maven layout: src/main/resources/)
```

Store the answer in `.camel-kit/config.yaml` as `project.runtime: jbang | spring-boot | quarkus`.

If already set, skip this question.

---

### Question 1: Flow Intent and Data

```
What data does this flow process, and what is the goal?

Describe:
- Data type (e.g., "Order events", "Customer records")
- Format (e.g., JSON, XML, CSV)
- Goal (e.g., "Store in database", "Send to queue")

Example: "Process JSON order events and insert into warehouse database."
```

**After response — data format lookup (MANDATORY):**

→ **Load `guides/catalog-lookups.md` § Data Format Lookup** and execute the data format verification procedure with the format mentioned by the user.

**If user uncertain about format choice:**
→ Show the list from `camel_catalog_dataformats`, optionally load `skills/camel-flow/guides/data-formats.md` for comparison guidance, then ask the user to choose.

---

### Question 2: Source System

```
Where does the data come from?

- System name (e.g., "Shopify", "Kafka topic")
- Technology (e.g., "Kafka", "REST API", "File")
- Trigger (e.g., "New messages", "Polling every 5s")

Example: "Kafka topic 'orders', consuming new messages as they arrive"
```

**After response — select source component (MANDATORY):**

→ **Load `guides/component-selection.md`** and execute the component selection procedure with:
- `SYSTEM_DESCRIPTION`: the user's source system description
- `SYSTEM_ROLE`: "source"
- `CAMEL_VERSION`: from `.camel-kit/config.yaml`

**After user confirms component:**

**If user unsure about integration pattern:**
→ Load `skills/camel-flow/guides/integration-patterns.md`
→ Help classify pattern (Event-Driven, Request-Reply, Batch, Stream)

---

### Question 3: Transformations

```
What transformations or business rules are needed?

Examples:
- Parse JSON/XML
- Validate fields
- Filter messages
- Enrich with data
- Transform/map message format
- Route by condition
- None (data passes through as-is)

Describe your processing steps, or say "none" if data flows through unchanged.
```

**If user says "none", "no transformations", "passthrough", or similar:**

```
Noted. Data passes through from source to sink unchanged.
No transformations will be documented in the TDD.
```

Skip Question 3a (DataMapper) and all catalog lookups. Proceed directly to Question 4.

**After response (if transformations ARE needed):**

→ **Load `guides/catalog-lookups.md` § EIP Lookup** and execute the EIP verification procedure for each transformation mentioned.

→ **Load `guides/catalog-lookups.md` § Expression Language Lookup** and execute the expression language selection procedure for any EIP that requires an expression.

**If user unsure about EIP patterns:**
→ Query `camel_catalog_eips` for relevant categories first, then optionally load `skills/camel-flow/guides/eip-catalog.md` for higher-level guidance.

Do NOT include `unmarshal` or `marshal` steps unless the user explicitly said they need typed Java objects. Prefer Kaoto DataMapper via `camel-datamapper-interview`.

---

### Question 3a: Data Transformation & Field Mapping (Conditional)

**ONLY invoke if user mentioned data transformation, field mapping, or format conversion in Question 3 AND the format pair is XML→XML, JSON→JSON, JSON→XML, or XML→JSON.**

→ **Load `guides/datamapper-interview.md`** and follow all steps in that guide, passing the flow name, source format, and target format as context.

The guide will:
1. Collect source and target schemas (XSD / JSON Schema)
2. Collect Camel Variables/Headers used as mapping parameters
3. Auto-map exact field name matches and propose inferred mappings
4. Gather conditional and collection mapping requirements
5. Canonicalize field mappings with XSLT-ready Source XPaths and Target Elements (via `skills/shared/datamapper-canonicalize.md`)
6. Confirm enriched mapping table with user
7. Append a canonical `### DataMapper: kaoto-datamapper-{id}` section to the TDD

**After the guide completes**, resume this interview at Question 4.

**Use `unmarshal`/`marshal` ONLY as a fallback** when the format pair does not match any of the four DataMapper-supported combinations, or when the user explicitly requires Java-level processing.

When `unmarshal`/`marshal` IS required, call `camel_catalog_dataformat_doc` for the chosen data format (e.g. `jackson`, `jaxb`, `csv`) with `CAMEL_VERSION` to get the exact Maven coordinates, configuration options, and class model requirements before documenting them in the TDD.

---

### Question 4: Sink System

```
Where should the processed data go?

- System name (e.g., "PostgreSQL", "Fulfillment queue")
- Technology (e.g., "SQL", "Kafka", "HTTP POST")
- Action (e.g., "INSERT INTO", "POST to API")

Example: "PostgreSQL 'warehouse' database, INSERT into orders table"
```

**After response — select sink component (MANDATORY):**

→ **Load `guides/component-selection.md`** and execute the component selection procedure with:
- `SYSTEM_DESCRIPTION`: the user's sink system description
- `SYSTEM_ROLE`: "sink"
- `CAMEL_VERSION`: from `.camel-kit/config.yaml`

---

### Question 4a: Multi-Path Routing (Conditional)

**ONLY ask if** the user's answers to Q3 (Transformations) or Q4 (Sink) indicate multiple destinations, conditional routing, or fan-out (e.g., "route to different systems based on type", "send to both Kafka and database", "notify multiple services").

```
Does data need to be routed to different destinations based on conditions?

Examples:
- "Priority orders go to express queue, standard to normal queue"
- "Send to both database AND notification service"
- "Route by region: EU to one endpoint, US to another"

Describe the routing conditions and each destination, or say "no" if
all data goes to the single sink above.
```

**If user describes multiple paths:**

For each additional sink, execute the component selection procedure:
→ **Load `guides/component-selection.md`** with `SYSTEM_ROLE = "sink"` for each additional destination.

Document in TDD Section 3 (Processing Steps) as a `choice` or `multicast` EIP:
- **choice**: conditional routing (different destinations based on conditions)
- **multicast**: fan-out (same message to multiple destinations)

→ **Load `guides/catalog-lookups.md` § EIP Lookup** to verify the EIP.

**If user says "no":** Skip to Question 5.

---

### Question 5: Error Handling

```
What should happen when errors occur?

Options:
1. Dead Letter Channel - Failed messages to error queue
2. Retry with backoff - Retry N times, then DLQ
3. Log and continue - Log error, keep processing
4. Stop route - Halt on error

Your preference?
```

**Suggest based on response and document retry policy in TDD:**

```
Recommended:

Strategy: Dead Letter Channel
DLQ: [component]:{{dlq.endpoint}}

Retry policy (document in TDD Section 5):
- maximumRedeliveries: 3
- redeliveryDelay: 1000ms
- backOffMultiplier: 2  (1s → 2s → 4s)
- useExponentialBackOff: true

Logging: Error details with correlation ID
```

**Retry policy guidance:**
- Max 3–5 retries for transient failures; exponential backoff to avoid thundering-herd
- Keep retry delays short (< 30 s) — Camel blocks a thread during retry
- For long-delay retries, prefer dead-letter reprocessing via an external scheduler

---

### Resilience Sub-Questions (Conditional)

→ **Load `guides/resilience-interview.md`** and ask the applicable sub-questions based on:
- **Q5b Circuit Breaker:** if source or sink involves external HTTP/REST API
- **Q5c Idempotent Consumer:** if source is a message broker or user mentioned deduplication
- **Q5d Transactions:** if flow writes to more than one external system

If none of these conditions apply, skip directly to Question 6.

---

### Mid-Interview Checkpoint

After completing Questions 1–5 (core flow design), save a draft TDD to `docs/flows/{flow-name}/{flow-name}.tdd.draft.md` containing the data collected so far (source, sink, transformations, error handling). This allows resuming the interview if the session is interrupted.

If this file already exists when starting the interview, offer to resume from where the draft left off.

---

### Question 6: Performance & Throughput (Conditional)

**Ask ONLY if user mentioned:**
- "High volume" / ">100 messages/second"
- "Fast" / "real-time" / "low latency"
- "Performance" / "throughput"
- "Kubernetes" / "cloud" / "scale" / "replicas"

**If mentioned, ask:**

```
What are your performance requirements?

- Expected throughput: [N] messages/second
- Latency target: [N] milliseconds
- Deployment target: single instance or Kubernetes (multiple replicas)?
- Can afford message loss? (yes/no)
```

**Then load:**
→ `skills/camel-flow/guides/performance.md`
→ Show throughput classification, throttling configuration, and Kubernetes scaling guidance

**Throttling guidance (document in TDD if high-throughput):**
- Apply `throttle` EIP when consuming from unbounded sources to protect downstream systems
- Strategies: reject (strict SLA), block (internal), delay (batch), degrade (graceful)
- For Kafka: match `consumersCount` to partition count; `consumersCount × pod replicas` must not exceed partition count

**Kubernetes guidance (document in TDD if cloud deployment):**
- Externalise all config via environment variables or ConfigMaps
- Implement liveness + readiness health probes (`/q/health/live`, `/q/health/ready`)
- Use Kubernetes Secrets for credentials — never hardcode in YAML

**If NOT mentioned:**
→ Skip to Question 7

---

### Question 7: Security (Conditional)

**Ask ONLY if user mentioned:**
- "Security" / "authentication"
- "PII" / "sensitive data"
- "Compliance" / "GDPR" / "HIPAA" / "PCI-DSS"
- "Credentials" / "secrets"

**If mentioned, ask:**

```
What are your security requirements?

- Authentication method
- Sensitive data fields
- Compliance requirements
```

**Then load:**
→ `skills/camel-flow/guides/security.md`
→ Show authentication methods and data protection

**If NOT mentioned:**
→ Skip to Question 8

---

### Question 8: Monitoring (Conditional)

**Ask ONLY if user mentioned:**
- "Monitoring" / "metrics"
- "Logging" / "tracing"
- "Observability" / "debugging"

**If mentioned, ask:**

```
What monitoring do you need?

- Metrics to track
- Logging requirements
- Distributed tracing
```

**Then load:**
→ `skills/camel-flow/guides/monitoring.md`
→ Show correlation IDs, metrics, and health checks

**If NOT mentioned:**
→ Use standard monitoring approach

---

### Question 9: Configuration Summary

```
Configuration properties needed:

Based on our discussion:
- Source: {component} connection details
- Sink: {component} connection details
- Processing: [list parameters]
- Error handling: DLQ endpoint, retry config
[+ Circuit breaker thresholds — if Q5b selected]
[+ Idempotent repository config — if Q5c selected]
[+ Transaction manager ref — if Q5d selected]
[+ Performance config — if Q6 triggered]
[+ Security credentials — if Q7 triggered]
[+ Monitoring config — if Q8 triggered]

Any additional properties? (specify or say "no")
```

---

## Constitution Gate Check

Before creating TDD, verify design:

```
Checking against constitution...

✓ Route Structure: Single responsibility
✓ Configuration: Externalized to properties
✓ Error Handling: Dead Letter Channel configured
✓ Security: No hardcoded credentials
[+ Performance if applicable]
[+ Compliance if applicable]
```

---

## Generate TDD

Create `docs/flows/{flow-name}/{flow-name}.tdd.md`:

**Core Sections (always include):**
1. Overview (business context, technical summary)
2. Source System (component, URI, config)
3. Processing Steps (EIPs, transformations)
4. Sink System (component, URI, config)
5. Error Handling (strategy, DLQ, retries)

**Conditional Sections (include only if the corresponding question was answered affirmatively):**
6. Resilience / Circuit Breaker (only if Q5b selected)
7. Idempotent Consumer (only if Q5c selected)
8. Transactions (only if Q5d selected)
9. Performance & Reliability (only if Q6 triggered)
10. Security (only if Q7 triggered)
11. Monitoring & Observability (only if Q8 triggered)

**Always include:**
12. Sequence Diagram
13. Configuration Properties
14. Dependencies
15. Constitution Gate Checks
16. Testing Strategy (high-level test scenarios — `/camel-test` reads this as input)
17. Implementation Checklist

For TDD templates, use minimal structure unless specific requirements need detailed sections.

---

## Summary and Save

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TECHNICAL DESIGN SUMMARY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Flow: {flow-name}

Source: [component]:{{source.endpoint}}
Processing: [list EIPs]
Sink: [component]:{{sink.endpoint}}
Error: [strategy] → {{dlq.endpoint}}
[Performance: {throughput} msg/sec, {latency}ms - if applicable]
[Security: {methods} - if applicable]
[Monitoring: {approach} - if applicable]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Save this TDD? (yes/no)
```

If confirmed:

```
✅ TDD saved to docs/flows/{flow-name}/{flow-name}.tdd.md

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
NEXT STEPS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Review TDD and get stakeholder approval

2. When ready to implement:
   /camel-implement {flow-name}

3. After implementation, validate:
   /camel-validate {flow-name}

4. Generate tests:
   /camel-test {flow-name}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Error Handling

### Missing BRD
```
❌ ERROR: Business Requirements Document not found

Run: /camel-project
```

### Flow Not in BRD
```
⚠️ WARNING: Flow '{flow-name}' not in BRD

Continue anyway? (yes/no)
```

---
name: camel-flow
description: Design integration flows when user wants to create TDD, define data flow, specify source and sink systems, plan transformations, or architect message routing
user-invocable: true
metadata:
  version: "2.0.0"
  author: "camel-kit"
  category: "integration"
  license: "Apache-2.0"
---

# Camel Flow - Orchestrator

You are an orchestrator designing an integration flow. You conduct an interactive interview with the user to gather requirements, then dispatch sub-agents for computational steps (component lookup, transformation design, TDD assembly).

## Parameters

```
/camel-flow <flow-name>
```

Example: `/camel-flow order-to-warehouse`

## Context Loading (do this first)

**ALWAYS read at the start:**
1. `docs/business-requirements.md` - Business context (REQUIRED)
2. `docs/constitution.md` - Best practices. If missing, copy from `templates/constitution.md` and continue.
3. `.camel-kit/config.yaml` - **REQUIRED** - extract `project.camelVersion` as `CAMEL_VERSION` and `project.runtime` as `RUNTIME`. If the file does not exist, ask the user for the Camel version before proceeding.

## Check for Existing TDD

Check if `docs/flows/{flow-name}/{flow-name}.tdd.md` exists.

If exists, ask:
1. Update existing TDD
2. Start fresh
3. Review and continue to implementation

## Interview (conversational - stay in main context)

Ask **ONE question at a time**. Wait for response before proceeding.

### Introduction

Based on the Business Requirements Document:
- Flow: {flow-name}
- Purpose: [extract from BRD if available]

### Step 0 - Target Runtime

If `project.runtime` is not set in `.camel-kit/config.yaml`, ask:
- (a) Camel JBang (lightweight) - recommended for prototyping
- (b) Spring Boot (Maven layout)
- (c) Quarkus (Maven layout)

Store answer in `.camel-kit/config.yaml` as `project.runtime`. If already set, skip.

### Question 1: Flow Intent and Data

Ask: What data does this flow process, and what is the goal?
- Data type (e.g., "Order events", "Customer records")
- Format (e.g., JSON, XML, CSV)
- Goal (e.g., "Store in database", "Send to queue")

After response: note the data format for **Step A (component/format lookup)**.

### Question 2: Source System

Ask: Where does the data come from?
- System name (e.g., "Shopify", "Kafka topic")
- Technology (e.g., "Kafka", "REST API", "File")
- Trigger (e.g., "New messages", "Polling every 5s")

After response: note source system details for **Step A (component selection)**.

### Question 3: Transformations

Ask: What transformations or business rules are needed?
- Parse JSON/XML, Validate fields, Filter messages, Enrich with data
- Transform/map message format, Route by condition, or None

If "none"/passthrough: skip Question 3a, proceed to Question 4.
If transformations needed: note for **Step B (EIP lookup)** and **Step C (datamapper interview)** if field mapping is involved.

### Question 3a: Data Transformation (Conditional)

ONLY if user mentioned data transformation/field mapping AND format pair is XML/JSON combinations (XML->XML, JSON->JSON, JSON->XML, XML->JSON).
Note for **Step C (datamapper interview)**.

### Question 4: Sink System

Ask: Where should the processed data go?
- System name (e.g., "PostgreSQL", "Fulfillment queue")
- Technology (e.g., "SQL", "Kafka", "HTTP POST")
- Action (e.g., "INSERT INTO", "POST to API")

After response: note sink system details for **Step A (component selection)**.

### Question 4a: Multi-Path Routing (Conditional)

ONLY if user indicated multiple destinations or conditional routing.
Ask about routing conditions and each destination.

### Question 5: Error Handling

Ask: What should happen when errors occur?
1. Dead Letter Channel - Failed messages to error queue
2. Retry with backoff - Retry N times, then DLQ
3. Log and continue - Log error, keep processing
4. Stop route - Halt on error

Suggest DLQ with retry policy: maximumRedeliveries=3, redeliveryDelay=1000ms, backOffMultiplier=2, useExponentialBackOff=true.

Resilience sub-questions (conditional):
- If source/sink involves external HTTP/REST API -> circuit breaker (Step D)
- If source is a message broker or user mentioned deduplication -> idempotent consumer (Step D)
- If flow writes to more than one external system -> transactions (Step D)

### Mid-Interview Checkpoint

Save draft to `docs/flows/{flow-name}/{flow-name}.tdd.draft.md` with data collected so far. If this file already exists when starting, offer to resume.

### Question 6: Performance (Conditional)

ONLY if user mentioned high volume, real-time, latency, Kubernetes, scale.
Ask about throughput, latency target, deployment target. Note for **Step E (performance guide)**.

### Question 7: Security (Conditional)

ONLY if user mentioned security, PII, compliance, credentials.
Ask about auth method, sensitive fields, compliance. Note for **Step F (security guide)**.

### Question 8: Monitoring (Conditional)

ONLY if user mentioned monitoring, metrics, logging, tracing.
Ask about metrics, logging, distributed tracing. Note for **Step G (monitoring guide)**.

### Question 9: Configuration Summary

Summarize all configuration properties needed based on interview answers. Ask if any additional properties are needed.

## Computational Steps (dispatch to sub-agents)

After the interview, dispatch sub-agents for the heavy lifting. Create `docs/flows/{flow-name}/.steps/` for intermediate outputs.

### Guide Manifest

| Step | Guide | Shared Guide | ~Tokens | When |
|------|-------|-------------|---------|------|
| A | guides/component-selection.md | shared/mcp-setup.md | 2.1K | Always |
| A | guides/catalog-lookups.md | shared/mcp-setup.md | 2.4K | Always |
| A | guides/data-formats.md | - | 0.6K | Format choice unclear |
| B | guides/eip-catalog.md | - | 2.3K | Transformations needed |
| B | guides/integration-patterns.md | - | 1K | Complex patterns |
| C | guides/datamapper-interview.md | shared/datamapper-canonicalize.md | 4.9K | Field mapping needed |
| D | guides/resilience-interview.md | - | 0.8K | Circuit breaker/idempotent/tx needed |
| E | guides/performance.md | - | 1.2K | Performance requirements |
| F | guides/security.md | - | 1.2K | Security requirements |
| G | guides/monitoring.md | - | 1.2K | Monitoring requirements |
| H | guides/tdd-assembly.md | - | 1.5K | Always (final step) |

### Context Passing

Include in each sub-agent prompt:
- Flow name: {flow-name}
- Camel version: from config.yaml
- Runtime: from config.yaml
- User answers relevant to this step
- File paths of prior step outputs in `.steps/` (let sub-agent read them)

### Final Assembly

After all computational steps complete, dispatch the tdd-assembly sub-agent (Step H) with all `.steps/` outputs to produce the final `docs/flows/{flow-name}/{flow-name}.tdd.md`.

Delete draft file if it exists.

## Summary and Next Steps

After TDD is saved, display:

```
TDD saved to docs/flows/{flow-name}/{flow-name}.tdd.md

Next steps:
1. Review TDD and get stakeholder approval
2. /camel-implement {flow-name}
3. /camel-validate {flow-name}
4. /camel-test {flow-name}
```

## Error Handling

- Missing BRD: suggest `/camel-project`
- Flow not in BRD: warn and ask to continue

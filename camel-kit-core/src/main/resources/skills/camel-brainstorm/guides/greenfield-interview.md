# Greenfield Interview Guide

> **Context:** Loaded by `camel-brainstorm` when the project type is greenfield.
> **Purpose:** Socratic interview to understand business needs, systems, flows, and constraints.
> **Output:** Structured answers ready for `design-assembly.md` to produce the design spec.

---

## Interview Rules

1. Ask **ONE question at a time**. Wait for the user's response before proceeding.
2. Prefer **multiple choice** when possible, open-ended when necessary.
3. **Listen** — extract all information from each answer, don't re-ask what's already been said.
4. If the user's initial request contains enough detail to answer some questions, pre-fill and confirm rather than re-asking.
5. Never skip questions. Mark conditional questions as "N/A" if the condition isn't met.

---

## Question 1: Project Name

```
What is the name of this integration project?

This should be a clear, descriptive name (e.g., "E-Commerce Order Fulfillment Integration").
```

Record: `project.name`

---

## Question 2: Business Purpose

```
What is the business purpose of this integration?

- What business problem does it solve?
- What business value does it deliver?
- Who are the primary beneficiaries?

Example: "Automate order fulfillment by connecting the e-commerce platform to the warehouse,
reducing manual data entry and enabling same-day processing."
```

If the purpose is vague, probe:
```
Can you tell me:
- What manual process or problem exists today?
- What will be different/better after this integration?
- Who will benefit from this change?
```

Record: `project.purpose`, `project.value`, `project.stakeholders`

---

## Question 3: Systems Landscape

```
What systems or applications need to be integrated?

Just list the system names and types — we'll define how they connect next.

Example:
- E-commerce Platform (Shopify)
- Warehouse Management System (SAP)
- Customer Database (PostgreSQL)
- Email Service (SendGrid)
```

Record: `systems[]` with name, type, role (source/target/both)

---

## Question 4: Integration Goals and Flow Naming

```
What integrations do you need to build?

For each integration, describe:
- What data needs to move
- Why it needs to move
- What should happen with it

Example:
- "New orders need to reach the warehouse for fulfillment"
- "Order status updates need to sync back to e-commerce"
- "Customers should receive email confirmations when orders ship"
```

After receiving goals, suggest flow names using kebab-case (`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`):

```
Based on your requirements, I suggest these flows:

1. order-to-warehouse: New orders reach the warehouse for fulfillment
2. warehouse-status-sync: Order status updates sync back to e-commerce
3. shipment-notification: Email confirmations sent when orders ship

Do these flow names work? (yes/modify)
```

If user provides names with spaces, uppercase, or special characters — auto-correct and confirm.

Record: `flows[]` with name, description, source_system, target_system

---

## Per-Flow Deep Dive

For EACH flow identified, ask the following questions in sequence. This replaces the old `/camel-flow` interview — all flows are designed in a single brainstorm session.

### Question 5: Flow Intent and Data (per flow)

```
For flow "[flow-name]":

What data does this flow process, and what is the goal?
- Data type (e.g., "Order events", "Customer records")
- Format (e.g., JSON, XML, CSV)
- Goal (e.g., "Store in database", "Send to queue")
```

Record: `flow.dataType`, `flow.dataFormat`, `flow.goal`

### Question 6: Source System (per flow)

```
For flow "[flow-name]":

Where does the data come from?
- System name (e.g., "Shopify", "Kafka topic")
- Technology (e.g., Kafka, REST API, File, Database)
- Trigger (e.g., "New messages on topic", "Polling every 5s", "HTTP POST")
```

After response: note source for MCP component verification.

Record: `flow.source.system`, `flow.source.technology`, `flow.source.trigger`

### Question 7: Transformations (per flow)

```
For flow "[flow-name]":

What transformations or business rules are needed?

a) Parse JSON/XML
b) Validate fields
c) Filter messages
d) Enrich with data from another system
e) Transform/map message format (field mapping)
f) Route by condition (different destinations)
g) None — passthrough

Select all that apply.
```

If user selects (e) and format pair is XML/JSON combinations: note for DataMapper interview.
If user selects (f): note for multi-path routing question.
If "none"/passthrough: skip Question 7a.

Record: `flow.transformations[]`

### Question 7a: Data Transformation Detail (conditional)

ONLY if field mapping needed AND format pair is XML/JSON combinations.

```
For the data transformation in "[flow-name]":

I'll need to understand the field mappings. Can you describe:
- Source fields and their paths
- Target fields and their paths
- Any transformation logic (concatenation, formatting, default values)

Or if you have example source and target messages, share those.
```

Load `camel-design/guides/datamapper-interview.md` and `shared/datamapper-canonicalize.md` for detailed field mapping.

Record: `flow.datamapper.mappings[]`

### Question 8: Sink System (per flow)

```
For flow "[flow-name]":

Where should the processed data go?
- System name (e.g., "PostgreSQL", "Fulfillment queue")
- Technology (e.g., SQL, Kafka, HTTP POST)
- Action (e.g., "INSERT INTO orders", "POST to /api/fulfillment")
```

After response: note sink for MCP component verification.

Record: `flow.sink.system`, `flow.sink.technology`, `flow.sink.action`

### Question 8a: Multi-Path Routing (conditional)

ONLY if user indicated multiple destinations or conditional routing.

```
For the conditional routing in "[flow-name]":

What are the routing conditions?
- Condition 1: [condition] → [destination]
- Condition 2: [condition] → [destination]
- Default: [destination]
```

Record: `flow.routing.conditions[]`

### Question 9: Error Handling (per flow)

```
For flow "[flow-name]":

What should happen when errors occur?

a) Dead Letter Channel — failed messages sent to error queue
b) Retry with backoff — retry N times, then DLQ
c) Log and continue — log error, keep processing
d) Stop route — halt on error

Recommended: (b) with maximumRedeliveries=3, redeliveryDelay=1000ms,
backOffMultiplier=2, useExponentialBackOff=true
```

Resilience sub-questions (ask if applicable):
- Source/sink involves external HTTP/REST API → circuit breaker?
- Source is message broker or user mentioned deduplication → idempotent consumer?
- Flow writes to multiple external systems → transactions?

If any resilience patterns needed, load `camel-design/guides/resilience-interview.md`.

Record: `flow.errorHandling`, `flow.resilience`

---

## Cross-Cutting Questions (after all flows)

### Question 10: Performance (conditional)

ONLY if user mentioned high volume, real-time, latency, Kubernetes, scale.

```
You mentioned performance requirements. Can you specify:
- Expected throughput (messages/second or messages/minute)
- Latency target (end-to-end processing time)
- Deployment target (local, VM, Kubernetes)
```

Record: `project.performance`

### Question 11: Security (conditional)

ONLY if user mentioned security, PII, compliance, credentials.

```
You mentioned security/compliance requirements. Can you specify:
- Authentication method (OAuth2, API keys, certificates)
- Sensitive fields to protect (PII, financial data)
- Compliance standards (GDPR, SOC 2, HIPAA)
```

Record: `project.security`

### Question 12: Monitoring (conditional)

ONLY if user mentioned monitoring, metrics, logging, tracing.

```
You mentioned monitoring requirements. Can you specify:
- Metrics platform (Prometheus, Micrometer, custom)
- Logging requirements (structured logging, log levels)
- Distributed tracing (OpenTelemetry, Jaeger)
```

Record: `project.monitoring`

### Question 13: Constraints

```
Are there any other constraints or requirements we haven't covered?

- Technology preferences (e.g., "must use Kafka for messaging")
- Team constraints (e.g., "no Java code, YAML only")
- Timeline requirements
- Or type "none" to use standard Apache Camel best practices.
```

Record: `project.constraints`

---

## MCP Component Verification

After the interview, for EACH flow, verify all source and sink components via MCP catalog:

1. Call `camel_catalog_component` for each source/sink technology
2. Confirm the component exists and note exact option names
3. Call `camel_rh_build_component_info` to check Red Hat support status
4. If a component is not supported or Technology Preview, present alternatives

This verification happens BEFORE assembling the design spec. Failed verification = the design spec is wrong.

---

## Handoff

After the interview and component verification, proceed to:
1. `guides/version-selection.md` — select Red Hat Camel version
2. `guides/design-assembly.md` — assemble the design spec from interview answers

Pass all recorded data to the assembly guide.

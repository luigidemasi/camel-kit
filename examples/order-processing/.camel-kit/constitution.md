# Camel-Kit Constitution

> This constitution establishes the core principles and best practices for designing Apache Camel integrations. It guides all route design decisions and is enforced during validation.

---

## Document Control

| Property | Value |
|----------|-------|
| Version | 1.0 |
| Last Updated | {{DATE}} |
| Camel Version | {{CAMEL_VERSION}} |

---

## Core Principles

> These principles reflect Apache Camel best practices and integration design patterns. They are **non-negotiable** defaults that ensure maintainable, reliable integrations.

### 1. Route Structure

Every route MUST have a **Source** and a **Sink**. Processing steps are optional.

**Guidance:**
- **Source (REQUIRED)**: Where data originates — the `from:` endpoint
- **Sink (REQUIRED)**: Where data is delivered — the final `to:` endpoint
- **Processing Steps (OPTIONAL)**: Transformations between source and sink

**Pattern:**
```
Source → [Processing Steps] → Sink

Examples:
  kafka:orders → jpa:Order                           # Direct passthrough
  kafka:orders → unmarshal → filter → jpa:Order      # With processing
  file:input → split → transform → kafka:output      # Complex processing
```

**Exception - Internal Routes:**
Routes starting with `direct:` or `seda:` may omit an external sink if they are sub-routes called by other routes.

```
# Main route (has external source and sink)
kafka:orders → direct:process → jpa:Order

# Sub-route (internal, no external sink required)
direct:process → validate → enrich → [returns to caller]
```

**Enforcement:**
- Routes without a source: **ERROR** (validation fails)
- Routes without a sink (non-internal): **ERROR** (validation fails)
- Routes with no processing steps: **WARNING** (pass-through routes should be intentional)

---

### 2. Single Responsibility

Each route MUST have a clear, single responsibility.

**Guidance:**
- One route = one purpose
- If a route description requires "and", consider splitting
- A route should be explainable in one sentence

**Example:**
```
✅ "Consume orders from Kafka and persist to database"
❌ "Consume orders, validate, enrich with customer data, check inventory,
    apply discounts, persist, send confirmation email, and update analytics"
```

**Enforcement:** Routes with more than 7 processing steps trigger a review warning.

---

### 3. Separation of Concerns

Decompose complex integrations into discrete, composable routes.

**Guidance:**
- Use `direct:` for synchronous internal routing
- Use `seda:` for asynchronous internal routing with backpressure
- Follow the pattern: Ingestion → Processing → Delivery
- Keep business logic in beans, integration logic in routes

**Pattern:**
```
[External Source] → route:ingestion → direct:process → direct:deliver → [External Sink]
```

**Enforcement:** External calls from within deep processing chains trigger warnings.

---

### 4. Error Handling is Mandatory

Every route MUST declare an explicit error handling strategy.

**Guidance:**
- Never rely on silent failures
- Choose the appropriate handler for your use case:
  - `deadLetterChannel`: For preserving failed messages for analysis/retry
  - `defaultErrorHandler`: For propagating errors to callers
  - `onException`: For type-specific handling
- Combine `errorHandler` with `onException` for robust strategies

**Options:**

| Strategy | Use When |
|----------|----------|
| Dead Letter Channel | Messages must not be lost; manual intervention needed |
| Retry with Backoff | Transient failures are expected (network, locks) |
| Circuit Breaker | External service may be unavailable |
| Custom onException | Different exceptions need different handling |

**Enforcement:** Routes without error handling fail validation.

---

### 5. Retry Policy Best Practices

Configure retries appropriately for the failure type.

**Guidance:**
- **Short retry**: For transient issues (network glitches, brief locks)
  - Max 3-5 retries, delays under 5 seconds
  - Camel retries only the failed endpoint, not the whole route
  - Thread is blocked during retry — keep delays short
- **Long retry**: Use external scheduler or dead letter reprocessing
  - Don't block threads for minutes waiting to retry
- **Exponential backoff**: Use `backOffMultiplier` to avoid thundering herd
- **Collision avoidance**: Enable for high-throughput scenarios

**Default Policy:**
```yaml
maximumRedeliveries: 3
redeliveryDelay: 1000      # 1 second
backOffMultiplier: 2       # 1s, 2s, 4s
useExponentialBackOff: true
```

**Enforcement:** Retry delays over 30 seconds trigger a warning.

---

### 6. Resilience for External Calls

Protect routes from external service failures.

**Guidance:**
- Wrap external HTTP/REST calls with Circuit Breaker
- Configure appropriate thresholds:
  - `failureRateThreshold`: Percentage to trip circuit (default: 50%)
  - `waitDurationInOpenState`: Cool-down before retry (default: 10s)
- Provide fallback behavior when circuit is open
- Consider bulkhead isolation for independent external services

**Enforcement:** External service calls without resilience patterns trigger warnings.

---

### 7. Idempotent Processing

Design routes to handle duplicate messages safely.

**Guidance:**
- Use Idempotent Consumer EIP for exactly-once semantics
- Choose appropriate repository for your environment:
  - `MemoryIdempotentRepository`: Development/testing only
  - `JpaMessageIdRepository`: Single-node production
  - `HazelcastIdempotentRepository`: Clustered environments
  - `InfinispanIdempotentRepository`: Distributed data grid
- Define meaningful message IDs (business keys preferred over technical IDs)

**Enforcement:** High-volume routes without idempotency configured trigger warnings.

---

### 8. Data Format Discipline

Be explicit about data serialization at system boundaries.

**Guidance:**
- Always `unmarshal` at route entry, `marshal` at route exit
- Validate schemas at ingestion (fail fast)
- Use typed objects internally, raw formats only at boundaries
- Document data contracts in route specifications

**Pattern:**
```
from(external)
  → unmarshal (JSON/XML/Avro)
  → validate (optional but recommended)
  → process (work with typed objects)
  → marshal (if needed)
  → to(external)
```

**Enforcement:** Routes consuming structured data without unmarshal trigger warnings.

---

### 9. Naming Conventions

Use consistent, meaningful names throughout.

**Route IDs:**
```
<domain>-<action>[-<qualifier>]

Examples:
  order-ingestion
  order-validation
  customer-enrichment
  inventory-lookup
```

**Endpoints:**
```
direct:<route-id>           # Matches the route it invokes
seda:<domain>-<purpose>     # Describes the queue purpose
```

**Headers and Properties:**
```
CamelCase for Camel headers: CamelFileName, CamelHttpMethod
kebab-case for custom: order-id, correlation-id, retry-count
```

**Enforcement:** Route IDs not matching convention trigger warnings.

---

### 10. Observability by Design

Build routes that can be monitored and debugged.

**Guidance:**
- Assign meaningful `routeId` to every route
- Add `description` to document intent
- Use correlation IDs across routes (propagate via headers)
- Log at decision points, not every step
- Leverage Camel's built-in metrics and tracing

**Standard Headers:**
```
X-Correlation-ID: Trace requests across routes and systems
X-Source-System: Identify message origin
X-Timestamp: Track timing through pipeline
```

**Enforcement:** Routes without IDs fail validation.

---

### 11. External Configuration

Parameterize application configuration and externalize it from the deployment archive.

**Guidance:**
- Never hardcode connection strings, credentials, or environment-specific values
- Use `{{PLACEHOLDER}}` syntax for environment variables
- Follow configuration hierarchy (highest priority first):
  1. Environment variables: `KAFKA_BROKERS`
  2. System properties: `-Dkafka.brokers=...`
  3. External file: `application.properties`
  4. Bundled defaults: `application-default.properties`

**Pattern:**
```yaml
from:
  uri: kafka:{{kafka.topic.orders}}
  parameters:
    brokers: "{{kafka.brokers}}"
    groupId: "{{kafka.consumer.group}}"
```

**Best Practices:**
- Document all required configuration in `.env.example`
- Use profiles for environment-specific config
- Validate configuration at startup (fail fast)

**Enforcement:** Hardcoded connection strings or credentials fail validation.

---

### 12. Throttling and Backpressure

Control throughput to prevent overloading downstream systems and meet SLAs.

**Guidance:**
- Apply throttling when consuming from unbounded sources
- Choose appropriate strategy based on requirements:

| Strategy | Description | Use Case |
|----------|-------------|----------|
| **Reject** | Return error immediately | API endpoints with strict SLAs |
| **Block** | Wait until capacity available | Internal processing |
| **Delay** | Queue and process later | Batch operations |
| **Degrade** | Reduce functionality | Graceful degradation |

**Camel Support:**
- `throttle` EIP: Request-based throttling
- `ThrottlingInflightRoutePolicy`: Inflight message-based throttling
- `seda` with bounded queue: Queue-based backpressure

**Enforcement:** High-throughput routes without throttling configured trigger warnings.

---

### 13. VETRO Pattern

Follow the VETRO pattern for message processing.

**V**alidate → **E**nrich → **T**ransform → **R**oute → **O**perate

| Phase | Purpose | Example EIPs |
|-------|---------|--------------|
| Validate | Check message structure and content | Validate, Filter |
| Enrich | Add context from external sources | Enrich, PollEnrich |
| Transform | Convert to required format | Transform, Marshal |
| Route | Direct to appropriate destination | Choice, RecipientList |
| Operate | Execute the business action | To, Bean |

**Enforcement:** Informational only (pattern suggestion during design).

---

## Design Patterns Reference

> For comprehensive guidance on 20 Camel Design Patterns organized by category (Foundational, Error Handling, Deployment), see [`templates/design-patterns.md`](templates/design-patterns.md).

Key patterns include:
- **Foundational**: VETRO, Canonical Data Model, Edge Component, CQRS, Reusable Route
- **Error Handling**: Data Integrity, Saga, Idempotent Filter, Retry, Circuit Breaker, Error Channel
- **Deployment**: Service Instance, Singleton Service, Load Levelling, Parallel Pipeline, Bulkhead

---

## Project Customizations

> Extend or override the core principles below for project-specific needs. Customizations are documented here for team alignment.

### Allowed Components

<!-- Restrict to specific components if needed -->
<!-- Example: only kafka, http, jpa, file -->

```yaml
# allowedComponents:
#   - kafka
#   - http
#   - jpa
#   - file
#   - direct
#   - seda
```

### Naming Overrides

<!-- Override default naming conventions if needed -->
<!-- Example: prefix all routes with team name -->

```yaml
# routeIdPattern: "myteam-{domain}-{action}"
```

### Error Handling Overrides

<!-- Override default error handling requirements -->
<!-- Example: specific DLQ topics per domain -->

```yaml
# deadLetterPatterns:
#   order: kafka:order-dlq
#   payment: kafka:payment-dlq
#   default: kafka:integration-dlq
```

### Security Requirements

<!-- Define security requirements for this project -->

```yaml
# security:
#   requireTLS: true
#   sensitiveHeaders:
#     - Authorization
#     - X-API-Key
#   secretsPattern: "{{ENV_VAR}}"  # Never hardcode secrets
```

### Performance Requirements

<!-- Define performance constraints -->

```yaml
# performance:
#   maxRouteSteps: 10           # Override default of 7
#   requireIdempotency: true    # Force idempotent consumer
#   requireCircuitBreaker: true # Force circuit breaker on all external calls
```

### Additional Patterns

<!-- Define project-specific patterns -->

---

## Governance

### Constitution Authority

This constitution is the primary authority for design decisions. When in conflict with other documentation or practices, this constitution takes precedence.

### Amendments

To amend this constitution:
1. Document the proposed change
2. Explain the rationale
3. Update this file with the new guidance
4. Re-run `/camel.validate` to verify existing routes

### Validation

All routes MUST pass validation before generation:
- `/camel.validate` checks compliance with this constitution
- Errors block generation
- Warnings are advisory but should be addressed

---

## References

- [Camel Design Patterns](https://leanpub.com/camel-design-patterns) by Bilgin Ibryam
- [Apache Camel Error Handler](https://camel.apache.org/manual/error-handler.html)
- [Dead Letter Channel EIP](https://camel.apache.org/components/4.14.x/eips/dead-letter-channel.html)
- [Idempotent Consumer EIP](https://camel.apache.org/components/4.14.x/eips/idempotentConsumer-eip.html)
- [Route Configuration](https://camel.apache.org/manual/route-configuration.html)
- [Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/)
- [Release It!](https://pragprog.com/titles/mnee2/release-it-second-edition/) by Michael T. Nygard

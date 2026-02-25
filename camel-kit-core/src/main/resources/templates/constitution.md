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
- Choose the appropriate approach based on your use case

**Exception Handling Approaches:**

| Approach | Scope | Use When |
|----------|-------|----------|
| `doTry/doCatch/doFinally` | Inline, specific code block | Fine-grained control within a route; handle exceptions locally |
| `errorHandler` | Route or context level | Default handling for all unhandled exceptions |
| `onException` | Route or context level | Type-specific handling; different exceptions need different responses |

**doTry/doCatch/doFinally:**
- Similar to Java try/catch/finally
- Handles exceptions inline within the route
- Use when you need localized exception handling
- `doFinally` always executes regardless of success/failure

```yaml
steps:
  - doTry:
      steps:
        - to: http:external-service
    doCatch:
      - exception: java.net.ConnectException
        steps:
          - log: "Connection failed, using fallback"
          - to: direct:fallback
    doFinally:
      steps:
        - to: direct:cleanup
```

**Error Handler Types:**

| Type | Behavior | Use When |
|------|----------|----------|
| `noErrorHandler` | Exceptions propagate to caller | Caller handles errors; simple routes |
| `defaultErrorHandler` | Default Camel behavior with optional retry | Standard processing; transient failures |
| `deadLetterChannel` | Failed messages sent to DLQ | Messages must not be lost; manual intervention needed |

**onException Clause:**
- Catches specific exception types
- Configure handling behavior with:
  - `handled(true)`: Exception is handled; route completes normally
  - `continued(true)`: Exception is handled; continue processing from next step
  - `markRollbackOnly()`: Mark transaction for rollback (when using transactions)

```yaml
- onException:
    exception: com.example.ValidationException
    handled: true
    steps:
      - to: kafka:invalid-messages
- onException:
    exception: java.sql.SQLException
    handled: true
    steps:
      - log: "Database error: ${exception.message}"
      - to: kafka:db-errors
```

**Best Practice:** Combine `errorHandler` with `onException` for robust strategies — use `errorHandler` as the catch-all and `onException` for specific exception types.

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

**Enforcement:** Informational only — apply when explicitly requested during flow design.

---

### 7. Transaction Handling

Use transactions to ensure data consistency across multiple operations.

**Guidance:**
- Use `.transacted()` DSL to enable transaction management
- Choose the appropriate propagation policy for your use case
- Transactions are essential when writing to multiple resources (database + message broker)

**Transaction Propagation Policies:**

| Policy | Behavior |
|--------|----------|
| `PROPAGATION_REQUIRED` | Join existing transaction or create new one (default) |
| `PROPAGATION_REQUIRES_NEW` | Always create a new transaction |
| `PROPAGATION_MANDATORY` | Must run within existing transaction; throws exception otherwise |
| `PROPAGATION_SUPPORTS` | Use transaction if available; run without if not |
| `PROPAGATION_NOT_SUPPORTED` | Execute without transaction; suspend if one exists |
| `PROPAGATION_NEVER` | Execute without transaction; throw exception if one exists |

**Pattern:**
```yaml
- route:
    id: transacted-order-processing
    from:
      uri: jms:queue:orders
    steps:
      - transacted:
          ref: PROPAGATION_REQUIRED
      - to:
          uri: jpa:Order
      - to:
          uri: jms:queue:notifications
```

**Transaction vs Exception Handling:**
- Use `markRollbackOnly()` in `onException` to rollback transaction on specific exceptions
- Combine with Dead Letter Channel to preserve failed messages after rollback

```yaml
- onException:
    exception: com.example.BusinessException
    handled: true
    markRollbackOnly: true
    steps:
      - to: kafka:failed-orders
```

**Enforcement:** Informational only — apply when explicitly requested during flow design.

---

### 8. Idempotent Processing

Design routes to handle duplicate messages safely.

**Guidance:**
- Use Idempotent Consumer EIP for exactly-once semantics
- Choose appropriate repository for your environment:
  - `MemoryIdempotentRepository`: Development/testing only
  - `JpaMessageIdRepository`: Single-node production
  - `HazelcastIdempotentRepository`: Clustered environments
  - `InfinispanIdempotentRepository`: Distributed data grid
- Define meaningful message IDs (business keys preferred over technical IDs)

**Enforcement:** Informational only — apply when explicitly requested during flow design.

---

### 9. Data Format Discipline

Be intentional about data serialization — unmarshal when needed, not by default.

**Guidance:**
- **Unmarshal when** you need to work with data as typed objects (field access, complex transformations, bean processing)
- **Skip unmarshal when** routing/forwarding without inspection, or using expression languages (JSONPath, XPath) that work on raw data
- **Schema validation before unmarshal** — JSON Schema, XSD validation works on raw data
- **Bean validation after unmarshal** — JSR-380 (Hibernate Validator) works on Java objects
- Document data contracts in route specifications

**Validation Order:**

| Validation Type | When | Works On | Camel Component |
|-----------------|------|----------|-----------------|
| JSON Schema | Before unmarshal | Raw JSON string | `json-validator` |
| XML Schema (XSD) | Before unmarshal | Raw XML string | `validator` |
| Bean Validation | After unmarshal | Java object | `bean-validator` |

**When to Unmarshal:**

| Scenario | Unmarshal? | Why |
|----------|------------|-----|
| Complex bean processing | Yes | Need typed object access |
| Field-level transformations | Yes | Easier with POJOs |
| Simple filtering with JSONPath | No | JSONPath works on raw JSON |
| Pass-through routing | No | Unnecessary overhead |
| Content-based routing on headers | No | Body not inspected |

**Pattern (with schema validation and unmarshalling):**
```
from(external)
  → validate (JSON Schema/XSD - on raw data)
  → unmarshal (JSON/XML/Avro)
  → bean-validate (optional - on Java object)
  → process (work with typed objects)
  → marshal (if sink expects specific format)
  → to(external)
```

**Pattern (when raw data suffices):**
```
from(external)
  → validate (JSON Schema/XSD - optional)
  → filter (using jsonpath or xpath)
  → to(external)
```

**Enforcement:** Informational only — unmarshal/marshal decisions depend on processing needs.

---

### 10. Naming Conventions

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

### 11. Observability by Design

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

### 12. External Configuration

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

### 13. Throttling and Backpressure

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

### 14. VETRO Pattern

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

### 15. Kafka Consumer Scaling

Configure Kafka consumers appropriately for parallel processing and high throughput.

**Guidance:**
- Understand the relationship between consumers and partitions
- Scale consumers to match partition count for optimal throughput
- Avoid creating more consumers than partitions (starving consumers)

**Consumer Scaling Parameters:**

| Parameter | Description |
|-----------|-------------|
| `consumersCount` | Number of consumer threads in a single Camel instance |
| `consumersCount` with replicas | Total consumers = consumersCount × pod replicas |

**Partition Assignment Rules:**
- Each partition is assigned to exactly one consumer in a consumer group
- If consumers > partitions: some consumers will be idle (starving)
- If consumers < partitions: some consumers handle multiple partitions
- If consumers = partitions: optimal parallel processing

**Pattern:**
```yaml
from:
  uri: kafka:{{kafka.topic}}
  parameters:
    brokers: "{{kafka.brokers}}"
    groupId: "{{kafka.consumer.group}}"
    consumersCount: 3  # Match to partition count or expected replicas
    autoOffsetReset: earliest
```

**Scaling Considerations:**
- When running multiple replicas in Kubernetes, total consumers = `consumersCount × replicas`
- Example: 3 replicas with consumersCount=2 = 6 total consumers
- If topic has only 4 partitions, 2 consumers will be idle
- Use `consumersCount: 1` per pod and scale via Kubernetes replicas for flexibility

**Enforcement:** Informational only (configuration depends on deployment topology).

---

### 16. Kubernetes Deployment

Design routes for cloud-native deployment on Kubernetes.

**Guidance:**
- Externalize all configuration using environment variables or ConfigMaps
- Implement health checks for liveness and readiness probes
- Configure resource requests and limits appropriately
- Use Secrets for sensitive configuration

**Configuration Hierarchy:**
1. Environment variables (highest priority)
2. System properties (`-D`)
3. ConfigMap-mounted `application.properties`
4. Bundled defaults

**ConfigMap Pattern:**
```yaml
# Kubernetes ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: camel-config
data:
  application.properties: |
    kafka.brokers=kafka:9092
    kafka.topic=orders
    kafka.consumer.group=order-processors
```

**Secrets for Sensitive Data:**
```yaml
# Kubernetes Secret
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials
type: Opaque
stringData:
  db.username: admin
  db.password: secret
```

**Health Probes:**

| Probe | Purpose | Camel Support |
|-------|---------|---------------|
| **Readiness** | Is the app ready to receive traffic? | Camel Health Check API |
| **Liveness** | Is the app still running correctly? | Camel Health Check API |
| **Startup** | Has the app finished starting? | Camel Startup Check |

```yaml
# Kubernetes Deployment probe configuration
livenessProbe:
  httpGet:
    path: /q/health/live
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /q/health/ready
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 10
```

**Resource Configuration:**
```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "250m"
  limits:
    memory: "512Mi"
    cpu: "500m"
```

**Best Practices:**
- Never hardcode hostnames, ports, or credentials
- Use property placeholders: `{{property.name}}`
- Implement graceful shutdown handling
- Monitor route health via metrics endpoints

**Enforcement:** Routes with hardcoded Kubernetes-specific values trigger warnings.

---

## Design Patterns Reference

> For comprehensive guidance on 20 Camel Design Patterns organized by category (Foundational, Error Handling, Deployment), see [`templates/design-patterns.md`](design-patterns.md).

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

- [Apache Camel Error Handler](https://camel.apache.org/manual/error-handler.html)
- [Dead Letter Channel EIP](https://camel.apache.org/components/4.14.x/eips/dead-letter-channel.html)
- [Idempotent Consumer EIP](https://camel.apache.org/components/4.14.x/eips/idempotentConsumer-eip.html)
- [Route Configuration](https://camel.apache.org/manual/route-configuration.html)
- [Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/)

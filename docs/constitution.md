# Camel-Kit Constitution

The constitution defines best practices that Camel-Kit enforces during integration design. These guidelines help create maintainable, resilient, and observable Camel routes.

## Table of Contents

- [Overview](#overview)
- [Core Principles](#core-principles)
- [Customization](#customization)
- [Validation Codes](#validation-codes)

---

## Overview

The constitution is stored in `.camel-kit/constitution.md` and is read by the AI assistant during route design. When you make design decisions, the assistant checks against these principles and provides guidance.

**Constitution enforcement levels:**

| Level | Description |
|-------|-------------|
| **Required** | Must be followed; blocks validation if violated |
| **Recommended** | Should be followed; generates warnings |
| **Optional** | Suggested patterns; informational only |

---

## Core Principles

### 1. Single Responsibility

**Level:** Required

Each route should have one clear purpose. If a route does too many things, split it into smaller routes connected via `direct:` or `seda:` endpoints.

**Good:**
```
order-ingestion: Kafka → Database
order-validation: direct:validate → REST API
order-notification: direct:notify → Email
```

**Bad:**
```
order-processing: Kafka → Validate → Database → REST → Email
```

---

### 2. Error Handling Mandatory

**Level:** Required

Every route must declare an error handling strategy. No route should silently swallow exceptions.

**Approaches:**

| Approach | Scope | Use When |
|----------|-------|----------|
| `doTry/doCatch/doFinally` | Inline | Fine-grained control within a route |
| `errorHandler` | Route/Context | Default handling for all exceptions |
| `onException` | Route/Context | Type-specific handling |

**Error Handler Types:**

| Type | Behavior |
|------|----------|
| `noErrorHandler` | Exceptions propagate to caller |
| `defaultErrorHandler` | Default Camel behavior with retry |
| `deadLetterChannel` | Failed messages sent to DLQ |

**Example - Error Handler:**
```yaml
errorHandler:
  deadLetterChannel:
    deadLetterUri: kafka:orders-dlq
    redeliveryPolicy:
      maximumRedeliveries: 3
      redeliveryDelay: 1000
```

**Example - doTry/doCatch:**
```yaml
- doTry:
    steps:
      - to: http:external-service
  doCatch:
    - exception: java.net.ConnectException
      steps:
        - to: direct:fallback
  doFinally:
    steps:
      - to: direct:cleanup
```

**Example - onException:**
```yaml
- onException:
    exception: com.example.ValidationException
    handled: true
    steps:
      - to: kafka:invalid-messages
```

---

### 3. Circuit Breaker for External Calls

**Level:** Required

Any call to an external service (REST API, database, third-party) must use a resilience pattern.

**Why:** External services can fail, be slow, or become unavailable. Without protection, your integration can:
- Exhaust resources waiting for timeouts
- Cascade failures to other systems
- Create retry storms

**Implementation:**
```yaml
- circuitBreaker:
    resilience4jConfiguration:
      failureRateThreshold: 50
      waitDurationInOpenState: 30000
    steps:
      - to:
          uri: http://external-service/api
    onFallback:
      - setBody:
          constant: '{"status": "fallback"}'
```

---

### 4. Idempotent Consumer

**Level:** Recommended

Consumer routes should handle duplicate messages gracefully using idempotent consumer pattern.

**When needed:**
- Kafka consumers (rebalancing can cause re-delivery)
- Any at-least-once delivery source
- Messages with business-critical side effects

**Implementation:**
```yaml
- idempotentConsumer:
    simple: "${header.messageId}"
    idempotentRepository: "#memoryIdempotentRepository"
    steps:
      - to: direct:process
```

---

### 5. Schema Validation at Boundaries

**Level:** Recommended

Validate data schemas at integration entry points. Don't trust external input.

**Entry points to validate:**
- Kafka consumers
- REST endpoints
- File ingestion
- Any external source

**Implementation:**
```yaml
- unmarshal:
    json:
      unmarshalType: com.example.Order
- to:
    uri: bean-validator:validate
```

---

### 6. Secrets Management

**Level:** Required

Never hardcode secrets, passwords, or API keys. Use environment variables or external secret stores.

**Good:**
```yaml
parameters:
  brokers: "{{KAFKA_BROKERS}}"
  password: "{{DB_PASSWORD}}"
```

**Bad:**
```yaml
parameters:
  brokers: "kafka-prod.example.com:9092"
  password: "mysecretpassword123"
```

---

### 7. Observability

**Level:** Recommended

Routes should support observability through:
- Correlation IDs for tracing
- Structured logging at key points
- Metrics for monitoring

**Correlation ID propagation:**
```yaml
- setHeader:
    name: correlationId
    simple: "${header.correlationId ?: ${exchangeId}}"
- log:
    message: "Processing order ${body.orderId} [${header.correlationId}]"
```

---

### 8. Naming Conventions

**Level:** Required

Route IDs must follow consistent naming:

**Pattern:** `<domain>-<action>`

**Examples:**
- `order-ingestion`
- `customer-enrichment`
- `payment-validation`
- `inventory-lookup`

**Rules:**
- Lowercase with hyphens
- Domain first, action second
- Descriptive but concise
- No abbreviations unless universally understood

---

### 9. Data Transformation Locality

**Level:** Recommended

Transform data as close to the source/sink as possible. Keep intermediate processing working with normalized internal formats.

**Flow:**
```
External Format → Unmarshal → Internal Model → Process → Marshal → External Format
```

---

### 10. Graceful Shutdown

**Level:** Recommended

Routes should support graceful shutdown, completing in-flight messages before stopping.

**Considerations:**
- Use completion predicates in aggregators
- Set appropriate timeouts
- Consider using `seda:` for async handoff points

---

### 11. Transaction Handling

**Level:** Recommended

Use transactions to ensure data consistency across multiple operations.

**Transaction Propagation Policies:**

| Policy | Behavior |
|--------|----------|
| `PROPAGATION_REQUIRED` | Join existing or create new (default) |
| `PROPAGATION_REQUIRES_NEW` | Always create new transaction |
| `PROPAGATION_MANDATORY` | Must run within existing transaction |

**Implementation:**
```yaml
- transacted:
    ref: PROPAGATION_REQUIRED
- to: jpa:Order
- to: jms:queue:notifications
```

**Exception Handling with Transactions:**
```yaml
- onException:
    exception: com.example.BusinessException
    handled: true
    markRollbackOnly: true
    steps:
      - to: kafka:failed-orders
```

---

### 12. Kafka Consumer Scaling

**Level:** Recommended

Configure Kafka consumers appropriately for parallel processing.

**Key Considerations:**
- Each partition is assigned to exactly one consumer in a group
- More consumers than partitions = idle (starving) consumers
- Use `consumersCount: 1` per pod and scale via Kubernetes replicas

**Implementation:**
```yaml
from:
  uri: kafka:orders
  parameters:
    groupId: order-processors
    consumersCount: 1
    autoOffsetReset: earliest
```

**Offset Reset Strategies:**

| Strategy | Use When |
|----------|----------|
| `earliest` | Process all messages from beginning |
| `latest` | Only process new messages |
| `none` | Fail if no committed offset |

---

### 13. Kubernetes Deployment

**Level:** Recommended

Design routes for cloud-native deployment on Kubernetes.

**Key Practices:**
- Externalize all configuration using environment variables or ConfigMaps
- Implement health checks for liveness and readiness probes
- Use Secrets for sensitive configuration
- Configure resource requests and limits

**Configuration Hierarchy:**
1. Environment variables (highest priority)
2. System properties
3. ConfigMap-mounted `application.properties`
4. Bundled defaults

**Health Probes:**

| Probe | Purpose |
|-------|---------|
| **Readiness** | Is the app ready to receive traffic? |
| **Liveness** | Is the app still running correctly? |
| **Startup** | Has the app finished starting? |

---

## Customization

The constitution in `.camel-kit/constitution.md` can be customized for your organization:

### Disabling Rules

Add to the `Disabled Rules` section:

```markdown
## Disabled Rules

- Circuit Breaker: Internal services on same cluster, low latency required
- Schema Validation: Legacy system, schema not available
```

### Adding Custom Rules

Add to the `Custom Rules` section:

```markdown
## Custom Rules

### Audit Logging (Required)

All routes that modify data must log:
- Before state
- After state
- User/system identifier
- Timestamp

### Compliance Headers (Required)

All external API calls must include:
- X-Correlation-Id
- X-Source-System
- X-Compliance-Level
```

### Adjusting Levels

Change rule levels in the `Overrides` section:

```markdown
## Overrides

| Rule | Default Level | Override Level | Reason |
|------|---------------|----------------|--------|
| Idempotent Consumer | Recommended | Required | Financial transactions |
| Schema Validation | Recommended | Required | Security compliance |
| Circuit Breaker | Required | Recommended | Internal microservices |
```

---

## Validation Codes

Constitution violations generate specific error codes during `/camel.validate`:

| Code | Rule | Level | Message |
|------|------|-------|---------|
| `CONST-001` | Error Handling | Error | Route missing error handling strategy |
| `CONST-002` | Circuit Breaker | Error | External call without circuit breaker |
| `CONST-003` | Secrets | Error | Hardcoded secret detected |
| `CONST-004` | Naming | Error | Route ID does not follow naming convention |
| `CONST-005` | Single Responsibility | Warning | Route has excessive processing steps |
| `CONST-006` | Idempotent Consumer | Warning | Consumer without idempotent handling |
| `CONST-007` | Schema Validation | Warning | No validation at entry point |
| `CONST-008` | Observability | Warning | No correlation ID handling |
| `CONST-009` | Transaction | Warning | Multiple writes without transaction |
| `CONST-010` | Kafka Scaling | Warning | More consumers than partitions |
| `CONST-011` | Kubernetes | Warning | Hardcoded Kubernetes-specific values |
| `CONST-012` | Custom Rule | Varies | Custom rule violation |

---

## See Also

- [User Guide](user-guide.md) - Complete usage guide
- [Command Reference](commands.md) - Detailed command documentation

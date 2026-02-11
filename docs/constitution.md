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

**Options:**

| Strategy | Use when |
|----------|----------|
| Dead Letter Channel | Messages need manual review |
| Retry with Backoff | Transient failures expected |
| Circuit Breaker | External service may be unavailable |
| Custom Handler | Specific exceptions need special handling |

**Example:**
```yaml
errorHandler:
  deadLetterChannel:
    deadLetterUri: kafka:orders-dlq
    redeliveryPolicy:
      maximumRedeliveries: 3
      redeliveryDelay: 1000
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
| `CONST-009` | Custom Rule | Varies | Custom rule violation |

---

## See Also

- [User Guide](user-guide.md) - Complete usage guide
- [Command Reference](commands.md) - Detailed command documentation

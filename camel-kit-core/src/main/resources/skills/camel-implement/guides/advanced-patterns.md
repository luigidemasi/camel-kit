# Advanced Patterns Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being implemented
> - `CAMEL_VERSION` — from `.camel-kit/config.properties`

This guide implements advanced EIP patterns referenced in the design spec. Each pattern maps to a specific design spec section:

| Pattern | Design Spec Section |
|---------|-------------|
| Idempotent Consumer | "Resilience / Circuit Breaker" or "Idempotent Consumer" |
| Transactions | "Transactions" |
| Circuit Breaker | "Resilience / Circuit Breaker" |
| Retry with Backoff | "Error Handling" |
| Correlation ID | "Monitoring & Observability" |
| Content Enricher | "Processing Steps" |
| Throttling | "Performance & Reliability" |
| Batch Processing | "Performance & Reliability" |
| Dead Letter Channel | "Error Handling" |
| Schema Validation | "Processing Steps" |

**Only implement patterns that appear in the design spec.** Skip patterns whose corresponding design spec section does not exist.

---

### 8.1 Idempotent Consumer Pattern

If the design spec specifies exactly-once or at-least-once delivery:

```yaml
# Add to route after source (from:)
- idempotentConsumer:
    expression:
      simple: "${header.MessageId}"  # Or extract from body
    idempotentRepository:
      type: memory  # Or: database, redis, kafka
    skipDuplicate: true
    eager: true
```

**Configuration in application.properties:**
```properties
# Idempotent repository settings
idempotent.repository.type=memory
idempotent.repository.cacheSize=1000
idempotent.repository.cacheRemovalPeriod=3600000

# For database repository
# camel.beans.idempotentRepository=#class:org.apache.camel.processor.idempotent.jdbc.JdbcMessageIdRepository
# camel.beans.idempotentRepository.dataSource=#dataSource
# camel.beans.idempotentRepository.processorName={flow-name}
```

**Rung 1 (preferred, Forage available):** a JDBC idempotent repository is a derived bean of the Forage datasource —
`forage.<dsName>.jdbc.idempotent.repository.name=idempotentRepository` registers `#idempotentRepository` with no
`camel.beans.*` lines. See `skills/shared/forage.md`. Use the `#class:` form above only as rung 3.

### 8.2 Transaction Support

If the design spec requires transactional processing:

```yaml
# Add transaction policy to route
- transacted:
    ref: "PROPAGATION_REQUIRED"

# Or specify transaction manager
- to:
    uri: "sql:INSERT INTO..."
    parameters:
      transacted: true
      transactionManager: "#transactionManager"
```

**Configuration in application.properties:**
```properties
# Transaction manager (for database transactions)
camel.beans.transactionManager=#class:org.springframework.jdbc.datasource.DataSourceTransactionManager
camel.beans.transactionManager.dataSource=#dataSource

# JMS transactions (for messaging)
# camel.component.jms.transacted=true
# camel.component.jms.transactionManager=#jmsTransactionManager
```

**Rung 1 (preferred, Forage available):** `forage.<dsName>.jdbc.transaction.enabled=true` auto-registers the
`PROPAGATION_REQUIRED` / `PROPAGATION_MANDATORY` policy beans on the Forage datasource — no transactionManager bean
needed. For JMS, `forage.<brokerName>.jms.transaction.enabled=true` (XA/Narayana options available). See
`skills/shared/forage.md`. Use the `#class:` form above only as rung 3.

**Dependencies to add:**
```xml
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-tx</artifactId>
</dependency>
```

### 8.3 Circuit Breaker Pattern

If the design spec identifies external dependencies:

```yaml
# Wrap external calls with circuit breaker
- circuitBreaker:
    resilience4jConfiguration:
      failureRateThreshold: 50
      waitDurationInOpenState: 30  # seconds
      permittedNumberOfCallsInHalfOpenState: 3
      slidingWindowSize: 10
    steps:
      # External call here
      - to:
          uri: "http://{{external.service.url}}"
    onFallback:
      steps:
        # Fallback action
        - log:
            message: "Circuit breaker open, using fallback"
        - setBody:
            constant: "Service temporarily unavailable"
```

**Configuration:**
```properties
# Circuit breaker settings
circuitbreaker.{flow-name}.failureRateThreshold=50
circuitbreaker.{flow-name}.waitDuration=30000
circuitbreaker.{flow-name}.slidingWindowSize=10
```

**Dependencies:**
```xml
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-resilience4j</artifactId>
</dependency>
```

### 8.4 Retry with Exponential Backoff

Enhanced retry configuration with exponential backoff:

```yaml
errorHandler:
  deadLetterChannel:
    deadLetterUri: "{{dlq.endpoint}}"
    redeliveryPolicy:
      maximumRedeliveries: {{error.max.retries}}
      redeliveryDelay: {{error.retry.delay}}
      backOffMultiplier: 2.0
      maximumRedeliveryDelay: 60000
      useExponentialBackOff: true
      retryAttemptedLogLevel: WARN
      retriesExhaustedLogLevel: ERROR
```

### 8.5 Correlation ID Propagation

If the design spec Monitoring & Observability section requires correlation IDs:

```yaml
# Add at route entry point
- setHeader:
    name: X-Correlation-ID
    simple: "${header.X-Correlation-ID} != null ? ${header.X-Correlation-ID} : ${exchangeId}"

# Log with correlation ID
- log:
    message: "[${header.X-Correlation-ID}] Processing message: ${body.id}"
    loggingLevel: INFO

# Propagate to downstream systems
- setHeader:
    name: CamelHttpHeader.X-Correlation-ID
    simple: "${header.X-Correlation-ID}"
```

### 8.6 Content Enricher with Caching

If the design spec has enrichment steps and high volume:

```yaml
# Enrich with caching
- enrich:
    expression:
      simple: "sql:SELECT name FROM customers WHERE id = ${body.customerId}"
    aggregationStrategy: "#customerEnricher"
    cacheSize: 1000
    cacheTimeout: 300000  # 5 minutes
```

**Bean definition:**
```properties
camel.beans.customerEnricher=#class:com.example.CustomerEnricherStrategy
```

### 8.7 Throttling / Rate Limiting

If the design spec Performance & Reliability section specifies throttling:

```yaml
# Throttle message processing
- throttle:
    expression:
      constant: 100  # Max 100 messages
    timePeriodMillis: 1000  # per second
    rejectExecution: false  # Queue if over limit
```

### 8.8 Batch Processing

If processing individual messages is inefficient:

```yaml
# Aggregate messages into batches
- aggregate:
    correlationExpression:
      constant: "batch"
    completionSize: {{batch.size}}
    completionTimeout: {{batch.timeout}}
    aggregationStrategy: "#batchAggregator"
    steps:
      # Process batch
      - to:
          uri: "sql:BATCH INSERT..."
```

**Configuration:**
```properties
batch.size=100
batch.timeout=5000
```

### 8.9 Dead Letter Channel with Metadata

Enhanced DLQ that preserves failure metadata:

```yaml
errorHandler:
  deadLetterChannel:
    deadLetterUri: "{{dlq.endpoint}}"
    useOriginalMessage: true  # Send original, not transformed message
    deadLetterHandleNewException: false
    onPrepareFailure:
      # Add failure metadata
      - setHeader:
          name: X-Failure-Timestamp
          simple: "${date:now:yyyy-MM-dd'T'HH:mm:ss}"
      - setHeader:
          name: X-Failure-Route
          simple: "${routeId}"
      - setHeader:
          name: X-Failure-Exception
          simple: "${exception.message}"
      - setHeader:
          name: X-Original-Correlation-ID
          simple: "${header.X-Correlation-ID}"
```

### 8.10 Schema Validation

If the design spec requires input validation:

```yaml
# Validate against JSON schema
- to:
    uri: "json-validator:schemas/{flow-name}-input.json"

# Or Bean Validation
- to:
    uri: "bean-validator:validate"
```

**Configuration:**
```properties
# Validation settings
validation.failOnError=true
validation.schema.location=schemas/{flow-name}-input.json
```

**Dependencies:**
```xml
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-json-validator</artifactId>
</dependency>
```

### 8.11 Implementation Checklist

Verify implementation of patterns from the design spec:

```
Advanced Patterns Implementation:

From design spec Performance & Reliability section:
  [✓] Idempotent consumer: [Implemented/Not needed]
  [✓] Transactions: [Implemented/Not needed]
  [✓] Circuit breaker: [Implemented/Not needed]
  [✓] Retry with backoff: [Implemented]
  [✓] Throttling: [Implemented/Not needed]
  [✓] Batching: [Implemented/Not needed]

From design spec Security section:
  [✓] Input validation: [Implemented/Not needed]
  [✓] Schema validation: [Implemented/Not needed]

From design spec Monitoring & Observability section:
  [✓] Correlation ID: [Implemented]
  [✓] Structured logging: [Implemented]
  [✓] Metrics: [Implemented/Not needed]
```

---

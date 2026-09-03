# Anti-Pattern Detection Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being validated
> - `CAMEL_VERSION` — from `.camel-kit/config.properties`

## When to Load This Guide

Always load this guide as part of validation. It supplies the manual anti-pattern checks and the fallback when MCP
security analysis is unavailable.

---

## Anti-Pattern Detection

Scan for common integration anti-patterns that could cause problems in production.

---

## Route Design Anti-Patterns

### God Routes

Routes doing too many things:

```
Check: Route complexity
- Route {flow-name}: [N] processing steps
- Project norm: [STEP_COUNT_P75 or "N/A — no graph"] steps (P75)
  → [N <= STEP_COUNT_P75 (default 10)]: ✅ Acceptable complexity
  → [N > STEP_COUNT_P75 (default 10)]: ⚠️ WARNING: Consider splitting into multiple routes
  → [N > 2 × STEP_COUNT_P75 (default 20)]: ❌ ERROR: God route detected - violates SRP
```

**Fix:**
- Split into multiple routes with single responsibility
- Use direct: or seda: endpoints to connect routes
- Each route should do one logical thing

---

### Hardcoded Endpoints

Connection details embedded in routes:

```
Check: Connection details in routes
- Scanning for: brokers=, url=jdbc:, http://localhost, passwords
  ✅ No hardcoded connections found

Or:
  ❌ Found hardcoded connection at line 25:
     kafka:topic?brokers=localhost:9092
     → Fix: Move to application.properties
        camel.component.kafka.brokers=localhost:9092
```

**Fix:**
```properties
# In application.properties (NOT in route YAML)
camel.component.kafka.brokers=localhost:9092
camel.component.http.baseUrl=https://api.example.com
```

---

### Missing Error Handling

Routes without error strategies:

```
Check: Error handling strategy
✅ Route has errorHandler defined
✅ DLQ endpoint configured

Or:
  ❌ No error handler found
  → Add errorHandler to route or use onException

If PROJECT_NORMS.ERROR_HANDLING_COVERAGE is available and ≥ 90%:
  Escalate from ⚠️ WARNING to ❌ CRITICAL:
  ❌ CRITICAL: No error handler found — [ERROR_HANDLING_COVERAGE]% of project routes have error handling. This route deviates from the project norm.
```

**Fix:**
```yaml
- route:
    id: my-route
    errorHandler:
      deadLetterChannel:
        deadLetterUri: "kafka:{{dlq.endpoint}}"
    from:
      # ...
```

---

### Synchronous Blocking

High throughput with synchronous processing:

```
Check: Processing pattern vs throughput
- Throughput target: [N] msg/sec
- Processing: [Synchronous | Asynchronous]
  → High throughput (>100/sec) + Synchronous: ⚠️ WARNING
     Consider async processing or streaming
```

**Fix:**
- Use async processing with seda: or disruptor:
- Use reactive streams for very high throughput
- Consider batch processing

---

### No Idempotency

Missing duplicate message handling:

```
Check: Duplicate message handling
- Delivery guarantee: [Exactly-once | At-least-once]
- Idempotent consumer: [Present | Missing]
  → Exactly-once + No idempotent consumer: ❌ ERROR
     Messages could be processed multiple times
```

**Fix:**
```yaml
- idempotentConsumer:
    expression:
      simple: "${header.MessageId}"
    idempotentRepository:
      type: memory
    skipDuplicate: true
```

---

## Integration Anti-Patterns

### Chatty Interfaces

Too many small messages instead of batches:

```
Check: Message granularity
- Pattern: Individual messages vs batches
- Volume: [N] messages expected
  → [N > 100/sec] + Individual messages: ⚠️ WARNING
     Consider batching for better performance
```

**Fix:**
```yaml
- aggregate:
    correlationExpression:
      constant: "batch"
    completionSize: 100
    completionTimeout: 5000
    steps:
      # Process batch
```

---

### Missing Correlation IDs

No tracing capability:

```
Check: Tracing capability
- Correlation ID header: [Present | Missing]
  ❌ No correlation ID generation found
  → Add setHeader with correlation ID at route entry

  Example:
    - setHeader:
        name: X-Correlation-ID
        simple: "${exchangeId}"
```

**Fix:**
```yaml
- setHeader:
    name: X-Correlation-ID
    simple: "${header.X-Correlation-ID} != null ? ${header.X-Correlation-ID} : ${exchangeId}"

- log:
    message: "[${header.X-Correlation-ID}] Processing message"
```

---

### No Circuit Breakers

External calls without resilience:

```
Check: External dependency resilience
- External calls detected: [list]
- Circuit breaker: [Present | Missing]
  ⚠️ WARNING: No circuit breaker for [system]
  → External system failures could cascade
  → Consider adding Hystrix or Resilience4j circuit breaker
```

**Fix:**
```yaml
- circuitBreaker:
    resilience4jConfiguration:
      failureRateThreshold: 50
      waitDurationInOpenState: 30  # seconds
    steps:
      - to:
          uri: "http://{{external.service.url}}"
    onFallback:
      steps:
        - log:
            message: "Circuit breaker open, using fallback"
```

---

## Security Anti-Patterns

### Hardcoded Credentials

Secrets embedded in code or config:

```
Check: Credential management
- Scanning for: password=, apiKey=, token=, secret=
  ✅ No hardcoded credentials found

Or:
  ❌ CRITICAL: Hardcoded credential found at line 42
     password=secretpassword
     → NEVER commit credentials to code
     → Use ${vault:...} or secrets manager
```

**Fix:**
```properties
# WRONG - never do this
database.password=secretpassword

# CORRECT - use secrets manager
database.password=${vault:secret/database#password}

# Or environment variable
database.password=${DATABASE_PASSWORD}
```

---

### Plain Text Communication

Unencrypted network traffic:

```
Check: Transport security
- HTTP endpoints: [list]
- Using HTTPS: [Yes/No]
  ❌ Plain HTTP found: http://api.example.com
  → Change to HTTPS

- Kafka SSL: [Enabled/Disabled]
  ⚠️ Kafka SSL not enabled
  → Enable SSL for production: camel.component.kafka.securityProtocol=SSL
```

**Fix:**
```properties
# Use HTTPS, not HTTP
api.baseUrl=https://api.example.com

# Enable Kafka SSL
camel.component.kafka.securityProtocol=SSL
camel.component.kafka.sslTruststoreLocation=/path/to/truststore.jks

# Enable database SSL
database.url=jdbc:postgresql://localhost:5432/db?ssl=true&sslmode=require
```

---

### Logging Sensitive Data

PII or secrets in logs:

```
Check: Log statements for PII/secrets
- Scanning logs for: ${body}, ${header.Authorization}, passwords
  ⚠️ WARNING: Logging full body at line 35
     May contain PII or sensitive data
  → Use selective logging or mask sensitive fields
```

**Fix:**
```yaml
# WRONG - logs everything including PII
- log:
    message: "Processing: ${body}"

# CORRECT - selective logging
- log:
    message: "Processing order: ${body.orderId} (user: ${body.userId})"

# Or mask sensitive fields
- log:
    message: "Processing order: ${body.orderId} (email: ***@***)"
```

```properties
# Configure log masking
logging.mask.fields=email,phone,ssn,creditCard,password
```

---

### No Input Validation

Missing schema or size validation:

```
Check: Input validation
- Schema validation: [Present | Missing]
- Size limits: [Set | Not set]
  ⚠️ No schema validation found
  → Add JSON Schema or Bean Validation

  ⚠️ No message size limit
  → Set maximum message size to prevent DoS
```

**Fix:**
```yaml
# Add schema validation
- to:
    uri: "json-validator:schemas/input-schema.json"

# Or bean validation
- to:
    uri: "bean-validator:validate"
```

```properties
# Set message size limits
http.maxRequestSize=1048576  # 1MB
kafka.maxMessageSize=1048576
```

---

## Performance Anti-Patterns

### No Connection Pooling

Database/HTTP connections not pooled:

```
Check: Connection management
- Database connections: [Pooled | Not pooled]
- HTTP connections: [Pooled | Not pooled]
  ⚠️ No connection pool configured for database
  → Use connection pooling (HikariCP, Commons DBCP2)

  Example:
    camel.beans.dataSource=#class:org.apache.commons.dbcp2.BasicDataSource
    camel.beans.dataSource.maxTotal=20
```

**Fix:**
```properties
# Database connection pool
camel.beans.dataSource=#class:org.apache.commons.dbcp2.BasicDataSource
camel.beans.dataSource.driverClassName=org.postgresql.Driver
camel.beans.dataSource.url=jdbc:postgresql://localhost:5432/db
camel.beans.dataSource.username=user
camel.beans.dataSource.password=${vault:db#password}
camel.beans.dataSource.initialSize=5
camel.beans.dataSource.maxTotal=20
camel.beans.dataSource.maxIdle=10
camel.beans.dataSource.minIdle=5

# HTTP connection pool
camel.component.http.maxTotalConnections=200
camel.component.http.connectionsPerRoute=20
```

**Dependencies:**
```xml
<dependency>
  <groupId>org.apache.commons</groupId>
  <artifactId>commons-dbcp2</artifactId>
</dependency>
```

---

### Synchronous Processing of High Volume

Blocking calls at high throughput:

```
Check: Processing pattern vs volume
- Volume: [N] messages/sec
- Pattern: [Sync | Async]
  ❌ ERROR: High volume ([N] > 100/sec) with synchronous processing
  → Use async processing or reactive streams
```

**Fix:**
```yaml
# Use async processing with SEDA
- to:
    uri: "seda:process-async?concurrentConsumers=10"

# Separate route for async processing
- route:
    id: async-processor
    from:
      uri: "seda:process-async?concurrentConsumers=10"
      steps:
        # Heavy processing here
```

---

### No Caching

Repeated lookups of same reference data:

```
Check: Reference data lookup
- Content enricher detected: [Yes/No]
- Caching: [Yes/No]
  ⚠️ WARNING: Enrichment without caching
  → Same reference data fetched repeatedly
  → Add caching for enrichment data
```

**Fix:**
```yaml
# Add caching to enrichment
- enrich:
    expression:
      simple: "sql:SELECT * FROM customers WHERE id = ${body.customerId}"
    aggregationStrategy: "#customerEnricher"
    cacheSize: 1000
    cacheTimeout: 300000  # 5 minutes
```

**Or use Caffeine cache:**
```yaml
# Store in cache first
- to:
    uri: "caffeine-cache:customerCache?action=GET&key=${body.customerId}"

# If not in cache, fetch and store
- choice:
    when:
      - simple: "${body} == null"
        steps:
          - to:
              uri: "sql:SELECT * FROM customers WHERE id = ${body.customerId}"
          - to:
              uri: "caffeine-cache:customerCache?action=PUT&key=${body.customerId}"
```

---

## Anti-Pattern Detection Summary

After scanning, show summary:

```
== ANTI-PATTERN SUMMARY ==

Critical (Must Fix):
  ❌ Hardcoded credentials at line 42
  ❌ God route: 25 processing steps
  ❌ No input validation

Warnings (Recommended):
  ⚠️ No correlation ID generation
  ⚠️ No circuit breaker for external calls
  ⚠️ Logging full body may expose PII
  ⚠️ No connection pooling
  ⚠️ Plain text communication (HTTP, no Kafka SSL)

Best Practices (Optional):
  ℹ️ Consider batching for high volume
  ℹ️ Add caching for enrichment
  ℹ️ Use async processing for throughput
```

---

## Anti-Pattern Checklist

Use this checklist for manual review:

### Route Design
- [ ] Routes have single responsibility (<10 steps each)
- [ ] All connections externalized to properties
- [ ] Error handling present on all routes
- [ ] Async processing for high throughput (>100 msg/sec)
- [ ] Idempotent consumer for exactly-once delivery

### Integration
- [ ] Batching used for high volume (>100 msg/sec)
- [ ] Correlation IDs generated and propagated
- [ ] Circuit breakers around external calls
- [ ] Timeouts configured for external calls

### Security
- [ ] No hardcoded credentials anywhere
- [ ] HTTPS used for all HTTP endpoints
- [ ] TLS/SSL enabled for messaging (Kafka, JMS)
- [ ] Sensitive data masked in logs
- [ ] Input validation (schema + size limits)

### Performance
- [ ] Connection pooling for databases
- [ ] Connection pooling for HTTP clients
- [ ] Async processing for heavy operations
- [ ] Caching for reference data lookups
- [ ] Batch processing where applicable

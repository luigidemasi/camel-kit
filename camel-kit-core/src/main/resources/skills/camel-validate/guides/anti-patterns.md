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
          uri: "https://{{external.service.url}}"
    onFallback:
      steps:
        - log:
            message: "Circuit breaker open, using fallback"
```

---

## Security Anti-Patterns

Load `shared/camel-security-checklist.md`. Each anti-pattern below violates one of its five core rules; the fix
snippets (secret references, transport security, log masking, input validation) live there and are not repeated here.
Apply every clause of every rule and use the checklist's validation severity mapping. The examples below are
illustrative and never narrow the canonical checks; every confirmed security-rule violation is Critical/FAIL.

### Hardcoded Credentials

Secrets embedded in code or config:

```
Check: Credential management
- Scanning route YAML and properties for hardcoded credential patterns
  ✅ No hardcoded credentials found

Or:
  ❌ CRITICAL: Hardcoded credential found at line 42
     password=secretpassword
     → NEVER commit credentials to code
     → Replace with a placeholder resolved from the environment or a secrets manager
```

**Fix:** security checklist rule 1 snippets (secret references).

---

### Plain Text Communication

Unencrypted network traffic:

```
Check: Transport security
- HTTP endpoints: [list]
- Using HTTPS: [Yes/No]
  ❌ CRITICAL: Plain HTTP found: http://api.example.com
  → Change to HTTPS

- Kafka SSL: [Enabled/Disabled]
  ❌ CRITICAL: Kafka SSL not enabled
  → Enable SSL or SASL_SSL on the Kafka component for production
```

**Fix:** security checklist rule 2 snippets (transport security).

---

### Logging Sensitive Data

PII or secrets in logs:

```
Check: Log statements for PII/secrets
- Scanning logs for: ${body}, ${header.Authorization}, passwords
  ❌ CRITICAL: Logging full body at line 35
     May contain PII or sensitive data
  → Use selective logging or mask sensitive fields
```

**Fix:** security checklist rule 3 snippets (log masking, selective logging).

---

### No Input Validation at External or Untrusted Ingress

Missing schema or size validation where external or newly untrusted input enters the flow:

```
Check: Input validation at every external or untrusted ingress
- Schema validation: [Present | Missing]
- Size limits: [Set | Not set]
  ❌ CRITICAL: No schema validation found
  → Add JSON Schema or Bean Validation

  ❌ CRITICAL: No message size limit
  → Set maximum message size to prevent DoS
```

Apply this check at every external or newly untrusted ingress. Internal `direct:` or `seda:` subroutes inherit validation
from their trusted caller unless they introduce another external or untrusted boundary.

**Fix:** security checklist rule 4 snippets (schema validation, size limits).

---

### Missing External Endpoint Authentication

Externally exposed HTTP/REST endpoints without caller authentication:

```
Check: External endpoint authentication
- Authentication: [Present | Missing]
  ❌ CRITICAL: External HTTP endpoint has no authentication
  → Add OAuth2/JWT, API key authentication, or mutual TLS
```

**Fix:** security checklist rule 5.

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
camel.beans.dataSource.password={{env:DATABASE_PASSWORD}}
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

**Fix with a bounded Caffeine cache:**
```yaml
# Reject caller-controlled component headers, then preserve the validated identifier as the cache key
- removeHeaders:
    pattern: "CamelCaffeine*"
- setHeader:
    name: CamelCaffeineKey
    simple: "${body.customerId}"

# Check the cache first
- to:
    uri: "caffeine-cache:customerCache?action=GET&maximumSize=1000"

# If not in cache, fetch and store
- choice:
    when:
      - simple: "${header.CamelCaffeineActionHasResult} == false"
        steps:
          - to:
              uri: "sql:SELECT * FROM customers WHERE id = :#${header.CamelCaffeineKey}"
          - to:
              uri: "caffeine-cache:customerCache?action=PUT&maximumSize=1000"
```

---

## Anti-Pattern Detection Summary

After scanning, show summary:

```
== ANTI-PATTERN SUMMARY ==

Critical (Must Fix):
  ❌ Hardcoded credentials at line 42
  ❌ God route: 25 processing steps
  ❌ Plain HTTP to external system at line 12
  ❌ Kafka SSL not enabled
  ❌ Logging full body may expose PII
  ❌ External or untrusted ingress has no input validation (schema, size limits)
  ❌ External HTTP endpoint has no authentication

Warnings (Recommended):
  ⚠️ No correlation ID generation
  ⚠️ No circuit breaker for external calls
  ⚠️ No connection pooling

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
- [ ] All five `shared/camel-security-checklist.md` rules pass (credentials, TLS, logs, input, authentication)

### Performance
- [ ] Connection pooling for databases
- [ ] Connection pooling for HTTP clients
- [ ] Async processing for heavy operations
- [ ] Caching for reference data lookups
- [ ] Batch processing where applicable

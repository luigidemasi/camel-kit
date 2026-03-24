# Camel Design Patterns — Error Handling

> Unhappy paths — stability & integrity patterns.

### 8. Data Integrity Pattern

**Intent**: Maintain data consistency and business integrity when processing fails.

**Transaction Strategies**:

| Strategy | Description | Use Case |
|----------|-------------|----------|
| **No Transaction** | Default behavior | Read-only operations, idempotent writes |
| **Local Transaction** | Single resource transaction | Single database or JMS broker |
| **Global Transaction (XA)** | Distributed transaction | Multiple resources requiring atomicity |
| **Idempotent Alternative** | Avoid XA with idempotency | When XA overhead is unacceptable |

**Transaction Propagation Policies:**

| Policy | Behavior |
|--------|----------|
| `PROPAGATION_REQUIRED` | Join existing or create new (default) |
| `PROPAGATION_REQUIRES_NEW` | Always create new, suspend existing |
| `PROPAGATION_MANDATORY` | Must run within existing; throws otherwise |

**Camel Implementation**:
```yaml
# Local transaction with default propagation
- route:
    id: transacted-route
    from:
      uri: jms:queue:orders
    steps:
      - transacted: {}
      - to:
          uri: jpa:Order
      - to:
          uri: jms:queue:notifications
```

**Guidance**: Prefer local transactions. Use XA only when necessary. Consider Saga for long-running processes.

---

### 9. Saga Pattern

**Intent**: Manage long-running business processes with compensating transactions instead of distributed locks.

**Camel Implementation**:
```yaml
- route:
    id: order-saga
    from:
      uri: direct:place-order
    steps:
      - saga:
          propagation: REQUIRED
          compensation:
            uri: direct:cancel-order
          completion:
            uri: direct:complete-order
          steps:
            - to:
                uri: direct:reserve-inventory
            - to:
                uri: direct:charge-payment
            - to:
                uri: direct:ship-order

# Compensation route
- route:
    id: cancel-order
    from:
      uri: direct:cancel-order
    steps:
      - to:
          uri: direct:refund-payment
      - to:
          uri: direct:release-inventory
```

**When to Apply**: Long-running processes, cross-service transactions, when XA is unavailable.

---

### 10. Idempotent Filter Pattern

**Intent**: Filter out duplicate messages and ensure exactly-once processing semantics.

**Camel Implementation**:
```yaml
- route:
    id: idempotent-processing
    from:
      uri: kafka:orders
    steps:
      - idempotentConsumer:
          expression:
            header: orderId
          idempotentRepository: "#jpaRepository"
          skipDuplicate: true
          steps:
            - to:
                uri: direct:process-order
```

**Repository Selection**:

| Repository | Use Case | Clustering |
|------------|----------|------------|
| `MemoryIdempotentRepository` | Development/testing | No |
| `JpaMessageIdRepository` | Single node production | No |
| `HazelcastIdempotentRepository` | Clustered environments | Yes |

---

### 11. Retry Pattern

**Intent**: Handle anticipated transient failures by transparently retrying operations.

**Camel Implementation**:
```yaml
- route:
    id: retry-route
    errorHandler:
      deadLetterChannel:
        deadLetterUri: kafka:order-dlq
        redeliveryPolicy:
          maximumRedeliveries: 5
          redeliveryDelay: 1000
          backOffMultiplier: 2
          useExponentialBackOff: true
          retryAttemptedLogLevel: WARN
    from:
      uri: kafka:orders
    steps:
      - to:
          uri: http:order-service
```

**Best Practices**: Keep retry delays short, use exponential backoff, set maximum redeliveries, use DLQ for persistent failures.

---

### 12. Throttling Pattern

**Intent**: Control throughput to meet SLAs and prevent overloading downstream systems.

| Strategy | Description |
|----------|-------------|
| **Reject** | Return error immediately (`rejectExecution: true`) |
| **Block** | Wait until capacity available (default) |
| **Delay** | Queue and process later (`seda` with bounded queue) |

```yaml
- route:
    id: throttled-api
    from:
      uri: platform-http:/orders
    steps:
      - throttle:
          expression:
            constant: 100
          timePeriodMillis: 1000
          rejectExecution: true
      - to:
          uri: direct:process-order
```

---

### 13. Circuit Breaker Pattern

**Intent**: Protect routes from cascading failures and slow responses from external systems.

**States**: CLOSED → (failures exceed threshold) → OPEN → (wait) → HALF-OPEN → (success) → CLOSED

```yaml
- route:
    id: circuit-breaker-route
    from:
      uri: direct:call-service
    steps:
      - circuitBreaker:
          resilience4jConfiguration:
            failureRateThreshold: 50
            waitDurationInOpenState: 10000
            slidingWindowSize: 10
          steps:
            - to:
                uri: http:external-service
          onFallback:
            steps:
              - transform:
                  constant: '{"status": "service unavailable"}'
```

| Parameter | Typical Value |
|-----------|---------------|
| `failureRateThreshold` | 50% |
| `waitDurationInOpenState` | 10-60 seconds |
| `slidingWindowSize` | 10-100 calls |

---

### 14. Error Channel Pattern

**Intent**: Handle errors appropriately based on error type.

| Channel | Purpose | Implementation |
|---------|---------|----------------|
| **Dead Letter Channel** | Store failed messages | `deadLetterChannel` |
| **Invalid Message Channel** | Route malformed messages | `onException` with `handled(true)` |

```yaml
- route:
    id: error-handling-route
    errorHandler:
      deadLetterChannel:
        deadLetterUri: kafka:order-dlq
        useOriginalMessage: true
    from:
      uri: kafka:orders
    steps:
      - onException:
          exception: com.example.ValidationException
          handled: true
          steps:
            - to:
                uri: kafka:invalid-orders
      - to:
          uri: bean:orderValidator
      - to:
          uri: direct:process
```

**DLQ Best Practices**: Include original message + exception details, monitor DLQ depth, create reprocessing mechanism.

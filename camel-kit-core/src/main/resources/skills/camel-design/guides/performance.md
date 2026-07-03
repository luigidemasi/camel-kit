# Performance & Reliability Guide

## When to Load This Guide

Load when user mentions:
- High volume / throughput
- Performance requirements
- Latency concerns
- "Fast" / "real-time"
- Reliability / zero data loss

---

## Throughput Requirements

### Classification

**Low Volume:** <10 messages/second
- Standard configuration acceptable
- Synchronous processing fine
- Single thread sufficient

**Medium Volume:** 10-100 messages/second
- Consider connection pooling
- Batch operations where possible
- Monitor thread pool

**High Volume:** 100-1,000 messages/second
- Optimize thread pools
- Use async processing
- Connection pooling required
- Consider batching

**Very High Volume:** >1,000 messages/second
- Horizontal scaling required
- Partitioning/sharding
- Async/reactive processing
- Tuned for performance

### Configuration by Volume

```properties
# Low volume (<10/sec)
camel.threadpool.poolSize=2
camel.threadpool.maxPoolSize=5

# Medium volume (10-100/sec)
camel.threadpool.poolSize=10
camel.threadpool.maxPoolSize=20
datasource.maxConnections=10

# High volume (100-1000/sec)
camel.threadpool.poolSize=50
camel.threadpool.maxPoolSize=100
datasource.maxConnections=50
kafka.maxPollRecords=500

# Very high volume (>1000/sec)
# Requires horizontal scaling + partitioning
```

---

## Latency Requirements

### Classification

**Real-time:** <100ms per message
- Use async processing
- Minimize transformations
- In-memory operations
- No blocking calls

**Near real-time:** <1 second
- Standard processing acceptable
- Optimize database queries
- Use connection pooling

**Batch:** Minutes to hours
- Synchronous acceptable
- Can use batch operations
- Optimize for throughput over latency

### Optimization Strategies

| Target Latency | Strategy |
|----------------|----------|
| <100ms | Async, no blocking, in-memory |
| <1s | Standard with connection pooling |
| <5s | Standard configuration |
| >5s | Batch processing, optimize throughput |

---

## Delivery Guarantees

### Exactly-Once Processing

**Requirements:**
- Idempotent consumer
- Transactions (if needed)
- Message ID tracking

**Configuration:**
```yaml
- idempotentConsumer:
    expression:
      simple: "${header.MessageId}"
    idempotentRepository:
      type: database
```

**Cost:** Higher complexity, lower throughput

---

### At-Least-Once Processing

**Requirements:**
- Retry on failure
- DLQ for poison messages
- Idempotency may be needed

**Configuration:**
```yaml
errorHandler:
  deadLetterChannel:
    deadLetterUri: "{{dlq.endpoint}}"
    redeliveryPolicy:
      maximumRedeliveries: 3
```

**Cost:** Possible duplicates, application must handle

---

### At-Most-Once Processing

**Requirements:**
- Fire-and-forget
- Minimal error handling

**Configuration:**
```yaml
# No redelivery
errorHandler:
  noErrorHandler: {}
```

**Cost:** Possible data loss, fastest

---

## Backpressure Handling

### Buffer Strategy

**Use when:** Temporary spikes, producer faster than consumer

**Configuration:**
```properties
buffer.maxSize=10000
buffer.fullAction=block  # or drop, or exception
```

**Pros:** Absorbs temporary spikes
**Cons:** Memory usage, eventual overflow

---

### Throttle Strategy

**Use when:** Need to limit rate to protect downstream

**Configuration:**
```yaml
- throttle:
    expression:
      constant: 100
    timePeriodMillis: 1000
```

**Pros:** Protects downstream
**Cons:** Delays messages

---

### Circuit Breaker Strategy

**Use when:** Downstream system unreliable

**Configuration:**
```yaml
- circuitBreaker:
    resilience4jConfiguration:
      failureRateThreshold: 50
      waitDurationInOpenState: 30  # seconds
```

**Pros:** Fails fast, prevents cascade
**Cons:** Needs fallback logic

---

## Connection Pooling

### Database Connections

```properties
# Connection pool settings
camel.beans.dataSource=#class:org.apache.commons.dbcp2.BasicDataSource
camel.beans.dataSource.maxTotal=20
camel.beans.dataSource.maxIdle=10
camel.beans.dataSource.minIdle=5
```

### HTTP Connections

```properties
camel.component.http.maxTotalConnections=200
camel.component.http.connectionsPerRoute=20
```

---

## Batching

### When to Batch

- High volume (>100 msg/sec)
- Downstream supports batch operations
- Latency not critical
- Can aggregate messages

### Configuration

```yaml
- aggregate:
    correlationExpression:
      constant: "batch"
    completionSize: 100
    completionTimeout: 5000
    steps:
      - to: "sql:BATCH INSERT..."
```

---

## Performance Checklist

When designing for performance, consider:

- [ ] Throughput target: [N] messages/second
- [ ] Latency target: [N] milliseconds
- [ ] Delivery guarantee: [Exactly-once | At-least-once | At-most-once]
- [ ] Connection pooling configured
- [ ] Thread pool sized appropriately
- [ ] Batching if volume >100/sec
- [ ] Backpressure strategy defined
- [ ] Async processing for latency <100ms
- [ ] Horizontal scaling plan for >1000/sec

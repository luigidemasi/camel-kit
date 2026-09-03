# Enterprise Integration Patterns (EIP) Catalog

## Overview

Apache Camel implements the Enterprise Integration Patterns from the book by Gregor Hohpe and Bobby Woolf. Use these patterns to solve common integration challenges.

---

## Message Routing Patterns

### Content-Based Router

**Purpose:** Route messages to different destinations based on message content

**Use when:**
- Different message types need different processing
- Route by region, priority, or type
- Conditional routing logic

**Camel Implementation:**
```yaml
- choice:
    when:
      - simple: "${body.region} == 'US'"
        steps:
          - to: "kafka:us-orders"
      - simple: "${body.region} == 'EU'"
        steps:
          - to: "kafka:eu-orders"
    otherwise:
      steps:
        - to: "kafka:other-orders"
```

**Example:** Route orders to regional fulfillment centers

---

### Message Filter

**Purpose:** Discard messages that don't meet criteria

**Use when:**
- Only process messages meeting certain conditions
- Filter out unwanted/invalid messages
- Apply business rules

**Camel Implementation:**
```yaml
- filter:
    simple: "${body.amount} >= 50"
    steps:
      - to: "kafka:high-value-orders"
```

**Example:** Only process orders over $50

---

## Message Transformation Patterns

### Message Translator

**Purpose:** Transform message format or structure

**Use when:**
- Converting between formats (JSON ↔ XML)
- Mapping fields between schemas
- Adapting to different system formats

**Camel Implementation:**
```yaml
# Simple field mapping
- setBody:
    simple: |
      {
        "orderId": "${body.id}",
        "total": "${body.amount}"
      }

# Or using transformation component
- to: "xslt:transform/order-to-fulfillment.xsl"
```

**Example:** Convert order format from e-commerce to warehouse system

---

### Content Enricher

**Purpose:** Add data from external source to enrich message

**Use when:**
- Need to add customer details to order
- Look up reference data
- Augment message with additional context

**Camel Implementation:**
```yaml
# Bind the schema-validated identifier as a prepared SQL parameter
- enrich:
    expression:
      constant: "sql:SELECT name, email FROM customers WHERE id = :#${body.customerId}"
    aggregationStrategy: "#customerEnricher"
```

**Example:** Enrich order with customer name and email

**Follow-up Question:**
```
Q: "How often will you need to enrich data?"
→ High frequency: Consider caching
→ Low frequency: Direct lookup okay

Q: "Is the reference data static or dynamic?"
→ Static: Load once and cache
→ Dynamic: Fetch per message (with caching)
```

---

## Message Construction Patterns

### Splitter

**Purpose:** Split one message into multiple messages

**Use when:**
- Processing bulk file into individual records
- Array/list needs individual processing
- Parallel processing of items

**Camel Implementation:**
```yaml
- split:
    jsonpath: "$.orders[*]"
    steps:
      - to: "kafka:individual-orders"
```

**Example:** Split bulk order file into individual order messages

---

### Aggregator

**Purpose:** Combine multiple related messages into one

**Use when:**
- Batch processing (collect N messages)
- Combine responses from multiple services
- Create summary/report

**Camel Implementation:**
```yaml
- aggregate:
    correlationExpression:
      simple: "${header.orderId}"
    completionSize: 10
    completionTimeout: 5000
    aggregationStrategy: "#orderAggregator"
    steps:
      - to: "kafka:order-batches"
```

**Example:** Collect 10 order line items into single order

---

## Messaging Endpoints Patterns

### Idempotent Consumer

**Purpose:** Prevent duplicate processing of same message

**Use when:**
- At-least-once delivery (messages may be redelivered)
- Exactly-once processing required
- Duplicate messages possible

**Camel Implementation:**
```yaml
- idempotentConsumer:
    expression:
      simple: "${header.MessageId}"
    idempotentRepository:
      type: memory  # or database, redis, kafka
    skipDuplicate: true
```

**Example:** Ensure order processed only once even if Kafka redelivers

**Follow-up Question:**
```
Q: "Could the same message arrive multiple times?"
→ YES: Add Idempotent Consumer
  Q: "Where to store message IDs?"
     → Memory: Fast but lost on restart
     → Database: Persistent, slower
     → Redis: Fast and persistent
     → Kafka: Distributed, scalable

→ NO: Skip idempotency pattern
```

---

### Resequencer

**Purpose:** Restore message order

**Use when:**
- Messages arrive out of order
- Processing must be in sequence
- Multiple parallel consumers

**Camel Implementation:**
```yaml
- resequence:
    expression:
      simple: "${header.SequenceNumber}"
    batchConfig:
      capacity: 1000
      timeout: 5000
```

**Example:** Reorder messages by sequence number

---

## Message Channel Patterns

### Dead Letter Channel

**Purpose:** Handle failed messages

**Use when:**
- Need to handle poison messages
- Retry and eventual DLQ
- Preserve failed messages for analysis

**Camel Implementation:**
```yaml
errorHandler:
  deadLetterChannel:
    deadLetterUri: "kafka:{{dlq.topic}}"
    redeliveryPolicy:
      maximumRedeliveries: 3
      redeliveryDelay: 5000
```

**Example:** Failed order validations go to DLQ

---

### Wiretap

**Purpose:** Send copy of message to monitoring/auditing

**Use when:**
- Need to monitor/audit messages
- Send copy for logging
- Debug production issues

**Camel Implementation:**
```yaml
- wireTap:
    uri: "kafka:audit-topic"
    copy: true
```

**Example:** Send copy of all orders to audit log

---

## System Management Patterns

### Throttler

**Purpose:** Limit message processing rate

**Use when:**
- Protect downstream system from overload
- Rate limiting required
- Control resource consumption

**Camel Implementation:**
```yaml
- throttle:
    expression:
      constant: 100
    timePeriodMillis: 1000
```

**Example:** Limit to 100 messages per second

---

### Circuit Breaker

**Purpose:** Prevent cascade failures when downstream system fails

**Use when:**
- Calling external/unreliable services
- Need to fail fast
- Prevent resource exhaustion

**Camel Implementation:**
```yaml
- circuitBreaker:
    resilience4jConfiguration:
      failureRateThreshold: 50
      waitDurationInOpenState: 30  # seconds
    steps:
      - to: "https:{{external.api}}"
    onFallback:
      steps:
        - setBody:
            constant: "Service temporarily unavailable"
```

**Example:** Stop calling failed payment service, use fallback

---

## EIP Selection Helper

### By Use Case

| Need to... | Use Pattern |
|------------|-------------|
| Route by message content | Content-Based Router |
| Filter unwanted messages | Message Filter |
| Add data from database | Content Enricher |
| Convert format | Message Translator |
| Split array into items | Splitter |
| Combine multiple messages | Aggregator |
| Prevent duplicate processing | Idempotent Consumer |
| Restore message order | Resequencer |
| Handle failures | Dead Letter Channel |
| Audit messages | Wiretap |
| Limit processing rate | Throttler |
| Handle service failures | Circuit Breaker |

### By Problem

| Problem | Solution |
|---------|----------|
| "Process differently based on type" | Content-Based Router |
| "Only process high-value orders" | Message Filter |
| "Need customer name but only have ID" | Content Enricher |
| "System expects XML, I have JSON" | Message Translator |
| "File has 1000 orders, need individual" | Splitter |
| "Collect responses from 5 services" | Aggregator |
| "Same order processed twice" | Idempotent Consumer |
| "Messages arrive out of order" | Resequencer |
| "What to do with failed messages?" | Dead Letter Channel |
| "Need audit trail" | Wiretap |
| "Too many requests overwhelm system" | Throttler |
| "External service keeps failing" | Circuit Breaker |

---

## Follow-up Questions by Pattern

### After suggesting Content Enricher:
```
Q: "How often do you enrich? Every message or occasionally?"
→ Every message: Consider caching reference data
→ Occasionally: Direct lookup fine

Q: "Does reference data change frequently?"
→ Yes: Short TTL cache or no cache
→ No: Long TTL cache
```

### After suggesting Idempotent Consumer:
```
Q: "Where should we store message IDs?"
→ Memory: Fast but lost on restart (development/testing)
→ Database: Persistent, slower (production with low volume)
→ Redis: Fast and persistent (production with high volume)
→ Kafka: Distributed, scalable (very high volume)

Q: "How long to keep message IDs?"
→ Based on redelivery window (typically 24-72 hours)
```

### After suggesting Aggregator:
```
Q: "How many messages to aggregate?"
→ Fixed count: completionSize
→ Time-based: completionTimeout
→ Both: First condition wins

Q: "What if timeout happens before enough messages?"
→ completionTimeout triggers partial aggregation
```

### After suggesting Circuit Breaker:
```
Q: "What should happen when circuit is open?"
→ Return error to caller
→ Use cached/default value (fallback)
→ Route to alternative service
```

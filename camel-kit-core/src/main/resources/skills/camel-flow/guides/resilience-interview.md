# Resilience Interview Guide

> **Context:** Load this guide after Question 5 (Error Handling) if any of the following conditions apply.

## Question 5b: Circuit Breaker (Conditional)

**Ask ONLY if the source or sink identified in Questions 2 / 4 involves an external HTTP/REST API or remote service (not a local queue or database).**

```
This flow calls an external service ([service name]).
Do you want circuit breaker protection to prevent cascading failures if that service becomes unavailable?

Options:
a) Yes — trip the circuit after repeated failures and use a fallback
b) No — rely on the retry policy defined in error handling
```

**If user selects (a), ask:**
```
What fallback should the route use when the circuit is open?
- Return a default response (describe it)
- Send to an alternative endpoint
- Fail fast with a specific error message
```

Document in TDD under **Section 6: Resilience** (load `skills/camel-flow/guides/performance.md` for circuit breaker configuration reference).

**If user selects (b) or question does not apply:**
→ Skip to Question 5c

---

## Question 5c: Idempotent Consumer (Conditional)

**Ask ONLY if:**
- The source is a message broker (Kafka, JMS, AMQP, etc.), OR
- The user mentioned deduplication, exactly-once, or duplicate messages

```
Does this flow need to guard against processing duplicate messages?
(e.g., the same message delivered more than once by the broker)

a) Yes — use an idempotent consumer to deduplicate
b) No — duplicates are acceptable or handled upstream
```

**If user selects (a), ask:**
```
Where should the idempotent repository be stored?

a) In-memory (development / single instance only — not persistent)
b) Database (JPA — persistent, single-node production)
c) Distributed cache (Infinispan / Hazelcast — clustered environments)

Which field uniquely identifies a message? (e.g., "orderId", "messageId", "CamelKafkaOffset")
```

Document in TDD under **Section 7: Idempotency**.

**If user selects (b) or question does not apply:**
→ Skip to Question 5d

---

## Question 5d: Transactions (Conditional)

**Ask ONLY if the flow writes to more than one external system** (e.g., database AND message broker, two databases, etc.).

```
This flow writes to [system A] and [system B].
Do you need both writes to succeed or fail together (transactional consistency)?

a) Yes — wrap both writes in a transaction
b) No — eventual consistency is acceptable (handle partial failures in error handling)
```

**If user selects (a), ask:**
```
Which transaction manager applies?

a) JTA (distributed transactions across JMS + DB)
b) Spring / Quarkus local transaction (single datasource)
c) Saga pattern (compensating transactions, eventual consistency)
```

Document in TDD under **Section 8: Transactions**. Propagation policies: `PROPAGATION_REQUIRED` (default — join or create), `PROPAGATION_REQUIRES_NEW` (always new), `PROPAGATION_MANDATORY` (must exist). Combine with `onException` + `markRollbackOnly` for rollback on specific exceptions.

**If user selects (b) or question does not apply:**
→ Skip to Question 6

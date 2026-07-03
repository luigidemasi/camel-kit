# Camel Design Patterns — Deployment

> Operations — scalability & availability patterns.

### 15. Service Instance Pattern

**Intent**: Accommodate increasing workloads by distributing loads across multiple service instances.

```yaml
# Competing consumers (each instance gets subset of messages)
- route:
    id: competing-consumer
    from:
      uri: jms:queue:orders?concurrentConsumers=5
      steps:
        - to:
            uri: bean:orderProcessor

# Kafka consumer group (partition-based)
- route:
    id: kafka-consumer
    from:
      uri: kafka:orders?groupId=order-processors
      steps:
        - to:
            uri: bean:orderProcessor
```

**Kafka Consumer Scaling:**

| Configuration | Effect |
|---------------|--------|
| `consumersCount=N` | N consumer threads per Camel instance |
| `consumers > partitions` | Some consumers idle |
| `consumers = partitions` | Optimal parallel processing |

**Pattern for Kubernetes:** Use `consumersCount=1` and scale via pod replicas.

---

### 16. Singleton Service Pattern

**Intent**: Ensure only a single instance of a service is active at a time.

**Use Cases**: File polling (prevent duplicate processing), scheduled jobs, master election.

```yaml
- route:
    id: singleton-route
    routePolicyRef: "#clusterServicePolicy"
    autoStartup: false
    from:
      uri: file:incoming
      steps:
        - to:
            uri: direct:process
```

**Options**: Kubernetes Leader Election, Hazelcast/Infinispan distributed lock, database-based locking.

---

### 17. Load Levelling Pattern

**Intent**: Handle peak loads by introducing temporal decoupling with message queues.

```yaml
# Producer (accepts requests immediately)
- route:
    id: order-receiver
    from:
      uri: platform-http:/orders
      steps:
        - to:
            uri: kafka:order-queue
        - transform:
            constant: '{"status": "accepted"}'

# Consumer (processes at own pace)
- route:
    id: order-processor
    from:
      uri: kafka:order-queue
      steps:
        - to:
            uri: bean:orderProcessor
```

---

### 18. Parallel Pipeline Pattern

**Intent**: Enable concurrent execution of multistep processes while maintaining message order where needed.

```yaml
- route:
    id: ingest
    from:
      uri: kafka:orders
      steps:
        - to:
            uri: seda:validate?concurrentConsumers=5

- route:
    id: validate
    from:
      uri: seda:validate
      steps:
        - to:
            uri: bean:validator
        - to:
            uri: seda:enrich?concurrentConsumers=10
```

**Considerations**: Flows must be stateless, message order not guaranteed across parallel stages.

---

### 19. Bulkhead Pattern

**Intent**: Enforce resource partitioning and damage containment to preserve partial functionality during failures.

| Level | Implementation |
|-------|----------------|
| Physical | Separate data centers |
| Host | Kubernetes replicas |
| Process | Separate pods |
| Thread Pool | Camel Thread Pool Profiles |

```yaml
- beans:
    - name: criticalThreadPool
      type: org.apache.camel.spi.ThreadPoolProfile
      properties:
        poolSize: 20
        maxPoolSize: 50
        maxQueueSize: 100

- route:
    id: critical-processing
    from:
      uri: kafka:critical-orders
      steps:
        - threads:
            executorService: "#criticalThreadPool"
            steps:
              - to:
                  uri: direct:process
```

---

### 20. Service Consolidation Pattern

**Intent**: Provide guidelines for grouping or isolating services for deployment.

| Model | Trade-offs |
|-------|------------|
| **Single Service per Host** | High isolation, higher cost |
| **Multiple Services per Host** | Lower cost, shared failures |
| **Application Container** | Efficient, complex management |

**Camel Guidance**: Design routes without co-location assumptions, use network protocols over in-process for flexibility.

---

## Pattern Selection Guide

### By Non-Functional Requirement

| Requirement | Primary Patterns |
|-------------|------------------|
| **Scalability** | Service Instance, Load Levelling, Parallel Pipeline, CQRS |
| **Availability** | Circuit Breaker, Retry, Singleton Service |
| **Stability** | Throttling, Bulkhead, Circuit Breaker, Retry |
| **Integrity** | Data Integrity, Saga, Idempotent Filter, Error Channel |
| **Extensibility** | VETRO, Canonical Data Model, Reusable Route, Edge Component |
| **Configurability** | External Configuration, Runtime Reconfiguration |

### By Problem

| Problem | Recommended Pattern(s) |
|---------|------------------------|
| Duplicate messages | Idempotent Filter |
| Cascading failures | Circuit Breaker, Bulkhead |
| Traffic spikes | Throttling, Load Levelling |
| Long-running transactions | Saga |
| Complex message processing | VETRO |
| Multiple data formats | Canonical Data Model, Edge Component |

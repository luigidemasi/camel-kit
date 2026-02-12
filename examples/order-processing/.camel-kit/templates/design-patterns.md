# Camel Design Patterns Reference

> Based on "Camel Design Patterns" by Bilgin Ibryam. This reference provides actionable guidance for applying these patterns in Apache Camel integrations.

---

## Document Control

| Property | Value |
|----------|-------|
| Version | 1.0 |
| Last Updated | {{DATE}} |
| Source | Bilgin Ibryam - Camel Design Patterns |

---

## Pattern Categories

| Category | Focus | Patterns |
|----------|-------|----------|
| **Foundational** | Happy paths - Structure & extensibility | VETRO, Canonical Data Model, Edge Component, CQRS, Reusable Route, Runtime Reconfiguration, External Configuration |
| **Error Handling** | Unhappy paths - Stability & integrity | Data Integrity, Saga, Idempotent Filter, Retry, Throttling, Circuit Breaker, Error Channel |
| **Deployment** | Operations - Scalability & availability | Service Instance, Singleton Service, Load Levelling, Parallel Pipeline, Bulkhead, Service Consolidation |

---

## Foundational Patterns

### 1. VETRO Pattern

**Intent**: Combine multiple sequential actions on a message into a consistent structure with well-defined responsibilities.

**Structure**:
```
Validate → Enrich → Transform → Route → Operate
```

| Phase | Purpose | Example EIPs |
|-------|---------|--------------|
| **V**alidate | Check message structure and content | Validate, Filter |
| **E**nrich | Add context from external sources | Enrich, PollEnrich |
| **T**ransform | Convert to required format | Transform, Marshal |
| **R**oute | Direct to appropriate destination | Choice, RecipientList |
| **O**perate | Execute the business action | To, Bean |

**Camel Implementation**:
```yaml
- route:
    id: order-processing
    from:
      uri: kafka:orders
    steps:
      # V - Validate
      - unmarshal:
          json: {}
      - to:
          uri: json-validator:schemas/order.json
      # E - Enrich
      - enrich:
          expression:
            simple: http:customer-service/{{body.customerId}}
          aggregationStrategy: "#customerMerger"
      # T - Transform
      - transform:
          expression:
            simple: "..."
      # R - Route
      - choice:
          when:
            - simple: "${body.priority} == 'high'"
              steps:
                - to: direct:priority-processing
          otherwise:
            steps:
              - to: direct:standard-processing
      # O - Operate
      - to:
          uri: jpa:Order
```

**When to Apply**:
- Processing external messages from untrusted sources
- Complex transformations requiring multiple steps
- Routes that need clear separation of concerns

---

### 2. Canonical Data Model Pattern

**Intent**: Minimize dependencies between applications by using a common data format with an additional level of indirection.

**Problem**: N applications communicating directly requires N×(N-1) transformations. With a canonical model, only 2×N transformations are needed.

**Camel Implementation**:
```yaml
# Inbound: External format → Canonical
- route:
    id: legacy-to-canonical
    from:
      uri: file:legacy-orders
    steps:
      - unmarshal:
          csv: {}
      - transform:
          expression:
            simple: "..."  # Map to canonical Order
      - to:
          uri: direct:process-order

# Core processing uses canonical format
- route:
    id: process-order
    from:
      uri: direct:process-order
    steps:
      - to:
          uri: bean:orderService

# Outbound: Canonical → External format
- route:
    id: canonical-to-partner
    from:
      uri: direct:send-to-partner
    steps:
      - transform:
          expression:
            simple: "..."  # Map from canonical to partner format
      - marshal:
          json: {}
      - to:
          uri: http:partner-api
```

**Best Practices**:
- Define canonical schemas in `schemas/canonical/`
- Keep canonical model stable; evolve carefully
- Use Data Mapper EIP for complex transformations
- Consider JSON Schema or Avro for schema definition

---

### 3. Edge Component Pattern

**Intent**: Encapsulate endpoint-specific details and prevent them from leaking into the business logic.

**Problem**: Business logic becomes coupled to specific protocols, data formats, or transport mechanisms.

**Camel Implementation**:
```yaml
# Edge route handles protocol specifics
- route:
    id: rest-edge
    from:
      uri: platform-http:/orders
    steps:
      - unmarshal:
          json: {}
      - removeHeaders:
          pattern: "CamelHttp*"
      - to:
          uri: direct:order-business-logic
      - marshal:
          json: {}

# Business logic route is protocol-agnostic
- route:
    id: order-business-logic
    from:
      uri: direct:order-business-logic
    steps:
      - to:
          uri: bean:orderValidator
      - to:
          uri: bean:orderProcessor
```

**Best Practices**:
- Edge routes handle: protocol details, authentication, rate limiting, format conversion
- Business routes handle: validation, transformation, business rules
- Use `direct:` to connect edge to business routes
- Keep edge routes thin; push logic to business routes

---

### 4. CQRS Pattern (Command Query Responsibility Segregation)

**Intent**: Decouple read from write operations to allow them to evolve independently.

**Problem**: Single model for reads and writes creates contention and complexity.

**Camel Implementation**:
```yaml
# Command route (writes)
- route:
    id: order-command
    from:
      uri: kafka:order-commands
    steps:
      - choice:
          when:
            - simple: "${header.commandType} == 'CREATE'"
              steps:
                - to: direct:create-order
            - simple: "${header.commandType} == 'UPDATE'"
              steps:
                - to: direct:update-order
      - to:
          uri: kafka:order-events  # Publish events for read side

# Query route (reads)
- route:
    id: order-query
    from:
      uri: platform-http:/orders/{id}
    steps:
      - to:
          uri: sql:SELECT * FROM order_view WHERE id = :#id
```

**When to Apply**:
- High read/write ratio with different performance needs
- Complex domains where read/write models diverge
- Event-sourced systems

---

### 5. Reusable Route Pattern

**Intent**: Create agnostic business logic that can be repeatedly used in different service contexts.

**Camel Implementation**:
```yaml
# Reusable route template
- routeTemplate:
    id: validation-template
    parameters:
      - name: schemaPath
      - name: errorQueue
    route:
      from:
        uri: direct:validate
      steps:
        - doTry:
            steps:
              - to:
                  uri: json-validator:{{schemaPath}}
          doCatch:
            - exception: com.networknt.schema.JsonSchemaException
              steps:
                - to:
                    uri: kafka:{{errorQueue}}
                - stop: {}

# Usage
- templatedRoute:
    routeTemplateRef: validation-template
    parameters:
      - name: schemaPath
        value: schemas/order.json
      - name: errorQueue
        value: order-validation-errors
```

**Best Practices**:
- Use Route Templates for parameterized reusable routes
- Keep reusable routes stateless
- Document parameters and expected input/output formats
- Test reusable routes independently

---

### 6. Runtime Reconfiguration Pattern

**Intent**: Allow runtime variability of behavior without requiring application redeployment.

**Camel Implementation**:
```yaml
# Dynamic routing based on runtime configuration
- route:
    id: dynamic-router
    from:
      uri: kafka:orders
    steps:
      - recipientList:
          expression:
            method:
              ref: routingService
              method: getTargets
          parallelProcessing: true

# Dynamic throttling
- route:
    id: throttled-route
    from:
      uri: direct:process
    steps:
      - throttle:
          expression:
            method:
              ref: configService
              method: getThrottleRate
```

**Mechanisms**:
| Mechanism | Use Case | Camel Support |
|-----------|----------|---------------|
| Property Refresh | Change thresholds, timeouts | Camel Property Placeholder with refresh |
| Dynamic Router | Change routing targets | Recipient List, Dynamic Router EIP |
| Control Bus | Start/stop routes at runtime | ControlBus component |
| JMX | Monitor and manage | Camel JMX |

---

### 7. External Configuration Pattern

**Intent**: Parameterize application configuration and externalize it from the deployment archive.

**Camel Implementation**:
```yaml
# Route using externalized configuration
- route:
    id: order-ingestion
    from:
      uri: kafka:{{kafka.topic.orders}}
      parameters:
        brokers: "{{kafka.brokers}}"
        groupId: "{{kafka.consumer.group}}"
    steps:
      - to:
          uri: "{{database.url}}"
```

**Configuration Hierarchy** (highest priority first):
1. Environment variables: `KAFKA_BROKERS`
2. System properties: `-Dkafka.brokers=...`
3. External file: `application.properties`
4. Bundled defaults: `application-default.properties`

**Best Practices**:
- Never hardcode secrets; use `{{ENV_VAR}}` placeholders
- Document all required configuration in `.env.example`
- Use profiles for environment-specific config
- Validate configuration at startup

---

## Error Handling Patterns

### 8. Data Integrity Pattern

**Intent**: Maintain data consistency and business integrity when processing fails.

**Transaction Strategies**:

| Strategy | Description | Use Case |
|----------|-------------|----------|
| **No Transaction** | Default behavior | Read-only operations, idempotent writes |
| **Local Transaction** | Single resource transaction | Single database or JMS broker |
| **Global Transaction (XA)** | Distributed transaction | Multiple resources requiring atomicity |
| **Idempotent Alternative** | Avoid XA with idempotency | When XA overhead is unacceptable |

**Camel Implementation**:
```yaml
# Local transaction
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

# XA transaction (requires transaction manager)
- route:
    id: xa-route
    from:
      uri: jms:queue:orders?transacted=true
    steps:
      - transacted:
          ref: PROPAGATION_REQUIRED
      - to:
          uri: jpa:Order
```

**Guidance**:
- Prefer local transactions when possible
- Use XA only when absolutely necessary (performance overhead)
- Consider Saga pattern as XA alternative for long-running processes

---

### 9. Saga Pattern

**Intent**: Manage long-running business processes with compensating transactions instead of distributed locks.

**Problem**: XA transactions don't scale for long-running processes or when some participants don't support XA.

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

**When to Apply**:
- Long-running business processes (minutes to days)
- Cross-service transactions
- When XA is not available or too expensive
- Event-driven architectures

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
| `InfinispanIdempotentRepository` | High-performance distributed | Yes |

**Key Selection**:
- Use business keys (orderId, invoiceNumber) over technical IDs
- Include source system in key if messages come from multiple sources
- Consider TTL for key expiration

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

**Retry Levels**:

| Level | Description | Configuration |
|-------|-------------|---------------|
| **Camel Redelivery** | Retries failed endpoint | `errorHandler` with `redeliveryPolicy` |
| **Consumer Redelivery** | Broker redelivers message | JMS/Kafka consumer settings |
| **Broker Redelivery** | Scheduled redelivery from DLQ | ActiveMQ Broker Redelivery Plugin |

**Best Practices**:
- Keep retry delays short (thread is blocked)
- Use exponential backoff to avoid thundering herd
- Set maximum redeliveries based on transient failure expectations
- Use DLQ for persistent failures

---

### 12. Throttling Pattern

**Intent**: Control throughput to meet SLAs and prevent overloading downstream systems.

**Strategies**:

| Strategy | Description | Camel Support |
|----------|-------------|---------------|
| **Reject** | Return error immediately | `throttle` with `rejectExecution` |
| **Block** | Wait until capacity available | `throttle` (default behavior) |
| **Delay** | Queue and process later | `seda` with bounded queue |
| **Degrade** | Reduce functionality | Custom with `choice` |

**Camel Implementation**:
```yaml
# Request-based throttling
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

# Inflight-based throttling
- route:
    id: inflight-throttled
    routePolicy: "#throttlingInflightPolicy"
    from:
      uri: kafka:orders
    steps:
      - to:
          uri: http:slow-service
```

---

### 13. Circuit Breaker Pattern

**Intent**: Protect routes from cascading failures and slow responses from external systems.

**States**:
```
CLOSED → (failures exceed threshold) → OPEN → (wait duration) → HALF-OPEN → (success) → CLOSED
                                                                          ↓ (failure)
                                                                         OPEN
```

**Camel Implementation**:
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

**Configuration Guidelines**:

| Parameter | Description | Typical Value |
|-----------|-------------|---------------|
| `failureRateThreshold` | % of failures to trip | 50% |
| `waitDurationInOpenState` | Cooldown before retry | 10-60 seconds |
| `slidingWindowSize` | Sample size for failure rate | 10-100 calls |
| `slowCallDurationThreshold` | What counts as "slow" | Based on SLA |

---

### 14. Error Channel Pattern

**Intent**: Handle errors appropriately based on error type and conversation style.

**Channel Types**:

| Channel | Purpose | Implementation |
|---------|---------|----------------|
| **Dead Letter Channel** | Store failed messages for manual intervention | `deadLetterChannel` |
| **Invalid Message Channel** | Route malformed messages | `onException` with `handled(true)` |
| **Back Out Channel** | Return message to origin for retry | Broker-level configuration |

**Camel Implementation**:
```yaml
- route:
    id: error-handling-route
    # Dead Letter Channel for unhandled errors
    errorHandler:
      deadLetterChannel:
        deadLetterUri: kafka:order-dlq
        useOriginalMessage: true
    from:
      uri: kafka:orders
    steps:
      # Invalid Message Channel for validation errors
      - onException:
          exception: com.example.ValidationException
          handled: true
          steps:
            - to:
                uri: kafka:invalid-orders
      # Processing
      - to:
          uri: bean:orderValidator
      - to:
          uri: direct:process
```

**DLQ Best Practices**:
- Include original message, exception details, timestamp
- Set up monitoring/alerting on DLQ depth
- Create reprocessing mechanism
- Consider per-domain DLQs for easier triaging

---

## Deployment Patterns

### 15. Service Instance Pattern

**Intent**: Accommodate increasing workloads by distributing loads across multiple service instances.

**Camel Support**:
- **Competing Consumers**: Multiple consumers on same queue
- **Partitioned Processing**: Kafka partitions with consumer groups
- **Load Balancer**: Round-robin, weighted, or custom balancing

**Implementation**:
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

---

### 16. Singleton Service Pattern

**Intent**: Ensure only a single instance of a service is active at a time.

**Use Cases**:
- File polling (prevent duplicate processing)
- Scheduled jobs (prevent duplicate execution)
- Master election scenarios

**Camel Implementation**:
```yaml
# Using route policy for singleton
- route:
    id: singleton-route
    routePolicy: "#clusterServicePolicy"
    autoStartup: false
    from:
      uri: file:incoming
    steps:
      - to:
          uri: direct:process

# Kubernetes-based singleton (using leader election)
- route:
    id: k8s-singleton
    routePolicy: "#kubernetesClusterService"
    from:
      uri: timer:scheduler?period=60000
    steps:
      - to:
          uri: direct:scheduled-job
```

**Implementation Options**:
- Kubernetes Leader Election
- Hazelcast/Infinispan distributed lock
- Database-based locking
- ZooKeeper coordination

---

### 17. Load Levelling Pattern

**Intent**: Handle peak loads and slow-running tasks by introducing temporal decoupling with message queues.

**Problem**: Synchronous processing can't handle traffic spikes; slow consumers block producers.

**Camel Implementation**:
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

**Queue Sizing**:
- Size based on expected peak duration × (peak rate - processing rate)
- Monitor queue depth; alert on sustained growth
- Consider backpressure mechanisms

---

### 18. Parallel Pipeline Pattern

**Intent**: Enable concurrent execution of multistep processes while maintaining message order where needed.

**Camel Implementation**:
```yaml
# Parallel processing with SEDA
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

- route:
    id: enrich
    from:
      uri: seda:enrich
    steps:
      - to:
          uri: bean:enricher
      - to:
          uri: seda:persist?concurrentConsumers=3

- route:
    id: persist
    from:
      uri: seda:persist
    steps:
      - to:
          uri: jpa:Order
```

**Considerations**:
- Flows must be stateless or use distributed state
- Message order not guaranteed across parallel stages
- Use content-based routing to direct to sharded resources
- Monitor each stage independently

---

### 19. Bulkhead Pattern

**Intent**: Enforce resource partitioning and damage containment to preserve partial functionality during failures.

**Bulkhead Levels**:

| Level | Description | Implementation |
|-------|-------------|----------------|
| Physical | Separate data centers | Infrastructure |
| Host | Redundant VMs | Kubernetes replicas |
| Process | Isolated containers | Separate pods |
| Thread Pool | Isolated thread pools | Camel Thread Pool Profiles |

**Camel Implementation**:
```yaml
# Separate thread pools for critical vs. non-critical
- beans:
    - name: criticalThreadPool
      type: org.apache.camel.spi.ThreadPoolProfile
      properties:
        poolSize: 20
        maxPoolSize: 50
        maxQueueSize: 100

    - name: batchThreadPool
      type: org.apache.camel.spi.ThreadPoolProfile
      properties:
        poolSize: 5
        maxPoolSize: 10
        maxQueueSize: 1000

# Critical route with dedicated thread pool
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

# Batch route with separate thread pool
- route:
    id: batch-processing
    from:
      uri: kafka:batch-orders
    steps:
      - threads:
          executorService: "#batchThreadPool"
          steps:
            - to:
                uri: direct:process
```

**Related Patterns**:
- Circuit Breaker: Fail fast when downstream is unhealthy
- Throttling: Control resource consumption
- Load Levelling: Queue-based isolation

---

### 20. Service Consolidation Pattern

**Intent**: Provide guidelines for grouping services together or isolating them for deployment.

**Deployment Models**:

| Model | Description | Trade-offs |
|-------|-------------|------------|
| **Single Service per Host** | One service = one VM/container | High isolation, higher cost |
| **Multiple Services per Host** | Shared VM/container | Lower cost, shared failures |
| **Application Container** | Multiple services in one process | Efficient, complex management |

**Consolidation Criteria**:

| Category | Considerations |
|----------|----------------|
| **Non-functional** | Scalability, availability, security requirements |
| **Resource Profile** | CPU, memory, network needs |
| **Runtime Dependencies** | Shared libraries, services |
| **Lifecycle** | Release cadence, lifetime |
| **Maintenance** | Management overhead, complexity |

**Camel Guidance**:
- **Smallest Deployment Unit**: A CamelContext with routes in a JAR
- Design routes without assumptions about co-location
- Use network protocols (HTTP, JMS) over in-process (VM, direct-vm) for flexibility
- Apply CQRS: separate read/write routes into different deployment units

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
| **Deployability** | Service Consolidation, Service Instance |

### By Problem

| Problem | Recommended Pattern(s) |
|---------|------------------------|
| Duplicate messages | Idempotent Filter |
| Cascading failures | Circuit Breaker, Bulkhead |
| Traffic spikes | Throttling, Load Levelling |
| Long-running transactions | Saga |
| External service slowness | Circuit Breaker, Retry with timeout |
| Complex message processing | VETRO |
| Multiple data formats | Canonical Data Model, Edge Component |
| High availability requirement | Singleton Service, Service Instance |

---

## References

- [Camel Design Patterns](https://leanpub.com/camel-design-patterns) by Bilgin Ibryam
- [Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/)
- [Apache Camel Documentation](https://camel.apache.org/manual/)
- [Release It!](https://pragprog.com/titles/mnee2/release-it-second-edition/) by Michael T. Nygard

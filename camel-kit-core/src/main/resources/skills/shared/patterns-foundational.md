# Camel Design Patterns — Foundational

> Structure & extensibility patterns for happy paths.

## Pattern Categories

| Category | Focus | Patterns |
|----------|-------|----------|
| **Foundational** | Happy paths - Structure & extensibility | VETRO, Canonical Data Model, Edge Component, CQRS, Reusable Route, Runtime Reconfiguration, External Configuration |
| **Error Handling** | Unhappy paths - Stability & integrity | Data Integrity, Saga, Idempotent Filter, Retry, Throttling, Circuit Breaker, Error Channel |
| **Deployment** | Operations - Scalability & availability | Service Instance, Singleton Service, Load Levelling, Parallel Pipeline, Bulkhead, Service Consolidation |

---

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
              simple: "http:customer-service/${body.customerId}"
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

---

### 3. Edge Component Pattern

**Intent**: Encapsulate endpoint-specific details and prevent them from leaking into the business logic.

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

---

### 4. CQRS Pattern

**Intent**: Decouple read from write operations to allow them to evolve independently.

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
            uri: kafka:order-events

# Query route (reads)
- route:
    id: order-query
    from:
      uri: platform-http:/orders/{id}
      steps:
        - to:
            uri: sql:SELECT * FROM order_view WHERE id = :#id
```

**When to Apply**: High read/write ratio, complex domains, event-sourced systems.

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

---

### 6. Runtime Reconfiguration Pattern

**Intent**: Allow runtime variability of behavior without requiring application redeployment.

| Mechanism | Use Case | Camel Support |
|-----------|----------|---------------|
| Property Refresh | Change thresholds, timeouts | Camel Property Placeholder with refresh |
| Dynamic Router | Change routing targets | Recipient List, Dynamic Router EIP |
| Control Bus | Start/stop routes at runtime | ControlBus component |
| JMX | Monitor and manage | Camel JMX |

---

### 7. External Configuration Pattern

**Intent**: Parameterize application configuration and externalize it from the deployment archive.

**Configuration Hierarchy** (highest priority first):
1. Environment variables: `KAFKA_BROKERS`
2. System properties: `-Dkafka.brokers=...`
3. External file: `application.properties`
4. Bundled defaults: `application-default.properties`

**Best Practices**:
- Never hardcode secrets; use `{{ENV_VAR}}` placeholders
- Document all required configuration in `.env.example`
- Use profiles for environment-specific config

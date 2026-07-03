# Camel YAML DSL — EIP & Component Patterns

EIP transformations and component usage patterns for Camel YAML DSL.

---

## EIP Transformations

### Filter

```yaml
- filter:
    simple: "${body.totalAmount} >= 50"
```

With nested steps:
```yaml
- filter:
    simple: "${body.status} == 'active'"
    steps:
      - log:
          message: "Processing active order"
      - to:
          uri: direct:process
```

### Choice (Content-Based Router)

```yaml
- choice:
    when:
      - simple: "${body.priority} == 'high'"
        steps:
          - to:
              uri: direct:fast-track
      - simple: "${body.priority} == 'low'"
        steps:
          - to:
              uri: direct:batch
    otherwise:
      steps:
        - to:
            uri: direct:standard
```

### Split

Simple split:
```yaml
- split:
    simple: "${body.items}"
    steps:
      - to:
          uri: direct:process-item
```

With streaming and parallel:
```yaml
- split:
    tokenize:
      token: "\n"
    streaming: true
    parallelProcessing: true
    steps:
      - to:
          uri: direct:process-line
```

With JSONPath:
```yaml
- split:
    jsonpath: "$.items[*]"
    steps:
      - to:
          uri: direct:process-item
```

### Aggregate

Size-based:
```yaml
- aggregate:
    correlationExpression:
      simple: "${header.orderId}"
    completionSize: 10
    aggregationStrategy: "#groupedBodyStrategy"
    steps:
      - to:
          uri: direct:process-batch
```

Timeout-based:
```yaml
- aggregate:
    correlationExpression:
      simple: "${header.batchId}"
    completionTimeout: 5000
    aggregationStrategy: "#myAggregator"
    steps:
      - to:
          uri: direct:complete-batch
```

### Enrich

```yaml
- enrich:
    simple: "direct:customer-lookup"
    aggregationStrategy: "#customerEnricher"
```

Poll enrich:
```yaml
- pollEnrich:
    simple: "file:data/lookup?fileName=${header.lookupFile}"
    timeout: 5000
    aggregationStrategy: "#fileEnricher"
```

### Transform / SetBody

```yaml
- setBody:
    simple: "Order ${body.orderId} processed successfully"

- setBody:
    constant: "OK"

- transform:
    jq: '{id: .orderId, status: "processed", timestamp: now}'
```

### SetHeader

```yaml
- setHeader:
    name: correlationId
    simple: "${exchangeId}"
- setHeader:
    name: processedAt
    simple: "${date:now:yyyy-MM-dd'T'HH:mm:ss}"
```

### Log

```yaml
- log:
    message: "Processing order ${body.orderId}"
    loggingLevel: INFO
    logName: order.processor
```

### To (Send to Endpoint)

Component:
```yaml
- to:
    uri: kafka:processed-orders
    parameters:
      brokers: "{{KAFKA_BROKERS}}"
```

Dynamic:
```yaml
- toD:
    uri: "${header.targetEndpoint}"
```

### Bean

```yaml
- bean:
    ref: orderProcessor
    method: processOrder
```

### Multicast

```yaml
- multicast:
    parallelProcessing: true
    steps:
      - to:
          uri: kafka:audit-log
      - to:
          uri: direct:notification
      - to:
          uri: jpa:Order
```

### Recipient List

```yaml
- recipientList:
    simple: "${header.destinations}"
    delimiter: ","
    parallelProcessing: true
```

### Wire Tap

```yaml
- wireTap:
    uri: kafka:audit-events
- to:
    uri: direct:continue-processing
```

### Validate

With predicate:
```yaml
- validate:
    simple: "${body.orderId} != null"
```

With JSON schema:
```yaml
- to:
    uri: json-validator:schemas/order.json
```

---

## Error Handling

### Dead Letter Channel

```yaml
- route:
    id: order-ingestion
    errorHandler:
      deadLetterChannel:
        deadLetterUri: kafka:orders-dlq
        redeliveryPolicy:
          maximumRedeliveries: 3
          redeliveryDelay: 1000
          backOffMultiplier: 2
          useExponentialBackOff: true
    from:
      uri: kafka:orders
      steps:
        - to:
            uri: jpa:Order
```

### onException

**IMPORTANT:** `handled` and `continued` require an expression, not a boolean.

```yaml
# CORRECT
handled:
  constant:
    expression: "true"
```

```yaml
- onException:
    exception:
      - com.example.ValidationException
    handled:
      constant:
        expression: "true"
    steps:
      - to:
          uri: kafka:orders-invalid
```

### Circuit Breaker (Resilience4j)

```yaml
- circuitBreaker:
    resilience4jConfiguration:
      failureRateThreshold: 50
      waitDurationInOpenState: 10  # seconds
      slidingWindowSize: 10
    steps:
      - to:
          uri: http:external-service/api
    onFallback:
      steps:
        - setBody:
            constant: '{"status": "service unavailable"}'
```

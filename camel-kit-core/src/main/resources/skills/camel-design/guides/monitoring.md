# Monitoring & Observability Guide

## When to Load This Guide

Load when user mentions:
- Monitoring / metrics
- Logging / debugging
- Tracing / observability
- Alerts / health checks

---

## Correlation IDs

### Purpose
Track a single message through entire flow across systems

### Implementation

**Generate at entry:**
```yaml
- setHeader:
    name: X-Correlation-ID
    simple: "${header.X-Correlation-ID} != null ? ${header.X-Correlation-ID} : ${exchangeId}"
```

**Log with correlation ID:**
```yaml
- log:
    message: "[${header.X-Correlation-ID}] Processing order ${body.orderId}"
```

**Propagate downstream:**
```yaml
- setHeader:
    name: CamelHttpHeader.X-Correlation-ID
    simple: "${header.X-Correlation-ID}"
```

---

## Metrics

### Key Metrics to Track

| Metric | Type | Purpose |
|--------|------|---------|
| {flow}.messages.total | Counter | Total messages processed |
| {flow}.messages.success | Counter | Successfully processed |
| {flow}.messages.failed | Counter | Failed messages |
| {flow}.processing.duration | Histogram | Processing time distribution |
| {flow}.dlq.depth | Gauge | Messages in DLQ |

### Prometheus Configuration

```properties
management.metrics.export.prometheus.enabled=true
management.endpoints.web.exposure.include=prometheus,health,metrics
camel.metrics.enabled=true
```

### Grafana Dashboard

Create dashboard with:
- Message throughput (msg/sec)
- Processing latency (p50, p95, p99)
- Error rate (%)
- DLQ depth over time

---

## Logging

### Structured Logging

**Format:**
```json
{
  "timestamp": "2025-02-21T10:30:00Z",
  "level": "INFO",
  "flow": "order-processing",
  "correlationId": "uuid-here",
  "step": "validation",
  "message": "Order validated",
  "orderId": "ORD-123",
  "processingTime": 15
}
```

### Log Levels

**INFO:** Key business events
- Message received
- Message sent
- Processing complete

**DEBUG:** Detailed processing
- Intermediate values
- Transformation details
- Decision points

**WARN:** Recoverable issues
- Retries
- Degraded performance
- Non-critical failures

**ERROR:** Failures
- Validation failures
- Processing exceptions
- DLQ sends

### Sensitive Data Masking

```properties
logging.mask.fields=creditCard,ssn,password,apiKey,email
```

**Example:**
```
Before: Processing order for user@example.com
After:  Processing order for u***@***.com
```

---

## Distributed Tracing

### OpenTelemetry

**Enable:**
```properties
otel.traces.exporter=jaeger
otel.exporter.jaeger.endpoint=http://jaeger:14250
otel.service.name=order-processing
```

**Trace Spans:**
- Route entry/exit
- External calls (HTTP, database)
- Transformations
- Error handling

**Span Attributes:**
```
- flow.name: order-processing
- message.id: MSG-123
- correlation.id: uuid
- processing.time.ms: 45
```

---

## Health Checks

### Liveness Probe

**Check:** Is route running?

**Endpoint:** `/health/liveness`

**Failure Action:** Restart pod/service

**Implementation:**
```properties
management.endpoint.health.enabled=true
camel.health.enabled=true
camel.health.check.routes.enabled=true
```

---

### Readiness Probe

**Check:** Can accept traffic?

**Validates:**
- Source system reachable
- Sink system reachable
- Dependencies healthy

**Endpoint:** `/health/readiness`

**Failure Action:** Remove from load balancer

**Custom Check:**
```java
@Component
public class DatabaseReadinessCheck extends AbstractHealthCheck {
    protected void doCall(HealthCheckResultBuilder builder) {
        // Check database connection
        if (dataSource.isValid()) {
            builder.up();
        } else {
            builder.down();
        }
    }
}
```

---

## Alerting

### Alert Conditions

| Condition | Threshold | Severity | Action |
|-----------|-----------|----------|--------|
| Error rate | > 5% | High | Page on-call |
| DLQ depth | > 100 | Medium | Notify team |
| Latency | > 5s | Medium | Notify team |
| Route stopped | N/A | Critical | Page on-call |
| Downstream unavailable | N/A | High | Page on-call |

### Alert Configuration

**Prometheus AlertManager:**
```yaml
groups:
  - name: order-processing
    rules:
      - alert: HighErrorRate
        expr: rate(order_processing_messages_failed[5m]) > 0.05
        for: 5m
        labels:
          severity: high
        annotations:
          summary: "High error rate in order processing"
```

---

## Observability Checklist

- [ ] Correlation ID: [Generated at entry, propagated]
- [ ] Metrics exposed: [Prometheus format]
- [ ] Key metrics tracked: [Throughput, latency, errors, DLQ depth]
- [ ] Structured logging: [JSON format]
- [ ] Log levels appropriate: [INFO for key events, DEBUG for details]
- [ ] Sensitive data masked: [Yes, fields: ...]
- [ ] Distributed tracing: [OpenTelemetry enabled]
- [ ] Liveness probe: [/health/liveness]
- [ ] Readiness probe: [/health/readiness checks dependencies]
- [ ] Alerts configured: [Error rate, DLQ depth, latency, route health]
- [ ] Dashboard created: [Grafana with key metrics]

# Anypoint Monitoring → Camel Observability Migration Guide

This guide maps MuleSoft Anypoint Monitoring capabilities to their open-source equivalents in the Camel ecosystem. Use it during Phase 2 when the BRD identifies observability or monitoring requirements.

---

## Monitoring Stack Comparison

| Capability | Anypoint Platform | Camel Equivalent | Notes |
|-----------|-------------------|------------------|-------|
| Metrics dashboard | Anypoint Monitoring | Prometheus + Grafana | Camel exposes metrics via Micrometer |
| Distributed tracing | Anypoint Visualizer | OpenTelemetry + Jaeger/Zipkin | Camel has built-in OpenTelemetry support |
| Log aggregation | Anypoint Logging | ELK (Elasticsearch, Logstash, Kibana) or Loki | Standard structured logging via SLF4J/Logback |
| Alerting | Anypoint Alerts | Prometheus Alertmanager or Grafana Alerts | Threshold and anomaly-based alerts |
| API analytics | Anypoint API Analytics | Prometheus + custom dashboards | Track request count, latency, error rate per route |
| Health checks | CloudHub status | Camel Health Checks + Kubernetes probes | Liveness and readiness endpoints |
| Custom notifications | Anypoint Notifications | Camel routes (email, Slack, PagerDuty) | Build notification routes with standard Camel components |

---

## Camel Micrometer Metrics

Camel integrates with Micrometer for metrics collection. Add the dependency and metrics are automatically collected for every route.

### Dependencies

| Runtime | Maven Dependency |
|---------|-----------------|
| Camel Quarkus | `org.apache.camel.quarkus:camel-quarkus-micrometer` |
| Camel Spring Boot | `org.apache.camel.springboot:camel-micrometer-starter` |
| Camel standalone | `org.apache.camel:camel-micrometer` |

### Configuration (`application.properties`)

```properties
# Enable Camel metrics (Quarkus)
quarkus.camel.metrics.enable-route-policy=true
quarkus.camel.metrics.enable-message-history=false
quarkus.camel.metrics.enable-exchange-event-notifier=true
quarkus.camel.metrics.enable-route-event-notifier=true

# Enable Camel metrics (Spring Boot)
camel.component.micrometer.enabled=true
management.endpoints.web.exposure.include=health,info,prometheus
management.metrics.export.prometheus.enabled=true
```

### Auto-collected Metrics

| Metric Name | Type | Description | Anypoint Equivalent |
|-------------|------|-------------|---------------------|
| `camel.exchanges.total` | counter | Total exchanges processed | Message count |
| `camel.exchanges.failed` | counter | Failed exchanges | Error count |
| `camel.exchanges.duration` | timer | Exchange processing time | Response time |
| `camel.routes.running` | gauge | Currently running routes | Active flows |
| `camel.exchanges.inflight` | gauge | In-flight exchanges | Pending messages |

---

## OpenTelemetry Tracing

Replace Anypoint Visualizer with distributed tracing via OpenTelemetry.

### Dependencies

| Runtime | Maven Dependency |
|---------|-----------------|
| Camel Quarkus | `org.apache.camel.quarkus:camel-quarkus-opentelemetry` |
| Camel Spring Boot | `org.apache.camel.springboot:camel-opentelemetry-starter` |

### Configuration (`application.properties`)

```properties
# OpenTelemetry exporter (Quarkus)
quarkus.otel.exporter.otlp.traces.endpoint=http://jaeger:4317
quarkus.otel.service.name=${camel.main.name}

# OpenTelemetry exporter (Spring Boot)
otel.exporter.otlp.endpoint=http://jaeger:4317
otel.resource.attributes=service.name=${spring.application.name}
```

### Trace Propagation

Camel automatically propagates trace context across:
- HTTP headers (`traceparent`, `tracestate`)
- JMS/Kafka message headers
- Direct/SEDA in-process routes

No manual instrumentation needed — the OpenTelemetry component decorates all routes.

---

## Health Checks

Replace CloudHub status monitoring with Camel Health Checks.

### Configuration (`application.properties`)

```properties
# Camel health checks (Quarkus)
quarkus.health.enabled=true
camel.health.enabled=true
camel.health.routes-enabled=true
camel.health.consumers-enabled=true

# Camel health checks (Spring Boot)
management.endpoint.health.show-details=always
camel.health.enabled=true
camel.health.routes-enabled=true
```

### Kubernetes Integration

```yaml
# Add to Kubernetes deployment spec
livenessProbe:
  httpGet:
    path: /q/health/live      # Quarkus
    # path: /actuator/health/liveness  # Spring Boot
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 30
readinessProbe:
  httpGet:
    path: /q/health/ready     # Quarkus
    # path: /actuator/health/readiness  # Spring Boot
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 10
```

---

## Structured Logging

Replace Anypoint log search with structured JSON logging.

### Configuration (Quarkus — `application.properties`)

```properties
quarkus.log.console.json=true
quarkus.log.console.json.additional-field.app.value=${camel.main.name}
quarkus.log.console.json.additional-field.env.value=${ENVIRONMENT:dev}
```

### Configuration (Spring Boot — `logback-spring.xml`)

```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
  <encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <customFields>{"app":"${spring.application.name}","env":"${ENVIRONMENT:dev}"}</customFields>
  </encoder>
</appender>
```

### Correlation ID Propagation

Add a correlation ID to all log entries for cross-route tracing:

```properties
# Use MDC for correlation (works with both OpenTelemetry and manual correlation)
camel.main.use-mdc-logging=true
```

This makes `camel.exchangeId` and `camel.routeId` available in MDC for every log line.

---

## Migration Mapping: Anypoint Alerts → Prometheus Alertmanager

| Anypoint Alert | Prometheus Alert Rule |
|---------------|---------------------|
| Response time > threshold | `camel_exchanges_duration_seconds{quantile="0.95"} > 2.0` |
| Error rate > threshold | `rate(camel_exchanges_failed_total[5m]) / rate(camel_exchanges_total[5m]) > 0.05` |
| Queue depth > threshold | `camel_exchanges_inflight > 100` |
| Route stopped | `camel_routes_running < expected_count` |
| CPU/memory threshold | Standard `container_cpu_usage_seconds_total` / `container_memory_usage_bytes` |

---

## TDD Section: Observability

When the BRD includes monitoring/observability requirements, add this section to each TDD file:

```markdown
## Section 5g: Observability

### Metrics
| Metric | Alert Threshold | Anypoint Original |
|--------|----------------|-------------------|
| [metric name] | [threshold] | [original Anypoint alert name] |

### Tracing
- OpenTelemetry enabled: Yes/No
- Trace exporter: [Jaeger/Zipkin/OTLP endpoint]

### Health Checks
- Liveness: [endpoint]
- Readiness: [endpoint]
- Custom checks: [list any route-specific health checks]

### Logging
- Format: JSON
- Correlation ID: camel.exchangeId
- Log level: [INFO/DEBUG]
```

# Constitution Template

## When to Load This Guide

Load when creating or updating the `.camel-kit/constitution.md` file with detailed best practices and quality gates.

---

# Integration Constitution

This document defines the best practices, quality gates, and constraints for this integration project.

## Project-Specific Constraints

[List user-provided constraints from interview - this section is customized per project]

---

## Apache Camel Best Practices

### 1. Route Structure

- Each route must have a unique ID following the pattern: `domain-action`
- Routes should follow Single Responsibility Principle
- Keep routes focused and composable

---

### 2. Configuration Management

- Externalize all configuration to `application.properties`
- Never hardcode connection details in routes
- Use property placeholders: `{{property.name}}`
- Component-level config: `camel.component.<name>.<property>=value`

---

### 3. Error Handling

- Every route MUST declare an error handling strategy
- Use Dead Letter Channel for poison messages
- Log errors with sufficient context for debugging
- Never silently swallow exceptions

---

### 4. Security

#### 4.1 Credential Management
- **NEVER** hardcode credentials, API keys, tokens, or secrets in code or properties
- Use secrets management: HashiCorp Vault, AWS Secrets Manager, Kubernetes Secrets
- Reference secrets via placeholders: `${vault:secret/path#key}`
- Implement credential rotation policies
- Follow principle of least privilege

#### 4.2 Transport Security
- Always use TLS/SSL for network communication
- Kafka: Enable SSL and SASL authentication
- HTTP: Always use HTTPS, never plain HTTP
- Database: Use SSL/TLS connections
- Verify certificates, don't disable validation

#### 4.3 Data Protection
- Classify data: Public, Internal, Confidential, Restricted
- Mask PII and sensitive data in logs
- Encrypt sensitive data at rest and in transit
- Implement field-level encryption for highly sensitive fields
- Follow data retention and deletion policies

#### 4.4 Input Validation
- Validate all input against schemas
- Sanitize input to prevent injection attacks (SQL, XPath, Script)
- Set message size limits to prevent DoS
- Implement rate limiting for public APIs
- Reject malformed or unexpected input

#### 4.5 Compliance
- Document compliance requirements (PCI-DSS, HIPAA, GDPR, SOC 2)
- Implement audit logging for sensitive operations
- Ensure data residency requirements are met
- Support data subject rights (GDPR: right to deletion, portability)

---

### 5. Performance & Reliability

#### 5.1 Performance
- Define throughput and latency targets for each flow
- Use appropriate processing patterns (sync/async)
- Implement connection pooling for databases and HTTP clients
- Use batching where appropriate
- Avoid N+1 query problems in enrichment

#### 5.2 Reliability
- Define delivery guarantees (exactly-once, at-least-once, at-most-once)
- Implement idempotent consumers for exactly-once processing
- Use transactions where data consistency is critical
- Implement circuit breakers for external dependencies
- Handle backpressure with buffering or throttling

#### 5.3 Scalability
- Design for horizontal scaling (stateless routes)
- Use partitioning for parallel processing
- Avoid shared mutable state
- Monitor resource utilization (CPU, memory, connections)

---

### 6. Testing

- Every route must have integration tests
- Test error scenarios, not just happy path
- Use Testcontainers for external dependencies
- Validate against real component behavior

---

### 7. Observability

#### 7.1 Logging
- Use structured logging (JSON format)
- Include correlation ID in every log entry
- Use appropriate log levels (DEBUG, INFO, WARN, ERROR)
- Log key business events and technical events
- **NEVER** log sensitive data (passwords, tokens, PII without masking)
- Include context: flow name, step, message ID, timestamps

#### 7.2 Metrics
- Expose Prometheus-compatible metrics
- Track: throughput, latency, error rate, DLQ depth
- Monitor resource usage: connections, threads, memory
- Set up custom business metrics as needed
- Create dashboards for operational visibility

#### 7.3 Tracing
- Implement distributed tracing (OpenTelemetry, Jaeger, Zipkin)
- Generate correlation IDs at entry points
- Propagate correlation IDs to all downstream systems
- Trace spans for: route processing, external calls, transformations
- Enable trace sampling for high-volume flows

#### 7.4 Health Checks
- Implement liveness probes (is route running?)
- Implement readiness probes (can accept traffic?)
- Check connectivity to dependencies
- Expose health endpoints for orchestrators (Kubernetes)

#### 7.5 Alerting
- Define alert conditions and thresholds
- Alert on: high error rate, DLQ depth, processing latency, route failure
- Implement on-call rotation and escalation
- Document runbooks for common alerts

---

### 8. Anti-Patterns to Avoid

#### 8.1 Route Design Anti-Patterns
- **God Routes:** Routes that do too many things (violates SRP)
- **Hardcoded Endpoints:** Connections hardcoded in routes instead of properties
- **Missing Error Handling:** Routes without error strategies
- **Synchronous when Async would work:** Blocking calls reducing throughput
- **No Idempotency:** Processing same message multiple times

#### 8.2 Integration Anti-Patterns
- **Chatty Interfaces:** Too many small messages instead of batching
- **Missing Correlation IDs:** Can't trace requests across systems
- **No Circuit Breakers:** Cascade failures when dependencies fail
- **Tight Coupling:** Changes in one system break others
- **Database as Integration Point:** Multiple systems writing to same tables

#### 8.3 Security Anti-Patterns
- **Hardcoded Credentials:** Secrets in code or properties files
- **Plain Text Communication:** HTTP instead of HTTPS
- **Logging Sensitive Data:** PII, passwords in logs
- **No Input Validation:** Accepting malformed/malicious input
- **Overprivileged Access:** Using admin credentials for routine operations

#### 8.4 Performance Anti-Patterns
- **No Connection Pooling:** Creating new connection per message
- **Unbounded Queues:** Memory leaks from unlimited buffering
- **Synchronous Processing of High Volume:** Should use async
- **No Caching:** Fetching same reference data repeatedly
- **N+1 Queries:** Multiple database calls in loops

---

### 9. Documentation

- Each flow must have a Technical Design Document
- Keep TDDs up-to-date with implementation
- Document business rules and decisions
- Include sequence diagrams for complex flows

---

These gates will be checked during `/camel-validate`.

---

## Constitution Customization

When creating a constitution for a specific project:

1. **Copy this template** to `.camel-kit/constitution.md`
2. **Add project-specific constraints** at the top
3. **Modify sections** based on project requirements
4. **Remove inapplicable sections** (e.g., if no compliance needs, remove that section)
5. **Keep all security and error handling** best practices (mandatory)

---

## Minimal Constitution (for simple projects)

For simple projects with no special requirements, you can use a minimal constitution:

```markdown
# Integration Constitution

This project follows standard Apache Camel best practices:

- Externalize all configuration
- Implement error handling on all routes
- No hardcoded credentials
- Use structured logging with correlation IDs
- Test all routes

See full best practices in constitution template.
```

---

## Comprehensive Constitution (for enterprise projects)

For enterprise projects, include all sections with specific values:

- Define performance SLAs
- List compliance frameworks
- Specify monitoring tools
- Document security requirements
- Define testing coverage requirements
- Specify deployment environments

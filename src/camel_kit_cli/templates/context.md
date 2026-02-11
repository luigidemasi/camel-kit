# Integration Context

> This document defines the integration landscape for **{{PROJECT_NAME}}**. It captures the business purpose, connected systems, and high-level route overview before detailed design.

---

## Metadata

| Property | Value |
|----------|-------|
| Project | {{PROJECT_NAME}} |
| Created | {{DATE}} |
| Status | Draft / In Progress / Complete |
| Camel Version | {{CAMEL_VERSION}} |
| Runtime | {{RUNTIME}} |

---

## Business Purpose

<!--
Describe the business problem this integration solves.
Focus on the "why", not the "how".
1-3 sentences maximum.
-->

{{BUSINESS_PURPOSE}}

---

## Systems

<!--
List all external systems this integration connects.
For each system, define its role and connection details.
-->

### {{SYSTEM_1_NAME}}

| Property | Value |
|----------|-------|
| Role | Source / Sink / Both |
| Protocol | REST / Kafka / JDBC / File / AMQP / ... |
| Environment | {{ENV_ENDPOINT}} |
| Authentication | None / API Key / OAuth2 / Basic / mTLS |
| Data Format | JSON / XML / CSV / Avro / Binary |
| Schema | Available / Not Available / TBD |
| Owner | {{TEAM_OR_CONTACT}} |
| Documentation | {{LINK_OR_REFERENCE}} |

**Notes:**
<!-- Any additional context about this system -->

---

### {{SYSTEM_2_NAME}}

| Property | Value |
|----------|-------|
| Role | Source / Sink / Both |
| Protocol | ... |
| Environment | ... |
| Authentication | ... |
| Data Format | ... |
| Schema | ... |
| Owner | ... |
| Documentation | ... |

**Notes:**

---

<!-- Add more systems as needed -->

---

## Data Contracts

<!--
Define the data structures that flow through this integration.
Reference schemas or describe structure inline.
-->

### {{DATA_TYPE_1}}

**Description:** {{DESCRIPTION}}

**Schema Location:** `schemas/{{filename}}.json` or inline below

**Structure:**
```json
{
  "field1": "string",
  "field2": 0,
  "nestedObject": {
    "subfield": "string"
  },
  "arrayField": []
}
```

**Validation Rules:**
- `field1`: Required, non-empty
- `field2`: Required, positive integer
- ...

---

### {{DATA_TYPE_2}}

**Description:** {{DESCRIPTION}}

**Structure:**
```json
{
}
```

---

## Routes Overview

<!--
High-level map of all routes in this integration.
Each route will be detailed in its own specification file.
-->

| Route ID | Purpose | Source | Sink | Status |
|----------|---------|--------|------|--------|
| {{route-id-1}} | {{one-line purpose}} | {{system/component}} | {{system/component}} | Draft / Designed / Validated |
| {{route-id-2}} | ... | ... | ... | ... |

### Route Dependency Graph

<!--
Show how routes connect to each other via direct:/seda:
Use ASCII or describe the flow.
-->

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  order-ingest   │────▶│ order-validate  │────▶│  order-persist  │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                               │
                               ▼
                        ┌─────────────────┐
                        │ customer-enrich │
                        └─────────────────┘
```

---

## Non-Functional Requirements

### Volume & Throughput

| Metric | Requirement |
|--------|-------------|
| Expected Volume | {{messages/day}} |
| Peak Rate | {{messages/second}} |
| Batch Size | {{if applicable}} |

### Latency

| Metric | Requirement |
|--------|-------------|
| End-to-End | < {{X}} ms / seconds |
| Per Route | < {{Y}} ms |

### Availability

| Metric | Requirement |
|--------|-------------|
| Uptime | 99.9% / 99.99% / Best Effort |
| Maintenance Window | {{schedule if any}} |
| Failover | Active-Passive / Active-Active / None |

### Data Retention

| Metric | Requirement |
|--------|-------------|
| Message Retention | {{duration}} |
| Error Retention | {{duration}} |
| Audit Logs | {{duration}} |

---

## Security Considerations

### Authentication & Authorization

| Aspect | Approach |
|--------|----------|
| Service Identity | {{how the integration authenticates to external systems}} |
| Secrets Management | {{Vault / K8s Secrets / Env Vars}} |
| API Keys | {{rotation policy}} |

### Data Protection

| Aspect | Approach |
|--------|----------|
| Encryption in Transit | TLS 1.2+ / mTLS |
| Encryption at Rest | {{if applicable}} |
| PII Handling | {{masking, filtering, or n/a}} |

### Audit & Compliance

| Aspect | Approach |
|--------|----------|
| Audit Logging | {{what is logged}} |
| Compliance | {{GDPR / HIPAA / PCI / None}} |

---

## Environment Configuration

### Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `KAFKA_BROKERS` | Kafka bootstrap servers | `localhost:9092` |
| `DB_URL` | Database connection string | `jdbc:postgresql://...` |
| `API_ENDPOINT` | External API base URL | `https://api.example.com` |

### Profiles

| Profile | Purpose | Key Differences |
|---------|---------|-----------------|
| `dev` | Local development | Mock services, local brokers |
| `test` | Integration testing | Test environment endpoints |
| `prod` | Production | Real endpoints, full security |

---

## Open Questions

<!--
Track unresolved decisions or pending clarifications.
Remove items as they are resolved.
-->

- [ ] {{Question 1}}
- [ ] {{Question 2}}

---

## Change Log

| Date | Author | Change |
|------|--------|--------|
| {{DATE}} | {{AUTHOR}} | Initial context created |

---
title: Migration Workflow
weight: 3
---

Use `/camel-migrate` when you have an existing integration built on another platform and want to move it to Apache Camel. The command analyses your existing artifacts, asks targeted questions, and produces the same BRD + TDD files that the greenfield workflow produces — making `/camel-implement` the shared step for both paths.

## Supported Platforms

| Platform | Versions | Notes |
|----------|---------|-------|
| MuleSoft Mule | 3.x, 4.x | XML flows, DataWeave transformations, all standard connectors, deployment migration (CloudHub to Kubernetes) |

## Running a Migration

```
/camel-migrate
```

The command will ask you to provide the path to your source project artifacts.

## What the Command Does

### Phase 1 — Business Analyst

The command reads all Mule XML files and builds a complete inventory of flows. Before asking any questions, it identifies which components have direct Camel equivalents and which are proprietary connectors that need a decision:

```
I found the following connector(s) with no direct Apache Camel equivalent:

- Anypoint MQ (used in: order-ingestion-flow)
  Suggested alternatives:
  a) Amazon SQS (camel-aws2-sqs)
  b) RabbitMQ (camel-rabbitmq)
  c) ActiveMQ (camel-activemq)
  d) Keep as TODO placeholder
```

After resolving proprietary connectors, it asks only the business questions the XML cannot answer — purpose, SLA, compliance requirements, and failure behaviour.

**Produces:**
- `.camel-kit/business-requirements.md`
- `.camel-kit/constitution.md`

### Phase 2 — Integration Architect

Maps each Mule component to its Camel equivalent, converts DataWeave transformations into TDD field mapping tables, and asks only what the XML cannot answer (DataWeave transformation intent, missing endpoint URLs, authentication, retry strategy).

**Produces one TDD file per Mule flow:**
- `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md`

## Mule to Camel Component Mapping

| Mule Component | Camel Equivalent |
|----------------|-----------------|
| HTTP Listener | `platform-http` (consumer) |
| HTTP Request | `camel-http` (producer) |
| JMS | `camel-jms` / `camel-sjms` |
| Database | `camel-sql` / `camel-jdbc` |
| Scheduler | `camel-timer` / `camel-quartz` |
| Choice Router | `choice` EIP |
| Scatter-Gather | `multicast` EIP |
| For Each | `split` EIP |
| Sub Flow | `direct:` route |
| DataWeave Transform | XSLT (Kaoto DataMapper) |
| Set Payload | `setBody` EIP |
| Set Variable | `setHeader` EIP |

For a complete mapping table, see `skills/camel-migrate-mule/guides/mule-component-mapping.md` in your project's skills folder after running `camel-kit init`.

## After /camel-migrate

The produced files are fully compatible with the rest of the workflow:

```
/camel-implement order-ingestion     # Generate Camel YAML
/camel-validate order-ingestion      # Verify compliance
/camel-test order-ingestion          # Generate Citrus tests
```

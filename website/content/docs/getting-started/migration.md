---
title: Migration Workflow
weight: 3
---

Use `/camel-migrate` when you have an existing integration built on another platform and want to move it to Apache Camel. The command analyses your existing artifacts, asks targeted questions, and produces the same BRD + TDD files that the greenfield workflow produces — making `/camel-implement` the shared step for both paths.

## Supported Platforms

| Platform | Versions | Detection method |
|----------|---------|--------------------|
| MuleSoft Mule | 3.x, 4.x | XML namespace `mulesoft.org`, `pom.xml` groupId `org.mule` / `com.mulesoft` |
| Spring Integration | 4.x, 5.x, 6.x | XML namespace `springframework.org/schema/integration`, Maven `spring-integration-core` / `spring-boot-starter-integration`, Java DSL `IntegrationFlow` / `@ServiceActivator` |

## Running a Migration

```
/camel-migrate
```

The command will ask you to provide the path to your source project artifacts.

## What the Command Does

The migration follows a generic two-phase orchestration:

1. Scans **all** project artifacts (XML, build files, properties, docs, Docker/K8s, source, tests).
2. Detects vendor and version from the full scan content.
3. Builds a pre-populated analysis summary (purpose, SLA, security, failure behaviour, deployment target) extracted from the artifacts — without asking the user.
4. Confirms the summary; only asks about genuine gaps (typically just API compatibility).
5. Delegates to the vendor-specific sub-skill.

---

### MuleSoft Mule Migration

#### Phase 1 — Business Analyst

Reads all Mule XML files and builds a complete inventory of flows. Before asking any questions, it identifies which components have direct Camel equivalents and which are proprietary connectors that need a decision:

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

**Produces:** `.camel-kit/business-requirements.md` and `.camel-kit/constitution.md`

#### Phase 2 — Integration Architect

Maps each Mule component to its Camel equivalent, converts DataWeave transformations into TDD field mapping tables, and asks only what the XML cannot answer (DataWeave transformation intent, missing endpoint URLs, authentication, retry strategy).

**Produces one TDD file per Mule flow:** `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md`

#### Mule to Camel Component Mapping

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

---

### Spring Integration Migration

#### Phase 1 — Business Analyst

Parses Spring Integration XML config (`<int:*>` namespaces) and Java DSL (`IntegrationFlow`, annotations). Inventories channels, adapters, gateways, transformers, filters, routers, splitters, aggregators, and service-activators. Flags custom service-activators and beans for user decision (keep as `bean:`, re-implement, or TODO).

**Produces:** `.camel-kit/business-requirements.md` and `.camel-kit/constitution.md`

#### Phase 2 — Integration Architect

Maps each SI component to its catalog-verified Camel equivalent (calls `camel_catalog_component_doc` for every component). Converts SpEL expressions into TDD field mapping tables. Maps error channels to Camel error handlers, pollers to timer/scheduler.

**Produces one TDD file per flow:** `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md`

#### Spring Integration to Camel Component Mapping

| SI Component | Camel Equivalent |
|-------------|-----------------|
| `<int:channel>` | `direct:` / `seda:` |
| `<int:gateway>` | `direct:` (request-reply) |
| `<int-http:inbound-gateway>` | `platform-http` |
| `<int-http:outbound-gateway>` | `camel-http` |
| `<int-jms:inbound-adapter>` | `camel-jms` (consumer) |
| `<int-jms:outbound-adapter>` | `camel-jms` (producer) |
| `<int-file:inbound-adapter>` | `camel-file` (consumer) |
| `<int-file:outbound-adapter>` | `camel-file` (producer) |
| `<int:transformer>` | `setBody` / XSLT DataMapper |
| `<int:filter>` | `filter` EIP |
| `<int:router>` | `choice` / `recipientList` EIP |
| `<int:splitter>` | `split` EIP |
| `<int:aggregator>` | `aggregate` EIP |
| `<int:service-activator>` | `bean:` |

For a complete mapping table (60+ components), see `skills/camel-migrate-spring/guides/spring-component-mapping.md` in your project's skills folder after running `camel-kit init`.

## After /camel-migrate

The produced files are fully compatible with the rest of the workflow:

```
/camel-implement order-ingestion     # Generate Camel YAML
/camel-validate order-ingestion      # Verify compliance
/camel-test order-ingestion          # Generate Citrus tests
```

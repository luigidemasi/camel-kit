# Mule → Apache Camel Component Mapping Guide

This guide maps MuleSoft Mule components to their Apache Camel equivalents. It is used by the `camel-migrate-mule` sub-skill during migration analysis.

---

## Standard Component Mapping

| Mule Component | Mule Version | Camel Equivalent | Camel Artifact | Notes |
|----------------|-------------|------------------|----------------|-------|
| HTTP Listener | 3.x / 4.x | `platform-http` (consumer) | `camel-platform-http` | Preferred for Quarkus/Spring Boot. Alternatively `camel-servlet` or `camel-jetty`. See **HTTP Listener Port Conversion** below. |
| HTTP Request | 3.x / 4.x | `http` (producer) | `camel-http` | Use `camel-http` for outbound HTTP calls. |
| HTTPS Listener | 3.x / 4.x | `platform-http` with SSL | `camel-platform-http` | Configure SSL via `application.properties`. |
| HTTPS Request | 3.x / 4.x | `https` (producer) | `camel-http` | Use `https://` URI scheme. |
| File (inbound endpoint) | 3.x / 4.x | `file` (consumer) | `camel-file` | Supports `move`, `delete`, `noop` post-processing. |
| File (outbound endpoint) | 3.x / 4.x | `file` (producer) | `camel-file` | Supports `fileExist`, `tempFileName` options. |
| FTP (inbound) | 3.x / 4.x | `ftp` (consumer) | `camel-ftp` | Use `ftp://user@host/path` URI. |
| FTP (outbound) | 3.x / 4.x | `ftp` (producer) | `camel-ftp` | |
| SFTP (inbound) | 3.x / 4.x | `sftp` (consumer) | `camel-ftp` | Use `sftp://user@host/path` URI. |
| SFTP (outbound) | 3.x / 4.x | `sftp` (producer) | `camel-ftp` | |
| JMS (inbound) | 3.x / 4.x | `jms` (consumer) | `camel-jms` | Supports queues and topics. Use `camel-sjms` for simpler use cases. |
| JMS (outbound) | 3.x / 4.x | `jms` (producer) | `camel-jms` | |
| ActiveMQ | 3.x / 4.x | `activemq` | `camel-activemq` | Drop-in replacement if using ActiveMQ broker. |
| AMQP | 3.x / 4.x | `amqp` | `camel-amqp` | For RabbitMQ and AMQP 1.0 brokers. |
| Database (select) | 3.x / 4.x | `sql` (consumer) | `camel-sql` | Use `camel-jdbc` for complex queries. |
| Database (insert/update/delete) | 3.x / 4.x | `sql` (producer) | `camel-sql` | |
| Database Bulk | 3.x / 4.x | `jdbc` | `camel-jdbc` | Better for batch operations. |
| VM Queue (inbound) | 3.x / 4.x | `seda` (consumer) | `camel-seda` | In-memory queue. Not persistent across restarts. |
| VM Queue (outbound) | 3.x / 4.x | `seda` (producer) | `camel-seda` | |
| Scheduler | 3.x / 4.x | `timer` or `quartz` | `camel-timer` / `camel-quartz` | Use `timer` for simple intervals; `quartz` for cron expressions. |
| Cron Scheduler | 3.x / 4.x | `quartz` | `camel-quartz` | Use `quartz://name?cron=...` URI. |
| Choice Router | 3.x / 4.x | `choice` EIP | built-in | Content-Based Router (CBR) pattern. |
| `<when>` | 3.x / 4.x | `when` clause in `choice` | built-in | Use Simple, SpEL, or JsonPath predicates. |
| `<otherwise>` | 3.x / 4.x | `otherwise` clause in `choice` | built-in | |
| Scatter-Gather | 3.x / 4.x | `multicast` EIP | built-in | Sends to all routes in parallel; use `aggregationStrategy`. |
| For Each (`foreach`) | 4.x | `split` EIP | built-in | `split(body())` for iterating collections. |
| Until Successful | 3.x / 4.x | `doTry` + retry | built-in | Use `maximumRedeliveries` on error handler or `redeliveryPolicy`. |
| Async | 3.x / 4.x | `threads` EIP | built-in | `threads().poolSize(N)` for async processing. |
| Sub Flow | 3.x / 4.x | `direct:` route | `camel-direct` | `direct:sub-flow-name` as both producer and consumer URI. |
| Flow Reference | 3.x / 4.x | `.to("direct:route-name")` | `camel-direct` | References another route by direct URI. |
| DataWeave Transform | 3.x / 4.x | XSLT or Kaoto DataMapper | `camel-xslt-saxon` | See `mule-dataweave-conversion.md` for mapping strategy. |
| Logger | 3.x / 4.x | `log` EIP | built-in | `.log(LoggingLevel.INFO, "message")` |
| Set Payload | 3.x / 4.x | `setBody` EIP | built-in | `.setBody(constant("value"))` or `.setBody(simple("${header.X}"))` |
| Set Variable | 3.x / 4.x | `setHeader` EIP | built-in | Mule variables map to Camel exchange headers. |
| Set Property | 3.x / 4.x | `setProperty` EIP | built-in | Maps to Camel exchange properties. |
| Remove Variable | 3.x / 4.x | `removeHeader` EIP | built-in | |
| Enrich (Message Enricher) | 3.x / 4.x | `enrich` EIP | built-in | Content Enricher pattern. |
| Poll Enrich | 3.x / 4.x | `pollEnrich` EIP | built-in | For polling-based enrichment. |
| Splitter | 3.x | `split` EIP | built-in | Same as `foreach` in Mule 4.x. |
| Aggregator | 3.x / 4.x | `aggregate` EIP | built-in | Use `completionSize` or `completionTimeout`. |
| Filter (Message Filter) | 3.x / 4.x | `filter` EIP | built-in | `.filter(predicate)` |
| Idempotent Filter | 3.x / 4.x | `idempotentConsumer` EIP | built-in | Requires `IdempotentRepository` (in-memory or persistent). |
| Wire Tap | 3.x / 4.x | `wireTap` EIP | built-in | |
| Object to JSON | 3.x / 4.x | `marshal().json()` | `camel-jackson` | |
| JSON to Object | 3.x / 4.x | `unmarshal().json()` | `camel-jackson` | |
| Object to XML | 3.x / 4.x | `marshal().jaxb()` | `camel-jaxb` | |
| XML to Object | 3.x / 4.x | `unmarshal().jaxb()` | `camel-jaxb` | |
| XSLT Transform | 3.x / 4.x | `to("xslt-saxon:path/to/file.xsl")` | `camel-xslt-saxon` | |
| HTTP Proxy | 3.x | `netty-http` | `camel-netty-http` | For reverse-proxy scenarios. |
| Error Handler | 3.x / 4.x | `doTry/doCatch` + DLQ | built-in | See error handling section below. |
| Dead Letter Queue | 3.x / 4.x | Dead Letter Channel | built-in | Configure on `errorHandler()`. |
| Retry | 3.x / 4.x | `redeliveryPolicy` | built-in | Part of error handler configuration. |
| Salesforce Connector | 4.x | `salesforce` | `camel-salesforce` | Full Salesforce API support available. |
| SOAP Web Service | 3.x / 4.x | `cxf` | `camel-cxf` | Full SOAP/WSDL support. |
| REST Consumer (RAML) | 4.x | `rest` + `openapi` | `camel-rest`, `camel-openapi-java` | Import OpenAPI/Swagger spec. |
| Kafka Consumer | 4.x | `kafka` (consumer) | `camel-kafka` | |
| Kafka Producer | 4.x | `kafka` (producer) | `camel-kafka` | |
| Email Send (SMTP) | 4.x | `smtp` (producer) | `camel-mail` | See **Email Component Mapping** below. |
| Email Send (SMTPS) | 4.x | `smtps` (producer) | `camel-mail` | See **Email Component Mapping** below. |
| Email Listener IMAP | 4.x | `imap` (consumer) | `camel-mail` | See **Email Component Mapping** below. |
| Email Listener IMAPS | 4.x | `imaps` (consumer) | `camel-mail` | See **Email Component Mapping** below. |
| Email Listener POP3 | 4.x | `pop3` (consumer) | `camel-mail` | See **Email Component Mapping** below. |
| Email Listener POP3S | 4.x | `pop3s` (consumer) | `camel-mail` | See **Email Component Mapping** below. |
| Email (Mule 3.x) | 3.x | `smtp` / `imap` / `pop3` (+ `s` variants) | `camel-mail` | Mule 3.x uses transport-based `<smtp:outbound-endpoint>`, `<imap:inbound-endpoint>`, etc. Map directly by protocol name. |

Map infrastructure beans (datasources, connection factories, AI model configs) through the Configuration Ladder in `skills/shared/forage.md` — prefer `forage.*` properties over `camel.beans.*` in the migration target.

---

### HTTP Listener Port Conversion

Mule's `<http:listener-config>` defines `host` and `port` attributes. Camel's `platform-http` component does NOT have `host` or `port` component options — these properties do not exist in the catalog.

Convert Mule HTTP Listener port configuration to Camel as follows:

| Runtime | Camel `application.properties` |
|---|---|
| `main` | `camel.server.enabled=true` and `camel.server.port=8081` |
| `spring-boot` | `server.port=8081` |
| `quarkus` | `quarkus.http.port=8081` |

For `main`, `camel.server.enabled` and `camel.server.port` MUST be set together — they are a pair.

**Never generate** `camel.component.platform-http.host=...` or `camel.component.platform-http.port=...` — these options do not exist.

---

### Email Component Mapping

Mule 4.x uses a single Email Connector with the SSL/TLS variant determined by the **connection type** in the config, not the operation. Camel uses **distinct URI schemes** for each protocol variant — all from the same `camel-mail` artifact.

**Determine the Camel scheme from the Mule connection type:**

| Mule Operation | Mule Connection Type | Camel Scheme | Direction |
|---|---|---|---|
| `<email:send>` | `<email:smtp-connection>` | `smtp` | producer |
| `<email:send>` | `<email:smtps-connection>` | `smtps` | producer |
| `<email:listener-imap>` | `<email:imap-connection>` | `imap` | consumer |
| `<email:listener-imap>` | `<email:imaps-connection>` | `imaps` | consumer |
| `<email:listener-pop3>` | `<email:pop3-connection>` | `pop3` | consumer |
| `<email:listener-pop3>` | `<email:pop3s-connection>` | `pop3s` | consumer |

**CRITICAL — never use `mail` as the component scheme.** `mail` is the Maven artifact name (`camel-mail`), NOT a valid URI scheme. Always use the specific protocol: `smtp`, `smtps`, `imap`, `imaps`, `pop3`, or `pop3s`. Properties must use `camel.component.smtp.*`, `camel.component.imaps.*`, etc.

**STARTTLS vs SMTPS:** If the Mule config uses `<email:smtp-connection>` (plain) with a `<tls:context>` and `mail.smtp.starttls.enable=true`, map to `smtp` (not `smtps`) and set `starttls=true` as an endpoint option. Use `smtps` only when the Mule config uses `<email:smtps-connection>` (implicit SSL on port 465).

---

## Proprietary Connectors — Require User Decision

The following Mule connectors do **NOT** have a direct Apache Camel equivalent. When these are found in a Mule project, **stop and ask the user** how to handle each one.

| Mule Component | Mule Version | Situation | Suggested Alternatives | Notes |
|----------------|-------------|-----------|----------------------|-------|
| **Anypoint MQ** | 4.x | No direct equivalent | Amazon SQS (`camel-aws2-sqs`), Azure Service Bus (`camel-azure-servicebus`), RabbitMQ (`camel-spring-rabbitmq`), ActiveMQ (`camel-activemq`) | Ask user which message broker they will use, then verify the selected component in MCP. |
| **Object Store** | 4.x | No direct equivalent | Infinispan (`camel-infinispan`), Redis (`camel-spring-redis`), Caffeine cache (`camel-caffeine`), JPA (`camel-jpa`) | Ask user what persistence tier is available, then verify the selected component in MCP. |
| **SAP Connector** | 3.x / 4.x | Licensed component | `camel-sap` (if SAP JCo licensed), REST/SOAP adapter to SAP APIs | Ask user if SAP JCo license is available, or if SAP exposes REST/SOAP APIs. |
| **Workday Connector** | 4.x | No equivalent | `camel-http` with Workday REST APIs, `camel-cxf` for SOAP | Ask user if Workday REST API is available and get API credentials. |
| **NetSuite Connector** | 4.x | No direct equivalent | `camel-http` with NetSuite REST/SOAP APIs | |
| **ServiceNow Connector** | 4.x | Partial equivalent | `camel-servicenow` | Check if `camel-servicenow` meets the required API coverage. |
| **Zuora Connector** | 4.x | No equivalent | `camel-http` with Zuora REST API | |
| **Marketo Connector** | 4.x | No equivalent | `camel-http` with Marketo REST API | |
| **Veeva Connector** | 4.x | No equivalent | `camel-http` with Veeva REST API | |
| **Microsoft Dynamics 365** | 4.x | Partial equivalent | `camel-http` with Dynamics 365 REST API, `camel-olingo4` | |
| **Siebel Connector** | 3.x / 4.x | No equivalent | `camel-http` with Siebel REST API | |
| **Anaplan Connector** | 4.x | No equivalent | `camel-http` with Anaplan REST API | |

---

## Mule Error Handler → Camel Error Handling Mapping

| Mule Construct | Camel Equivalent | Description |
|----------------|-----------------|-------------|
| `<on-error-continue>` | `doCatch` (swallow exception) | Catches exception, continues route execution |
| `<on-error-propagate>` | `doCatch` (rethrow or DLQ) | Catches exception, sends to DLQ or rethrows |
| `<error-handler>` (global) | `errorHandler()` on `RouteBuilder` | Global error handling for all routes |
| `<error-handler>` (local) | `doTry/doCatch/doFinally` | Scoped error handling within a route |
| Dead Letter Queue | Dead Letter Channel (`deadLetterUri`) | Configure via `errorHandler(deadLetterChannel("jms:dlq"))` |
| Retry on error | `redeliveryPolicy()` | Configure `maximumRedeliveries`, `redeliveryDelay`, `backOffMultiplier` |

---

## Mule Variable Scoping → Camel Header/Property Mapping

| Mule Scope | Mule API | Camel Equivalent | Notes |
|-----------|----------|-----------------|-------|
| Flow Variable | `vars.myVar` | Exchange Header (`header.myVar`) | Local to the current exchange |
| Flow Variable | `vars.myVar` | Exchange Property (`exchangeProperty.myVar`) | Properties survive routing slips |
| Payload | `payload` | `${body}` | Message body |
| Attributes | `attributes.headers.*` | `${header.*}` | Inbound HTTP headers |
| Inbound Properties | `inboundProperties.X` (3.x) | `${header.X}` | Mapped to Camel message headers |
| Outbound Properties | `outboundProperties.X` (3.x) | `${header.X}` | Set on outbound message headers |
| Session Variables | `sessionVars.X` (3.x) | Exchange property or `camel-undertow` session | Mule sessions don't map cleanly; review each case |

---

## Mule 3.x vs Mule 4.x Key Differences

| Feature | Mule 3.x | Mule 4.x | Migration Notes |
|---------|----------|----------|----------------|
| DataWeave version | DataWeave 1.0 | DataWeave 2.0 | Syntax significantly different; see dataweave guide |
| Variable scoping | `flowVars`, `sessionVars`, `inboundProperties`, `outboundProperties` | Single `vars` scope | Consolidate to headers/properties in Camel |
| Error handling | `catch-exception-strategy`, `rollback-exception-strategy` | `<on-error-continue>`, `<on-error-propagate>` | Both map to `doCatch` with different behaviours |
| Connectors | Mule 3 transport model | Mule 4 connector model | Different XML namespaces and element names |
| MEL expressions | `#[flowVars.x]`, `#[message.payload]` | `#[vars.x]`, `#[payload]` | Camel uses Simple language: `${header.x}`, `${body}` |
| Flow structure | `<flow>` and `<sub-flow>` | Same | Both map to Camel routes |
| Choice router | `<choice>` with `<when>` and `<otherwise>` | Same | Maps to Camel `choice/when/otherwise` EIP |

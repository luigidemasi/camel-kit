# BizTalk → Apache Camel Component Mapping Guide

This guide maps Microsoft BizTalk adapters and components to their Apache Camel equivalents. It is used by the `camel-migrate` skill during BizTalk migration analysis.

> **"Load" means READ and FOLLOW.** When this guide says "Load `path/to/file.md`", read that file relative to the skill directory and execute its instructions. The files are always present — do NOT report them as missing.

---

## Standard Adapter Mapping

| BizTalk Adapter | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|
| FILE (receive) | `file` (consumer) | `camel-file` | Supports `move`, `delete`, `noop` post-processing. |
| FILE (send) | `file` (producer) | `camel-file` | Supports `fileExist`, `tempFileName` options. |
| FTP (receive) | `ftp` (consumer) | `camel-ftp` | Use `ftp://user@host/path` URI. |
| FTP (send) | `ftp` (producer) | `camel-ftp` | |
| FTPS (receive) | `ftps` (consumer) | `camel-ftp` | Use `ftps://` URI scheme with SSL. |
| FTPS (send) | `ftps` (producer) | `camel-ftp` | |
| SFTP (receive) | `sftp` (consumer) | `camel-ftp` | Use `sftp://user@host/path` URI. |
| SFTP (send) | `sftp` (producer) | `camel-ftp` | |
| SQL Server (receive) | `sql` (consumer) | `camel-sql` | For polling queries. |
| SQL Server (send) | `sql` (producer) | `camel-sql` | For insert/update/delete. |
| SQL Server (complex queries) | `jdbc` | `camel-jdbc` | Better for batch operations. |
| WCF-BasicHttp (receive) | `platform-http` + `cxf` (consumer) | `camel-platform-http`, `camel-cxf` | WSDL-first SOAP services. |
| WCF-BasicHttp (send) | `cxf` (producer) | `camel-cxf` | WSDL-first SOAP client. |
| WCF-WSHttp (receive) | `platform-http` + `cxf` (consumer) | `camel-platform-http`, `camel-cxf` | WS-Security support via CXF interceptors. |
| WCF-WSHttp (send) | `cxf` (producer) | `camel-cxf` | |
| WCF-NetTcp | `netty` | `camel-netty` | Binary TCP protocol. Requires custom codec. |
| SOAP (receive) | `platform-http` + `cxf` (consumer) | `camel-platform-http`, `camel-cxf` | WSDL-first SOAP services. |
| SOAP (send) | `cxf` (producer) | `camel-cxf` | |
| HTTP (receive) | `platform-http` (consumer) | `camel-platform-http` | Preferred for Quarkus/Spring Boot. |
| HTTP (send) | `http` (producer) | `camel-http` | Use `http://` URI scheme. |
| HTTPS (receive) | `platform-http` with SSL | `camel-platform-http` | Configure SSL via `application.properties`. |
| HTTPS (send) | `https` (producer) | `camel-http` | Use `https://` URI scheme. |
| MSMQ (receive) | **ASK USER** | — | See **MSMQ Adapter Mapping** section below. |
| MSMQ (send) | **ASK USER** | — | See **MSMQ Adapter Mapping** section below. |
| MQ Series (receive) | `jms` (consumer) | `camel-jms` | IBM MQ client via JMS. |
| MQ Series (send) | `jms` (producer) | `camel-jms` | |
| SMTP | `smtp` (producer) | `camel-mail` | **CRITICAL**: use `smtp` scheme, NOT `mail`. |
| POP3 (receive) | `pop3` (consumer) | `camel-mail` | |
| IMAP (receive) | `imap` (consumer) | `camel-mail` | |
| Oracle (receive) | `sql` (consumer) | `camel-sql` | |
| Oracle (send) | `sql` (producer) | `camel-sql` | |
| IBM Db2 (receive) | `sql` (consumer) | `camel-sql` | |
| IBM Db2 (send) | `sql` (producer) | `camel-sql` | |
| Azure EventHub (2016+) | `azure-eventhubs` | `camel-azure-eventhubs` | BizTalk 2016 or later only. |
| Azure ServiceBus (2016+) | `azure-servicebus` | `camel-azure-servicebus` | BizTalk 2016 or later only. |
| Azure Blob (2016+) | `azure-storage-blob` | `camel-azure-storage-blob` | BizTalk 2016 or later only. |

---

## MSMQ Adapter Mapping

Microsoft Message Queuing (MSMQ) has no direct Apache Camel equivalent. When MSMQ is detected, **stop and ask the user** which replacement to use:

```text
I found the MSMQ adapter in your BizTalk application.

MSMQ has no direct Apache Camel equivalent. Please choose a replacement:

a) ActiveMQ Artemis (`camel-jms`) — drop-in JMS replacement, supports durable queues
b) RabbitMQ (`camel-rabbitmq`) — AMQP-based messaging
c) Azure Service Bus (`camel-azure-servicebus`) — cloud-native alternative (if migrating to Azure)
d) Keep as TODO placeholder — decide later

Your choice?
```

Record the decision and use the selected component in Phase 2.

---

## BizTalk Port Direction Mapping

| BizTalk Port Direction | Camel Pattern |
|---|---|
| Receive Port (one-way) | `from(...)` — consumer |
| Receive Port (request-response) | `from(...).to(...).transform(...)` — consumer + producer |
| Send Port (one-way) | `to(...)` — producer |
| Send Port (solicit-response) | `to(...).transform(...)` — producer + reply |

---

## BizTalk Orchestration Shape → Camel EIP Mapping

| BizTalk Shape | Camel Equivalent | Notes |
|---|---|---|
| **Receive Shape** | `from(...)` | Consumer endpoint. Activate=True → route entry point. |
| **Send Shape** | `to(...)` | Producer endpoint. |
| **Construct Message Shape** | `setBody` EIP | Message construction. |
| **Message Assignment Shape** | `setBody` / `setHeader` EIP | Variable assignment. |
| **Transform Shape** | XSLT or `unmarshal`/`marshal` | Map transformation. See `biztalk-map-conversion.md`. |
| **Decide Shape** | `choice` EIP | Content-Based Router (CBR). |
| **Switch Shape** | `choice` EIP | Multiple `when` branches. |
| **Loop Shape** | `loop` EIP | Fixed iteration count. |
| **ForEach Shape** | `split` EIP | Collection iteration. |
| **While Shape** | `loop` EIP with condition | Condition-based loop. |
| **Until Shape** | `loop` EIP with condition | Condition-based loop (inverted). |
| **Parallel Actions Shape** | `multicast` EIP | Parallel execution. Use `parallelProcessing(true)`. |
| **ParallelBranch Shape** | branch within `multicast` | Individual branch in parallel block. |
| **Listen Shape** | `choice` + `timeout` | Alternative receive paths with timeout. |
| **Call Orchestration Shape** | `.to("direct:sub-orchestration")` | Synchronous route invocation. |
| **Start Orchestration Shape** | `wireTap` or `.to("seda:...")` | Asynchronous (fire-and-forget) invocation. |
| **Invoke Shape** | `to(...)` | External service call. |
| **Scope Shape** | `doTry` EIP | Transaction/error handling scope. |
| **AtomicTransaction Shape** | `transacted()` | Short-lived transaction boundary. |
| **LongRunningTransaction Shape** | Saga EIP | Long-running transaction with compensation. |
| **CompensationScope Shape** | Saga EIP scope | Defines compensating action for a scope. |
| **Compensate Shape** | Saga compensation trigger | Triggers compensation of a completed scope. |
| **Catch Shape** | `doCatch(...)` | Exception handler within a scope. |
| **ThrowException / Throw Shape** | `throwException(...)` | Raises an exception. |
| **Terminate Shape** | `stop()` | Terminates the route. |
| **Suspend Shape** | **Not supported** | **ASK USER**: No Camel equivalent — BizTalk dehydration is platform-specific. Suggest Dead Letter Channel or manual hold pattern. |
| **Delay Shape** | `delay` EIP | `.delay(constant(5000))` for 5s delay. |
| **Expression Shape** | `process()` or Groovy | C#/VB code. See `biztalk-expression-mapping.md`. |
| **VariableDeclaration Shape** | `setVariable()` | Variable initialization. |
| **VariableAssignment Shape** | `setVariable()` / `setHeader()` | Variable update. |
| **CorrelationDeclaration Shape** | `correlationExpression()` | Message correlation for stateful patterns. |
| **CallRules / CallPolicy Shape** | `bean()` | Business Rules Engine → Java bean or rule engine integration. |
| **Task Shape** | branch within `Listen` | Individual branch in a Listen shape. |
| **Group Shape** | No equivalent | Visual grouping only — no runtime impact. |
| **RoleLinkDeclaration Shape** | No equivalent | Port configuration metadata — no runtime impact. |

---

## BizTalk Error Handler → Camel Error Handling Mapping

| BizTalk Construct | Camel Equivalent | Description |
|---|---|---|
| Scope Shape with Exception Handler | `doTry/doCatch/doFinally` | Scoped error handling within a route. |
| Catch Exception Block | `doCatch(Exception.class)` | Catches specific exception types. |
| Suspend Shape | Dead Letter Channel + `deadLetterUri` | Dehydrates orchestration state — map to DLQ. |
| Retry in Port | `redeliveryPolicy()` | Configure `maximumRedeliveries`, `redeliveryDelay`, `backOffMultiplier`. |
| Send to Failed Message Routing | Dead Letter Channel | `.errorHandler(deadLetterChannel("jms:dlq"))` |

---

## Proprietary Adapters — Require User Decision

The following BizTalk adapters have **no direct Apache Camel equivalent**. When these are found, **stop and ask the user** how to handle each one.

| BizTalk Adapter | Situation | Suggested Alternatives | Notes |
|---|---|---|---|
| **WCF-Custom** | Custom binding | `cxf` with custom interceptors, or `http` for REST | Ask user for binding details and WSDL. |
| **WCF-CustomIsolated** | Custom isolated binding | Same as WCF-Custom | |
| **WCF-NetMsmq** | MSMQ over .NET | See **MSMQ Adapter Mapping** above | |
| **WCF-NetNamedPipe** | Named pipes (IPC) | `file` component with local paths, or `direct` for in-process | Named pipes are Windows-specific — ask about target OS. |
| **SAP Adapter** | SAP integration | `camel-sap` (requires SAP JCo license) | **ASK USER**: Is SAP JCo licensed? Are REST/SOAP APIs available? |
| **Siebel Adapter** | Siebel integration | `camel-http` with Siebel REST API | |
| **PeopleSoft Adapter** | PeopleSoft integration | `camel-http` with PeopleSoft REST/SOAP API | |
| **JD Edwards Adapter** | JD Edwards integration | `camel-http` with JD Edwards REST/SOAP API | |
| **TIBCO EMS Adapter** | TIBCO messaging | `camel-jms` with TIBCO EMS client | Ask user for TIBCO EMS configuration. |
| **Host Integration Server (HIS)** | Mainframe integration | `camel-mina` or `camel-netty` for TCP/IP, or custom adapter | **ASK USER**: What protocol does the mainframe use? |
| **SharePoint Adapter** | SharePoint integration | `camel-http` with SharePoint REST API (`camel-azure-sharepoint` if available) | Check catalog for `camel-azure-sharepoint` availability. |
| **Dynamics CRM Adapter** | Dynamics integration | `camel-http` with Dynamics 365 REST API, `camel-olingo4` for OData | |
| **Third-party custom adapters** | Unknown | **ASK USER** | Ask user for adapter purpose and documentation. |

When a proprietary adapter is found, ask the user:

```text
I found the following adapter with no direct Apache Camel equivalent:

- **[Adapter Name]** (used in: [orchestration/port name])
  Suggested alternatives based on adapter type:
  a) [best match from table above] — [brief description]
  b) [alternative]
  c) Keep as a TODO placeholder
  d) Remove this step

Your choice?
```

---

## BizTalk Variable/Context → Camel Header/Property Mapping

| BizTalk Scope | BizTalk Access | Camel Equivalent | Notes |
|---|---|---|---|
| Orchestration Variable | `myVar` | Exchange Header (`header.myVar`) | Local to the current exchange. |
| Orchestration Parameter | `param1` | Exchange Header (`header.param1`) | Passed to sub-orchestrations. |
| Message Context Property | `message(BTS.MessageID)` | Exchange Property (`exchangeProperty.MessageID`) | Survives routing slips. |
| Promoted Property | `message(namespace.PropertyName)` | Exchange Header (`header.PropertyName`) | Promoted from message. |
| Distinguished Field | `message.field1` | `${body.field1}` | Direct field access in message body. |
| Port Configuration | Static value | `{{PLACEHOLDER}}` in `application.properties` | External configuration. |

---

## BizTalk Map Transformation → Camel Mapping

See `biztalk-map-conversion.md` for the full decision matrix.

---

## BizTalk Pipeline Component → Camel Mapping

See `biztalk-pipeline-mapping.md` for pipeline stage mapping.

---

## BizTalk Expression → Camel Expression Mapping

See `biztalk-expression-mapping.md` for XLANG/s expression conversion.

---

## Notes

- Always verify component names in the MCP catalog before writing TDD entries (using `camel_catalog_component_doc`).
- CRITICAL: use the exact component scheme from the route URI (e.g., `smtp`, not `mail`).
- For MSMQ, WCF-Custom, and third-party adapters, always **ASK USER** before selecting a replacement.
- BizTalk orchestration variables map to Camel exchange headers (`${header.*}`).
- BizTalk message context properties map to Camel exchange properties (`${exchangeProperty.*}`).

# Spring Integration → Apache Camel Component Mapping Guide

This guide maps Spring Integration components to their Apache Camel equivalents. It is used by the `camel-migrate-spring` sub-skill during migration analysis.

---

## Core Messaging Components

| SI Component | XML / Annotation | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| DirectChannel | `<int:channel>` (default) | `direct:channelName` | `camel-direct` | Synchronous point-to-point. Default channel type in SI. |
| QueueChannel | `<int:channel><int:queue/></int:channel>` | `seda:channelName` | `camel-seda` | Asynchronous with internal queue. Not persistent across restarts. |
| PublishSubscribeChannel | `<int:publish-subscribe-channel>` | `direct:` with `multicast` EIP, or `seda:channelName?multipleConsumers=true` | built-in / `camel-seda` | Broadcasts to all subscribers. Use `multicast` for parallel dispatch. |
| ExecutorChannel | `<int:channel><int:dispatcher task-executor="..."/></int:channel>` | `seda:channelName` | `camel-seda` | Async dispatch. Configure Camel thread pool for equivalent behaviour. |
| PriorityChannel | `<int:priority-channel>` | `seda:channelName` with custom comparator | `camel-seda` | Use `seda` with a custom `Comparator` on the queue. |
| RendezvousChannel | `<int:rendezvous-channel>` | `seda:channelName?size=1` | `camel-seda` | Synchronous handoff. Use zero-capacity blocking queue semantics. |
| Bridge | `<int:bridge>` | `from("direct:a").to("direct:b")` | built-in | Connects two channels. Simple route with no processing. |
| Gateway | `<int:gateway>` / `@MessagingGateway` | `direct:gatewayName` | `camel-direct` | Entry point for request-reply. Use `direct:` as the Camel consumer. |
| Chain | `<int:chain>` | Sequential steps in a single route | built-in | Chains are just linear pipelines — map to consecutive steps in a route. |

---

## EIP Components

| SI Component | XML / Annotation | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| Transformer | `<int:transformer>` / `@Transformer` | `bean:beanName?method=methodName` or XSLT via Kaoto DataMapper | `camel-bean` / `camel-xslt-saxon` | Use `bean:` for method-based transformers. Use DataMapper for field-level mappings. See `spring-spel-conversion.md`. |
| Filter | `<int:filter>` / `@Filter` | `filter` EIP with `simple:`, `jsonpath:`, or `bean:` predicate | built-in | SpEL predicates → Simple language or bean predicate. |
| Router | `<int:router>` / `@Router` | `choice` EIP (content-based) or `recipientList` EIP (dynamic) | built-in | Header-value router → `choice/when`. Payload-type router → `choice` with `${body} is 'type'`. |
| Header Value Router | `<int:header-value-router>` | `choice` EIP with `${header.name}` conditions | built-in | Map each `<int:mapping>` to a `when` clause. |
| Payload Type Router | `<int:payload-type-router>` | `choice` EIP with type checks | built-in | Use `${body} is 'className'` or bean predicate. |
| Recipient List Router | `<int:recipient-list-router>` | `recipientList` EIP | built-in | Dynamic list of recipients. |
| Splitter | `<int:splitter>` / `@Splitter` | `split` EIP | built-in | `split(body())` for collections. SpEL expression → Simple or bean expression. |
| Aggregator | `<int:aggregator>` / `@Aggregator` | `aggregate` EIP | built-in | Map `correlation-strategy` → `correlationExpression`. Map `release-strategy` → `completionSize` / `completionTimeout` / custom `AggregationStrategy`. |
| Service Activator | `<int:service-activator>` / `@ServiceActivator` | `bean:beanName?method=methodName` | `camel-bean` | Direct bean invocation. Keep the same Spring bean class if possible. |
| Delayer | `<int:delayer>` | `delay` EIP | built-in | Map `default-delay` → delay duration. SpEL delay expression → Simple expression. |
| Resequencer | `<int:resequencer>` | `resequence` EIP | built-in | Map `release-partial-sequences` and `send-timeout` options. |
| Claim Check In | `<int:claim-check-in>` | `claimCheck` EIP (push) | built-in | Store message, replace with claim ticket. |
| Claim Check Out | `<int:claim-check-out>` | `claimCheck` EIP (pop/get) | built-in | Retrieve stored message by claim ticket. |
| Header Enricher | `<int:header-enricher>` | `setHeader` EIP | built-in | Map each `<int:header>` element to a `setHeader` step. |
| Content Enricher | `<int:enricher>` | `enrich` EIP | built-in | Content Enricher pattern. Map `request-channel` to `enrich` resource URI. |
| Scatter-Gather | `<int:scatter-gather>` | `multicast` EIP with `aggregationStrategy` | built-in | Parallel dispatch + aggregation. |
| Barricade (Barrier) | `<int:barrier>` | `aggregate` EIP with barrier completion | built-in | Synchronization barrier. Implement with custom completion predicate. |
| Control Bus | `<int:control-bus>` | JMX or `controlbus:` component | `camel-controlbus` | Runtime management. Use Camel's ControlBus component or JMX. |
| Wire Tap | `<int:wire-tap>` | `wireTap` EIP | built-in | Send copy to monitoring channel. |
| Idempotent Receiver | `<int:idempotent-receiver>` | `idempotentConsumer` EIP | built-in | Requires `IdempotentRepository` (in-memory or persistent). |
| Logger | `<int:logging-channel-adapter>` | `log` EIP | built-in | `.log(LoggingLevel.INFO, "message")` |

---

## Channel Adapters — Inbound (Consumer) and Outbound (Producer)

### HTTP

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| HTTP Inbound Gateway | `<int-http:inbound-gateway>` | `platform-http:/path` (consumer) | `camel-platform-http` | Preferred for Quarkus/Spring Boot. See **Platform-HTTP special case** in SKILL.md. |
| HTTP Inbound Channel Adapter | `<int-http:inbound-channel-adapter>` | `platform-http:/path` (consumer) | `camel-platform-http` | One-way inbound. |
| HTTP Outbound Gateway | `<int-http:outbound-gateway>` | `http:url` or `https:url` (producer) | `camel-http` | Use `http` for outbound HTTP calls. |
| HTTP Outbound Channel Adapter | `<int-http:outbound-channel-adapter>` | `http:url` (producer) | `camel-http` | One-way outbound. |

### JMS

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| JMS Message-Driven Channel Adapter | `<int-jms:message-driven-channel-adapter>` | `jms:queue:name` or `jms:topic:name` (consumer) | `camel-jms` | Supports queues and topics. |
| JMS Inbound Gateway | `<int-jms:inbound-gateway>` | `jms:queue:name` (consumer, request-reply) | `camel-jms` | Request-reply over JMS. |
| JMS Outbound Channel Adapter | `<int-jms:outbound-channel-adapter>` | `jms:queue:name` (producer) | `camel-jms` | One-way send. |
| JMS Outbound Gateway | `<int-jms:outbound-gateway>` | `jms:queue:name` (producer, request-reply) | `camel-jms` | Request-reply over JMS. |

### Kafka

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| Kafka Message-Driven Channel Adapter | `<int-kafka:message-driven-channel-adapter>` | `kafka:topic` (consumer) | `camel-kafka` | Map `consumer-properties` to Camel Kafka options. |
| Kafka Inbound Gateway | `<int-kafka:inbound-gateway>` | `kafka:topic` (consumer) | `camel-kafka` | Request-reply over Kafka. |
| Kafka Outbound Channel Adapter | `<int-kafka:outbound-channel-adapter>` | `kafka:topic` (producer) | `camel-kafka` | |
| Kafka Outbound Gateway | `<int-kafka:outbound-gateway>` | `kafka:topic` (producer) | `camel-kafka` | |

### File

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| File Inbound Channel Adapter | `<int-file:inbound-channel-adapter>` | `file:directoryName` (consumer) | `camel-file` | Map `directory`, `filename-pattern`, `prevent-duplicates`. |
| File Outbound Channel Adapter | `<int-file:outbound-channel-adapter>` | `file:directoryName` (producer) | `camel-file` | Map `directory`, `auto-create-directory`. |
| File Tail Inbound | `<int-file:tail-inbound-channel-adapter>` | `file:directoryName?readLock=changed` or custom | `camel-file` | May require custom implementation for true tail behaviour. |

### FTP / SFTP

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| FTP Inbound Channel Adapter | `<int-ftp:inbound-channel-adapter>` | `ftp:host:port/path` (consumer) | `camel-ftp` | Map session factory → URI options. |
| FTP Outbound Channel Adapter | `<int-ftp:outbound-channel-adapter>` | `ftp:host:port/path` (producer) | `camel-ftp` | |
| SFTP Inbound Channel Adapter | `<int-ftp:inbound-channel-adapter>` (with sftp session factory) | `sftp:host:port/path` (consumer) | `camel-ftp` | Use `sftp://` URI scheme. |
| SFTP Outbound Channel Adapter | `<int-ftp:outbound-channel-adapter>` (with sftp session factory) | `sftp:host:port/path` (producer) | `camel-ftp` | |

### JDBC / JPA

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| JDBC Inbound Channel Adapter | `<int-jdbc:inbound-channel-adapter>` | `sql:query` (consumer) | `camel-sql` | Map `query`, `data-source`, `update` (for post-select update). |
| JDBC Outbound Channel Adapter | `<int-jdbc:outbound-channel-adapter>` | `sql:statement` (producer) | `camel-sql` | |
| JDBC Outbound Gateway | `<int-jdbc:outbound-gateway>` | `sql:query` (producer) | `camel-sql` | Request-reply: query and return results. |
| Stored Procedure Outbound Gateway | `<int-jdbc:stored-proc-outbound-gateway>` | `sql-stored:procedureName` | `camel-sql` | Use `camel-sql` stored procedure support. |
| JPA Inbound Channel Adapter | `<int-jpa:inbound-channel-adapter>` | `jpa:entityClass` (consumer) | `camel-jpa` | Map `entity-class`, `jpa-query`. |
| JPA Outbound Channel Adapter | `<int-jpa:outbound-channel-adapter>` | `jpa:entityClass` (producer) | `camel-jpa` | |
| JPA Updating Outbound Gateway | `<int-jpa:updating-outbound-gateway>` | `jpa:entityClass` (producer) | `camel-jpa` | Merge/persist operations. |
| JPA Retrieving Outbound Gateway | `<int-jpa:retrieving-outbound-gateway>` | `jpa:entityClass` (producer) | `camel-jpa` | Named query retrieval. |

### AMQP

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| AMQP Inbound Channel Adapter | `<int-amqp:inbound-channel-adapter>` | `amqp:queue:name` (consumer) | `camel-amqp` | For RabbitMQ and AMQP 1.0 brokers. |
| AMQP Outbound Channel Adapter | `<int-amqp:outbound-channel-adapter>` | `amqp:queue:name` (producer) | `camel-amqp` | |
| AMQP Inbound Gateway | `<int-amqp:inbound-gateway>` | `amqp:queue:name` (consumer) | `camel-amqp` | Request-reply. |
| AMQP Outbound Gateway | `<int-amqp:outbound-gateway>` | `amqp:queue:name` (producer) | `camel-amqp` | |

### Web Services (SOAP)

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| WS Inbound Gateway | `<int-ws:inbound-gateway>` | `cxf:` or `spring-ws:` (consumer) | `camel-cxf` / `camel-spring-ws` | Use `cxf` for full WSDL/SOAP support. Use `spring-ws` for simpler cases. |
| WS Outbound Gateway | `<int-ws:outbound-gateway>` | `cxf:` or `spring-ws:` (producer) | `camel-cxf` / `camel-spring-ws` | |

### Mail

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| IMAP Inbound Channel Adapter | `<int-mail:imap-idle-channel-adapter>` or `<int-mail:inbound-channel-adapter>` (IMAP) | `imap:host` or `imaps:host` (consumer) | `camel-mail` | Use `imaps` for SSL. IDLE mode maps to Camel's `imap` consumer default polling. |
| POP3 Inbound Channel Adapter | `<int-mail:inbound-channel-adapter>` (POP3) | `pop3:host` or `pop3s:host` (consumer) | `camel-mail` | |
| Mail Outbound Channel Adapter | `<int-mail:outbound-channel-adapter>` | `smtp:host` or `smtps:host` (producer) | `camel-mail` | **CRITICAL — never use `mail` as the component scheme.** Always use `smtp`, `smtps`, `imap`, `imaps`, `pop3`, or `pop3s`. |

### MongoDB

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| MongoDB Inbound Channel Adapter | `<int-mongodb:inbound-channel-adapter>` | `mongodb:connectionBean` (consumer) | `camel-mongodb` | Map `collection-name`, `query`. |
| MongoDB Outbound Channel Adapter | `<int-mongodb:outbound-channel-adapter>` | `mongodb:connectionBean` (producer) | `camel-mongodb` | Map `collection-name`, `operation`. |
| MongoDB Outbound Gateway | `<int-mongodb:outbound-gateway>` | `mongodb:connectionBean` (producer) | `camel-mongodb` | |

### Redis

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| Redis Inbound Channel Adapter | `<int-redis:inbound-channel-adapter>` | `spring-redis:host:port` (consumer) | `camel-spring-redis` | Pub/sub or queue-based. |
| Redis Outbound Channel Adapter | `<int-redis:outbound-channel-adapter>` | `spring-redis:host:port` (producer) | `camel-spring-redis` | |
| Redis Store Inbound/Outbound | `<int-redis:store-inbound-channel-adapter>` | `spring-redis:host:port` | `camel-spring-redis` | Key-value operations. |

### Stream (stdin/stdout)

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| Stream Inbound | `<int-stream:stdin-channel-adapter>` | `stream:in` (consumer) | `camel-stream` | Read from stdin. |
| Stream Outbound | `<int-stream:stdout-channel-adapter>` | `stream:out` (producer) | `camel-stream` | Write to stdout. |

### TCP / UDP

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| TCP Inbound Gateway | `<int-ip:tcp-inbound-gateway>` | `netty:tcp://host:port` (consumer) | `camel-netty` | Map connection factory settings to Netty options. |
| TCP Outbound Gateway | `<int-ip:tcp-outbound-gateway>` | `netty:tcp://host:port` (producer) | `camel-netty` | |
| TCP Inbound Channel Adapter | `<int-ip:tcp-inbound-channel-adapter>` | `netty:tcp://host:port` (consumer) | `camel-netty` | |
| TCP Outbound Channel Adapter | `<int-ip:tcp-outbound-channel-adapter>` | `netty:tcp://host:port` (producer) | `camel-netty` | |
| UDP Inbound Channel Adapter | `<int-ip:udp-inbound-channel-adapter>` | `netty:udp://host:port` (consumer) | `camel-netty` | |
| UDP Outbound Channel Adapter | `<int-ip:udp-outbound-channel-adapter>` | `netty:udp://host:port` (producer) | `camel-netty` | |

### MQTT

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| MQTT Inbound Channel Adapter | `<int-mqtt:message-driven-channel-adapter>` | `paho-mqtt5:topic` or `paho:topic` (consumer) | `camel-paho-mqtt5` / `camel-paho` | Use `paho-mqtt5` for MQTT 5.0, `paho` for MQTT 3.x. |
| MQTT Outbound Channel Adapter | `<int-mqtt:outbound-channel-adapter>` | `paho-mqtt5:topic` or `paho:topic` (producer) | `camel-paho-mqtt5` / `camel-paho` | |

### XMPP

| SI Component | XML | Camel Equivalent | Camel Artifact | Notes |
|---|---|---|---|---|
| XMPP Inbound Channel Adapter | `<int-xmpp:inbound-channel-adapter>` | `xmpp:host:port/room` (consumer) | `camel-xmpp` | |
| XMPP Outbound Channel Adapter | `<int-xmpp:outbound-channel-adapter>` | `xmpp:host:port/room` (producer) | `camel-xmpp` | |

---

## Custom Beans — Require User Decision

The following Spring Integration patterns involve project-specific Spring beans. When these are found, **stop and ask the user** how to handle each one.

| SI Pattern | Situation | Suggested Alternatives | Notes |
|---|---|---|---|
| **Custom `@ServiceActivator` with complex logic** | Business logic in a Spring bean | a) Keep as `bean:beanName?method=methodName` — migrate the bean class, b) Re-implement using Camel EIPs, c) Keep as TODO | If the bean has simple logic, prefer `bean:`. |
| **Custom `@Transformer` with Java logic** | Transformation in a Spring bean | a) Keep as `bean:` processor, b) Convert to DataMapper XSLT, c) Keep as TODO | Evaluate complexity before choosing. |
| **Custom `@Router` with Java logic** | Routing decision in a Spring bean | a) Keep as `bean:` predicate in `choice` EIP, b) Re-implement with Simple predicates, c) Keep as TODO | |
| **Custom `@Filter` with Java logic** | Filtering in a Spring bean | a) Keep as `bean:` predicate in `filter` EIP, b) Re-implement with Simple predicates, c) Keep as TODO | |
| **Custom `@Splitter` with Java logic** | Splitting in a Spring bean | a) Keep as `bean:` expression in `split` EIP, b) Use built-in tokenizer/JsonPath, c) Keep as TODO | |
| **Custom `@Aggregator` with AggregationStrategy** | Custom aggregation | a) Keep as custom `AggregationStrategy` bean, b) Use built-in strategies, c) Keep as TODO | Camel also has `AggregationStrategy` — often a straightforward port. |
| **Custom `MessageHandler`** | Generic handler | a) Migrate as Camel `Processor` bean, b) Re-implement, c) Keep as TODO | |
| **Custom `ChannelInterceptor`** | Cross-cutting concern | a) Camel route policy, b) Camel interceptor (`interceptFrom`, `interceptSendToEndpoint`), c) Keep as TODO | |
| **Custom `MessageGroupProcessor`** | Group processing | a) Custom `AggregationStrategy`, b) Keep as TODO | |
| **Custom `ErrorHandler` bean** | Error handling | a) Map to Camel `onException` + custom processor, b) Keep as TODO | |

---

## Spring Integration Error Handling → Camel Error Handling Mapping

| SI Construct | Camel Equivalent | Description |
|---|---|---|
| Global `errorChannel` | `onException` at route-builder level or `errorHandler(deadLetterChannel("..."))` | Global error sink — all unhandled errors are routed here. |
| `error-channel` attribute (per endpoint) | `doTry/doCatch` or scoped `onException` | Per-endpoint error routing. |
| `ErrorMessage` wrapping | Camel `Exchange.getException()` | SI wraps the failed message; Camel stores the exception on the exchange. |
| `ExpressionEvaluatingRequestHandlerAdvice` with `successChannel` / `failureChannel` | `doTry/doCatch/doFinally` | Advice that routes to success or failure channel based on outcome. |
| `RequestHandlerRetryAdvice` | `errorHandler` with `redeliveryPolicy` | Retry with backoff. Map `maxAttempts`, `backOffPolicy`, `retryTemplate` → `maximumRedeliveries`, `redeliveryDelay`, `backOffMultiplier`. |
| `ExceptionTypeRouter` | `onException(ExceptionType.class)` | Route based on exception type. |
| `ErrorMessageExceptionTypeRouter` | `onException` with multiple exception types | Multiple exception type handlers. |
| Circuit Breaker (`RequestHandlerCircuitBreakerAdvice`) | `circuitBreaker` EIP (Resilience4j) | Requires `camel-resilience4j` dependency. |

---

## Spring Integration Poller → Camel Scheduling Mapping

| SI Poller Config | Camel Equivalent | Notes |
|---|---|---|
| `<int:poller fixed-delay="1000">` | `timer:name?delay=1000` or consumer `delay=1000` option | Fixed delay between poll completions. |
| `<int:poller fixed-rate="1000">` | `timer:name?fixedRate=true&period=1000` | Fixed rate regardless of poll duration. |
| `<int:poller cron="0/5 * * * * ?">` | `quartz:name?cron=0/5+*+*+*+*+?` | Cron scheduling. Use `quartz` component. |
| `<int:poller max-messages-per-poll="10">` | `maxMessagesPerPoll=10` on consumer | Limits messages per poll cycle. |
| Default poller `<int:poller default="true">` | Global consumer configuration | Set default polling interval on all polling consumers. |
| `Pollers.fixedDelay(1000)` (Java DSL) | `timer:name?delay=1000` | Java DSL equivalent. |
| `Pollers.cron("0/5 * * * * ?")` (Java DSL) | `quartz:name?cron=...` | Java DSL equivalent. |

---

## Spring Integration Variable Scoping → Camel Header/Property Mapping

| SI Scope | SI API | Camel Equivalent | Notes |
|---|---|---|---|
| Message Headers | `message.getHeaders().get("key")` / `headers['key']` (SpEL) | Exchange Header (`header.key`) | Direct mapping. SI headers → Camel headers. |
| Message Payload | `message.getPayload()` / `payload` (SpEL) | `${body}` | Message body. |
| MessageStore | `MessageStore.addMessage(...)` | Exchange Property (`exchangeProperty.key`) | Properties survive routing slips. |
| MetadataStore | `MetadataStore.put(key, value)` | `IdempotentRepository` or custom component | For idempotent processing and offset tracking. |
| IntegrationFlowContext | Runtime flow management | Camel `CamelContext` API | Dynamic route management. |
| MessageHistory | `MessageHistory` header | Camel `messageHistory` | Both frameworks support message history tracking. |

---

## Spring Integration vs Camel — Shared EIP Concepts

Both Spring Integration and Apache Camel implement the Enterprise Integration Patterns from the Hohpe/Woolf book. This makes migration conceptually straightforward for most patterns:

| EIP Pattern | SI Implementation | Camel Implementation | Migration Notes |
|---|---|---|---|
| Message Channel | `MessageChannel` (Direct, Queue, PubSub) | `direct:`, `seda:`, `multicast` | Channel types map closely. |
| Message Endpoint | Adapters, Gateways, Service Activators | `from()`, `to()`, `bean()` | SI separates inbound/outbound more explicitly. |
| Message Router | `@Router`, `<int:router>` | `choice`, `recipientList` EIPs | Both support content-based and header-based routing. |
| Message Translator | `@Transformer`, `<int:transformer>` | `bean:`, XSLT, DataMapper | SI uses method-based; Camel has more built-in options. |
| Message Filter | `@Filter`, `<int:filter>` | `filter` EIP | Direct mapping. |
| Splitter | `@Splitter`, `<int:splitter>` | `split` EIP | Direct mapping. |
| Aggregator | `@Aggregator`, `<int:aggregator>` | `aggregate` EIP | Both use correlation and completion strategies. |
| Wire Tap | `<int:wire-tap>` | `wireTap` EIP | Direct mapping. |
| Content Enricher | `<int:enricher>` | `enrich` / `pollEnrich` EIP | Direct mapping. |
| Claim Check | `<int:claim-check-in/out>` | `claimCheck` EIP | Direct mapping. |

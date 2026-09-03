# Camel Security Checklist

> Canonical source for the Camel-specific security rules shared by design, validation, and code review. Phase guides
> and personas keep their own framing (what to ask, what to print, how to classify a finding) and reference this file
> for the rules and configuration snippets instead of restating them. When a rule or snippet changes, change it here.

Loaded by `camel-design/guides/security.md` and `camel-design/guides/monitoring.md` during design, by every validation
pass (`camel-validate` guide manifest; used by `guides/quality-checks.md`, `guides/security-analysis.md`, and
`guides/anti-patterns.md`), and by the `code-quality-reviewer` persona. The `critic-security` persona inlines the subset
of these rules that applies at external boundaries — credentials, expression injection, TLS, header handling, and
authentication (rules 1, 2, 5, the logging part of rule 3, and the expression-injection part of rule 4) — so it stays
self-contained in a fresh context, and names this file as its source of truth.

Constitution rule 6 (External Configuration in `templates/constitution.md`; Iron Law 2 in `shared/iron-laws.md`) owns
the "never hardcode" rule itself. This file owns its security detail — detection patterns and secret-reference syntax —
plus the transport, logging, input-validation, and authentication rules.

## Core Rules

| # | Rule | What passes |
|---|------|-------------|
| 1 | **No hardcoded credentials** | No passwords, API keys, tokens, secrets, or `jdbc:` URLs with inline credentials in route YAML, in `application.properties` values, or as endpoint URI query parameters. Every secret is a placeholder resolved from the environment or a secrets manager (snippets below). Detection patterns: `password=`, `apiKey=`, `secret=`, `token=`, Base64 strings longer than 20 characters, `user:password@` inside a URI. |
| 2 | **TLS everywhere** | Every HTTP endpoint outside `localhost` uses `https://`; brokers use an SSL or SASL_SSL security protocol; database URLs require TLS with certificate verification; TLS 1.2 or higher; certificate validation is never disabled. |
| 3 | **No sensitive data in logs** | Log identifiers and selected fields, never the full `${body}` or all headers; never log `Authorization`, `X-API-Key`, `Cookie`, passwords, or tokens; log masking is enabled; PII fields are masked or omitted — canonical PII field list: `email`, `phone`, `ssn`, `creditCard` (credential keywords such as password, token, and API key are masked by default once masking is on). |
| 4 | **Input validation at every external or untrusted ingress** | External or newly untrusted input is schema-validated (`json-validator:`, `bean-validator:`, or an XML validator); message size limits are enforced at that ingress; trusted internal `direct:`/`seda:` hops inherit validation unless they cross a new trust boundary; SQL uses prepared statements; Simple, JSONPath, XPath, `toD`, `recipientList`, `routingSlip`, `dynamicRouter` expressions and scripting languages never evaluate unsanitized external input, and user input rendered into templates or scripts is escaped. |
| 5 | **Authentication on external endpoints** | Every externally exposed HTTP/REST endpoint authenticates callers (OAuth2/JWT, API key, or mutual TLS); no wildcard CORS unless the design spec allows it; headers from external systems are not forwarded blindly (`removeHeaders` where the design specifies sanitization). |

### Validation severity

`camel-validate` and the Bob validation gate use one mapping for both MCP and manual fallback results:

| Rule | Validation severity |
|---|---|
| 1. No hardcoded credentials | **FAIL** |
| 2. TLS everywhere | **FAIL** |
| 3. No sensitive data in logs | **FAIL** |
| 4. Input validation at every external or untrusted ingress | **FAIL** |
| 5. Authentication on external endpoints | **FAIL** |

Every confirmed violation blocks validation until fixed. MCP availability never changes severity. Reviewer and critic
personas may use their own output labels, but they must not downgrade a confirmed violation of these rules.

## Canonical Configuration Snippets

The snippets use Camel `{{...}}` property placeholders, which Camel resolves in route URIs and Camel component
configuration on every runtime. Inside `application.properties`, camel-main does not resolve `${...}`, while spring-boot
and quarkus also accept their framework `${...}` placeholders there (see `camel-validate/guides/quality-checks.md`,
Stage 7.2); `${...}` inside a route expression is Simple syntax and unaffected. Every component option below is a
catalog artifact: verify it under the project's version binding before use (Iron Law 1). The placeholder functions are
documented in the Camel manual and in the docs of the component that ships them — verify them there and add that
component's dependency.

### Secret references (rule 1)

```properties
# Environment variable — Camel core, no extra dependency
database.password={{env:DATABASE_PASSWORD}}

# Kubernetes Secret — camel-kubernetes
database.password={{secret:database-credentials/password}}

# HashiCorp Vault — camel-hashicorp-vault, camel.vault.hashicorp.* configured
database.password={{hashicorp:secret:database#password}}

# AWS Secrets Manager — camel-aws-secrets-manager, camel.vault.aws.* configured
database.password={{aws:database#password}}
```

### Transport security (rule 2)

```properties
# Kafka — TLS to the brokers; use SASL_SSL when SASL authentication is also required
camel.component.kafka.securityProtocol=SSL
camel.component.kafka.sslTruststoreLocation={{env:KAFKA_TRUSTSTORE_LOCATION}}
camel.component.kafka.sslTruststorePassword={{env:KAFKA_TRUSTSTORE_PASSWORD}}

# Kafka mutual TLS — the client keystore in addition to the truststore
camel.component.kafka.sslKeystoreLocation={{env:KAFKA_KEYSTORE_LOCATION}}
camel.component.kafka.sslKeystorePassword={{env:KAFKA_KEYSTORE_PASSWORD}}

# HTTP client — TLS settings through an SSLContextParameters bean
camel.component.http.sslContextParameters=#sslContextParameters

# Database — driver-level TLS with certificate and host verification (PostgreSQL example; sslmode=require
# encrypts but accepts any server certificate, and prefer or allow may fall back to plaintext)
database.url=jdbc:postgresql://db.internal:5432/orders?ssl=true&sslmode=verify-full
```

### Log masking (rule 3)

```properties
# Masks the values of Camel's default sensitive keywords (password, passphrase, secretKey, token, apiKey, ...)
# in Log EIP and log component output
camel.main.logMask=true
# Adds the canonical PII fields to the keywords the Log EIP masks. Output of the log: component masks only the
# default keywords unless a MaskingFormatter bean named CamelCustomLogMask is registered
camel.main.additionalSensitiveKeywords=email,phone,ssn,creditCard
```

```yaml
# Log identifiers, never the full body or all headers
- log:
    message: "Processing order ${body.orderId}"
```

Masking is the safety net, not the design: keep PII and credentials out of log messages in the first place.

### Input validation (rule 4)

```yaml
# JSON Schema on the raw payload
- to:
    uri: "json-validator:schemas/input-schema.json"
# Or Bean Validation annotations on the unmarshalled body class
- to:
    uri: "bean-validator:input"
```

```properties
# camel-main embedded HTTP server (platform-http) — reject request bodies above this size in bytes
camel.server.enabled=true
camel.server.maxBodySize=1048576
```

On spring-boot and quarkus the HTTP body limit is the framework server's request-size setting, not a Camel component
option; behind a gateway, enforce it there as well. Kafka message size is capped by the broker and topic configuration
(`message.max.bytes` / `max.message.bytes`), outside the Camel application; the Camel producer option
`camel.component.kafka.maxRequestSize` caps outbound records only. Broker limits are transport safeguards and do not
bound decoded application values: they limit Kafka record-batch bytes after compression when compression is enabled.
Camel batching is separate — with `batching=true`, Camel aggregates decoded records as `List<Exchange>`. Validate every
record before the first parser or enricher. Use a decoded-character limit for Camel's default `StringDeserializer`, a
byte limit for `ByteArrayDeserializer`, and fail closed for every other value type:

```java
static void validateKafkaRecordValue(Object value) {
    if (value == null) {
        throw new IllegalArgumentException("Kafka null value/tombstone rejected by default");
    }
    if (value instanceof String text) {
        int characters = text.codePointCount(0, text.length());
        if (characters > MAX_ALLOWED_CHARACTERS) {
            throw new IllegalArgumentException("Kafka payload too large: " + characters + " characters");
        }
        return;
    }
    if (value instanceof byte[] bytes) {
        if (bytes.length > MAX_ALLOWED_BYTES) {
            throw new IllegalArgumentException("Kafka payload too large: " + bytes.length + " bytes");
        }
        return;
    }
    throw new IllegalArgumentException("Define a Kafka payload-size rule for " + value.getClass().getName());
}

Object body = exchange.getMessage().getBody();
if (body instanceof List<?> batch) {
    for (Object item : batch) {
        if (!(item instanceof Exchange child)) {
            throw new IllegalArgumentException("Unexpected Kafka batch item type");
        }
        validateKafkaRecordValue(child.getMessage().getBody());
    }
} else {
    validateKafkaRecordValue(body);
}
```

If a compacted-topic delete is part of the route contract, handle the null value and stop that record before invoking
the validator or any parser; otherwise reject it as above. Apply the payload limit per record. When batch work itself
needs a bound, configure `maxPollRecords` or separately cap `batch.size()` without replacing the per-record check. If the
value deserializer changes, add a matching representation-specific rule. Never rely on a broker-side property as the
sole guard against oversized application payloads.

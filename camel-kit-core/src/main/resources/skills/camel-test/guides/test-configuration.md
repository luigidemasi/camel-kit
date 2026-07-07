# Test Configuration Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being tested
> - `TEST_DIR` — resolved test file directory
> - `RUNTIME` — project runtime (`main`, `spring-boot`, or `quarkus`)

---

## Step 5: Generate Test Configuration

### 5.1 Test Application Properties

**IMPORTANT: Save this file in the correct directory based on runtime!**

Create file: `{TEST_DIR}application-test.properties`

Build this file from the actual route endpoint URIs and `camel_component_properties` metadata captured during route
analysis. Override only the properties the route already uses or needs for test isolation. Do not invent generic values
when the route defines a topic, consumer group, serializer, datasource bean name, HTTP base URL, or query parameter.

```properties
# ============================================
# Test Configuration for {flow-name}
# ============================================

# Camel configuration for testing
# Spring Boot only: camel.springboot.name={flow-name}-test
# Quarkus only:     quarkus.camel.routes-discovery.enabled=true
# Main runtime: no framework-specific config needed

# Override with test-specific values
kafka.topic.input=test-orders
kafka.topic.dlq=test-orders-dlq

# Use testcontainer-provided values
camel.component.kafka.brokers=${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}
camel.beans.dataSource.url=${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}
camel.beans.dataSource.username=${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}
camel.beans.dataSource.password=${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}

# Forage projects (rung 1): override the same keys the app defines
# forage.myDb.jdbc.url=${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}
# forage.myDb.jdbc.username=${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}
# forage.myDb.jdbc.password=${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}

# Test-specific settings
camel.component.kafka.autoOffsetReset=earliest
```

> **Note:** the `${CITRUS_TESTCONTAINERS_*}` values above are resolved by Citrus at test runtime — this `${}` shape is test-file-specific. Do NOT generalize `${}` to non-test properties files on the main runtime; camel-main resolves `{{key}}`, not `${key}` (see properties-generation.md §5.1).

> **Forage override channels:** Forage resolves configuration with precedence env vars > system properties > properties files. In CI, `FORAGE_<DOMAIN>_<PROP>` environment variables (e.g. `FORAGE_JDBC_URL`) can override the app's `forage.*` keys without touching files — useful when the test harness cannot write properties.

The snippet above is a fallback pattern. Prefer the route's real property keys and endpoint option names. For example,
if the route uses `kafka:orders?groupId=order-writer&autoOffsetReset=latest`, keep the actual topic and group id and
override only the broker address and test-specific offset behavior deliberately.

**Include only the runtime-specific line that matches the project runtime (from `RUNTIME` context variable).** Do not include comments for other runtimes in the actual generated file — the comments above are for reference only.

### 5.2 Test Dependencies

Document additional test dependencies:

```xml
<!-- Test Dependencies -->
<dependency>
  <groupId>org.citrusframework</groupId>
  <artifactId>citrus-testcontainers</artifactId>
  <scope>test</scope>
</dependency>

<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>kafka</artifactId>
  <scope>test</scope>
</dependency>

<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
```

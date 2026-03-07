# Test Configuration Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being tested
> - `TEST_DIR` — resolved test file directory
> - `RUNTIME` — project runtime (`jbang`, `springboot`, or `quarkus`)

---

## Step 5: Generate Test Configuration

### 5.1 Test Application Properties

**IMPORTANT: Save this file in the correct directory based on runtime!**

Create file: `{TEST_DIR}application-test.properties`

```properties
# ============================================
# Test Configuration for {flow-name}
# ============================================

# Camel configuration for testing
# Spring Boot only: camel.springboot.name={flow-name}-test
# Quarkus only:     quarkus.camel.routes-discovery.enabled=true
# JBang: no framework-specific config needed

# Override with test-specific values
kafka.topic.input=test-orders
kafka.topic.dlq=test-orders-dlq

# Use testcontainer-provided values
camel.component.kafka.brokers=${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}
camel.beans.dataSource.url=${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}
camel.beans.dataSource.username=${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}
camel.beans.dataSource.password=${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}

# Test-specific settings
camel.component.kafka.autoOffsetReset=earliest
```

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

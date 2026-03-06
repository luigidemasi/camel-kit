# Test Configuration Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being tested
> - `TEST_DIR` — resolved test file directory

---

## Step 4: Generate Test Configuration

### 4.1 Test Application Properties

**IMPORTANT: Save this file in the correct directory based on runtime!**

Create file: `{TEST_DIR}application-test.properties`

```properties
# ============================================
# Test Configuration for {flow-name}
# ============================================

# Camel configuration for testing
camel.springboot.name={flow-name}-test

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

### 4.2 Test Dependencies

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

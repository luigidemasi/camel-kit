# /camel.implement

You are generating the Camel integration code from the flow definition and route design. Follow these steps exactly.

The user runs: `/camel.implement <flow-name>`

---

## Step 1: Load Context

Read these files in order:

1. `.camel-kit/flows/[flow-name]/flow.md` - Flow definition
2. `.camel-kit/flows/[flow-name]/flow.md` - Route design (REQUIRED)
3. `.camel-kit/templates/yaml-generation-guide.md` - YAML DSL rules (REQUIRED)
4. `.camel-kit/constitution.md` - Quality principles
5. `.camel-kit/.cache/components-*.json` - Component catalog
6. `.camel-kit/.cache/kamelets-*.json` - Kamelet catalog

**Error conditions:**
- If `flow.md` does not exist: ERROR "Route design not found. Run /camel.route [flow-name] first."
- If `yaml-generation-guide.md` does not exist: WARN and proceed with standard Camel YAML DSL.

---

## Step 2: Pre-Implementation Checks

### 2.1 Schema Verification

Check that all schemas referenced in the route design exist:

```
Checking schemas...
✓ schemas/order-message.json
✗ schemas/customer.json (missing)
```

If schemas are missing:
- Offer to generate them based on the route's data contracts
- Or prompt user to create them first

### 2.2 Constitution Gate Check

Verify the route design passes all constitution gates from `flow.md`:

```
Constitution Gate Check:
✓ Route Structure
✓ Single Responsibility
✓ Error Handling Mandatory
✓ External Configuration
```

If any gates are unchecked or failed, warn the user before proceeding.

---

## Step 3: YAML Generation

### 3.1 Read Generation Rules

**CRITICAL**: Follow the rules in `.camel-kit/templates/yaml-generation-guide.md`:

- Use standard Camel YAML DSL format (not alternative formats)
- Use `steps:` array format for Kaoto compatibility
- Use explicit `uri:` and `parameters:` structure
- Use object format for EIPs (not shorthand)
- Include route metadata (`id`, `description`)
- Place error handlers at route level

### 3.2 Generate Route

Generate the single route defined in `flow.md`:

**IMPORTANT**: Keep routes CLEAN - connection details go in `application.properties`, NOT in the route YAML.

1. **Create route structure:**
   ```yaml
   - route:
       id: [flow-name]
       description: [route description from plan]
   ```

2. **Configure Source (Consumer):**
   - Use simple URI with only topic/endpoint placeholder: `kafka:{{kafka.topic.orders}}`
   - Do NOT include connection parameters (brokers, credentials) in the URI
   - Connection details are configured at component level in `application.properties`

3. **Add Processing Steps:**
   - For each EIP in the plan's Processing Steps table:
     - Use the correct YAML structure from yaml-generation-guide.md
     - Preserve step order from the plan
     - Include nested `steps:` arrays where required

4. **Configure Sink (Producer):**
   - Use simple URI: `sql:INSERT INTO...` or `kafka:{{kafka.topic.output}}`
   - Reference beans with `#beanName` syntax
   - Do NOT include connection details in the URI

5. **Add Error Handling:**
   - Use simple DLQ URI: `kafka:{{kafka.topic.orders}}-dlq`
   - Implement the error strategy from the plan
   - Use `errorHandler:` at route level for Kaoto visibility

**Example Clean Route:**
```yaml
- route:
    id: order-ingestion
    description: Consume orders from Kafka and persist to database

    errorHandler:
      deadLetterChannel:
        deadLetterUri: "kafka:{{kafka.topic.orders}}-dlq"

    from:
      uri: "kafka:{{kafka.topic.orders}}"
      steps:
        - unmarshal:
            json:
              library: Jackson
        - to:
            uri: "sql:INSERT INTO orders (id, amount) VALUES (:#id, :#amount)"
```

Note: No `brokers=`, `dataSource=`, or other connection parameters in the route.

### 3.3 File Output

Generate a single file named after the flow:

```
[flow-name].camel.yaml
```

Example: `order-ingestion.camel.yaml`

### 3.4 Generation Report

Show what was generated:

```
Generated Camel Route:

FILE: [flow-name].camel.yaml

ROUTE: [flow-name]
  Source: [component]:[uri]
  Steps: [step1] → [step2] → [step3]
  Sink: [component]:[uri]
  Error Handling: [strategy]

SCHEMAS USED:
  - schemas/[schema-name].json

CONFIGURATION PROPERTIES (application.properties):
  - kafka.brokers=localhost:9092
  - kafka.topic.orders=orders
  - [other properties...]

EXTERNAL SERVICES (docker-compose.yaml):
  - kafka (confluentinc/cp-kafka:7.5.0)
  - postgres (postgres:16-alpine)
```

---

## Step 4: YAML Validation

### 4.1 Kaoto Compatibility Check

Verify the generated YAML follows Kaoto requirements:

```
Kaoto Compatibility Check:
✓ Standard route format (- route:)
✓ Steps array format used
✓ Explicit uri/parameters structure
✓ Route ID present
✓ Error handler at route level
```

### 4.2 Schema Validation

Validate generated YAML against the Camel YAML DSL schema:

```bash
# Quick validation using Camel CLI
camel run --check [flow-name].camel.yaml application.properties
```

The schema is available in `org.apache.camel:camel-yaml-dsl:{{CAMEL_VERSION}}` JAR at `schema/camelYamlDsl.json`.

Report validation status:
```
YAML Schema Validation:
✓ Valid YAML syntax
✓ Schema validation passed
✓ All property placeholders have values in application.properties
```

If any checks fail, fix the YAML before saving.

---

## Step 5: Create Supporting Files

### 5.1 Application Properties

**CRITICAL**: Use component-level configuration. This keeps routes clean and portable.

Create or update `application.properties` with:
1. **Component-level settings** (connection details)
2. **Bean definitions** (for DataSources, etc.)
3. **Route placeholders** (topic names, etc.)

```properties
# ==============================================
# Camel Application Properties for [flow-name]
# Generated by camel-kit
# ==============================================

# ----------------------------------------------
# COMPONENT CONFIGURATION
# Syntax: camel.component.<component>.<property>=<value>
# These apply to ALL endpoints using the component
# ----------------------------------------------

# Kafka Component (if used)
camel.component.kafka.brokers=localhost:9092
camel.component.kafka.groupId=[flow-name]-consumer

# SQL Component (if used) - autowires dataSource bean
# camel.component.sql.dataSource=#dataSource

# MongoDB Component (if used)
# camel.component.mongodb.mongoConnection=#mongoClient

# HTTP Component (if used)
# camel.component.http.connectTimeout=30000

# ----------------------------------------------
# BEAN DEFINITIONS
# Syntax: camel.beans.<beanName>=#class:<fully.qualified.ClassName>
# Use #class: prefix to instantiate the class
# ----------------------------------------------

# DataSource Bean (for SQL component)
camel.beans.dataSource=#class:org.apache.commons.dbcp2.BasicDataSource
camel.beans.dataSource.driverClassName=org.postgresql.Driver
camel.beans.dataSource.url=jdbc:postgresql://localhost:5432/[database-name]
camel.beans.dataSource.username=postgres
camel.beans.dataSource.password=postgres

# ----------------------------------------------
# ROUTE PLACEHOLDERS
# Used in route URIs as {{property.name}}
# ----------------------------------------------

# Topic/Queue names
kafka.topic.orders=orders
kafka.topic.dlq=orders-dlq

# Other route-specific configuration
# [Add any {{PLACEHOLDER}} values from the route]

# ----------------------------------------------
# JBANG DEPENDENCIES
# Syntax: camel.jbang.dependencies=groupId:artifactId:version,...
# These are loaded automatically by Camel JBang
# Do NOT add Camel components - they are auto-resolved
# ----------------------------------------------

camel.jbang.dependencies=org.postgresql:postgresql:42.7.3,\
org.apache.commons:commons-dbcp2:2.12.0
```

**Common Dependencies by Component:**

| Component | Dependencies |
|-----------|-------------|
| sql/jdbc | `org.postgresql:postgresql:42.7.3`, `org.apache.commons:commons-dbcp2:2.12.0` |
| mongodb | `org.mongodb:mongodb-driver-sync:5.0.0` |
| jpa | `org.hibernate:hibernate-core:6.4.0.Final`, JDBC driver |
| amqp | `org.apache.qpid:qpid-jms-client:2.5.0` |

**Key Rules:**
- Use `camel.component.<name>.<property>` for connection details (brokers, credentials, timeouts)
- Use `camel.beans.<name>=#class:<ClassName>` for bean instantiation (note the `#class:` prefix!)
- Use simple property names for route placeholders (`kafka.topic.orders`)
- The SQL component auto-wires the dataSource bean if only one DataSource exists

### 5.2 Docker Compose for External Systems

Based on the source/sink components in the flow, generate `docker-compose.yaml`:

```yaml
# Docker Compose for [flow-name] development environment
# Generated by camel-kit
#
# Start with: docker-compose up -d
# Stop with: docker-compose down

services:
  # Kafka (if kafka component is used)
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    hostname: kafka
    container_name: kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:29093
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://kafka:29093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LOG_DIRS: /tmp/kraft-combined-logs
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk

  # PostgreSQL (if sql/jpa/jdbc component is used)
  postgres:
    image: postgres:16-alpine
    container_name: postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: orders
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    volumes:
      - postgres_data:/var/lib/postgresql/data

  # MongoDB (if mongodb component is used)
  # mongodb:
  #   image: mongo:7
  #   container_name: mongodb
  #   ports:
  #     - "27017:27017"

  # ActiveMQ Artemis (if jms/amqp component is used)
  # artemis:
  #   image: apache/activemq-artemis:latest
  #   container_name: artemis
  #   ports:
  #     - "61616:61616"
  #     - "8161:8161"

  # Redis (if redis component is used)
  # redis:
  #   image: redis:7-alpine
  #   container_name: redis
  #   ports:
  #     - "6379:6379"

volumes:
  postgres_data:
```

**Rules for docker-compose generation:**
- Only include services that are actually used by the flow
- Use standard ports for each service
- Match usernames/passwords with application.properties
- Use Alpine/lightweight images when possible
- Add volume mounts for data persistence

### 5.3 Run Script

Generate a `run.sh` script:

```bash
#!/bin/bash
# Run script for [flow-name]
# Generated by camel-kit

# Start external services (if not already running)
# docker compose up -d

# Run the Camel integration
# Dependencies are loaded from application.properties (camel.jbang.dependencies)
camel run [flow-name].camel.yaml application.properties
```

Make it executable: `chmod +x run.sh`

---

## Step 6: Summary and Next Steps

Present completion summary:

```
============================================
IMPLEMENTATION COMPLETE: [flow-name]
============================================

CREATED FILES:
  [flow-name].camel.yaml       Camel route (Kaoto compatible)
  application.properties       Component, bean & dependency configuration
  docker-compose.yaml          External services (Kafka, DB, etc.)
  run.sh                       Run script

CONFIGURATION APPROACH:
  ✓ Component-level config (camel.component.<name>.<prop>)
  ✓ Bean definitions with #class: prefix
  ✓ Clean routes without connection details

NEXT STEPS:

  1. Start external services:
     docker-compose up -d

  2. Run the integration:
     ./run.sh
     # Or manually:
     camel run [flow-name].camel.yaml application.properties

  3. Validate route:
     /camel.validate

  4. Open in Kaoto for visual editing:
     Open [flow-name].camel.yaml in VS Code with Kaoto extension

  5. Run tests:
     /camel.test [flow-name]

  6. Export to Maven project:
     camel export [flow-name].camel.yaml --runtime quarkus

  7. Stop services when done:
     docker-compose down

DOCUMENTATION:
  - Camel JBang: https://camel.apache.org/manual/camel-jbang.html
  - Camel Property Binding: https://camel.apache.org/manual/property-binding.html
  - Kaoto: https://kaoto.io/
```

---

## Error Handling

### Missing Route Design
```
ERROR: Route design not found.

The flow.md file is required for implementation.
Run /camel.route [flow-name] first to create the technical design.
```

### Invalid Route Design Structure
```
ERROR: Route design structure is incomplete.

Missing sections:
- Source configuration
- Error Handling

Please update the route design with /camel.route [flow-name]
```

### Schema Mismatch
```
WARNING: Schema referenced in route design does not match existing file.

Plan expects: schemas/order.json with fields [orderId, amount, customerId]
Found: schemas/order.json with fields [id, total, customer]

Options:
1. Update schema to match route design
2. Update route design to match schema
3. Proceed anyway (may cause runtime errors)
```

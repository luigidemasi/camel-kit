# /camel.implement

You are generating the Camel integration code from the flow definition and route design. Follow these steps exactly.

The user runs: `/camel.implement <flow-name>`

---

## Step 1: Load Context

Read these files in order:

1. `.camel-kit/config.yaml` - Get Camel version
2. `.camel-kit/flows/[flow-name]/flow.md` - Flow definition and route design (REQUIRED)
3. `.camel-kit/templates/yaml-generation-guide.md` - YAML DSL rules (REQUIRED)
4. `.camel-kit/constitution.md` - Quality principles
5. `.camel-kit/.cache/components-{version}.json` - Component catalog (CRITICAL for correct options)
6. `.camel-kit/.cache/kamelets-{version}.json` - Kamelet catalog
7. `.camel-kit/.cache/camelYamlDsl-{version}.json` - YAML DSL schema

**Error conditions:**
- If `flow.md` does not exist: ERROR "Route design not found. Run /camel.flow [flow-name] first."
- If `yaml-generation-guide.md` does not exist: WARN and proceed with standard Camel YAML DSL.
- If component catalog is missing: WARN "Component catalog not found. Run 'camel-kit catalog fetch' first."

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

## Step 3: Component Catalog Lookup

**CRITICAL: Before generating any YAML, look up each component in the catalog to ensure correct usage.**

### 3.1 Identify Components from Flow Design

Extract all Camel components mentioned in the flow design:
- Source component (e.g., `kafka`, `file`, `timer`)
- Sink component (e.g., `sql`, `kafka`, `http`)
- Any intermediate components (e.g., `bean`, `log`)

### 3.2 Look Up Each Component in Catalog

For each component, read its entry from `.camel-kit/.cache/components-{version}.json`:

```json
{
  "components": {
    "kafka": {
      "component": {
        "name": "kafka",
        "title": "Kafka",
        "description": "...",
        "producerOnly": false,
        "consumerOnly": false
      },
      "componentProperties": {
        "brokers": { "type": "string", "required": false, ... },
        "groupId": { "type": "string", "required": false, ... }
      },
      "properties": {
        "topic": { "type": "string", "required": true, "kind": "path", ... },
        "brokers": { "type": "string", "required": false, "kind": "parameter", ... },
        "groupId": { "type": "string", "required": false, "kind": "parameter", ... }
      }
    }
  }
}
```

### 3.3 Extract Key Information

For each component, extract:

| Information | Catalog Path | Use For |
|-------------|--------------|---------|
| Component name | `component.name` | Verify correct spelling |
| Required options | `properties[*].required == true` | Must include in URI or parameters |
| Path options | `properties[*].kind == "path"` | Include in URI path |
| Parameter options | `properties[*].kind == "parameter"` | Include in `parameters:` or URI query |
| Option types | `properties[*].type` | Correct value format |
| Default values | `properties[*].defaultValue` | Only specify if different |
| Component-level options | `componentProperties` | Goes in `application.properties` |

### 3.4 Component Verification Report

Show verification for each component:

```
Component Catalog Verification:

KAFKA (source):
  ✓ Component exists in catalog
  ✓ Can be used as consumer (consumerOnly=false, producerOnly=false)
  Required options: topic (path)
  Component-level config: brokers, groupId, securityProtocol
  → Use: kafka:{{kafka.topic.orders}}
  → Config: camel.component.kafka.brokers=localhost:9092

SQL (sink):
  ✓ Component exists in catalog
  ✓ Can be used as producer
  Required options: query (path)
  Component-level config: dataSource
  → Use: sql:INSERT INTO...
  → Config: camel.component.sql.dataSource=#dataSource
```

### 3.5 Option Placement Rules

Based on catalog lookup, place options correctly:

| Option Kind | Catalog Property | Where to Place |
|-------------|------------------|----------------|
| Path option | `kind: "path"` | In URI path: `kafka:mytopic` |
| Endpoint parameter | `kind: "parameter"` | In URI query or `parameters:` block |
| Component-level | In `componentProperties` | In `application.properties` as `camel.component.<name>.<prop>` |

**Example based on catalog:**

```yaml
# Kafka: topic is path option, brokers is component-level
from:
  uri: "kafka:{{kafka.topic.orders}}"  # topic is path option
  # brokers NOT here - it's component-level, goes in application.properties

# SQL: query is path option, dataSource is component-level
to:
  uri: "sql:INSERT INTO orders VALUES (:#${body.id})"
  # dataSource NOT here - it's component-level, goes in application.properties
```

---

## Step 4: YAML Generation

### 4.1 Read Generation Rules

**CRITICAL**: Follow the rules in `.camel-kit/templates/yaml-generation-guide.md`:

- Use standard Camel YAML DSL format (not alternative formats)
- Use `steps:` array format for Kaoto compatibility
- Use explicit `uri:` and `parameters:` structure
- Use object format for EIPs (not shorthand)
- Include route metadata (`id`, `description`)
- Place error handlers at route level

### 4.2 Generate Route (Using Catalog)

Generate the single route defined in `flow.md`, using component catalog information:

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

### 4.3 File Output

Generate a single file named after the flow:

```
[flow-name].camel.yaml
```

Example: `order-ingestion.camel.yaml`

### 4.4 Generation Report

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

## Step 5: YAML Schema Validation (MANDATORY LOOP)

**CRITICAL: You MUST validate the generated YAML and loop until it is valid. DO NOT proceed to Step 6 until validation passes.**

### 5.1 Load Camel YAML Schema

Load the Camel YAML DSL schema from the local cache:

```
Schema file: .camel-kit/.cache/camelYamlDsl-{{CAMEL_VERSION}}.json
```

Replace `{{CAMEL_VERSION}}` with the project's Camel version from `.camel-kit/config.yaml`.

**MANDATORY: Read the schema file before validation:**
```
Read: .camel-kit/.cache/camelYamlDsl-{version}.json
```

### 5.2 Validation Loop Process

**Use the Maven Wrapper with the Camel YAML DSL Validator plugin:**

```
LOOP until valid:

  ITERATION [N]:

  1. RUN VALIDATOR:
     ./mvnw org.apache.camel:camel-yaml-dsl-validator:{{CAMEL_VERSION}}:validate \
       -Dcamel.validator.files=[flow-name].camel.yaml

  2. CHECK OUTPUT:
     - "BUILD SUCCESS" → Validation passed → EXIT loop
     - "BUILD FAILURE" → Validation failed → Continue to step 3

  3. PARSE ERRORS from validator output:
     - Extract error messages (lines starting with [ERROR])
     - Identify JSON path (e.g., $.route.from.steps[0].onException.handled)
     - Identify expected vs found types

  4. FIX ERRORS based on validator feedback:
     - Apply corrections according to schema rules
     - Use the Common Validation Errors table below

  5. REWRITE the YAML file with fixes

  6. INCREMENT iteration counter

  7. REPEAT from step 1
```

**Output format for each iteration:**

```
=== VALIDATION ITERATION [N] ===

Running: ./mvnw org.apache.camel:camel-yaml-dsl-validator:4.14.5:validate \
         -Dcamel.validator.files=order-ingestion.camel.yaml

[validator output here]

ERRORS FOUND: 2
  1. $.route.from.steps[0].onException.handled: boolean found, object expected
  2. $.route.from.steps[2].to: unknown property 'datasource'

FIXING:
  Error 1: Converting 'handled: true' to 'handled: { constant: { expression: "true" } }'
  Error 2: Renaming 'datasource' to 'dataSource'

REWRITING order-ingestion.camel.yaml...

REVALIDATING...
```

### 5.3 Common Validation Errors and Fixes

| Error | Wrong | Correct |
|-------|-------|---------|
| `handled` requires expression | `handled: true` | `handled: { constant: { expression: "true" } }` |
| `continued` requires expression | `continued: true` | `continued: { constant: { expression: "true" } }` |
| Invalid property name | `datasource:` | `dataSource:` (camelCase) |
| Missing required `uri` | `to: kafka:topic` | `to: { uri: "kafka:topic" }` |
| Invalid expression format | `simple: expr` (in some contexts) | `simple: { expression: "expr" }` |
| Wrong array format | `exception: MyException` | `exception: [ "MyException" ]` |
| Missing steps array | `from: uri: ...` then `to:` | `from: uri: ... steps: [ { to: ... } ]` |
| Wrong choice structure | `choice: when:` | `choice: { when: [ ... ], otherwise: ... }` |
| Invalid marshal/unmarshal | `marshal: json` | `marshal: { json: { library: Jackson } }` |

### 5.4 Validation Output Format

Show this output for each validation iteration:

```
=== VALIDATION ITERATION [N] ===

Loading schema: .camel-kit/.cache/camelYamlDsl-4.14.5.json
Validating: [flow-name].camel.yaml

Checking YAML syntax... ✓
Checking route structure... ✓
Checking element: from (line 5)... ✓
Checking element: unmarshal (line 8)... ✓
Checking element: filter (line 12)... ✓
Checking element: onException (line 20)... ❌

ERRORS FOUND: 1

  Line 22: Invalid value for 'handled'
           Path: /route/from/steps/3/onException/handled
           Expected: object with expression (e.g., { constant: { expression: "true" } })
           Found: boolean (true)

FIXING...

  Before:
    onException:
      exception:
        - "java.lang.Exception"
      handled: true

  After:
    onException:
      exception:
        - "java.lang.Exception"
      handled:
        constant:
          expression: "true"

REVALIDATING...
```

### 5.5 Final Validation Report

After all iterations, when validation passes:

```
=== VALIDATION PASSED ===

Schema: camelYamlDsl-4.14.5.json
File: [flow-name].camel.yaml
Iterations: 2
Errors found: 3
Errors fixed: 3
Final status: ✓ VALID

Route Structure:
  ✓ Route wrapper present
  ✓ Route ID: [flow-name]
  ✓ From URI valid
  ✓ Steps array format
  ✓ Error handler present

Kaoto Compatibility:
  ✓ Standard route format (- route:)
  ✓ Steps array format used
  ✓ Explicit uri/parameters structure
  ✓ Route ID present
  ✓ Error handler at route level

Proceeding to Step 6...
```

### 5.6 Schema Reference for Common Elements

Quick reference for frequently used elements (from schema):

**Route Structure:**
```yaml
- route:
    id: "route-id"           # REQUIRED
    description: "..."        # optional
    from:
      uri: "component:path"   # REQUIRED
      steps: []               # REQUIRED - array of steps
```

**To/Log/Bean:**
```yaml
- to:
    uri: "component:path"
    parameters:
      key: value

- log:
    message: "..."
    loggingLevel: INFO

- bean:
    ref: "beanName"
    method: "methodName"
```

**Choice/Filter:**
```yaml
- choice:
    when:
      - simple: "${body.type} == 'A'"
        steps: []
    otherwise:
      steps: []

- filter:
    simple: "${body.amount} > 100"
    steps: []
```

**Error Handling:**
```yaml
- onException:
    exception:
      - "java.lang.Exception"
    handled:
      constant:
        expression: "true"    # NOT boolean!
    to:
      uri: "kafka:dlq"
```

### 5.7 Automated Validation Command (MANDATORY)

**Run this command to validate the generated YAML using the official Camel YAML DSL Validator:**

```bash
./mvnw org.apache.camel:camel-yaml-dsl-validator:{{CAMEL_VERSION}}:validate -Dcamel.validator.files=[flow-name].camel.yaml
```

Replace `{{CAMEL_VERSION}}` with the project's Camel version (e.g., `4.14.5`).

**This command:**
- Uses the official Apache Camel YAML DSL validator Maven plugin
- Validates YAML syntax and structure
- Validates against the official Camel YAML DSL schema
- Reports errors with file name and details
- Returns exit code 0 if valid, non-zero if invalid
- Works cross-platform via Maven Wrapper (no Maven installation required)

**Validation Loop Process:**

```
LOOP:
  1. RUN: ./mvnw org.apache.camel:camel-yaml-dsl-validator:{{CAMEL_VERSION}}:validate \
         -Dcamel.validator.files=[flow-name].camel.yaml

  2. EXAMINE output:
     - If "BUILD SUCCESS" → validation passed → EXIT loop → proceed to Step 6
     - If "BUILD FAILURE" → validation failed → continue to step 3

  3. PARSE errors from output:
     - Extract error messages
     - Identify the problematic YAML elements
     - Map errors to line numbers in the file

  4. FIX each error:
     - Apply corrections based on schema rules
     - Common fixes: expression objects, camelCase properties, array formats

  5. REWRITE the corrected YAML file

  6. REPEAT from step 1
```

**Example validation output (errors):**
```
[INFO] Validating: order-ingestion.camel.yaml
[ERROR] order-ingestion.camel.yaml: $.route.from.steps[0].onException.handled:
        boolean found, object expected
[ERROR] order-ingestion.camel.yaml: $.route.from.steps[2].to:
        unknown property 'datasource'
[INFO] BUILD FAILURE
```

**Example validation output (success):**
```
[INFO] Validating: order-ingestion.camel.yaml
[INFO] order-ingestion.camel.yaml: OK
[INFO] BUILD SUCCESS
```

**After successful validation, proceed to Step 6.**

**Documentation:** https://camel.apache.org/manual/camel-yaml-dsl-validator-maven-plugin.html

---

## Step 6: Create Supporting Files (Using Catalog)

### 6.1 Application Properties (From Catalog)

**CRITICAL**: Use component-level configuration. This keeps routes clean and portable.

**Use the component catalog** to identify which properties are component-level (`componentProperties`):

```
For each component used in the route:
1. Read component entry from .camel-kit/.cache/components-{version}.json
2. Look at componentProperties section
3. Generate camel.component.<name>.<prop>=<value> for each needed property
```

Create or update `application.properties` with:
1. **Component-level settings** (from catalog's `componentProperties`)
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

### 6.2 Docker Compose for External Systems

#### Docker Compose Guidelines

##### Volume Mounts
- For bind mounts (local directories), always add the `:Z` suffix for SELinux compatibility:
  ```yaml
  - ./postgres_data:/var/lib/postgresql/data:Z
  - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql:Z
- Always use the current directory (./) for bind mounts, relative to the compose file location
- Create any required bind mount directories if they don't already exist
- Only declare named volumes in the top-level volumes: section if they are actually used by services
- Do not mix bind mounts (./path) with named volume declarations for the same data

Compatibility

- The :Z suffix works on all platforms (Linux, macOS, Windows) - it is ignored where SELinux is not present

Based on the source/sink components in the flow, generate `docker-compose.yaml`:

```yaml
# Docker Compose for [flow-name] development environment
# Generated by camel-kit
#
# Start with: docker compose up -d
# Stop with: docker compose down

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
      - postgres_data:/var/lib/postgresql/data:Z

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

### 6.3 Run Script

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

## Step 7: Summary and Next Steps

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
     docker compose up -d

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
     docker compose down

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

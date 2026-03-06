# Quality Checks Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being validated
> - `CAMEL_VERSION` — from `.camel-kit/config.yaml`

## Stage 4: Completeness Checks

Verify all required elements are present:

| Check | Pass Criteria |
|-------|---------------|
| Route ID | Route has `id:` property |
| Source defined | Route has `from:` section |
| Sink defined | Route has `to:` or ends with producer |
| Error handling | Route declares error handling strategy |
| Description | Route has meaningful description |

Show results:

```
== COMPLETENESS CHECKS ==

✅ {flow-name}: Route ID present
✅ {flow-name}: Source defined (kafka:{{kafka.topic.input}})
✅ {flow-name}: Sink defined (sql:INSERT INTO...)
✅ {flow-name}: Error handling defined (deadLetterChannel)
✅ {flow-name}: Description present
```

---

## Stage 5: Correctness Checks

Validate component usage and configuration:

### 5.1 Component Catalog Validation

For each component used, verify:
- Component exists in Camel catalog for version
- Required parameters provided
- Parameter types correct
- Component used correctly (consumer vs producer)

```
== COMPONENT VALIDATION ==

Kafka Component:
  ✅ Valid component (Camel {{VERSION}})
  ✅ Used as consumer: kafka:{{kafka.topic.input}}
  ✅ Required parameters: topic (provided via placeholder)
  ✅ Component-level config: camel.component.kafka.brokers (defined)

SQL Component:
  ✅ Valid component (Camel {{VERSION}})
  ✅ Used as producer: sql:INSERT INTO...
  ✅ Required parameters: query (provided inline)
  ✅ Component-level config: camel.component.sql.dataSource (defined)
```

### 5.2 Red Hat Support Check (optional, if camel-knowledge MCP is available)

For each component, call `camel_rh_build_component_info` to check Red Hat support status. If the tool call fails (tool not found, network error), skip this section silently.

```
== RED HAT SUPPORT CHECK ==

Kafka Component:
  ℹ️ Red Hat supported (4.14)

SQL Component:
  ℹ️ Red Hat supported (4.14)

Custom Component:
  ⚠️ Not found in Red Hat Build of Apache Camel docs (may still work, not officially supported)
```

This is informational only — do NOT fail validation based on Red Hat support status.

### 5.3 Expression Validation

Validate expressions (Simple, JSONPath, etc.):

```
✅ Simple expressions: Syntax valid
✅ JSONPath expressions: Syntax valid
✅ Expression variables: All referenced properties defined
```

---

## Stage 6: Constitution Checks

Validate against constitution rules from `docs/constitution.md`:

### 6.1 Standard Constitution Gates

| Gate | Check |
|------|-------|
| Route Structure | Route ID follows pattern, single responsibility |
| Configuration | All connections externalized to application.properties |
| Error Handling | Every route has error strategy |
| Security | No hardcoded secrets |
| Naming | Route ID follows `domain-action` convention |
| Clean Routes | No connection details in YAML |

Show results:

```
== CONSTITUTION COMPLIANCE ==

✅ Route Structure: Single responsibility
✅ Route Naming: Follows 'domain-action' pattern
✅ External Configuration: No hardcoded connections
✅ Error Handling: Dead Letter Channel configured
✅ Security: No hardcoded secrets found
✅ Clean Routes: All configuration externalized
```

### 6.2 Custom Constitution Rules

If constitution.md defines custom rules, validate those:

```
✅ Custom Rule: [rule name] - [result]
```

---

## Stage 7: Configuration Validation

Validate `application.properties`:

### 7.1 Property Format

```
== CONFIGURATION VALIDATION ==

application.properties:

✅ Component config: Uses camel.component.<name>.<property> pattern
✅ Bean definitions: Uses #class: prefix correctly
✅ Property placeholders: All {{placeholders}} defined
✅ No duplicates: No duplicate property keys
```

### 7.2 Property Completeness

Verify all placeholders used in routes are defined:

```
Checking property placeholders...

Route uses:
  - {{kafka.topic.input}}
  - {{kafka.topic.dlq}}
  - {{sql.insert}}

Properties file defines:
  ✅ kafka.topic.input=orders
  ✅ kafka.topic.dlq=orders-dlq
  ✅ sql.insert=INSERT INTO...

All placeholders resolved: ✅
```

### 7.3 Bean Definitions

Validate bean definitions:

```
Bean Definitions:

✅ dataSource: #class:org.apache.commons.dbcp2.BasicDataSource
   Properties:
     ✅ driverClassName=org.postgresql.Driver
     ✅ url=jdbc:postgresql://...
     ✅ username=postgres
     ✅ password=postgres
```

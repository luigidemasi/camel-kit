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
| Description | Route has `description:` with at least 10 characters describing the flow's business purpose |

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

Validate all expressions used in the route:

| Language | Validation Rules |
|----------|-----------------|
| Simple | `${header.X}` / `${body}` / `${exchangeProperty.X}` — verify referenced headers/properties exist in the flow. No undefined variable references. |
| JSONPath | Must start with `$` or `$.`. Verify valid JSONPath syntax (matched brackets, valid operators). |
| XPath | Must be well-formed XPath 1.0/2.0. Verify namespace prefixes are declared if used. |
| Constant | Literal values only. No expression syntax inside `constant` blocks. |
| JQ | Must start with `.` — verify valid JQ filter syntax. |

```
✅ Simple expressions: Syntax valid
✅ JSONPath expressions: Syntax valid
✅ Expression variables: All referenced properties defined
```

---

## Stage 6: Constitution Checks

Validate against constitution rules from `docs/constitution.md`:

### 6.1 Standard Constitution Gates

| Gate | Check | Formal Criteria |
|------|-------|-----------------|
| Route Structure | Single responsibility | Route has exactly ONE `from:` and its processing steps serve ONE business purpose. Fail if route contains multiple unrelated `from:` consumers or mixes unrelated business logic (e.g., order processing AND user notification in the same route). Multiple `to:` endpoints serving the same flow are acceptable. |
| Configuration | Externalized connections | No hostname, port, IP address, database URL, or queue name literals in the route YAML. All must use `{{placeholder}}` syntax referencing `application.properties`. |
| Error Handling | Error strategy present | Route declares `onException`, `errorHandler`, or `deadLetterChannel`. Global error handlers in the same file count. |
| Security | No hardcoded secrets | No string values matching: passwords, API keys, tokens, or credentials. Detect patterns: `password=`, `apiKey=`, `secret=`, `token=`, Base64-encoded strings > 20 chars, `jdbc:` URLs with inline credentials (e.g., `user:pass@host`). All must use `{{placeholder}}` syntax. |
| Naming | Route ID convention | Route ID matches pattern `{domain}-{action}` using lowercase kebab-case. Valid: `order-process`, `user-notify`, `inventory-sync`. Invalid: `route1`, `myRoute`, `OrderProcess`, `process_orders`. Regex: `^[a-z][a-z0-9]*(-[a-z][a-z0-9]*)+$` |
| Clean Routes | No connection details | No inline connection strings, broker URLs, database endpoints, or file paths in route YAML. Everything via `{{placeholder}}`. |

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

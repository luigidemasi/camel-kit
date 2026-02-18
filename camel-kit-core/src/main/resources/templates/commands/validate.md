# /camel.validate

You are validating the user's Camel routes and specifications. Follow these steps exactly.

The user runs: `/camel.validate` or `/camel.validate <flow-name>`

---

## Step 1: Load Files

Read these files:
- `.camel-kit/config.yaml` - for Camel version
- `.camel-kit/constitution.md` - for rules to enforce
- `.camel-kit/flows/*/flow.md` - all flow specifications
- `.camel-kit/.cache/components-*.json` - component catalog
- `.camel-kit/.cache/kamelets-*.json` - Kamelet catalog
- `*.camel.yaml` - generated route files
- `application.properties` - configuration file

If validating a specific flow, only load that flow's files.

---

## Step 2: YAML Schema Validation

**CRITICAL**: Validate generated YAML files against the Camel YAML DSL schema and auto-fix errors.

### 2.1 Load Schema

Load the Camel YAML DSL schema from the local cache:

```
Schema file: .camel-kit/.cache/camelYamlDsl-{{CAMEL_VERSION}}.json
```

Replace `{{CAMEL_VERSION}}` with the version from `.camel-kit/config.yaml` (e.g., `4.10.0`).

If the schema file is not cached, fetch it from GitHub:
```
Schema URL: https://raw.githubusercontent.com/apache/camel/camel-{{CAMEL_VERSION}}/dsl/camel-yaml-dsl/camel-yaml-dsl/src/generated/resources/schema/camelYamlDsl.json
```

Read the schema to understand:
- Valid property names (watch for camelCase vs lowercase)
- Required fields
- Allowed values and types
- Expression formats

### 2.2 Validate Each YAML File

For each `*.camel.yaml` file:

1. **Syntax validation**: Parse YAML and check for syntax errors
2. **Schema validation**: Validate against `camelYamlDsl.json` schema
3. **Property types**: Check values match expected types
4. **Expression formats**: Verify expressions use correct structure
5. **Property placeholders**: Check that all `{{property}}` references exist in `application.properties`

### 2.3 Common Schema Errors and Fixes

| Error | Wrong | Correct |
|-------|-------|---------|
| `handled` requires expression | `handled: true` | `handled: { constant: { expression: "true" } }` |
| `continued` requires expression | `continued: true` | `continued: { constant: { expression: "true" } }` |
| Wrong property case | `datasource:` | `dataSource:` |
| Missing uri wrapper | `to: kafka:topic` | `to: { uri: "kafka:topic" }` |
| Wrong exception format | `exception: MyEx` | `exception: [ "MyEx" ]` |
| Invalid redeliveryPolicy | nested wrong | check schema for exact structure |

### 2.4 Auto-Fix Validation Errors

**IMPORTANT: If validation errors are found, automatically fix them and re-validate.**

```
== YAML SCHEMA VALIDATION ==

Fetching schema for Camel {{CAMEL_VERSION}}...
Validating order-processing.camel.yaml...

❌ Error 1: Property 'handled' at line 25
   Expected: object (expression)
   Found: boolean
   → AUTO-FIX: Converting 'handled: true' to expression format

❌ Error 2: Unknown property 'datasource' at line 42
   Did you mean: 'dataSource'?
   → AUTO-FIX: Renaming to 'dataSource'

Applying fixes to order-processing.camel.yaml...
Re-validating...

✅ order-processing.camel.yaml: Valid YAML syntax
✅ order-processing.camel.yaml: Schema validation passed (2 errors fixed)
✅ order-processing.camel.yaml: All properties resolved
```

### 2.5 Fix Loop

Repeat validation until:
- All errors are fixed, OR
- An error cannot be auto-fixed (requires user input)

For errors that cannot be auto-fixed, provide clear instructions:

```
❌ Error: Cannot auto-fix
   Property 'customProcessor' references bean not defined

   Manual fix required:
   Add bean definition to application.properties:
   camel.beans.customProcessor=#class:com.example.MyProcessor
```

---

## Step 3: Run Camel Validation

Use the Camel CLI to validate the route:

```bash
camel run --check <flow-name>.camel.yaml application.properties
```

This will:
- Parse the YAML
- Resolve property placeholders
- Validate component URIs
- Check for missing dependencies

Report any errors from `camel run --check`.

---

## Step 4: Completeness Checks

For each flow, check the following:

| Check | Pass if |
|-------|---------|
| Source defined | Route has a `from:` section |
| Sink defined | Route has `to:` or ends with producer |
| Error handling | Route declares `errorHandler:` |
| Route ID | Route has `id:` property |

---

## Step 5: Correctness Checks

| Check | Pass if |
|-------|---------|
| Valid component | Component name exists in catalog |
| Valid Kamelet | Kamelet name exists in catalog |
| Required options | All required parameters are provided |
| Expression syntax | Simple/JSONPath expressions are valid |

---

## Step 6: Constitution Checks

| Check | Pass if |
|-------|---------|
| Naming convention | Route ID follows `domain-action` pattern |
| Clean routes | No connection details in YAML (use application.properties) |
| Error handling | Every route has error strategy |
| Secrets | No hardcoded passwords/keys |

---

## Step 7: Configuration Checks

Validate `application.properties`:

| Check | Pass if |
|-------|---------|
| Component config | Uses `camel.component.<name>.<prop>` pattern |
| Bean definitions | Uses `#class:` prefix for bean instantiation |
| Property references | All `{{placeholder}}` values are defined |

---

## Step 8: Show Results

Present results in this format:

```
🔍 Validating camel-kit specifications...

== YAML SCHEMA VALIDATION ==
✅ order-processing.camel.yaml: Valid YAML syntax
✅ order-processing.camel.yaml: Schema validation passed
✅ order-processing.camel.yaml: All properties resolved

== CAMEL VALIDATION ==
✅ camel run --check: Route compiles successfully
✅ Components: kafka, sql - valid
✅ Properties: All placeholders resolved

== COMPLETENESS ==
✅ order-processing: source defined (kafka)
✅ order-processing: sink defined (sql)
✅ order-processing: error handling defined (deadLetterChannel)
✅ order-processing: route ID present

== CORRECTNESS ==
✅ kafka component valid (Camel {{CAMEL_VERSION}})
✅ sql component valid
✅ All required options provided

== CONSTITUTION ==
✅ Route naming follows convention
✅ Clean route (no connection details in YAML)
✅ No hardcoded secrets

== CONFIGURATION ==
✅ application.properties: Component-level config used
✅ application.properties: Bean definitions with #class: prefix
✅ application.properties: All properties defined
```

---

## Step 9: Handle Failures

If any checks fail, show:

```
== YAML SCHEMA VALIDATION ==
❌ order-processing.camel.yaml: Schema validation failed
   Line 15: Unknown property 'brokers' on kafka endpoint
   Hint: Use camel.component.kafka.brokers in application.properties instead
```

At the end, summarize:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
❌ VALIDATION FAILED - 2 errors, 1 warning
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Errors (must fix):
1. order-processing.camel.yaml: Schema validation error
   Line 15: Unknown property 'brokers'
   → Move to application.properties: camel.component.kafka.brokers=localhost:9092

2. Missing property: kafka.topic.orders
   → Add to application.properties: kafka.topic.orders=orders

Warnings (recommended):
1. Consider adding circuit breaker for external calls

Run /camel.validate again after fixes.
```

---

## Step 10: Handle Success

If all checks pass:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ VALIDATION PASSED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

All routes are valid and ready to run.

Next steps:
  1. Start external services: docker compose up -d
  2. Run the integration: ./run.sh
  3. Generate tests: /camel.test <flow-name>
```

---

## Step 11: Save Report

Save a validation report to `.camel-kit/validation-report.md`:

```markdown
# Validation Report

Generated: [timestamp]
Camel Version: {{CAMEL_VERSION}}

## Summary

| Status | Count |
|--------|-------|
| ✅ Passed | 15 |
| ❌ Errors | 0 |
| ⚠️ Warnings | 1 |

## YAML Schema Validation

| File | Status | Details |
|------|--------|---------|
| order-processing.camel.yaml | ✅ | Valid |

## Routes

### order-processing

| Check | Status | Details |
|-------|--------|---------|
| YAML Schema | ✅ | Valid against camelYamlDsl.json |
| Camel Check | ✅ | camel run --check passed |
| Source | ✅ | kafka:{{kafka.topic.orders}} |
| Sink | ✅ | sql:INSERT INTO... |
| Error Handling | ✅ | deadLetterChannel |
| Constitution | ✅ | All rules pass |

## Configuration

### application.properties

| Check | Status |
|-------|--------|
| Component config | ✅ camel.component.kafka.brokers |
| Bean definitions | ✅ camel.beans.dataSource=#class:... |
| Properties | ✅ All placeholders defined |
```

Confirm:

```
Report saved: .camel-kit/validation-report.md
```

---

## Quick Validation Command

For quick validation without full report, suggest:

```bash
# Quick syntax check
camel run --check order-processing.camel.yaml application.properties

# Or validate all YAML files
for f in *.camel.yaml; do camel run --check "$f" application.properties; done
```

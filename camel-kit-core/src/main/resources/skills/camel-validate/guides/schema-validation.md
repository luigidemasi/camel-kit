# Schema Validation Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being validated
> - `CAMEL_VERSION` — from `.camel-kit/config.properties`

## Stage 1: YAML Schema Validation

### 1.1 Load Schema

Load the Camel YAML DSL schema:

```
Schema file: .camel-kit/.cache/camelYamlDsl-{{CAMEL_VERSION}}.json
```

Replace `{{CAMEL_VERSION}}` with version from `.camel-kit/config.properties`.

If schema not cached, fetch from GitHub:
```
URL: https://raw.githubusercontent.com/apache/camel/camel-{{CAMEL_VERSION}}/dsl/camel-yaml-dsl/camel-yaml-dsl/src/generated/resources/schema/camelYamlDsl.json
```

### 1.2 Validate YAML Files

For each `*.camel.yaml` file (or specified flow):

```
Running YAML Schema Validation...

./mvnw org.apache.camel:camel-yaml-dsl-validator:{{CAMEL_VERSION}}:validate \
  -Dcamel.validator.files={flow-name}.camel.yaml
```

Parse output:
- `BUILD SUCCESS` → Schema valid → Continue to Stage 2
- `BUILD FAILURE` → Schema errors → Auto-fix and retry

### 1.3 Auto-Fix Common Schema Errors

If validation errors found, attempt auto-fix:

| Error Pattern | Auto-Fix |
|--------------|----------|
| `handled: true` (boolean) | Convert to `{ constant: { expression: "true" } }` |
| `continued: true` (boolean) | Convert to `{ constant: { expression: "true" } }` |
| `datasource:` (wrong case) | Rename to `dataSource:` |
| Missing `uri:` wrapper | Wrap in `{ uri: "..." }` |
| Wrong exception format | Convert to array `[ "..." ]` |

Show auto-fix report:

```
== YAML SCHEMA VALIDATION ==

Validating {flow-name}.camel.yaml...

❌ Error 1: Property 'handled' at line 25
   Expected: object (expression)
   Found: boolean
   → AUTO-FIX: Converting 'handled: true' to expression format

❌ Error 2: Unknown property 'datasource' at line 42
   Did you mean: 'dataSource'?
   → AUTO-FIX: Renaming to 'dataSource'

Applying fixes to {flow-name}.camel.yaml...
Re-validating...

✅ {flow-name}.camel.yaml: Valid YAML syntax
✅ {flow-name}.camel.yaml: Schema validation passed (2 errors fixed)
```

### 1.4 Manual Fix Required

If error cannot be auto-fixed:

```
❌ Error: Cannot auto-fix

Property 'customProcessor' references bean not defined

Manual fix required:
  Add bean definition to application.properties:
  camel.beans.customProcessor=#class:com.example.MyProcessor
```

# Schema Validation Guide

> **Context variables provided by master SKILL.md:**
> - `CAMEL_VERSION` — from `.camel-kit/config.properties`
> - `ROUTE_FILES` — exact runtime/module-aware relative route paths from the validation inventory

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

For each exact path in `ROUTE_FILES`:

```
Running YAML Schema Validation...

./mvnw org.apache.camel:camel-yaml-dsl-validator:{{CAMEL_VERSION}}:validate \
  -Dcamel.validator.files={ROUTE_FILE}
```

Parse output:
- `BUILD SUCCESS` → Schema valid → Continue to Stage 2
- `BUILD FAILURE` → Record schema errors and recommended corrections in the validation report, then continue without modifying the route

### 1.3 Report Common Schema Corrections

If validation errors are found, report the applicable correction without applying it:

| Error Pattern | Recommended Correction |
|--------------|------------------------|
| `handled: true` (boolean) | Convert to `{ constant: { expression: "true" } }` |
| `continued: true` (boolean) | Convert to `{ constant: { expression: "true" } }` |
| `datasource:` (wrong case) | Rename to `dataSource:` |
| Missing `uri:` wrapper | Wrap in `{ uri: "..." }` |
| Wrong exception format | Convert to array `[ "..." ]` |

Show the finding in the validation report:

```
== YAML SCHEMA VALIDATION ==

Validating {ROUTE_FILE}...

❌ Error 1: Property 'handled' at line 25
   Expected: object (expression)
   Found: boolean
   → RECOMMENDATION: Convert 'handled: true' to expression format

❌ Error 2: Unknown property 'datasource' at line 42
   Did you mean: 'dataSource'?
   → RECOMMENDATION: Rename to 'dataSource'

Result: FAIL (2 schema errors reported; no route files modified)
```

### 1.4 Additional Correction Required

If no common correction applies, record the error and a targeted recommendation:

```
❌ Error: Correction requires implementation work

Property 'customProcessor' references bean not defined

Recommended implementation correction:
  First check the Configuration Ladder (`skills/shared/forage.md`): if a Forage factory covers the bean, recommend `forage.X.<domain>.*` keys rather than `camel.beans.*`.
  Otherwise recommend adding the justified bean definition to application.properties:
  camel.beans.customProcessor=#class:com.example.MyProcessor
```

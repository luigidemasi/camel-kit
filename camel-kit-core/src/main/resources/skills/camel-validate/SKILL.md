---
name: camel-validate
description: Validate routes when user wants to check YAML syntax, verify security compliance, analyze route quality, find issues, perform security hardening, or ensure best practices
user-invocable: true
metadata:
  version: "1.0.0"
  author: "camel-kit"
  category: "integration"
  license: "Apache-2.0"
---

# Camel Validate - Route Validation and Quality Assurance

You are acting as a **Quality Assurance Engineer** validating Camel integrations against technical standards and best practices. Systematically validate all aspects of the implementation, auto-fix common errors, and ensure readiness for production deployment.

## Parameters

This skill can validate a specific flow or all flows:

```
/camel-validate <flow-name>   # Validate specific flow
/camel-validate all           # Validate all flows
/camel-validate --all         # Same as above
/camel-validate               # Same as above (no argument)
```

Example: `/camel-validate order-to-warehouse`

### Batch Mode (`all`)

When `all`, `--all`, or no argument is specified:

1. **Discover flows:** List all directories under `docs/flows/` that contain a `{flow-name}.tdd.md` file, and verify corresponding route YAML exists
2. **Show plan and proceed immediately** (the user already confirmed by passing `--all`):
   ```
   Found [N] flows to validate:
     1. flow-name-1  ({flow-name-1}.camel.yaml)
     2. flow-name-2  ({flow-name-2}.camel.yaml)
     ...

   Validating all [N] flows sequentially...
   ```
3. **Process sequentially:** For each flow, run the full validation pipeline (Stages 1–8). Between flows, report progress:
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ✅ [current]/[total] — {flow-name} PASSED
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ```
   **If a flow fails validation:**
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ❌ [current]/[total] — {flow-name} FAILED
      Errors: [count] errors, [count] warnings
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ```
   **Continue to the next flow.** Do NOT stop the batch on failure.

4. **Final summary:** After all flows, show combined summary:
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   BATCH VALIDATION COMPLETE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

   Results: [passed]/[total] flows validated
     ✅ flow-name-1
     ❌ flow-name-2 — [error summary]
     ✅ flow-name-3
     ...

   [If any failed:]
   Failed flows need fixes:
     - flow-name-2: [error details]

   Next steps:
     /camel-test --all    # Generate tests for all passing flows
   ```

If no route YAML files are found: ERROR "No Camel routes found. Run /camel-implement first."

---

## Context Loading

**ALWAYS read at the start:**
1. `docs/business-requirements.md` - Business context (if exists)
2. `docs/constitution.md` - Best practices and quality gates. If missing, copy from `templates/constitution.md` and continue.
3. `.camel-kit/config.yaml` - Camel version (if exists)
4. `docs/flows/{flow-name}/{flow-name}.tdd.md` - Technical specification (for specific flow)
5. Route YAML files — read from project root (JBang) or `src/main/resources/camel/` (Spring Boot/Quarkus) based on `project.runtime` in `.camel-kit/config.yaml`

**For validation:**
- `.camel-kit/.cache/camelYamlDsl-{{CAMEL_VERSION}}.json` - YAML DSL schema
- Component skills as needed for component-specific validation

**Anti-Pattern Guide (conditional):**
- Load `guides/anti-patterns.md` ONLY if:
  - User explicitly requests comprehensive validation (e.g., `--comprehensive` flag or "check for anti-patterns")
  - All 8 validation stages pass without errors — load as an optional enhancement step

---

## MCP Server Configuration (Recommended)

→ **For MCP setup, version mapping, and fallback policy:** see `skills/shared/mcp-setup.md`

The Camel MCP server provides validation and security analysis tools for this skill:
- **URI Validation** (`camel_validate_route`) - Validate endpoint URIs against catalog (catches typos)
- **Security Analysis** (`camel_route_harden_context`) - 47 built-in security checks for hardcoded credentials, insecure protocols, etc.
- **Route Understanding** (`camel_route_context`) - Extract and document components from routes

The camel-knowledge MCP server provides Red Hat Build of Apache Camel documentation:
- **Red Hat Component Info** (`camel_rh_build_component_info`) - Check if a component is supported by Red Hat
- **Red Hat Docs Search** (`camel_rh_build_search`) - Search Red Hat Build of Apache Camel docs

---

## Context Variable Resolution

Before dispatching to guides, resolve these variables:

| Variable | Source | Passed to |
|----------|--------|-----------|
| `FLOW_NAME` | From parameter | All guides |
| `CAMEL_VERSION` | From `.camel-kit/config.yaml` (`project.camelVersion`) | All guides |
| `RUNTIME` | From `.camel-kit/config.yaml` (`project.runtime`, default: `jbang`) | `endpoint-validation.md` (route file location) |

All guides receive these variables and declare them in a header block.

---

## Validation Process

The validation proceeds through multiple stages:

1. **YAML Schema Validation** - Validate against Camel YAML DSL schema
2. **Endpoint URI Validation** - Validate URIs against catalog (MCP or manual)
3. **Camel Runtime Validation** - Use `camel run --check`
4. **Completeness Checks** - Verify all required elements present
5. **Correctness Checks** - Validate component usage and configuration
6. **Constitution Checks** - Verify compliance with best practices
7. **Configuration Checks** - Validate application.properties
8. **Security Analysis** - MCP 47-check security scan or manual anti-patterns

---

## Execute Validation Pipeline

Execute these guides in order.

> **"Load" means READ and FOLLOW.** When this document says "Load `guides/xyz.md`", you MUST read that file from the `guides/` subdirectory next to this SKILL.md and execute its instructions. The guide files are always present — do NOT report them as missing.

1. **→ Load `guides/schema-validation.md`** — Stage 1: YAML Schema Validation + auto-fix
2. **→ Load `guides/endpoint-validation.md`** — Stages 2-3: Endpoint URI + Runtime Validation
3. **→ Load `guides/quality-checks.md`** — Stages 4-7: Completeness, Correctness, Constitution (7 rules from `docs/constitution.md`), Configuration
4. **→ Load `guides/security-analysis.md`** — Stage 8: MCP Security Analysis (47 checks)
5. **→ Load `guides/anti-patterns.md`** — ONLY if user requests comprehensive validation or all stages pass and you want to provide additional recommendations

---

## Validation Report

### Success Report

If all checks pass:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ VALIDATION PASSED: {flow-name}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Summary:
  Routes validated: 1
  YAML schema: ✅ PASSED
  Endpoint URIs: ✅ PASSED (MCP validated)
  Camel runtime: ✅ PASSED
  Completeness: ✅ PASSED (5/5 checks)
  Correctness: ✅ PASSED (all components valid)
  Constitution: ✅ PASSED (all gates)
  Configuration: ✅ PASSED
  Security: ✅ PASSED (47/47 checks - MCP)

The integration is ready for testing.

Next steps:
  /camel-test {flow-name}    # Generate integration tests

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Failure Report

If checks fail:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
❌ VALIDATION FAILED: {flow-name}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Summary:
  Routes validated: 1
  YAML schema: ❌ FAILED (2 errors)
  Endpoint URIs: ⚠️ WARNINGS (1 issue)
  Camel runtime: ✅ PASSED
  Completeness: ⚠️ WARNING (4/5 checks)
  Correctness: ✅ PASSED
  Constitution: ❌ FAILED (1 gate)
  Configuration: ❌ FAILED (1 error)
  Security: ⚠️ WARNINGS (3 issues - MCP)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Errors (must fix):

1. YAML Schema Error in {flow-name}.camel.yaml line 15:
   Unknown property 'brokers' on kafka endpoint

   Fix: Move to application.properties:
   camel.component.kafka.brokers=localhost:9092

2. Constitution Error:
   Hardcoded connection string found at line 42

   Fix: Extract to application.properties:
   database.url=jdbc:postgresql://...

3. Configuration Error:
   Missing property placeholder: kafka.topic.orders

   Fix: Add to application.properties:
   kafka.topic.orders=orders

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Warnings (MCP Security - recommended):

1. HTTP instead of HTTPS at line 42
   Risk: Unencrypted communication
   Fix: Change to https://{{api.endpoint}}

2. No authentication on HTTP endpoint
   Fix: Add OAuth2 or API key authentication

3. Logging full body may expose PII at line 28
   Fix: Log only specific safe fields

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Fix the errors above and run /camel-validate again.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Validation Report File

Save detailed report to `.camel-kit/validation-report.md`:

Include all validation results with:
- Timestamp and Camel version
- Summary table
- Detailed results for each stage
- MCP security analysis results (if available)
- Recommendations

Confirm:
```
✅ Validation report saved to .camel-kit/validation-report.md
```

---

## Error Handling

### No Routes Found

```
❌ ERROR: No Camel routes found

Looking for: *.camel.yaml

Have you run /camel-implement yet?
```

### MCP Tool Call Failed

```
ℹ️ INFO: MCP tool call failed

Falling back to:
- Manual URI validation
- Standard anti-pattern checks

To enable MCP (recommended):
Add to .mcp.json:
{
  "mcpServers": {
    "camel": {
      "command": "jbang",
      "args": ["--repos", "redhat=https://maven.repository.redhat.com/ga/", "-Dquarkus.log.level=WARN", "org.apache.camel:camel-jbang-mcp:LATEST:runner"]
    }
  }
}

Benefits:
- 47 automated security checks
- Real-time catalog validation
- Typo detection in endpoint URIs
```

### Schema Not Cached

```
⚠️ WARNING: Schema not cached locally

Fetching from GitHub:
https://raw.githubusercontent.com/apache/camel/camel-{{CAMEL_VERSION}}/...

[Download progress]

✅ Schema cached to .camel-kit/.cache/camelYamlDsl-{{CAMEL_VERSION}}.json
```

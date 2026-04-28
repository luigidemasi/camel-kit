---
name: camel-validate
description: Use when you want to validate generated routes against schema, constitution rules, security best practices, and detect anti-patterns — validation only, no modifications
---

# Camel Validate — Validation Pipeline (Bob)

Validate generated Apache Camel routes across multiple quality dimensions. This is a READ-ONLY skill — report findings without modifying files.

Follow every step in order. Do NOT skip steps.

**Core principle:** Validation is separate from implementation. Find issues, report them, but don't fix them during validation.

## Guide Locations

All validation guides are in `.bob/skills/camel-validate/guides/`. When this file says `guides/X.md`, read `.bob/skills/camel-validate/guides/X.md`. Do NOT explore or list directories to find guides.

<Steps>
<Step>
## Switch to Validate Mode

Switch to **camel-validate** mode using the mode selector or `/camel-validate` command.
This restricts tools to read-only operations — preventing accidental modifications during validation.
</Step>

<Step>
## Load Validation Context

Read these files:
1. `docs/constitution.md` — constitution rules (all 7 rules)
2. `.camel-kit/config.properties` — Camel version, runtime, platform BOM
3. `.camel-kit/project-graph.json` — project norms and conventions (if exists)
4. `docs/design-spec.md` — approved design spec (if exists)
5. `docs/implementation-plan.md` — approved plan (if exists)

Load validation guides:
- `guides/schema-validation.md` — YAML DSL schema validation rules
- `guides/endpoint-validation.md` — endpoint URI validation via MCP
- `guides/quality-checks.md` — quality metrics and thresholds
- `guides/security-analysis.md` — security checks catalog
- `guides/anti-patterns.md` — anti-pattern detection catalog
</Step>

<Step>
## Discover All Routes

Find all YAML route files:
```bash
find src/main/resources/camel -name "*.camel.yaml"
```

List all discovered routes. If none found, report and stop.
</Step>

<Step>
## Schema Validation

For EACH route file:

Load `guides/schema-validation.md`.

Check:
- Valid YAML syntax
- Required fields present (`- route:`, `from:`, `steps:`)
- Component URI structure matches pattern: `\{scheme\}:\{path\}?\{options\}`
- EIP structure matches catalog schema
- No unknown properties

Report any schema violations per route.
</Step>

<Step>
## Endpoint Validation

For EACH route file:

Load `guides/endpoint-validation.md`.

For EVERY endpoint URI:
1. Extract scheme (component name)
2. Call `camel_catalog_component(name="\{scheme\}", runtime="...", platformBom="...")`
3. Parse URI options
4. Validate each option against catalog schema
5. Check for typos in option names
6. Check for deprecated options

Report any endpoint validation issues per route.
</Step>

<Step>
## Constitution Compliance Check

For EACH route file:

Check all 7 constitution rules:

**Rule 1: No Hardcoded URLs**
- Scan for `http://`, `https://`, `jdbc:`, `amqp://` in URIs
- All URLs must use `{{property}}` syntax
- Report violations with line numbers

**Rule 2: Explicit Error Handling**
- Every route must have `onException:` or `doTry:`
- Error handlers must log exceptions
- Report routes without error handling

**Rule 3: Structured Logging**
- Routes must log at entry with correlation ID
- Routes must log at exit
- Use `log:` EIP with structured format
- Report routes without logging

**Rule 4: Idempotency**
- Routes with `file:`, `ftp:`, `sftp:`, `kafka:` consumers must use `idempotentConsumer:`
- Report stateful routes without idempotency

**Rule 5: Circuit Breaker**
- Routes with `http:`, `https:` calls must use `circuitBreaker:` or `resilience4j:`
- Report HTTP calls without resilience

**Rule 6: TLS Everywhere**
- All HTTP endpoints must use HTTPS (except localhost)
- All AMQP endpoints must use TLS
- Report insecure endpoints

**Rule 7: Component Verification**
- Call `camel_catalog_component` for every component
- Report unrecognized components

Report constitution violations per route.
</Step>

<Step>
## Security Analysis

Load `guides/security-analysis.md`.

Check for:
- Hardcoded credentials in properties or URIs
- Missing authentication on HTTP endpoints
- Missing authorization checks
- Insecure TLS configurations (e.g., `sslContextParameters` with weak ciphers)
- Sensitive data in logs
- Missing input validation

Report security issues per route.
</Step>

<Step>
## Anti-Pattern Detection

Load `guides/anti-patterns.md`.

Check for:
- **Polling too frequently** — `file:` consumer with `delay` < 1000ms
- **Missing maxMessagesPerPoll** — `file:`, `ftp:` without max limit
- **Synchronous HTTP in loops** — `split:` → `to: http:` without async
- **Large message loading** — missing streaming for file/HTTP body
- **No timeout on HTTP calls** — `http:` without `connectTimeout` / `socketTimeout`
- **Missing correlation ID** — routes without `exchangeProperty.correlationId`
- **Logging full body** — `log: $\{body\}` on large messages
- **No dead letter channel** — routes without DLC for retries

Report anti-patterns per route.
</Step>

<Step>
## Quality Metrics

Load `guides/quality-checks.md`.

For each route, calculate:
- Route complexity (steps count)
- Nesting depth (max depth of nested EIPs)
- Error handling coverage (% routes with error handling)
- Logging coverage (% routes with entry/exit logs)
- Property usage (% endpoints using properties vs hardcoded)

Compare against thresholds:
- Route complexity: warn if > 20 steps
- Nesting depth: warn if > 4 levels
- Error handling coverage: fail if < 100%
- Logging coverage: warn if < 100%
- Property usage: fail if < 100%

Report metrics and threshold violations.
</Step>

<Step>
## Graph-Based Validation

If `.camel-kit/project-graph.json` exists:

Load `guides/graph-project-context.md`.

Run graph-based checks:
1. **Project norms check:**
   ```bash
   {COMMAND_PREFIX} graph project-norms
   ```
   This checks for naming convention violations, inconsistent patterns, etc.

2. **Dead code detection:**
   ```bash
   {COMMAND_PREFIX} graph dead-code
   ```
   This finds unused routes, unreachable endpoints, orphaned properties.

Report graph analysis findings.
</Step>

<Step>
## Generate Validation Report

Assemble all findings into a validation report at `docs/validation-report.md`:

```markdown
# Validation Report

**Date:** [current date]
**Camel Version:** [from config.properties]
**Routes Validated:** [N]

---

## Summary

| Category | Status | Issues |
|----------|--------|--------|
| Schema Validation | PASS/FAIL | N |
| Endpoint Validation | PASS/FAIL | N |
| Constitution Compliance | PASS/FAIL | N |
| Security Analysis | PASS/FAIL | N |
| Anti-Patterns | PASS/FAIL | N |
| Quality Metrics | PASS/FAIL | N |
| Graph Analysis | PASS/FAIL | N |

---

## Findings by Route

### Route: \{route-name\}

**File:** `src/main/resources/camel/\{route-name\}.camel.yaml`

#### Schema Validation
- [✓] Valid YAML syntax
- [✗] Missing required field: `steps`

#### Constitution Compliance
- [✗] Rule 1 violated: Hardcoded URL at line 15: `http://api.example.com`
- [✓] Rule 2: Error handling present
...

---

## Recommendations

1. **Critical:** Fix hardcoded URLs in 3 routes
2. **Important:** Add circuit breaker to HTTP calls in 2 routes
3. **Suggestion:** Reduce complexity of order-processing route (25 steps → split into 2 routes)
```

Save the report and present findings to the user.
</Step>

<Step>
## Present Findings

Summarize validation results:

```
Validation complete. Report: docs/validation-report.md

Routes Validated: N
Issues Found:
  Critical: N (constitution violations, security issues)
  Important: N (anti-patterns, quality threshold violations)
  Suggestions: N (quality improvements)

Constitution Compliance: PASS/FAIL
Security Analysis: PASS/FAIL
Overall Status: PASS/FAIL

[If FAIL] Recommended next step: Fix critical issues before proceeding.
[If PASS] All routes pass validation. Ready for testing.
```
</Step>
</Steps>

## Iron Laws

Validation enforces:
- **Iron Law 1**: MCP Catalog Verification — endpoint validation uses MCP catalog as source of truth
- **Iron Law 2**: Constitution Compliance — validation checks all 7 constitution rules

## MCP Tools Used

- `camel_catalog_component` — verify component exists, validate options
- `camel_catalog_eip` — verify EIP schema

For MCP setup: see `shared/mcp-setup.md`

## Important

This is a READ-ONLY skill. Do NOT:
- Modify route files
- Fix validation issues
- Generate new code
- Commit changes

Report findings only. The user or camel-implement skill will handle fixes.

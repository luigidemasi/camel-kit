# Code Quality Reviewer Criteria

> **Context:** Used by `camel-execute` to build the prompt for code-quality-reviewer subagents.
> **Purpose:** Defines the exact checks the quality reviewer performs.

---

## Overview

The code quality reviewer answers: **Is the implementation well-built?**

This is Stage 2 of the two-stage review. It runs ONLY AFTER spec compliance review passes (Iron Law 5). The implementation already matches the spec — this review checks quality.

---

## Reviewer Prompt Template

Build the reviewer prompt with these sections:

```
## Your Role

You are the Code Quality Reviewer. The implementation has already passed spec compliance
review — it matches the design spec. Your job is to verify it's well-built: constitution
compliance, security, anti-patterns, and YAML quality.

## Files to Review

[List the generated files with paths]

## Constitution Rules (ALL 7 MUST PASS)

For EACH generated route, check:

### Rule 1: Route Structure
- Has `from:` (source endpoint)
- Has terminal `to:` (sink endpoint)
- Exception: `direct:`/`seda:` sub-routes may omit external sink

### Rule 2: Single Responsibility
- One clear purpose, explainable in one sentence
- ≤7 processing steps (WARNING if exceeded)

### Rule 3: Separation of Concerns
- Follows Ingestion → Processing → Delivery pattern
- Business logic in beans, integration logic in routes
- Uses `direct:` for sync, `seda:` for async internal routing

### Rule 4: Naming Conventions
- Route ID: `<domain>-<action>[-<qualifier>]`
- Internal endpoints: `direct:<route-id>`, `seda:<domain>-<purpose>`
- Custom headers: `kebab-case` (not CamelCase)

### Rule 5: Observability
- Every route declares `routeId`
- Every route declares `description`
- Correlation IDs used for cross-route tracing

### Rule 6: External Configuration
- No hardcoded connection strings, credentials, or environment values
- All configurable values use `{{placeholder}}` syntax
- Configuration hierarchy documented in properties

### Rule 7: Component Support Verification
- Spot-check 2-3 components via `camel_rh_build_component_info`
- All should be "Production Support"
- Technology Preview or unsupported → flag as Important issue

## Security Checks

- No credentials in YAML or properties files
- No passwords, API keys, tokens, or secrets in plain text
- TLS/SSL configured for external connections
- Sensitive headers not logged or exposed
- No command injection vectors in expression languages

## Anti-Pattern Checks

- No empty routes (source → sink with no processing)
- No routes exceeding single responsibility (>7 steps)
- No direct-to-direct chains without purpose
- No sync/async mixing without design rationale
- No redundant type conversions
- No overly broad exception handlers

## YAML Quality

- Kaoto-compatible structure
- Consistent indentation
- No deprecated DSL constructs
- Proper `parameters:` blocks vs inline URI options

## MCP Spot-Check

- Verify 2-3 endpoint URIs via `camel_catalog_component`
- Confirm option names match catalog exactly
- Check for deprecated options

## Output Format

```
## Code Quality Review

### Constitution: PASS/FAIL
Rule 1 (Route Structure): PASS/FAIL — [evidence]
Rule 2 (Single Responsibility): PASS/FAIL — [evidence]
Rule 3 (Separation of Concerns): PASS/FAIL — [evidence]
Rule 4 (Naming): PASS/FAIL — [evidence]
Rule 5 (Observability): PASS/FAIL — [evidence]
Rule 6 (External Config): PASS/FAIL — [evidence]
Rule 7 (Component Support): PASS/FAIL — [evidence]

### Security: PASS/FAIL (N issues)
- [Critical/Important/Suggestion]: [description]

### Anti-Patterns: PASS/FAIL (N detected)
- [pattern]: [location and recommendation]

### YAML Quality: PASS/FAIL
- [issues if any]

### MCP Spot-Check: PASS/FAIL
- [components verified, issues found]

### Overall: PASS/FAIL
Critical: [N] | Important: [N] | Suggestion: [N]
[summary — Critical issues MUST be fixed before proceeding]
```
```

---

## Issue Categories

| Category | Blocks Completion? | Examples |
|----------|-------------------|----------|
| **Critical** | YES — must fix | Security vulnerabilities, constitution violations, broken routes, missing routeId |
| **Important** | NO — noted | Anti-patterns, missing description, deprecated options, naming inconsistencies |
| **Suggestion** | NO — noted | Formatting, additional error handling, performance hints |

---

## Failure Handling

If the quality reviewer reports Critical issues:

1. Extract specific Critical issues from the review
2. Send to implementer subagent with fix instructions
3. After fixes, re-dispatch the quality reviewer
4. Loop until no Critical issues remain
5. Important and Suggestion issues are reported but don't block

**Maximum iterations:** 3. If Critical issues persist after 3 rounds, escalate to the user.

---

## MCP Tools for Quality Review

The quality reviewer should use:
- `camel_catalog_component(name, runtime, platformBom)` — verify endpoint option names
- `camel_rh_build_component_info(component)` — verify Red Hat support status
- `camel_knowledge_search(query)` — check for known issues or CVEs

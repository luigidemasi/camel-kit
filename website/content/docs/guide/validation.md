---
title: Validation
weight: 4
---

Validation checks your routes for correctness, security, and compliance.

## Running Validation

```
/camel-validate           # Validate all flows
/camel-validate <flow-name>  # Validate specific flow
```

## MCP-Enhanced Validation

When the Camel MCP server is configured, validation adds three automated phases: route structure analysis, URI validation, and security analysis (47 checks covering credentials, encryption, authentication, input validation, data exposure, and compliance).

## What's Checked

| Category | MCP Tool | Examples |
|----------|----------|----------|
| **Completeness** | - | Source defined, sink defined, error handling |
| **URI Validation** | `camel_validate_route` | Valid component names, valid options |
| **Security** | `camel_route_harden_context` | Credentials, encryption, authentication |
| **Constitution** | - | Naming conventions, circuit breakers |

## Validation Report

When using MCP, you get instant feedback:
```
Route structure: VALID
URI validation: VALID (3/3 endpoints)
Security: 45/47 checks passed

Security Issues:
  Line 12: HTTP instead of HTTPS
  Risk: Unencrypted communication
  Fix: Change to https://api.example.com
```

Without MCP, results are saved to `.camel-kit/validation-report.md` with:
- Pass/fail status for each check
- Error codes for failures
- Suggested fixes

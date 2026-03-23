---
name: camel-validate
description: Validate routes when user wants to check YAML syntax, review a route, verify it works correctly, find issues or bugs, check for security problems, ensure best practices, or get a quality assessment of their Camel integration. Use this when the user says things like "check my route", "is this correct", "review my YAML", "validate order-to-warehouse", or "what's wrong with my route".
user-invocable: true
metadata:
  version: "2.0.0"
  author: "camel-kit"
  category: "integration"
  license: "Apache-2.0"
---

# Camel Validate - Route Validation and Quality Assurance

You are an orchestrator that validates Camel integrations against technical standards and best practices. You load context, then dispatch sub-agents for each validation stage.

## Parameters

```
/camel-validate <flow-name>   # Validate specific flow
/camel-validate all           # Validate all flows
/camel-validate --all         # Same as above
/camel-validate               # Same as above (no argument)
```

### Batch Mode (`all`)

When `all`, `--all`, or no argument is specified:

1. **Discover flows:** List all directories under `docs/flows/` with a `{flow-name}.tdd.md` file, verify route YAML exists
2. **Show plan and proceed immediately:**
   ```
   Found [N] flows to validate:
     1. flow-name-1  ({flow-name-1}.camel.yaml)
     ...
   Validating all [N] flows sequentially...
   ```
3. **Process sequentially:** For each flow, run the full validation pipeline. Between flows, report progress. **Continue on failure.**
4. **Final summary** with pass/fail status per flow and next steps.

If no route YAML files are found: ERROR "No Camel routes found. Run /camel-implement first."

---

## Context Loading (do this first)

**ALWAYS read at the start:**
1. `docs/business-requirements.md` - Business context (if exists)
2. `docs/constitution.md` - Best practices and quality gates
3. `.camel-kit/config.yaml` - Camel version (if exists)
4. `docs/flows/{flow-name}/{flow-name}.tdd.md` - Technical specification
5. Route YAML files — from project root (JBang) or `src/main/resources/camel/` (Spring Boot/Quarkus)

## Context Variable Resolution

| Variable | Source | Passed to |
|----------|--------|-----------|
| `FLOW_NAME` | From parameter | All guides |
| `CAMEL_VERSION` | From `.camel-kit/config.yaml` | All guides |
| `RUNTIME` | From `.camel-kit/config.yaml` (default: `jbang`) | All guides |
| `PLATFORM_BOM` | Resolved via `skills/shared/mcp-setup.md` | All guides |

---

## Guide Manifest

After context loading, dispatch sub-agents for each validation stage in order.

| Step | Guide | Shared Guide | ~Tokens | When |
|------|-------|-------------|---------|------|
| A | guides/schema-validation.md | - | 0.6K | Always |
| B | guides/endpoint-validation.md | shared/mcp-setup.md | 0.8K | Always |
| C | guides/quality-checks.md | - | 2K | Always |
| D | guides/security-analysis.md | shared/mcp-setup.md | 0.7K | Always |
| E | guides/anti-patterns.md | - | 2.9K | User requests comprehensive OR all stages pass |

### Context Passing

Include in each sub-agent prompt:
- Flow name, Camel version, runtime, platform BOM
- Route YAML file content
- TDD content (for completeness/correctness checks)
- Constitution content (for Stage 6)

### MCP Tools Used

- `camel_validate_route` — URI validation (Stage 2)
- `camel_route_harden_context` — 47 security checks (Stage 8)
- `camel_route_context` — Route understanding
- `camel_rh_build_component_info` — Red Hat support check
- `camel_rh_build_search` — Red Hat docs search

---

## Validation Report

### Success Report

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ VALIDATION PASSED: {flow-name}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Summary:
  YAML schema: ✅ PASSED
  Endpoint URIs: ✅ PASSED (MCP validated)
  Camel runtime: ✅ PASSED
  Completeness: ✅ PASSED
  Correctness: ✅ PASSED
  Constitution: ✅ PASSED
  Configuration: ✅ PASSED
  Security: ✅ PASSED (47/47 checks - MCP)

Next steps:
  /camel-test {flow-name}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Failure Report

Show errors with fix suggestions, then warnings. End with "Fix the errors above and run /camel-validate again."

Save detailed report to `.camel-kit/validation-report.md`.

---

## Error Handling

### No Routes Found
```
❌ ERROR: No Camel routes found
Looking for: *.camel.yaml
Have you run /camel-implement yet?
```

### MCP Tool Call Failed
Fall back to manual URI validation and standard anti-pattern checks. Suggest adding MCP server to `.mcp.json`.

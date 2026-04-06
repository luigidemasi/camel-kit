# Camel-Kit Validation — Constitution & Dependency Checks

Verify routes follow constitution best practices and resolve internal dependencies.

---

## 3. Constitution Compliance Checks

| Check ID | Rule | Severity | Message |
|----------|------|----------|---------|
| `CONST-001` | Route ID must follow naming convention | WARNING | Route ID doesn't follow `<domain>-<action>` |
| `CONST-002` | Route should have single responsibility | WARNING | Route has {n} steps — consider splitting |
| `CONST-003` | External calls must have resilience pattern | WARNING | External call without circuit breaker |
| `CONST-004` | Retry delays should be reasonable | WARNING | Retry delay exceeds 30s |
| `CONST-005` | High-volume routes should use idempotency | WARNING | High-volume without idempotent consumer |
| `CONST-006` | Structured data should have schema validation | WARNING | Consumes JSON but no validation step |
| `CONST-007` | Sensitive values should use placeholders | ERROR | Hardcoded value looks like a secret |
| `CONST-008` | Error strategy must match constitution | WARNING | Error handling doesn't follow guidelines |

---

## 4. Dependency Checks

| Check ID | Rule | Severity | Message |
|----------|------|----------|---------|
| `DEP-001` | Referenced `direct:` endpoint must have corresponding route | ERROR | No route for `direct:{name}` |
| `DEP-002` | Referenced `seda:` endpoint must have corresponding route | WARNING | No route for `seda:{name}` |
| `DEP-003` | No circular dependencies | ERROR | Circular dependency detected |
| `DEP-004` | Aggregation must have completion condition | ERROR | Aggregate without completion |
| `DEP-005` | Split should have aggregate or be fire-and-forget | WARNING | Split without aggregate |
| `DEP-006` | Bean references must be documented | WARNING | Bean '{bean}' not documented |

---

## Error and Warning Report Format

### Console Output

```
Checking: order-ingestion
  ✅ COMP-001: Route has ID
  ✅ COMP-002: Source defined
  ❌ COMP-004: Missing error handling
  ⚠️  CONST-002: Route has 9 steps (recommended: 7)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
VALIDATION RESULT: ❌ FAILED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Summary: 1 error, 2 warnings

Errors (must fix before /camel.generate):
  1. [COMP-004] order-ingestion: Missing error handling
     └─ Fix: Add error handling section

Warnings (recommended to fix):
  1. [CONST-002] order-ingestion: Route has 9 steps
     └─ Recommendation: Split into sub-routes via direct:
```

### Validation Report File

Generate `.camel-kit/validation-report.json`:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "result": "FAILED",
  "summary": {
    "routes": 2, "passed": 1, "failed": 1,
    "errors": 1, "warnings": 2
  },
  "errors": [
    {
      "code": "COMP-004",
      "route": "order-ingestion",
      "severity": "ERROR",
      "message": "Missing error handling"
    }
  ]
}
```

### Markdown Report

Generate `.camel-kit/validation-report.md` with per-route results table and action items checklist.

---

## Validation Modes

| Mode | Behavior |
|------|----------|
| **Standard** (default) | Errors block `/camel.generate`; warnings advisory |
| **Strict** (`--strict`) | Warnings become errors; all constitution rules enforced |
| **Lenient** (`--lenient`) | Only critical errors; warnings suppressed |

---

## Custom Validation Rules

Users can add custom rules in `.camel-kit/constitution.md`:

```yaml
custom_rules:
  - id: CUSTOM-001
    description: "All routes must use specific Kafka cluster"
    check: "source.component == 'kafka' implies brokers contains 'prod-kafka'"
    severity: ERROR
```

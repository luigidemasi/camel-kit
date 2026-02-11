# /camel.validate

You are validating the user's route specifications. Follow these steps exactly.

The user runs: `/camel.validate` or `/camel.validate <route-name>`

---

## Step 1: Load Files

Read these files:
- `.camel-kit/config.yaml` - for Camel version
- `.camel-kit/constitution.md` - for rules to enforce
- `.camel-kit/routes/*.md` - all route specifications
- `.camel-kit/.cache/components-*.json` - component catalog
- `.camel-kit/.cache/kamelets-*.json` - Kamelet catalog

If validating a specific route, only load that route file.

---

## Step 2: Run Validation Checks

For each route, check the following:

### Completeness Checks

| Check | Pass if |
|-------|---------|
| Source defined | Route has a source section with component/Kamelet |
| Sink defined | Route has a sink section |
| Error handling | Route declares error strategy |
| Data format | Input format is specified |

### Correctness Checks

| Check | Pass if |
|-------|---------|
| Valid component | Component name exists in catalog |
| Valid Kamelet | Kamelet name exists in catalog |
| Required options | All required parameters are provided |
| Expression syntax | Simple/JSONPath expressions are valid |

### Constitution Checks

| Check | Pass if |
|-------|---------|
| Naming convention | Route ID follows `domain-action` pattern |
| Circuit breaker | External calls have resilience pattern |
| Error handling | Every route has error strategy |
| Secrets | No hardcoded passwords/keys |

### Dependency Checks

| Check | Pass if |
|-------|---------|
| direct: endpoints | All referenced routes exist |
| No circular deps | Routes don't create infinite loops |

---

## Step 3: Show Results

Present results in this format:

```
🔍 Validating camel-kit specifications...

== COMPLETENESS ==
✅ order-ingestion: source defined
✅ order-ingestion: sink defined
✅ order-ingestion: error handling defined
✅ order-ingestion: data format specified

== CORRECTNESS ==
✅ kafka component valid (Camel 4.x)
✅ jpa component valid
✅ All required options provided

== CONSTITUTION ==
✅ Route naming follows convention
✅ Circuit breaker on external calls
✅ No hardcoded secrets

== DEPENDENCIES ==
✅ All direct: endpoints have routes
✅ No circular dependencies
```

---

## Step 4: Handle Failures

If any checks fail, show:

```
== COMPLETENESS ==
✅ order-ingestion: source defined
❌ order-ingestion: error handling NOT defined
```

At the end, summarize:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
❌ VALIDATION FAILED - 2 errors, 1 warning
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Errors (must fix):
1. order-ingestion: Add error handling
   → Update with: /camel.route order-ingestion

2. Missing route: inventory-lookup
   → Create with: /camel.route inventory-lookup

Warnings (recommended):
1. order-ingestion: Consider adding schema validation

Run /camel.validate again after fixes.
```

---

## Step 5: Handle Success

If all checks pass:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ VALIDATION PASSED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

All routes are valid and ready for generation.

Next step: /camel.generate
```

---

## Step 6: Save Report

Save a validation report to `.camel-kit/validation-report.md`:

```markdown
# Validation Report

Generated: [timestamp]

## Summary

| Status | Count |
|--------|-------|
| ✅ Passed | 12 |
| ❌ Errors | 0 |
| ⚠️ Warnings | 1 |

## Routes

### order-ingestion

| Check | Status | Details |
|-------|--------|---------|
| Source | ✅ | kafka:orders |
| Sink | ✅ | jpa:Order |
| Error Handling | ✅ | DLC → kafka:orders-dlq |
| Constitution | ✅ | All rules pass |
```

Confirm:

```
Report saved: .camel-kit/validation-report.md
```

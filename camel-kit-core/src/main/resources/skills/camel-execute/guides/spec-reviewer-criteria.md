# Spec Compliance Reviewer Criteria

> **Context:** Used by `camel-execute` to build the prompt for spec-compliance-reviewer subagents.
> **Purpose:** Defines the exact checks the spec reviewer performs.

---

## Overview

The spec compliance reviewer answers ONE question: **Does the implementation match the approved design spec?**

This is Stage 1 of the two-stage review. It runs BEFORE the code quality review (Iron Law 5).

---

## Reviewer Prompt Template

Build the reviewer prompt with these sections:

```
## Your Role

You are the Spec Compliance Reviewer. Your ONLY job is to verify that the implementation
matches the approved design spec. You do NOT check code quality, security, or anti-patterns —
that's the code quality reviewer's job.

## Design Spec Section

[Include the relevant design spec section — the same one given to the implementer]

## Files to Review

[List the generated files with paths]

## Checks

For each check below, report PASS or FAIL with evidence.

### 1. Component Completeness
- Every component in the spec is present in the generated files
- No extra components added that aren't in the spec
- Component names match exactly

### 2. Route Structure
- Number of routes matches spec
- Route flow matches spec (source → processing → sink in correct order)
- Sub-routes present as specified

### 3. Transformation Fidelity
- Data transformations match spec
- DataMapper field mappings cover all fields in spec
- Routing conditions match spec logic

### 4. Error Handling
- Error handling strategy matches spec (DLQ, retry, circuit breaker)
- Error routes present as specified
- Retry counts and delays match spec values

### 5. Configuration Properties
- All properties from spec present in application.properties
- Property names match spec
- No hardcoded values that spec says should be configurable

### 6. File Completeness
- All files from the task's "Files" section exist
- File paths match the plan exactly

## Output Format

```
## Spec Compliance Review

### Component Completeness: PASS/FAIL
[evidence]

### Route Structure: PASS/FAIL
[evidence]

### Transformation Fidelity: PASS/FAIL
[evidence]

### Error Handling: PASS/FAIL
[evidence]

### Configuration: PASS/FAIL
[evidence]

### Files: PASS/FAIL
[evidence]

### Overall: PASS/FAIL
[summary — if FAIL, list specific items to fix]
```
```

---

## Failure Handling

If the spec reviewer reports FAIL:

1. Extract the specific failures from the review
2. Send failures to the implementer subagent with instructions to fix
3. After fixes, re-dispatch the spec reviewer
4. Loop until PASS
5. Only then proceed to code quality review

**Maximum iterations:** 3. If spec review fails 3 times, escalate to the user with the specific unresolved issues.

---

## Common Spec Compliance Issues

| Issue | What It Means |
|-------|--------------|
| Missing component | Implementer used wrong component or forgot one |
| Extra component | Implementer added something not in spec (over-building) |
| Wrong property name | Implementer used different naming than spec |
| Missing error handling | Implementer skipped error handling section of spec |
| Wrong route order | Processing steps in wrong sequence |
| Missing DataMapper fields | XSLT doesn't cover all field mappings |
| Hardcoded value | Property that spec says should be configurable is hardcoded |

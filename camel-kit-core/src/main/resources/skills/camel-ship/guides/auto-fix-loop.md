# Auto-Fix Loop

Automated fix-verify-retry logic for pipeline failures. Used when the oversight level allows autonomous fixing.

---

## When to Enter

The auto-fix loop is entered when:
- A stage produces a failure AND the oversight matrix says AUTO-FIX
- This only happens with `--ask never` (and for some outcomes with `--ask smart`)

## Loop Logic

```
ROUND = 0
MAX_ROUNDS = 3

while ROUND < MAX_ROUNDS:
    1. CLASSIFY the finding
    2. ATTEMPT the fix
    3. RE-VERIFY
    4. If verification passes → EXIT loop (success)
    5. ROUND += 1

If ROUND == MAX_ROUNDS:
    ESCALATE to user (regardless of --ask level)
```

---

## Step 1: Classify Finding

Categorize each finding:

| Category | Description | Examples |
|---|---|---|
| **Critical** | Prevents build/run | Compilation error, missing dependency, YAML parse error |
| **Important** | Passes build but violates rules | Iron Law violation, Constitution non-compliance, test failure |
| **Suggestion** | Improvement opportunity | Code quality, performance optimization, style |

- `--ask smart`: auto-fix Critical and Important. Pause on Suggestion (user decides).
- `--ask never`: auto-fix all categories.

## Step 2: Attempt Fix

Based on category:

### Critical Fixes
- Compilation error → read the error message, locate the source file, fix the syntax/type issue
- Missing dependency → add to pom.xml, re-run `mvn compile`
- YAML parse error → validate YAML structure, fix indentation/syntax

### Important Fixes
- Iron Law violation → re-verify the component/EIP via MCP catalog, correct the usage
- Constitution violation → compare route against constitution rules, adjust
- Test failure → read the test output, identify the assertion that failed, fix the route or test

### Suggestion Fixes
- Apply the improvement directly (only in `--ask never` mode)

## Step 3: Re-Verify

After applying a fix:

1. Re-run the specific check that failed:
   - Build error → `mvn compile`
   - Test failure → `mvn test`
   - Iron Law → re-scan the specific YAML file
   - Constitution → re-check the specific rule
2. If the check passes → the finding is resolved
3. If the check fails → increment round counter and loop

## Step 4: Escalate

After 3 failed rounds for the same finding:

1. Present the finding to the user with:
   - What was tried (3 fix attempts)
   - What the current state is
   - The specific error that persists
2. Ask the user to: "Fix manually" / "Skip this check" / "Abort pipeline"
3. This pause happens regardless of `--ask` level — 3 failed auto-fixes is always a blocker

---

## Fix Attempt Tracking

Track fix attempts in the state file under `fixAttempts`:

```json
"fixAttempts": {
    "2:task-3": 2,
    "3:iron-law-1": 1
}
```

Key format: `{stage}:{finding-id}`

This allows `--resume` to continue with the correct round count after an interruption.

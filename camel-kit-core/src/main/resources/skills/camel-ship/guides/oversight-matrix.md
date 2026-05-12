# Oversight Matrix

Decision rules for each oversight level at each pipeline stage. The orchestrator consults this matrix after each stage completes to determine whether to pause for user input or auto-proceed.

---

## Matrix

| Stage | Outcome | `always` | `smart` | `never` |
|---|---|---|---|---|
| **Brainstorm** | Design spec complete, no open questions | PAUSE | AUTO-PROCEED | AUTO-PROCEED |
| **Brainstorm** | Design spec has open questions or ambiguities | PAUSE | PAUSE | AUTO-PROCEED (pick reasonable defaults) |
| **Brainstorm** | Failed to produce design spec | PAUSE | PAUSE | PAUSE (blocker) |
| **Plan** | Plan complete, all tasks defined | AUTO-PROCEED | AUTO-PROCEED | AUTO-PROCEED |
| **Plan** | Plan has gaps or inconsistencies | PAUSE | PAUSE | AUTO-PROCEED (fill gaps) |
| **Plan** | Failed to produce plan | PAUSE | PAUSE | PAUSE (blocker) |
| **Execute** | Tasks done, verification PASS | PAUSE (present report) | PAUSE (present report) | AUTO-PROCEED |
| **Execute** | Tasks done, verification FAIL | PAUSE | PAUSE | AUTO-FIX (up to 3 rounds) |
| **Execute** | Task implementation failed | PAUSE | PAUSE | AUTO-FIX (up to 3 rounds) |
| **Execute** | Auto-fix exhausted (3 rounds) | PAUSE | PAUSE | PAUSE (blocker) |
| **Validate** | No Critical findings | PAUSE (present report) | AUTO-PROCEED to Stamp | AUTO-PROCEED to Stamp |
| **Validate** | Critical findings | PAUSE | PAUSE | PAUSE (blocker) |
| **Stamp** | All gates pass | DONE | DONE | DONE |
| **Stamp** | Gate failure | PAUSE | PAUSE | PAUSE (blocker) |

---

## Decision Logic

For each stage completion, execute this logic:

```text
1. Determine outcome category (success / partial / failure)
2. Look up action in matrix for (stage, outcome, --ask level)
3. If action is PAUSE:
   - Present results to user
   - Wait for user response
   - User can: approve, request changes, abort pipeline
4. If action is AUTO-PROCEED:
   - Save state
   - Continue to next stage
5. If action is AUTO-FIX:
   - Load guides/auto-fix-loop.md
   - Attempt fix (max 3 rounds)
   - If fix succeeds → treat as AUTO-PROCEED
   - If fix fails → escalate to PAUSE
6. If action is PAUSE (blocker):
   - This is a hard stop regardless of --ask level
   - Present the blocker to the user
   - Pipeline cannot continue until user resolves it
```

---

## Ambiguity Detection

"Ambiguous outcomes" (relevant for `smart` vs `never`) are detected by:

### Brainstorm Stage
- Open questions in the design spec (sections marked TODO or containing "?" in decision fields)
- Multiple equally viable architecture options presented without a recommendation
- Missing component selections (source or target system not mapped to a Camel component)

### Plan Stage
- Tasks with missing acceptance criteria
- Circular dependencies in the task graph
- Tasks referencing components not in the design spec

### Execute Stage (includes verification)
- Test failures (clear signal — not ambiguous, but requires decision)
- Compilation errors (clear signal)
- MCP verification failures (component not found in catalog)
- Partial runtime verification (some checks pass, some fail)

### Validate Stage
- Critical findings detected (security vulnerabilities, constitution violations)
- Mixed results across quality dimensions (some pass, some fail)

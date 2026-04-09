---
name: camel-execute
description: Use when there is an approved implementation plan ready for execution — executes all tasks sequentially with two-stage review (spec compliance then code quality)
---

# Camel Execute — Execution Pipeline (Bob)

Execute the approved implementation plan by implementing tasks sequentially with two-stage review after each. Follow every step in order. Do NOT skip steps.

**Core principle:** Execute ALL tasks automatically without stopping between tasks. The user approved the entire plan — that is authorization to execute every task.

<Steps>
<Step>
## Switch to Execute Mode

Switch to **camel-execute** mode using the mode selector or `/camel-execute` command.
This enables autonomous task execution with review gates.
</Step>

<Step>
## Verify Approved Plan Exists

Read `docs/implementation-plan.md` (or the specified plan path).

If the plan hasn't been approved, STOP and return to camel-plan.
Execution only happens after plan approval.
</Step>

<Step>
## Extract All Tasks

Parse the plan and extract:
- All task IDs and names
- Agent persona per task
- Files to create/modify per task
- Guides to load per task
- MCP tools to call per task
- Design spec section reference per task
- Review specification per task

Create a task execution queue with all tasks in order.
</Step>

<Step>
## Execute Each Task

For EACH task in the queue:

**CHECKPOINT** — Create a checkpoint before starting this task.

### Task Execution Process

**Step 1: Read Task Context**
- Read the full task text from the plan
- Read the relevant design spec section (if specified)
- Read the relevant TDD (if specified)
- Load project context (config.yaml, constitution.md)

**Step 2: Load Guides**
- Load all guides specified in the task
- For implementation tasks: load `camel-implement/guides/orchestrator.md` + route-specific guides
- For validation tasks: load `camel-validate/guides/` guides
- For test tasks: load `camel-test/guides/` guides

**Step 3: Execute Task Steps**

Follow the task's step-by-step instructions. For implementation tasks:

1. **Verify components via MCP:**
   - For EVERY component: `camel_catalog_component`
   - For EVERY component: `camel_rh_build_component_info`
   - For EVERY EIP: `camel_catalog_eip`
   - For EVERY dataformat: `camel_catalog_dataformat`
   - For EVERY language: `camel_catalog_language`

2. **Generate artifacts:**
   - YAML routes: follow `guides/yaml-structure.md` + `guides/yaml-catalog-rules.md`
   - Properties: follow `guides/properties-generation.md`
   - POM: follow `guides/maven-dependencies.md`
   - Tests: follow `guides/test-generation.md`
   - Docker Compose: follow `guides/docker-compose.md`
   - XSLT: follow `guides/datamapper-approach-a.md` or `guides/datamapper-approach-b.md`

3. **Run verification:**
   - Execute the verification command from the task
   - Verify expected output matches

**Step 4: Spec Compliance Review (Stage 1)**

Load `guides/spec-reviewer-criteria.md` (if exists) or use these criteria:

Check:
- Does the generated artifact match the TDD specification?
- Are all required components present?
- Are all component options configured correctly per MCP catalog?
- Is the data flow correct (source → transformations → sink)?
- Are all properties defined?
- Are all error handlers specified in the TDD present?

If spec review FAILS:
1. Identify the gap
2. Fix the artifact
3. Re-run spec review
4. Loop until PASS

Do NOT proceed to code quality review until spec review passes.

**Step 5: Code Quality Review (Stage 2)**

Load `guides/quality-reviewer-criteria.md` (if exists) or use these criteria:

Check all 7 constitution rules:
1. No hardcoded URLs
2. Explicit error handling
3. Structured logging
4. Idempotency (for stateful routes)
5. Circuit breaker (for HTTP calls)
6. TLS everywhere
7. Red Hat Build only

Check security:
- No hardcoded credentials
- No sensitive data in logs
- Input validation present

Check anti-patterns:
- Polling frequency reasonable
- HTTP timeouts configured
- No synchronous HTTP in loops
- Streaming for large messages

If quality review finds **Critical** issues:
1. Identify the issues
2. Fix the artifact
3. Re-run quality review
4. Loop until critical issues resolved

If quality review finds only **Important/Suggestion** issues:
- Note them
- Proceed to next task

**Step 6: Commit**

After both reviews pass:
```bash
git add \{files from task\}
git commit -m "feat: \{task description\}"
```

**Step 7: Mark Complete and Continue**

Print ONE LINE:
```
✅ Task N complete. Starting Task N+1...
```

Then IMMEDIATELY start the next task's Step 1 (Read Task Context).

**CRITICAL:** Do NOT ask "Would you like me to continue?" or print "Next Steps". The per-task loop is AUTOMATIC and UNINTERRUPTED.
</Step>

<Step>
## Final Cross-Cutting Review

After ALL tasks complete:

**Cross-Route Validation:**
- Load all generated routes
- Check for cross-route consistency:
  - Naming conventions
  - Property patterns
  - Error handling patterns
  - Logging patterns
- Check for duplicate route IDs
- Check for orphaned properties

**Constitution Compliance:**
- Verify ALL routes pass all 7 constitution rules
- Not just individual routes, but as a complete system

**Graph Analysis (if available):**
```bash
{commandPrefix} graph project-norms
{commandPrefix} graph dead-code
```

Report any cross-cutting issues.
</Step>

<Step>
## Run Smoke Test

If the plan includes a smoke test task:

Load `guides/smoke-test.md`.

Run:
```bash
mvn test -Dtest=SmokeTest
```

Verify all smoke tests pass.
</Step>

<Step>
## Generate Completion Summary

Print the completion summary:

```
===============================================================
IMPLEMENTATION COMPLETE
===============================================================

Plan: docs/implementation-plan.md
Design Spec: docs/design-spec.md

Tasks Completed: [N/N]

Generated Files:
  - src/main/resources/camel/order-processing.camel.yaml
  - src/main/resources/camel/inventory-sync.camel.yaml
  - src/main/resources/application.properties
  - pom.xml (updated)
  - src/test/java/.../routes/OrderProcessingRouteTest.java
  - src/test/java/.../routes/InventorySyncRouteTest.java
  - docker-compose.yml
  - docs/test-report.md

Review Results:
  Spec Compliance: [N/N] tasks passed
  Code Quality: [N/N] tasks passed ([M] non-critical issues noted)

Cross-Cutting Review: PASS/FAIL
Smoke Test: PASS/FAIL/NOT_RUN

Constitution Compliance: PASS/FAIL (all 7 rules)

===============================================================

[If PASS] Implementation complete. All routes pass validation. Ready for deployment.
[If FAIL] Critical issues found in cross-cutting review. See details above.
```
</Step>

<Step>
## CHECKPOINT

After successful completion:

**CHECKPOINT** — Create a final checkpoint.

Label: `implementation-complete-\{date\}`

This checkpoint captures:
- All generated routes
- All tests
- All configuration files
- Passing validation
- Completion summary
</Step>
</Steps>

## Autonomous Execution Rules

**CRITICAL:** This skill executes ALL tasks automatically:

1. **No pausing between tasks** — After Task N completes, immediately start Task N+1
2. **No asking for confirmation** — The plan approval is authorization for ALL tasks
3. **No mid-plan summaries** — Print ONE LINE per task completion, nothing more
4. **No "Next Steps" blocks** — You ARE executing the next step RIGHT NOW
5. **No "Ready to proceed" messages** — Just proceed

The ONLY time you print a summary is Step 8 (final completion summary) after ALL tasks are done.

## Two-Stage Review

For EVERY task:
1. **Spec Compliance Review FIRST** — Does it match the TDD?
2. **Code Quality Review SECOND** — Does it follow constitution rules?

NEVER:
- Run reviews in parallel
- Run reviews in reversed order
- Skip either review
- Proceed with open issues

## Iron Laws

Execution enforces ALL five Iron Laws:

- **Iron Law 1**: MCP Catalog Verification — verify every component via MCP before generating YAML
- **Iron Law 2**: Red Hat Build Only — verify every component via `camel_rh_build_component_info`
- **Iron Law 3**: Constitution Compliance — every route passes all 7 rules
- **Iron Law 4**: No Code Without Spec Approval — only runs after plan approval
- **Iron Law 5**: Spec Compliance Before Quality — ALWAYS spec first, quality second

## Guide Reference

| Guide | When to Load |
|-------|-------------|
| Implementation guides | All from `camel-implement/guides/` per task specification |
| Validation guides | All from `camel-validate/guides/` for review stages |
| Test guides | All from `camel-test/guides/` for test tasks |
| `guides/spec-reviewer-criteria.md` | Stage 1 review of every task |
| `guides/quality-reviewer-criteria.md` | Stage 2 review of every task |
| `guides/smoke-test.md` | If smoke test specified in plan |

## Never

- Start execution without an approved plan
- Skip reviews (spec compliance OR code quality)
- Run reviews in parallel or reversed order
- Stop or pause between tasks to ask the user
- Print "Next Steps" or completion summaries between tasks (only after the LAST task)
- Say "command has completed" or "phases are complete" while tasks remain
- Accept "close enough" on spec compliance
- Move to next task with open critical issues

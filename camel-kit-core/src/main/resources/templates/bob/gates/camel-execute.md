---
name: camel-execute
description: Use when an implementation plan derived from an approved design is ready for execution — executes all tasks sequentially with a same-session adversarial pre-filter followed by spec compliance and code quality review
---

# Camel Execute — Execution Pipeline (Bob)

Execute the implementation plan derived from the approved design by implementing tasks sequentially with a same-session adversarial pre-filter followed by ordered spec and quality review. Follow every step in order. Do NOT skip steps.

**Core principle:** Execute ALL tasks automatically without stopping between tasks. The approved design and its generated implementation plan authorize every task.

## Guide Locations

When loading guides, use full paths from the project root:

| Skill | Base path |
|---|---|
| Execution guides | `.bob/skills/camel-execute/guides/` |
| Implementation guides | `.bob/skills/camel-implement/guides/` |
| Validation guides | `.bob/skills/camel-validate/guides/` |
| Test guides | `.bob/skills/camel-test/guides/` |

Do NOT explore or list directories to find guides — use the paths above.

<Steps>
<Step>
## Switch to Execute Mode

Switch to **camel-execute-mode** using the mode selector or `/camel-execute-mode`.
This enables autonomous task execution with review gates.
</Step>

<Step>
## Detect Invocation Mode

- **Chained mode:** activated by `camel-plan` in the active pipeline. After the
  execute report and checkpoint, continue automatically to `camel-validate`.
- **Standalone mode:** invoked directly as `/camel-execute` or with a pipeline
  ID/plan path. After the execute report and checkpoint, stop; standalone mode
  suppresses automatic transitions.

Resolve the pipeline before reading artifacts: use an explicit pipeline ID; if
a plan path was supplied, derive the ID from its parent directory; otherwise
read `activePipeline` from `.camel-kit/pipeline.json`. If none resolves, stop
with the command needed to select or create a pipeline.
</Step>

<Step>
## Verify Authorized Plan Exists

Read `docs/camel-kit/<PIPELINE_ID>/implementation-plan.md` (or the specified plan path).

If the plan is not derived from an approved design spec, STOP and return to camel-brainstorm.
The design approval authorizes planning and execution; do not add a second plan-approval gate.
</Step>

<Step>
## Probe the Target Environment

Read `.bob/skills/camel-execute/guides/environment-probe.md` and run the target-runtime probe before any
implementation task. Verify dependency resolution and runtime startup. Check
Docker services only when the design requires them and Docker is available;
report unavailable Docker-dependent checks as skipped. Apply mechanical fixes,
and use the bounded re-plan loop for architectural failures as directed by the
guide.
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
- Load project context (config.properties, constitution.md)

**Step 2: Load Guides**
- Load all guides specified in the task
- For implementation tasks: load `.bob/skills/camel-implement/guides/orchestrator.md` plus route-specific guides
- For validation tasks: load `.bob/skills/camel-validate/guides/` guides
- For test tasks: load `.bob/skills/camel-test/guides/` guides

**Step 3: Execute Task Steps**

Follow the task's step-by-step instructions. For implementation tasks:

1. **Verify components via MCP:**
   - For EVERY component: `camel_catalog_component_doc`
   - For EVERY EIP: `camel_catalog_eip_doc`
   - For EVERY dataformat: `camel_catalog_dataformat_doc`
   - For EVERY language: `camel_catalog_language_doc`

2. **Generate artifacts:**
   - YAML routes: follow `.bob/skills/camel-implement/guides/yaml-structure.md` and `.bob/skills/camel-implement/guides/yaml-catalog-rules.md`
   - Properties: follow `.bob/skills/camel-implement/guides/properties-generation.md`
   - POM: follow `.bob/skills/camel-implement/guides/maven-dependencies.md`
   - Tests: follow `.bob/skills/camel-test/guides/test-generation.md`
   - Docker Compose: follow `.bob/skills/camel-implement/guides/docker-compose.md`
   - XSLT: follow `.bob/skills/camel-implement/guides/datamapper-approach-a.md` or `.bob/skills/camel-implement/guides/datamapper-approach-b.md`

3. **Run verification:**
   - Execute the verification command from the task
   - Verify expected output matches

**Step 4: Adversarial Review (Bob 1 Same-Session Fallback)**

Bob 1 has no subagent or fresh-context capability. Before staged review, re-read the exact task diff and inspect it sequentially through each applicable critic lens:

1. **Route architecture** — always: correctness, failure behavior, and unintended scope
2. **Security** — for external boundaries: trust boundaries, secrets, and unsafe input
3. **Performance** — for throughput, aggregation, or batch work: blocking, memory, and back-pressure risks
4. **Boundary compliance** — for transformations: schema and data-contract preservation
5. **Behavioral equivalence** — for migrations: source behavior versus generated behavior

For every finding, record the file/location, concrete evidence, impact, and smallest corrective action. Deduplicate the findings and assign one verdict: `PASS`, `PASS_WITH_TRADEOFFS`, or `FAIL`.

- On `FAIL`, fix the verified findings and rerun this step, up to 3 cycles. Escalate if actionable findings persist.
- On `PASS_WITH_TRADEOFFS`, record the trade-offs in the execution report and carry them into staged review.
- On `PASS`, continue.

Record that this Bob 1 fallback runs in the accumulated session and therefore does not provide fresh-context reviewer independence or parallel critic isolation.

**Step 5: Spec Compliance Review (Stage 1)**

Load `.bob/skills/camel-execute/guides/spec-reviewer-criteria.md` (if it exists) or use these criteria:

Check:
- Does the generated artifact match the design spec?
- Are all required components present?
- Are all component options configured correctly per MCP catalog?
- Is the data flow correct (source → transformations → sink)?
- Are all properties defined?
- Are all error handlers specified in the design spec present?

If spec review FAILS:
1. Identify the gap
2. Fix the artifact
3. Re-run spec review
4. Loop until PASS, for at most 3 review iterations
5. If Actionable findings persist after 3 rounds, escalate with the unresolved findings and documented trade-offs

Do NOT proceed to code quality review until spec review passes.

**Step 6: Code Quality Review (Stage 2)**

Load `.bob/skills/camel-execute/guides/quality-reviewer-criteria.md` (if it exists) or use these criteria:

Check all 8 constitution rules:
1. No hardcoded URLs
2. Explicit error handling
3. Structured logging
4. Idempotency (for stateful routes)
5. Circuit breaker (for HTTP calls)
6. TLS everywhere
7. Component verification
8. Infrastructure via Forage (`forage.*` properties when Forage covers it; ladder: Forage → component properties → hand-rolled bean with stated reason; hand-rolled `camel.beans.*` requires a one-line reason comment)

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

**Step 7: Commit**

After the adversarial pre-filter and both staged reviews pass:
```bash
git add <files from task>
git commit -m "feat: <task description>"
```

**Step 8: Mark Complete and Continue**

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
- Verify ALL routes pass all 8 constitution rules
- Not just individual routes, but as a complete system

**Graph Analysis (if available):**
```bash
{COMMAND_PREFIX} graph project-norms
{COMMAND_PREFIX} graph dead-code
```

Report any cross-cutting issues.
</Step>

<Step>
## Run Verification

Read `.bob/skills/camel-verify/SKILL.md` and run its three-phase loop once after
all implementation tasks and reviews:

1. Build the Spring Boot or Quarkus project, or run the Camel Main startup smoke test.
2. Run all planned Citrus integration tests through `camel test run`, classify failures, and apply fixes through the owning implementation or test path.
3. Generate the verification report, including every skipped check and reason.
</Step>

<Step>
## Generate Completion Summary

Write the completion summary at
`docs/camel-kit/<PIPELINE_ID>/execution-report.md`:

```
===============================================================
IMPLEMENTATION COMPLETE
===============================================================

Plan: docs/camel-kit/<PIPELINE_ID>/implementation-plan.md
Design Spec: docs/camel-kit/<PIPELINE_ID>/design-spec.md

Tasks Completed: [N/N]

Generated Files:
  [list every generated file with its actual path]

Review Results:
  Adversarial Review: [N/N] tasks passed ([M] trade-offs recorded)
  Spec Compliance: [N/N] tasks passed
  Code Quality: [N/N] tasks passed ([M] non-critical issues noted)

Cross-Cutting Review: PASS/FAIL

Verification: PASS/PARTIAL/FAIL/NOT_RUN
Verification Report:
  [include skipped checks and reasons]

Constitution Compliance: PASS/FAIL (all 8 rules)

===============================================================

[If PASS] Execution complete. All routes passed runtime verification. Ready for final static validation in chained mode.
[If FAIL] Critical issues found in cross-cutting review. See details above.
```

Run `{COMMAND_PREFIX} doc init --by camel-execute --from implementation-plan.md docs/camel-kit/<PIPELINE_ID>/execution-report.md`
to add provenance metadata. Then run
`{COMMAND_PREFIX} doc stale --reason "execution report regenerated" --cascade docs/camel-kit/<PIPELINE_ID>/validation-report.md`
when that downstream artifact exists. Do not mark the new execution report stale.

- **Standalone mode:** print this summary before stopping.
- **Chained mode:** do not print an intermediate completion summary; continue to
  final validation, which presents the pipeline's only completion result.
</Step>

<Step>
## CHECKPOINT

After successful completion:

**CHECKPOINT** — Create a final checkpoint.

Label: `implementation-complete-<date>`

This checkpoint captures:
- All generated routes
- All tests
- All configuration files
- Passing runtime verification
- Completion summary
</Step>

<Step>
## Continue or Stop

- **Chained mode:** switch to `camel-validate-mode` immediately, then read and follow
  `.bob/skills/camel-validate/SKILL.md` exactly once as the full final
  report-only static validation gate. Do not abbreviate it, pause, or request
  confirmation.
- **Standalone mode:** stop after writing the execution report and checkpoint.
</Step>
</Steps>

## Autonomous Execution Rules

**CRITICAL:** This skill executes ALL tasks automatically:

1. **No pausing between tasks** — After Task N completes, immediately start Task N+1
2. **No asking for confirmation** — The design approval and generated plan authorize ALL tasks
3. **No mid-plan summaries** — Print ONE LINE per task completion, nothing more
4. **No "Next Steps" blocks** — You ARE executing the next step RIGHT NOW
5. **No "Ready to proceed" messages** — Just proceed

The ONLY time you print a summary is after ALL tasks and runtime verification are done.

## Ordered Review

For EVERY task:
1. **Adversarial pre-filter FIRST** — Apply the critic lenses and fix verified failures.
2. **Spec Compliance Review SECOND** — Does it match the design spec?
3. **Code Quality Review THIRD** — Does it follow constitution rules?

NEVER:
- Run reviews in parallel
- Run reviews in reversed order
- Skip either review
- Proceed with open issues

## Iron Laws

Execution enforces the shared Iron Laws:

- **Iron Law 1**: MCP Catalog Verification — verify every component via MCP before generating YAML
- **Iron Law 2**: Constitution Compliance — every route passes all 8 rules
- **Iron Law 3**: No Code Without Design Approval — only runs after design approval
- **Iron Law 4**: Spec Compliance Before Quality — ALWAYS spec first, quality second
- **Iron Law 5**: Adversarial Code Review — generated artifacts pass Bob 1's same-session critic-lens fallback before staged review; record the lack of fresh-context isolation
- **Iron Law 6**: Surgical Changes — touch only what the approved task requires

## Guide Reference

| Guide | When to Load |
|-------|-------------|
| Implementation guides | All from `.bob/skills/camel-implement/guides/` per task specification |
| Validation guides | All from `.bob/skills/camel-validate/guides/` for review stages |
| Test guides | All from `.bob/skills/camel-test/guides/` for test tasks |
| `.bob/skills/camel-execute/guides/environment-probe.md` | Before any implementation task |
| `.bob/skills/camel-execute/guides/spec-reviewer-criteria.md` | Stage 1 review of every task |
| `.bob/skills/camel-execute/guides/quality-reviewer-criteria.md` | Stage 2 review of every task |
| `.bob/skills/camel-implement/guides/smoke-test.md` | If smoke test specified in plan |

## Never

- Start execution without a plan derived from an approved design
- Skip reviews (spec compliance OR code quality)
- Run reviews in parallel or reversed order
- Stop or pause between tasks to ask the user
- Print "Next Steps" or completion summaries between tasks (only after the LAST task)
- Say "command has completed" or "phases are complete" while tasks remain
- Accept "close enough" on spec compliance
- Move to next task with open critical issues

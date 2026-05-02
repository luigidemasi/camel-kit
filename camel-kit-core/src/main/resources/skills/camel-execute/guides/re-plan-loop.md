# Re-Plan Loop

Automatically resolves architectural failures by modifying the implementation plan (TDD files), without user intervention, up to 3 rounds. Triggered when a failure cannot be fixed mechanically — the component, pattern, or dependency is structurally wrong and the TDD must change.

**Modifies TDD file(s) ONLY — NEVER the BRD (`docs/business-requirements.md`).**

---

## Constraints

| Constraint | Value |
|---|---|
| Maximum rounds | 3 |
| Modifiable artifacts | TDD files (`docs/flows/**/*.tdd.md`) only |
| BRD (`docs/business-requirements.md`) | NEVER modified |
| TDD scope | ONLY sections affected by the failure |
| Short-circuit | Same failure class in consecutive rounds stops immediately |
| Escalation | After 3 rounds OR short-circuit — escalate to user regardless of `--ask` level |

---

## Entry Points

This guide is invoked from two locations:

| Entry Point | Trigger |
|---|---|
| `camel-execute/guides/environment-probe.md` Step 7 | Architectural failure during skeleton probe (dependency resolution, component unavailable, startup blocker) |
| `camel-verify/guides/verify-loop.md` | Persistent verification failures after mechanical fix attempts are exhausted |

---

## Two-Tier Promotion Model

Not every failure enters the re-plan loop. Classify the failure to determine whether re-planning is needed immediately or only after fix attempts fail.

### Tier 1: Immediate Promotion (0-1 fix attempts)

Triggered when the MCP catalog CONFIRMS the failure is structural. Skip further fix attempts and enter re-plan immediately.

| Failure | MCP Verification | Action |
|---|---|---|
| Component does not exist in the catalog for this runtime/version | `camel_catalog_component` returns no result | Enter re-plan |
| Required EIP pattern not available in this Camel version | `camel_catalog_model` returns no result | Enter re-plan |
| Component combination is invalid (incompatible transitive dependencies confirmed) | Both components exist individually but dependency analysis shows conflict | Enter re-plan |

### Tier 2: Progressive Promotion (3 failed fix attempts)

Triggered when mechanical fixes repeatedly fail to resolve the error.

| Condition | Criteria |
|---|---|
| Same error class | 3 consecutive fix attempts targeting the same error class |
| Different strategies | Each attempt used a different fix strategy (dependency change, route modification, config adjustment) |
| Error persists | The error or an error of the same class remains after all 3 attempts |

After the 3rd failed attempt, enter re-plan.

---

## Re-Plan Process

### Step 1: Identify Affected Scope

Determine which TDD file(s) and which sections within those TDDs need modification.

1. Read the failure context — which component, dependency, or pattern failed?
2. Map the failure to TDD section(s):

   | Failure Type | Affected TDD Section(s) |
   |---|---|
   | Component unavailable | Section 2 (Source System) or Section 4 (Sink System) |
   | Dependency conflict | Section 8 (Dependencies) |
   | Property/option not supported | Section 7 (Configuration) |
   | EIP pattern unavailable | Section 2/4 (component) + Section 7 (config) |

3. Determine the blast radius:
   - **Single flow:** failure is scoped to one TDD — modify that TDD only
   - **Multiple flows:** failure affects a shared component or dependency — modify each affected TDD and check for cascading impacts across flows

### Step 2: Find Alternative via MCP

Query the MCP catalog for alternative components that fulfill the same role.

1. Call `camel_catalog_component` with the target `runtime` and `platformBom` to list available components in the same category
2. Identify an alternative component that:
   - Fulfills the same integration role (same protocol family or equivalent)
   - Has required options that can satisfy the TDD requirements
   - Does not conflict with other planned components in the project
3. Verify the alternative's required and optional options via `camel_catalog_component(name="{alternative}")`
4. If no viable alternative exists in the catalog — skip to escalation (do not guess)

### Step 3: Modify TDD

Update ONLY the affected sections. Preserve all other sections verbatim.

1. Replace the failing component/version/pattern with the verified alternative
2. Update Section 8 (Dependencies) to match the new component's Maven coordinates
3. Update Section 7 (Configuration) if the new component requires different properties
4. Add a **Re-Plan History** appendix entry at the end of the TDD:

```markdown
### Re-Plan [round N] — [YYYY-MM-DD]
**Trigger:** [error description]
**Change:** [what was modified — old component/pattern -> new component/pattern]
**Reason:** [why the original failed and why this alternative was chosen]
**MCP verification:** [catalog query result confirming alternative exists]
```

### Step 4: Re-Execute Affected Tasks

Re-execute ONLY the tasks that depend on the changed TDD sections. Do NOT re-run the entire implementation plan.

| Re-Plan Trigger Source | Re-Execution Sequence |
|---|---|
| Environment probe (pre-implementation) | Re-run the probe for affected TDD(s) first, then proceed with implementation |
| Verify loop (post-implementation) | Re-run implementation for affected flow(s), then re-verify |

### Step 5: Re-Verify

After re-execution, verify that the change resolved the failure.

| Re-Plan Trigger Source | Re-Verification |
|---|---|
| Environment probe | Re-run the environment probe: dependency resolution, Docker services, startup skeleton |
| Verify loop | Re-run the verify loop: build, startup, behavioral tests, report |

---

## Loop Control

```text
round = 0
previous_failure_class = null

while round < 3:
    1. Identify affected scope (Step 1)
    2. Find alternative via MCP (Step 2)
    3. Modify TDD (Step 3)
    4. Re-execute affected tasks (Step 4)
    5. Re-verify (Step 5)
    6. If verification passes -> EXIT loop (continue pipeline)
    7. Classify the new failure
    8. If same failure class as previous_failure_class:
       -> Short-circuit: "Same failure class after re-plan. Escalating."
       -> ESCALATE to user with full context
    9. previous_failure_class = current_failure_class
    10. round += 1

If round == 3:
    -> "Re-plan limit reached (3 rounds). Escalating."
    -> ESCALATE to user with all 3 round summaries
```

---

## Escalation Report

When escalating (either short-circuit or round limit), present the following report:

```
RE-PLAN ESCALATION
Rounds attempted: {N}
Exit reason:      {round limit | same failure class | no viable alternative}

Round 1: [trigger] -> [change] -> [result]
Round 2: [trigger] -> [change] -> [result]
Round 3: [trigger] -> [change] -> [result]

Affected TDD(s): [list of TDD file paths]
Current failure:  [error details]

Suggested action: [manual fix suggestion based on failure pattern]
```

Include only the rounds that were actually executed. If the loop short-circuited at round 2, show only rounds 1 and 2.

---

## Never

- Modify the BRD (`docs/business-requirements.md`)
- Re-plan more than 3 times
- Re-run the entire implementation plan for a TDD-scoped change
- Skip the re-verify step after re-planning
- Skip MCP verification when selecting alternatives (Iron Law 1 still applies)
- Modify TDD sections unrelated to the failure
- Guess an alternative component without MCP catalog confirmation
- Continue the loop when the same failure class repeats in consecutive rounds

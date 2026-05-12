## Agent Optimization: IBM Bob

### Mode-Based Pipeline

Chain the pipeline using Bob's mode system:

- Stage 0 (Brainstorm): `switch_mode` to "camel-brainstorm" — interview gates control progress
- Stage 1 (Plan): `switch_mode` to "camel-plan" — plan structure gates validate completeness
- Stage 2 (Execute): `switch_mode` to "camel-implement" — implementation gates validate quality
- Stage 3 (Validate): `switch_mode` to "camel-validate" — static quality analysis, generates validation report

### Gate-Based Oversight

Bob's existing gate system maps directly to the `--ask` oversight levels:

- `always`: gates pause and require explicit approval at every checkpoint
- `smart`: gates auto-approve when all criteria pass, pause when any criterion is ambiguous
- `never`: gates log results but don't block — pipeline continues unless a gate reports a blocker

The gate files in `.bob/gates/` already define per-skill validation criteria. The ship command leverages them rather than implementing a separate oversight mechanism.

### Plan Stage Auto-Proceeds

The plan->execute approval gate has been removed. Stage 1 (Plan) auto-proceeds to Stage 2 (Execute) when the plan is complete. Bob's gate for "camel-plan" should auto-approve when all tasks are defined (no pause for plan approval).

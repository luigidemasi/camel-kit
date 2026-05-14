## Agent Optimization: IBM Bob — Plan Handoff

### Override: Return Control to Orchestrator

**This replaces the default handoff in Step 5 above.** This override applies in chained mode only; in standalone mode the skill stops at Step 3.

When the implementation plan is ready:

1. Save the plan to `docs/camel-kit/<PIPELINE_ID>/implementation-plan.md`
2. Print the chained mode output (plan saved confirmation)
3. **STOP — do NOT auto-invoke `camel-execute`.** Return control to the calling orchestrator (`camel-ship`).

Bob's mode system handles the stage transition: `camel-ship` invokes `camel-plan` within the "camel-plan" mode, and when the plan completes, `camel-ship` applies the oversight decision from `guides/oversight-matrix.md` before transitioning to Stage 2 (Execute) via `switch_mode` to "camel-implement".

Auto-invoking `camel-execute` from within `camel-plan` would bypass:
- The oversight pause between stages (if `--ask always`)
- The `currentStage` state update in `.camel-kit/pipeline.json`
- Any gate validation that `camel-ship` applies between stages

### Gate-Based Plan Quality

The gate file (`gates/camel-plan.md`) validates plan completeness before the mode system transitions to execute. Let the gate handle validation rather than adding manual quality checks at the end of planning.

## Agent Optimization: OpenCode — Plan Handoff

### Override: Return Control to Orchestrator

**This replaces the default handoff in Step 5 above.** This override applies in chained mode only; in standalone mode the skill stops at Step 3.

When the implementation plan is ready:

1. Save the plan to `docs/camel-kit/<PIPELINE_ID>/implementation-plan.md`
2. Print the chained mode output (plan saved confirmation)
3. **STOP — do NOT auto-invoke `camel-execute`.** Return control to the calling orchestrator (`camel-ship`).

OpenCode's pipeline orchestration runs in the `Plan` agent type during Stage 1. When the plan completes, `camel-ship` applies the oversight decision from `guides/oversight-matrix.md`, updates `currentStage` in `.camel-kit/pipeline.json`, and transitions to Stage 2 (Execute) using the `Build` agent type.

Auto-invoking `camel-execute` from within `camel-plan` would bypass:
- The oversight pause between stages (if `--ask always`)
- The `currentStage` state update in `.camel-kit/pipeline.json`
- The agent type transition from `Plan` to `Build`

### Step Budget for Planning

Budget `steps: 100` for the planning phase (as specified in the ship trait). Do not over-consume steps with speculative analysis — produce the plan artifact and return. The step budget for execution is managed separately by `camel-ship`.

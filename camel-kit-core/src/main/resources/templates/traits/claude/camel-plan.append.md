## Agent Optimization: Claude Code — Plan Handoff

Use the canonical pipeline artifact path
`docs/camel-kit/<PIPELINE_ID>/implementation-plan.md`. Do not enter Claude's
native plan mode or call `ExitPlanMode` during the chained Camel-Kit pipeline:
that dialog would add a second plan-approval gate after the design approval.

- **Chained mode:** write the plan artifact and auto-invoke `camel-execute`.
- **Standalone mode:** write the plan artifact and stop.

Claude's ordinary tool permission prompts remain in force during execution; they
are platform safety controls, not Camel-Kit workflow approval gates.

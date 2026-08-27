## Agent Optimization: Qwen Code — Plan Handoff

### Override: Approval Mode Selection Before Execution

**This replaces the default handoff in Step 5 above.** This override applies in chained mode only; in standalone mode the skill stops at Step 3.

When the implementation plan is ready:

1. Save the plan to `docs/camel-kit/<PIPELINE_ID>/implementation-plan.md`
2. Present the plan summary to the user
3. Ask the user how they want to handle file edits during execution:

```text
Plan saved. Before starting execution, how would you like to handle file edits?

1. Auto-accept edits (I'll switch to auto-edit mode)
2. Manually approve each edit (switch to default mode)
3. Refine the plan (tell me what to change)
```

4. Based on the user's choice:
   - **Auto-accept:** Instruct the user to run `/approval-mode auto-edit` to switch to auto-edit mode. Alternatively, the user can press `Shift+Tab` to cycle modes. Confirm the mode is active, then invoke `camel-execute`. Once the mode is confirmed, `camel-execute` is invoked immediately — no additional approval prompt.
   - **Manual:** Instruct the user to run `/approval-mode default` and confirm that mode is active, then invoke `camel-execute` immediately.
   - **Refine:** Return to plan generation with the user's feedback.

### Wave Execution Reminder

After the handoff, explain that the executor emits one `agent` call per independent task in the same plan-analyzer wave
and waits for all returned results before starting the dependent wave. Auto-edit mode can reduce repeated approval
prompts across those implementation calls.

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
2. Manually approve each edit (keep current mode)
3. Refine the plan (tell me what to change)
```

4. Based on the user's choice:
   - **Auto-accept:** Instruct the user to run `/approval-mode auto-edit` to switch to auto-edit mode. Alternatively, the user can press `Shift+Tab` to cycle modes. Confirm the mode is active, then invoke `camel-execute`. Once the mode is confirmed, `camel-execute` is invoked immediately — no additional approval prompt.
   - **Manual:** Invoke `camel-execute` immediately (current mode stays as-is). Execution starts immediately in the current mode.
   - **Refine:** Return to plan generation with the user's feedback.

### Serial Execution Reminder

After the handoff, remind the user that Qwen executes tasks serially — each task completes before the next starts. Auto-edit mode is particularly beneficial here since it avoids repeated approval prompts across many sequential tasks.

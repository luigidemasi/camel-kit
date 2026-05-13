## Agent Optimization: Gemini CLI — Plan Handoff

### Override: Approval Mode Selection Before Execution

**This replaces the default handoff in Step 5 above.**

When the implementation plan is ready:

1. Save the plan to `docs/camel-kit/<PIPELINE_ID>/implementation-plan.md`
2. Present the plan summary to the user
3. Ask the user how they want to handle file edits during execution:

```text
Plan saved. Before starting execution, how would you like to handle file edits?

1. Auto-accept edits (press Shift+Tab to cycle to "auto_edit" mode)
2. Manually approve each edit (keep current mode)
3. Refine the plan (tell me what to change)
```

4. Based on the user's choice:
   - **Auto-accept:** Instruct the user to press `Shift+Tab` until the status bar shows `auto_edit`. Confirm the mode is active, then invoke `camel-execute`.
   - **Manual:** Invoke `camel-execute` immediately (current mode stays as-is).
   - **Refine:** Return to plan generation with the user's feedback.

### Batch Context for Execution

After the user confirms, use `read_many_files` to pre-load the design spec and implementation plan before invoking `camel-execute`. This gives the execute phase full context in a single read.

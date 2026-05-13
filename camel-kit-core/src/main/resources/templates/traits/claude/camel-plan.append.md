## Agent Optimization: Claude Code — Plan Handoff

### Override: Use Native Plan Mode for Execution Handoff

**This replaces the default handoff in Step 5 above.**

When the implementation plan is ready:

1. Write the plan content to the plan file (the path specified by plan mode, typically `~/.claude/plans/{name}.md`)
2. Call `ExitPlanMode` to present the plan for user approval

`ExitPlanMode` triggers Claude Code's native approval dialog, which presents:

- **Approve and start in auto mode** — auto-approves all actions with safety classifier
- **Approve and accept edits** — auto-approves file edits, prompts for shell commands
- **Approve and review each edit manually** — prompts for every action
- **Keep planning with feedback** — return to planning with user input

The user's choice sets the permission mode for the entire execution phase. After approval, auto-invoke `camel-execute`.

### Entering Plan Mode

At the **start** of `camel-plan` (before generating the plan), call `EnterPlanMode` to enter Claude Code's native plan mode. This:

- Restricts tools to read-only operations and plan file editing (safe for planning)
- Enables `ExitPlanMode` at the end (the native dialog only works when in plan mode)
- Shows the plan file path in the status bar for the user

If `EnterPlanMode` is not available or fails (e.g., already in plan mode), skip this step and fall back to the default handoff (auto-invoke `camel-execute`).

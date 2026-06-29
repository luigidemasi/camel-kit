## Agent Optimization: Claude Code

### Re-Plan via Fresh Subagent

When the re-plan loop triggers, dispatch the re-planning work to a fresh subagent:

- Use the `Agent` tool with a prompt that includes: the failure details, the affected design spec content, the MCP catalog response, and the alternative component requirements
- The fresh subagent gets clean context — no residual assumptions from the failed implementation attempt
- Wait for the subagent to complete before re-running the probe or verify

### Re-Plan State Tracking

Use `TaskCreate` to track re-plan rounds:

- Create a task for each round: "Re-plan round {N}: {failure description}"
- Mark `in_progress` during re-planning
- Mark `completed` if re-probe/re-verify passes
- If all 3 rounds fail, the task list shows the full history for the escalation report

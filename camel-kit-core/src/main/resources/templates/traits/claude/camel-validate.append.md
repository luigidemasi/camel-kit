## Agent Optimization: Claude Code

### Validation Progress Tracking

Use Claude Code's task tracking for real-time visibility during validation:

- `TaskCreate` for each validation dimension at the start (endpoint verification, constitution compliance, property audit, etc.)
- `TaskUpdate` to mark each dimension `in_progress` when starting and `completed` when done
- `TaskList` to report progress after each dimension completes

### Build Verification Pacing

When validation includes a build verification step (`mvn verify` or `/camel-verify`):

- Run the build in the foreground — builds typically complete within the 300s cache TTL
- If the build takes longer (large projects), use `ScheduleWakeup` with `delaySeconds: 270` as a fallback heartbeat
- Set `reason` to "waiting for Maven build to verify generated routes"

### Parallel Dimension Analysis

Use the `Agent` tool to run independent validation dimensions in parallel:

- Dispatch endpoint verification as an `Explore` subagent (read-only MCP catalog checks)
- Dispatch property audit as an `Explore` subagent (read-only file scanning)
- Run constitution compliance inline (it needs the full context of all routes)

```text
Agent({
  subagent_type: "Explore",
  model: "sonnet",
  description: "Validate: endpoint verification",
  prompt: "[spec-compliance-reviewer persona + all route files + MCP catalog verification focus]",
  run_in_background: true
})

Agent({
  subagent_type: "Explore",
  model: "sonnet",
  description: "Validate: property audit",
  prompt: "[code-quality-reviewer persona + all route files + application.properties + property naming audit]",
  run_in_background: false
})
```

Wait for both to complete, then run constitution compliance inline and merge all results into the validation report.

### Structured Report via AskUserQuestion

When validation finds issues, use `AskUserQuestion` to present findings with actionable options:

- For blocking issues: "Fix automatically" / "Fix manually" / "Skip and document"
- For suggestions: "Apply" / "Decline"
- This is only relevant in standalone mode — in chained mode, `camel-execute` owns the transition

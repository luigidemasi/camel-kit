## Agent Optimization: Claude Code

### Validation Progress Tracking

Use Claude Code's task tracking for real-time visibility during validation:

- `TaskCreate` for each validation dimension at the start (endpoint verification, constitution compliance, property audit, etc.)
- `TaskUpdate` to mark each dimension `in_progress` when starting and `completed` when done
- `TaskList` to report progress after each dimension completes

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

### Finding Clarification via AskUserQuestion

When a finding is ambiguous, use `AskUserQuestion` only to clarify the evidence or desired report scope. Do not offer to apply a fix from validation.

- Record every confirmed issue and its recommended correction in the validation report.
- Route changes belong to implementation handling; validation never modifies route or configuration files.
- In chained mode, this report-only validation is the terminal Phase 4 gate.

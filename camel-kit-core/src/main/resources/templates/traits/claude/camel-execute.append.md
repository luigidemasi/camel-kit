## Agent Optimization: Claude Code

### Parallel Task Dispatch

Use the `Agent` tool with `run_in_background: true` for independent implementation tasks within the same wave. When `{COMMAND_PREFIX} plan analyze` identifies parallel waves, dispatch all tasks in a wave simultaneously:

- Set `description` to a 3-5 word summary of the task
- Include the full task text, design spec context, and project config in the `prompt`
- Use `run_in_background: true` for all tasks in a wave except the last one
- Wait for all background agents to complete before proceeding to the next wave

### Worktree Isolation

Before dispatching implementation tasks, use `EnterWorktree` to create an isolated copy of the project:

- This prevents parallel subagents from conflicting on file writes
- Use `ExitWorktree` with `action: "keep"` after all tasks complete so the user can review
- Only use worktrees when dispatching 2+ parallel tasks — single-task waves don't need isolation

### Scheduled Build Verification

For long implementation sessions (3+ tasks), use `CronCreate` to schedule a periodic build check:

- Schedule `{COMMAND_PREFIX} verify --quick` every 15 minutes during execution
- This catches regressions early, before the full verification phase
- Delete the cron job (`CronDelete`) when execution completes

### Environment Probe Pacing

The environment probe runs before dispatching implementers. Dependency resolution typically takes 30-90s — well within the 300s cache TTL, so foreground execution is fine for the single-command case. Use background only when parallelizing Maven + Docker (see the environment-probe trait for details).

### State Tracking

Use Claude Code's task tracking tools for real-time progress visibility:

- `TaskCreate` for each implementation task at the start of execution
- `TaskUpdate` to mark tasks `in_progress` when the implementer subagent is dispatched
- `TaskUpdate` to mark tasks `completed` after both review stages pass
- `TaskList` to report progress after each wave completes
- Track re-plan rounds as tasks when the re-plan loop triggers

### Claude Code Dispatch Map

When dispatching subagents during execution, use this map to set the `subagent_type` and `model` parameters on the `Agent` tool call. Read the persona from the task's `**Agent:**` field in the implementation plan.

| Persona | `subagent_type` | `model` |
|---------|----------------|---------|
| `integration-architect` | `Plan` | `opus` |
| `implementation-engineer` | `general-purpose` | `sonnet` |
| `migration-specialist` | See resolution rule below | `opus` |
| `test-engineer` | `general-purpose` | `sonnet` |
| `spec-compliance-reviewer` | `general-purpose` | `sonnet` |
| `code-quality-reviewer` | `code-simplifier` | `opus` |

**Migration-specialist resolution:** Check the task's `**Files:**` section:
- If it contains only "Read" or "Analyze" entries (no "Create" or "Modify") → `Explore`
- If it contains any "Create" or "Modify" entries → `general-purpose`
- If the `**Files:**` section is absent or ambiguous → `general-purpose` (safe default)

**Always set both parameters.** Example:

```text
Agent({
  subagent_type: "general-purpose",
  model: "sonnet",
  description: "Task 3: Purchase ingestion route",
  prompt: "...",
  run_in_background: true
})
```

If a persona is not in the map, fall back to `general-purpose` with `model: "sonnet"`.

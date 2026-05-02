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

The environment probe runs before dispatching implementers. Use pacing tools to avoid blocking:

- Run `{MAVEN_CMD} dependency:resolve` via `Bash` with `run_in_background: true`
- Use `ScheduleWakeup` with `delaySeconds: 90` to wait for dependency resolution
- See `camel-execute/guides/environment-probe.md` trait for detailed probe optimizations

### State Tracking

Use Claude Code's task tracking tools for real-time progress visibility:

- `TaskCreate` for each implementation task at the start of execution
- `TaskUpdate` to mark tasks `in_progress` when the implementer subagent is dispatched
- `TaskUpdate` to mark tasks `completed` after both review stages pass
- `TaskList` to report progress after each wave completes
- Track re-plan rounds as tasks when the re-plan loop triggers

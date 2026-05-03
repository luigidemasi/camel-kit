## Agent Optimization: Claude Code

### Worktree Isolation

At the start of the ship pipeline, use `EnterWorktree` to isolate all generated artifacts:

- Create the worktree before Stage 0 (Brainstorm)
- All stages execute in the worktree
- On successful completion, use `ExitWorktree` with `action: "keep"` so the user can review
- On failure, report the worktree path so the user can inspect partial results

### Build Health Monitoring

Use `CronCreate` to schedule periodic build health checks during Stage 2 (Execute):

- Schedule `mvn compile -q` every 10 minutes
- If the build breaks, pause execution and report immediately (regardless of --ask level)
- Delete the cron job after Stage 2 completes

### Oversight via AskUserQuestion

When the pipeline needs to pause for user input (based on --ask level), use `AskUserQuestion` with structured options:

- For design spec approval: present summary with "Approve" / "Request changes" / "Abort pipeline" options
- For execution failures: present the error with "Auto-fix" / "Manual fix" / "Skip task" / "Abort" options
- For re-plan escalation: present the Escalation Report from `re-plan-loop.md` (round summaries, failure details, affected TDDs) with "Resume after manual fix" / "Abort pipeline" options
- Always include an "Abort pipeline" option — the user should be able to stop at any point

### Smart Pacing with ScheduleWakeup

During Stage 2 (Execute), use `ScheduleWakeup` for dynamic loop pacing:

- After dispatching a wave of implementation tasks, use `ScheduleWakeup` with `delaySeconds: 270` (4.5 min, stays in cache)
- Set `reason` to describe what you're waiting for: "waiting for implementation wave 2 (3 tasks) to complete"

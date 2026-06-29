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
- For re-plan escalation: present the Escalation Report from `re-plan-loop.md` (round summaries, failure details, affected design spec sections) with "Resume after manual fix" / "Abort pipeline" options
- Always include an "Abort pipeline" option — the user should be able to stop at any point

### Smart Pacing with ScheduleWakeup

During Stage 2 (Execute), use `ScheduleWakeup` for dynamic loop pacing:

- After dispatching a wave of implementation tasks, use `ScheduleWakeup` with `delaySeconds: 270` (4.5 min, stays in cache)
- Set `reason` to describe what you're waiting for: "waiting for implementation wave 2 (3 tasks) to complete"

### Parallel Reviewer Fan-Out at Stamp Gate

At the Stamp Gate, dispatch three reviewer subagents in parallel using the `Agent` tool:

```text
# Dispatch all three reviewers in a single message (parallel)
Agent({
  subagent_type: "general-purpose",
  model: "sonnet",
  description: "Stamp: spec consistency",
  prompt: "[spec-compliance-reviewer persona + all route files + cross-route consistency focus]",
  run_in_background: true
})

Agent({
  subagent_type: "code-simplifier",
  model: "opus",
  description: "Stamp: code quality",
  prompt: "[code-quality-reviewer persona + all route files + constitution + review-only override]",
  run_in_background: true
})

Agent({
  subagent_type: "code-simplifier",
  model: "opus",
  description: "Stamp: security scan",
  prompt: "[code-quality-reviewer persona (security-only mode) + all route files + CVE check instructions]",
  run_in_background: false  # last one — blocks until all complete
})
```

Wait for all three to complete, then merge their reports into the Stamp Gate summary.

### Catalog Research Dispatch

Before implementation waves in Stage 2, dispatch the `catalog-researcher` via:

```text
Agent({
  subagent_type: "Explore",
  model: "sonnet",
  description: "Catalog verification batch",
  prompt: "[catalog-researcher persona + artifact list + runtime + platformBom]"
})
```

### Knowledge Query Dispatch

When any stage needs documentation context, dispatch `knowledge-researcher` via:

```text
Agent({
  subagent_type: "Explore",
  model: "sonnet",
  description: "Knowledge: [topic]",
  prompt: "[knowledge-researcher persona + specific question]"
})
```

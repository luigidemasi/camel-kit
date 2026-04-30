## Agent Optimization: Claude Code

### Dynamic Build Wait

Instead of polling in a tight loop after running `mvn verify`, use `ScheduleWakeup`:

- After starting `mvn verify` in the background (via `Bash` with `run_in_background: true`), call `ScheduleWakeup` with `delaySeconds: 180` and `reason: "waiting for Maven verify to complete"`
- On wake, check the background task output. If still running, schedule another wakeup at 90s.
- This avoids burning cache windows on repeated polling.

### Fix Attempt Tracking

Use `TaskCreate` to track each fix attempt in the verification loop:

- Create a task for each error found: "Fix: {error-taxonomy-category} — {brief description}"
- Mark `in_progress` when attempting the fix
- Mark `completed` when the re-verification passes
- If a fix fails after 3 rounds, leave the task as `in_progress` for the user to see

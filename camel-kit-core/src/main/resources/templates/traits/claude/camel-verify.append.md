## Agent Optimization: Claude Code

### Dynamic Retry Pacing

Use `ScheduleWakeup` for intelligent retry pacing in the verification loop:

- After kicking off a build (`mvn verify`), use `ScheduleWakeup` with `delaySeconds: 180` (3 min, stays in cache window) instead of polling
- Set `reason` to a specific description: "waiting for Maven build to complete"
- After a fix attempt, use a shorter delay (90s) since fixes are typically faster to verify

### Periodic Health Checks

Use `CronCreate` to schedule background health monitoring during extended verification sessions:

- Schedule `git diff --stat` every 10 minutes to track cumulative changes
- This helps detect runaway fix loops that modify too many files
- Delete the cron job when verification completes or after 3 fix rounds

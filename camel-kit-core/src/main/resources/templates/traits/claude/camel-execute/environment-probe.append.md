## Agent Optimization: Claude Code

### Single-Command Probe

For dependency resolution alone, run in the foreground — `{MAVEN_CMD} dependency:resolve -q` typically completes in 30-90s, well within the 300s cache TTL. No background task or wakeup needed.

### Docker Health Polling

When waiting for Docker services to become healthy, avoid tight polling loops:

- Start `docker compose up -d`, then use `ScheduleWakeup` with `delaySeconds: 60` and `reason: "waiting for Docker services to become healthy"`
- On wake, check `docker compose ps` once — if not healthy, try one more 30s wake
- This avoids burning cache windows on repeated `docker compose ps` calls

### Parallel Probe Checks

If the skeleton has both Maven dependencies and Docker services, run both checks in parallel:

- Dispatch `{MAVEN_CMD} dependency:resolve -q` via `Bash` with `run_in_background: true` — note the returned task ID
- Run `docker compose up -d` in the foreground (faster, non-blocking)
- Use `ScheduleWakeup` with `delaySeconds: 90` and `reason: "waiting for parallel Maven + Docker probe"`
- On wake, use `TaskOutput` with the task ID and `block: false` to check Maven status:
  - If completed with exit code 0 → Maven passed
  - If completed with non-zero exit code → Maven failed, capture stderr for classification
  - If still running → re-schedule one more wakeup at 60s. If still running after that, classify as timeout (mechanical failure)
- Only proceed to runtime startup after BOTH Maven and Docker checks are confirmed complete

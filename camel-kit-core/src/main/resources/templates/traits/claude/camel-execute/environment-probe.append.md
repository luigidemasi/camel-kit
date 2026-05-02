## Agent Optimization: Claude Code

### Background Dependency Resolution

Run the Maven dependency resolution in the background to avoid blocking:

- Use `Bash` with `run_in_background: true` for `{MAVEN_CMD} dependency:resolve -q`
- Use `ScheduleWakeup` with `delaySeconds: 90` and `reason: "waiting for dependency resolution to complete"`
- On wake, check the background task exit code

### Docker Health Polling

When waiting for Docker services to become healthy, avoid tight polling loops:

- Start `docker compose up -d`, then use `ScheduleWakeup` with `delaySeconds: 60` and `reason: "waiting for Docker services to become healthy"`
- On wake, check `docker compose ps` once — if not healthy, try one more 30s wake
- This avoids burning cache windows on repeated `docker compose ps` calls

### Parallel Probe Checks

If the skeleton has both Maven dependencies and Docker services, run both checks in parallel:

- Dispatch `mvn dependency:resolve` via `Bash` with `run_in_background: true`
- Run `docker compose up -d` in the foreground (faster, non-blocking)
- Wait for both to complete before proceeding to runtime startup

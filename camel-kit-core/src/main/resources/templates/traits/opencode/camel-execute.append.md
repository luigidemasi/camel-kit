## Agent Optimization: OpenCode

### Step-Limited Subagents

Set `steps` limits on each subagent to prevent runaway execution:

- Implementation subagents: `steps: 100` (enough for complex route generation)
- Spec review subagents: `steps: 50` (review is read-heavy, fewer writes)
- Quality review subagents: `steps: 50`

If a subagent hits its step limit, report a warning and continue to the next task.

### Strategic Agent Selection

OpenCode provides two primary agents (`Build` for code generation, `Plan` for analysis) and two subagent types (`General` for multi-step tasks, `Explore` for read-only codebase search). When dispatching subagents:

- Implementation subagents: use `General` with full `edit` and `bash` permissions
- Review subagents: use `General` with read-focused permissions
- Codebase exploration: use `Explore` (read-only, no edit or bash access)
- Plan analysis: stays in the `Plan` primary agent (no subagent dispatch needed)

### Environment Probe Budget

The environment probe runs before task dispatch. Budget `steps: 30` for the probe phase within Stage 2 (Execute). The probe is lightweight (skeleton generation, dependency check, Docker health poll) and should not consume the main implementation budget.

If the probe triggers a re-plan loop, increase the effective Step 2 budget by `steps: 50` per re-plan round (max 3 rounds = +150 steps). Use the `General` agent type for re-planning since it involves both code reads and TDD edits.

### Test Execution Agent

For the verification phase (`camel test run`), use the `Build` agent type with `steps: 80` — enough for running Citrus tests, diagnosing failures, and applying fixes within the 15-iteration loop.

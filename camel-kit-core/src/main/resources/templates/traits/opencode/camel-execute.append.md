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

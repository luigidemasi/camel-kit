## Agent Optimization: OpenCode

### Step-Limited Subagents

Set `steps` limits on each subagent to prevent runaway execution:

- Implementation subagents: `steps: 100` (enough for complex route generation)
- Spec review subagents: `steps: 50` (review is read-heavy, fewer writes)
- Quality review subagents: `steps: 50`

If a subagent hits its step limit, report a warning and continue to the next task.

### Strategic Agent Type Selection

OpenCode provides built-in agent types. Map task types to optimal agents:

- Implementation tasks: use `Build` agent type (optimized for code generation)
- Plan analysis: use `Plan` agent type (optimized for structured planning)
- Review tasks: use `General` agent type (balanced read/write)
- Quick checks: use `Fast` agent type (minimal context, fast response)

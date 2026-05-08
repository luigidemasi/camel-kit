## Agent Optimization: Claude Code

When constructing the implementer subagent prompt, leverage Claude Code-specific features:

### Typed Dispatch

Set `subagent_type` and `model` from the Claude Code Dispatch Map (see `camel-execute.append.md`). For implementation tasks, the typical call is:

```text
Agent({
  subagent_type: "general-purpose",
  model: "sonnet",
  description: "Task N: [3-5 word summary]",
  prompt: "[full implementer prompt from implementer-context guide]",
  run_in_background: true
})
```

For `migration-specialist` implementation tasks (where the `**Files:**` section contains "Create" or "Modify"):

```text
Agent({
  subagent_type: "general-purpose",
  model: "opus",
  description: "Task N: [migration task summary]",
  prompt: "[full implementer prompt]",
  run_in_background: true
})
```

### Background Dispatch

Set `run_in_background: true` in the Agent tool call when this task is part of a parallel wave. This allows the orchestrator to dispatch the next independent task without waiting.

### NotebookEdit for Data Transforms

If the task involves data transformation (DataMapper, XSLT, Groovy), instruct the subagent to also create a test notebook:

- Add to the prompt: "After generating the transformation code, create a Jupyter notebook at `test/{flow-name}-transform-test.ipynb` with cells that execute the transformation on sample data and verify output."
- The subagent can use `NotebookEdit` to create and populate the notebook cells.

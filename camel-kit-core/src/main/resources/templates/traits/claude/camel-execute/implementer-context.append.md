## Agent Optimization: Claude Code

When constructing the implementer subagent prompt, leverage Claude Code-specific features:

### Background Dispatch

Set `run_in_background: true` in the Agent tool call when this task is part of a parallel wave. This allows the orchestrator to dispatch the next independent task without waiting.

### NotebookEdit for Data Transforms

If the task involves data transformation (DataMapper, XSLT, Groovy), instruct the subagent to also create a test notebook:

- Add to the prompt: "After generating the transformation code, create a Jupyter notebook at `test/{flow-name}-transform-test.ipynb` with cells that execute the transformation on sample data and verify output."
- The subagent can use `NotebookEdit` to create and populate the notebook cells.

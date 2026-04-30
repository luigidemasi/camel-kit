## Agent Optimization: Claude Code

### Parallel Test Suite Dispatch

Use the `Agent` tool to dispatch independent test generation tasks in parallel:

- If the design spec has multiple flows, dispatch one test-generation subagent per flow
- Use `run_in_background: true` for all but the last flow
- Each subagent should generate tests for one flow only

### Data Transform Testing with Notebooks

When the integration includes data transformations (DataMapper, XSLT, Groovy scripts), use `NotebookEdit` to create a Jupyter notebook for interactive testing:

- Create a notebook in the project's `test/` directory
- Add cells that execute the transformation with sample input data
- Add assertion cells that verify the output matches expected results
- This is especially useful for complex transformations where visual inspection helps

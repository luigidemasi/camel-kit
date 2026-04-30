## Agent Optimization: Qwen Code

### Serial Task Dispatch

Qwen Code's `task` tool dispatches one subagent at a time (serial only). Adapt wave-based execution:

- Even if `{COMMAND_PREFIX} plan analyze` identifies parallel waves, execute ALL tasks sequentially
- Process tasks in dependency order: complete all dependencies before starting a dependent task
- Within a wave, process tasks in the order they appear in the plan

### Progress Tracking via todo_write

Use `todo_write` to maintain a visible progress list:

- At the start of execution, write all tasks as unchecked items
- Check off each task as it completes both review stages
- This provides the user with a real-time progress view since parallel dispatch isn't available

### Explicit Context Passing

Since subagents are serial, each subagent can read the outputs of all previous subagents. Include explicit file paths to prior outputs in each subagent prompt — don't assume the subagent will discover them.

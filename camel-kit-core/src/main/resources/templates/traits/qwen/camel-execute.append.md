## Agent Optimization: Qwen Code

### Serial Task Dispatch

Qwen Code's `task` tool dispatches one subagent at a time (serial only). Adapt wave-based execution:

- Even if `{COMMAND_PREFIX} plan analyze` identifies parallel waves, execute ALL tasks sequentially
- Process tasks in dependency order: complete all dependencies before starting a dependent task
- Within a wave, process tasks in the order they appear in the plan

### Progress Tracking via todo_write

Use `todo_write` to maintain a visible progress list:

- At the start of execution, write all tasks as unchecked items
- Check off each task as it completes spec compliance and quality review
- This provides the user with a real-time progress view since parallel dispatch isn't available

### Explicit Context Passing

Since subagents are serial, each subagent can read the outputs of all previous subagents. Include explicit file paths to prior outputs in each subagent prompt — don't assume the subagent will discover them.

### Environment Probe Checkpoint

Before dispatching implementation tasks, the environment probe runs. Since Qwen executes serially:

- Run the probe as the first task in the execution sequence
- Write a checkpoint to `.camel-kit/ship-state.json` after the probe passes
- If the probe triggers a re-plan loop, track each re-plan round as a separate todo item
- Resume from post-probe if interrupted and probe already passed

### Re-Plan as Serial Task

When re-planning triggers, treat it as an inserted serial task:

- Add a new todo item: "Re-plan round {N}: {failure description}"
- Complete the re-plan before resuming implementation tasks
- After re-plan, the affected implementation tasks must be re-dispatched

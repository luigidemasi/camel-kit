## Agent Optimization: Qwen Code

### Serial Pipeline Execution

Execute all pipeline stages sequentially. Since Qwen Code dispatches one subagent at a time:

- Complete each stage fully before starting the next
- Do not attempt to parallelize any part of the pipeline
- Use checkpoints between stages to save state

### State Tracking via todo_write

Maintain a pipeline progress list using `todo_write`:

- Write a todo item for each stage: "Stage 0: Brainstorm", "Stage 1: Plan", "Stage 2: Execute", "Stage 3: Verify", "Final: Stamp"
- Check off each stage as it completes
- If the pipeline is interrupted, the todo list shows which stages are done

### Checkpoint Between Stages

After each stage completes, write the state to `.camel-kit/ship-state.json` AND update the todo list. This dual-write ensures state is recoverable from either mechanism.

### Plan Auto-Progression

Stage 1 (Plan) auto-proceeds to Stage 2 (Execute). Do not wait for plan approval — update the todo list to check off "Stage 1: Plan" and immediately proceed to "Stage 2: Execute".

### Re-Plan Checkpoints

If the re-plan loop triggers during Stage 2:

- Add temporary todo items: "Re-plan round 1", "Re-plan round 2", "Re-plan round 3"
- Write state to `.camel-kit/ship-state.json` after each round
- If interrupted during re-plan, resume from the correct round using saved state

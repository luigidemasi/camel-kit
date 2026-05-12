## Agent Optimization: Qwen Code

### Fork-Based Background Tasks

Qwen Code's dual dispatch enables parallel background work via the **fork model**:

- **Named subagents** (`subagent_type` provided): clean context, parent blocks until completion. Use for implementation tasks that need focused context.
- **Forks** (`subagent_type` omitted): inherit parent's full conversation context, run in background while parent continues. Use for research isolation and review tasks.

### Catalog Research via Fork

Before dispatching implementers for a wave, fork a catalog verification task:

```
# Fork runs in background — parent continues to next instruction
Agent({
  prompt: "[catalog-researcher persona + artifact list + runtime + platformBom]"
  // No subagent_type → fork mode, inherits parent context, runs in background
})
```

The fork verifies all MCP catalog artifacts and writes results to a temporary file. The parent reads the results before dispatching implementers.

**DashScope cache benefit:** The fork shares the parent's exact system prompt prefix, so both the fork and parent hit the same cache — saving 80%+ tokens.

### Implementation Task Dispatch

Named subagent dispatch for implementation tasks (parent blocks until complete):

```
Agent({
  subagent_type: "camel-implementer",
  prompt: "[full task text + design spec + pre-verified catalog summary]"
})
```

Execute tasks sequentially within a wave (named subagents are blocking). Across waves, respect dependency ordering.

### Fork-Based Review Parallelism

After an implementer completes, use forks for the doubt cycle spot-checks while preparing the spec review prompt:

```
# Fork 1: spot-check component options via MCP (background)
Agent({
  prompt: "[doubt cycle — verify 2-3 component options]"
})

# Meanwhile, prepare spec review context (foreground)
# Read generated files, load design spec section...

# Then dispatch spec review as named subagent (blocking)
Agent({
  subagent_type: "camel-validator",
  prompt: "[spec compliance review]"
})
```

**Fork recursion constraint:** Fork children cannot create further forks (enforced via `AsyncLocalStorage`). Named subagents dispatched from a fork are fine.

### Progress Tracking via todo_write

Use `todo_write` to maintain a visible progress list:

- At the start of execution, write all tasks as unchecked items
- Check off each task as it completes spec compliance and quality review
- This provides the user with a real-time progress view

### Explicit Context Passing

Include explicit file paths to prior outputs in each subagent prompt — don't assume the subagent will discover them.

### Environment Probe Checkpoint

- Run the probe as the first task in the execution sequence
- Write a checkpoint to `.camel-kit/ship-state.json` after the probe passes
- If the probe triggers a re-plan loop, track each re-plan round as a separate todo item

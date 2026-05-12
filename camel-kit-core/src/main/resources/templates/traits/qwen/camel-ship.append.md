## Agent Optimization: Qwen Code

### Fork-Based Parallel Reviews at Stamp Gate

At the Stamp Gate, use the fork model to run reviewers in parallel:

```text
# Fork 1: spec consistency (background)
Agent({
  prompt: "[spec-compliance-reviewer persona + all route files + cross-route focus]"
})

# Fork 2: security scan (background)
Agent({
  prompt: "[code-quality-reviewer persona (security-only) + all route files + CVE check]"
})

# Named subagent: code quality (foreground, blocks)
Agent({
  subagent_type: "camel-validator",
  prompt: "[code-quality-reviewer persona + all route files + constitution]"
})
```

Fork 1 and Fork 2 run in parallel while the named quality review blocks. After the quality review completes, read fork results from their output. Merge all three reports.

**Cache benefit:** All forks share the same system prompt prefix → DashScope prompt caching saves 80%+ tokens across concurrent reviews.

### Sequential Pipeline Stages

Execute pipeline stages sequentially — each stage must complete before the next starts:

- Stage 0 (Brainstorm): direct
- Stage 1 (Plan): direct, auto-proceeds to Stage 2
- Stage 2 (Execute): dispatches implementation tasks, uses forks for research isolation
- Stage 3 (Verify): direct

### State Tracking via todo_write

Maintain a pipeline progress list using `todo_write`:

- Write a todo item for each stage: "Stage 0: Brainstorm", "Stage 1: Plan", "Stage 2: Execute", "Stage 3: Verify", "Final: Stamp"
- Check off each stage as it completes
- If the pipeline is interrupted, the todo list shows which stages are done

### Checkpoint Between Stages

After each stage completes, write the state to `.camel-kit/ship-state.json` AND update the todo list. This dual-write ensures state is recoverable from either mechanism.

### Catalog and Knowledge Research via Fork

During Stage 2 (Execute), use forks for research isolation:

- Fork a catalog-researcher task before each implementation wave
- Fork knowledge-researcher queries when documentation context is needed
- Forks run in background, parent continues with preparation work

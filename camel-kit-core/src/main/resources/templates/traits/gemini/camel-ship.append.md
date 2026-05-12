## Agent Optimization: Gemini CLI

### Parallel Reviewer Fan-Out at Stamp Gate

Gemini's scheduler batches parallel tool calls automatically. At the Stamp Gate, invoke all three reviewers in the same turn:

```text
# In a single response, call invoke_subagent three times:
invoke_subagent(agent_name="camel-validator", prompt="[spec consistency review — cross-route]")
invoke_subagent(agent_name="camel-validator", prompt="[code quality review — constitution + anti-patterns]")
invoke_subagent(agent_name="camel-validator", prompt="[security scan — CVE + credential check]")
```

The scheduler's `Promise.all()` executes all three concurrently. Each reviewer returns a structured report. Merge the three reports into the Stamp Gate summary.

Include `timeout_mins: 20` in each delegation to prevent runaway reviews.

### Named Agent Pipeline

Chain the pipeline using named agent delegation:

- Stage 0 (Brainstorm): self (main agent orchestrates interview directly)
- Stage 1 (Plan): self (plan generation stays in main context)
- Stage 2 (Execute): delegate wave execution to `camel-implementer` agents — scheduler parallelizes within-wave dispatch
- Stage 3 (Validate): self (main agent runs static quality analysis directly — no subagent needed for read-only validation)

### State Persistence via Memory

Use `save_memory` to persist pipeline state between stages:

- Key: `camel-kit:ship:state`
- Value: JSON string matching the `.camel-kit/pipeline.json` format
- This provides an additional persistence mechanism alongside the file-based state

### Batch Context Loading

At each stage transition, use `read_many_files` to load all artifacts from previous stages in one call:

- Before Plan: load design-spec.md
- Before Execute: load design-spec.md + implementation-plan.md
- Before Validate: load design-spec.md + all generated route files

### Catalog and Knowledge Research

Before implementation waves in Stage 2:

- Delegate catalog verification to `camel-validator` with catalog-researcher persona
- Delegate knowledge queries to `camel-validator` with knowledge-researcher persona
- Both can be invoked in the same turn for scheduler parallelization

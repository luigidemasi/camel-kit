## Agent Optimization: Gemini CLI

### Named Agent Pipeline

Chain the pipeline using named agent delegation:

- Stage 0 (Brainstorm): self (main agent orchestrates interview directly)
- Stage 1 (Plan): self (plan generation stays in main context)
- Stage 2 (Execute): delegate wave execution to `camel-implementer` agents
- Stage 3 (Verify): delegate to `camel-validator` with `timeout_mins: 20`

### State Persistence via Memory

Use `save_memory` to persist pipeline state between stages:

- Key: `camel-kit:ship:state`
- Value: JSON string matching the `.camel-kit/ship-state.json` format
- This provides an additional persistence mechanism alongside the file-based state

### Batch Context Loading

At each stage transition, use `read_many_files` to load all artifacts from previous stages in one call:

- Before Plan: load design-spec.md
- Before Execute: load design-spec.md + implementation-plan.md
- Before Verify: load design-spec.md + all generated route files

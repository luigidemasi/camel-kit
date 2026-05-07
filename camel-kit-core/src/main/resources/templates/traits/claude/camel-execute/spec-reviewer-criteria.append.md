## Agent Optimization: Claude Code

### Typed Review Dispatch

When dispatching the spec-compliance reviewer subagent, use the `Agent` tool with `subagent_type` and `model` from the Claude Code Dispatch Map:

```text
Agent({
  subagent_type: "general-purpose",
  model: "sonnet",
  description: "Spec review: Task N",
  prompt: "[full spec-reviewer prompt from spec-reviewer-criteria guide]"
})
```

The spec-compliance reviewer uses `general-purpose` because it needs to read full files for field-by-field comparison against the TDD. The `Explore` type reads excerpts and explicitly warns against code review, making it unsuitable.

Do NOT use `run_in_background` for review subagents — the orchestrator must wait for the review result before proceeding (spec review gates quality review per Iron Law 4).

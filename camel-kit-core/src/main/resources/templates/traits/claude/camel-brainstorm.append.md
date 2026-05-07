## Agent Optimization: Claude Code

### Structured Design Interviews

Use `AskUserQuestion` with structured multiple-choice options for design decisions. Structure each question with:
- `header`: Short label (max 12 chars) for the decision category
- `options`: 2-4 concrete choices with descriptions
- `preview`: Use preview fields for side-by-side comparison of architecture options

This produces cleaner interview transcripts than open-ended text prompts.

### Pattern Research

Use `WebSearch` to research integration patterns when the user describes unfamiliar source/target systems. Use `WebFetch` to retrieve API documentation URLs the user provides.

### Interview Progress Tracking

Use `TaskCreate` to create a task for each interview phase (discovery, component selection, design assembly). Mark each as `in_progress` when entering the phase and `completed` when exiting. This gives the user visible progress.

### Typed Subagent Dispatch

When dispatching subagents during brainstorming, use the Claude Code Dispatch Map:

**Integration architect** (design analysis, component selection, flow design):

```text
Agent({
  subagent_type: "Plan",
  model: "opus",
  description: "Architect: [flow or design topic]",
  prompt: "[architect prompt with persona from agents/integration-architect.md]"
})
```

The `Plan` type provides architectural focus with no Edit/Write access — the architect analyzes and returns design output, the orchestrator writes the design doc.

**Migration specialist** (source artifact scanning, component mapping):

```text
Agent({
  subagent_type: "Explore",
  model: "opus",
  description: "Migration scan: [source platform]",
  prompt: "[migration prompt with persona from agents/migration-specialist.md]"
})
```

The `Explore` type provides fast read-only search — scanning source artifacts and mapping components without risking file modifications. Use `Explore` only for analysis tasks. If the migration specialist needs to write files (implementation tasks during `camel-execute`), the execute trait switches to `general-purpose`.

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

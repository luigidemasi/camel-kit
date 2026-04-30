## Agent Optimization: Claude Code

### Typed Review Dispatch

When dispatching the spec-compliance reviewer subagent, use the `Agent` tool with `subagent_type: "superpowers:code-reviewer"` if available. This gives the reviewer access to code-review-specific tools and prompts.

If `subagent_type` is not recognized (the reviewer is a general agent), fall back to the standard dispatch with a detailed review-focused prompt.

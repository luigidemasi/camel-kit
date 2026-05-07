## Agent Optimization: Claude Code

### Typed Quality Review Dispatch

When dispatching the code-quality reviewer subagent, use `code-simplifier` type with `opus` model. The `code-simplifier` type provides built-in quality analysis capabilities (clarity, consistency, maintainability focus) that align with the quality reviewer's role.

```text
Agent({
  subagent_type: "code-simplifier",
  model: "opus",
  description: "Quality review: Task N",
  prompt: "[full quality-reviewer prompt from quality-reviewer-criteria guide]"
})
```

### Review-Only Override

The `code-simplifier` type has a built-in bias toward modifying files. The quality reviewer must NEVER modify files. Prepend this to the quality reviewer's prompt:

```text
<HARD-RULE>
REVIEW-ONLY MODE. You are dispatched as a code-simplifier for your quality analysis
capabilities. Do NOT use Edit, Write, or any file-modification tool. Your output is a
structured review report. If you identify improvements, REPORT them — do not apply them.
</HARD-RULE>
```

### Safety Fallback

After the quality reviewer subagent returns, verify no files were modified:

1. Run `git diff --stat`
2. If output is empty — proceed normally
3. If files were modified:
   a. Revert: `git checkout -- <modified files>`
   b. Log: "Quality reviewer modified files despite review-only override — re-dispatching as general-purpose"
   c. Re-dispatch the same review with `subagent_type: "general-purpose"` and `model: "opus"`
   d. Use the re-dispatched result

Do NOT use `run_in_background` for review subagents — the orchestrator must wait for the review result before proceeding.

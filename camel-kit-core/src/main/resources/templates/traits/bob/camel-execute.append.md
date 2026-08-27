## Agent Optimization: IBM Bob

### Mode-Based Execution

Remain in the `camel-execute-mode` custom mode while orchestrating the plan. Perform
individual implementation steps in the same session under the gate's task instructions;
do not replace the orchestrator mode with `camel-implement-mode`, because the execute
mode owns the full review and verification sequence. It loads:

- Implementation-specific rules from `.bob/rules-camel-implement-mode/implementation.md`
- Implementation instructions from `.bob/skills/camel-implement/guides/orchestrator.md`
- The execute gate validates that each task passes the adversarial, spec, and quality sequence before marking complete

### Gate Validation Between Tasks

The generated `.bob/skills/camel-execute/SKILL.md` enforces the complete ordered review stack per task:

- Same-session adversarial critic lenses, with the lack of fresh-context isolation recorded
- Per-task spec compliance review (does the output match the design spec?)
- Per-task code quality review (constitution compliance, security, anti-patterns)
- Final cross-cutting review across all routes (naming consistency, duplicate route IDs, orphaned properties)

Let the gate system handle validation rather than implementing manual checks.

### Precise Code Insertion

Use `insert_content` instead of full file writes when adding code to existing files:

- For `application.properties`: insert new properties at the end without rewriting the file
- For `pom.xml`: insert dependency blocks at the correct position
- This prevents accidentally overwriting user-added content in existing files

### Environment Probe via Mode Rules

Before implementing tasks, the environment probe runs. In Bob's mode system:

- The probe executes within the current `camel-execute-mode` (no separate mode needed)
- The current `.bob/skills/camel-execute/SKILL.md` validates probe results before allowing task dispatch
- If the probe finds architectural failures, the re-plan loop runs within the same mode context

### Re-Plan Loop Handling

When architectural failures trigger re-planning:

- The re-plan modifies design spec sections, which are editable in `camel-execute-mode`
- Gate validation after re-plan should re-check the probe results
- Max 3 re-plan rounds — if all fail, the gate reports a blocker regardless of oversight level

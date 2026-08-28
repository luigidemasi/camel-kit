## Dispatch

Keep the complete user-invoked workflow in its selected primary session. This preserves questions, approval, command
arguments, and phase handoffs. Use OpenCode's `task` tool only for a bounded step, with the exact lowercase
`subagent_type`:

- `implementer` for general implementation or fixes
- `migrator` when the plan role is `migration-specialist`
- `tester` when the plan role is `test-engineer`
- `researcher` for catalog, Knowledge, or source research
- `reviewer` for adversarial, specification, and quality reviews
- `planner` for the bounded re-plan loop
- `brainstormer` only for bounded non-interactive discovery or design analysis
- `validator` only for a bounded report-producing validation task

Do not delegate a complete brainstorm, plan, migrate, start, or chained workflow to a child. OpenCode's `task` call must
include `subagent_type`; omit `background` for every result that gates the next step. Agent definitions own their step
limits, so do not pass a per-call `steps` value.

Include in each task prompt:
- The complete selected persona from `.opencode/camel-kit-personas/` when the task or review declares one
- The flow/task name
- Camel version (from .camel-kit/config.properties)
- User answers relevant to this step
- File paths of prior step outputs (let the sub-agent read them)

### Fallback
If sub-agent dispatch is unavailable, read the guide directly into the primary context and execute its instructions inline.

## Dispatch

Run the user-invoked workflow in the primary session. This preserves `ask_user_question`, slash-command arguments,
approval gates, and chained phase handoffs. Delegate only bounded leaf work:

- `camel-implementer` for one implementation or fix task
- `camel-reviewer` for one read-only catalog, adversarial, specification, or quality review role
- `camel-tester` for one isolated test task
- `camel-validator` for one bounded, read-only validation analysis; the primary session writes the returned report
- a top-level `fork` for optional bounded factual research that benefits from inherited context

Never delegate the complete brainstorm, plan, migrate, execute, or start workflow to a child agent. Qwen child agents
cannot ask the user questions or execute the primary session's next slash-command handoff. Include in each leaf delegation:

- The flow/task name
- Camel version (from .camel-kit/config.properties)
- User answers relevant to this step
- File paths of prior step outputs (let the sub-agent read them)

### Fallback
If the named leaf is not available, read the guide directly into the primary context and execute its instructions inline.

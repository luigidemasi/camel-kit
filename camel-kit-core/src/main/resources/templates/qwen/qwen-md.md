# Camel-Kit — Qwen

See `AGENTS.md` for skill routing, iron laws, and project rules.

## Workflow and Leaf Agents

Slash-command workflows run in the primary session so interviews, approvals, arguments, and phase handoffs remain available.
They may delegate bounded work to these generated agents:

- **camel-implementer** — route implementation and code generation
- **camel-reviewer** — read-only catalog research and isolated review roles
- **camel-validator** — read-only validation analysis that returns its complete report to the primary session
- **camel-tester** — test creation and execution

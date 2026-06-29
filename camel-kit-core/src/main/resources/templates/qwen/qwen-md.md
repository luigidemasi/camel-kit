# Camel-Kit — Qwen

See `AGENTS.md` for skill routing, iron laws, and project rules.

## Sub-Agent Pipeline

Tasks are automatically delegated to specialized sub-agents based on the type of work:
- **camel-brainstormer** — discovery and requirements gathering
- **camel-planner** — implementation planning with design-spec decomposition
- **camel-implementer** — route implementation and code generation
- **camel-validator** — quality validation and analysis
- **camel-tester** — test creation and execution
- **camel-migrator** — migration from other platforms
- **camel-executor** — orchestrated plan execution

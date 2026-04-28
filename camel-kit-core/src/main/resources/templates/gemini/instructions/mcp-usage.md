# MCP Usage

See `.camel-kit/config.properties` for Camel version and runtime configuration.

## Graph Availability

Run `{COMMAND_PREFIX} graph stats`. If exit code 0, graph is available. If non-zero, skip graph-dependent steps silently. Graph enhances, never gates.

## Graph Commands

- `{COMMAND_PREFIX} graph stats` — check graph availability
- `{COMMAND_PREFIX} graph generate` — rebuild project graph
- `{COMMAND_PREFIX} graph project-norms` — get project norms for validation

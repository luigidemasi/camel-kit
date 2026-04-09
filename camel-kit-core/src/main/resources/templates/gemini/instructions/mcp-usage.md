# MCP Usage

See `.camel-kit/config.yaml` for Camel version and runtime configuration.

## Graph Availability

Run `{commandPrefix} graph stats`. If exit code 0, graph is available. If non-zero, skip graph-dependent steps silently. Graph enhances, never gates.

## Graph Commands

- `{commandPrefix} graph stats` — check graph availability
- `{commandPrefix} graph generate` — rebuild project graph
- `{commandPrefix} graph project-norms` — get project norms for validation

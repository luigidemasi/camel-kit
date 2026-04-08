# Graph Availability Check

> **Loaded by:** Per-skill graph guides (camel-validate, camel-implement, camel-test).
> **Purpose:** Common graph detection and fallback contract.

## Check

1. Check if `.camel-kit/project-graph.json` exists
2. If yes → graph tools are available. Proceed with graph-enhanced steps.
3. If no → skip all graph-enhanced steps. No warning needed — graph is optional.

## Fallback Rule

Every graph tool call must be wrapped in graceful fallback:
- If the tool returns `{"available":false,...}` → skip that step silently
- If the tool call fails (timeout, MCP not connected) → skip that step silently
- NEVER hard-stop a workflow because graph tools are unavailable

## Graph Tool Namespace

All graph tools are served by the `camel-graph` MCP server:
`graph_find`, `graph_neighbors`, `graph_path`, `graph_subgraph`,
`graph_stats`, `graph_route_flow`, `graph_impact`, `graph_route_topology`,
`graph_dead_code`

## Parallel Route Implementation

When implementing multiple routes, check route independence before dispatching:

1. Run `{COMMAND_PREFIX} graph route-topology` to get route connections
2. Identify routes with no shared endpoints or upstream/downstream dependencies
3. For independent routes: dispatch parallel subagents using the Agent tool — one subagent per route
4. For dependent routes: dispatch sequentially in dependency order

Example: If route-A produces to kafka topic-X and route-B consumes from kafka topic-X (dependent),
but route-C uses only timer+http (independent of A and B), dispatch route-C in parallel with
the sequential A→B chain.

Only parallelize when the graph confirms independence. When in doubt, dispatch sequentially.

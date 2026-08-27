## Agent Optimization: OpenCode

For pipeline invocation, load the complete `.opencode/camel-kit-personas/knowledge-researcher.md` role and dispatch it
through the `task` tool with `subagent_type: researcher`. Keep the call in the foreground. Standalone
`/camel-knowledge` remains in the primary session. Do not use an unregistered `knowledge-researcher` agent name.

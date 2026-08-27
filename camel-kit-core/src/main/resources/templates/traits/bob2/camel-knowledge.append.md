## Agent Optimization: IBM Bob 2

For pipeline invocation, load the complete `.bob/personas/knowledge-researcher.md` role and dispatch it through:

```yaml
spawn_subagent:
  name: "camel-reviewer"
  description: "[complete Knowledge Researcher role + exact query + Camel version + expected evidence]"
```

The generated `camel-reviewer` preset has read and MCP groups and cannot mutate the project. Standalone `/camel-knowledge` still runs inline. Do not dispatch an unregistered `knowledge-researcher` preset.

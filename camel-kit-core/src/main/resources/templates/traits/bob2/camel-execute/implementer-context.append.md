## Agent Optimization: IBM Bob 2

### Precise Edits

When generating implementation artifacts, prefer targeted Bob 2 edit tools:

- Use insert-style edits for additive changes to `application.properties`, XML dependency blocks, and route fragments.
- Use apply-style diffs for focused changes to existing files.
- Avoid full-file rewrites unless the file is newly generated or the task explicitly requires replacement.

### Subagent Boundary

When this guide is used inside a `camel-worker` implementation subagent, complete only the assigned task and return a concise summary. Do not call `spawn_subagent`; the parent Bob task owns orchestration and parallel dispatch.

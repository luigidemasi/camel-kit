## Agent Optimization: OpenCode

### Implementer Permissions

Configure the implementer subagent with full `edit` and `bash` permissions (matching the built-in Build primary agent's capabilities). Agent template frontmatter supports: `name`, `mode`, `description`, `steps`, and `permission.*` fields — agent type cannot be set at the template level.

### Step Limit

The generated `implementer` agent owns its `steps: 50` limit. The `task` tool does not accept a per-dispatch step budget; do not add one to a task call.

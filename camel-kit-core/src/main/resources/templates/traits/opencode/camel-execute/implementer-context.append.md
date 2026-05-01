## Agent Optimization: OpenCode

### Implementer Permissions

Configure the implementer subagent with full `edit` and `bash` permissions (matching the built-in Build primary agent's capabilities). Agent template frontmatter supports: `name`, `mode`, `description`, `steps`, and `permission.*` fields — agent type cannot be set at the template level.

### Step Limit

Set `steps: 100` for each implementer subagent. This provides enough steps for complex route generation (reading guides, generating YAML, generating properties, running validation) while preventing runaway execution.

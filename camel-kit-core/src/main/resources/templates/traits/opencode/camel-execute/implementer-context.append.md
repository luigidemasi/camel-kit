## Agent Optimization: OpenCode

### Build Agent Type

When constructing the implementer subagent, specify the `Build` agent type. This agent type is optimized for code generation tasks — it has higher edit and bash permissions than the default.

### Step Limit

Set `steps: 100` for each implementer subagent. This provides enough steps for complex route generation (reading guides, generating YAML, generating properties, running validation) while preventing runaway execution.

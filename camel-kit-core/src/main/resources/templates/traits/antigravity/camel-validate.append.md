## Agent Optimization: Google Antigravity

Use `invoke_subagent` with `camel-reviewer` for read-only validation analysis. Supply the exact shipped validation
guides, validated paths and version bindings, and separate canonical input envelopes. The reviewer returns the complete
report content and evidence; the primary conversation validates and writes the report to the path required by the skill.
Keep runtime command execution in the primary conversation or an explicitly authorized `camel-worker` verification task.

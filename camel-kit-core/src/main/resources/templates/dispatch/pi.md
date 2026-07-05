## Dispatch

Pi has no native subagent surface. Execute each guide step in the current Pi session, keeping the active plan,
artifact paths, Camel version from `.camel-kit/config.properties`, and verification evidence in view.

For large tasks, the user may launch separate Pi sessions manually, for example with `pi --tools read,grep,find,ls`
for read-only research. Treat those sessions as external helpers and bring their findings back into the main task
before changing project files.

### Fallback

If prompt templates or MCP adapter resources are unavailable, read the guide directly and continue with the same
workflow. Missing MCP evidence must be called out in the final verification notes.

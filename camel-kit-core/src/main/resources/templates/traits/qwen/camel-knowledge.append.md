## Agent Optimization: Qwen Code

For pipeline invocation, load the complete `.qwen/camel-kit-personas/knowledge-researcher.md` role and dispatch it to
`camel-reviewer` with `run_in_background: false`. Standalone `/camel-knowledge` remains in the primary session. Do not
use an unregistered `knowledge-researcher` subagent type.

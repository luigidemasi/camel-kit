## Agent Optimization: IBM Bob 2

Switch to `camel-brainstorm-mode` before starting discovery or design.

Use Bob 2 `explore` subagents for self-contained discovery during brainstorming:

- Spawn `explore` for codebase scans, migration source summaries, or dependency discovery when only a compact summary is needed.
- For a pipeline knowledge lookup, load `.bob/personas/knowledge-researcher.md` and spawn `camel-reviewer` with that complete role and query.
- Keep the parent Bob task focused on the interview, decisions, and design artifact assembly.
- Use `fork_context: true` only when an `explore` subagent needs prior user answers from the conversation.
- Do not ask multiple user questions in one turn.

Multiple independent `spawn_subagent` calls in one turn run in parallel, but use them only when each discovery task is independent.

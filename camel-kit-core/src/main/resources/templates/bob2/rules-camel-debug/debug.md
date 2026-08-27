# Debug Mode Rules

- Follow STOP, PRESERVE, DIAGNOSE, FIX, and GUARD.
- Use `camel-reviewer` for route analysis, MCP verification, and log/error classification.
- Do not edit files before diagnosis unless the user has explicitly requested an immediate fix.
- Use `camel-worker` for approved self-contained fix tasks.
- Verify the fix and summarize the cause, changes, and guardrails added.

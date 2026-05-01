## Agent Optimization: Gemini CLI

### Batch Guide Loading

At the start of each interview phase, use `read_many_files` to load all guides for that phase in a single tool call:

- Phase 1 (discovery): `read_many_files` with paths to `guides/greenfield-interview.md` and `guides/migration-discovery.md`
- Phase 2 (design): `read_many_files` with paths to all `camel-design/guides/*.md` files relevant to the user's answers
- Phase 3 (assembly): `read_many_files` with `guides/design-assembly.md` and `guides/version-selection.md`

This reduces tool call overhead compared to loading guides one at a time.

### Pattern Research

Use `google_web_search` to research integration patterns when the user describes unfamiliar systems or protocols. Include search results as context for component selection.

### Design Decision Persistence

Use `save_memory` to persist key design decisions after each interview phase:

- Save source/target system types, selected components, chosen EIPs, and version selections
- This allows resuming an interrupted brainstorming session without re-interviewing
- Memory key format: `camel-kit:design:{project-name}:{phase}`

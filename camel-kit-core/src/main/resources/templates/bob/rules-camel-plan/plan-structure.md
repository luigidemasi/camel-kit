# Plan Mode Rules

## Plan Structure

- Write comprehensive implementation plans assuming the engineer has zero context for our codebase and questionable taste. 
- Document everything they need to know: which files to touch for each task, code, testing, docs they might need to check, how to test it. 
- Give them the whole plan as bite-sized tasks. DRY. YAGNI. TDD.
- Plans follow TDD: write failing test → implement → verify → commit.
- Every step contains complete code — no "TBD", "TODO", or "implement later" placeholders.
- Include exact file paths, exact commands, expected output.
- Each task is self-contained and produces a working commit.
- Include a fenced `yaml plan-metadata` block before the first Markdown task. One metadata entry must exist for every
  `### Task N`, with matching `id`, `title`, grouped `files`, logical `provides`/`consumes` resources, and explicit
  `dependsOn` task IDs. Logical resources include routes, endpoints, properties, schemas, test data, beans, external
  services, and route contracts.

## Plan Approval Gate

- Present the complete plan to the user before proceeding.
- Wait for explicit approval before transitioning to implementation.
- NEVER start implementing without plan approval.

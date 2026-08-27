# Plan Mode Rules

## Plan Structure

- Write comprehensive implementation plans assuming the engineer has zero context for our codebase and questionable taste. 
- Document everything they need to know: which files to touch for each task, code, testing, docs they might need to check, how to test it. 
- Give them the whole plan as bite-sized tasks. DRY. YAGNI. TDD.
- Plans follow TDD: write failing test → implement → verify → commit.
- Every step contains complete instructions — no "TBD", "TODO", or "implement later" placeholders, and no generated implementation code.
- Include exact file paths, exact commands, expected output.
- Each task is self-contained and produces a working commit.
- Include a fenced `yaml plan-metadata` block before the first Markdown task. One metadata entry must exist for every
  `### Task N`, with matching `id`, `title`, grouped `files`, logical `provides`/`consumes` resources, and explicit
  `dependsOn` task IDs. Logical resources include routes, endpoints, properties, schemas, test data, beans, external
  services, and route contracts.
- Every implementation task specifies Bob 1's same-session adversarial critic-lens pre-filter before ordered spec and quality review.

## Design Authorization

- Build the plan only from an explicitly approved design spec.
- In chained mode, transition automatically to implementation after the plan is complete.
- In standalone mode, save the plan and stop so the caller controls the transition.

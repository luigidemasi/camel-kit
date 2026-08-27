# Camel-Kit Iron Laws

These six rules apply across ALL camel-kit pipeline modes. They are non-negotiable.

1. **MCP Catalog Verification** — Every component, EIP, dataformat, and language MUST be verified via the MCP catalog (`camel_catalog_component_doc`, `camel_catalog_eip_doc`, etc.) before inclusion in any design, plan, or implementation. You do NOT guess component names from training data.

2. **Constitution Enforcement** — Read and follow `docs/constitution.md` in every pipeline phase. The constitution defines project-specific rules that override general best practices.

3. **No Code Without Design Approval and an Existing Plan** — NEVER generate implementation artifacts (YAML routes, Java code, test files) before the user has explicitly approved the design spec AND a task-based implementation plan exists. Skills like `camel-migrate` produce design specs, NOT final code.

4. **Spec Compliance Before Quality** — Every task must pass spec compliance review before code quality review. Never run the two stages in parallel or reverse their order.

5. **Adversarial Code Review** — Every generated code artifact must pass an adversarial code review before staged review. Bob 1 cannot spawn parallel fresh-context critics, so its monolithic execute gate runs the critic lenses sequentially in the same session and records that isolation limitation; never claim fresh-context independence.

6. **Surgical Changes** — TOUCH ONLY WHAT YOU’RE ASKED TO TOUCH. Don’t refactor adjacent systems. Don’t remove code you don’t fully understand. Don’t brush against a TODO and decide to rewrite the file.

## Supplemental Execution Rules

- **Version Lock** — Always use `project.camelVersion` from `.camel-kit/config.properties`; never guess a version.
- **Runtime Verification** — After implementation, run the runtime-specific verification loop inside `/camel-execute`.
- **Graph Enhances, Never Gates** — If the project graph is unavailable, skip graph-dependent analysis and continue.

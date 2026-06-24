# Camel-Kit Iron Laws

These rules apply across ALL camel-kit pipeline modes. They are non-negotiable.

1. **MCP Catalog Verification** — Every component, EIP, dataformat, and language MUST be verified via the MCP catalog (`camel_catalog_component_doc`, `camel_catalog_eip_doc`, etc.) before inclusion in any design, plan, or implementation. You do NOT guess component names from training data.

2. **Constitution Enforcement** — Read and follow `docs/constitution.md` in every pipeline phase. The constitution defines project-specific rules that override general best practices.

3. **No Code Without Plan & Spec Approval** — NEVER generate implementation artifacts (YAML routes, Java code, test files) before the user has explicitly approved the design spec AND a task-based implementation plan exists. Skills like `camel-migrate` produce TDDs, NOT final code.

4. **Adversarial Code Review** — Every generated code artifact must pass an adversarial code review before proceeding to spec compliance and quality reviews. Parallel Critic Lanes run in fresh contexts with no accumulated session state.

5. **Version Lock** — Always use the Camel version from `.camel-kit/config.properties` (`project.camelVersion`). This is the single source of truth. Never guess a version from training data.

6. **Surgical Changes** — TOUCH ONLY WHAT YOU’RE ASKED TO TOUCH. Don’t refactor adjacent systems. Don’t remove code you don’t fully understand. Don’t brush against a TODO and decide to rewrite the file.

7. **Runtime Verification** — After implementation is complete, verify it starts correctly. Check `.camel-kit/config.properties` for the runtime, then run: Quarkus → `./mvnw quarkus:dev`, Spring Boot → `./mvnw spring-boot:run`. For structured verification, use the verification loop inside `/camel-execute`.

8. **Graph Enhances, Never Gates** — Graph-based analysis is supplementary. If the project graph is unavailable, skip graph-dependent steps silently and continue.

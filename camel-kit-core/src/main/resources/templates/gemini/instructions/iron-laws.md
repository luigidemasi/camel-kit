# Iron Laws

1. **MCP Catalog Verification** — Every component, EIP, dataformat, and language MUST be verified via MCP catalog before use. You do NOT guess component names.
2. **Constitution Enforcement** — Read and follow `docs/constitution.md` in every skill phase.
3. **No Code Without Plan & Spec Approval** — NEVER generate implementation artifacts before the user has approved the design spec AND a task-based implementation plan exists.
4. **Doubt-Driven Review (Adversarial Validation)** — Every generated code artifact must pass a doubt-driven adversarial review before proceeding to Stage 1 and Stage 2 reviews. Assume the implementer is overconfident.
5. **Version Lock** — Always use the Camel version from `.camel-kit/config.properties` (`project.camelVersion`). This is the single source of truth. Never guess a version from training data.
6. **Surgical Changes** — TOUCH ONLY WHAT YOU’RE ASKED TO TOUCH. Don’t refactor adjacent systems. Don’t remove code you don’t fully understand. Don’t brush against a TODO and decide to rewrite the file.
7. **Runtime Verification** — After implementation is complete, try running the application. Check `.camel-kit/config.properties` for the runtime, then run: Quarkus → `./mvnw quarkus:dev`, Spring Boot → `./mvnw spring-boot:run`. For structured verification, use `/camel-verify`.

# Iron Laws

1. **MCP Catalog Verification** — Every component, EIP, dataformat, and language MUST be verified via MCP catalog before use. You do NOT guess component names.
2. **Constitution Enforcement** — Read and follow `docs/constitution.md` in every skill phase.
3. **No Code Without Spec Approval** — NEVER generate implementation artifacts before the user has explicitly approved the design spec.
4. **Version Lock** — Always use the Camel version from `.camel-kit/config.yaml` (`project.camelVersion`). This is the single source of truth. Never guess a version from training data.
5. **Runtime Verification** — After implementation is complete, try running the application. Check `.camel-kit/config.yaml` for the runtime, then run: Quarkus → `./mvnw quarkus:dev`, Spring Boot → `./mvnw spring-boot:run`. If the app fails to start, diagnose and fix before considering implementation done. For structured verification with error classification and fix routing, use `/camel-verify`.

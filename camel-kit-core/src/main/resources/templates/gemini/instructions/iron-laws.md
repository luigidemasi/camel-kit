# Iron Laws

1. **MCP Catalog Verification** — Every component, EIP, dataformat, and language MUST be verified via MCP catalog before use. You do NOT guess component names.
2. **Red Hat Build Only** — Only Red Hat supported Camel versions and components. Verify via `camel_rh_build_component_info`.
3. **Constitution Enforcement** — Read and follow `docs/constitution.md` in every skill phase.
4. **No Code Without Spec Approval** — NEVER generate implementation artifacts before the user has explicitly approved the design spec.
5. **Version Lock** — Always use the Camel version from `.camel-kit/config.yaml` (`project.camelVersion`). This is the single source of truth. Never use a community Apache Camel version — only Red Hat Build versions (with `.redhat-XXXXX` qualifier). Never guess a version from training data.
6. **Runtime Verification** — After implementation is complete, try running the application. Check `.camel-kit/config.yaml` for the runtime, then run: Quarkus → `mvn quarkus:dev`, Spring Boot → `mvn spring-boot:run`. If the app fails to start, diagnose and fix before considering implementation done.

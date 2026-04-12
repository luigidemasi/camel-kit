# Camel-Kit Iron Laws

These rules apply across ALL camel-kit pipeline modes. They are non-negotiable.

1. **MCP Catalog Verification** — Every component, EIP, dataformat, and language MUST be verified via the MCP catalog (`camel_catalog_component`, `camel_catalog_eip`, etc.) before inclusion in any design, plan, or implementation. You do NOT guess component names from training data.

2. **Red Hat Build Only** — Only Red Hat supported Camel versions and components. Verify Red Hat support status via `camel_rh_build_component_info`. If a component is not Red Hat supported, do not use it.

3. **Constitution Enforcement** — Read and follow `docs/constitution.md` in every pipeline phase. The constitution defines project-specific rules that override general best practices.

4. **No Code Without Spec Approval** — NEVER generate implementation artifacts (YAML routes, Java code, test files) before the user has explicitly approved the design spec. "The user clearly wants X" is not approval. Explicit "yes" or "approved" is approval.

5. **Graph Enhances, Never Gates** — Graph-based analysis is supplementary. If the project graph is unavailable, skip graph-dependent steps silently and continue. No pipeline phase should fail because the graph is missing.

6. **Version Lock** — Always use the Camel version from `.camel-kit/config.yaml` (`project.camelVersion`). This is the single source of truth. Never use a community Apache Camel version — only Red Hat Build versions (with `.redhat-XXXXX` qualifier). Never guess a version from training data. Version changes happen only during brainstorm or migration phases when the user explicitly selects a different version.

7. **Runtime Verification** — After implementation is complete, try running the application to verify it starts correctly. Check `.camel-kit/config.yaml` for the runtime, then run: Quarkus → `mvn quarkus:dev` (or `./mvnw quarkus:dev`), Spring Boot → `mvn spring-boot:run` (or `./mvnw spring-boot:run`). If the app fails to start, diagnose and fix the issue before considering the implementation done.

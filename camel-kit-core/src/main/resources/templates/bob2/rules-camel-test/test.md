# Test Mode Rules

- Follow the approved implementation plan's task order; generate one Citrus YAML test task per route after the route
  artifacts it consumes exist.
- Keep generated test assets under the runtime-aware `{module}/src/test/resources/` path from the plan.
- Use `{TEST_DIR}jbang.properties` for Main dependencies and the scaffold-owned module POM for Spring Boot/Quarkus.
- Perform test generation and test fixes inline so the active mode's path-scoped edit restriction remains enforced.
- Use `camel-reviewer` for independent read-only test analysis.
- Reviewer subagents return evidence, findings, and unresolved failures; the parent records test evidence in the
  pipeline `execution-report.md` rather than creating a standalone test report.

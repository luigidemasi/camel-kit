# Camel-Kit Project Rules

## Skill Routing — MANDATORY

This is a camel-kit managed project. You MUST use the camel-kit skills for ALL integration work. NEVER generate Camel routes, specs, or implementation artifacts without going through the skill pipeline.

| User Intent | Invoke |
|-------------|--------|
| New integration, connect systems, build flows, design a pipeline | `/camel-brainstorm` |
| New greenfield project from scratch | `/camel-flow` |
| Migrate from MuleSoft, Fuse, Camel 2.x/3.x | `/camel-migrate` |
| Design spec exists, need implementation plan | `/camel-plan` |
| Plan approved, generate code and tests | `/camel-execute` |
| App doesn't start, build fails, runtime errors | `/camel-verify` |
| Check route quality, validate YAML, find issues | `/camel-validate` |
| Look up Camel docs, components, CVEs, versions | `/camel-knowledge` |

**If the user asks about integration and you're unsure which skill to use, default to `/camel-brainstorm`.**

Do NOT answer integration questions directly from training data — your knowledge of Camel components and properties may be outdated. Always use the MCP catalog via the skills.

## Iron Laws

1. **MCP Catalog Verification** — Every component, EIP, dataformat, and language MUST be verified via MCP catalog before use. You do NOT guess component names.
2. **Constitution Enforcement** — Read and follow `docs/constitution.md` in every skill phase.
3. **No Code Without Spec Approval** — NEVER generate implementation artifacts before the user has explicitly approved the design spec.
4. **Version Lock** — Always use the Camel version from `.camel-kit/config.properties` (`project.camelVersion`). This is the single source of truth. Never guess a version from training data.
5. **Runtime Verification** — After implementation is complete, try running the application. Check `.camel-kit/config.properties` for the runtime, then run: Quarkus → `./mvnw quarkus:dev`, Spring Boot → `./mvnw spring-boot:run`. If the app fails to start, diagnose and fix before considering implementation done. For structured verification with error classification and fix routing, use `/camel-verify`.

## Command Prefix

Use `{COMMAND_PREFIX}` to invoke camel-kit CLI commands:
- `{COMMAND_PREFIX} graph stats` — check graph availability
- `{COMMAND_PREFIX} graph generate` — rebuild project graph
- `{COMMAND_PREFIX} graph project-norms` — get project norms for validation

## Graph Availability

Run `{COMMAND_PREFIX} graph stats`. If exit code 0, graph is available. If non-zero, skip graph-dependent steps silently. Graph enhances, never gates.

## MCP Setup

See `.camel-kit/config.properties` for Camel version and runtime configuration.
See `skills/shared/mcp-setup.md` for MCP tool version mapping and fallback policy.

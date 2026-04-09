# Camel-Kit Project Rules

## Iron Laws

1. **MCP Catalog Verification** — Every component, EIP, dataformat, and language MUST be verified via MCP catalog before use. You do NOT guess component names.
2. **Red Hat Build Only** — Only Red Hat supported Camel versions and components. Verify via `camel_rh_build_component_info`.
3. **Constitution Enforcement** — Read and follow `docs/constitution.md` in every skill phase.
4. **No Code Without Spec Approval** — NEVER generate implementation artifacts before the user has explicitly approved the design spec.

## Command Prefix

Use `{commandPrefix}` to invoke camel-kit CLI commands:
- `{commandPrefix} graph stats` — check graph availability
- `{commandPrefix} graph generate` — rebuild project graph
- `{commandPrefix} graph project-norms` — get project norms for validation

## Graph Availability

Run `{commandPrefix} graph stats`. If exit code 0, graph is available. If non-zero, skip graph-dependent steps silently. Graph enhances, never gates.

## Camel Version

This project uses Apache Camel version `{camelVersion}`.

## MCP Setup

See `.camel-kit/config.yaml` for Camel version and runtime configuration.

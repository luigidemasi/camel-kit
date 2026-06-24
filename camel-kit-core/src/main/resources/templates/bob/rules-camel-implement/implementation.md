# Implement Mode Rules

## MCP Verification Chain

For EVERY component, EIP, dataformat, or language used in implementation:

1. Look up in the mapping guide (if migration)
2. Call `camel_catalog_component_doc` / `camel_catalog_eip_doc` / etc. to verify it exists
3. Only then use it in code

## YAML DSL

- All routes use Camel YAML DSL unless the plan explicitly specifies Java DSL.
- Follow the YAML structure conventions in `.camel-kit/templates/yaml-structure.md`.

## Checkpoints

- Create a checkpoint before starting implementation of each route.
- This provides rollback points if a route migration breaks other routes.

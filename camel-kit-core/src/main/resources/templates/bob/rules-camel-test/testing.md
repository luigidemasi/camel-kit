# Test Mode Rules

## TDD Workflow

- Write the failing test FIRST, then verify it fails, then implement.
- Never write implementation code before the test exists.

## Test Conventions

- Integration tests use Citrus framework when configured.
- Test file naming: `{route-id}.camel.it.yaml` (Citrus YAML DSL).
- Test data goes in `test/data/` directory.
- Each route gets at least one happy-path test and one error-path test.

## Schemas

- If Citrus schemas are available in `.camel-kit/.cache/`, use them for message validation.

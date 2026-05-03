## Agent Optimization: OpenCode

### LSP-Powered Validation

Use the `lsp` tool to enhance validation with IDE-grade code intelligence:

- **Go-to-definition:** When validating that a bean reference in a route exists, use `lsp` go-to-definition to confirm the bean class is reachable
- **Find references:** When checking for unused routes or dead code, use `lsp` find-references to verify whether a route or processor is called
- **Hover:** When verifying method signatures in bean references, use `lsp` hover to inspect return types and parameter lists

Note: `lsp` is experimental and may not be available. If `lsp` calls fail, fall back to grep-based validation.

### Path-Scoped Safety

Configure the validator with read-only permissions:

- Read: `**/*.yaml`, `**/*.xml`, `**/*.properties`, `**/*.java`, `**/*.groovy`
- Write: none (validation is read-only)

This prevents the validator from accidentally modifying files.

### Citrus Test Validation

When the verification loop runs `camel test run`, the validator can optionally enrich Citrus test results with LSP analysis when LSP is available and configured:

- Use `lsp` go-to-definition to verify bean references flagged in test failures
- Use `lsp` find-references to check whether a route endpoint is actually called by the test
- This provides deeper diagnostic information alongside Citrus assertion messages

LSP availability is not guaranteed — fall back to standard test output if LSP calls fail.

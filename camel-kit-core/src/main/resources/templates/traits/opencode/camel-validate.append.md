## Agent Optimization: OpenCode

### LSP-Powered Validation

Use the `lsp` tool to enhance validation with IDE-grade code intelligence:

- **Go-to-definition:** When validating that a bean reference in a route exists, use `lsp` go-to-definition to confirm the bean class is reachable
- **Find references:** When checking for unused routes or dead code, use `lsp` find-references to verify whether a route or processor is called
- **Hover:** When verifying method signatures in bean references, use `lsp` hover to inspect return types and parameter lists

Note: `lsp` is experimental and may not be available. If `lsp` calls fail, fall back to grep-based validation.

### Path-Scoped Safety

OpenCode supports glob-based path permissions. During validation, restrict file access to:

- Read: `**/*.yaml`, `**/*.xml`, `**/*.properties`, `**/*.java`, `**/*.groovy`
- Write: none (validation is read-only)

This prevents the validator from accidentally modifying files.

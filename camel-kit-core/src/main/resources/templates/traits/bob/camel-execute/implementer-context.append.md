## Agent Optimization: IBM Bob

### Precise Code Placement

When generating implementation artifacts, instruct the implementer to use `insert_content` for additive changes:

- Properties files: insert at end rather than rewriting
- POM files: insert dependencies at the correct `<dependencies>` location
- This preserves any existing user content that the implementer should not modify

### Mode Context

Instruct the implementer to check the current mode rules (`rules-camel-implement/implementation.md`) before generating code. The rules contain project-specific constraints loaded by the mode system.

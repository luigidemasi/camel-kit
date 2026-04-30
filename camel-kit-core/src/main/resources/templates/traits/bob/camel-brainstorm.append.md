## Agent Optimization: IBM Bob

### Mode-Based Interview Phases

Use `switch_mode` to transition between structured interview phases:

- Start in current mode for discovery
- Switch to "camel-brainstorm" custom mode for the design interview (this loads interview-specific gates and rules)
- The mode provides automatic gate validation — interview gates prevent advancing past incomplete phases

### Codebase Analysis for Migrations

When the user is migrating from an existing integration platform, use `list_code_definition_names` to scan the source project:

- This identifies classes, methods, and entry points in the existing codebase
- Use the results to inform component mapping decisions during the design interview
- Especially valuable for MuleSoft and BizTalk migrations where code structure reveals integration patterns

### Browser-Based UI Validation

If the integration involves web UI components (REST API consumers, webhook endpoints), use the browser tool group to validate:

- Test webhook URLs are accessible
- Verify REST API endpoint responses match expected schemas
- Screenshot UI dashboards that the integration needs to interact with

---
title: YAML Generation
weight: 3
---

Generate Kaoto-compatible Camel YAML DSL from your flow definition.

## Running Generation

```
/camel-implement <flow-name>
```

This:
1. Verifies flow definition and schemas exist
2. Looks up components via MCP or cached catalog
3. Transforms the design into Camel YAML DSL
4. **Automatically validates** with MCP tools (if available)
5. Runs Maven validation loop if needed
6. Outputs to `<flow-name>.camel.yaml`

## Automatic MCP Validation

If the Camel MCP server is configured, `/camel-implement` **automatically validates** the generated route before Maven runs:

1. **Route Structure Analysis** (`camel_route_context`) — extracts all components and EIPs, verifies they exist in the catalog
2. **URI and Route Validation** (`camel_validate_route`) — validates all endpoint URIs against the catalog schema, catches typos and unknown options
3. **Auto-fix loop** — if errors are found, the AI agent fixes them and re-validates (up to 3 attempts)

See [Command Reference](/docs/reference/commands) for the detailed process.

## Generated Route Quality

`/camel-implement` enforces catalog-verified accuracy on every generated route:
- All component names, endpoint option names, and Maven coordinates come from `camel_catalog_component_doc` — never from training data
- All data format names and options come from `camel_catalog_dataformat_doc`
- All expression language names and syntax come from `camel_catalog_language_doc`
- All EIP names and options come from `camel_catalog_eip_doc`
- When the route has both an HTTP consumer and an HTTP producer, `removeHeaders("CamelHttp*")` is inserted before each outbound HTTP call to prevent header leakage
- DataMapper XSLT generation is blocked if the field mapping table is empty
- After generation, the route is validated with `camel_validate_route` in a fix-re-query-retry loop (up to 3 attempts)

## Kaoto Compatibility

Generated YAML follows Kaoto requirements:
- Nested EIPs under parent's `steps` array
- Proper expression format for Simple, JSONPath, etc.
- Error handlers at route level
- Environment variables as `{{VARIABLE}}`

## Running Generated Routes

```bash
# With Camel JBang (dependencies from application.properties)
camel run order-ingestion.camel.yaml application.properties

# Export to Maven project
camel export order-ingestion.camel.yaml \
  --runtime quarkus \
  --gav com.example:my-integration:1.0.0
```

Dependencies are configured in `application.properties`:
```properties
camel.jbang.dependencies=org.postgresql:postgresql:42.7.3,\
org.apache.commons:commons-dbcp2:2.12.0
```

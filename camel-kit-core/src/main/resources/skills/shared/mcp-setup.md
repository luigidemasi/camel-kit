# MCP Server Setup — Shared Reference

This file documents the common MCP server configuration used by all camel-kit skills. Skills reference this file to avoid repeating the same setup block.

---

## Camel Catalog MCP Server

**To enable**, add to `.mcp.json`:
```json
{
  "mcpServers": {
    "camel": {
      "command": "jbang",
      "args": [
        "--repos", "redhat=https://maven.repository.redhat.com/ga/",
        "-Dquarkus.log.level=WARN",
        "org.apache.camel:camel-jbang-mcp:LATEST:runner"
      ]
    }
  }
}
```

Use `LATEST` for the MCP server artifact (must resolve to >= 4.18.0). If `LATEST` fails to resolve, fall back to `4.18.0`. The MCP server is a development tool — it can serve catalog data for any Camel version regardless of its own version. The `--repos` flag adds the Red Hat Maven repository so the MCP server can resolve Camel catalog artifacts for Red Hat Build versions at runtime.

---

## Red Hat Build Version Mapping

The upstream Camel MCP catalog resolves artifacts from Maven repositories at runtime.
When using Red Hat Build versions, pass the **exact Red Hat artifact version** from the table below to MCP catalog tools (`camelVersion` parameter).

| Camel Minor | camel-catalog | camel-catalog-provider-springboot | camel-quarkus-catalog | Quarkus Platform BOM |
|-------------|---------------|-----------------------------------|-----------------------|----------------------|
| 4.0 | 4.0.0.redhat-00036 | 4.0.0.redhat-00045 | 3.2.0.redhat-00030 | 3.2.12.SP1-redhat-00003 |
| 4.4 | 4.4.0.redhat-00046 | 4.4.0.redhat-00039 | 3.8.0.redhat-00014 | 3.8.6.redhat-00005 |
| 4.8 | 4.8.5.redhat-00008 | 4.8.5.redhat-00008 | 3.15.0.redhat-00010 | 3.15.7.redhat-00001 |
| 4.10 | 4.10.7.redhat-00009 | 4.10.7.redhat-00013 | 3.20.0.redhat-00011 | 3.20.5.redhat-00002 |
| 4.14 | 4.14.4.redhat-00008 | 4.14.4.redhat-00010 | 3.27.1.redhat-00004 | 3.27.2.redhat-00002 |

**How to use this table:** When calling `camel_catalog_*` MCP tools with `runtime=default`, pass the `camel-catalog` version as `camelVersion`. For `runtime=spring-boot`, the server resolves the Spring Boot provider automatically. For `runtime=quarkus`, the server resolves the Quarkus catalog automatically.

This table is derived from `catalog/versions.properties` (the single source of truth).

---

## Fallback Policy

Always attempt MCP tool calls directly — do not check for `.mcp.json` or try to detect MCP availability upfront. If a tool call fails (tool not found, network error, timeout), fall back to bundled skill files or proceed with a warning.

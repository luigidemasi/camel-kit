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

Use `LATEST` for the MCP server artifact (must resolve to ≥ 4.18.0). If `LATEST` fails to resolve, fall back to `4.18.0`. The MCP server is a development tool — it can serve catalog data for any Camel version regardless of its own version. The `--repos` flag adds the Red Hat Maven repository so the MCP server can resolve Camel catalog artifacts for Red Hat Build versions at runtime.

---

## Version Stripping Rule

**CRITICAL:** If `CAMEL_VERSION` contains a `.redhat-XXXXX` suffix (e.g., `4.14.4.redhat-00008`), strip it before passing to MCP catalog tools (`camel_catalog_*`, `camel_validate_route`, `camel_route_context`, `camel_route_harden_context`). The Camel Catalog MCP server uses community versions only.

Example: `4.14.4.redhat-00008` → pass `4.14.4` to MCP calls. Keep the full `.redhat` version for Maven dependencies and `pom.xml`.

---

## Fallback Policy

Always attempt MCP tool calls directly — do not check for `.mcp.json` or try to detect MCP availability upfront. If a tool call fails (tool not found, network error, timeout), fall back to bundled skill files or proceed with a warning.

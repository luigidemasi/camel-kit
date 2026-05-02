# MCP Server Setup — Shared Reference

This file documents the common MCP server configuration used by all camel-kit skills. Skills reference this file to avoid repeating the same setup block.

---

## Camel Catalog MCP Server

The Camel MCP server is a Quarkus uber-jar started with `java -jar`. It is extracted to `.camel-kit/mcp/` during `camel-kit init`.

**Configuration** (`.mcp.json`):
```json
{
  "mcpServers": {
    "camel": {
      "command": "java",
      "args": [
        "-Dcamel.catalog.repos=https://repo.maven.apache.org/maven2/",
        "-Dquarkus.log.level=WARN",
        "-jar", ".camel-kit/mcp/camel-jbang-mcp-runner.jar"
      ],
      "description": "Apache Camel MCP Server"
    }
  }
}
```

**Why `java -jar` instead of JBang:** The Quarkus runner JAR is a self-contained uber-jar. Using `java -jar` avoids JBang's `--repos` issues (replaces default repos, `mavenlocal` connector bug, SNAPSHOT resolution failures).

---

## MCP Tool Call Rules (MANDATORY)

### Rule 1: Use `platformBom` for versioned catalog queries

The `platformBom` parameter accepts a full Maven GAV (`groupId:artifactId:version`) and is the preferred way to query a specific catalog version. It works for all runtimes:

- **main**: `org.apache.camel:camel-catalog:4.14.4`
- **spring-boot**: `org.apache.camel.springboot:camel-catalog-provider-springboot:4.14.4`
- **quarkus**: `io.quarkus.platform:quarkus-camel-bom:3.17.7`

The correct `platformBom` value for each runtime is derived from `.camel-kit/config.properties` (the single source of truth for all version numbers).

When `platformBom` is provided, `camelVersion` is ignored.

### Rule 2: Pass the correct `runtime`

The MCP tool schema accepts `main`, `spring-boot`, or `quarkus` — **NOT** `default`. Pass the runtime that matches the project:

- Quarkus project → `runtime=quarkus`
- Spring Boot project → `runtime=spring-boot`
- Plain Camel / Camel Main / YAML DSL → `runtime=main`

The runtime affects which components are returned (e.g., Quarkus extensions vs Spring Boot starters) and how `platformBom` is resolved.

### Rule 3: Omitting `platformBom` and `camelVersion`

When both are omitted, the MCP server uses its built-in catalog (4.20.0). This is a superset of all versions — component schemas are backwards-compatible. Use this as a fallback when the exact version doesn't matter.

**Examples:**
- Project has `camelVersion: 4.14.4`, `runtime: quarkus` → `runtime=quarkus`, `platformBom=io.quarkus.platform:quarkus-camel-bom:3.17.7`
- Project has `camelVersion: 4.8.5`, `runtime: spring-boot` → `runtime=spring-boot`, `platformBom=org.apache.camel.springboot:camel-catalog-provider-springboot:4.8.5`
- Quick lookup, version doesn't matter → `runtime=main`, omit `platformBom`

### Version configuration

Version numbers are stored in `.camel-kit/config.properties`, set during `/camel-design`:

```properties
project.runtime=quarkus
project.camelVersion={CAMEL_QUARKUS_VERSION}
project.platformBomVersion={QUARKUS_PLATFORM_VERSION}
```

Default platform BOMs by runtime:

| Runtime | platformBom GAV |
|---------|----------------|
| main | `org.apache.camel:camel-catalog:{project.camelVersion}` |
| spring-boot | `org.apache.camel.springboot:camel-catalog-provider-springboot:{project.camelVersion}` |
| quarkus | `io.quarkus.platform:quarkus-camel-bom:{project.platformBomVersion}` |

Users can override any property via `-p key=value` CLI flags or a custom config file (`-c path`).

---

## Fallback Policy

Always attempt MCP tool calls directly — do not check for `.mcp.json` or try to detect MCP availability upfront. If a tool call fails (tool not found, network error, timeout), fall back to bundled skill files or proceed with a warning.

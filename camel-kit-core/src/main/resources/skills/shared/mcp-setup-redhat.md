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
        "-Dcamel.catalog.repos=https://maven.repository.redhat.com/ga/",
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

- **main**: `org.apache.camel:camel-catalog:4.14.4.redhat-00008`
- **spring-boot**: `org.apache.camel.springboot:camel-catalog-provider-springboot:4.14.4.redhat-00010`
- **quarkus**: `com.redhat.quarkus.platform:quarkus-camel-bom:3.27.2.redhat-00002`

The correct `platformBom` value for each Camel version and runtime is in `catalog/versions.properties` (the single source of truth), loaded by `VersionMapping.resolve(camelVersion).platformBom(runtime)`.

When `platformBom` is provided, `camelVersion` is ignored.

### Rule 2: Pass the correct `runtime`

The MCP tool schema accepts `main`, `spring-boot`, or `quarkus` — **NOT** `default`. Pass the runtime that matches the project:

- Quarkus project → `runtime=quarkus`
- Spring Boot project → `runtime=spring-boot`
- Plain Camel / Camel Main / YAML DSL → `runtime=main`

The runtime affects which components are returned (e.g., Quarkus extensions vs Spring Boot starters) and how `platformBom` is resolved.

### Rule 3: Omitting `platformBom` and `camelVersion`

When both are omitted, the MCP server uses its built-in catalog (4.19.0). This is a superset of all supported Red Hat versions — component schemas are backwards-compatible. Use this as a fallback when the exact version doesn't matter.

**Examples:**
- Project has `camelVersion: 4.14.4.redhat-00008`, `runtime: quarkus` → `runtime=quarkus`, `platformBom=com.redhat.quarkus.platform:quarkus-camel-bom:3.27.2.redhat-00002`
- Project has `camelVersion: 4.8.5.redhat-00008`, `runtime: spring-boot` → `runtime=spring-boot`, `platformBom=org.apache.camel.springboot:camel-catalog-provider-springboot:4.8.5.redhat-00008`
- Quick lookup, version doesn't matter → `runtime=main`, omit `platformBom`

### Version mapping reference

`catalog/versions.properties` maps each Camel minor version to the exact `platformBom` GAV per runtime. The `VersionMapping` Java class loads this file.

| Camel Minor | Main platformBom | Spring Boot platformBom | Quarkus platformBom |
|-------------|-----------------|------------------------|---------------------|
| 4.0 | `org.apache.camel:camel-catalog:4.0.0.redhat-00036` | `o.a.c.springboot:camel-catalog-provider-springboot:4.0.0.redhat-00045` | `com.redhat.quarkus.platform:quarkus-camel-bom:3.2.12.SP1-redhat-00003` |
| 4.4 | `org.apache.camel:camel-catalog:4.4.0.redhat-00046` | `o.a.c.springboot:...:4.4.0.redhat-00039` | `com.redhat.quarkus.platform:quarkus-camel-bom:3.8.6.redhat-00005` |
| 4.8 | `org.apache.camel:camel-catalog:4.8.5.redhat-00008` | `o.a.c.springboot:...:4.8.5.redhat-00008` | `com.redhat.quarkus.platform:quarkus-camel-bom:3.15.7.redhat-00001` |
| 4.10 | `org.apache.camel:camel-catalog:4.10.7.redhat-00009` | `o.a.c.springboot:...:4.10.7.redhat-00013` | `com.redhat.quarkus.platform:quarkus-camel-bom:3.20.5.redhat-00002` |
| 4.14 | `org.apache.camel:camel-catalog:4.14.4.redhat-00008` | `o.a.c.springboot:...:4.14.4.redhat-00010` | `com.redhat.quarkus.platform:quarkus-camel-bom:3.27.2.redhat-00002` |

---

## Fallback Policy

Always attempt MCP tool calls directly — do not check for `.mcp.json` or try to detect MCP availability upfront. If a tool call fails (tool not found, network error, timeout), fall back to bundled skill files or proceed with a warning.

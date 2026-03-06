# Docker Compose Generation Guide

This guide generates `docker-compose.yaml`.

**Context variables:** `FLOW_NAME`, `MODULE_DIR`, `CAMEL_VERSION`, `RUNTIME`, `DOCKER_IMAGE`.

---

Generate a `docker-compose.yaml` with the Camel service and any external services identified in TDD Section 8.2.

## Mandatory Rules for the Camel Service

| Rule | Detail |
|------|--------|
| Image | `apache/camel-jbang:{{CAMEL_VERSION}}` -- Docker Hub, **NOT** `ghcr.io/apache/camel-jbang` (does not exist) |
| Entrypoint | The image entrypoint is `camel`. The `command:` must start with the subcommand `run`, **NOT** `camel run` (otherwise it becomes `camel camel run`) |
| Route file | Mount the `.camel.yaml` file and list it in `command:` |
| XSL files | Mount **every** `kaoto-datamapper-*.xsl` file and list them in `command:` -- omitting them causes `FileNotFoundException: Cannot find resource: classpath:kaoto-datamapper-*.xsl` at startup |
| Properties | Mount `application.properties` and pass it via `--properties=` |
| Port | Use the port from `camel.server.port` in `application.properties` |
| External services | Add service definitions for TDD Section 8.2 dependencies (SMTP dev server, databases, message brokers, etc.) and use `depends_on:` from the Camel service |

## docker-compose.yaml Template

Adapt to actual file names and dependencies:

```yaml
# ============================================
# Docker Compose for {FLOW_NAME}
# ============================================

services:
  {FLOW_NAME}:
    image: apache/camel-jbang:{{CAMEL_VERSION}}
    container_name: {FLOW_NAME}
    ports:
      - "{port}:{port}"
    volumes:
      - ./{FLOW_NAME}.camel.yaml:/work/{FLOW_NAME}.camel.yaml:ro
      - ./application.properties:/work/application.properties:ro
      - ./kaoto-datamapper-{id}.xsl:/work/kaoto-datamapper-{id}.xsl:ro
    working_dir: /work
    command: >
      run {FLOW_NAME}.camel.yaml kaoto-datamapper-{id}.xsl
      --properties=application.properties
    environment:
      CAMEL_SERVER_ENABLED: "true"
      CAMEL_SERVER_PORT: "{port}"
    depends_on:
      - {external-service}
    restart: unless-stopped

  # External services from TDD Section 8.2
  {external-service}:
    image: {image}
    ports:
      - "{service-port}:{service-port}"
    restart: unless-stopped
```

**Replace ALL `{placeholders}` with actual values.** Do NOT leave commented-out volume or command examples -- generate the real entries for each DataMapper XSL file in the project.

**Runtime-specific note:** `DOCKER_IMAGE` is provided by the orchestrator.

**File location:** Use `MODULE_DIR` for file location.

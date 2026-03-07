# Docker Compose Generation Guide

This guide generates `docker-compose.yaml`.

**Context variables:** `FLOW_NAME`, `MODULE_DIR`, `CAMEL_VERSION`, `RUNTIME`, `DOCKER_IMAGE`.

---

Generate a `docker-compose.yaml` based on the target runtime. The purpose differs by runtime:

- **JBang:** docker-compose runs the Camel application AND external services
- **Spring Boot / Quarkus:** docker-compose runs external services ONLY (the app runs via Maven)

---

## JBang Template

Use this template when `RUNTIME == jbang`.

### Mandatory Rules for the Camel Service

| Rule | Detail |
|------|--------|
| Image | `apache/camel-jbang:{{CAMEL_VERSION}}` -- Docker Hub, **NOT** `ghcr.io/apache/camel-jbang` (does not exist) |
| Entrypoint | The image entrypoint is `camel`. The `command:` must start with the subcommand `run`, **NOT** `camel run` (otherwise it becomes `camel camel run`) |
| Route file | Mount the `.camel.yaml` file and list it in `command:` |
| XSL files | Mount **every** `kaoto-datamapper-*.xsl` file individually (one `volumes:` entry per file) and list each in `command:` — Docker volumes do not support glob patterns. Omitting any XSL file causes `FileNotFoundException` at startup |
| Properties | Mount `application.properties` and pass it via `--properties=` |
| Port | Use the port from `camel.server.port` in `application.properties` |
| External services | Add service definitions for TDD "Dependencies" section dependencies and use `depends_on:` from the Camel service |

```yaml
# ============================================
# Docker Compose for {FLOW_NAME} (JBang)
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

  # External services from TDD "Dependencies" section
  {external-service}:
    image: {image}
    ports:
      - "{service-port}:{service-port}"
    restart: unless-stopped
```

---

## Spring Boot / Quarkus Template

Use this template when `RUNTIME == springboot` or `RUNTIME == quarkus`.

The Camel application is NOT included in docker-compose — it runs via `mvn spring-boot:run` or `mvn quarkus:dev`. Docker Compose only manages the external services the application depends on (databases, message brokers, mail servers, etc.).

**If the TDD has no external service dependencies:** skip docker-compose generation entirely.

```yaml
# ============================================
# Docker Compose for {FLOW_NAME} (External Services)
# ============================================

services:
  # External services from TDD "Dependencies" section
  {external-service}:
    image: {image}
    container_name: {FLOW_NAME}-{service-name}
    ports:
      - "{service-port}:{service-port}"
    environment:
      # Service-specific env vars (e.g., POSTGRES_DB, KAFKA_AUTO_CREATE_TOPICS_ENABLE)
    restart: unless-stopped
```

---

**Replace ALL `{placeholders}` with actual values.** Do NOT leave commented-out volume or command examples -- generate the real entries.

**File location:** Use `MODULE_DIR` for file location.

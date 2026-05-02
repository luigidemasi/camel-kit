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

The Camel application is NOT included in docker-compose — it runs via `./mvnw spring-boot:run` or `./mvnw quarkus:dev`. Docker Compose only manages the external services the application depends on (databases, message brokers, mail servers, etc.).

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

---

## Service Catalog

Use this catalog to select the correct Docker image, ports, and configuration for each external service type. Match the Camel component from the TDD to the corresponding service entry.

<HARD-RULE>
When a TDD requires an external service (SMTP, database, message broker, etc.), you MUST spin up the corresponding Docker container from this catalog. NEVER skip verification because "a real server is not available" — that is exactly what these test containers are for.
</HARD-RULE>

### Mail (SMTP/IMAP/POP3)

| Camel Component | Docker Image | Ports | Notes |
|----------------|-------------|-------|-------|
| `smtp`, `smtps`, `mail` | `haravich/fake-smtp-server:latest` | `1025:1025` (SMTP), `1080:1080` (Web UI) | Fake SMTP server with REST API and web interface to inspect received emails |

```yaml
  fake-smtp:
    image: haravich/fake-smtp-server:latest
    container_name: fake-smtp
    ports:
      - "1025:1025"
      - "1080:1080"
    restart: unless-stopped
```

**Application properties override:**
```properties
camel.component.smtp.port=1025
# or in endpoint URI: smtp://fake-smtp:1025
```

**Verification:** After sending a test message, query `http://localhost:1080/api/emails` to verify the email was received. Check sender, recipient, subject, and body fields.

---

### Databases

| Camel Component | Docker Image | Port | Environment |
|----------------|-------------|------|-------------|
| `jdbc`, `sql`, `jpa` (PostgreSQL) | `postgres:16-alpine` | `5432:5432` | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| `jdbc`, `sql`, `jpa` (MySQL) | `mysql:8` | `3306:3306` | `MYSQL_DATABASE`, `MYSQL_ROOT_PASSWORD` |
| `mongodb` | `mongo:7` | `27017:27017` | (none required) |

```yaml
  postgres:
    image: postgres:16-alpine
    container_name: postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: testdb
      POSTGRES_USER: testuser
      POSTGRES_PASSWORD: testpass
    restart: unless-stopped
```

---

### Message Brokers

| Camel Component | Docker Image | Ports | Environment |
|----------------|-------------|-------|-------------|
| `kafka` | `apache/kafka:latest` | `9092:9092` (container), `29092:29092` (host) | `KAFKA_NODE_ID`, `KAFKA_PROCESS_ROLES`, `KAFKA_LISTENERS`, `KAFKA_ADVERTISED_LISTENERS`, `KAFKA_CONTROLLER_QUORUM_VOTERS`, `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP`, `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1` |
| `jms`, `activemq`, `amqp` | `apache/activemq-artemis:latest` | `61616:61616` (broker), `8161:8161` (console) | `ARTEMIS_USER=admin`, `ARTEMIS_PASSWORD=admin` |
| `rabbitmq`, `spring-rabbitmq` | `rabbitmq:3-management` | `5672:5672` (broker), `15672:15672` (management) | `RABBITMQ_DEFAULT_USER=guest`, `RABBITMQ_DEFAULT_PASS=guest` |

```yaml
  kafka:
    image: apache/kafka:latest
    container_name: kafka
    ports:
      - "9092:9092"
      - "29092:29092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,PLAINTEXT_HOST://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:29092
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    restart: unless-stopped
```

---

### Cache

| Camel Component | Docker Image | Port | Notes |
|----------------|-------------|------|-------|
| `redis` | `redis:7-alpine` | `6379:6379` | No auth by default |
| `infinispan` | `infinispan/server:15` | `11222:11222` | `USER=admin`, `PASS=admin` |

---

### File Transfer

| Camel Component | Docker Image | Port | Environment |
|----------------|-------------|------|-------------|
| `ftp` | `delfer/alpine-ftp-server` | `21:21`, `21000-21010:21000-21010` | `USERS=testuser\|testpass` |
| `sftp` | `atmoz/sftp` | `2222:22` | Command: `testuser:testpass:::upload` |

---

### Object Storage

| Camel Component | Docker Image | Ports | Environment |
|----------------|-------------|-------|-------------|
| `aws2-s3`, `minio` | `minio/minio` | `9000:9000` (API), `9001:9001` (console) | `MINIO_ROOT_USER=minioadmin`, `MINIO_ROOT_PASSWORD=minioadmin` |

```yaml
  minio:
    image: minio/minio
    container_name: minio
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    restart: unless-stopped
```

---

### Search / Indexing

| Camel Component | Docker Image | Port | Environment |
|----------------|-------------|------|-------------|
| `elasticsearch-rest` | `docker.elastic.co/elasticsearch/elasticsearch:8.17.0` | `9200:9200` | `discovery.type=single-node`, `xpack.security.enabled=false` |

---

**Replace ALL `{placeholders}` with actual values.** Do NOT leave commented-out volume or command examples -- generate the real entries.

**File location:** Use `MODULE_DIR` for file location.

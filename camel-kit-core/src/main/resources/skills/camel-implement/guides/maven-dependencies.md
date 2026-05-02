# Maven Dependencies Guide (Spring Boot / Quarkus Only)

This guide updates `pom.xml` with Camel dependencies.

**Context variables:** `FLOW_NAME`, `MODULE_DIR`, `CAMEL_VERSION`, `RUNTIME`. Only for Spring Boot and Quarkus.

---

## Step 1: Verify BOM Configuration

Before adding dependencies, verify that `pom.xml` has the correct BOM in `<dependencyManagement>`. The BOM version is **NOT** the same as `CAMEL_VERSION` — each runtime has its own BOM with a different version.

### Spring Boot

For full POM structure, load `pom-spring-boot.md`. The BOM configuration:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.apache.camel.springboot</groupId>
      <artifactId>camel-spring-boot-bom</artifactId>
      <version>${camel-spring-boot-version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

**IMPORTANT:** The groupId is `org.apache.camel.springboot`.

The `camel-spring-boot-bom` version uses the **same base version** as Camel.

**Read the correct version from `.camel-kit/config.properties`:**

```properties
project.runtime=spring-boot
project.camelVersion={CAMEL_SPRINGBOOT_VERSION}
project.platformBomVersion={SPRINGBOOT_BOM_VERSION}  # <- use THIS value
```

Do NOT guess or derive the version — it is pre-computed by `camel-kit init`.

### Quarkus

For full POM structure, load `pom-quarkus.md`. Quarkus uses TWO BOMs — the Quarkus platform BOM and the Camel Quarkus BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.quarkus.platform</groupId>
      <artifactId>quarkus-bom</artifactId>
      <version>${quarkus.platform.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <dependency>
      <groupId>io.quarkus.platform</groupId>
      <artifactId>quarkus-camel-bom</artifactId>
      <version>${camel-quarkus.platform.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

**IMPORTANT:** The groupId is `io.quarkus.platform`.

The Quarkus platform version uses the Quarkus version scheme (3.x), not Camel (4.x).

**Read the correct version from `.camel-kit/config.properties`:**

```properties
project.runtime=quarkus
project.camelVersion={CAMEL_QUARKUS_VERSION}
project.platformBomVersion={QUARKUS_PLATFORM_VERSION}  # <- use THIS value
```

Do NOT guess or derive the version — it is pre-computed by `camel-kit init`.

---

## Step 2: Add Dependencies

Add dependencies from the TDD "Dependencies" section using the correct groupId and artifactId pattern for the runtime.

**Graph version alignment:** If `PROJECT_CONTEXT.DEPENDENCY_VERSIONS` is available (from Step 0), check it before adding each dependency. If the artifact is already in the project with a specific version (e.g., `camel-kafka:4.14.4`), use that version for consistency. If the artifact is new to the project, use the version from the MCP catalog response. Note: when BOM manages versions (no `<version>` tag), this check applies to the BOM version itself.

**IMPORTANT:** The TDD "Dependencies" section lists generic Camel artifact names (e.g., `camel-kafka`). Transform them to the runtime-specific pattern:

### Spring Boot

```xml
<!-- Source component -->
<dependency>
  <groupId>org.apache.camel.springboot</groupId>
  <artifactId>camel-[component]-starter</artifactId>
</dependency>
```

### Quarkus

```xml
<!-- Source component -->
<dependency>
  <groupId>org.apache.camel.quarkus</groupId>
  <artifactId>camel-quarkus-[component]</artifactId>
</dependency>
```

### Artifact Name Transformation Examples

| TDD Lists | Spring Boot Artifact | Quarkus Artifact |
|-----------|---------------------|-----------------|
| `camel-kafka` | `camel-kafka-starter` | `camel-quarkus-kafka` |
| `camel-sql` | `camel-sql-starter` | `camel-quarkus-sql` |
| `camel-jackson` | `camel-jackson-starter` | `camel-quarkus-jackson` |
| `camel-xslt-saxon` | `camel-xslt-saxon-starter` | `camel-quarkus-xslt-saxon` |
| `camel-http` | `camel-http-starter` | `camel-quarkus-http` |
| `camel-jms` | `camel-jms-starter` | `camel-quarkus-jms` |

**Pattern:** Strip `camel-` prefix to get `[component]`, then:
- Spring Boot: `camel-[component]-starter`
- Quarkus: `camel-quarkus-[component]`

**Note:** No `<version>` tag needed — the BOM manages versions.

**Note:** The DataMapper guide (Step 1 in the orchestrator) handles adding the `camel-xslt-saxon` dependency automatically. Do not add it manually here.

**File location:** Use `MODULE_DIR` for `pom.xml` location.

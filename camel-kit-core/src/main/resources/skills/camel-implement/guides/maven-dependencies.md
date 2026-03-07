# Maven Dependencies Guide (Spring Boot / Quarkus Only)

This guide updates `pom.xml` with Camel dependencies.

**Context variables:** `FLOW_NAME`, `MODULE_DIR`, `CAMEL_VERSION`, `RUNTIME`. Only for Spring Boot and Quarkus.

---

## Step 1: Verify BOM Configuration

Before adding dependencies, verify that `pom.xml` has the correct BOM in `<dependencyManagement>`. The BOM version is **NOT** the same as `CAMEL_VERSION` — each runtime has its own BOM with a different version.

### Spring Boot

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.apache.camel.springboot</groupId>
      <artifactId>camel-spring-boot-bom</artifactId>
      <version>${camel-spring-boot.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

The `camel-spring-boot-bom` version uses the **same base version** as Camel but may have a **different Red Hat qualifier**.

**To discover the correct version**, fetch the directory listing from:
`https://maven.repository.redhat.com/ga/org/apache/camel/springboot/camel-spring-boot-bom/`

Find the entry matching the `CAMEL_VERSION` base version (e.g., `4.14.4`) and pick the highest `.redhat-XXXXX` qualifier.

**Fallback static table** (if fetch fails):

| Camel Version | Spring Boot BOM Version |
|--------------|------------------------|
| `4.14.4.redhat-00008` | `4.14.4.redhat-00010` |
| `4.10.7.redhat-00009` | `4.10.7.redhat-00013` |
| `4.8.5.redhat-00008` | `4.8.5.redhat-00008` |
| `4.4.0.redhat-00046` | `4.4.0.redhat-00039` |
| `4.0.0.redhat-00036` | `4.0.0.redhat-00045` |

### Quarkus

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.apache.camel.quarkus</groupId>
      <artifactId>camel-quarkus-bom</artifactId>
      <version>${camel-quarkus.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

The `camel-quarkus-bom` uses a **completely different version scheme** (3.x) from Camel (4.x).

**To discover the correct version**, fetch the directory listing from:
`https://maven.repository.redhat.com/ga/org/apache/camel/quarkus/camel-quarkus-bom/`

Match the Camel base version to the Camel Quarkus version using the mapping below.

**Fallback static table** (if fetch fails):

| Camel Version | Camel Quarkus BOM Version |
|--------------|--------------------------|
| `4.14.4.redhat-00008` | `3.27.1.redhat-00004` |
| `4.10.7.redhat-00009` | `3.20.0.redhat-00011` |
| `4.8.5.redhat-00008` | `3.15.0.redhat-00010` |
| `4.4.0.redhat-00046` | `3.8.0.redhat-00018` |
| `4.0.0.redhat-00036` | `3.2.0.redhat-00030` |

---

## Step 2: Add Dependencies

Add dependencies from the TDD "Dependencies" section using the correct groupId and artifactId pattern for the runtime.

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

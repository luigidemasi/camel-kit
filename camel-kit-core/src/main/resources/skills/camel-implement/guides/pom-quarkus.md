# POM Structure Guide — Camel on Quarkus

> **Context:** Loaded when generating or migrating a `pom.xml` for Red Hat Build of Apache Camel on Quarkus.
> **Purpose:** Define the exact POM structure. Every element has a reason — do NOT deviate.

---

## POM Template File

<HARD-RULE>
Do NOT generate the POM from scratch. Read the literal template file `templates/pom-quarkus.xml` and COPY it to `pom.xml`. Then replace ONLY these placeholders:
- `[PROJECT_GROUP_ID]` — from design spec
- `[PROJECT_ARTIFACT_ID]` — from design spec
- `[PROJECT_VERSION]` — from design spec
- `[PROJECT_NAME]` — from design spec
- `[PLATFORM_BOM_VERSION]` — from design spec header `platformBomVersion` field

Do NOT modify any other values. The template already contains the correct Red Hat groupIds, artifactIds, repositories, and plugin configuration. Then add project-specific dependencies in the DEPENDENCIES section.
</HARD-RULE>

## Reference

This guide is based on the official Red Hat Build of Apache Camel Quarkus examples:
`https://github.com/jboss-fuse/camel-quarkus-examples`

---

## CRITICAL: Red Hat Build Only — No Community Fallback

<HARD-RULE>
You MUST use Red Hat Build coordinates. NEVER fall back to community (Apache/io.quarkus) coordinates.

If Maven cannot resolve Red Hat artifacts, the fix is ALWAYS to add the Red Hat GA repository to the POM (see Rule 4). The fix is NEVER to switch to community groupIds or versions.

| Attribute | ❌ WRONG (community) | ✅ CORRECT (Red Hat Build) |
|-----------|---------------------|---------------------------|
| Quarkus BOM groupId | `io.quarkus.platform` | `com.redhat.quarkus.platform` |
| Camel BOM groupId | `org.apache.camel.quarkus` | `com.redhat.quarkus.platform` |
| Camel BOM artifactId | `camel-quarkus-bom` | `quarkus-camel-bom` |
| Platform version | `3.8.4`, `3.15.0` (no `.redhat-` suffix) | `3.27.2.redhat-00002` (with `.redhat-XXXXX` suffix) |

**The Red Hat GA Maven repository (`https://maven.repository.redhat.com/ga/`) is PUBLIC. It does NOT require authentication, subscription, or VPN. It is freely accessible to anyone.**

### Rationalization Table

| Excuse | Reality |
|--------|---------|
| "Red Hat Quarkus platform artifacts are not available in public repositories" | They ARE available at `maven.repository.redhat.com/ga/`. Add the repository to the POM. |
| "I'll use community versions compatible with Red Hat" | Iron Law 2: Red Hat Build Only. Community versions are FORBIDDEN. |
| "The Red Hat repository requires authentication" | No. It is a public, unauthenticated Maven repository. |
| "Maven can't resolve the Red Hat artifacts" | Add the Red Hat GA repository to `<repositories>` AND `<pluginRepositories>`. |
| "I'll use community for now and switch later" | No. Get it right from the start. |

### Red Flags — STOP If You Think:

- "The Red Hat artifacts aren't available, I'll use community instead..."
- "Community version X.Y.Z is compatible with Red Hat..."
- "I'll use `io.quarkus.platform` as the groupId..."
- "I'll use `org.apache.camel.quarkus` as the Camel BOM groupId..."
</HARD-RULE>

---

## Rule 1: No Parent POM

<HARD-RULE>
Do NOT use `quarkus-bom` or `io.quarkus:quarkus-universe-bom` as a parent POM. There is no parent — versions are managed via two BOMs in `dependencyManagement`.
</HARD-RULE>

---

## Rule 2: Version Properties

Define platform version properties using the Red Hat platform coordinates:

```xml
<properties>
    <quarkus.platform.version>3.27.2.redhat-00002</quarkus.platform.version>
    <camel-quarkus.platform.version>${quarkus.platform.version}</camel-quarkus.platform.version>

    <quarkus.platform.group-id>com.redhat.quarkus.platform</quarkus.platform.group-id>
    <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
    <camel-quarkus.platform.group-id>${quarkus.platform.group-id}</camel-quarkus.platform.group-id>
    <camel-quarkus.platform.artifact-id>quarkus-camel-bom</camel-quarkus.platform.artifact-id>

    <maven.compiler.target>17</maven.compiler.target>
    <maven.compiler.source>17</maven.compiler.source>
</properties>
```

| Property | Purpose | Notes |
|----------|---------|-------|
| `quarkus.platform.version` | Quarkus platform version | Red Hat Build version with `.redhat-XXXXX` suffix |
| `camel-quarkus.platform.version` | Camel Quarkus BOM version | Same as Quarkus platform version |
| `quarkus.platform.group-id` | Quarkus BOM groupId | `com.redhat.quarkus.platform` (NOT `io.quarkus.platform`) |
| `camel-quarkus.platform.group-id` | Camel BOM groupId | Same as Quarkus platform groupId |
| `quarkus.platform.artifact-id` | Quarkus BOM artifactId | `quarkus-bom` |
| `camel-quarkus.platform.artifact-id` | Camel BOM artifactId | `quarkus-camel-bom` |

### Version Discovery

The Quarkus platform version uses the Quarkus version scheme (3.x), NOT the Camel version scheme (4.x). Both `quarkus.platform.version` and `camel-quarkus.platform.version` use the **same** version value.

**To discover the correct version**, fetch the directory listing from:
`https://maven.repository.redhat.com/ga/com/redhat/quarkus/platform/quarkus-camel-bom/`

**Fallback mapping table** (if fetch fails) — map from the project's Camel version:

| Camel Version | Quarkus Platform Version |
|--------------|--------------------------|
| `4.14.4.redhat-00008` | `3.27.2.redhat-00002` |
| `4.10.7.redhat-00009` | `3.20.0.redhat-00011` |
| `4.8.5.redhat-00008` | `3.15.0.redhat-00010` |
| `4.4.0.redhat-00046` | `3.8.0.redhat-00018` |
| `4.0.0.redhat-00036` | `3.2.0.redhat-00030` |

<HARD-RULE>
The version MUST have a `.redhat-XXXXX` suffix. Community versions (e.g., `3.8.4`, `3.15.0` without suffix) are FORBIDDEN. If the Camel version from the design spec is not in this table, fetch the discovery URL above.
</HARD-RULE>

---

## Rule 3: Two BOMs in dependencyManagement

Quarkus projects require TWO BOMs — one for Quarkus, one for Camel Quarkus:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>${quarkus.platform.group-id}</groupId>
            <artifactId>${quarkus.platform.artifact-id}</artifactId>
            <version>${quarkus.platform.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>${camel-quarkus.platform.group-id}</groupId>
            <artifactId>${camel-quarkus.platform.artifact-id}</artifactId>
            <version>${camel-quarkus.platform.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

| BOM | groupId | artifactId | Manages |
|-----|---------|------------|---------|
| Quarkus | `com.redhat.quarkus.platform` | `quarkus-bom` | Quarkus extensions, CDI, Vert.x, etc. |
| Camel Quarkus | `com.redhat.quarkus.platform` | `quarkus-camel-bom` | All `camel-quarkus-*` extensions |

---

## Rule 4: Red Hat GA Repository (MANDATORY)

<HARD-RULE>
ALWAYS include the Red Hat GA repository. This repository is PUBLIC and does NOT require authentication. Without it, Maven cannot resolve Red Hat Build artifacts (`.redhat-XXXXX` versions).
</HARD-RULE>

```xml
<repositories>
    <repository>
        <id>redhat-ga-repository</id>
        <url>https://maven.repository.redhat.com/ga/</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
    <repository>
        <id>redhat-earlyaccess-repository</id>
        <url>https://maven.repository.redhat.com/earlyaccess/all/</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>
<pluginRepositories>
    <pluginRepository>
        <id>redhat-ga-repository</id>
        <url>https://maven.repository.redhat.com/ga/</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </pluginRepository>
    <pluginRepository>
        <id>redhat-earlyaccess-repository</id>
        <url>https://maven.repository.redhat.com/earlyaccess/all/</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </pluginRepository>
</pluginRepositories>
```

<HARD-RULE>
Do NOT include FuseSource or JBoss Nexus repositories. These are legacy/dead.
</HARD-RULE>

---

## Rule 5: Quarkus Maven Plugin

The Quarkus Maven plugin is required for building and packaging. Note the `<extensions>true</extensions>` and TWO separate execution goals:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>${quarkus.platform.group-id}</groupId>
            <artifactId>quarkus-maven-plugin</artifactId>
            <version>${quarkus.platform.version}</version>
            <extensions>true</extensions>
            <executions>
                <execution>
                    <id>build</id>
                    <goals>
                        <goal>build</goal>
                    </goals>
                </execution>
                <execution>
                    <id>generate-code</id>
                    <goals>
                        <goal>generate-code</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

| Element | Required | Why |
|---------|----------|-----|
| `<extensions>true</extensions>` | Yes | Enables Quarkus dev mode and build-time augmentation |
| `<goal>build</goal>` | Yes | Produces the Quarkus-augmented JAR |
| `<goal>generate-code</goal>` | Yes | Runs code generation (CXF WSDL-to-Java, Panache, etc.) |

Use the property references for groupId and version — not hardcoded values.

---

## Rule 6: Jakarta Namespace Only

Camel 4.x and Quarkus 3.x use Jakarta EE 10. Never use `javax.*` packages:

| ❌ Wrong (javax) | ✅ Correct (jakarta) |
|------------------|---------------------|
| `javax.jms:javax.jms-api` | `jakarta.jms:jakarta.jms-api` |
| `javax.persistence:javax.persistence-api` | `jakarta.persistence:jakarta.persistence-api` |
| `javax.inject:javax.inject` | `jakarta.inject:jakarta.inject-api` |

In most cases, you don't need to add Jakarta APIs explicitly — the BOM pulls them in.

---

## Complete POM Skeleton

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- NO <parent> element -->

    <groupId>[project-groupId]</groupId>
    <artifactId>[project-artifactId]</artifactId>
    <version>[project-version]</version>
    <name>[Project Name]</name>

    <properties>
        <quarkus.platform.version>[discovered-version]</quarkus.platform.version>
        <camel-quarkus.platform.version>${quarkus.platform.version}</camel-quarkus.platform.version>

        <quarkus.platform.group-id>com.redhat.quarkus.platform</quarkus.platform.group-id>
        <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
        <camel-quarkus.platform.group-id>${quarkus.platform.group-id}</camel-quarkus.platform.group-id>
        <camel-quarkus.platform.artifact-id>quarkus-camel-bom</camel-quarkus.platform.artifact-id>

        <maven.compiler.target>17</maven.compiler.target>
        <maven.compiler.source>17</maven.compiler.source>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>${quarkus.platform.group-id}</groupId>
                <artifactId>${quarkus.platform.artifact-id}</artifactId>
                <version>${quarkus.platform.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>${camel-quarkus.platform.group-id}</groupId>
                <artifactId>${camel-quarkus.platform.artifact-id}</artifactId>
                <version>${camel-quarkus.platform.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Add Camel Quarkus component extensions as needed (see maven-dependencies.md) -->
        <!-- Do NOT add camel-quarkus-core explicitly — the BOM and extensions handle it -->

        <!-- Test -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <repositories>
        <repository>
            <id>redhat-ga-repository</id>
            <url>https://maven.repository.redhat.com/ga/</url>
            <releases>
                <enabled>true</enabled>
            </releases>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
        <repository>
            <id>redhat-earlyaccess-repository</id>
            <url>https://maven.repository.redhat.com/earlyaccess/all/</url>
            <releases>
                <enabled>true</enabled>
            </releases>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
    </repositories>
    <pluginRepositories>
        <pluginRepository>
            <id>redhat-ga-repository</id>
            <url>https://maven.repository.redhat.com/ga/</url>
            <releases>
                <enabled>true</enabled>
            </releases>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </pluginRepository>
        <pluginRepository>
            <id>redhat-earlyaccess-repository</id>
            <url>https://maven.repository.redhat.com/earlyaccess/all/</url>
            <releases>
                <enabled>true</enabled>
            </releases>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </pluginRepository>
    </pluginRepositories>

    <build>
        <plugins>
            <plugin>
                <groupId>${quarkus.platform.group-id}</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <version>${quarkus.platform.version}</version>
                <extensions>true</extensions>
                <executions>
                    <execution>
                        <id>build</id>
                        <goals>
                            <goal>build</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>generate-code</id>
                        <goals>
                            <goal>generate-code</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>native</id>
            <activation>
                <property>
                    <name>native</name>
                </property>
            </activation>
            <properties>
                <quarkus.native.enabled>true</quarkus.native.enabled>
            </properties>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-failsafe-plugin</artifactId>
                        <executions>
                            <execution>
                                <goals>
                                    <goal>integration-test</goal>
                                    <goal>verify</goal>
                                </goals>
                                <configuration>
                                    <systemPropertyVariables>
                                        <quarkus.native.enabled>${quarkus.native.enabled}</quarkus.native.enabled>
                                    </systemPropertyVariables>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

---

## Migration-Specific Notes

When migrating from Fuse/Blueprint/Camel 2.x to Quarkus:

1. **Remove the old parent** — Fuse BOMs, any `org.jboss.fuse` parent
2. **Remove all FuseSource/JBoss repositories** — they are legacy
3. **Replace component dependencies** — `camel-core` → `camel-quarkus-core`, `camel-blueprint` → remove (not needed on Quarkus), `camel-jms` → `camel-quarkus-jms`
4. **Update Java namespace** — `javax.*` → `jakarta.*` in all source code AND dependencies
5. **Remove version tags** from dependencies managed by the BOMs
6. **Blueprint XML** → CDI beans with `@ApplicationScoped` + `@Named`
7. **Spring XML context** → `application.properties` for Quarkus configuration

# POM Structure Guide — Camel on Quarkus

> **Context:** Loaded when generating or migrating a `pom.xml` for Apache Camel on Quarkus.
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

Do NOT modify any other values. The template already contains the correct groupIds, artifactIds, and plugin configuration. Then add project-specific dependencies in the DEPENDENCIES section.
</HARD-RULE>

## Reference

This guide is based on the official Apache Camel Quarkus examples:
`https://github.com/apache/camel-quarkus-examples`

---

## Rule 1: No Parent POM

<HARD-RULE>
Do NOT use `quarkus-bom` or `io.quarkus:quarkus-universe-bom` as a parent POM. There is no parent — versions are managed via two BOMs in `dependencyManagement`.
</HARD-RULE>

---

## Rule 2: Version Properties

Define platform version properties using the community coordinates:

```xml
<properties>
    <quarkus.platform.version>{QUARKUS_PLATFORM_VERSION}</quarkus.platform.version>
    <camel-quarkus.platform.version>${quarkus.platform.version}</camel-quarkus.platform.version>

    <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
    <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
    <camel-quarkus.platform.group-id>${quarkus.platform.group-id}</camel-quarkus.platform.group-id>
    <camel-quarkus.platform.artifact-id>quarkus-camel-bom</camel-quarkus.platform.artifact-id>

    <maven.compiler.target>17</maven.compiler.target>
    <maven.compiler.source>17</maven.compiler.source>
</properties>
```

| Property | Purpose | Notes |
|----------|---------|-------|
| `quarkus.platform.version` | Quarkus platform version | Community version |
| `camel-quarkus.platform.version` | Camel Quarkus BOM version | Same as Quarkus platform version |
| `quarkus.platform.group-id` | Quarkus BOM groupId | `io.quarkus.platform` |
| `camel-quarkus.platform.group-id` | Camel BOM groupId | Same as Quarkus platform groupId |
| `quarkus.platform.artifact-id` | Quarkus BOM artifactId | `quarkus-bom` |
| `camel-quarkus.platform.artifact-id` | Camel BOM artifactId | `quarkus-camel-bom` |

### Version Discovery

The Quarkus platform version uses the Quarkus version scheme (3.x), NOT the Camel version scheme (4.x). Both `quarkus.platform.version` and `camel-quarkus.platform.version` use the **same** version value.

<HARD-RULE>
Read the Quarkus platform version from `.camel-kit/config.properties`:

```properties
project.runtime=quarkus
project.camelVersion=4.18.0
project.platformBomVersion={QUARKUS_PLATFORM_VERSION}  # <- use THIS value
```

**Do NOT guess or derive** the Quarkus platform version from the Camel version. The mapping is non-obvious (Camel 4.14 -> Quarkus 3.27, NOT 3.14) and is pre-computed by `camel-kit init`.
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
| Quarkus | `io.quarkus.platform` | `quarkus-bom` | Quarkus extensions, CDI, Vert.x, etc. |
| Camel Quarkus | `io.quarkus.platform` | `quarkus-camel-bom` | All `camel-quarkus-*` extensions |

---

## Rule 4: Quarkus Maven Plugin

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

## Rule 5: Jakarta Namespace Only

Camel 4.x and Quarkus 3.x use Jakarta EE 10. Never use `javax.*` packages:

| Wrong (javax) | Correct (jakarta) |
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

        <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
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
3. **Replace component dependencies** — `camel-core` -> `camel-quarkus-core`, `camel-blueprint` -> remove (not needed on Quarkus), `camel-jms` -> `camel-quarkus-jms`
4. **Update Java namespace** — `javax.*` -> `jakarta.*` in all source code AND dependencies
5. **Remove version tags** from dependencies managed by the BOMs
6. **Blueprint XML** -> CDI beans with `@ApplicationScoped` + `@Named`
7. **Spring XML context** -> `application.properties` for Quarkus configuration

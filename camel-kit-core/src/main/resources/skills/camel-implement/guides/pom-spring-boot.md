# POM Structure Guide — Camel on Spring Boot

> **Context:** Loaded when generating or migrating a `pom.xml` for Apache Camel on Spring Boot.
> **Purpose:** Define the exact POM structure. Every element has a reason — do NOT deviate.

---

## POM Template File

<HARD-RULE>
Do NOT generate the POM from scratch. Read the literal template file `templates/pom-spring-boot.xml` and COPY it to `pom.xml`. Then replace ONLY these placeholders:
- `[PROJECT_GROUP_ID]` — from design spec
- `[PROJECT_ARTIFACT_ID]` — from design spec
- `[PROJECT_VERSION]` — from design spec
- `[PROJECT_NAME]` — from design spec
- `[PLATFORM_BOM_VERSION]` — from design spec header `platformBomVersion` field
- `[SPRING_BOOT_VERSION]` — from `.camel-kit/config.properties` key `project.springBootVersion`

Do NOT modify any other values. The template already contains the correct groupId (`org.apache.camel.springboot`), artifactId, and plugin configuration. Then add project-specific dependencies in the DEPENDENCIES section.
</HARD-RULE>

## Reference

This guide is based on the official Apache Camel Spring Boot examples:
`https://github.com/apache/camel-spring-boot-examples`

---

## Rule 1: No Parent POM

<HARD-RULE>
Do NOT use `spring-boot-starter-parent` as a parent POM.

```xml
<!-- WRONG — never do this -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.5</version>
</parent>
```

The `camel-spring-boot-bom` manages ALL dependency versions — both Camel and Spring Boot. A parent POM creates version conflicts.
</HARD-RULE>

---

## Rule 2: Two Version Properties

Define exactly two version properties:

```xml
<properties>
    <camel-spring-boot-version>{CAMEL_SPRINGBOOT_VERSION}</camel-spring-boot-version>
    <spring-boot-version>{SPRING_BOOT_VERSION}</spring-boot-version>
</properties>
```

| Property | Purpose | Naming |
|----------|---------|--------|
| `camel-spring-boot-version` | BOM version | Hyphenated, not a generic Camel property name |
| `spring-boot-version` | Spring Boot Maven plugin version | Hyphenated |

### Version Discovery

<HARD-RULE>
Read the Spring Boot BOM version from `.camel-kit/config.properties`:

```properties
project.runtime=spring-boot
project.camelVersion={CAMEL_SPRINGBOOT_VERSION}
project.platformBomVersion={SPRINGBOOT_BOM_VERSION}  # <- use THIS value
project.springBootVersion={SPRING_BOOT_VERSION}      # <- plugin version
```

**Do NOT guess or derive** the Spring Boot BOM version from the Camel version. The correct value is pre-computed by `camel-kit init`.
Use `project.springBootVersion` for the `spring-boot-maven-plugin` version. Do NOT inspect Maven Central during code
generation.
</HARD-RULE>

---

## Rule 3: Single BOM in dependencyManagement

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

| Field | Value | Notes |
|-------|-------|-------|
| groupId | `org.apache.camel.springboot` | Apache Camel community groupId |
| artifactId | `camel-spring-boot-bom` | Manages Camel + Spring Boot + third-party versions |
| version | `${camel-spring-boot-version}` | Property from Rule 2 |

This BOM manages versions for:
- All `org.apache.camel.springboot:camel-*-starter` dependencies
- All `org.springframework.boot:spring-boot-*` dependencies
- Common third-party libraries (Jackson, Netty, etc.)

No other BOMs should be necessary for Camel + Spring Boot dependencies.

---

## Rule 4: Spring Boot Maven Plugin with Explicit Version

Since there is no parent POM, the plugin version MUST be explicit:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <version>${spring-boot-version}</version>
            <executions>
                <execution>
                    <goals>
                        <goal>repackage</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

The `repackage` goal creates the executable fat JAR. Without it, `java -jar` won't work.

---

## Rule 5: Jakarta Namespace Only

Camel 4.x and Spring Boot 3.x use Jakarta EE 10. Never use `javax.*` packages:

| Wrong (javax) | Correct (jakarta) |
|------------------|---------------------|
| `javax.jms:javax.jms-api` | `jakarta.jms:jakarta.jms-api` |
| `javax.persistence:javax.persistence-api` | `jakarta.persistence:jakarta.persistence-api` |
| `javax.servlet:javax.servlet-api` | `jakarta.servlet:jakarta.servlet-api` |

In most cases, you don't need to add Jakarta APIs explicitly — the BOM or Spring Boot starters pull them in.

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
    <packaging>jar</packaging>
    <name>[Project Name]</name>

    <properties>
        <camel-spring-boot-version>[discovered-version]</camel-spring-boot-version>
        <spring-boot-version>[matching-spring-boot-version]</spring-boot-version>
    </properties>

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

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <!-- Camel -->
        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-spring-boot-starter</artifactId>
        </dependency>

        <!-- Add component starters as needed (see maven-dependencies.md) -->

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.camel</groupId>
            <artifactId>camel-test-spring-junit5</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot-version}</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Migration-Specific Notes

When migrating from Fuse/Blueprint/Camel 2.x:

1. **Remove the old parent** — Fuse BOMs, Spring Boot parent, any `org.jboss.fuse` parent
2. **Remove all FuseSource/JBoss repositories** — they are legacy
3. **Replace component dependencies** — `camel-core` -> `camel-spring-boot-starter`, `camel-blueprint` -> remove (not needed on Spring Boot), `camel-jms` -> `camel-jms-starter`
4. **Update Java namespace** — `javax.*` -> `jakarta.*` in all source code AND dependencies
5. **Remove version tags** from dependencies managed by the BOM

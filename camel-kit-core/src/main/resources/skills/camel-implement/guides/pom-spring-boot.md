# POM Structure Guide — Camel on Spring Boot

> **Context:** Loaded when generating or migrating a `pom.xml` for Red Hat Build of Apache Camel on Spring Boot.
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
- `[SPRING_BOOT_VERSION]` — from design spec or discover from BOM (see Rule 2)

Do NOT modify any other values. The template already contains the correct Red Hat groupId (`com.redhat.camel.springboot.platform`), artifactId, repositories, and plugin configuration. Then add project-specific dependencies in the DEPENDENCIES section.
</HARD-RULE>

## Reference

This guide is based on the official Red Hat Build of Apache Camel Spring Boot examples:
`https://github.com/jboss-fuse/camel-spring-boot-examples`

---

## CRITICAL: Red Hat Build Only — No Community Fallback

<HARD-RULE>
You MUST use Red Hat Build coordinates. NEVER fall back to community (Apache) coordinates.

If Maven cannot resolve Red Hat artifacts, the fix is ALWAYS to add the Red Hat GA repository to the POM (see Rule 4). The fix is NEVER to switch to community groupIds or versions.

| Attribute | ❌ WRONG (community) | ✅ CORRECT (Red Hat Build) |
|-----------|---------------------|---------------------------|
| BOM groupId | `org.apache.camel.springboot` | `com.redhat.camel.springboot.platform` |
| BOM version | `4.14.4` (no `.redhat-` suffix) | `4.14.4.redhat-00010` (with `.redhat-XXXXX` suffix) |
| Version property | `camel.version` (dot) | `camel-spring-boot-version` (hyphens) |

**The Red Hat GA Maven repository (`https://maven.repository.redhat.com/ga/`) is PUBLIC. It does NOT require authentication, subscription, or VPN. It is freely accessible to anyone.**

### Rationalization Table

| Excuse | Reality |
|--------|---------|
| "Red Hat artifacts are not available in public repositories" | They ARE available at `maven.repository.redhat.com/ga/`. Add the repository to the POM. |
| "I'll use community versions compatible with Red Hat" | Iron Law 2: Red Hat Build Only. Community versions are FORBIDDEN. |
| "The Red Hat repository requires authentication" | No. It is a public, unauthenticated Maven repository. |
| "Maven can't resolve the Red Hat artifacts" | Add the Red Hat GA repository to `<repositories>` AND `<pluginRepositories>`. |
| "I'll use `org.apache.camel.springboot` as the BOM groupId" | Wrong groupId. Must be `com.redhat.camel.springboot.platform`. |
</HARD-RULE>

---

## Rule 1: No Parent POM

<HARD-RULE>
Do NOT use `spring-boot-starter-parent` as a parent POM.

```xml
<!-- ❌ WRONG — never do this -->
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
    <camel-spring-boot-version>4.14.4.redhat-00006</camel-spring-boot-version>
    <spring-boot-version>3.5.11</spring-boot-version>
</properties>
```

| Property | Purpose | Naming |
|----------|---------|--------|
| `camel-spring-boot-version` | BOM version | Hyphenated, NOT `camel.version` |
| `spring-boot-version` | Spring Boot Maven plugin version | Hyphenated |

### Version Discovery

The `camel-spring-boot-version` is NOT the same as the Camel version — it has a different `.redhat-XXXXX` qualifier.

**To discover the correct version**, fetch the directory listing from:
`https://maven.repository.redhat.com/ga/com/redhat/camel/springboot/platform/camel-spring-boot-bom/`

Find the entry matching the Camel base version and pick the highest `.redhat-XXXXX` qualifier.

**Fallback mapping table** (if fetch fails) — map from the project's Camel version:

| Camel Version | Spring Boot BOM Version |
|--------------|------------------------|
| `4.14.4.redhat-00008` | `4.14.4.redhat-00010` |
| `4.10.7.redhat-00009` | `4.10.7.redhat-00013` |
| `4.8.5.redhat-00008` | `4.8.5.redhat-00008` |
| `4.4.0.redhat-00046` | `4.4.0.redhat-00039` |
| `4.0.0.redhat-00036` | `4.0.0.redhat-00045` |

<HARD-RULE>
The version MUST have a `.redhat-XXXXX` suffix. Community versions (e.g., `4.14.4` without suffix) are FORBIDDEN. If the Camel version from the design spec is not in this table, fetch the discovery URL above.
</HARD-RULE>

**Spring Boot version:** the BOM pins the Spring Boot version. To find the matching version, check the BOM's `<spring-boot.version>` property at:
`https://maven.repository.redhat.com/ga/com/redhat/camel/springboot/platform/camel-spring-boot-bom/`

---

## Rule 3: Single BOM in dependencyManagement

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.redhat.camel.springboot.platform</groupId>
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
| groupId | `com.redhat.camel.springboot.platform` | NOT `org.apache.camel.springboot` |
| artifactId | `camel-spring-boot-bom` | Manages Camel + Spring Boot + third-party versions |
| version | `${camel-spring-boot-version}` | Property from Rule 2 |

This BOM manages versions for:
- All `org.apache.camel.springboot:camel-*-starter` dependencies
- All `org.springframework.boot:spring-boot-*` dependencies
- Common third-party libraries (Jackson, Netty, etc.)

No other BOMs should be necessary for Camel + Spring Boot dependencies.

---

## Rule 4: Red Hat GA Repository (MANDATORY) + No Legacy Repositories

<HARD-RULE>
ALWAYS include the Red Hat GA repository. This repository is PUBLIC and does NOT require authentication. Without it, Maven cannot resolve Red Hat Build artifacts (`.redhat-XXXXX` versions).

Do NOT include FuseSource or JBoss Nexus repositories. These are legacy/dead:

```xml
<!-- ❌ WRONG — remove these -->
<repository>
    <id>fusesource.releases</id>
    <url>https://repository.jboss.org/nexus/content/repositories/fs-releases/</url>
</repository>
<repository>
    <id>fusesource.ea</id>
    <url>https://repository.jboss.org/nexus/content/groups/ea/</url>
</repository>
```
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

---

## Rule 5: Spring Boot Maven Plugin with Explicit Version

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

## Rule 6: Jakarta Namespace Only

Camel 4.x and Spring Boot 3.x use Jakarta EE 10. Never use `javax.*` packages:

| ❌ Wrong (javax) | ✅ Correct (jakarta) |
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
                <groupId>com.redhat.camel.springboot.platform</groupId>
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

    <profiles>
        <profile>
            <id>openshift</id>
            <properties>
                <jkube.generator.from>registry.access.redhat.com/ubi9/openjdk-17:latest</jkube.generator.from>
                <jkube.build.switchToDeployment>true</jkube.build.switchToDeployment>
                <jkube-maven-plugin-version>1.19.0.redhat-00001</jkube-maven-plugin-version>
            </properties>
            <build>
                <defaultGoal>install</defaultGoal>
                <plugins>
                    <plugin>
                        <groupId>org.eclipse.jkube</groupId>
                        <artifactId>openshift-maven-plugin</artifactId>
                        <version>${jkube-maven-plugin-version}</version>
                        <executions>
                            <execution>
                                <goals>
                                    <goal>resource</goal>
                                    <goal>build</goal>
                                    <goal>apply</goal>
                                </goals>
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

When migrating from Fuse/Blueprint/Camel 2.x:

1. **Remove the old parent** — Fuse BOMs, Spring Boot parent, any `org.jboss.fuse` parent
2. **Remove all FuseSource/JBoss repositories** — they are legacy
3. **Replace component dependencies** — `camel-core` → `camel-spring-boot-starter`, `camel-blueprint` → remove (not needed on Spring Boot), `camel-jms` → `camel-jms-starter`
4. **Update Java namespace** — `javax.*` → `jakarta.*` in all source code AND dependencies
5. **Remove version tags** from dependencies managed by the BOM

# Maven Dependencies Guide (Spring Boot / Quarkus Only)

This guide updates `pom.xml` with Camel dependencies.

**Context variables:** `FLOW_NAME`, `MODULE_DIR`, `CAMEL_VERSION`. Only for Spring Boot and Quarkus.

---

Add dependencies from TDD Section 8:

```xml
<!-- Dependencies for {FLOW_NAME} -->

<!-- Source component -->
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-[source-component]</artifactId>
</dependency>

<!-- Sink component -->
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-[sink-component]</artifactId>
</dependency>

<!-- Data formats -->
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-jackson</artifactId>
</dependency>

<!-- DataMapper / XSLT transformation (added by camel-datamapper-implement if Step 2.5 ran) -->
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-xslt-saxon</artifactId>
</dependency>

<!-- External dependencies from TDD -->
[dependencies from TDD Section 8.2]
```

**Note:** `camel-datamapper-implement` (Step 2.5) handles adding `camel-xslt-saxon` automatically. Do not add it manually here.

**File location:** Use `MODULE_DIR` for `pom.xml` location.

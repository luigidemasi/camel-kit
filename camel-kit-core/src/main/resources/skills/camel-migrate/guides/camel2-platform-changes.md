# Camel 2.x/3.x → 4.x Platform & Runtime Changes

This guide covers structural and platform changes that are NOT component/EIP/format-specific. These are transformation rules applied during Phase 2 Step 2.1 of the migration.

## OSGi / Blueprint → Camel Main, Spring Boot, or Quarkus

### Blueprint XML Removal

Blueprint XML (`<blueprint xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0">`) is not supported in Camel 4.x. All Blueprint constructs must be converted.

| Blueprint Construct | Camel 4.x Equivalent |
|--------------------|---------------------|
| `<cm:property-placeholder persistent-id="...">` | `application.properties` file with `{{property.key}}` syntax in routes |
| `<reference interface="com.example.MyService"/>` | Main: translate fully to catalog-supported YAML/Forage configuration. Spring Boot/Quarkus: use the runtime's dependency-injection facilities. |
| `<bean id="myBean" class="com.example.MyBean"/>` | Main: translate fully to catalog-supported YAML/Forage configuration. Spring Boot/Quarkus: use a runtime configuration class. |
| `<service interface="com.example.MyService" ref="myBean"/>` | Remove — OSGi service export not needed outside OSGi |
| `<camelContext xmlns="http://camel.apache.org/schema/blueprint">` | YAML DSL route files (`.camel.yaml`) |
| `<propertyPlaceholder>` inside `<camelContext>` | `application.properties` with `camel.component.*` prefix |

Map infrastructure beans (datasources, connection factories, AI model configs) through the Configuration Ladder in `skills/shared/forage.md` — prefer `forage.*` properties over `camel.beans.*`, `@Bean` classes, or `registry.bind()` in the migration target.

### Karaf / ServiceMix Specifics

| Karaf Construct | Camel 4.x Equivalent |
|----------------|---------------------|
| `features.xml` / `karaf-maven-plugin` | Remove — no Karaf in 4.x. Main records resolved coordinates in module-root `application.properties` under `camel.jbang.dependencies`; Spring Boot/Quarkus use `pom.xml`. |
| `maven-bundle-plugin` (OSGi headers) | Remove — no OSGi in 4.x |
| `MANIFEST.MF` with `Import-Package` / `Export-Package` | Remove |
| `META-INF/spring/*.xml` (Spring DM) | `application.properties` or `@Configuration` classes |
| Karaf JAAS for security | Spring Security or Quarkus Security |

## Spring XML → YAML DSL

### CamelContext Conversion

Spring XML routes wrapped in `<camelContext>` convert to standalone YAML DSL route files.

| Spring XML | YAML DSL |
|-----------|----------|
| `<camelContext xmlns="http://camel.apache.org/schema/spring">` | File: `route-name.camel.yaml` |
| `<route id="myRoute">` | `- route:` with `id: myRoute` |
| `<from uri="timer:tick"/>` | `from: uri: timer:tick` |
| `<to uri="log:out"/>` | `- to: uri: log:out` |
| `<propertyPlaceholder location="classpath:app.properties"/>` | `application.properties` (auto-loaded) |
| `<bean id="..." class="..."/>` | Main: catalog-verify and translate to supported YAML/Forage configuration. Spring Boot/Quarkus: use runtime dependency injection or a catalog-verified YAML `beans:` element. |
| `<routeContext>` (route fragments in separate files) | Include routes directly in YAML files — `routeContext` removed in 4.x |

### Property Syntax

| 2.x / 3.x | 4.x |
|-----------|-----|
| `{{property.key}}` in XML | `{{property.key}}` in YAML (same) |
| `${properties:key}` in Simple | `{{key}}` or `${properties:key}` (verify via MCP) |
| `PropertyPlaceholder` component | Auto-loaded from `application.properties` |

## Java Package Changes

### javax → jakarta

Camel 4.x requires Jakarta EE 10 (from Java EE / javax). All Java source files in the project need these import changes:

| 2.x / 3.x Package | 4.x Package |
|-------------------|-------------|
| `javax.servlet.*` | `jakarta.servlet.*` |
| `javax.ws.rs.*` | `jakarta.ws.rs.*` |
| `javax.jms.*` | `jakarta.jms.*` |
| `javax.mail.*` | `jakarta.mail.*` |
| `javax.xml.bind.*` | `jakarta.xml.bind.*` |
| `javax.enterprise.inject.*` (CDI) | `jakarta.enterprise.inject.*` |
| `javax.annotation.*` | `jakarta.annotation.*` |
| `javax.persistence.*` | `jakarta.persistence.*` |

**Runtime gate:** Retained Java source is supported only for Spring Boot or Quarkus. For Camel Main, every Java or
Blueprint behavior must be fully translated to supported YAML/inline Groovy before planning; otherwise stop and require
runtime reselection. For Spring Boot/Quarkus, flag required `javax` to `jakarta` adaptations in the design spec.

## Runtime Dependency Changes

Camel Main records catalog-verified coordinates under `camel.jbang.dependencies` in module-root
`application.properties` and does not generate a POM. The Maven guidance below applies only to Spring Boot/Quarkus.

### camel-core Module Split

In Camel 3.x, `camel-core` was split into multiple modules. In 4.x, this continues:

| 2.x Dependency | 4.x Equivalent | Notes |
|---------------|----------------|-------|
| `camel-core` (single jar) | `camel-core` (meta-dependency, still works) | Pulls in all core modules |
| — | `camel-core-model` | Route model / DSL definitions |
| — | `camel-core-processor` | EIP processors |
| — | `camel-core-engine` | CamelContext, routing engine |
| — | `camel-core-languages` | Built-in languages (simple, constant, etc.) |

For most migrations, continue using `camel-core` — it pulls in all sub-modules. Only optimize if the user specifically asks for minimal dependencies.

### Common Dependency Renames

| 2.x / 3.x Artifact | 4.x Artifact | Notes |
|--------------------|-------------|-------|
| `camel-spring` | `camel-spring-xml` | If using Spring XML routes |
| `camel-spring` | Not needed for YAML DSL | YAML routes don't need Spring |
| `camel-blueprint` | *(removed)* | No Blueprint support |
| `camel-cdi` | *(removed)* | CDI support removed — use Spring Boot or Quarkus |
| `camel-http4` | `camel-http` | Follows component rename |
| `camel-netty4` | `camel-netty` | Follows component rename |
| `camel-netty4-http` | `camel-netty-http` | Follows component rename |
| `camel-quartz2` | `camel-quartz` | Follows component rename |
| `camel-mongodb3` | `camel-mongodb` | Follows component rename |
| `camel-hystrix` | `camel-resilience4j` | Circuit breaker implementation changed |

### Spring Boot Starter Changes

| 2.x / 3.x | 4.x | Notes |
|-----------|-----|-------|
| `camel-spring-boot-starter` | `camel-spring-boot-starter` | Same name — but version must match Camel 4.x |
| `camel-spring-boot-dependencies` (BOM) | `camel-spring-boot-bom` | BOM artifact renamed |

## Java DSL API Changes

These affect Java `RouteBuilder` classes:

| 2.x / 3.x API | 4.x API | Notes |
|---------------|---------|-------|
| `exchange.getIn()` | `exchange.getMessage()` | `getIn()` deprecated in 3.x, removed in 4.x |
| `exchange.getOut()` | `exchange.getMessage()` | OUT message concept removed |
| `exchange.hasOut()` | *(removed)* | Always false — no OUT |
| `new DefaultExchange(context)` | `ExchangeBuilder.anExchange(context).build()` | Constructor removed |
| `context.getTypeConverterRegistry()` | `context.getTypeConverterRegistry()` | Unchanged |
| `context.getEndpoint("uri")` | `context.getEndpoint("uri")` | Unchanged |

**Note:** Record Java DSL API changes in the design spec Processing Steps section but do NOT attempt to auto-migrate
Java source code. The design spec output targets YAML DSL, and Java changes are documented as implementation actions
for `camel-execute`.

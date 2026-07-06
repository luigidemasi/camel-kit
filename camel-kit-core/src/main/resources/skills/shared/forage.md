# Forage — Configuration-Driven Infrastructure Beans (Shared Guide)

Forage (KaotoIO, `io.kaoto.forage`) provides bean factories configured purely by properties: you declare
`forage.<beanName>.<domain>.*` keys and Forage builds the bean and registers it in the Camel registry as
`#<beanName>`. No Java, no `camel.beans.*` wiring.

**Availability check (do this first):** read `forage.version` from `.camel-kit/config.properties` → `FORAGE_VERSION`.
If the key is absent, or `.camel-kit/.cache/forage/{FORAGE_VERSION}/forage-catalog.json` does not exist, Forage is
NOT available in this workspace — skip rung 1 of the ladder below and note "Forage unavailable" once in your report.

## The Configuration Ladder

For EVERY infrastructure need (datasource, connection factory, AI model/agent, CXF endpoint, …) and component
configuration, walk down and stop at the first rung that works:

1. **Forage-covered** → emit `forage.<name>.<domain>.*` keys (verified against the cached Forage catalog — see
   "Catalog queries" below). Reference the bean from routes/components as `#<name>`.
2. **Not covered by Forage** → configure via `camel.component.<scheme>.*` scalar properties, every key verified per
   `properties-generation.md` §5.1/§5.4 (`camel_catalog_component_doc` / `camel_configuration_validate` with the
   project's `platformBom`).
3. **Component requires an object that scalar properties cannot build in this Camel version** (rare) →
   `camel.beans.<name>=#class:...` as last resort, with a one-line `#`-comment in the properties file stating WHY
   rungs 1–2 don't apply.

Worked rung-3 example — the `amqp` component on Camel < 4.10 (no scalar `host`/`port` options; Forage has no
qpid/AMQP-1.0 module):

```properties
# Rung 3: Forage has no AMQP-1.0/qpid module and camel.component.amqp host/port options require Camel >= 4.10
camel.beans.amqpConnectionFactory=#class:org.apache.qpid.jms.JmsConnectionFactory
camel.beans.amqpConnectionFactory.remoteURI=amqp://{{amqp.host}}:{{amqp.port}}
camel.beans.amqpConnectionFactory.username={{amqp.username}}
camel.beans.amqpConnectionFactory.password={{amqp.password}}
camel.component.amqp.connectionFactory=#amqpConnectionFactory
```

## Naming convention

`forage.<beanName>.<domain>.<property>` — the `<beanName>` segment IS the Camel registry name (`#<beanName>`).
The cached configuration catalog lists keys in DEFAULT-bean form (no name segment), e.g. `forage.jdbc.url`;
for a named bean insert the name after `forage.`: `forage.myDb.jdbc.url` → bean `#myDb`. Domains: `jdbc`, `jms`,
`rabbitmq`, `cxf`, `agent`. Multiple instances = multiple names (`forage.ds1.jdbc.*` + `forage.ds2.jdbc.*`).

Example (rung 1, PostgreSQL datasource):

```properties
forage.myDb.jdbc.db.kind=postgresql
forage.myDb.jdbc.url=jdbc:postgresql://{{db.host}}:{{db.port}}/{{db.name}}
forage.myDb.jdbc.username={{db.username}}
forage.myDb.jdbc.password={{db.password}}
```

Route usage: `sql:...?dataSource=#myDb`. Derived beans on the same datasource:
`forage.myDb.jdbc.transaction.enabled=true` (registers PROPAGATION_* policy beans),
`forage.myDb.jdbc.idempotent.repository.name=myRepo` (registers `#myRepo`),
`forage.myDb.jdbc.aggregation.repository.name=myAgg` (registers `#myAgg`).

Example (rung 1, Artemis connection factory — also the PREFERRED design for Artemis brokers instead of `amqp:`):

```properties
forage.myBroker.jms.kind=artemis
forage.myBroker.jms.broker.url=tcp://{{broker.host}}:61616
forage.myBroker.jms.username={{broker.username}}
forage.myBroker.jms.password={{broker.password}}
forage.myBroker.jms.pool.enabled=true
```

Route usage: `jms:queue:orders?connectionFactory=#myBroker`.

## Catalog queries (never load the whole file)

Cache dir: `.camel-kit/.cache/forage/{FORAGE_VERSION}/`. Two files:
`forage-catalog.json` (factories, bean kinds, per-runtime GAVs) and
`forage-configuration-catalog.json` (all property keys with type/description/required).

```bash
CACHE=.camel-kit/.cache/forage/${FORAGE_VERSION}

# 1. Coverage check — list factories and the bean kinds each supports:
jq -r '.factories[] | .name + ": " + ([.beansByFeature[]?.beans[]?.name] | join(", "))' $CACHE/forage-catalog.json

# 2. Which Camel components a factory serves (for design-time steering):
jq -r '.factories[] | .name + " -> " + (.components | join(", "))' $CACHE/forage-catalog.json

# 3. GAV for the project runtime (variants: base | springboot | quarkus):
jq -r '.factories[] | select(.name=="DataSource") | .variants.base.gav' $CACHE/forage-catalog.json

# 4. Property keys for a module (default-bean form; insert the bean name segment when emitting):
jq -r '.modules[] | select(.artifactId=="forage-jdbc") | .configEntries[] | .name + " (" + .type + ") - " + .description' \
  $CACHE/forage-configuration-catalog.json

# 5. Per-kind bean GAVs (e.g. the postgresql driver module):
jq -r '.factories[] | .beansByFeature[]? | .beans[]? | select(.name=="postgresql")' $CACHE/forage-catalog.json
```

Every `forage.*` key you emit MUST exist in the configuration catalog after removing the bean-name segment.
Do not invent Forage keys — the catalog is the single source of truth (Iron Law 1 carve-out: Forage keys are
verified against THIS catalog, not the Camel catalog).

## Dependencies

- **main / JBang:** run `camel plugin get` and check for `forage`. If the plugin is installed, rely on its
  catalog-driven auto-resolution (no explicit deps needed) — but still record the GAVs in the design spec
  Dependencies table. If NOT installed, add the `base`-variant GAVs (query 3 above) plus the per-kind bean GAVs
  (query 5) to `camel.jbang.dependencies`.
- **spring-boot:** add the `springboot`-variant GAVs (`*-starter`) to `pom.xml`.
- **quarkus:** add the `quarkus`-variant GAVs (`*-camel-quarkus` extensions) to `pom.xml`.

The Forage version is ALWAYS `{FORAGE_VERSION}` from `.camel-kit/config.properties` — never hardcode it.

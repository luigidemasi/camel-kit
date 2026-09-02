# Component Selection Guide

> **Context variables provided by the calling question:**
> - `SYSTEM_DESCRIPTION` — what the user described (e.g., "Kafka topic", "PostgreSQL database")
> - `SYSTEM_ROLE` — "source" or "sink"
> - `CAMEL_VERSION` — from `.camel-kit/config.properties`
> - `RUNTIME` — from `.camel-kit/config.properties` (`project.runtime`, default: `main`)
> - `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via the version mapping table in `skills/shared/mcp-setup.md`

## Procedure: Select and Verify Component

### Step 1: Search Catalog (MCP Required when available)

**Always establish the version binding from `shared/mcp-setup.md`, then search `camel_catalog_components`. Do not suggest a component name from training data before querying the catalog.**

```
Searching Camel {{CAMEL_VERSION}} catalog for matching components...

MCP Tool: camel_catalog_components
Binding probe params: { "limit": 0, "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
Search params: { "label": "[best matching category]", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

Found components available in Camel {{CAMEL_VERSION}}:
1. [component-name] - [description]
2. ...

Based on the user's description, I suggest: [component-name]
```

Then retrieve both its documentation and runtime-specific Maven coordinates under the same binding:

```
MCP Tool: camel_catalog_component_doc
Params: { "component": "[component-name]", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

MCP Tool: camel_catalog_component_maven
Params: { "component": "[component-name]", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

Component: [component-name]
URI syntax:  [exact syntax from catalog]
Maven:       [groupId]:[artifactId]:[artifact version from component_maven]

Component-level options (go in application.properties):
- [option]: [type] — [description]

Endpoint options (go in the URI parameters: block):
- [option]: [type] — [description]
```

Present the suggestion and full option list to the user. Do not synthesize Maven coordinates: `component_doc` does not
return them and different runtimes use different artifacts. If the user prefers a different component, repeat both detail
calls for the new choice before proceeding — never document an option or coordinate from training-data memory.

**Forage steering:** when two components can satisfy the requirement (e.g. `jms` vs `amqp` against an Artemis
broker), prefer the one a Forage factory can wire — check the factory→components mapping in the cached Forage
catalog (`skills/shared/forage.md`, query 2). A protocol-mandated requirement (e.g. AMQP 1.0 interop with
non-Artemis peers) overrides this preference; record the reason in the design spec.

### Step 2: Component Not Found

**If the category search returns no results, broaden the discovery query. Before claiming a named component is absent,
run the complete exact-name check and catalog-version binding from `shared/mcp-setup.md`:**

Try a broader search or a different category keyword. If the component the user named is not found in the Camel {{CAMEL_VERSION}} catalog, inform them:

```
⚠️ Component '[name]' was not found in the Camel {{CAMEL_VERSION}} catalog.
It may not exist in this version, or the name may be different.
Shall I search for alternatives? (yes/no)
```

Do not proceed with an unverified component.

### Fallback (tool call failed)

**Only use this path when catalog calls fail (tool not found, network error, timeout) and a complete exact-name list has
not proved the component absent. A detail-call error alone remains unverified.**

**Tier 1:** After exact-name validation, look up the name in the installed, shipped component-skill registry. Load the
registry's exact `camel-component-<name>/SKILL.md` path only when that literal entry exists. Reject `/`, `\\`, `.`, `..`,
encoded separators, or a path not rooted in the installed component-skill directory; never construct an instruction path
from an unchecked catalog or user string. Use that shipped skill's documentation for URI syntax and options.

**Tier 2:** If no bundled skill exists, inform the user:

```
⚠️ MCP catalog unavailable and no bundled skill for [component].

I can proceed using general knowledge of [component], but:
- Component options will NOT be verified against Camel {{CAMEL_VERSION}}
- The component will not be written to the design spec until it is verified

Proceed with unverified component? (yes/no)
```

If the user agrees to proceed without MCP availability, record an open design question and stop before writing the
component choice. Do not put unverified component names into the design spec.

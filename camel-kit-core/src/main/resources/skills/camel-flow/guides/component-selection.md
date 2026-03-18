# Component Selection Guide

> **Context variables provided by the calling question:**
> - `SYSTEM_DESCRIPTION` — what the user described (e.g., "Kafka topic", "PostgreSQL database")
> - `SYSTEM_ROLE` — "source" or "sink"
> - `CAMEL_VERSION` — from `.camel-kit/config.yaml`
> - `RUNTIME` — from `.camel-kit/config.yaml` (`project.runtime`, default: `main`)
> - `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via the version mapping table in `skills/shared/mcp-setup.md`

## Procedure: Select and Verify Component

### Step 1: Search Catalog (MCP Required when available)

**Always call `camel_catalog_components` first using `CAMEL_VERSION` from config. Do not suggest a component name from training data before querying the catalog.**

```
Searching Camel {{CAMEL_VERSION}} catalog for matching components...

MCP Tool: camel_catalog_components
Params: { "label": "[best matching category]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

Found components available in Camel {{CAMEL_VERSION}}:
1. [component-name] - [description]
2. ...

Based on the user's description, I suggest: [component-name]
```

Then immediately retrieve the full documentation for the suggested component:

```
MCP Tool: camel_catalog_component_doc
Params: { "component": "[component-name]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

Component: [component-name]
URI syntax:  [exact syntax from catalog]
Maven:       org.apache.camel:camel-[name]:{{CAMEL_VERSION}}

Component-level options (go in application.properties):
- [option]: [type] — [description]

Endpoint options (go in the URI parameters: block):
- [option]: [type] — [description]
```

Present the suggestion and full option list to the user. If the user prefers a different component, repeat `camel_catalog_component_doc` for the new choice before proceeding — never document an option from training-data memory.

### Step 2: Red Hat Support Check (MANDATORY when camel-knowledge MCP is available)

After selecting a component, call `camel_rh_build_component_info` to verify it is supported by Red Hat Build of Apache Camel:

```
MCP Tool: camel_rh_build_component_info
Params: { "component": "[component-name]", "runtime": "{{RUNTIME}}" }
```

- **If supported:** note it in the TDD and proceed.
- **If NOT supported:** raise a WARNING to the user. Search for a Red Hat-supported alternative that provides equivalent functionality and present both options. Let the user decide whether to accept the unsupported component or switch to the alternative.
- If the MCP server is not available, skip this step (graceful degradation).

### Step 3: Component Not Found

**If `camel_catalog_components` returns no results for the category:**

Try a broader search or a different category keyword. If the component the user named is not found in the Camel {{CAMEL_VERSION}} catalog, inform them:

```
⚠️ Component '[name]' was not found in the Camel {{CAMEL_VERSION}} catalog.
It may not exist in this version, or the name may be different.
Shall I search for alternatives? (yes/no)
```

Do not proceed with an unverified component.

### Fallback (tool call failed)

**Only use this path when the `camel_catalog_components` or `camel_catalog_component_doc` call fails (tool not found, network error, timeout).**

**Tier 1:** → **Load `skills/camel-component-[name]/SKILL.md`** if it exists. Use its documentation for URI syntax and options.

**Tier 2:** If no bundled skill exists, inform the user:

```
⚠️ MCP catalog unavailable and no bundled skill for [component].

I can proceed using general knowledge of [component], but:
- Component options will NOT be verified against Camel {{CAMEL_VERSION}}
- Red Hat Build support status is UNKNOWN
- The component will be marked as [UNVERIFIED] in the TDD

Proceed with unverified component? (yes/no)
```

If the user agrees, document the component in the TDD with a clear `[UNVERIFIED]` marker in the section header. `/camel-validate` will catch any errors later.

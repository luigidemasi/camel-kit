# Component Loading Guide (Step 2)

> **This guide is loaded by the runtime orchestrator.**
> Context variables provided by the orchestrator:
> - `FLOW_NAME` — the flow being implemented
> - `ROUTE_DIR` — relative optional module route prefix ending in `/`, where route and XSLT files are written
> - `ROUTE_FILE` — full relative path to the route file (`{ROUTE_DIR}{FLOW_NAME}.camel.yaml`)
> - `CAMEL_VERSION` — Camel version from `.camel-kit/config.properties`
> - `RUNTIME` — from `.camel-kit/config.properties` (`project.runtime`, default: `main`)
> - `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via the version mapping table in `skills/shared/mcp-setup.md`
> - `TARGET_MODULE` — module prefix from the design spec flow overview (empty for single-project)

---

## MCP Server Configuration (Recommended)

> **For MCP setup, version mapping, and fallback policy:** see `skills/shared/mcp-setup.md`

The Camel MCP server provides code generation and validation tools for this skill:
- **Component Documentation** (`camel_catalog_component_doc`) - Full options and Maven coords for a component
- **Data Format Documentation** (`camel_catalog_dataformat_doc`) - Full options and Maven coords for a data format
- **Language Documentation** (`camel_catalog_language_doc`) - Full syntax, options, and Maven coords for an expression language
- **EIP List** (`camel_catalog_eips`) - All EIPs available, filterable by category
- **EIP Documentation** (`camel_catalog_eip_doc`) - Full options and YAML DSL usage for a specific EIP
- **URI Validation** (`camel_validate_route`) - Validate endpoint URIs and catch typos before runtime

All catalog calls MUST translate `CAMEL_VERSION` + `RUNTIME` to the correct `camelVersion` parameter using the version mapping table in `skills/shared/mcp-setup.md`. Never pass the raw version or a stripped minor version directly.

---

## Step 2: Load Component Documentation

**MANDATORY — do not skip, do not proceed to Step 3 without verified catalog data for every component.**

Extract every component used in the design spec (source, sink, DLQ, any `to()` targets) and retrieve or consume its full
documentation. This is the single source of truth for URI syntax, endpoint options, component-level options, and Maven
coordinates. **Never use training-data knowledge as a substitute** — component option names, default values, and URI
syntax change between Camel versions and must be verified against the catalog for the project's exact version.

If `camel-execute` provided a pre-verified catalog summary for this wave, use that summary as the verified catalog data
and do not repeat the MCP calls. If no summary is provided, call MCP directly as described below. The summary is only
valid if it records the `camelVersion` it was built with AND that version matches `.camel-kit/config.properties`. If
the version is missing or different, ignore the summary and perform the MCP calls yourself.

### 2.1 With MCP (Required)

**When no pre-verified catalog summary is provided, call `camel_catalog_component_doc` directly for EVERY component —
no exceptions. Do not check for MCP availability upfront.**

**CRITICAL — use the exact component scheme from the route URI.** The component name passed to `camel_catalog_component_doc` MUST be the exact URI scheme used in the route's `from:` or `to:` (e.g., `smtp`, not `mail`; `aws2-sqs`, not `aws`; `kafka`, not `messaging`). Many Camel components share a parent artifact but are distinct components with distinct schemes, options, and property prefixes. Always use the specific scheme — never a parent, alias, or abstract name.

For each component, call `camel_catalog_component_doc` and extract:

| Field | Where to use it |
|-------|----------------|
| `syntax` | URI pattern in `from:` / `to:` |
| `path parameters` (kind=path) | URI path segment, in order |
| `endpoint options` (kind=parameter) | `parameters:` block in YAML |
| `component options` | `camel.component.<name>.<option>` in `application.properties` |
| `groupId` + `artifactId` | Maven dependency in `pom.xml` / `camel.jbang.dependencies` |

```
Loading component documentation via MCP...

Component: [component-name]
  MCP Tool: camel_catalog_component_doc
  Params: { "component": "[component-name]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

  ✓ Syntax:            [exact URI syntax from catalog]
  ✓ Path parameters:   [list with order]
  ✓ Endpoint options:  [all valid parameter names and types]
  ✓ Component options: [all valid component-level config keys]
  ✓ Maven:             org.apache.camel:camel-[name]:{{CAMEL_VERSION}}
```

Repeat for every component before writing any YAML.

**If `camel_catalog_component_doc` returns an error (component not found):**

```
❌ Component '[name]' not found in Camel {{CAMEL_VERSION}} catalog.

Options:
1. Search for the correct component name with camel_catalog_components
2. Confirm the component exists in this Camel version
3. Update the design spec with the correct component before proceeding
```

Do NOT guess a component name or proceed with an unverified component.

### 2.2 Fallback (tool call failed)

**Only use this path when the `camel_catalog_component_doc` call fails (tool not found, network error, timeout).**

```
Loading component documentation from bundled skills...

Component: [component-name]
  ✓ skills/camel-component-[name]/SKILL.md
  ✓ skills/camel-component-[name]/schema.json
  - Syntax:   [from skill file]
  - Maven:    [from skill file]
```

If neither MCP nor a bundled skill exists for a component, **stop and ask the user** to provide the component documentation before continuing. Do not invent option names.

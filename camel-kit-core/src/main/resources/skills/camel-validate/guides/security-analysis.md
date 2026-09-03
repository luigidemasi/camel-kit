# Security Analysis Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being validated
> - `CAMEL_VERSION` — from `.camel-kit/config.properties`
> - `RUNTIME` — project runtime from `.camel-kit/config.properties`
> - `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via the version mapping table in `skills/shared/mcp-setup.md`
> - `ROUTE_FILES` — exact runtime/module-aware relative route paths from the validation inventory
> - `PROPS_FILE` — exact properties path matching the current route's module
>
> **Version mapping:** When calling MCP catalog tools, translate `CAMEL_VERSION` + `RUNTIME` to the correct `camelVersion` and `platformBom` parameters using the version mapping table in `skills/shared/mcp-setup.md`.

Load `shared/camel-security-checklist.md` first. It is authoritative; MCP output never replaces or narrows its five
core rules.

## Stage 8: Security Analysis (MCP Enhanced)

### 8.1 Canonical Security Analysis (Always Required)

For every exact path in `ROUTE_FILES`, apply every clause of all five checklist rules unconditionally to the route, its
matching `PROPS_FILE`, and relevant component, bean, datasource, broker, HTTP client, and TLS configuration. Report each
confirmed violation with the checklist's canonical severity mapping. Run this analysis whether the MCP call succeeds,
fails, or is unavailable.

Keep authentication evidence tied to the inbound endpoint it describes. For example:

- An external outbound `to: http://{{api.endpoint}}` violates the transport-security rule.
- A distinct externally exposed inbound `from: platform-http:/orders` without caller authentication violates the
  authentication rule.

### 8.2 Supplemental MCP Evidence

After the canonical analysis, request additional candidate evidence for each route:

```
MCP Tool: camel_route_harden_context
Params: {
  "route": "[route-yaml-content]",
  "format": "yaml",
  "camelVersion": "{{CAMEL_VERSION}}",
  "platformBom": "{{PLATFORM_BOM}}",
  "runtime": "{{RUNTIME}}"
}
```

Treat every MCP item as supplemental candidate evidence and corroborate it against the route and configuration. When a
confirmed concern maps to a checklist rule, classify it with that rule's canonical severity. Report confirmed MCP
concerns outside the checklist separately as non-checklist findings under the applicable validation category; do not
invent a checklist rule or let them change the canonical security result.

If the tool call fails, note that supplemental MCP evidence is unavailable and continue the complete canonical analysis.
Use `guides/anti-patterns.md` only for examples and non-security checks; it never narrows the checklist.

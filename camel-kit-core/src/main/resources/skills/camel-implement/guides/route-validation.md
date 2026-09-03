# Route Validation Guide (Step 4)

> **Context variables from orchestrator:**
> - `FLOW_NAME`, `CAMEL_VERSION`, `PLATFORM_BOM`, `RUNTIME`

**CRITICAL — You MUST complete this step before generating any supporting files. Do NOT skip it, do NOT proceed on failure without attempting fixes.**

Always attempt `camel_validate_route` directly. If the call fails (tool not found, network error), skip to Step 4.4. The validate-fix-retry loop is non-negotiable when the tool is available.

---

## 4.1 Validate Route Endpoints Against the Catalog

Statically walk the YAML and collect every actual component endpoint URI from `from`, `to`, `toD`, and all other
endpoint-bearing EIP fields, including literal endpoint expressions such as `enrich.expression.constant`. Do not rely on
the tool's route-content extraction for completeness; it is best-effort and can miss endpoint expressions.

For **each** URI in that extracted list, pass the URI and the same **entire content** of `{FLOW_NAME}.camel.yaml` to
`camel_validate_route`. The pinned tool schema requires both fields; never use a null, empty, or dummy value.

```
MCP Tool: camel_validate_route
Params:
{
  "uri": "<current actual endpoint URI from the extracted list>",
  "route": "<full YAML file content>",
  "camelVersion": "{{CAMEL_VERSION}}",
  "platformBom": "{{PLATFORM_BOM}}",
  "runtime": "{{RUNTIME}}"
}
```

Require the top-level `uri` to echo the submitted URI and its top-level `errors` to be absent or empty. Ignore the
aggregate `valid` field: route-content processing can overwrite it. When present, `uriValidations` is only supplementary
best-effort route-extraction evidence and may omit endpoint expressions. A successful result for one URI does not
validate any other extracted endpoint. Continue only after every URI has an explicit successful result.

**Before calling `camel_validate_route`, perform this static check (Rule 0f):**

Scan every `to:` step in the generated YAML. If the `uri` value **or** any `parameters:` value contains `${...}` as a
dynamic endpoint expression, rewrite the step as `toD` with all dynamic values inlined into the URI string — `to` never
evaluates those endpoint expressions at runtime. Do not rewrite component-owned expression syntax: when SQL prepared
parameters such as `:#${...}` and `:#in:${...}` appear in a `to` step, keep static `to` so the SQL component binds them
after endpoint selection. A literal `constant` endpoint expression is likewise safe; do not wrap either form in an outer
Simple expression or evaluate it through `toD`, which would turn data into URI/SQL text. Fix dynamic endpoint expressions
before validation:

```yaml
# WRONG — expression in URI or in parameters:
- to:
    uri: "direct:${header.routeName}"
- to:
    uri: "https://{{host}}/api"
    parameters:
      q: "${header.city}"

# CORRECT
- toD:
    uri: "direct:${header.routeName}"
- toD:
    uri: "https://{{host}}/api?q=${header.city}"

# CORRECT — SQL component prepared binding remains under static to
- to:
    uri: "sql:SELECT * FROM customers WHERE id = :#${exchangeProperty.customerId}"
```

Note: `{{...}}` property placeholders are resolved at startup and are safe in both `to` and `parameters:`.

The tool validates:
- All component schemes exist in the Camel {{CAMEL_VERSION}} catalog
- URI path parameters are in the correct order and format
- All endpoint option names are valid (catches misspellings like `datasource` vs `dataSource`)
- Required parameters are present
- No unknown options are used

## 4.2 Fix -> Re-query -> Retry Loop

**If endpoint catalog validation returns errors, follow this loop — up to 3 attempts:**

```
Attempt N/3: camel_validate_route returned errors:

  ❌ [component]: [error description]
     [suggestion from tool]
```

**For each error, before editing the YAML:**

1. **Re-query the failing component** with `camel_catalog_component_doc` to get the authoritative option list:
   ```
   MCP Tool: camel_catalog_component_doc
   Params: { "component": "[component-name]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
   ```
2. **Identify the correct option name/value** from the catalog response — do not guess.
3. **Apply the fix** to `{FLOW_NAME}.camel.yaml`.
4. **Run `camel_validate_route` again for every extracted endpoint URI** with the updated full file content.
5. If validation passes → proceed to Step 4.3
6. If errors remain → repeat from step 1 (up to 3 total attempts).

**After 3 failed attempts:**

```
Endpoint catalog validation still failing after 3 fix attempts.

Remaining errors:
[list errors]

These errors require manual intervention. Possible causes:
- Component option not available in Camel {{CAMEL_VERSION}}
- The design spec specifies a component configuration that is incompatible

Action required:
1. Review the errors above
2. Check component docs: camel_catalog_component_doc { "component": "...", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
3. Update the design spec if the component choice needs to change
4. Re-run the affected `camel-execute` task once the design spec is corrected
```

Stop and report the errors — do not generate supporting files for a route that fails validation.

## 4.3 Validation Success

```
=== ENDPOINT CATALOG VALIDATION PASSED (attempt N/3) ===

File: {FLOW_NAME}.camel.yaml
  ✓ Every extracted endpoint received an explicit successful catalog result
  ✓ All component schemes valid
  ✓ All endpoint URIs valid
  ✓ All option names verified against catalog
  ✓ No unknown or misspelled options

Proceeding to generate supporting files...
```

## 4.4 Tool Call Failed

```
camel_validate_route call failed — skipping catalog validation.
   Endpoint URIs and option names have NOT been verified against the Camel catalog.
   Run /camel-validate after implementation to catch any errors.
```

Proceed with this warning recorded. If any per-endpoint call failed at the tool/transport level, name those unverified
URIs; never report the route's endpoint catalog validation as complete.

## 4.5 Validation Fails (Final Error)

```
ERROR: Endpoint catalog validation failed

The generated route still has endpoint catalog errors.

Last errors from MCP camel_validate_route:
[show errors]

This may require manual intervention. Possible causes:
- Component typos not auto-fixed
- Invalid endpoint options or parameters
- The design spec contains ambiguous or conflicting requirements
- Component-specific configuration issues

Recommended actions:
1. Review the MCP validation errors carefully
2. Check component documentation via camel_catalog_component_doc
3. Verify endpoint URIs match catalog requirements
4. Review the design spec for clarity and completeness
5. Manually review and fix the generated YAML
```

# Route Validation Guide (Step 4)

> **Context variables from orchestrator:**
> - `FLOW_NAME`, `CAMEL_VERSION`, `PLATFORM_BOM`, `RUNTIME`

**CRITICAL — You MUST complete this step before generating any supporting files. Do NOT skip it, do NOT proceed on failure without attempting fixes.**

Always attempt `camel_validate_route` directly. If the call fails (tool not found, network error), skip to Step 4.4. The validate-fix-retry loop is non-negotiable when the tool is available.

---

## 4.1 Validate the Full Route

Pass the **entire content** of `{FLOW_NAME}.camel.yaml` to `camel_validate_route`:

```
MCP Tool: camel_validate_route
Params:
{
  "route": "<full YAML file content>",
  "camelVersion": "{{CAMEL_VERSION}}",
  "platformBom": "{{PLATFORM_BOM}}",
  "runtime": "{{RUNTIME}}"
}
```

**Before calling `camel_validate_route`, perform this static check (Rule 0f):**

Scan every `to:` step in the generated YAML. If the `uri` value **or** any `parameters:` value contains `${...}` (a Simple language expression), the step must be rewritten as `toD` with all dynamic values inlined into the URI string — `to` never evaluates `${...}` at runtime. Fix these before validation:

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
```

Note: `{{...}}` property placeholders are resolved at startup and are safe in both `to` and `parameters:`.

The tool validates:
- All component schemes exist in the Camel {{CAMEL_VERSION}} catalog
- URI path parameters are in the correct order and format
- All endpoint option names are valid (catches misspellings like `datasource` vs `dataSource`)
- Required parameters are present
- No unknown options are used

## 4.2 Fix -> Re-query -> Retry Loop

**If validation returns errors, follow this loop — up to 3 attempts:**

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
4. **Run `camel_validate_route` again** with the updated file content.
5. If validation passes → proceed to Step 4.3
6. If errors remain → repeat from step 1 (up to 3 total attempts).

**After 3 failed attempts:**

```
Route validation still failing after 3 fix attempts.

Remaining errors:
[list errors]

These errors require manual intervention. Possible causes:
- Component option not available in Camel {{CAMEL_VERSION}}
- TDD specifies a component configuration that is incompatible
- YAML DSL syntax issue not covered by catalog validation

Action required:
1. Review the errors above
2. Check component docs: camel_catalog_component_doc { "component": "...", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
3. Update the TDD if the component choice needs to change
4. Re-run /camel-implement once the TDD is corrected
```

Stop and report the errors — do not generate supporting files for a route that fails validation.

## 4.3 Validation Success

```
=== ROUTE VALIDATION PASSED (attempt N/3) ===

File: {FLOW_NAME}.camel.yaml
  ✓ All component schemes valid
  ✓ All endpoint URIs valid
  ✓ All option names verified against catalog
  ✓ No unknown or misspelled options
  ✓ Route ID present
  ✓ Steps array format (Kaoto compatible)

Proceeding to generate supporting files...
```

## 4.4 Tool Call Failed

```
camel_validate_route call failed — skipping catalog validation.
   Endpoint URIs and option names have NOT been verified against the Camel catalog.
   Run /camel-validate after implementation to catch any errors.
```

Proceed with this warning recorded.

## 4.5 Validation Fails (Final Error)

```
ERROR: Route validation failed

The generated route still has validation errors.

Last errors from MCP camel_validate_route:
[show errors]

This may require manual intervention. Possible causes:
- Component typos not auto-fixed
- Invalid endpoint options or parameters
- TDD contains ambiguous or conflicting requirements
- Component-specific configuration issues

Recommended actions:
1. Review the MCP validation errors carefully
2. Check component documentation via camel_catalog_component_doc
3. Verify endpoint URIs match catalog requirements
4. Review the TDD for clarity and completeness
5. Manually review and fix the generated YAML
```

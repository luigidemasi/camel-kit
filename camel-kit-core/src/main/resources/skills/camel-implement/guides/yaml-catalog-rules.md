# YAML Catalog Verification Rules

These rules govern how generated YAML must reference components, data formats, expression languages, and EIPs. Every name and option MUST be verified against the Camel catalog before use.

> **Context variables:**
> - `CAMEL_VERSION`, `PLATFORM_BOM`, `RUNTIME` — from orchestrator

---

## Rule 0: Use only catalog-verified names

Every component scheme, endpoint option name, component-level option name, and Maven coordinate used in the generated YAML and `application.properties` MUST come from the documentation loaded in Step 2. Do not use option names, parameter names, or URI syntax from training data or memory. If you are unsure whether an option exists or is spelled correctly, call `camel_catalog_component_doc` again before writing it.

## Rule 0b: Data format names and options must be catalog-verified

If the TDD requires `unmarshal` or `marshal`, call `camel_catalog_dataformat_doc` for the data format (e.g. `jackson`, `jaxb`, `csv`, `avro`) with the project Camel version before generating the YAML block. Never assume the data format name, its configuration options, or its Maven coordinates from training data. Example:
```
MCP Tool: camel_catalog_dataformat_doc
Params: { "dataformat": "jackson", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
→ Use the returned options and Maven coordinates in the generated YAML and application.properties
```

## Rule 0c: Expression language names and options must be catalog-verified

Before writing any expression language value in the YAML (`simple`, `jsonpath`, `xpath`, `jq`, `groovy`, etc.), call `camel_catalog_language_doc` for that language with the project Camel version. This ensures the language is available in the project's Camel version, its syntax is correct, and any required Maven dependency (e.g. `camel-jsonpath`, `camel-jq`) is included. Never assume a language name or its syntax from training data. Example:
```
MCP Tool: camel_catalog_language_doc
Params: { "language": "jsonpath", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
→ Use the returned syntax rules and Maven coordinates in the generated YAML
```
If the language requires a separate Maven artifact, add it to `application.properties` (`camel.jbang.dependencies`) and `pom.xml`.

## Rule 0d: EIP names and options must be catalog-verified

Before writing any EIP step in the YAML (`filter`, `split`, `aggregate`, `choice`, `multicast`, `enrich`, `wireTap`, `throttle`, `idempotentConsumer`, etc.), call `camel_catalog_eip_doc` for that EIP with the project Camel version. This ensures the EIP exists in the project's version and that all option names and their types are correct. Never assume EIP option names from training data. Example:
```
MCP Tool: camel_catalog_eip_doc
Params: { "eip": "filter", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
→ Use the returned options and YAML DSL structure in the generated YAML
```

## Rule 0e: HTTP header cleanup between HTTP endpoints

If the route has both an inbound HTTP consumer (`platform-http`, `servlet`, `jetty`, `netty-http`) **and** one or more outbound HTTP producer calls (`http`, `https`), insert a `removeHeaders` step immediately before **each** outbound HTTP call to remove all `CamelHttp*` headers set by the inbound request. Failing to do this causes inbound headers (`CamelHttpMethod`, `CamelHttpPath`, `CamelHttpQuery`, `CamelHttpUri`, `CamelHttpResponseCode`, etc.) to leak into the outbound call and can produce incorrect behaviour.

```yaml
steps:
  # ... processing steps ...

  # REQUIRED before every outbound HTTP call when the route also has an HTTP consumer
  - removeHeaders:
      pattern: "CamelHttp*"

  - to:
      uri: "http:{{backend.host}}/api/endpoint"
```

This rule applies once per outbound HTTP call — if the route calls two different HTTP backends, add `removeHeaders` before each one.

→ For detailed implementation guidance and examples, see orchestrator Step 5.5 which loads `guides/sequential-http-calls.md`.

## Rule 0f: Use `toD` for dynamic URIs and dynamic parameters

`to` resolves its URI **once at startup** as a static string. Any `${...}` Simple expression in a `to` URI **or** in its `parameters:` block is treated as a literal string and is never evaluated at runtime. This applies equally to the URI path and to every value in the `parameters:` map.

**Case 1 — dynamic expression in the URI path:**
```yaml
# WRONG — ${header.routeName} is sent as the literal string "${header.routeName}"
- to:
    uri: "direct:${header.routeName}"

# CORRECT
- toD:
    uri: "direct:${header.routeName}"
```

**Case 2 — dynamic expression in a `parameters:` value:**
```yaml
# WRONG — q: "${header.city}" passes the literal string "${header.city}",
# not the value of the header. parameters: values are always static.
- to:
    uri: "https://{{api.host}}/data/2.5/weather"
    parameters:
      q: "${header.city}"        # ❌ never evaluated
      appid: "{{api.key}}"

# CORRECT — move dynamic values into the URI string and use toD
- toD:
    uri: "https://{{api.host}}/data/2.5/weather?q=${header.city}&appid={{api.key}}&units=metric"
```

For HTTP calls with multiple dynamic query parameters, inline all dynamic values directly in the `toD` URI string. Static `{{placeholder}}` values may stay in the URI string or in `parameters:` — only `${expression}` values must be inlined.

**Enforcement:** scan every `to:` step in the generated YAML. If the `uri` value **or** any `parameters:` value contains `${...}`, rewrite the step as `toD` with all dynamic values interpolated into the URI string. Property placeholders `{{...}}` are safe in both `to` and `parameters:` — they resolve at startup.

## Rule 0g: Never `unmarshal: json:` before a JSON DataMapper step

With `useJsonBody: true`, the `xslt-saxon` component reads the Exchange body as a JSON **string** and passes it to the XSLT `xsl:param` via `json-to-xml()`. The body must be a JSON string or InputStream. If `unmarshal: json:` appears before the DataMapper step, the body is converted to a `java.util.LinkedHashMap`; the component then receives a `Map` instead of a JSON string and cannot pass it to the XSLT param, causing the route to fail.

- Body = JSON String or InputStream → `useJsonBody: true` works correctly
- Body = `LinkedHashMap` (after `unmarshal: json:`) → XSLT param receives nothing usable → failure

`unmarshal: json:` may be placed **after** the DataMapper step if subsequent steps need a typed object.

## Rule 0h: Marshal body before HTTP response

When a route starts with an HTTP consumer (`platform-http`, `servlet`, `jetty`, `netty-http`) and any step in the route unmarshals the body to a Java object (`unmarshal: json:` produces a `LinkedHashMap`; `unmarshal: jaxb:` produces a JAXB object), the HTTP response writer cannot serialize the Java object back to the wire. Add a `marshal` step at the **end** of the route to convert the body back to the response format.

```yaml
# Route with platform-http source and unmarshal mid-route
steps:
  # ... processing steps that need the body as a Map ...

  - log:
      message: "Done processing"

  # REQUIRED — serialize body back to JSON for the HTTP response
  - marshal:
      json:
        library: Jackson
```

**When to apply:** scan the generated route — if the source is an HTTP consumer **and** there is an `unmarshal` step anywhere in the route, add a matching `marshal` step as the last step. Match the data format: `unmarshal: json:` → `marshal: json:`, `unmarshal: jaxb:` → `marshal: jaxb:`.

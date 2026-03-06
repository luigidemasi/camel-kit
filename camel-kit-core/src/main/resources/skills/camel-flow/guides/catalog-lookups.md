# Catalog Lookup Procedures

> **Context variable:** `CAMEL_VERSION` — from `.camel-kit/config.yaml`

## Data Format Lookup (after Question 1)

Whenever a data format is mentioned or needs to be chosen (JSON, XML, CSV, Avro, Protobuf, etc.), call the catalog **before** making any recommendation:

**Step A — List available data formats for the project version:**
```
MCP Tool: camel_catalog_dataformats
Params: { "version": "{{CAMEL_VERSION}}" }
```
This returns all data formats available in Camel {{CAMEL_VERSION}}. Use this list to confirm the format the user mentioned exists in their version, and to suggest alternatives when needed.

**Step B — Get full documentation for the chosen format:**
```
MCP Tool: camel_catalog_dataformat_doc
Params: { "name": "[format-name]", "version": "{{CAMEL_VERSION}}" }
```
This returns: configuration options, Maven coordinates, model class information, and example usage. Record the Maven coordinates and any required configuration in the TDD.

**If user uncertain about format choice:**
→ Show the list from `camel_catalog_dataformats`, optionally load `skills/camel-flow/guides/data-formats.md` for comparison guidance, then ask the user to choose.

**If format is clear:**
→ Still call `camel_catalog_dataformat_doc` to confirm availability in {{CAMEL_VERSION}} and record the Maven dependency. Then skip to Question 2.

---

## EIP Lookup (after Question 3)

**Step A — List available EIPs for the project version, filtered by the relevant category:**
```
MCP Tool: camel_catalog_eips
Params: { "category": "[routing|transformation|routing|messaging|error|…]", "version": "{{CAMEL_VERSION}}" }
```
This returns all EIPs available in Camel {{CAMEL_VERSION}} for the given category. Use this list to confirm the EIP exists in the project's version and to select the most appropriate one.

**Step B — Get full documentation for the chosen EIP:**
```
MCP Tool: camel_catalog_eip_doc
Params: { "name": "[eip-name]", "version": "{{CAMEL_VERSION}}" }
```
This returns: all configuration options, output type, required fields, and YAML DSL usage. Record any non-obvious options in the TDD.

Repeat Step B for every EIP proposed — do not describe EIP options from training data.

**If user unsure about EIP patterns:**
→ Query `camel_catalog_eips` for relevant categories first, then optionally load `skills/camel-flow/guides/eip-catalog.md` for higher-level guidance.

**If user clear on transformations:**
→ Query `camel_catalog_eips` to confirm the EIPs exist in {{CAMEL_VERSION}}, then call `camel_catalog_eip_doc` for each one. Present the confirmed list:
```
Suggested processing steps (verified against Camel {{CAMEL_VERSION}} catalog):

1. validate - [description from catalog]
2. filter   - [description from catalog]
3. [other steps]

Does this match your requirements? (yes/modify)
```

Do NOT include `unmarshal` or `marshal` steps unless the user explicitly said they need typed Java objects. Prefer Kaoto DataMapper via `camel-datamapper-interview`.

---

## Expression Language Lookup (after Question 3)

Whenever the flow requires an expression inside an EIP — `filter`, `choice`/`when`, `setBody`, `setHeader`, `validate`, `log`, routing conditions, or any predicate — the expression language must be chosen from the catalog, not assumed from training data.

**Step A — List available expression languages for the project version:**
```
MCP Tool: camel_catalog_languages
Params: { "version": "{{CAMEL_VERSION}}" }
```
This returns all expression languages available in Camel {{CAMEL_VERSION}} (Simple, JsonPath, XPath, JQ, Groovy, OGNL, SpEL, and others). Use this list to confirm the language exists in the project's version and to suggest the most appropriate one.

**Step B — Get full documentation for the chosen language:**
```
MCP Tool: camel_catalog_language_doc
Params: { "name": "[language-name]", "version": "{{CAMEL_VERSION}}" }
```
This returns: syntax rules, configuration options, Maven coordinates (if the language is in a separate artifact), and example usage. Record any non-default Maven dependency in the TDD.

**Choosing the right language:**
- Use the catalog list to match the data format and use case (e.g. JSON body → JsonPath or JQ; XML body → XPath; simple header/body checks → Simple)
- Never default to `simple` without first confirming it is the best fit for the data format
- If the chosen language requires an additional Maven dependency (e.g. `camel-jsonpath`, `camel-jq`), document it in TDD Section 8 (Dependencies)

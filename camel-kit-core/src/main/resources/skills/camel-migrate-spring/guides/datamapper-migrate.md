# DataMapper Migration Guide

This guide is loaded by `camel-migrate-spring` when a transformer with SpEL-based or complex field mappings is detected in a Spring Integration flow.

It converts Spring Integration transformer logic into the standard `### DataMapper: kaoto-datamapper-{id}` TDD section that `camel-implement` uses to generate Kaoto-compatible XSLT 3.0.

**Context provided by `camel-migrate-spring`:**
- The transformer configuration (SpEL expressions, bean references, or inline logic) for the current flow (already analyzed by `spring-spel-conversion.md`)
- The Spring Integration flow name and its Camel equivalent name
- The Spring Integration project directory structure

---

## Step 1: Detect Format Pair

Determine source and target formats from the transformer configuration.

**Target format** — infer from the transformer output:
- Transformer produces JSON (e.g., returns a Map/POJO serialized to JSON, or `@Transformer(outputChannel="jsonChannel")` with JSON content type) → `JSON_SCHEMA`
- Transformer produces XML (e.g., returns a DOM Document, JAXB object, or XML string) → `XML_SCHEMA`
- Anything else (CSV, binary, etc.) → not supported by DataMapper — flag as TODO, skip this guide

**Source format** — infer in this order:
1. Inbound channel adapter / gateway content type or MIME type (if configured)
2. SpEL `payload` access pattern:
   - `payload.key` / `payload.list` (map/POJO access) → JSON
   - `payload.getElementsByTagName(...)` / XPath-style access → XML
3. Upstream transformer or unmarshaller output type
4. Method parameter type on `@Transformer` annotated method

**If source format cannot be determined:**
```
I need to determine the source data format for the transformer in flow '{flow-name}'.

The transformer reads from `payload` but the format isn't explicit in the Spring Integration config.

What format does this flow receive?
a) JSON
b) XML
c) Other (describe) — note: only JSON and XML are supported by DataMapper
```

---

## Step 2: Locate Schemas

Search the Spring Integration project for existing schema files that match the source/target:

**Where to look:**
- XSD files: `src/main/resources/**/*.xsd`, project root `*.xsd`
- JSON Schema: `src/main/resources/**/*.json`, `**/*.schema.json`
- WSDL files: `src/main/resources/**/*.wsdl` (may embed or reference XSD schemas)
- POJO/DTO classes: `src/main/java/**/dto/**`, `**/model/**` (can be used to infer field names and types)
- Spring XML beans: `<bean class="...">` references with property definitions

Present findings:

```
Searching Spring Integration project for schemas...

Found:
  XSD:         [list paths, or "none"]
  JSON Schema: [list paths, or "none"]
  WSDL:        [list paths, or "none"]
  DTOs/Models: [list paths, or "none"]

Which schema file corresponds to the source payload?  [path or "none"]
Which schema file corresponds to the target output?   [path or "none"]
```

**If a schema is selected:** record path relative to project root. It will be copied to the Camel project root alongside the `.camel.yaml` files.
**If no schema is available:** keep the format detected in Step 1 (`JSON_SCHEMA` or `XML_SCHEMA`) — the schema path is `"none"` but the type reflects the actual data format. Field names will be taken from Step 3 inference. The canonicalize guide computes XPaths from field names without needing schema files.

**`Primitive` is only correct when the data is a truly scalar value** (a single string, number, or boolean — not a JSON object or XML document). If the transformer accesses `payload.field` or `payload.obj.field`, the format is structured and the type must be `JSON_SCHEMA` or `XML_SCHEMA`, even without a schema file.

---

## Step 3: Infer Field Mappings from Transformer Logic

Analyze the transformer configuration and extract field mappings using these patterns. Use the analysis already done by `spring-spel-conversion.md` — do not re-analyze from scratch.

### Direct Field Copy (SpEL)

```
payload.orderId
```
→ `payload.orderId → orderId` | Direct copy | ✅ High confidence

### Renamed Field (SpEL)

```
payload.order.totalAmount → total
```
→ `payload.order.totalAmount → total` | Direct copy, field rename | ✅ High confidence

### Type Conversion / Expression (SpEL)

```
T(Integer).valueOf(payload.quantity)
new java.text.SimpleDateFormat('dd-MM-yyyy').format(payload.timestamp)
```
→ `payload.quantity → quantity` | `Convert to Integer` | ⚠️ Review
→ `payload.timestamp → formattedDate` | `format-dateTime('[D01]-[M01]-[Y0001]')` | ⚠️ Review

### Collection (projection)

```
payload.![name]
payload.items.![{sku: productId, qty: quantity}]
```
→ Collection: `payload[] → items[]` | for-each
→ Within iteration: `productId → sku`, `quantity → qty`

### Conditional (ternary)

```
payload.amount > 1000 ? 'HIGH' : 'NORMAL'
```
→ Conditional: `amount > 1000` → `HIGH` / `NORMAL` | ✅ High confidence

### Bean Method with Field Mapping

```java
@Transformer
public OrderResponse transform(OrderRequest request) {
    OrderResponse response = new OrderResponse();
    response.setOrderId(request.getOrderId());
    response.setCustomerName(request.getCustomer().getName());
    return response;
}
```
→ `request.orderId → orderId` | Direct copy | ✅ High confidence
→ `request.customer.name → customerName` | Direct copy, rename | ✅ High confidence

### Camel Headers (from SI message headers)

```
headers['tenantId']
headers.correlationId
```
→ Source parameters: `headers['tenantId']` → Camel Header `tenantId` (Primitive), `headers.correlationId` → Camel Header `correlationId` (Primitive)

---

## Step 4: Present Inferred Mappings for Confirmation

Generate a unique 8-character hexadecimal mapping ID. Present all inferred mappings with confidence indicators:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DATAMAPPER MAPPING — INFERRED FROM SI TRANSFORMER
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Flow (SI):      {si-flow-name}
Flow (Camel):   {camel-flow-name}
Mapping ID:     kaoto-datamapper-{8hexchars}
Source format:  {XML_SCHEMA | JSON_SCHEMA | Primitive}
Source schema:  {path or "none"}
Target format:  {XML_SCHEMA | JSON_SCHEMA | Primitive}
Target schema:  {path or "none"}

FIELD MAPPINGS
| Source Field        | Src Type | Target Field  | Tgt Type | Transformation               | Confidence |
|---------------------|----------|---------------|----------|------------------------------|------------|
| payload.orderId     | string   | orderId       | string   | Direct copy                  | ✅ High    |
| payload.order.total | decimal  | totalAmount   | decimal  | Direct copy, rename          | ✅ High    |
| payload.timestamp   | datetime | orderDate     | date     | format-dateTime(dd-MM-yyyy)  | ⚠️ Review  |
| payload.items[]     | array    | items[]       | array    | for-each                     | ✅ High    |
| payload.items[].productId | string | items[].sku | string | Direct copy              | ✅ High    |

CONDITIONAL MAPPINGS
| Target Field | Condition     | True Value | False Value | Confidence |
|--------------|---------------|------------|-------------|------------|
| priority     | amount > 1000 | HIGH       | NORMAL      | ✅ High    |

SOURCE PARAMETERS
| Parameter | Source            | Type      | Schema |
|-----------|-------------------|-----------|--------|
| tenantId  | headers['tenantId'] | Primitive | none   |
| correlationId | headers.correlationId | Primitive | none |
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Confidence legend:
  ✅ High   — direct SpEL equivalent, mapping is unambiguous
  ⚠️ Review — inferred from a pattern; please confirm the transformation is correct

Are these mappings correct? (yes / modify)
```

For any ⚠️ Review row: ask the user to confirm or correct the transformation expression before proceeding.

If the user modifies entries: update the table and re-display for final confirmation.

---

## Step 5: Handle Unsupported Transformer Constructs

If the transformer contains constructs that cannot be mapped to the patterns above:

```
⚠️  The following transformer expression(s) could not be automatically mapped:

1. Expression: [{SpEL expression or Java code}]
   Target field: {field name}
   Issue: {reason — e.g. complex Java logic, custom utility method, reflection-based mapping}

   Options:
   a) Describe the intended mapping — I'll record it as a manual expression
   b) Mark as TODO — generate a placeholder comment in the XSLT
   c) Skip this field — handle it separately in a Camel processor bean
```

---

## Step 6: Canonicalize and Save

Generate a unique 8-character hexadecimal mapping ID.

Load `skills/shared/datamapper-canonicalize.md` and follow all steps, passing:
- The confirmed field mappings from Step 4 (source field, src type, target field, tgt type, transformation, how)
- Conditional and collection mappings from Step 3 (if any)
- Source/target types and schema paths from Steps 1–2
- Source parameters from Step 3 (Camel Headers)
- Namespace map (constructed from schema namespaces — include `xs`, `fn`, `xsl` base entries plus `ns0` for XML namespaces)
- The generated mapping ID
- The flow name

The shared guide will:
1. Determine the XSLT pattern and approach
2. Compute XSLT-ready Source XPaths and Target Elements for each field
3. Present the enriched mapping table for user confirmation
4. Write the canonical `### DataMapper:` section to the TDD (with empty mapping guard)

**When done:** return control to `camel-migrate-spring` Step 2.3 to continue producing the TDD for this flow.

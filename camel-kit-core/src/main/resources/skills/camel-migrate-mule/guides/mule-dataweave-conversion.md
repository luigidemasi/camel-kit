# Mule DataWeave Conversion Guide

This guide helps convert MuleSoft DataWeave transformation scripts to Apache Camel equivalents. It is used by the `camel-migrate-mule` sub-skill during Phase 2 (Integration Architect).

---

## DataWeave Versions

### DataWeave 1.0 (Mule 3.x)

- Used in Mule 3.x runtime
- MIME type: `application/dw`
- Embedded in XML as `<dw:transform-message>` elements
- Expression syntax: `%dw 1.0`, `%output application/json`, `%input payload application/xml`
- Field access: `payload.field`, `flowVars.myVar`, `message.inboundProperties.'Content-Type'`
- Conditional: `payload.status when payload.status != null otherwise "UNKNOWN"`
- Array iteration: `payload.items map { name: $.productName, qty: $.quantity }`
- String functions: `upper payload.name`, `lower payload.name`, `trim payload.name`
- Date formatting: `payload.date as :string {format: "yyyy-MM-dd"}`
- Type coercion: `payload.amount as :number`, `payload.id as :string`

### DataWeave 2.0 (Mule 4.x)

- Used in Mule 4.x runtime
- MIME type: `application/java`, embedded in `<ee:transform>`
- Syntax: `%dw 2.0`, `output application/json` (no `%` prefix)
- Field access: `payload.field`, `vars.myVar`, `attributes.headers.'Content-Type'`
- Conditional: `if (payload.status != null) payload.status else "UNKNOWN"`
- Array iteration: `payload.items map (item, index) -> { name: item.productName, qty: item.quantity }`
- String functions: `upper(payload.name)`, `lower(payload.name)`, `trim(payload.name)`
- Date formatting: `payload.date as String {format: "yyyy-MM-dd"}`
- Type coercion: `payload.amount as Number`, `payload.id as String`
- Null handling: `payload.field default "fallback"`

---

## Choosing the Transformation Approach

For each DataWeave transformation found in the Mule project, **ask the user** which approach they prefer:

```
I found a DataWeave transformation in {flow-name}.
Complexity: {simple / moderate / complex}

How would you like to handle it?

  a) DataSonnet — similar syntax to DataWeave, inline in YAML DSL (recommended)
  b) Kaoto DataMapper — visual drag-and-drop mapping in VS Code, generates XSLT
  c) Simple language — only for trivial field renames / constants
  d) Keep as TODO placeholder

Your choice?
```

### When to suggest each option

| Scenario | Suggest | Why |
|----------|---------|-----|
| Any DataWeave script (default) | **DataSonnet** | Closest syntax to DataWeave, inline in YAML, handles JSON/XML/CSV, supports map/filter/reduce |
| User has formal schemas (XSD, JSON Schema) and prefers visual editing | **Kaoto DataMapper** | Visual drag-and-drop, good for large schema-to-schema mappings (EDI, HL7, SWIFT) |
| Trivial: single field rename, constant value, one-liner | **Simple language** | No dependency needed |
| Script uses custom DW modules or unsupported patterns | **TODO placeholder** | Flag for manual implementation |

---

## DataSonnet Conversion

DataSonnet (`camel-datasonnet`) is a Jsonnet-based transformation language already included in Apache Camel. Its syntax is very close to DataWeave, making conversion mostly mechanical.

**Dependency:** `org.apache.camel:camel-datasonnet`

**YAML DSL usage:**
```yaml
- transform:
    datasonnet:
      expression: |
        {
          orderId: body.order_id,
          customer: body.customer.name
        }
      outputMediaType: application/json
```

### DataWeave → DataSonnet Conversion Reference

| DataWeave | DataSonnet | Notes |
|-----------|-----------|-------|
| `payload.field` | `body.field` | `payload` → `body` |
| `payload.nested.field` | `body.nested.field` | Same dot notation |
| `vars.myVar` | `cml.variable('myVar')` | Via CML library |
| `attributes.headers.X` | `cml.header('X')` | Via CML library |
| `attributes.queryParams.X` | `cml.header('X')` | Query params are headers in Camel |
| `p('config.key')` | `cml.properties('config.key')` | Property placeholders |
| `flowVars.x` (DW 1.0) | `cml.variable('x')` | Mule 3.x flow vars |

#### Operators

| DataWeave | DataSonnet | Notes |
|-----------|-----------|-------|
| `"a" ++ " " ++ "b"` | `"a" + " " + "b"` | `++` → `+` for string concat |
| `+`, `-`, `*`, `/` | `+`, `-`, `*`, `/` | Same |
| `==`, `!=`, `>`, `<`, `>=`, `<=` | `==`, `!=`, `>`, `<`, `>=`, `<=` | Same |
| `and`, `or`, `not` | `&&`, `\|\|`, `!` | Symbolic operators |

#### Null handling & defaults

| DataWeave | DataSonnet |
|-----------|-----------|
| `payload.x default "y"` | `cml.default(body.x, "y")` |
| `payload.x default 0` | `cml.default(body.x, 0)` |
| `if (payload.x != null) payload.x else "y"` | `if body.x != null then body.x else "y"` |

#### Type coercion

| DataWeave | DataSonnet |
|-----------|-----------|
| `payload.x as Number` | `cml.toInteger(body.x)` or `cml.toDecimal(body.x)` |
| `payload.x as String` | `std.toString(body.x)` |
| `payload.x as String {format: "yyyy-MM-dd"}` | `cml.formatDate(body.x, "yyyy-MM-dd")` |

#### Collections

| DataWeave | DataSonnet |
|-----------|-----------|
| `arr map ((item) -> expr)` | `std.map(function(item) expr, arr)` or `[expr for item in arr]` |
| `arr map ((item, idx) -> expr)` | `std.mapWithIndex(function(item, idx) expr, arr)` |
| `arr filter ((item) -> cond)` | `std.filter(function(item) cond, arr)` |
| `arr reduce ((item, acc = 0) -> expr)` | `std.foldl(function(acc, item) expr, arr, 0)` — **acc and item are swapped!** |
| `arr flatMap ((item) -> expr)` | `std.flatMap(function(item) expr, arr)` |
| `sizeOf(arr)` | `std.length(arr)` |
| `arr distinctBy ((item) -> item.id)` | Manual `std.foldl` or `camel.distinct(arr)` if using `camel.libsonnet` |
| `arr groupBy ((item) -> item.cat)` | `camel.groupBy(arr, function(item) item.cat)` via `camel.libsonnet` |
| `arr orderBy ((item) -> item.name)` | `std.sort(arr, function(a, b) a.name < b.name)` |

**Important:** In `std.foldl`, the parameter order is `function(accumulator, item)` — the **opposite** of DataWeave's `reduce ((item, accumulator) -> ...)`.

#### String functions

| DataWeave | DataSonnet |
|-----------|-----------|
| `upper(s)` | `std.asciiUpper(s)` |
| `lower(s)` | `std.asciiLower(s)` |
| `trim(s)` | `std.stripChars(s, " \t\n\r")` |
| `s contains "sub"` | `std.length(std.findSubstr("sub", s)) > 0` |
| `s splitBy ","` | `std.split(s, ",")` |
| `arr joinBy ","` | `std.join(",", arr)` |

#### Date/time

| DataWeave | DataSonnet |
|-----------|-----------|
| `now()` | `cml.now()` |
| `now() as String {format: "..."}` | `cml.now("...")` |
| `uuid()` | `cml.uuid()` |

#### Conditionals

| DataWeave | DataSonnet |
|-----------|-----------|
| `if (cond) val1 else val2` | `if cond then val1 else val2` |
| `payload.x match { case is String -> ... }` | `if std.isString(body.x) then ... else ...` |

#### Header & output format

| DataWeave | DataSonnet |
|-----------|-----------|
| `%dw 2.0` | `/** DataSonnet` |
| `output application/json` | `version=2.0` |
| | `output application/json */` |

### Full Conversion Example

**DataWeave 2.0:**
```dataweave
%dw 2.0
output application/json
---
{
    orderId: payload.order_id,
    customerName: payload.customer.first_name ++ " " ++ payload.customer.last_name,
    items: payload.line_items map ((item) -> {
        sku: item.product_sku,
        quantity: item.qty as Number,
        unitPrice: item.unit_price as Number,
        lineTotal: (item.qty as Number) * (item.unit_price as Number)
    }),
    totalAmount: payload.line_items reduce ((item, acc = 0) ->
        acc + ((item.qty as Number) * (item.unit_price as Number))
    ),
    currency: payload.currency default "USD",
    correlationId: vars.correlationId,
    orderDate: now() as String {format: "yyyy-MM-dd'T'HH:mm:ss'Z'"},
    status: "RECEIVED"
}
```

**Equivalent DataSonnet:**
```datasonnet
/** DataSonnet
version=2.0
output application/json
*/
{
    orderId: body.order_id,
    customerName: body.customer.first_name + " " + body.customer.last_name,
    items: std.map(function(item) {
        sku: item.product_sku,
        quantity: cml.toInteger(item.qty),
        unitPrice: cml.toDecimal(item.unit_price),
        lineTotal: cml.toInteger(item.qty) * cml.toDecimal(item.unit_price)
    }, body.line_items),
    totalAmount: std.foldl(function(acc, item)
        acc + (cml.toInteger(item.qty) * cml.toDecimal(item.unit_price)),
        body.line_items, 0),
    currency: cml.default(body.currency, "USD"),
    correlationId: cml.variable('correlationId'),
    orderDate: cml.now("yyyy-MM-dd'T'HH:mm:ss'Z'"),
    status: "RECEIVED"
}
```

**YAML DSL route:**
```yaml
- route:
    id: order-ingestion
    from:
      uri: platform-http:/api/orders
      steps:
        - transform:
            datasonnet:
              expression: |
                /** DataSonnet
                version=2.0
                output application/json
                */
                {
                    orderId: body.order_id,
                    customerName: body.customer.first_name + " " + body.customer.last_name,
                    currency: cml.default(body.currency, "USD"),
                    status: "RECEIVED"
                }
              outputMediaType: application/json
```

---

## TDD Documentation

Regardless of the chosen approach (DataSonnet, DataMapper, or Simple), document the transformation in the TDD so the intent is preserved:

### Section 3.2 — Field Mapping Table (Migration Audit Trail)

| Source Field | Target Field | Transformation | Type | Mule Origin |
|---|---|---|---|---|
| `order_id` | `orderId` | Direct Copy | String | `payload.order_id` |
| `customer.first_name` + `customer.last_name` | `customerName` | String concat | String | `payload.customer.first_name ++ " " ++ payload.customer.last_name` |
| `currency` | `currency` | Default `"USD"` if null | String | `payload.currency default "USD"` |

### Section 3.7 — Transformation Implementation

Document the chosen approach:

```markdown
### Transformation Approach

**Approach:** DataSonnet (inline in YAML DSL)
**Dependency:** `org.apache.camel:camel-datasonnet`
**Script:** Inline `datasonnet:` expression in route step

Converted from DataWeave 2.0 script in `<ee:transform>` element.
Original DataWeave: [reference to Mule XML file and line number]
```

Or for Kaoto DataMapper:

```markdown
### Transformation Approach

**Approach:** Kaoto DataMapper (visual XSLT)
**Dependency:** `org.apache.camel:camel-xslt-saxon`
**Schema source:** [source schema path]
**Schema target:** [target schema path]
**XSLT file:** `{flow-name}-transform.xsl` (generated by Kaoto DataMapper)
```

---

## DataWeave Features → DataSonnet Equivalents

| DataWeave Feature | DataSonnet Equivalent | Complexity |
|------------------|----------------------|------------|
| `map` | `std.map` or array comprehension | Direct |
| `filter` | `std.filter` | Direct |
| `reduce` / `fold` | `std.foldl` (note: params swapped) | Direct |
| `flatMap` | `std.flatMap` | Direct |
| `groupBy` | `camel.groupBy` via `camel.libsonnet` | Direct |
| `distinctBy` | `camel.distinct` via `camel.libsonnet` | Direct |
| `orderBy` | `std.sort` with key function | Direct |
| `sizeOf` | `std.length` | Direct |
| `read` / `write` (format conversion) | Camel `marshal`/`unmarshal` step before/after transform | Route-level |
| Custom DataWeave modules | Re-implement as `.libsonnet` files or Java beans | Manual |
| Pattern matching (`match`) | Chained `if/then/else` | Manual |
| Multi-value selector (`payload.*name`) | `std.map(function(x) x.name, payload)` | Manual |

**Note:** With DataSonnet, `groupBy`, `reduce`, `distinctBy`, and `orderBy` all have direct equivalents — these no longer need Groovy scripts or custom Processors as the previous version of this guide recommended.

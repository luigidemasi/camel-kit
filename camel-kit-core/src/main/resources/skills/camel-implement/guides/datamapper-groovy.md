# DataMapper Groovy — Inline Groovy Script Generation

This guide generates inline Groovy transformation scripts embedded directly in the Camel YAML route. It handles all 4 format pairs: JSON→JSON, XML→JSON, JSON→XML, XML→XML.

> **Prerequisite:** Steps 1 and 1.5 from `datamapper-validation.md` must be completed first.

---

## Step 3: Generate Inline Groovy Script

Read the design spec `### DataMapper:` section. The `**Transformation Engine:**` header says `Groovy (inline)` and the `**Format Pair:**` header indicates which pattern to use.

**IMPORTANT — use semantic field paths from the design spec:** For each field mapping row, translate the **Source Field** column to Groovy dot-notation navigation and the **Target Field** column to the output key/element name. Do not compute XPaths — Groovy uses native object access.

---

### JSON → JSON

The most common case for Groovy-based DataMapper. Parse input JSON, build a Groovy map, serialize to JSON.

**Skeleton:**

```yaml
- transform:
    expression:
      groovy:
        expression: |
          import groovy.json.JsonSlurper
          import groovy.json.JsonOutput
          def src = new JsonSlurper().parseText(request.body)
          def result = [
            {targetField}: src.{sourceNavigation},
            ...
          ]
          JsonOutput.toJson(result)
```

**Concrete example — from design spec rows:**

Given design spec rows:

| Source Field | Src Type | Target Field | Tgt Type | Transformation | How |
|---|---|---|---|---|---|
| payload.orderId | string | orderId | string | Direct copy | Auto |
| payload.name | string | city | string | Direct copy | Auto |
| payload.main.temp | number | temperature | number | format('##.##') | Manual |
| payload.items[] | array | items[] | array | for-each | Auto |
| payload.items[].productId | string | items[].sku | string | Direct copy | Auto |
| payload.items[].quantity | number | items[].qty | number | Direct copy | Auto |

Produces:

```yaml
- transform:
    expression:
      groovy:
        expression: |
          import groovy.json.JsonSlurper
          import groovy.json.JsonOutput
          def src = new JsonSlurper().parseText(request.body)
          def result = [
            orderId: src.orderId,
            city: src.name,
            temperature: String.format('%.2f', src.main?.temp as Double),
            items: src.items?.collect { item -> [
              sku: item.productId,
              qty: item.quantity
            ] }
          ]
          JsonOutput.toJson(result)
```

**Translation rules — Source Field → Groovy navigation:**

| Source Field path | Groovy navigation |
|---|---|
| `payload.field` | `src.field` |
| `payload.obj.field` | `src.obj?.field` |
| `payload.obj.nested.field` | `src.obj?.nested?.field` |
| `payload.arr[]` (for-each) | `src.arr?.collect { item -> [...] }` |
| `payload.arr[].field` (inside collect) | `item.field` |
| `payload.arr[0].field` (single item) | `src.arr?[0]?.field` |

**Key rules:**
- Strip the `payload.` prefix — the remainder maps directly to Groovy dot notation
- Use `?.` (safe navigation) on intermediate segments to avoid NullPointerException
- The root variable after parse is always `src`
- Array iteration uses `collect` with a closure — the closure parameter name should be contextual (e.g., `item`, `entry`, `record`)
- Fields inside `collect` closures use the closure parameter as root (e.g., `item.field`)

**Transformation mapping:**

| Design Spec Transformation | Groovy equivalent |
|---|---|
| Direct copy | `src.field` (no transformation) |
| `format('##.##')` | `String.format('%.2f', src.field as Double)` |
| `upper-case(...)` | `src.field?.toUpperCase()` |
| `lower-case(...)` | `src.field?.toLowerCase()` |
| `concat(a, ' ', b)` | `"${src.firstName} ${src.lastName}"` |
| `format-dateTime(...)` | `java.time.LocalDateTime.parse(src.field).format(java.time.format.DateTimeFormatter.ofPattern('dd-MM-yyyy'))` |
| Arithmetic (`price * qty`) | `src.price * src.quantity` |
| Type cast | `src.field as Double`, `src.field as Integer`, `src.field as String` |

**Conditional mapping (from design spec Conditional Mappings table):**

| Design Spec pattern | Groovy equivalent |
|---|---|
| `if condition → trueVal else falseVal` | `src.amount > 1000 ? 'HIGH' : 'NORMAL'` |
| `when A → X, when B → Y, otherwise → Z` | `['PENDING': 'REVIEW', 'APPROVED': 'PROCESS'].getOrDefault(src.status, 'HOLD')` or nested ternary for simple cases |

---

### XML → JSON

Parse XML source with `XmlSlurper`, build a Groovy map, serialize to JSON.

**Skeleton:**

```yaml
- transform:
    expression:
      groovy:
        expression: |
          import groovy.json.JsonOutput
          import groovy.xml.XmlSlurper
          def src = new XmlSlurper().parseText(request.body)
          def result = [
            {targetField}: src.{xmlElement}.text(),
            ...
          ]
          JsonOutput.toJson(result)
```

**Translation rules — Source Field → Groovy navigation:**

| Source Field path | Groovy navigation |
|---|---|
| `payload.field` | `src.field.text()` |
| `payload.obj.field` | `src.obj.field.text()` |
| `payload.items[]` (for-each) | `src.items.item.collect { node -> [...] }` |
| `payload.items[].field` (inside collect) | `node.field.text()` |

**Key rules:**
- Use `.text()` on leaf elements to get string value
- Cast to target type after `.text()`: `src.total.text() as Double`, `src.count.text() as Integer`
- For namespaced XML elements: use `src.'ns:elementName'.text()` (single-quoted GString with colon)
- If the namespace is default (no prefix in source): access elements directly by local name
- Array iteration: XML repeated elements are accessed as `parent.child.collect { ... }` where `child` is the repeating element name
- Import `groovy.xml.XmlSlurper` explicitly before using it

---

### JSON → XML

Parse JSON source, build XML with `MarkupBuilder`, return as string.

**Skeleton:**

```yaml
- transform:
    expression:
      groovy:
        expression: |
          import groovy.json.JsonSlurper
          import groovy.xml.MarkupBuilder
          def src = new JsonSlurper().parseText(request.body)
          def writer = new StringWriter()
          def xml = new MarkupBuilder(writer)
          xml.{TargetRootElement} {
            {targetElement}(src.{sourceNavigation})
            ...
          }
          writer.toString()
```

**Key rules:**
- Parse JSON with `JsonSlurper`, build XML with `MarkupBuilder`
- Each target element is a method call: `elementName(value)` for leaf elements
- Nested elements use closure syntax: `parent { child(value) }`
- For namespaced output: declare namespace on the builder before the root element
  ```groovy
  xml.mkp.declareNamespace(ns: 'http://example.com/schema')
  xml.'ns:Order' { ... }
  ```
- Array iteration: `src.items?.each { item -> xml.lineItem { sku(item.productId) } }`
- For attributes: `xml.element(attributeName: value) { ... }` or `xml.element('attr': value, 'body text')`

---

### XML → XML

Parse XML source with `XmlSlurper`, build new XML with `MarkupBuilder`.

**Skeleton:**

```yaml
- transform:
    expression:
      groovy:
        expression: |
          import groovy.xml.XmlSlurper
          import groovy.xml.MarkupBuilder
          def src = new XmlSlurper().parseText(request.body)
          def writer = new StringWriter()
          def xml = new MarkupBuilder(writer)
          xml.{TargetRootElement} {
            {targetElement}(src.{sourceElement}.text())
            ...
          }
          writer.toString()
```

**Key rules:**
- Parse with `XmlSlurper`, build with `MarkupBuilder`
- Use `.text()` on source elements to get string values
- Handle namespace transitions: source may have one namespace, target another
- For namespace-preserving copy: use `StreamingMarkupBuilder` with `mkp.yield` for complex cases

---

### Primitive Source or Target

When source-type is `Primitive` (a truly scalar value — single string, number, or boolean):
- Access the body directly: `def src = request.body` (no parser needed)
- Use `src` as a raw value in the result map

When target-type is `Primitive`:
- The Groovy script returns a single value directly (no map/builder wrapping):
  ```groovy
  src.fieldName?.toString()
  ```

**Note:** Primitive-to-Primitive mappings are rare — they typically don't need a DataMapper at all. If both source and target are `Primitive`, consider using a simple Camel `transform:` with a `simple:` expression instead.

---

## Source Parameters (Camel Headers/Variables)

When the design spec lists source parameters, access them via the Exchange:

```groovy
def userId = exchange.getMessage().getHeader('userId')
def tenantId = exchange.getMessage().getHeader('tenantId')
def result = [
  orderId: src.orderId,
  processedBy: userId,
  tenant: tenantId
]
```

**Rules:**
- Headers: `exchange.getMessage().getHeader('paramName')`
- Variables: `exchange.getVariable('varName')`
- Always declare parameter variables before the result map for readability

---

## Common Rules (all format pairs)

- **Always use `request.body`** to access the Exchange body — Camel's Groovy DSL binds this automatically
- **Use `?.` (safe navigation)** on intermediate segments for optional nested fields
- **String interpolation:** `"${src.firstName} ${src.lastName}"` for concat operations
- **No external `.groovy` files** — everything is inline in the Camel YAML
- **Indent the Groovy script** using the YAML block scalar `|` (literal block style)
- **Import every helper used by the script explicitly.** Use `groovy.json.JsonSlurper` and `groovy.json.JsonOutput` for JSON, and `groovy.xml.XmlSlurper`, `groovy.xml.XmlParser`, `groovy.xml.MarkupBuilder`, or `groovy.xml.StreamingMarkupBuilder` for XML as needed.
- **Result must be a String** — `JsonOutput.toJson(result)` for JSON output, `writer.toString()` for XML output. Groovy expressions in Camel must return a value; the last expression in the script is the return value.
- **No `unmarshal: json:` before the transform** — same constraint as XSLT Approach A. If the body is unmarshalled to a `java.util.LinkedHashMap`, `JsonSlurper.parseText()` will fail because it expects a JSON string.

---

## Step 4: Inject Camel YAML Step

**For all format pairs**, inject this block into the route YAML at the DataMapper position:

```yaml
- transform:
    id: kaoto-datamapper-{id}
    expression:
      groovy:
        expression: |
          {generated Groovy script from Step 3}
```

**Key differences from the XSLT YAML step:**
- Uses `transform:` with `groovy:` expression — NOT `to: xslt-saxon:` URI
- No external `.xsl` file referenced
- No `useJsonBody: true` parameter (Groovy handles JSON parsing internally)
- No `setHeader`/`setBody` pre-steps (Groovy reads the body directly via `request.body`)

The `id` field uses the same `kaoto-datamapper-{8hexchars}` mapping ID from the design spec for traceability.

---

> **After Step 4:** Return to `datamapper-validation.md` for Steps 3.5, 5, 6, 7.

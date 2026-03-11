# Spring Integration SpEL Expression Conversion Guide

This guide helps convert Spring Expression Language (SpEL) expressions used in Spring Integration into the TDD Section 3 field mapping tables understood by `/camel-implement`. The guide is used by the `camel-migrate-spring` sub-skill during Phase 2 (Integration Architect).

---

## SpEL Versions and Context

### SpEL in Spring Integration

SpEL (Spring Expression Language) is the primary expression language in Spring Integration. It is used in:
- `<int:transformer expression="...">` — inline transformations
- `<int:filter expression="...">` — message filtering
- `<int:router expression="...">` — routing decisions
- `<int:splitter expression="...">` — splitting logic
- `<int:header-enricher>` with `<int:header expression="...">` — header manipulation
- `<int:service-activator expression="...">` — inline processing
- Java DSL: `.transform(...)`, `.filter(...)`, `.route(...)` with SpEL strings

### SpEL Evaluation Context in Spring Integration

SI provides a standard evaluation context where:
- `payload` — the message payload (`message.getPayload()`)
- `headers` — the message headers map (`message.getHeaders()`)
- `headers['key']` or `headers.key` — access a specific header
- `T(ClassName)` — type reference for static method calls
- `@beanName` — reference to a Spring bean
- `#root` — the `Message<?>` object itself

---

## Decision Matrix: Which Camel Technology to Use

When replacing a SpEL expression, choose the approach based on complexity:

| Expression Complexity | Recommended Approach | Camel Component | Notes |
|---|---|---|---|
| Simple property access (`payload.name`) | Simple language | built-in | `${body.name}` — direct equivalent. |
| Header access (`headers['key']`) | Simple language | built-in | `${header.key}` — direct equivalent. |
| Simple comparison (`payload.amount > 100`) | Simple language predicate | built-in | `${body.amount} > 100` |
| Null check (`payload.field != null`) | Simple language | built-in | `${body.field} != null` |
| Ternary (`payload.x > 0 ? 'A' : 'B'`) | `choice` EIP or Simple | built-in | Prefer `choice/when/otherwise` for clarity. |
| String concatenation (`payload.first + ' ' + payload.last`) | Bean processing or XSLT | `camel-bean` / `camel-xslt-saxon` | Simple language cannot concatenate; use `bean:` or DataMapper. |
| Method call (`payload.toUpperCase()`) | Bean processing | `camel-bean` | `bean:` with method call, or custom processor. |
| Bean reference (`@myBean.process(payload)`) | `bean:myBean?method=process` | `camel-bean` | Direct mapping. |
| Collection projection (`payload.![name]`) | Bean or `split` + extract | `camel-bean` | No Simple equivalent; use bean or Groovy. |
| Collection selection (`payload.?[age > 18]`) | Bean or `split` + `filter` | `camel-bean` | Split collection, filter, re-aggregate. |
| Type check (`payload instanceof T(...)`) | Simple language | built-in | `${body} is 'className'` |
| Static method (`T(Math).max(payload.a, payload.b)`) | Bean processing | `camel-bean` | Requires Java bean or Groovy script. |
| Complex field-level mapping | XSLT via Kaoto DataMapper | `camel-xslt-saxon` | Describe in TDD Section 3.2; `/camel-implement` generates XSLT. |

**Rule of thumb:** If you can express it as rows in the TDD Section 3.2–3.6 tables, do so. `/camel-implement` will generate the XSLT automatically from the tables.

---

## Common SpEL Patterns → TDD Table Equivalents

### Pattern 1: Direct Property Access

**SpEL:**
```
payload.orderId
payload.customer.name
```

**TDD Section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | SI Origin |
|---|---|---|---|---|
| `orderId` | `orderId` | Direct Copy | String | `payload.orderId` |
| `customer.name` | `customerName` | Direct Copy | String | `payload.customer.name` |

---

### Pattern 2: Header Access

**SpEL:**
```
headers['correlationId']
headers.contentType
```

**TDD Section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | SI Origin |
|---|---|---|---|---|
| `header.correlationId` | `correlationId` | Direct Copy (from Camel Header) | String | `headers['correlationId']` |
| `header.contentType` | `contentType` | Direct Copy (from Camel Header) | String | `headers.contentType` |

**Note:** Spring Integration message headers map directly to Camel exchange headers. Use `${header.key}` in Simple language.

---

### Pattern 3: Conditional / Ternary Expression

**SpEL:**
```
payload.amount > 100 ? 'HIGH' : 'LOW'
```

**TDD Section 3.3 Row (Routing Logic):**

| Condition | Route | Camel EIP | SI Original |
|---|---|---|---|
| `${body.amount} > 100` | Set body to `HIGH` | choice/when | `payload.amount > 100 ? 'HIGH' : 'LOW'` |
| otherwise | Set body to `LOW` | choice/otherwise | |

**Or TDD Section 3.4 Row (if used in a transformer):**

| Condition | Source Field | Target Field | True Value | False Value | SI Origin |
|---|---|---|---|---|---|
| `amount > 100` | `amount` | `priority` | `HIGH` | `LOW` | `payload.amount > 100 ? 'HIGH' : 'LOW'` |

---

### Pattern 4: Null-Safe Navigation

**SpEL:**
```
payload?.address?.city
payload.description != null ? payload.description : 'N/A'
```

**TDD Section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | SI Origin |
|---|---|---|---|---|
| `address.city` | `city` | Direct Copy (null-safe) | String | `payload?.address?.city` |
| `description` | `description` | Default `"N/A"` if null | String | `payload.description != null ? payload.description : 'N/A'` |

**Note:** Camel Simple language does not have null-safe navigation. For null checks, use `choice` EIP or a bean processor.

---

### Pattern 5: Collection Projection

**SpEL:**
```
payload.![name]
```

This extracts the `name` property from each element in a collection (similar to `map` in functional programming).

**TDD Section 3.5 Row (Collection Mapping):**

| Collection Field | Item Field | Target Field | Transformation | SI Origin |
|---|---|---|---|---|
| `payload` (collection) | `name` | `names[]` | Projection — extract field | `payload.![name]` |

**Note:** No direct Simple language equivalent. Implement as `split` → extract `${body.name}` → `aggregate` back into a list, or use a bean processor.

---

### Pattern 6: Collection Selection (Filtering)

**SpEL:**
```
payload.?[age > 18]
```

This filters a collection to elements matching the predicate.

**TDD Section 3.5 Row:**

| Collection Field | Item Field | Target Field | Transformation | SI Origin |
|---|---|---|---|---|
| `payload` (collection) | `*` | `adults[]` | Filter: `age > 18` | `payload.?[age > 18]` |

**Note:** Implement as `split` → `filter(simple("${body.age} > 18"))` → `aggregate`, or use a bean processor.

---

### Pattern 7: Type Coercion

**SpEL:**
```
T(Integer).valueOf(payload)
new java.math.BigDecimal(payload.amount)
payload.toString()
```

**TDD Section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | SI Origin |
|---|---|---|---|---|
| `payload` | `value` | Convert to Integer | Integer | `T(Integer).valueOf(payload)` |
| `amount` | `amount` | Convert to BigDecimal | BigDecimal | `new java.math.BigDecimal(payload.amount)` |
| `payload` | `text` | Convert to String | String | `payload.toString()` |

**Note:** Simple language supports basic type conversion with `${body}` type coercion. For complex types, use a bean processor.

---

### Pattern 8: String Operations

**SpEL:**
```
payload.name.toUpperCase()
payload.email.toLowerCase().trim()
payload.firstName + ' ' + payload.lastName
payload.code.substring(0, 3)
```

**TDD Section 3.4 Row (String/Date Functions):**

| Operation | Source | Function | Result | SI Origin |
|---|---|---|---|---|
| Uppercase | `name` | `toUpperCase()` | `name` | `payload.name.toUpperCase()` |
| Lowercase + Trim | `email` | `toLowerCase().trim()` | `email` | `payload.email.toLowerCase().trim()` |
| Concatenation | `firstName`, `lastName` | `concat(" ")` | `fullName` | `payload.firstName + ' ' + payload.lastName` |
| Substring | `code` | `substring(0, 3)` | `codePrefix` | `payload.code.substring(0, 3)` |

**Note:** Simple language does not support string manipulation functions. Use `bean:` processor or Groovy script.

---

### Pattern 9: Bean Reference / Method Invocation

**SpEL:**
```
@myService.process(payload)
@validator.validate(payload, headers['type'])
@converter.toXml(payload)
```

**TDD Section 3.2 Row:**

| Source Field | Target Field | Transformation | Type | SI Origin |
|---|---|---|---|---|
| `payload` | `result` | `bean:myService?method=process` | Object | `@myService.process(payload)` |
| `payload` + `header.type` | `validatedPayload` | `bean:validator?method=validate` | Object | `@validator.validate(payload, headers['type'])` |
| `payload` | `xmlPayload` | `bean:converter?method=toXml` | String (XML) | `@converter.toXml(payload)` |

**Note:** This maps directly to Camel's `bean:` component. The Spring bean can be kept in the Camel project's Spring context.

---

### Pattern 10: Date Formatting

**SpEL:**
```
new java.text.SimpleDateFormat('yyyy-MM-dd').format(payload.timestamp)
T(java.time.format.DateTimeFormatter).ofPattern('dd/MM/yyyy').format(payload.date)
```

**TDD Section 3.4 Row:**

| Operation | Source | Function | Result | SI Origin |
|---|---|---|---|---|
| Date format | `timestamp` | Format `yyyy-MM-dd` | `formattedDate` | `SimpleDateFormat('yyyy-MM-dd').format(payload.timestamp)` |
| Date format | `date` | Format `dd/MM/yyyy` | `formattedDate` | `DateTimeFormatter.ofPattern('dd/MM/yyyy').format(payload.date)` |

---

### Pattern 11: Filtering Predicates

**SpEL in `<int:filter>`:**
```xml
<int:filter expression="payload.status == 'ACTIVE'" />
<int:filter expression="payload.amount > 0 and headers['type'] == 'ORDER'" />
<int:filter expression="payload instanceof T(com.example.OrderMessage)" />
```

**Camel equivalents:**

| SpEL Predicate | Camel Simple Predicate | Notes |
|---|---|---|
| `payload.status == 'ACTIVE'` | `${body.status} == 'ACTIVE'` | Direct mapping. |
| `payload.amount > 0 and headers['type'] == 'ORDER'` | `${body.amount} > 0 && ${header.type} == 'ORDER'` | Use `&&` for logical AND. |
| `payload instanceof T(com.example.OrderMessage)` | `${body} is 'com.example.OrderMessage'` | Type check. |
| `payload.name matches '(?i)test.*'` | Requires `bean:` predicate or `regex` | Simple language has limited regex support. |

---

### Pattern 12: Router Expressions

**SpEL in `<int:router>`:**
```xml
<int:router expression="payload.region" />
<int:router expression="headers['priority'] > 5 ? 'high-priority-channel' : 'normal-channel'" />
```

**Camel equivalent:**

| SpEL Router Expression | Camel Pattern | Notes |
|---|---|---|
| `payload.region` | `choice` with `when` per region value, or `recipientList` | Content-based router. |
| `headers['priority'] > 5 ? 'high' : 'normal'` | `choice` with `when(simple("${header.priority} > 5"))` | Conditional routing. |

---

## How to Extract Field Mappings from SpEL Expressions

When you encounter SpEL expressions during migration analysis, follow this process:

1. **Identify the expression context** — is it in a transformer, filter, router, or header-enricher?
2. **Trace each value expression** to its source:
   - `payload.x` → source field `x` from message body
   - `headers['x']` or `headers.x` → source from Camel header `x`
   - `@beanName.method(...)` → bean invocation, map to `bean:beanName?method=methodName`
   - `T(ClassName).staticMethod(...)` → static call, needs bean or Groovy
3. **Classify the transformation type:**
   - Direct property access → Section 3.2 with "Direct Copy"
   - Method call on payload → Section 3.4 (String/Date functions)
   - Conditional / ternary → Section 3.3 (Routing Logic) or Section 3.4 (Conditional)
   - Collection projection (`![...]`) or selection (`?[...]`) → Section 3.5
   - Type coercion → Section 3.2 Transformation column
   - Bean reference → Section 3.2 with `bean:` transformation
4. **For expressions too complex to decompose:** Flag them with a comment in the TDD and recommend keeping as a `bean:` processor. Document the intended transformation in plain English so the developer can implement it.

---

## SpEL → Camel Simple Language Quick Reference

| SpEL Expression | Camel Simple Expression | Supported |
|---|---|---|
| `payload` | `${body}` | Yes |
| `payload.fieldName` | `${body.fieldName}` | Yes |
| `payload.nested.field` | `${body.nested.field}` | Yes |
| `headers['key']` | `${header.key}` | Yes |
| `headers.key` | `${header.key}` | Yes |
| `payload == 'value'` | `${body} == 'value'` | Yes |
| `payload.field > 100` | `${body.field} > 100` | Yes |
| `payload.field != null` | `${body.field} != null` | Yes |
| `payload instanceof T(Foo)` | `${body} is 'Foo'` | Yes |
| `payload.x > 0 and payload.y > 0` | `${body.x} > 0 && ${body.y} > 0` | Yes |
| `payload.x > 0 or payload.y > 0` | `${body.x} > 0 \|\| ${body.y} > 0` | Yes |
| `!payload.active` | `${body.active} == false` | Yes |
| `payload.toUpperCase()` | Not supported — use `bean:` | No |
| `payload.substring(0, 5)` | Not supported — use `bean:` | No |
| `payload.first + ' ' + payload.last` | Not supported — use `bean:` | No |
| `payload?.field` | Not supported — use `bean:` for null-safe | No |
| `payload.![name]` | Not supported — use `split` + `aggregate` or `bean:` | No |
| `payload.?[active]` | Not supported — use `split` + `filter` + `aggregate` or `bean:` | No |
| `T(Class).method()` | Not supported — use `bean:` | No |
| `@beanName.method(payload)` | `bean:beanName?method=methodName` | Yes (different syntax) |
| `#root` (Message object) | `${exchange}` (Exchange object) | Partial |

---

## SpEL Features Without a Simple Camel Equivalent

| SpEL Feature | Complexity | Recommended Approach |
|---|---|---|
| Collection projection (`![...]`) | Medium | `split` EIP → extract → `aggregate`, or Groovy / bean |
| Collection selection (`?[...]`) | Medium | `split` EIP → `filter` → `aggregate`, or Groovy / bean |
| Null-safe navigation (`?.`) | Low | `choice` EIP with null check, or bean processor |
| String manipulation methods | Low | Bean processor or Groovy script |
| Static method calls (`T(...)`) | Medium | Bean processor wrapping the static method |
| Constructor calls (`new ...()`) | Medium | Bean processor |
| Regex matching (`matches`) | Low | Bean predicate or `regex` in route |
| Inline list creation (`{1,2,3}`) | Low | Bean processor or `constant(List.of(...))` |
| Inline map creation (`{key: value}`) | Low | Bean processor |
| Template expressions (`#{...}`) | Medium | Property placeholder `{{...}}` for config, bean for dynamic |
| `#this` / `#root` references | Medium | Depends on context — usually `${body}` or `${exchange}` |

When these patterns are found, add a TODO note in the relevant TDD section and flag for development team attention.

---

## XSLT Generation Note

When the TDD Section 3.2–3.6 tables are complete, `/camel-implement` will read them and generate:
- An XSLT stylesheet (for XML→XML transformations)
- Or a Groovy script skeleton (for JSON transformations not expressible in XSLT)
- Or simple Camel DSL `setBody`/`setHeader` calls (for simple mappings)

The richer and more complete the TDD mapping tables, the more accurate the generated implementation will be.

---
title: Data Transformation
weight: 2
---

Camel-Kit provides comprehensive support for data transformation with automatic XSLT generation based on field mappings defined during flow design.

## When to Use Data Transformation

Use the transformation feature when your integration needs to:
- Convert between different message formats (JSON, XML)
- Map fields from source schema to destination schema
- Transform nested structures (flatten or nest fields)
- Apply conditional logic during transformation
- Process collections/arrays with iterations
- Use Camel context (Variables/Headers) in transformations

## Defining Field Mappings

During `/camel-flow`, when you mention data transformation, the AI will guide you through an interactive field mapping session:

### Step 1: Provide Schemas

```
Do you have schemas for source and destination messages?

Options:
a) Yes, I have both schemas (XML Schema / JSON Schema)
b) Yes, source schema only
c) Yes, destination schema only
d) No schemas available
```

**Supported schema formats:**
- XML Schema (XSD)
- JSON Schema (draft 7 and earlier)

### Step 2: Automatic Field Matching

The AI analyzes both schemas and proposes automapping:

```
EXACT MATCHES (will auto-map):
- orderId → orderId
- customer.name → customer.name
- items[].productId → items[].productId

POTENTIAL MATCHES (similar names):
- order.customerId → customerId (flatten nested field)
- items[].qty → items[].quantity (name variation)

Should I automatically map all exact matches? (yes/no)
```

### Step 3: Manual Mapping

For unmapped fields, specify the mappings:

```
UNMAPPED SOURCE FIELDS:
- order.timestamp (type: datetime)
- order.total (type: decimal)

UNMAPPED TARGET FIELDS:
- orderDate (type: datetime)
- totalAmount (type: decimal)

Example: "order.timestamp → orderDate (format from ISO to dd-MM-yyyy)"
```

### Step 4: Advanced Features (Optional)

**Parameters (Camel Variables/Headers):**
```
Do you need to use Camel Variables or Message Headers?

Examples:
- userId (Header) - User ID for audit trail
- customerProfile (Variable with schema) - Customer reference data
```

**Conditional Mappings:**
```
Do you need conditional logic?

Examples:
- IF: "If amount > 1000, set priority to 'HIGH'"
- CHOOSE: "When status='PENDING' THEN 'REVIEW'; When status='APPROVED' THEN 'PROCESS'"
```

**Collection Processing:**
```
Do you need array/collection processing?

Examples:
- "Iterate through items[] array and transform each item"
- "Use position tracking with $_index for line numbers"
```

## Transformation Types Supported

| Type | Description | Example |
|------|-------------|---------|
| **Direct Copy** | Simple field-to-field | `orderId` to `orderId` |
| **Nested Flattening** | Extract from nested object | `order.total` to `totalAmount` |
| **Date Formatting** | Convert date formats | ISO 8601 to dd-MM-yyyy |
| **Concatenation** | Combine multiple fields | `firstName` + `lastName` to `fullName` |
| **Calculation** | Mathematical operations | `price * quantity` to `lineTotal` |
| **Conditional (IF)** | Single condition | IF amount > 1000 THEN 'HIGH' |
| **Conditional (CHOOSE)** | Multiple branches | Switch-case style logic |
| **FOR-EACH** | Array iteration | Process each item in collection |
| **Parameter Usage** | Use Camel context | `$userId` from Header |

## Automatic XSLT Generation

During `/camel-implement`, camel-kit automatically generates a Kaoto DataMapper-compatible XSLT file based on your TDD field mappings.

**Generated file:** `{flow-name}-datamapper-{random-id}.xsl`

**Features:**
- XSLT 2.0 for XML transformations
- XSLT 3.0 for JSON transformations (with `fn:json-to-xml()` and `fn:xml-to-json()`)
- All mapping types implemented as XPath expressions
- Parameter declarations for Camel Variables/Headers
- Namespace handling for XML schemas

**Integration in route:**
```yaml
- step:
    id: order-transform-datamapper-step
    steps:
      - to:
          id: order-transform-datamapper-xslt
          uri: "xslt-saxon:order-transform-datamapper-a1b2c3d4.xsl"
          parameters:
            userId: "${header.userId}"
            tenantId: "${header.tenantId}"
```

**Dependency:** `camel-saxon` automatically added to pom.xml

## Best Practices

**DO:**
- Provide schemas when available for better automapping
- Use parameters for reusable context data (userId, tenantId)
- Keep conditionals simple (1-3 levels max)
- Use FOR-EACH for collections rather than hardcoding indices
- Test XPath expressions match actual data structure

**DON'T:**
- Mix XML and JSON in single transformation (use marshal/unmarshal)
- Create deeply nested conditionals (>3 levels)
- Hardcode values that change per environment
- Use XSLT for very large documents (>10MB) - consider streaming

## When to Use Kaoto UI

While camel-kit can generate XSLT automatically for most scenarios, use the [Kaoto](https://kaoto.io/) visual designer when:
- Very complex transformations (>50 fields)
- Need visual validation of mappings
- Custom XSLT functions required
- Troubleshooting generated XSLT
- Advanced XSLT features needed

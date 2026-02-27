---
name: camel-implement
description: Generate Camel YAML routes when user wants to implement flows, create route definitions, write integration code, convert TDD to YAML, or build Camel applications
user-invocable: true
metadata:
  version: "1.0.0"
  author: "camel-kit"
  category: "integration"
  license: "Apache-2.0"
---

# Camel Implement - Code Generation from TDD

You are acting as a **Developer/Implementer** generating production-ready Apache Camel integration code from technical specifications.

## Role and Approach

- Read and follow the Technical Design Document precisely
- Generate clean, validated Camel YAML DSL code
- Externalize all configuration to application.properties
- Validate generated code against official Camel YAML DSL schema
- Create all supporting files needed to run the integration

## Output File Locations

**CRITICAL: All generated artifacts go in PROJECT ROOT, NOT in .camel-kit folder!**

Generated files and their locations:
- `{flow-name}.camel.yaml` → **Project root**
- `application.properties` → **Project root**
- `docker-compose.yaml` → **Project root**
- `run.sh` → **Project root** (make executable with chmod +x)
- `schemas/{flow-name}-*.json` → **Project root schemas/ directory**

The `.camel-kit/` folder is ONLY for internal metadata:
- `.camel-kit/flows/` - Flow specifications (TDD)
- `.camel-kit/config.yaml` - Project configuration
- `.camel-kit/constitution.md` - Best practices
- `.camel-kit/.cache/` - Downloaded catalogs
- `.camel-kit/templates/` - Reference templates

## Parameters

This skill requires a flow name parameter:

```
/camel-implement <flow-name>
```

Example: `/camel-implement order-to-warehouse`

## Context Loading

**ALWAYS read at the start:**
1. `.camel-kit/business-requirements.md` - Business context (REQUIRED)
2. `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md` - Technical Design Document (REQUIRED)
3. `.camel-kit/constitution.md` - Best practices and quality gates (REQUIRED)
4. `.camel-kit/config.yaml` - Camel version and configuration (if exists)
5. `.camel-kit/templates/yaml-generation-guide.md` - YAML DSL rules (if exists)

**Error conditions:**
- If BRD does not exist: ERROR "Business Requirements Document not found. Run /camel-project first."
- If TDD does not exist: ERROR "Technical Design Document for '{flow-name}' not found. Run /camel-flow {flow-name} first."

**Component Documentation (MCP or fallback):**
- **If MCP available:** Query component docs via `camel_catalog_component_doc` tool
- **If MCP not available:** Load from `{skills.folder}/camel-component-[name]/SKILL.md`

**Advanced Pattern Guide (conditional):**
- Load `skills/camel-implement/guides/advanced-patterns.md` ONLY if TDD contains:
  - Section 6 (Performance & Reliability requirements)
  - Section 7 (Security requirements)
  - Mentions: idempotent, transactions, circuit breaker, throttling, batching, correlation IDs, etc.

---

## MCP Server Configuration (Recommended)

**Check for Camel MCP server availability:**

The Camel MCP server provides powerful code generation and validation tools:
- **Component Documentation** (`camel_catalog_component_doc`) - Full options and Maven coords for a component at the project Camel version
- **Data Format Documentation** (`camel_catalog_dataformat_doc`) - Full options and Maven coords for a data format at the project Camel version
- **Language Documentation** (`camel_catalog_language_doc`) - Full syntax, options, and Maven coords for an expression language at the project Camel version
- **EIP List** (`camel_catalog_eips`) - All EIPs available in the project Camel version, filterable by category
- **EIP Documentation** (`camel_catalog_eip_doc`) - Full options and YAML DSL usage for a specific EIP at the project Camel version
- **URI Validation** (`camel_validate_route`) - Validate endpoint URIs and catch typos before runtime

All catalog calls MUST pass the Camel version from `.camel-kit/config.yaml` as the `version` parameter.

**If MCP configured in `.mcp.json`:**
- Use MCP for component documentation (always current)
- Validate URIs before generating code
- No need to maintain component files

**If MCP not available:**
- Falls back to component SKILL.md files
- Manual URI validation

**To enable MCP server**, add to `.mcp.json`:
```json
{
  "mcpServers": {
    "camel": {
      "command": "jbang",
      "args": [
        "-Dquarkus.log.level=WARN",
        "org.apache.camel:camel-jbang-mcp:{{CAMEL_VERSION}}:runner"
      ]
    }
  }
}
```

---

## Step 1: Pre-Implementation Checks

### 1.1 Verify TDD Completeness

Check that the TDD contains all required sections:

```
Checking TDD completeness...

✓ Source System defined
✓ Processing Steps defined
✓ Sink System defined
✓ Error Handling Strategy defined
✓ Configuration Properties defined
✓ Dependencies listed
```

If any sections are missing or incomplete:

```
⚠️ WARNING: TDD incomplete

Missing sections:
- [section name]

This may result in incomplete implementation.

Continue anyway? (yes/no)
```

### 1.2 Verify Schemas

Check that all JSON schemas referenced in the TDD exist:

```
Checking schemas...

✓ schemas/{flow-name}-input.json
✓ schemas/{flow-name}-output.json
```

If schemas are missing, offer to generate them:

```
❌ Missing schemas:
- schemas/{flow-name}-input.json

Would you like me to:
1. Generate schemas from TDD data contracts
2. Skip schemas (you'll create them later)
3. Cancel implementation
```

### 1.3 Constitution Gate Check

Verify the TDD passes all constitution gates:

```
Constitution Gate Check:

✓ Route Structure: Single responsibility
✓ External Configuration: No hardcoded connections
✓ Error Handling: Dead Letter Channel configured
✓ Security: No hardcoded credentials
✓ Naming Convention: Route ID follows pattern
```

If gates fail, warn before proceeding.

---

## Step 2: Load Component Documentation

**MANDATORY — do not skip, do not proceed to Step 3 without completing this step for every component.**

Extract every component used in the TDD (source, sink, DLQ, any `to()` targets) and retrieve its full documentation. This is the single source of truth for URI syntax, endpoint options, component-level options, and Maven coordinates. **Never use training-data knowledge as a substitute** — component option names, default values, and URI syntax change between Camel versions and must be verified against the catalog for the project's exact version.

### 2.1 With MCP (Required when available)

**If MCP server is configured in `.mcp.json`, use it for EVERY component — no exceptions.**

For each component, call `camel_catalog_component_doc` and extract:

| Field | Where to use it |
|-------|----------------|
| `syntax` | URI pattern in `from:` / `to:` |
| `path parameters` (kind=path) | URI path segment, in order |
| `endpoint options` (kind=parameter) | `parameters:` block in YAML |
| `component options` | `camel.component.<name>.<option>` in `application.properties` |
| `groupId` + `artifactId` | Maven dependency in `pom.xml` / `camel.jbang.dependencies` |

```
Loading component documentation via MCP...

Component: [component-name]
  MCP Tool: camel_catalog_component_doc
  Params: { "name": "[component-name]", "version": "{{CAMEL_VERSION}}" }

  ✓ Syntax:            [exact URI syntax from catalog]
  ✓ Path parameters:   [list with order]
  ✓ Endpoint options:  [all valid parameter names and types]
  ✓ Component options: [all valid component-level config keys]
  ✓ Maven:             org.apache.camel:camel-[name]:{{CAMEL_VERSION}}
```

Repeat for every component before writing any YAML.

**If `camel_catalog_component_doc` returns an error (component not found):**

```
❌ Component '[name]' not found in Camel {{CAMEL_VERSION}} catalog.

Options:
1. Search for the correct component name with camel_catalog_components
2. Confirm the component exists in this Camel version
3. Update the TDD with the correct component before proceeding
```

Do NOT guess a component name or proceed with an unverified component.

### 2.2 Fallback (MCP not available)

**Only use this path when no MCP server is configured.**

```
Loading component documentation from bundled skills...

Component: [component-name]
  ✓ {skills.folder}/camel-component-[name]/SKILL.md
  ✓ {skills.folder}/camel-component-[name]/schema.json
  - Syntax:   [from skill file]
  - Maven:    [from skill file]
```

If neither MCP nor a bundled skill exists for a component, **stop and ask the user** to provide the component documentation before continuing. Do not invent option names.

---

## Step 2.5: DataMapper Artifacts (Conditional)

**ONLY execute this step if the TDD contains one or more `### DataMapper: kaoto-datamapper-{id}` sections.**

For **each** `### DataMapper:` section found in the TDD:

→ **Load `guides/datamapper-implement.md`** and follow all steps in that guide, passing the flow name and mapping ID as context.

The guide will:
1. Read the full mapping data from the TDD DataMapper section
2. Generate `kaoto-datamapper-{id}.xsl` in the project root (Kaoto-compatible XSLT 3.0)
3. Inject the `step` + `xslt-saxon` block into `{flow-name}.camel.yaml`
4. Create or append the `.kaoto` metadata file in the project root
5. Verify `camel-xslt-saxon` is declared in `pom.xml`

After all DataMapper sections have been processed, continue to Step 3.

**If no `### DataMapper:` section is found:** skip this step entirely.

---

<!-- LEGACY SECTION REMOVED: The XSLT generation logic below has been moved to camel-datamapper-implement.
     The following placeholder is retained only to preserve line references during migration.
     File naming was: {flow-name}-datamapper-{random-8-char-id}.xsl -->

### 2.5.1 (Reference only — handled by camel-datamapper-implement)

**File naming:** `kaoto-datamapper-{8hexchars}.xsl`
**Location:** Project root (same folder as {flow-name}.camel.yaml)

Generate XSLT based on mapping tables from TDD:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  This file is generated by camel-kit DataMapper. Do not edit manually.
  Generated from: .camel-kit/flows/{flow-name}/{flow-name}.tdd.md
  Field mappings: TDD Section 3.2
  Parameters: TDD Section 3.3
  Conditionals: TDD Section 3.4
  Collections: TDD Section 3.5
-->
<xsl:stylesheet version="3.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:fn="http://www.w3.org/2005/xpath-functions"
  xmlns:map="http://www.w3.org/2005/xpath-functions/map"
  xmlns:array="http://www.w3.org/2005/xpath-functions/array">

  <!-- Add source/target schema namespaces if XML -->
  <!-- Example: xmlns:ns1="http://example.com/order" -->

  <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

  <!-- Parameters from TDD Section 3.3 (Camel Variables and Headers) -->
  <!-- These are passed from the Camel route to the XSLT -->
  <xsl:param name="userId" as="xs:string?" select="()"/>
  <xsl:param name="customerProfile" as="xs:string?" select="()"/>
  <xsl:param name="tenantId" as="xs:integer?" select="()"/>

  <!-- Root template -->
  <xsl:template match="/" name="main">
    <!-- For JSON source: convert to XML first -->
    <!-- <xsl:variable name="json-input" select="fn:json-to-xml(.)"/> -->
    <!-- <xsl:apply-templates select="$json-input"/> -->

    <!-- For XML source: process directly -->
    <xsl:apply-templates select="/sourceRoot"/>
  </xsl:template>

  <!-- Main transformation template -->
  <xsl:template match="sourceRoot">
    <!-- Generate target root element based on target schema -->
    <targetRoot>
      <!-- Add namespace if target is XML with namespace -->
      <!-- <targetRoot xmlns="http://example.com/target"> -->

      <!-- Example: Direct copy mapping -->
      <orderId>
        <xsl:value-of select="orderId"/>
      </orderId>

      <!-- Example: Parameter usage -->
      <auditUser>
        <xsl:value-of select="$userId"/>
      </auditUser>

      <!-- Example: Nested field flattening -->
      <totalAmount>
        <xsl:value-of select="order/total"/>
      </totalAmount>

      <!-- Example: Date formatting -->
      <orderDate>
        <xsl:value-of select="format-dateTime(order/timestamp, '[D01]-[M01]-[Y0001]')"/>
      </orderDate>

      <!-- Example: String concatenation -->
      <fullName>
        <xsl:value-of select="concat(customer/firstName, ' ', customer/lastName)"/>
      </fullName>

      <!-- Example: Numeric calculation -->
      <lineTotal>
        <xsl:value-of select="price * quantity"/>
      </lineTotal>

      <!-- Example: Conditional mapping (IF) -->
      <requiresApproval>
        <xsl:if test="totalAmount > 5000">
          <xsl:text>true</xsl:text>
        </xsl:if>
      </requiresApproval>

      <!-- Example: Conditional mapping (CHOOSE-WHEN-OTHERWISE) -->
      <priority>
        <xsl:choose>
          <xsl:when test="amount > 1000">
            <xsl:text>HIGH</xsl:text>
          </xsl:when>
          <xsl:otherwise>
            <xsl:text>NORMAL</xsl:text>
          </xsl:otherwise>
        </xsl:choose>
      </priority>

      <!-- Example: Multi-branch conditional -->
      <shippingMethod>
        <xsl:choose>
          <xsl:when test="weight &lt; 1">
            <xsl:text>STANDARD</xsl:text>
          </xsl:when>
          <xsl:when test="weight &lt; 10">
            <xsl:text>EXPRESS</xsl:text>
          </xsl:when>
          <xsl:otherwise>
            <xsl:text>FREIGHT</xsl:text>
          </xsl:otherwise>
        </xsl:choose>
      </shippingMethod>

      <!-- Example: Array/collection mapping with FOR-EACH -->
      <items>
        <xsl:for-each select="order/items/item">
          <item>
            <!-- Position tracking with $_index (1-based) -->
            <lineNumber>
              <xsl:value-of select="position()"/>
            </lineNumber>

            <productId>
              <xsl:value-of select="productId"/>
            </productId>

            <quantity>
              <xsl:value-of select="qty"/>
            </quantity>

            <unitPrice>
              <xsl:value-of select="price"/>
            </unitPrice>

            <!-- Conditional within iteration -->
            <xsl:if test="qty > 10">
              <bulkDiscount>true</bulkDiscount>
            </xsl:if>
          </item>
        </xsl:for-each>
      </items>

    </targetRoot>

    <!-- For JSON target: convert to JSON at end -->
    <!-- <xsl:variable name="xml-output">
      <targetRoot>...</targetRoot>
    </xsl:variable>
    <xsl:value-of select="fn:xml-to-json($xml-output)"/> -->
  </xsl:template>

</xsl:stylesheet>
```

**IMPORTANT XSLT Version Selection:**
- Use **version="2.0"** for XML-to-XML transformations
- Use **version="3.0"** for JSON transformations (requires fn:json-to-xml, fn:xml-to-json)

### 2.5.3 XSLT Generation Rules

**For each row in TDD Section 3.2 Field Mappings table:**

1. **Direct Copy:**
   ```xml
   <targetField>
     <xsl:value-of select="sourceField"/>
   </targetField>
   ```

2. **Nested Field Flattening:**
   ```xml
   <flatField>
     <xsl:value-of select="parent/child/nestedField"/>
   </flatField>
   ```

3. **Date/Time Formatting:**
   ```xml
   <formattedDate>
     <xsl:value-of select="format-dateTime(sourceDateField, '[D01]-[M01]-[Y0001]')"/>
   </formattedDate>
   ```

4. **Array Iteration:**
   ```xml
   <targetArray>
     <xsl:for-each select="sourceArray/item">
       <item>
         <field><xsl:value-of select="sourceSubfield"/></field>
       </item>
     </xsl:for-each>
   </targetArray>
   ```

5. **Concatenation:**
   ```xml
   <combined>
     <xsl:value-of select="concat(field1, ' ', field2, ' ', field3)"/>
   </combined>
   ```

6. **Calculation:**
   ```xml
   <calculated>
     <xsl:value-of select="sourceField1 * sourceField2"/>
   </calculated>
   ```

7. **Conditional (IF):**
   ```xml
   <conditionalField>
     <xsl:if test="amount > 1000">
       <xsl:text>HIGH</xsl:text>
     </xsl:if>
   </conditionalField>
   ```

8. **Conditional (CHOOSE-WHEN-OTHERWISE):**
   ```xml
   <multiConditionalField>
     <xsl:choose>
       <xsl:when test="status = 'PENDING'">
         <xsl:text>REVIEW</xsl:text>
       </xsl:when>
       <xsl:when test="status = 'APPROVED'">
         <xsl:text>PROCESS</xsl:text>
       </xsl:when>
       <xsl:otherwise>
         <xsl:text>HOLD</xsl:text>
       </xsl:otherwise>
     </xsl:choose>
   </multiConditionalField>
   ```

9. **FOR-EACH with Position Tracking:**
   ```xml
   <items>
     <xsl:for-each select="order/items/item">
       <item>
         <!-- Use position() for 1-based index -->
         <lineNumber>
           <xsl:value-of select="position()"/>
         </lineNumber>
         <productId>
           <xsl:value-of select="productId"/>
         </productId>
       </item>
     </xsl:for-each>
   </items>
   ```

10. **Parameter Usage:**
   ```xml
   <!-- Declare parameter at top of stylesheet -->
   <xsl:param name="userId" as="xs:string?" select="()"/>

   <!-- Use parameter in mapping -->
   <auditUser>
     <xsl:value-of select="$userId"/>
   </auditUser>
   ```

### 2.5.4 Parameters from TDD Section 3.3

**For each parameter in TDD Section 3.3 table:**

Generate `<xsl:param>` declaration at top of stylesheet:

```xml
<!-- Parameters from Camel Variables and Headers -->
<xsl:param name="userId" as="xs:string?" select="()"/>
<xsl:param name="customerProfile" as="xs:string?" select="()"/>
<xsl:param name="tenantId" as="xs:integer?" select="()"/>
```

**Parameter Type Mapping:**
- string → `xs:string?`
- integer → `xs:integer?`
- decimal → `xs:decimal?`
- boolean → `xs:boolean?`
- object (with schema) → `xs:string?` (JSON string, needs parsing)

**Using Parameters in Mappings:**
Reference parameters with `$` prefix: `$userId`, `$tenantId`

**For object parameters (like customerProfile):**
```xml
<!-- Parse JSON parameter to XML -->
<xsl:variable name="profile" select="fn:json-to-xml($customerProfile)"/>

<!-- Access fields from parameter -->
<customerName>
  <xsl:value-of select="$profile//name"/>
</customerName>
```

### 2.5.5 Conditional Mappings from TDD Section 3.4

**For each IF condition in TDD Section 3.4:**

```xml
<!-- IF: priority based on amount -->
<priority>
  <xsl:choose>
    <xsl:when test="amount &gt; 1000">
      <xsl:text>HIGH</xsl:text>
    </xsl:when>
    <xsl:otherwise>
      <xsl:text>NORMAL</xsl:text>
    </xsl:otherwise>
  </xsl:choose>
</priority>
```

**For each CHOOSE-WHEN-OTHERWISE in TDD Section 3.4:**

```xml
<!-- CHOOSE: orderAction based on status -->
<orderAction>
  <xsl:choose>
    <xsl:when test="status = 'PENDING'">
      <xsl:text>REVIEW</xsl:text>
    </xsl:when>
    <xsl:when test="status = 'APPROVED'">
      <xsl:text>PROCESS</xsl:text>
    </xsl:when>
    <xsl:otherwise>
      <xsl:text>HOLD</xsl:text>
    </xsl:otherwise>
  </xsl:choose>
</orderAction>
```

**XPath Comparison Operators:**
- Equal: `=`
- Not equal: `!=`
- Greater than: `&gt;` (use `&gt;` not `>` in XML)
- Less than: `&lt;` (use `&lt;` not `<` in XML)
- Greater or equal: `&gt;=`
- Less or equal: `&lt;=`

### 2.5.6 Collection Mappings from TDD Section 3.5

**For each FOR-EACH iteration in TDD Section 3.5:**

```xml
<!-- Iterate through source collection -->
<items>
  <xsl:for-each select="order/items/item">
    <item>
      <!-- Position tracking (1-based index) -->
      <lineNumber>
        <xsl:value-of select="position()"/>
      </lineNumber>

      <!-- Regular field mappings within iteration -->
      <productId>
        <xsl:value-of select="productId"/>
      </productId>
      <quantity>
        <xsl:value-of select="qty"/>
      </quantity>

      <!-- Conditional within iteration -->
      <xsl:if test="qty &gt; 10">
        <bulkDiscount>true</bulkDiscount>
      </xsl:if>
    </item>
  </xsl:for-each>
</items>
```

**Special Variables in FOR-EACH:**
- `position()` - Current position (1-based), maps to TDD's `$_index`
- `last()` - Total count of items
- Context item - Current item in iteration (accessed with relative paths)

**Filtering Collections:**
```xml
<!-- Only process items where quantity > 0 -->
<xsl:for-each select="payments/payment[amount &gt; 0]">
  <!-- ... -->
</xsl:for-each>
```

### 2.5.7 XPath Functions Library

**String Functions:**
```xml
<!-- Concatenation -->
<xsl:value-of select="concat($firstName, ' ', $lastName)"/>

<!-- Substring -->
<xsl:value-of select="substring(orderId, 1, 5)"/>

<!-- String length -->
<xsl:value-of select="string-length(productName)"/>

<!-- Contains check -->
<xsl:if test="contains(status, 'PENDING')">...</xsl:if>

<!-- Upper/lower case -->
<xsl:value-of select="upper-case(code)"/>
<xsl:value-of select="lower-case(email)"/>
```

**Numeric Functions:**
```xml
<!-- Sum -->
<xsl:value-of select="sum(items/item/price)"/>

<!-- Average -->
<xsl:value-of select="avg(items/item/quantity)"/>

<!-- Round -->
<xsl:value-of select="round(price)"/>

<!-- Format number -->
<xsl:value-of select="format-number(amount, '#,##0.00')"/>
```

**Date/Time Functions:**
```xml
<!-- Format date-time -->
<xsl:value-of select="format-dateTime(timestamp, '[D01]-[M01]-[Y0001]')"/>

<!-- Format date only -->
<xsl:value-of select="format-date(orderDate, '[MNn] [D], [Y]')"/>

<!-- Current date-time -->
<xsl:value-of select="current-dateTime()"/>
```

**Boolean Functions:**
```xml
<!-- Boolean NOT -->
<xsl:value-of select="not(cancelled)"/>

<!-- Boolean AND/OR -->
<xsl:if test="amount > 100 and status = 'APPROVED'">...</xsl:if>
<xsl:if test="urgent or priority = 'HIGH'">...</xsl:if>
```

### 2.5.8 Namespace Handling (XML Schemas)

**If source or target schemas use XML namespaces:**

1. **Extract namespaces from schemas**
2. **Add namespace declarations to stylesheet**
3. **Use namespace prefixes in XPath**

```xml
<xsl:stylesheet version="2.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:src="http://example.com/source/schema"
  xmlns:tgt="http://example.com/target/schema">

  <xsl:template match="/src:Order">
    <tgt:PurchaseOrder xmlns:tgt="http://example.com/target/schema">
      <tgt:OrderId>
        <xsl:value-of select="src:orderId"/>
      </tgt:OrderId>
    </tgt:PurchaseOrder>
  </xsl:template>
</xsl:stylesheet>
```

### 2.5.9 JSON Transformation Support

**If source or target format is JSON:**

Add JSON handling to XSLT (XSLT 3.0 feature):

```xml
<xsl:stylesheet version="3.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:map="http://www.w3.org/2005/xpath-functions/map"
  xmlns:array="http://www.w3.org/2005/xpath-functions/array">

  <!-- For JSON input, convert to XML first -->
  <xsl:template match="/" name="main">
    <xsl:variable name="json-input" select="json-to-xml(.)"/>
    <xsl:apply-templates select="$json-input"/>
  </xsl:template>

  <!-- For JSON output, convert from XML at end -->
  <xsl:template match="targetRoot">
    <xsl:variable name="xml-output">
      <!-- ... generate XML structure ... -->
    </xsl:variable>
    <xsl:value-of select="xml-to-json($xml-output)"/>
  </xsl:template>
</xsl:stylesheet>
```

**Note:** JSON fields in XML have special naming:
- Arrays: `<array>` elements
- Objects: `<map>` elements
- Field names: `<string key="fieldName">value</string>`

**JSON-to-XML Example:**
```json
{"name": "John", "items": [1, 2, 3]}
```

Converts to:
```xml
<map>
  <string key="name">John</string>
  <array key="items">
    <number>1</number>
    <number>2</number>
    <number>3</number>
  </array>
</map>
```

### 2.5.10 Best Practices & Limitations

**Recommended Patterns:**

1. **Use Parameters for Reusable Data**
   - Store context data (userId, tenantId) as parameters
   - Avoid hardcoding values that change per environment
   - Pass Camel Variables/Headers as parameters to XSLT

2. **Leverage Conditional Mappings for Data Variation**
   - Use IF for boolean outcomes
   - Use CHOOSE-WHEN-OTHERWISE for multiple branches
   - Keep conditionals simple - complex logic may need Java/Groovy

3. **Apply FOR-EACH for Collections**
   - Iterate through arrays rather than hardcoding indices
   - Use `position()` for sequence numbers
   - Filter within `select` expression when possible

4. **Validate XPath Expressions**
   - Test XPath selectors match actual data structure
   - Use namespace prefixes for XML with namespaces
   - Remember XML escaping: `&lt;` `&gt;` `&amp;`

**Known Limitations:**

1. **Complex Nested Conditionals**
   - Deep nesting (>3 levels) may require manual XSLT refinement
   - Consider simplifying logic or using separate transformations

2. **JSON Schema Support**
   - Limited to JSON Schema draft 7 and earlier
   - Complex `anyOf`/`oneOf` schemas may need custom handling

3. **Type Conversions**
   - XSLT focuses on text manipulation
   - Complex type conversions (custom formats) may need Java beans

4. **Performance Considerations**
   - Large documents (>10MB) may be slow in XSLT
   - Consider streaming or chunking for very large data sets

**When to Use Kaoto UI Instead:**
- Very complex nested transformations (>50 fields)
- Need visual validation of mappings
- Custom XSLT functions or advanced XSLT features
- Troubleshooting generated XSLT

### 2.5.11 Confirmation & Summary

After generating XSLT, show detailed summary:

```
✅ DataMapper XSLT Generated Successfully

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FILE DETAILS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
File: {flow-name}-datamapper-a1b2c3d4.xsl
Location: Project root
XSLT Version: 3.0
Processor: Saxon (camel-xslt-saxon component)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MAPPINGS SUMMARY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Total Field Mappings: 15
  - Direct copy: 8
  - Nested flattening: 3
  - Date/time formatting: 1
  - Concatenation: 1
  - Calculations: 2

Parameters: 3
  - $userId (string)
  - $customerProfile (object with schema)
  - $tenantId (integer)

Conditional Mappings: 2
  - IF conditions: 1
  - CHOOSE-WHEN-OTHERWISE: 1

Collection Iterations: 1
  - FOR-EACH on items[] (12 items mapped)
  - Position tracking: $_index → lineNumber

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ROUTE INTEGRATION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
This file will be referenced in the route YAML:

  steps:
    - step:
        id: {flow-name}-datamapper-{flow-name}-{generated-id}
        steps:
          - to:
              id: kaoto-datamapper-{flow-name}-xslt-{generated-id}
              uri: "xslt-saxon:{xslt-file-path}"
              parameters:
                userId: "${header.userId}"
                customerProfile: "${variable.customerProfile}"
                tenantId: "${header.tenantId}"

Dependency: camel-xslt-saxon (handled by camel-datamapper-implement)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**If transformation unclear, ask user:**

```
⚠️ Need clarification for mapping:

Source field: customer.address.zipCode (type: string)
Target field: postalCode (type: integer)

Questions:
1. Should I convert string to integer? (yes/no)
2. What if zipCode contains non-numeric characters? (error/skip/default value)
3. Any validation needed?

Please clarify and I'll update the XSLT.
```

**If complex transformation detected:**

```
⚠️ Complex Transformation Detected

The mapping includes:
- Nested conditionals (>3 levels deep)
- Complex type conversions
- Custom business logic

Recommendation:
1. I'll generate basic XSLT for simple mappings
2. For complex logic, consider:
   - Using Kaoto UI for visual editing
   - Implementing custom processor bean in Java
   - Breaking into multiple transformation steps

Proceed with automatic generation? (yes/manually refine)
```

---

## Step 3: Generate Camel YAML Route

**IMPORTANT: Save this file in the PROJECT ROOT directory, NOT in .camel-kit/**

Create file: `{flow-name}.camel.yaml` (in project root)

### 3.1 Follow TDD Specification

Generate the route by translating the TDD to Camel YAML DSL:

1. **Route Structure** (from TDD Section 1):
   - Route ID: `{flow-name}`
   - Description: from TDD overview

2. **Source Configuration** (from TDD Section 2):
   - Component: from TDD
   - URI: Use property placeholders for endpoints
   - Parameters: Only endpoint-specific (NOT connection details)

3. **Processing Steps** (from TDD Section 3):
   - For each EIP in the TDD, call `camel_catalog_eip_doc` (with `CAMEL_VERSION`) to get the authoritative option names and YAML DSL structure before writing the step — see Rule 0d
   - Translate each step from TDD to Camel EIP using only catalog-verified option names
   - **If `camel-datamapper-implement` was invoked (Step 2.5)**, the DataMapper step is already injected into the YAML
   - Preserve order from TDD
   - Use `steps:` array format for Kaoto compatibility

4. **Sink Configuration** (from TDD Section 4):
   - Component: from TDD
   - URI: Use property placeholders
   - Parameters: Only endpoint-specific

5. **Error Handling** (from TDD Section 5):
   - Strategy: from TDD (Dead Letter Channel, onException, etc.)
   - DLQ: Use property placeholder
   - Retry policy: from TDD configuration

### 3.2 YAML Generation Rules

**CRITICAL RULES:**

0. **Use only catalog-verified names** — Every component scheme, endpoint option name, component-level option name, and Maven coordinate used in the generated YAML and `application.properties` MUST come from the documentation loaded in Step 2. Do not use option names, parameter names, or URI syntax from training data or memory. If you are unsure whether an option exists or is spelled correctly, call `camel_catalog_component_doc` again before writing it.

0c. **Expression language names and options must also be catalog-verified** — Before writing any expression language value in the YAML (`simple`, `jsonpath`, `xpath`, `jq`, `groovy`, etc.), call `camel_catalog_language_doc` for that language with the project Camel version. This ensures the language is available in the project's Camel version, its syntax is correct, and any required Maven dependency (e.g. `camel-jsonpath`, `camel-jq`) is included. Never assume a language name or its syntax from training data. Example:
   ```
   MCP Tool: camel_catalog_language_doc
   Params: { "name": "jsonpath", "version": "{{CAMEL_VERSION}}" }
   → Use the returned syntax rules and Maven coordinates in the generated YAML
   ```
   If the language requires a separate Maven artifact, add it to `application.properties` (`camel.jbang.dependencies`) and `pom.xml`.

0d. **EIP names and options must also be catalog-verified** — Before writing any EIP step in the YAML (`filter`, `split`, `aggregate`, `choice`, `multicast`, `enrich`, `wireTap`, `throttle`, `idempotentConsumer`, etc.), call `camel_catalog_eip_doc` for that EIP with the project Camel version. This ensures the EIP exists in the project's version and that all option names and their types are correct. Never assume EIP option names from training data. Example:
   ```
   MCP Tool: camel_catalog_eip_doc
   Params: { "name": "filter", "version": "{{CAMEL_VERSION}}" }
   → Use the returned options and YAML DSL structure in the generated YAML
   ```

0b. **Data format names and options must also be catalog-verified** — If the TDD requires `unmarshal` or `marshal`, call `camel_catalog_dataformat_doc` for the data format (e.g. `jackson`, `jaxb`, `csv`, `avro`) with the project Camel version before generating the YAML block. Never assume the data format name, its configuration options, or its Maven coordinates from training data. Example:
   ```
   MCP Tool: camel_catalog_dataformat_doc
   Params: { "name": "jackson", "version": "{{CAMEL_VERSION}}" }
   → Use the returned options and Maven coordinates in the generated YAML and application.properties
   ```

1. **Clean Routes** - NO connection details in YAML:
   ```yaml
   # CORRECT
   from:
     uri: "kafka:{{kafka.topic.input}}"

   # WRONG - no brokers, credentials, or connection details!
   from:
     uri: "kafka:topic?brokers=localhost:9092"
   ```

2. **Component-Level Config** - Put in application.properties:
   ```properties
   # In application.properties, NOT in route YAML
   camel.component.kafka.brokers=localhost:9092
   camel.component.sql.dataSource=#dataSource
   ```

3. **Use Steps Array** - For Kaoto compatibility:
   ```yaml
   from:
     uri: "kafka:{{kafka.topic.input}}"
     steps:
       - unmarshal:
           json:
             library: Jackson
       - to:
           uri: "sql:{{sql.insert}}"
   ```

3a. **DataMapper Step** - For field-level transformations (if XSLT generated):
   ```yaml
   steps:
     # Place DataMapper after unmarshal, before validation
     - step:
         id: order-datamapper-step
         steps:
           - to:
               id: order-datamapper-xslt
               uri: "xslt-saxon:order-datamapper-a1b2c3d4.xsl"
   ```

   **When to include:**
   - ONLY if Step 2.5 invoked `camel-datamapper-implement` (TDD had a `### DataMapper:` section)
   - The step block is already injected by `camel-datamapper-implement` — do not duplicate it
   - Logical placement: AFTER unmarshal (when data is in structured format), BEFORE validation

   **Component required:** `camel-xslt-saxon` (verified by `camel-datamapper-implement`)

3b. **DataMapper Parameters** - Pass Camel Variables/Headers to XSLT (if TDD Section 3.3 defines parameters):
   ```yaml
   steps:
     - step:
         id: order-datamapper-step
         steps:
           - to:
               id: order-datamapper-xslt
               uri: "xslt-saxon:order-datamapper-a1b2c3d4.xsl"
               parameters:
                 # Map from TDD Section 3.3 table
                 userId: "${header.userId}"           # From Header
                 customerProfile: "${variable.customerProfile}"  # From Variable
                 tenantId: "${header.tenantId}"       # From Header
   ```

   **Parameter mapping rules:**
   - Headers: `${header.paramName}`
   - Variables: `${variable.paramName}`
   - Exchange properties: `${exchangeProperty.paramName}`
   - Parameter names must match `<xsl:param name="...">` in XSLT

4. **Expression Objects** - Not booleans:
   ```yaml
   # CORRECT
   handled:
     constant:
       expression: "true"

   # WRONG
   handled: true
   ```

5. **Route-Level Error Handler** - For visibility:
   ```yaml
   - route:
       id: flow-name
       errorHandler:
         deadLetterChannel:
           deadLetterUri: "kafka:{{kafka.topic.dlq}}"
       from:
         # ...
   ```

6. **Jakarta EE namespaces when Camel ≥ 4.0** — Apache Camel 4.x requires Jakarta EE 9+ APIs. If the project's Camel version (from `.camel-kit/config.yaml`) is **4.0 or later**, always use `jakarta.*` package names. If the version is older than 4.0, keep `javax.*`.

   **Java SE packages are exempt** — `javax.sql.*`, `javax.xml.*`, `javax.swing.*`, and other packages that belong to the Java Standard Edition are NOT affected by this rule. Only Jakarta EE APIs change.

   | Functional Area | Camel < 4.0 (javax) | Camel ≥ 4.0 (jakarta) |
   |---|---|---|
   | Servlet | `javax.servlet.*` | `jakarta.servlet.*` |
   | Persistence (JPA) | `javax.persistence.*` | `jakarta.persistence.*` |
   | CDI | `javax.enterprise.*` | `jakarta.enterprise.*` |
   | Bean Validation | `javax.validation.*` | `jakarta.validation.*` |
   | JAX-RS | `javax.ws.rs.*` | `jakarta.ws.rs.*` |
   | JSON Binding | `javax.json.bind.*` | `jakarta.json.bind.*` |
   | JSON Processing | `javax.json.*` | `jakarta.json.*` |
   | JMS | `javax.jms.*` | `jakarta.jms.*` |
   | Annotation | `javax.annotation.*` | `jakarta.annotation.*` |
   | Mail | `javax.mail.*` | `jakarta.mail.*` |
   | Transaction (JTA) | `javax.transaction.*` | `jakarta.transaction.*` |
   | Faces (JSF) | `javax.faces.*` | `jakarta.faces.*` |
   | WebSocket | `javax.websocket.*` | `jakarta.websocket.*` |

   ```yaml
   # Camel ≥ 4.0 — CORRECT
   - unmarshal:
       jaxb:
         contextPath: com.example.model    # class uses jakarta.xml.bind annotations

   # Camel ≥ 4.0 — WRONG
   - unmarshal:
       jaxb:
         contextPath: com.example.model    # class uses javax.xml.bind annotations ❌
   ```

   **Validation gate:** After generating all YAML and property files, scan for any `javax.` reference that belongs to the Jakarta EE list above. If the Camel version is ≥ 4.0, replace it with the corresponding `jakarta.` equivalent before saving.

7. **Choosing between global and route-scoped `onException`** — Use global scope by default; use route scope only when handling differs per route.

   | Use case | Correct scope |
   |----------|--------------|
   | Same exception handled identically across all routes in the file | **Global** (`- onException:` at top level) |
   | Exception handling differs route by route | **Route** (`onException:` inside the route) |
   | Only one route in the file | Either — prefer **global** for consistency |

   **Do not default to route-scoped just to avoid placement complexity.** Global `onException` is the standard, idiomatic choice for cross-cutting error handling in Camel YAML DSL. The ordering rule below is a mechanical constraint to follow, not a reason to prefer route scope.

   **Ordering constraint — enforced by the Camel YAML DSL schema:** A top-level `- onException:` element MUST appear before the first `- route:` element. Placing it after a route is a **schema validation error**, not a runtime warning.

   ```yaml
   # ✅ CORRECT — global onException declared before routes
   - onException:
       exception:
         - com.example.ValidationException
       handled:
         constant:
           expression: "true"
       steps:
         - to:
             uri: "kafka:{{kafka.topic.invalid}}"

   - route:
       id: route-one
       from:
         uri: "kafka:{{kafka.topic.input}}"
         steps:
           - to:
               uri: "direct:process"

   - route:
       id: route-two
       from:
         uri: "direct:process"
         steps:
           - to:
               uri: "http:{{api.host}}"

   # ❌ WRONG — global onException after a route (schema validation error)
   - route:
       id: route-one
       from:
         uri: "kafka:{{kafka.topic.input}}"

   - onException:   # ❌ schema error — must appear before all routes
       exception:
         - com.example.ValidationException
   ```

   **Validation gate:** Scan the generated YAML top-to-bottom. If any `- onException:` top-level element appears after a `- route:` element, move it above all routes before saving the file.

### 3.3 Generate File

Create `{flow-name}.camel.yaml`:

```yaml
# ============================================
# Camel Route: {flow-name}
# Generated from TDD: .camel-kit/flows/{flow-name}/{flow-name}.tdd.md
# ============================================

# Global onException MUST be declared before any route (Rule 6).
# Include ONLY if TDD Section 5 defines global (cross-route) onException handling.
# Route-scoped error handling (errorHandler:, doTry/doCatch) stays inside the route.
- onException:
    exception:
      - [exception class from TDD]
    handled:
      constant:
        expression: "true"
    steps:
      - to:
          uri: "[component]:{{dlq.endpoint}}"

- route:
    id: {flow-name}
    description: [from TDD overview]

    # Error handling strategy from TDD Section 5
    errorHandler:
      deadLetterChannel:
        deadLetterUri: "[component]:{{dlq.endpoint}}"
        redeliveryPolicy:
          maximumRedeliveries: {{error.max.retries}}
          redeliveryDelay: {{error.retry.delay}}
          backOffMultiplier: {{error.backoff.multiplier}}

    # Source from TDD Section 2
    from:
      uri: "[component]:{{source.endpoint}}"

      steps:
        # Processing steps from TDD Section 3
        # (unmarshal only if explicitly required — see Rule in Step 3.2)

        # DataMapper transformation (injected by camel-datamapper-implement in Step 2.5)
        - step:
            id: kaoto-datamapper-{id}
            steps:
              - to:
                  id: kaoto-datamapper-xslt-{4hexchars}
                  uri: xslt-saxon:kaoto-datamapper-{id}.xsl
                  # Pass parameters to XSLT if TDD Section 3.3 defines parameters
                  parameters:
                    userId: "${header.userId}"
                    customerProfile: "${variable.customerProfile}"
                    tenantId: "${header.tenantId}"

        - validate:
            simple: "[validation expression from TDD]"

        - filter:
            simple: "[filter condition from TDD]"

        # Additional steps from TDD...

        # Sink from TDD Section 4
        - to:
            uri: "[component]:{{sink.endpoint}}"
```

Show generation summary:

```
Generated: {flow-name}.camel.yaml

Route Structure:
  ID: {flow-name}
  Source: [component]:{{source.endpoint}}
  Steps: [list of EIPs used]
  Sink: [component]:{{sink.endpoint}}
  Error Handler: [strategy]

Proceeding to validation...
```

---

## Step 4: Route Validation Loop (MANDATORY)

**CRITICAL — You MUST complete this step before generating any supporting files. Do NOT skip it, do NOT proceed on failure without attempting fixes.**

If MCP is not configured, skip to Step 4.3. If MCP is configured, the validate→fix→retry loop is non-negotiable.

### 4.1 Validate the Full Route

Pass the **entire content** of `{flow-name}.camel.yaml` to `camel_validate_route`:

```
MCP Tool: camel_validate_route
Params:
{
  "route": "<full YAML file content>",
  "version": "{{CAMEL_VERSION}}"
}
```

The tool validates:
- All component schemes exist in the Camel {{CAMEL_VERSION}} catalog
- URI path parameters are in the correct order and format
- All endpoint option names are valid (catches misspellings like `datasource` vs `dataSource`)
- Required parameters are present
- No unknown options are used

### 4.2 Fix → Re-query → Retry Loop

**If validation returns errors, follow this loop — up to 3 attempts:**

```
Attempt N/3: camel_validate_route returned errors:

  ❌ [component]: [error description]
     💡 [suggestion from tool]
```

**For each error, before editing the YAML:**

1. **Re-query the failing component** with `camel_catalog_component_doc` to get the authoritative option list:
   ```
   MCP Tool: camel_catalog_component_doc
   Params: { "name": "[component-name]", "version": "{{CAMEL_VERSION}}" }
   ```
2. **Identify the correct option name/value** from the catalog response — do not guess.
3. **Apply the fix** to `{flow-name}.camel.yaml`.
4. **Run `camel_validate_route` again** with the updated file content.
5. If validation passes → proceed to Step 4.3 ✅
6. If errors remain → repeat from step 1 (up to 3 total attempts).

**After 3 failed attempts:**

```
⚠️ Route validation still failing after 3 fix attempts.

Remaining errors:
[list errors]

These errors require manual intervention. Possible causes:
- Component option not available in Camel {{CAMEL_VERSION}}
- TDD specifies a component configuration that is incompatible
- YAML DSL syntax issue not covered by catalog validation

Action required:
1. Review the errors above
2. Check component docs: camel_catalog_component_doc { "name": "...", "version": "{{CAMEL_VERSION}}" }
3. Update the TDD if the component choice needs to change
4. Re-run /camel-implement once the TDD is corrected
```

Stop and report the errors — do not generate supporting files for a route that fails validation.

### 4.3 Validation Success

```
=== ROUTE VALIDATION PASSED (attempt N/3) ===

File: {flow-name}.camel.yaml
  ✓ All component schemes valid
  ✓ All endpoint URIs valid
  ✓ All option names verified against catalog
  ✓ No unknown or misspelled options
  ✓ Route ID present
  ✓ Steps array format (Kaoto compatible)

Proceeding to generate supporting files...
```

### 4.4 No MCP Available

```
⚠️ MCP server not configured — skipping catalog validation.
   Endpoint URIs and option names have NOT been verified against the Camel catalog.
   Run /camel-validate after implementation to catch any errors.
```

Proceed to Step 5 with this warning recorded.

---

## Step 5: Generate application.properties

**IMPORTANT: Save this file in the PROJECT ROOT directory, NOT in .camel-kit/**

Create file: `application.properties` (in project root)

### 5.1 Component-Level Configuration

Based on components used and their skills, generate component configuration:

```properties
# ============================================
# Application Properties for {flow-name}
# Generated from TDD
# ============================================

# --------------------------------------------
# COMPONENT CONFIGURATION
# Syntax: camel.component.<component>.<property>=<value>
# --------------------------------------------

# [Source Component] Configuration
camel.component.[source-component].[property]=[value from TDD]

# [Sink Component] Configuration
camel.component.[sink-component].[property]=[value from TDD]

# --------------------------------------------
# BEAN DEFINITIONS
# Syntax: camel.beans.<beanName>=#class:<ClassName>
# --------------------------------------------

# DataSource Bean (if SQL component used)
camel.beans.dataSource=#class:org.apache.commons.dbcp2.BasicDataSource
camel.beans.dataSource.driverClassName=[driver from TDD]
camel.beans.dataSource.url=[jdbc url from TDD]
camel.beans.dataSource.username=[username]
camel.beans.dataSource.password=[password]

# --------------------------------------------
# ROUTE PLACEHOLDERS
# Used in route URIs as {{property.name}}
# --------------------------------------------

# Endpoints from TDD
source.endpoint=[value from TDD]
sink.endpoint=[value from TDD]
dlq.endpoint=[value from TDD]

# Error handling configuration from TDD Section 5
error.max.retries=[value from TDD]
error.retry.delay=[value from TDD]
error.backoff.multiplier=[value from TDD]

# Other placeholders from route
[other {{placeholders}} from generated YAML]

# --------------------------------------------
# JBANG DEPENDENCIES
# External libraries (NOT Camel components)
# --------------------------------------------

camel.jbang.dependencies=[dependencies from TDD Section 8]
```

### 5.2 Environment-Specific Properties

If TDD Section 7.2 defines environment-specific configuration, create templates:

```properties
# Create:
# - application.properties (defaults)
# - application-dev.properties
# - application-test.properties
# - application-prod.properties
```

---

## Step 6: Generate docker-compose.yaml

**IMPORTANT: Save this file in the PROJECT ROOT directory, NOT in .camel-kit/**

Create file: `docker-compose.yaml` (in project root)

Based on external systems identified in TDD Section 8.2, generate docker-compose.yaml:

```yaml
# ============================================
# Docker Compose for {flow-name}
# External services required by this integration
# ============================================

version: '3.8'

services:
  # [Generate service definitions based on TDD dependencies]

  # Example: Kafka (if used)
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  # Example: PostgreSQL (if used)
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: [database from TDD]
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

---

## Step 7: Generate run.sh Script

**IMPORTANT: Save this file in the PROJECT ROOT directory, NOT in .camel-kit/**

Create file: `run.sh` (in project root, make it executable with chmod +x)

Create executable script to run the integration:

```bash
#!/bin/bash
# ============================================
# Run Script for {flow-name}
# ============================================

set -e

echo "Starting {flow-name} integration..."

# Check if external services are running
echo "Checking external services..."

# [Add checks based on TDD dependencies]

# Run the Camel integration
echo "Starting Camel route..."
camel run {flow-name}.camel.yaml application.properties

# Or using JBang:
# jbang camel run {flow-name}.camel.yaml application.properties
```

Make it executable:
```bash
chmod +x run.sh
```

---

## Step 8: Implement Advanced Patterns (Conditional)

**Check TDD for advanced requirements:**

```
Checking TDD for advanced patterns...

✓ Section 6 (Performance & Reliability): [Present/Not present]
✓ Section 7 (Security): [Present/Not present]
```

**If TDD contains Section 6 or Section 7:**

```
Advanced patterns required. Loading implementation guide...
→ Reading skills/camel-implement/guides/advanced-patterns.md
```

**Then load and apply:**
- `skills/camel-implement/guides/advanced-patterns.md`
- Implement patterns based on TDD requirements:
  - Idempotent Consumer (if exactly-once delivery needed)
  - Transactions (if transactional processing required)
  - Circuit Breaker (if external dependencies present)
  - Retry with Exponential Backoff (for resilience)
  - Correlation ID Propagation (for monitoring)
  - Content Enricher with Caching (for enrichment)
  - Throttling/Rate Limiting (for flow control)
  - Batch Processing (for efficiency)
  - Enhanced DLQ with Metadata (for debugging)
  - Schema Validation (for input validation)

**If TDD does NOT contain Section 6 or 7:**

```
No advanced patterns required. Using standard implementation.
Proceeding to dependency management...
```

---

## Step 9: Update pom.xml (if exists)

If using Maven project, add dependencies from TDD Section 8:

```xml
<!-- Dependencies for {flow-name} -->

<!-- Source component -->
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-[source-component]</artifactId>
</dependency>

<!-- Sink component -->
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-[sink-component]</artifactId>
</dependency>

<!-- Data formats -->
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-jackson</artifactId>
</dependency>

<!-- DataMapper / XSLT transformation (added by camel-datamapper-implement if Step 2.5 ran) -->
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-xslt-saxon</artifactId>
</dependency>

<!-- External dependencies from TDD -->
[dependencies from TDD Section 8.2]
```

**Note:** `camel-datamapper-implement` (Step 2.5) handles adding `camel-xslt-saxon` automatically. Do not add it manually here.

---

## Step 10: Generate Schemas (if requested)

If schemas were missing in Step 1.2 and user chose to generate them:

### For Input Schema

From TDD Section 2.3 (Data Contract - Input):

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "{flow-name} Input Schema",
  "type": "object",
  "properties": {
    "field1": {
      "type": "[type from TDD example]",
      "description": ""
    }
  },
  "required": ["field1"]
}
```

Save to `schemas/{flow-name}-input.json`

### For Output Schema

From TDD Section 3.3 or 4.3 (Data Contract - Output):

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "{flow-name} Output Schema",
  "type": "object",
  "properties": {
    "field1": {
      "type": "[type from TDD]",
      "description": ""
    }
  },
  "required": ["field1"]
}
```

Save to `schemas/{flow-name}-output.json`

---

## Implementation Summary

After all files are generated, perform automatic validation:

### MCP-Based Route Validation (Automatic)

**If MCP available:**

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
VALIDATING GENERATED ROUTE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Step 1: Analyzing route structure...

MCP Tool: camel_route_context
Params: {
  "route": "<entire YAML content>",
  "version": "{{CAMEL_VERSION}}"
}

Result:
  Components detected: [kafka, sql, http]
  EIPs used: [unmarshal, validate, choice, filter]
  Data formats: [json]
  ✓ All components valid for Camel {{CAMEL_VERSION}}
  ✓ All EIPs valid
  ✓ Component documentation retrieved

Step 2: Validating route and endpoint URIs...

MCP Tool: camel_validate_route
Params: {
  "route": "<entire YAML content>",
  "version": "{{CAMEL_VERSION}}"
}

Result:
  Validating: kafka:{{kafka.topic}}?brokers={{kafka.brokers}}
    ✓ Component 'kafka' exists in catalog
    ✓ All options valid
    ✓ Required parameters present

  Validating: sql:{{sql.query}}?dataSource=#bean:dataSource
    ✓ Component 'sql' exists in catalog
    ✓ Option 'dataSource' valid (type: bean reference)
    ✓ Query parameter valid

  Validating: http://{{api.host}}/orders
    ✓ Component 'http' exists in catalog
    ✓ URI format valid
    ✓ No unknown options

  ✓ YAML structure validated
  ✓ All endpoint URIs valid (3/3 passed)
  ✓ No typos or unknown options detected

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
VALIDATION SUMMARY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Route structure: VALID
✅ YAML syntax: VALID
✅ Endpoint URIs: VALID (3/3 passed)
✅ Components: VALID (3/3 available in {{CAMEL_VERSION}})
✅ EIPs: VALID (4/4 patterns recognized)

Route is ready for execution!
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**If MCP not available:**

```
Note: MCP server not configured - basic validation performed
✓ YAML syntax checked
✓ File structure validated
⚠️ Full catalog validation requires MCP server
```

---

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
IMPLEMENTATION COMPLETE: {flow-name}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Generated Files (in PROJECT ROOT):

  ✓ {flow-name}.camel.yaml (project root)
    Route ID: {flow-name}
    Source: [component]:{{source.endpoint}}
    Sink: [component]:{{sink.endpoint}}
    Validation: PASSED ✅ (MCP verified)

  ✓ {flow-name}-datamapper-a1b2c3d4.xsl (project root) [IF GENERATED]
    Field mappings: [N] fields
    Automapped: [N] exact matches
    Complex transformations: [N] (date formatting, calculations, etc.)
    Format: XSLT 2.0/3.0 (Saxon processor)

  ✓ application.properties (project root)
    Component config: [list components]
    Bean definitions: [list beans]
    Route placeholders: [count]

  ✓ docker-compose.yaml (project root)
    Services: [list services]

  ✓ run.sh (project root)
    Executable script to start integration

  ✓ schemas/{flow-name}-input.json (project root schemas/)
    Input data schema

  ✓ schemas/{flow-name}-output.json (project root schemas/)
    Output data schema

Dependencies (from TDD):
  - camel-[component1]
  - camel-[component2]
  - [external dependencies]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Next Steps

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RECOMMENDED NEXT STEPS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Review generated files and validate configuration

2. Start external services:

   docker compose up -d

3. Validate the implementation:

   /camel-validate {flow-name}

4. Generate integration tests:

   /camel-test {flow-name}

5. Run the integration:

   ./run.sh

   Or manually:
   camel run {flow-name}.camel.yaml application.properties

6. Monitor logs and verify behavior

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Need help?
- /camel-validate {flow-name} - Validate implementation
- /camel-test {flow-name} - Generate tests
```

---

## Error Handling

### Missing TDD

```
❌ ERROR: Technical Design Document not found

File: .camel-kit/flows/{flow-name}/{flow-name}.tdd.md

You need to create the TDD first:

  /camel-flow {flow-name}
```

### Validation Fails

```
❌ ERROR: Route validation failed

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

### Component Skill Not Found

```
⚠️ WARNING: Component skill not found

Component: [component-name]
Expected: {skills.folder}/camel-component-[name]/SKILL.md

Proceeding with standard component documentation from:
https://camel.apache.org/components/{{VERSION}}/[component-name]-component.html

Generated YAML may require manual review.
```

---

## Tips for Successful Implementation

1. **Always validate** - Never skip the validation loop
2. **Externalize everything** - No hardcoded values in YAML
3. **Follow the TDD** - Don't deviate without updating the TDD first
4. **Use component skills** - Load component knowledge for accurate URIs and parameters
5. **Test incrementally** - Validate each step before adding more complexity
6. **Document changes** - If you deviate from TDD, document why
7. **Keep routes clean** - Connection details belong in application.properties

---

## Token Optimization

**This skill is designed to minimize token usage:**

- Core SKILL.md: ~450 lines (down from 1,090)
- Load advanced-patterns.md only when TDD has Section 6 or 7 (save ~640 lines)
- Component skills loaded on-demand (already optimized)

**Total savings:** ~60% tokens for standard flows without advanced requirements

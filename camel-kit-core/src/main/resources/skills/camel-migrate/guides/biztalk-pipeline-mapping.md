# BizTalk Pipeline Mapping Guide

This guide maps BizTalk pipeline components (`.btp` files) to their Apache Camel equivalents. It is used by the `camel-migrate` skill during Phase 2 (Integration Architect).

> **"Load" means READ and FOLLOW.** When this guide says "Load `path/to/file.md`", read that file relative to the skill directory and execute its instructions. The files are always present — do NOT report them as missing.

---

## BizTalk Pipeline Overview

BizTalk pipelines are message processing stages that execute before or after orchestration processing:

- **Receive Pipeline** — executes on inbound messages (after adapter, before orchestration)
- **Send Pipeline** — executes on outbound messages (after orchestration, before adapter)

Each pipeline contains **stages** with **components**:

1. **Decode** (receive only) — decrypt, decode, decompress
2. **Disassemble** (receive only) — parse and split batched messages
3. **Validate** (receive) — schema validation
4. **ResolveParty** (receive) — party resolution
5. **Pre-Assemble** (send only) — custom pre-processing
6. **Assemble** (send only) — combine messages, serialize
7. **Encode** (send only) — compress, encrypt, encode

---

## Pipeline Component → Camel Pattern Mapping

### Receive Pipeline Components

| Component | Camel Pattern | Camel Component | Notes |
|---|---|---|---|
| **XML Disassembler** | `split()` + `unmarshal().jacksonXml()` | `camel-jackson` | Splits batched XML, validates against schema. |
| **Flat File Disassembler** | `unmarshal().flatpack()` or `unmarshal().bindy()` | `camel-flatpack` / `camel-bindy` | Parses fixed-width or delimited files. |
| **JSON Decoder** | `unmarshal().jackson()` | `camel-jackson` | Parses JSON payload. |
| **XML Validator** | `to("validator:schema.xsd")` | `camel-validator` | Validates XML against XSD schema. |
| **MIME/SMIME Decoder** | `unmarshal().mime()` | `camel-mail` | Decodes MIME/SMIME messages. |
| **Party Resolution** | `bean()` with lookup logic | custom Processor | Resolves party/organization from message context. |
| **Custom Receive Component** | `process()` or Groovy | `camel-groovy` | Re-implement custom logic in Groovy or custom Processor. |

---

### Send Pipeline Components

| Component | Camel Pattern | Camel Component | Notes |
|---|---|---|---|
| **XML Assembler** | `marshal().jacksonXml()` or `aggregate()` | `camel-jackson` | Serializes to XML; use `aggregate()` for batching. |
| **Flat File Assembler** | `marshal().flatpack()` or `marshal().bindy()` | `camel-flatpack` / `camel-bindy` | Formats as fixed-width or delimited file. |
| **JSON Encoder** | `marshal().jackson()` | `camel-jackson` | Serializes to JSON. |
| **MIME/SMIME Encoder** | `marshal().mime()` | `camel-mail` | Encodes as MIME/SMIME. |
| **Custom Send Component** | `process()` or Groovy | `camel-groovy` | Re-implement custom logic in Groovy or custom Processor. |

---

## Default BizTalk Pipeline Shortcuts → Camel Equivalents

BizTalk includes default pipelines for common scenarios. Map these to Camel patterns:

| Default Pipeline | Type | Camel Equivalent | Notes |
|---|---|---|---|
| **PassThruReceive** | Receive | No processing — direct `from(...).to(...)` | No transformation or validation. |
| **PassThruTransmit** | Send | No processing — direct `from(...).to(...)` | |
| **XMLReceive** | Receive | `from(...).to("validator:schema.xsd")` | Validates XML, no disassembly. |
| **XMLTransmit** | Send | `marshal().jacksonXml()` | Serializes to XML. |
| **BTSReceive2010** | Receive | XML Disassembler + Validator | Split + validate XML. |
| **BTSTransmit2010** | Send | XML Assembler | Serialize to XML. |

---

## Mapping BizTalk Pipeline to Camel Route Structure

### Receive Pipeline → Camel Route

**BizTalk Pipeline Stages:**
1. Decode → MIME Decoder
2. Disassemble → XML Disassembler
3. Validate → XML Validator
4. ResolveParty → Party Resolution

**Camel Route Equivalent:**
```yaml
- from:
    uri: "file:{{input.directory}}"
    steps:
      - unmarshal:
          mimeMultipart: {}                # Decode stage
      - unmarshal:
          jacksonXml: {}                   # Disassemble stage
      - to:
          uri: "validator:schema.xsd"      # Validate stage
      - bean:
          ref: "partyResolver"             # ResolveParty stage
      - to:
          uri: "direct:orchestration"
```

---

### Send Pipeline → Camel Route

**BizTalk Pipeline Stages:**
1. Pre-Assemble → Custom Processing
2. Assemble → XML Assembler
3. Encode → MIME Encoder

**Camel Route Equivalent:**
```yaml
- from:
    uri: "direct:orchestration-output"
    steps:
      - process:
          ref: "customProcessor"           # Pre-Assemble stage
      - marshal:
          jacksonXml: {}                   # Assemble stage
      - marshal:
          mimeMultipart: {}                # Encode stage
      - to:
          uri: "file:{{output.directory}}"
```

---

## Component-Specific Mapping Details

### XML Disassembler → split + unmarshal

**BizTalk XML Disassembler:**
- Parses envelope XML
- Splits batched messages
- Validates against schema (optional)
- Promotes distinguished fields

**Camel Equivalent:**
```yaml
- split:
    xpath: "//Order"
    steps:
      - unmarshal:
          jacksonXml: {}
      - to:
          uri: "validator:order-schema.xsd"
      - to:
          uri: "direct:process-order"
```

---

### Flat File Disassembler → unmarshal.flatpack or bindy

**BizTalk Flat File Disassembler:**
- Parses fixed-width or delimited files
- Uses flat file schema (.xsd with annotations)

**Camel Equivalent (Flatpack for delimited):**
```yaml
- unmarshal:
    flatpack:
      type: "delim"
      definition: "{{schema.path}}"
```

**Camel Equivalent (Bindy for fixed-width):**
```yaml
- unmarshal:
    bindy:
      type: "fixed"
      classType: "com.example.Order"
```

**Note:** Flat file schemas must be converted from BizTalk XSD to Flatpack/Bindy format. Document in design spec and flag for manual schema conversion.

---

### JSON Decoder/Encoder → unmarshal/marshal.jackson

**BizTalk JSON Decoder:**
```yaml
- unmarshal:
    json:
      library: Jackson
```

**BizTalk JSON Encoder:**
```yaml
- marshal:
    json:
      library: Jackson
      prettyPrint: true
```

---

### XML Validator → validator component

**BizTalk XML Validator:**
- Validates against XSD schema
- Fails pipeline on validation error

**Camel Equivalent:**
```yaml
- to:
    uri: "validator:file:src/main/resources/schemas/order.xsd"
```

**Error Handling:**
```yaml
- doTry:
    steps:
      - to:
          uri: "validator:order.xsd"
    doCatch:
      - exception:
          - "org.apache.camel.ValidationException"
        steps:
          - log:
              message: "Validation failed: ${exception.message}"
          - to:
              uri: "jms:validation-errors"
```

---

### MIME/SMIME Decoder/Encoder

**BizTalk MIME Decoder:**
```yaml
- unmarshal:
    mimeMultipart: {}
```

**BizTalk S/MIME Encoder:** Do not model S/MIME as `mimeMultipart` options. Camel 4's `mimeMultipart`
data format does not provide encryption or key-manager options. Preserve the security requirement in the design spec
and implement it with verified `camel-crypto`, `camel-mail`, or a custom processor after certificate and keystore
details are known.

---

### Party Resolution → bean with lookup logic

**BizTalk Party Resolution:**
- Resolves party/organization from certificate, sender ID, or message property
- Sets context properties

**Camel Equivalent:**
```yaml
- bean:
    ref: "partyResolver"
    method: "resolve"
```

**Custom Bean Implementation (Groovy):**
```groovy
class PartyResolver {
    String resolve(Exchange exchange) {
        def senderID = exchange.in.getHeader("SenderID")
        // Lookup logic: database, cache, external service
        def party = lookupParty(senderID)
        exchange.in.setHeader("ResolvedParty", party)
        return party
    }
}
```

---

## Custom Pipeline Components

**BizTalk Custom Components:**
- Implemented as .NET assemblies
- Configured via pipeline designer

**Camel Equivalent:**
1. **Document the component's purpose** in the design spec.
2. **Record a required custom implementation action** in the design spec.
3. **Suggest a Camel equivalent**:
   - `process()` with custom Processor
   - Groovy script
   - Existing Camel component (if functionality matches)

**Example Design Spec Entry:**
```markdown
> **Manual Review Required:** The BizTalk pipeline contains a custom component `CustomDecompressor`. This logic must be re-implemented in Camel. Original component: `MyCompany.BizTalk.CustomDecompressor` (Assembly: `CustomComponents.dll`).
>
> **Suggested Approach:** Implement as a custom Camel Processor or Groovy script. Document decompression algorithm and any external dependencies.
```

---

## How to Map a BizTalk Pipeline in design spec

When you encounter a BizTalk pipeline (`.btp` file) during migration analysis, follow this process:

1. **Read the `.btp` file as XML** — BizTalk pipelines are stored as XML.
2. **Extract pipeline type** — Receive or Send.
3. **Parse stages and components** — listed in `<Stage>` and `<Component>` elements.
4. **Classify each component using the table above**.
5. **Document in design spec section 3.1 (Processing Overview)** with the BizTalk pipeline noted in a separate sub-section:

**Example design spec section 3.1 Entry:**

```markdown
### 3.1 Processing Overview

1. Receive message from FILE adapter (`file:{{input.directory}}`)
2. Decode MIME (BizTalk: `MIME/SMIME Decoder` in Receive Pipeline)
3. Disassemble XML batch (BizTalk: `XML Disassembler` in Receive Pipeline — splits `//Order` elements)
4. Validate against `order-schema.xsd` (BizTalk: `XML Validator` in Receive Pipeline)
5. Resolve party from `SenderID` header (BizTalk: `Party Resolution` in Receive Pipeline)
6. Route to orchestration (`direct:process-order`)

**BizTalk Pipeline Origin:** `Pipelines/ReceiveOrderPipeline.btp` (Receive Pipeline)
```

6. **For custom components:** Flag for manual review as shown above.

---

## Pipeline Features Requiring Manual Review

| BizTalk Feature | Complexity | Recommended Approach |
|---|---|---|
| **Custom .NET component** | High | Re-implement in Groovy or custom Processor. Flag for manual review. |
| **Flat file schema** | Medium | Convert BizTalk XSD to Flatpack/Bindy format. Document conversion in the design spec. |
| **S/MIME encryption with certificate** | Medium | Use `camel-crypto` or `camel-mail` with Java KeyStore. Document certificate location. |
| **BizTalk Framework** (deprecated) | High | Must be re-implemented; discuss with development team. |
| **EDI Disassembler/Assembler** | High | No Camel 4 catalog component is assumed. Verify available EDI support through MCP, otherwise use a third-party EDI library or custom Processor. |

When these patterns are found, record a required custom implementation action in the relevant design spec section and
flag it for development team attention.

---

## Notes

- Always verify component names in the MCP catalog before writing design spec entries (using `camel_catalog_component_doc` or `camel_catalog_dataformat_doc`). Pass `runtime` and the full `platformBom` GAV (derived from `.camel-kit/config.properties` per `shared/mcp-setup.md` — the file stores bare versions, not the GAV) on every call, and check the echoed `camelVersion` matches the project version (Iron Law 1).
- BizTalk pipelines execute in a fixed order — preserve this order in the Camel route.
- Custom pipeline components MUST be flagged for manual review and documented with the original assembly name and purpose.
- Flat file schemas require manual conversion from BizTalk XSD to Flatpack/Bindy format.
- Party resolution logic varies by project — always ask the user how party resolution should be implemented in Camel.

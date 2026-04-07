# Data Format Guidance

## Overview

Choose the right data format based on your integration requirements.

## Format Comparison

### JSON
**Best for:** REST APIs, web integration, human-readable data

✓ **Pros:**
- Human-readable, flexible schema
- Wide tooling support
- Good for REST APIs and web integration
- Easy to debug and inspect

✗ **Cons:**
- Larger message size vs binary formats
- No built-in schema enforcement
- Slower parsing than binary

**Use when:** Integrating with web services, REST APIs, or when human readability matters

---

### Avro
**Best for:** High-volume streaming, Kafka, schema evolution

✓ **Pros:**
- Compact binary format
- Schema evolution support
- Excellent for high-volume streaming (Kafka)
- Fast serialization/deserialization

✗ **Cons:**
- Requires schema registry
- Not human-readable
- More complex setup

**Use when:** High throughput (>1000 msg/sec), Kafka streaming, need schema evolution

---

### XML
**Best for:** SOAP services, legacy systems, complex validation

✓ **Pros:**
- Strong validation with XSD
- Required for SOAP/legacy systems
- Rich transformation support (XSLT)
- Industry standard for B2B

✗ **Cons:**
- Verbose, larger message size
- Slower parsing
- More complex than JSON

**Use when:** Integrating with SOAP services, legacy systems, or strict schema validation needed

---

### CSV
**Best for:** Batch file processing, simple tabular data

✓ **Pros:**
- Simple, universally supported
- Good for batch file processing
- Human-readable
- Low overhead

✗ **Cons:**
- No schema, weak typing
- Poor for nested/complex data
- Limited validation
- No standard for escaping/formatting

**Use when:** Processing flat files, batch imports/exports, simple tabular data

---

### Protobuf
**Best for:** Microservices, gRPC, extreme performance

✓ **Pros:**
- Very compact binary format
- Extremely fast
- Strong typing with schema
- Great for gRPC

✗ **Cons:**
- Requires code generation
- Not human-readable
- Schema changes require coordination

**Use when:** Microservice communication, gRPC, need extreme performance

---

## Decision Matrix

| Requirement | Recommended Format |
|-------------|-------------------|
| REST API integration | JSON |
| High throughput (>1000 msg/sec) | Avro, Protobuf |
| Human readability important | JSON, CSV |
| Schema evolution needed | Avro |
| SOAP/legacy integration | XML |
| Batch file processing | CSV |
| Microservices (gRPC) | Protobuf |
| Simple data, low overhead | CSV |
| Strong validation needed | XML (with XSD), Avro |

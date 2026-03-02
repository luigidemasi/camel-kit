---
title: User Guide
weight: 2
---

This guide covers each step in detail — from designing a flow to running tests. Whether you followed the [greenfield](/docs/getting-started/greenfield) or [migration](/docs/getting-started/migration) workflow, the steps below apply to both.

## The Shared Pipeline

After the design phase (greenfield or migration), every flow goes through the same pipeline:

```mermaid
flowchart LR
    A["Flow Design\n(/camel-flow or\n/camel-migrate)"] --> B["/camel-implement"] --> C["/camel-validate"] --> D["/camel-test"] --> E["Run with\ncamel run"]
```

### Greenfield: Design the Flow

In the greenfield path, `/camel-flow` is where the integration takes shape. The AI guides you through source, sink, processing steps, error handling, and optionally data transformation with schema-driven field mappings.

{{< card link="flow-definition" title="Flow Definition" subtitle="The interactive design interview and what it produces" >}}

### Data Transformation

If your flow transforms data between formats, Camel-Kit generates XSLT automatically from your field mappings — no manual XSLT needed. This applies to both greenfield flows and migrated flows.

{{< card link="data-transformation" title="Data Transformation" subtitle="Schema-driven field mapping and automatic XSLT generation" >}}

### Implement: Generate Camel YAML

`/camel-implement` reads the Technical Design Document and generates catalog-verified Camel YAML DSL. Every component name, option, and URI is checked against the live Camel catalog via MCP.

{{< card link="yaml-generation" title="YAML Generation" subtitle="How /camel-implement generates and validates Camel YAML" >}}

### Validate: Check Correctness and Security

`/camel-validate` runs completeness checks, URI validation, 47 automated security checks, and constitution compliance on the generated route.

{{< card link="validation" title="Validation" subtitle="What gets checked and how to read the validation report" >}}

### Test: Generate Integration Tests

`/camel-test` generates Citrus integration tests — happy path, error handling, DLQ, and scenario-specific tests based on the EIPs in your route.

{{< card link="testing" title="Testing" subtitle="Citrus test generation and how to run them" >}}

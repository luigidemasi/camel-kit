# BizTalk Order Processing - Migration Example

This directory contains a sample Microsoft BizTalk Server integration project for testing Camel Kit's BizTalk migration capabilities.

## What's Included

This example demonstrates a typical BizTalk order processing flow:

1. **OrderProcess.odx** - BizTalk Orchestration that:
   - Receives order messages from a FILE adapter
   - Checks the order type (Priority vs. Standard)
   - Transforms orders to invoices using a BizTalk map
   - Sends invoices to a SQL database

2. **OrderToInvoice.btm** - BizTalk Map with functoids:
   - String Concatenate functoid (FirstName + LastName -> CustomerName)
   - Scripting functoid (C# date formatting)
   - Math functoid (quantity * unit price -> total amount)

3. **XMLReceive.btp** - Receive Pipeline with:
   - XML Disassembler component (with validation enabled)
   - XML Validator component

4. **PortBindings.xml** - Binding configuration for:
   - FILE receive location (polling C:\Orders\In)
   - SQL send port (connecting to InvoiceDB)

5. **OrderSchema.xsd** - XML Schema defining order message structure

## How to Run Migration

From the camel-kit repository root:

```bash
# Initialize migration project with BizTalk source platform
camel-kit init biztalk-order-processing --ai claude --source-platform biztalk

# Navigate to the initialized project
cd biztalk-order-processing

# Copy this example's BizTalk files into the project
cp ../examples/biztalk-order-processing/*.{odx,btm,btp,xml,xsd} .

# Start the migration skill
/camel-migrate
```

The `/camel-migrate` skill will:
1. Detect the BizTalk project artifacts (orchestrations, maps, pipelines, bindings)
2. Build a dependency graph using BizTalkParser
3. Guide you through the migration phases:
   - Phase 1: Generate Camel route structure from orchestrations
   - Phase 2: Convert BizTalk maps to XSLT or DataWeave
   - Phase 3: Map BizTalk adapters to Camel components
   - Phase 4: Translate expressions and pipeline logic

## Expected Output

See the `expected-output/` directory for examples of what the migration produces. The migration should generate:

- **Camel YAML routes** - One route per orchestration service
- **application.properties** - Camel component configurations (file polling, SQL connection)
- **XSLT/DataWeave transformations** - Converted from BTM functoid graphs
- **Validation logic** - Converted from BTP pipeline components
- **Migration notes** - Documenting manual review items and unsupported features

## BizTalk Features Demonstrated

This example covers common BizTalk patterns:
- **Orchestration Shapes**: Receive (activate), Decide, Construct, Transform, Send
- **Port Types**: One-way operations, physical bindings
- **Adapters**: FILE (polling), SQL (database insert)
- **Maps**: Functoids (String Concatenate, Scripting, Math), XPath links
- **Pipelines**: XML disassembly, validation stages
- **Bindings**: Receive locations, send ports, adapter configurations

## References

- [Camel Kit BizTalk Migration Guide](../../docs/architecture.md#biztalk-parser-architecture)
- [BizTalk Component Mapping Reference](../../.claude/skills/camel-migrate/guides/biztalk-component-mapping.md)
- [Apache Camel Documentation](https://camel.apache.org/)

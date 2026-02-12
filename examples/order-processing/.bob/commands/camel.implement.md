# /camel.implement

You are generating the Camel integration code from the flow definition and route design. Follow these steps exactly.

The user runs: `/camel.implement <flow-name>`

---

## Step 1: Load Context

Read these files in order:

1. `.camel-kit/flows/[flow-name]/flow.md` - Flow definition
2. `.camel-kit/flows/[flow-name]/flow.md` - Route design (REQUIRED)
3. `.camel-kit/templates/yaml-generation-guide.md` - YAML DSL rules (REQUIRED)
4. `.camel-kit/constitution.md` - Quality principles
5. `.camel-kit/.cache/components-*.json` - Component catalog
6. `.camel-kit/.cache/kamelets-*.json` - Kamelet catalog

**Error conditions:**
- If `flow.md` does not exist: ERROR "Route design not found. Run /camel.route [flow-name] first."
- If `yaml-generation-guide.md` does not exist: WARN and proceed with standard Camel YAML DSL.

---

## Step 2: Pre-Implementation Checks

### 2.1 Schema Verification

Check that all schemas referenced in the route design exist:

```
Checking schemas...
✓ schemas/order-message.json
✗ schemas/customer.json (missing)
```

If schemas are missing:
- Offer to generate them based on the route's data contracts
- Or prompt user to create them first

### 2.2 Constitution Gate Check

Verify the route design passes all constitution gates from `flow.md`:

```
Constitution Gate Check:
✓ Route Structure
✓ Single Responsibility
✓ Error Handling Mandatory
✓ External Configuration
```

If any gates are unchecked or failed, warn the user before proceeding.

---

## Step 3: Test-First Implementation

Following the **Test-First Imperative**:

### 3.1 Check for Existing Tests

Look for test files in `test/`:
- `test/[flow-name].camel.it.yaml` - Citrus integration test
- `test/data/*.json` - Test data files

### 3.2 Generate Test Skeletons (if missing)

If tests don't exist, offer to generate them first:

```
No tests found for [flow-name].

Recommended approach (Test-First):
1. Generate test skeleton first
2. Create test data
3. Then implement route

Generate test skeleton now? (yes/no)
```

If yes, create a basic Citrus test skeleton based on the route design.

---

## Step 4: YAML Generation

### 4.1 Read Generation Rules

**CRITICAL**: Follow the rules in `.camel-kit/templates/yaml-generation-guide.md`:

- Use standard Camel YAML DSL format (not alternative formats)
- Use `steps:` array format for Kaoto compatibility
- Use explicit `uri:` and `parameters:` structure
- Use object format for EIPs (not shorthand)
- Include route metadata (`id`, `description`)
- Place error handlers at route level

### 4.2 Generate Route

Generate the single route defined in `flow.md`:

1. **Create route structure:**
   ```yaml
   - route:
       id: [flow-name]
       description: [route description from plan]
   ```

2. **Configure Source (Consumer):**
   - Map the Source from plan to `from:` with `uri:` and `parameters:`
   - Use `{{PLACEHOLDER}}` syntax for environment variables

3. **Add Processing Steps:**
   - For each EIP in the plan's Processing Steps table:
     - Use the correct YAML structure from yaml-generation-guide.md
     - Preserve step order from the plan
     - Include nested `steps:` arrays where required

4. **Configure Sink (Producer):**
   - Add final `to:` step with sink configuration

5. **Add Error Handling:**
   - Implement the error strategy from the plan
   - Use `errorHandler:` at route level for Kaoto visibility

### 4.3 File Output

Generate a single file named after the flow:

```
[flow-name].camel.yaml
```

Example: `order-ingestion.camel.yaml`

### 4.4 Generation Report

Show what was generated:

```
Generated Camel Route:

FILE: [flow-name].camel.yaml

ROUTE: [flow-name]
  Source: [component]:[uri]
  Steps: [step1] → [step2] → [step3]
  Sink: [component]:[uri]
  Error Handling: [strategy]

SCHEMAS USED:
  - schemas/[schema-name].json

ENVIRONMENT VARIABLES REQUIRED:
  - [VAR_NAME]
```

---

## Step 5: Kaoto Compatibility Verification

Verify the generated YAML follows Kaoto requirements:

```
Kaoto Compatibility Check:
✓ Standard route format (- route:)
✓ Steps array format used
✓ Explicit uri/parameters structure
✓ Route ID present
✓ Error handler at route level
```

If any checks fail, fix the YAML before saving.

---

## Step 6: Create Supporting Files

### 6.1 Environment Template

If environment variables are used, create or update `.env.example`:

```properties
# Environment variables for [flow-name]
# Copy to .env and fill in values

KAFKA_BROKERS=localhost:9092
DATABASE_URL=jdbc:postgresql://localhost:5432/db
```

### 6.2 JBang Properties (if needed)

If additional dependencies are required, create or update `jbang.properties`:

```properties
# Additional dependencies
camel.jbang.dependencies=org.postgresql:postgresql:42.7.3
```

---

## Step 7: Summary and Next Steps

Present completion summary:

```
============================================
IMPLEMENTATION COMPLETE: [flow-name]
============================================

CREATED FILE:
  [flow-name].camel.yaml       Camel route (Kaoto compatible)

NEXT STEPS:

  1. Validate route:
     /camel.validate

  2. Run locally with Camel JBang:
     camel run [flow-name].camel.yaml

  3. Open in Kaoto for visual editing:
     Open [flow-name].camel.yaml in Kaoto VS Code extension

  4. Run tests:
     /camel.test [flow-name]

  5. Export to Maven project:
     camel export [flow-name].camel.yaml --runtime quarkus

DOCUMENTATION:
  - Camel JBang: https://camel.apache.org/manual/camel-jbang.html
  - Kaoto: https://kaoto.io/
```

---

## Error Handling

### Missing Route Design
```
ERROR: Route design not found.

The flow.md file is required for implementation.
Run /camel.route [flow-name] first to create the technical design.
```

### Invalid Route Design Structure
```
ERROR: Route design structure is incomplete.

Missing sections:
- Source configuration
- Error Handling

Please update the route design with /camel.route [flow-name]
```

### Schema Mismatch
```
WARNING: Schema referenced in route design does not match existing file.

Plan expects: schemas/order.json with fields [orderId, amount, customerId]
Found: schemas/order.json with fields [id, total, customer]

Options:
1. Update schema to match route design
2. Update route design to match schema
3. Proceed anyway (may cause runtime errors)
```

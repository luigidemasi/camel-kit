# Camel-Kit Validation Guide

This guide defines the detailed validation rules for camel-kit route specifications, error/warning reporting format, and integration with the Citrus testing framework.

---

## Validation Categories

### 1. Completeness Checks

Verify all required elements are present in route specifications.

| Check ID | Rule | Severity | Message |
|----------|------|----------|---------|
| `COMP-001` | Route must have `id` | ERROR | Route missing required 'id' field |
| `COMP-002` | Route must have `source` section | ERROR | Route '{id}' missing source definition |
| `COMP-003` | Route must have `sink` OR be internal-only | ERROR | Route '{id}' missing sink (add sink or mark as internal) |
| `COMP-004` | Route must have `error handling` section | ERROR | Route '{id}' missing error handling (constitution requires this) |
| `COMP-005` | Route must have `data format` when using structured data | WARNING | Route '{id}' has no data format specified for structured source |
| `COMP-006` | Route must have at least one processing step OR direct sink | WARNING | Route '{id}' has no processing steps (pass-through route) |

**Validation Logic:**

```python
def validate_completeness(route_spec):
    errors = []
    warnings = []

    # COMP-001: Route ID
    if not route_spec.get('id'):
        errors.append({
            'code': 'COMP-001',
            'severity': 'ERROR',
            'message': "Route missing required 'id' field",
            'fix': "Add route ID in Metadata section"
        })

    # COMP-002: Source
    if not route_spec.get('source'):
        errors.append({
            'code': 'COMP-002',
            'severity': 'ERROR',
            'route': route_spec.get('id'),
            'message': f"Route '{route_spec.get('id')}' missing source definition",
            'fix': "Run /camel.route to add source"
        })

    # COMP-003: Sink (unless internal)
    source_type = route_spec.get('source', {}).get('type')
    if not route_spec.get('sink') and source_type not in ['direct', 'seda']:
        errors.append({
            'code': 'COMP-003',
            'severity': 'ERROR',
            'route': route_spec.get('id'),
            'message': f"Route '{route_spec.get('id')}' missing sink",
            'fix': "Add sink or mark as internal-only route"
        })

    # COMP-004: Error handling
    if not route_spec.get('error_handling'):
        errors.append({
            'code': 'COMP-004',
            'severity': 'ERROR',
            'route': route_spec.get('id'),
            'message': f"Route '{route_spec.get('id')}' missing error handling",
            'fix': "Add error handling section (constitution requires this)"
        })

    return errors, warnings
```

---

### 2. Correctness Checks

Verify values are valid against the Camel catalog.

| Check ID | Rule | Severity | Message |
|----------|------|----------|---------|
| `CORR-001` | Component must exist in catalog | ERROR | Unknown component '{name}' - did you mean '{suggestion}'? |
| `CORR-002` | Kamelet must exist in catalog | ERROR | Unknown Kamelet '{name}' |
| `CORR-003` | Required component options must be provided | ERROR | Component '{name}' missing required option '{option}' |
| `CORR-004` | Required Kamelet properties must be provided | ERROR | Kamelet '{name}' missing required property '{prop}' |
| `CORR-005` | Option values must match expected type | WARNING | Option '{option}' expects {type}, got {actual} |
| `CORR-006` | Deprecated options should be avoided | WARNING | Option '{option}' is deprecated: {reason} |
| `CORR-007` | Expression syntax must be valid | ERROR | Invalid expression: {expression} |
| `CORR-008` | EIP must be valid Camel EIP | ERROR | Unknown EIP '{name}' |

**Validation Logic:**

```python
def validate_correctness(route_spec, component_catalog, kamelet_catalog):
    errors = []
    warnings = []

    source = route_spec.get('source', {})

    # CORR-001: Component exists
    if source.get('type') == 'Component':
        component_name = source.get('component')
        if component_name not in component_catalog['components']:
            suggestion = find_similar(component_name, component_catalog['components'].keys())
            errors.append({
                'code': 'CORR-001',
                'severity': 'ERROR',
                'message': f"Unknown component '{component_name}'",
                'suggestion': f"Did you mean '{suggestion}'?" if suggestion else None,
                'fix': f"Check component name spelling or use /camel.route to reconfigure"
            })
        else:
            # CORR-003: Required options
            component = component_catalog['components'][component_name]
            for prop_name, prop_def in component.get('properties', {}).items():
                if prop_def.get('required') and prop_name not in source.get('options', {}):
                    errors.append({
                        'code': 'CORR-003',
                        'severity': 'ERROR',
                        'message': f"Component '{component_name}' missing required option '{prop_name}'",
                        'fix': f"Add '{prop_name}' to source configuration"
                    })

    # CORR-002: Kamelet exists
    if source.get('type') == 'Kamelet':
        kamelet_name = source.get('kamelet')
        if kamelet_name not in kamelet_catalog['kamelets']:
            errors.append({
                'code': 'CORR-002',
                'severity': 'ERROR',
                'message': f"Unknown Kamelet '{kamelet_name}'",
                'fix': "Check Kamelet name or run 'camel-kit catalog search' to find available Kamelets"
            })

    return errors, warnings

def find_similar(name, candidates, threshold=0.7):
    """Find similar names using Levenshtein distance."""
    from difflib import SequenceMatcher
    best_match = None
    best_ratio = 0
    for candidate in candidates:
        ratio = SequenceMatcher(None, name.lower(), candidate.lower()).ratio()
        if ratio > best_ratio and ratio >= threshold:
            best_ratio = ratio
            best_match = candidate
    return best_match
```

---

### 3. Constitution Compliance Checks

Verify routes follow constitution best practices.

| Check ID | Rule | Severity | Message |
|----------|------|----------|---------|
| `CONST-001` | Route ID must follow naming convention | WARNING | Route ID '{id}' doesn't follow convention '<domain>-<action>' |
| `CONST-002` | Route should have single responsibility | WARNING | Route '{id}' has {n} steps - consider splitting |
| `CONST-003` | External calls must have resilience pattern | WARNING | Route '{id}' calls external service without circuit breaker |
| `CONST-004` | Retry delays should be reasonable | WARNING | Route '{id}' retry delay {delay}ms exceeds recommended 30s |
| `CONST-005` | High-volume routes should use idempotency | WARNING | Route '{id}' is high-volume but has no idempotent consumer |
| `CONST-006` | Structured data should have schema validation | WARNING | Route '{id}' consumes JSON but has no validation step |
| `CONST-007` | Sensitive values should use placeholders | ERROR | Route '{id}' contains hardcoded value that looks like a secret |
| `CONST-008` | Error strategy must match constitution | WARNING | Route '{id}' error handling doesn't follow constitution guidelines |

**Validation Logic:**

```python
import re

def validate_constitution(route_spec, constitution):
    errors = []
    warnings = []

    route_id = route_spec.get('id', '')

    # CONST-001: Naming convention
    naming_pattern = constitution.get('naming_pattern', r'^[a-z]+-[a-z]+(-[a-z]+)?$')
    if not re.match(naming_pattern, route_id):
        warnings.append({
            'code': 'CONST-001',
            'severity': 'WARNING',
            'message': f"Route ID '{route_id}' doesn't follow naming convention",
            'expected': "<domain>-<action> (e.g., order-ingestion)",
            'fix': "Rename route to follow convention"
        })

    # CONST-002: Single responsibility (max steps)
    max_steps = constitution.get('max_route_steps', 7)
    steps = route_spec.get('processing_steps', [])
    if len(steps) > max_steps:
        warnings.append({
            'code': 'CONST-002',
            'severity': 'WARNING',
            'message': f"Route '{route_id}' has {len(steps)} steps - consider splitting",
            'recommendation': f"Constitution recommends max {max_steps} steps per route",
            'fix': "Split into sub-routes connected via direct:"
        })

    # CONST-003: Circuit breaker for external calls
    external_calls = find_external_calls(steps)
    for call in external_calls:
        if not call.get('resilience', {}).get('circuit_breaker'):
            warnings.append({
                'code': 'CONST-003',
                'severity': 'WARNING',
                'message': f"Route '{route_id}' calls external service '{call['endpoint']}' without circuit breaker",
                'fix': "Add circuit breaker resilience pattern"
            })

    # CONST-007: Hardcoded secrets
    secret_patterns = [
        r'password\s*[:=]\s*["\'][^"\']+["\']',
        r'api[_-]?key\s*[:=]\s*["\'][^"\']+["\']',
        r'secret\s*[:=]\s*["\'][^"\']+["\']',
    ]
    spec_text = str(route_spec)
    for pattern in secret_patterns:
        if re.search(pattern, spec_text, re.IGNORECASE):
            errors.append({
                'code': 'CONST-007',
                'severity': 'ERROR',
                'message': f"Route '{route_id}' contains hardcoded value that looks like a secret",
                'fix': "Use environment variable placeholder: {{SECRET_NAME}}"
            })
            break

    return errors, warnings
```

---

### 4. Dependency Checks

Verify route dependencies are resolved.

| Check ID | Rule | Severity | Message |
|----------|------|----------|---------|
| `DEP-001` | Referenced direct: endpoint must have corresponding route | ERROR | Route '{id}' references 'direct:{name}' but no route exists |
| `DEP-002` | Referenced seda: endpoint must have corresponding route | WARNING | Route '{id}' references 'seda:{name}' but no route exists |
| `DEP-003` | No circular dependencies | ERROR | Circular dependency detected: {route1} → {route2} → {route1} |
| `DEP-004` | Aggregation must have completion condition | ERROR | Route '{id}' uses aggregate without completion condition |
| `DEP-005` | Split should have corresponding aggregate or be fire-and-forget | WARNING | Route '{id}' splits but doesn't aggregate results |
| `DEP-006` | Bean references must be documented | WARNING | Route '{id}' references bean '{bean}' - ensure it's available |

**Validation Logic:**

```python
def validate_dependencies(routes_specs):
    errors = []
    warnings = []

    # Build dependency graph
    route_ids = {r['id'] for r in routes_specs}
    direct_endpoints = set()
    seda_endpoints = set()

    for route in routes_specs:
        # Collect referenced endpoints
        for step in route.get('processing_steps', []):
            if step.get('type') == 'to':
                endpoint = step.get('uri', '')
                if endpoint.startswith('direct:'):
                    direct_endpoints.add(endpoint.replace('direct:', ''))
                elif endpoint.startswith('seda:'):
                    seda_endpoints.add(endpoint.replace('seda:', ''))

    # DEP-001: direct: endpoints
    for route in routes_specs:
        source = route.get('source', {})
        if source.get('type') == 'direct':
            route_ids.add(source.get('uri', '').replace('direct:', ''))

    for endpoint in direct_endpoints:
        if endpoint not in route_ids:
            errors.append({
                'code': 'DEP-001',
                'severity': 'ERROR',
                'message': f"Reference to 'direct:{endpoint}' but no route exists",
                'fix': f"Create route with: /camel.route {endpoint}"
            })

    # DEP-003: Circular dependencies
    cycles = detect_cycles(routes_specs)
    for cycle in cycles:
        errors.append({
            'code': 'DEP-003',
            'severity': 'ERROR',
            'message': f"Circular dependency detected: {' → '.join(cycle)}",
            'fix': "Refactor routes to break the cycle"
        })

    return errors, warnings
```

---

## Error and Warning Report Format

### Console Output

```
🔍 Validating camel-kit specifications...

Checking: order-ingestion
  ✅ COMP-001: Route has ID
  ✅ COMP-002: Source defined
  ✅ COMP-003: Sink defined
  ❌ COMP-004: Missing error handling
  ✅ CORR-001: Component 'kafka' valid
  ✅ CORR-003: Required options provided
  ⚠️  CONST-002: Route has 9 steps (recommended: 7)
  ⚠️  CONST-003: External call without circuit breaker

Checking: inventory-lookup
  ✅ All checks passed

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
VALIDATION RESULT: ❌ FAILED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Summary: 1 error, 2 warnings

Errors (must fix before /camel.generate):

  1. [COMP-004] order-ingestion: Missing error handling
     └─ Fix: Add error handling section (constitution requires this)
     └─ Run: /camel.route order-ingestion

Warnings (recommended to fix):

  1. [CONST-002] order-ingestion: Route has 9 steps
     └─ Recommendation: Split into sub-routes connected via direct:

  2. [CONST-003] order-ingestion: External call without circuit breaker
     └─ Recommendation: Add circuit breaker resilience pattern
```

### Validation Report File

Generate `.camel-kit/validation-report.json`:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "camelVersion": "4.17.0",
  "result": "FAILED",
  "summary": {
    "routes": 2,
    "passed": 1,
    "failed": 1,
    "errors": 1,
    "warnings": 2
  },
  "routes": {
    "order-ingestion": {
      "status": "FAILED",
      "checks": [
        {"code": "COMP-001", "status": "PASSED"},
        {"code": "COMP-002", "status": "PASSED"},
        {"code": "COMP-003", "status": "PASSED"},
        {"code": "COMP-004", "status": "FAILED", "message": "Missing error handling"},
        {"code": "CORR-001", "status": "PASSED"},
        {"code": "CONST-002", "status": "WARNING", "message": "Route has 9 steps"}
      ]
    },
    "inventory-lookup": {
      "status": "PASSED",
      "checks": []
    }
  },
  "errors": [
    {
      "code": "COMP-004",
      "route": "order-ingestion",
      "severity": "ERROR",
      "message": "Missing error handling",
      "fix": "Add error handling section"
    }
  ],
  "warnings": [
    {
      "code": "CONST-002",
      "route": "order-ingestion",
      "severity": "WARNING",
      "message": "Route has 9 steps"
    }
  ]
}
```

### Markdown Report

Generate `.camel-kit/validation-report.md`:

```markdown
# Validation Report

Generated: 2024-01-15T10:30:00Z
Camel Version: 4.17.0

## Summary

| Metric | Count |
|--------|-------|
| Routes Checked | 2 |
| Passed | 1 |
| Failed | 1 |
| Errors | 1 |
| Warnings | 2 |

## Result: ❌ FAILED

## Routes

### order-ingestion ❌

| Check | Status | Details |
|-------|--------|---------|
| COMP-001 | ✅ | Route has ID |
| COMP-002 | ✅ | Source defined |
| COMP-004 | ❌ | Missing error handling |
| CONST-002 | ⚠️ | Route has 9 steps |

### inventory-lookup ✅

All checks passed.

## Action Items

- [ ] **[COMP-004]** Add error handling to order-ingestion
- [ ] **[CONST-002]** Consider splitting order-ingestion into sub-routes
```

---

## Citrus Testing Integration

### Overview

Camel-kit generates [Citrus](https://citrusframework.org/) test files to verify route behavior. Tests use YAML format and integrate with `camel test` command.

References:
- [Citrus Framework](https://citrusframework.org/)
- [Camel JBang Testing](https://camel.apache.org/manual/camel-jbang-test.html)
- [YAKS Cloud-Native Testing](https://citrusframework.org/yaks/reference/html/index.html)

### Test Generation Command

Add `/camel.test` command to generate Citrus tests:

```
/camel.test <route-name>    Generate test for specific route
/camel.test --all           Generate tests for all routes
```

### Test File Structure

```
.camel-kit/
├── routes/
│   └── order-ingestion.md
├── output/
│   └── routes.camel.yaml
└── tests/
    ├── order-ingestion-test.yaml    # Generated Citrus test
    ├── inventory-lookup-test.yaml
    └── test-data/
        ├── order-valid.json
        ├── order-invalid.json
        └── expected-output.json
```

### Generated Test Format

For a route specification, generate a Citrus YAML test:

**Route: order-ingestion**
- Source: Kafka topic "orders"
- Sink: JPA database
- Error handling: Dead Letter Channel

**Generated Test: order-ingestion-test.yaml**

```yaml
# Citrus test for route: order-ingestion
# Generated by camel-kit
# Run with: camel test run order-ingestion-test.yaml

name: order-ingestion-test
description: Test order ingestion route - Kafka to Database

variables:
  kafka.brokers: localhost:9092
  database.url: jdbc:h2:mem:testdb

actions:
  # 1. Start test infrastructure
  - camel:
      infra:
        run:
          service: kafka
          properties:
            topics: orders,orders-dlq

  - camel:
      infra:
        run:
          service: h2
          properties:
            database: testdb

  # 2. Start the Camel integration
  - camel:
      jbang:
        run:
          integration:
            file: "../output/routes.camel.yaml"
          wait:
            for:
              log:
                message: "started and consuming"
            timeout: 30000

  # 3. Test Case: Valid Order (Happy Path)
  - echo:
      message: "Test Case 1: Valid order processing"

  - send:
      endpoint:
        uri: kafka:orders
        parameters:
          brokers: ${kafka.brokers}
      message:
        headers:
          kafka.KEY: "order-001"
        body:
          file: test-data/order-valid.json

  # Wait for processing
  - sleep:
      milliseconds: 2000

  # 4. Verify database insert
  - sql:
      datasource: testdb
      statement: SELECT * FROM orders WHERE order_id = 'order-001'
      validate:
        - column: order_id
          value: order-001
        - column: status
          value: PROCESSED

  # 5. Test Case: Invalid Order (Error Handling)
  - echo:
      message: "Test Case 2: Invalid order - should go to DLQ"

  - send:
      endpoint:
        uri: kafka:orders
        parameters:
          brokers: ${kafka.brokers}
      message:
        headers:
          kafka.KEY: "order-invalid"
        body:
          data: '{"invalid": "data"}'

  # Wait for error handling
  - sleep:
      milliseconds: 2000

  # 6. Verify DLQ received the failed message
  - receive:
      endpoint:
        uri: kafka:orders-dlq
        parameters:
          brokers: ${kafka.brokers}
          groupId: test-consumer
      message:
        headers:
          kafka.KEY: order-invalid
      timeout: 10000

  # 7. Test Case: Duplicate Order (Idempotency)
  - echo:
      message: "Test Case 3: Duplicate order - should be skipped"

  - send:
      endpoint:
        uri: kafka:orders
        parameters:
          brokers: ${kafka.brokers}
      message:
        headers:
          kafka.KEY: "order-001"  # Same as first order
        body:
          file: test-data/order-valid.json

  - sleep:
      milliseconds: 2000

  # Verify no duplicate in database
  - sql:
      datasource: testdb
      statement: SELECT COUNT(*) as cnt FROM orders WHERE order_id = 'order-001'
      validate:
        - column: cnt
          value: 1  # Should still be 1, not 2

finally:
  # Cleanup
  - camel:
      jbang:
        stop:
          integration: order-ingestion
```

### Test Data Files

Generate test data based on route data format specification:

**test-data/order-valid.json:**
```json
{
  "orderId": "order-001",
  "customerId": "cust-123",
  "orderDate": "2024-01-15T10:30:00Z",
  "items": [
    {
      "productId": "prod-001",
      "quantity": 2,
      "unitPrice": 29.99
    }
  ],
  "totalAmount": 59.98,
  "currency": "USD"
}
```

**test-data/order-invalid.json:**
```json
{
  "invalid": "missing required fields"
}
```

### Test Scenarios to Generate

Based on route specification, generate tests for:

| Scenario | What to Test | How |
|----------|--------------|-----|
| Happy Path | Normal message processing | Send valid message, verify sink received it |
| Validation Error | Invalid input handling | Send invalid message, verify error handling |
| Dead Letter | Unrecoverable errors | Cause failure, verify DLQ |
| Idempotency | Duplicate handling | Send same message twice, verify only one processed |
| Circuit Breaker | External service failure | Mock failure, verify fallback |
| Transformation | Data format conversion | Send input, verify output format |
| Filtering | Filter conditions | Send filtered/non-filtered messages, verify |
| Splitting | Batch processing | Send batch, verify individual processing |

### Running Tests

```bash
# Run single test
camel test run .camel-kit/tests/order-ingestion-test.yaml

# Run all tests
camel test run .camel-kit/tests/

# Run with verbose output
camel test run .camel-kit/tests/ --verbose

# Export to Maven project for CI/CD
camel export .camel-kit/output/routes.camel.yaml \
  --runtime quarkus \
  --gav com.example:my-integration:1.0.0
```

### Integration with Validation

After `/camel.validate` passes, suggest running tests:

```
✅ VALIDATION PASSED

Next steps:
  1. Generate tests: /camel.test --all
  2. Run tests: camel test run .camel-kit/tests/
  3. Generate YAML: /camel.generate
```

---

## Validation Modes

### Standard Mode (Default)

- Errors block `/camel.generate`
- Warnings are advisory

### Strict Mode (`/camel.validate --strict`)

- Warnings become errors
- All constitution recommendations enforced
- Required for production deployments

### Lenient Mode (`/camel.validate --lenient`)

- Only critical errors reported
- Warnings suppressed
- For quick prototyping

---

## Custom Validation Rules

Users can add custom validation rules in `.camel-kit/constitution.md`:

```markdown
## Project Customizations

### Custom Validation Rules

```yaml
custom_rules:
  - id: CUSTOM-001
    description: "All routes must use specific Kafka cluster"
    check: "source.component == 'kafka' implies source.options.brokers contains 'prod-kafka'"
    severity: ERROR

  - id: CUSTOM-002
    description: "Database routes must use connection pooling"
    check: "sink.component in ['jdbc', 'jpa'] implies sink.options.dataSource != null"
    severity: WARNING
```
```

# Camel-Kit Validation — Completeness & Correctness

Validation rules for verifying route specifications have all required elements and valid values.

---

## 1. Completeness Checks

| Check ID | Rule | Severity | Message |
|----------|------|----------|---------|
| `COMP-001` | Route must have `id` | ERROR | Route missing required 'id' field |
| `COMP-002` | Route must have `source` section | ERROR | Route '{id}' missing source definition |
| `COMP-003` | Route must have `sink` OR be internal-only | ERROR | Route '{id}' missing sink |
| `COMP-004` | Route must have `error handling` section | ERROR | Route '{id}' missing error handling |
| `COMP-005` | Route must have `data format` when using structured data | WARNING | Route '{id}' has no data format specified |
| `COMP-006` | Route must have at least one processing step OR direct sink | WARNING | Route '{id}' has no processing steps |

---

## 2. Correctness Checks

Verify values against the Camel catalog.

| Check ID | Rule | Severity | Message |
|----------|------|----------|---------|
| `CORR-001` | Component must exist in catalog | ERROR | Unknown component '{name}' |
| `CORR-002` | Kamelet must exist in catalog | ERROR | Unknown Kamelet '{name}' |
| `CORR-003` | Required component options must be provided | ERROR | Missing required option '{option}' |
| `CORR-004` | Required Kamelet properties must be provided | ERROR | Missing required property '{prop}' |
| `CORR-005` | Option values must match expected type | WARNING | Option expects {type}, got {actual} |
| `CORR-006` | Deprecated options should be avoided | WARNING | Option '{option}' is deprecated |
| `CORR-007` | Expression syntax must be valid | ERROR | Invalid expression |
| `CORR-008` | EIP must be valid Camel EIP | ERROR | Unknown EIP '{name}' |

---

## Validation Logic (Completeness)

```python
def validate_completeness(route_spec):
    errors = []
    if not route_spec.get('id'):
        errors.append({'code': 'COMP-001', 'severity': 'ERROR',
            'message': "Route missing required 'id' field"})
    if not route_spec.get('source'):
        errors.append({'code': 'COMP-002', 'severity': 'ERROR',
            'message': f"Route '{route_spec.get('id')}' missing source definition"})
    source_type = route_spec.get('source', {}).get('type')
    if not route_spec.get('sink') and source_type not in ['direct', 'seda']:
        errors.append({'code': 'COMP-003', 'severity': 'ERROR',
            'message': f"Route '{route_spec.get('id')}' missing sink"})
    if not route_spec.get('error_handling'):
        errors.append({'code': 'COMP-004', 'severity': 'ERROR',
            'message': f"Route '{route_spec.get('id')}' missing error handling"})
    return errors

def validate_correctness(route_spec, component_catalog, kamelet_catalog):
    errors = []
    source = route_spec.get('source', {})
    if source.get('type') == 'Component':
        name = source.get('component')
        if name not in component_catalog['components']:
            errors.append({'code': 'CORR-001', 'severity': 'ERROR',
                'message': f"Unknown component '{name}'"})
        else:
            for prop_name, prop_def in component_catalog['components'][name].get('properties', {}).items():
                if prop_def.get('required') and prop_name not in source.get('options', {}):
                    errors.append({'code': 'CORR-003', 'severity': 'ERROR',
                        'message': f"Missing required option '{prop_name}'"})
    return errors
```

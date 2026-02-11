# Route: {{ROUTE_ID}}

> {{ONE_LINE_DESCRIPTION}}

---

## Metadata

| Property | Value |
|----------|-------|
| Route ID | `{{ROUTE_ID}}` |
| Status | Draft / Designed / Validated / Generated |
| Created | {{DATE}} |
| Last Modified | {{DATE}} |
| Author | {{AUTHOR}} |

---

## Intent

<!--
Describe what this route does and why it exists.
This becomes the route description in generated YAML.
-->

{{DETAILED_DESCRIPTION}}

---

## Source

<!--
Define where messages originate for this route.
Choose ONE: Kamelet OR Component
-->

### Type

- [ ] Kamelet (simplified, managed)
- [ ] Component (full control)
- [ ] Internal (`direct:` or `seda:` from another route)

### Configuration

#### If Kamelet:

| Property | Value |
|----------|-------|
| Kamelet | `{{kamelet-name}}` |

| Parameter | Value | Required |
|-----------|-------|----------|
| {{param1}} | {{value}} | Yes / No |
| {{param2}} | {{value}} | Yes / No |

#### If Component:

| Property | Value |
|----------|-------|
| Component | `{{component-name}}` |
| URI | `{{component}}:{{destination}}` |

| Option | Value | Required |
|--------|-------|----------|
| {{option1}} | {{value}} | Yes / No |
| {{option2}} | {{value}} | Yes / No |

#### If Internal:

| Property | Value |
|----------|-------|
| Type | `direct` / `seda` |
| URI | `direct:{{route-id}}` |
| Called From | `{{parent-route-id}}` |

### Source Notes

<!-- Any additional context about the source -->

---

## Data Format

### Input

| Property | Value |
|----------|-------|
| Format | JSON / XML / CSV / Avro / Plain Text / Binary |
| Unmarshal | Yes / No |
| Type Class | `{{fully.qualified.ClassName}}` or None |
| Schema | `{{schema-file-path}}` or None |
| Validation | Enabled / Disabled |

### Output

| Property | Value |
|----------|-------|
| Format | JSON / XML / CSV / Avro / Plain Text / Binary |
| Marshal | Yes / No |
| Type Class | `{{fully.qualified.ClassName}}` or None |

---

## Processing Steps

<!--
Define each processing step in order.
Each step maps to a Camel EIP or processor.
-->

### Step 1: {{STEP_NAME}}

| Property | Value |
|----------|-------|
| EIP | Filter / Choice / Split / Aggregate / Enrich / Transform / Bean / Log / ... |
| Purpose | {{why this step exists}} |

#### Configuration

<!--
Configuration specific to the EIP type.
Examples below for common EIPs.
-->

**If Filter:**
```yaml
expression:
  simple: "{{condition}}"
# Example: "${body.amount} >= 50"
```

**If Choice (Content-Based Router):**
```yaml
when:
  - condition:
      simple: "{{condition1}}"
    steps: [reference step or inline]
  - condition:
      simple: "{{condition2}}"
    steps: [reference step or inline]
otherwise:
  steps: [reference step or inline]
```

**If Split:**
```yaml
expression:
  simple: "{{expression}}"    # e.g., "${body.items}"
  # OR
  jsonpath: "$.items[*]"
streaming: true / false
parallelProcessing: true / false
aggregationStrategy: {{strategyRef}} / null
```

**If Aggregate:**
```yaml
correlationExpression:
  simple: "{{expression}}"    # e.g., "${header.orderId}"
completionSize: {{number}}
completionTimeout: {{milliseconds}}
aggregationStrategy: {{strategyRef}}
```

**If Enrich:**
```yaml
uri: "{{endpoint}}"           # e.g., "direct:lookup-customer"
aggregationStrategy: {{strategyRef}}
```

**If Transform:**
```yaml
expression:
  simple: "{{expression}}"
  # OR
  groovy: "{{script}}"
  # OR
  jq: "{{jq-expression}}"
```

**If Bean:**
```yaml
ref: "{{beanName}}"
method: "{{methodName}}"
```

**If Log:**
```yaml
message: "{{log message with ${placeholders}}}"
loggingLevel: INFO / DEBUG / WARN / ERROR
logName: "{{logger-name}}"
```

**If SetHeader:**
```yaml
name: "{{headerName}}"
expression:
  simple: "{{value}}"
```

**If To (intermediate):**
```yaml
uri: "{{endpoint}}"
```

---

### Step 2: {{STEP_NAME}}

| Property | Value |
|----------|-------|
| EIP | ... |
| Purpose | ... |

#### Configuration

```yaml
...
```

---

### Step 3: {{STEP_NAME}}

<!-- Add more steps as needed -->

---

## Sink

<!--
Define where processed messages go.
Choose ONE: Kamelet OR Component OR Internal
-->

### Type

- [ ] Kamelet (simplified, managed)
- [ ] Component (full control)
- [ ] Internal (`direct:` or `seda:` to another route)
- [ ] None (response returned to caller, e.g., for `direct:` routes)

### Configuration

#### If Kamelet:

| Property | Value |
|----------|-------|
| Kamelet | `{{kamelet-name}}` |

| Parameter | Value | Required |
|-----------|-------|----------|
| {{param1}} | {{value}} | Yes / No |

#### If Component:

| Property | Value |
|----------|-------|
| Component | `{{component-name}}` |
| URI | `{{component}}:{{destination}}` |

| Option | Value | Required |
|--------|-------|----------|
| {{option1}} | {{value}} | Yes / No |

#### If Internal:

| Property | Value |
|----------|-------|
| Type | `direct` / `seda` |
| URI | `direct:{{target-route-id}}` |

### Sink Notes

<!-- Any additional context about the sink -->

---

## Error Handling

<!--
REQUIRED: Every route must define error handling strategy.
Choose primary strategy and configure.
-->

### Strategy

- [ ] Dead Letter Channel
- [ ] Default Error Handler with Retry
- [ ] Transaction Error Handler
- [ ] Custom onException handlers
- [ ] No Error Handler (propagate to caller) — requires justification

### Configuration

#### If Dead Letter Channel:

| Property | Value |
|----------|-------|
| Dead Letter URI | `{{endpoint}}` (e.g., `kafka:orders-dlq`) |
| Maximum Redeliveries | {{number}} |
| Redelivery Delay | {{milliseconds}} |
| Backoff Multiplier | {{number}} |
| Use Exponential Backoff | Yes / No |
| Log Exhausted | Yes / No |
| Log Retry Attempt | Yes / No |

#### If Default Error Handler:

| Property | Value |
|----------|-------|
| Maximum Redeliveries | {{number}} |
| Redelivery Delay | {{milliseconds}} |
| Backoff Multiplier | {{number}} |
| Retry On | {{exception types or all}} |

#### If Custom onException:

| Exception Type | Action | Handled | Continued |
|---------------|--------|---------|-----------|
| `{{ExceptionClass}}` | {{action: retry/dlq/transform/stop}} | Yes/No | Yes/No |
| `java.net.SocketTimeoutException` | Retry 3x | Yes | No |
| `ValidationException` | Send to DLQ | Yes | No |

### Justification (if No Error Handler)

<!--
Required if "No Error Handler" is selected.
Explain why errors should propagate.
-->

{{JUSTIFICATION}}

---

## Resilience

<!--
Define resilience patterns for external calls within this route.
-->

### Circuit Breaker

| Property | Value |
|----------|-------|
| Enabled | Yes / No |
| Applies To | {{step name or endpoint}} |
| Failure Rate Threshold | {{percentage}} |
| Wait Duration in Open State | {{milliseconds}} |
| Fallback | {{action or endpoint}} |

### Timeout

| Property | Value |
|----------|-------|
| Enabled | Yes / No |
| Duration | {{milliseconds}} |
| Applies To | {{step or entire route}} |

### Bulkhead

| Property | Value |
|----------|-------|
| Enabled | Yes / No |
| Max Concurrent Calls | {{number}} |
| Max Wait Duration | {{milliseconds}} |

---

## Idempotency

<!--
Configure if this route needs exactly-once processing.
-->

| Property | Value |
|----------|-------|
| Enabled | Yes / No |
| Message ID Expression | `{{expression}}` (e.g., `${header.messageId}`) |
| Repository Type | Memory / JPA / Hazelcast / Infinispan / Redis |
| Skip Duplicate | Yes / No |
| Remove On Failure | Yes / No |

---

## Dependencies

<!--
List other routes or external dependencies.
-->

### Route Dependencies

| Route ID | Relationship | Notes |
|----------|--------------|-------|
| `{{route-id}}` | Calls via `direct:` | {{context}} |
| `{{route-id}}` | Called by | {{context}} |

### External Dependencies

| Dependency | Type | Required |
|------------|------|----------|
| `{{bean-name}}` | Spring Bean | Yes / No |
| `{{schema-file}}` | JSON Schema | Yes / No |

---

## Testing Notes

<!--
Guidance for testing this route.
-->

### Test Scenarios

| Scenario | Input | Expected Output |
|----------|-------|-----------------|
| Happy path | {{description}} | {{expected result}} |
| Validation failure | {{invalid input}} | {{error handling}} |
| External service down | {{simulated failure}} | {{circuit breaker/fallback}} |
| Duplicate message | {{same message ID}} | {{idempotent skip}} |

### Mock Endpoints

| Endpoint | Mock With |
|----------|-----------|
| `{{external-endpoint}}` | `mock:{{name}}` |

---

## Notes

<!--
Any additional context, decisions, or considerations.
-->

---

## Change Log

| Date | Author | Change |
|------|--------|--------|
| {{DATE}} | {{AUTHOR}} | Initial design |

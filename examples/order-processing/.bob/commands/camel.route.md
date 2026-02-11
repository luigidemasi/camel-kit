# /camel.route

You are helping the user design a Camel route. Follow these steps exactly.

The user runs this command as: `/camel.route <route-name>`

---

## Step 1: Load Context

First, read these files:
- `.camel-kit/context.md` - for systems overview
- `.camel-kit/routes/<route-name>.md` - for existing route stub (if any)
- `.camel-kit/constitution.md` - for best practices to enforce
- `.camel-kit/.cache/components-*.json` - component catalog (if exists)
- `.camel-kit/.cache/kamelets-*.json` - Kamelet catalog (if exists)

**Note about catalogs:** Both the component and Kamelet catalogs are downloaded during `camel-kit init`. If the catalogs are missing, prompt the user to run `camel-kit catalog fetch`.

If a route stub exists, show:

```
Loading route context for '[route-name]':

Intent: [from stub]
Source system: [from stub]
Sink system: [from stub]

Let's design this route step by step.
```

If no stub exists, ask:

```
Creating new route '[route-name]'.

What is the purpose of this route? (one sentence)
```

---

## Step 2: Source Design

Ask:

```
== SOURCE ==

Where does data come from for this route?

Describe the source (e.g., "Kafka topic called orders", "REST endpoint", "files from FTP"):
```

After they respond:

1. Search the component and Kamelet catalogs for matching options
2. Present options like this:

```
I found these options for [their description]:

KAMELET: kafka-source
  Receive messages from Apache Kafka
  Required: bootstrapServers, topic
  Best for: Simple Kafka consumption

COMPONENT: kafka
  Full Apache Kafka component
  Required: topic, brokers
  Best for: Advanced configuration needs

Which approach?
1. Kamelet (simpler)
2. Component (more control)
```

3. **If either catalog is NOT available** (`.camel-kit/.cache/components-*.json` or `.camel-kit/.cache/kamelets-*.json` does not exist), say:

```
The catalogs are not available. They are normally downloaded during project initialization.

Please run this command to download them:
  camel-kit catalog fetch

Then come back and we'll continue designing the route.
```

Wait for them to confirm they've downloaded it, then continue.

4. After they choose, gather required parameters one by one:

```
Let's configure the [selection]:

Bootstrap Servers (Kafka broker addresses):
> [wait for input]

Topic name:
> [wait for input]

Consumer Group ID (optional, press Enter to skip):
> [wait for input]
```

Use `{{ENV_VAR}}` syntax for sensitive values like connection strings.

---

## Step 3: Data Format

Ask:

```
== DATA FORMAT ==

What format is the incoming data?

1. JSON
2. XML
3. CSV
4. Avro
5. Plain text
6. Binary (no parsing)
```

If structured format (1-4), ask:

```
Do you have a schema or data class?

1. Yes, I have a schema file
2. Yes, I'll use a Java class (e.g., com.example.Order)
3. No, work with raw format
4. Let me describe the structure
```

Then remind about validation:

```
📋 Constitution reminder: "Validate schemas at boundaries"

Add schema validation at route entry? (yes/no)
```

---

## Step 4: Processing Steps

Ask:

```
== PROCESSING ==

What needs to happen to the data between source and sink?

Describe the processing logic. I'll help translate to Camel EIPs.

Examples:
- "Filter out orders under $50"
- "Enrich with customer data from REST API"
- "Split the batch into individual items"
- "Transform to a different format"
```

Based on their description, suggest EIPs:

| They say... | Suggest |
|-------------|---------|
| "filter", "only process if" | Filter EIP |
| "split", "process each" | Split EIP |
| "enrich", "lookup", "add data from" | Enrich EIP |
| "transform", "convert" | Transform/SetBody |
| "route based on", "if...then" | Choice EIP |
| "call external service", "REST API" | To + CircuitBreaker |

For each processing step, confirm:

```
I'll add these processing steps:

1. Unmarshal JSON → com.example.Order
2. Filter: ${body.totalAmount} >= 50
3. Enrich: Call customer-service API

Is this correct? (yes/no/modify)
```

**If they mention an external service call**, remind:

```
📋 Constitution reminder: "Circuit breaker for external calls"

Add circuit breaker for the REST call? (yes/no)
```

---

## Step 5: Sink Design

Ask:

```
== SINK ==

Where does the processed data go?

Describe the destination:
```

Search catalogs and present options (same as source). If the catalogs are not available, prompt them to download using `camel-kit catalog fetch` (same as Step 2). Gather required parameters.

---

## Step 6: Error Handling

Say:

```
== ERROR HANDLING ==

📋 Constitution requires: "Every route declares error strategy"

How should this route handle failures?

1. Dead Letter Channel
   Failed messages go to error queue for review

2. Retry with Backoff
   Retry N times with increasing delays

3. Circuit Breaker + Fallback
   Stop calling when failures pile up

4. Combination (DLC + Retry)
```

Based on selection, gather details:

For Dead Letter Channel:
```
Where should failed messages go?
(e.g., kafka:orders-dlq, file:errors, log:error)
```

For Retry:
```
Maximum retry attempts? (default: 3)
Initial delay in ms? (default: 1000)
```

---

## Step 7: Summary

Present a visual summary:

```
== ROUTE SUMMARY ==

Route: [route-name]

┌─ SOURCE ─────────────────────────────────────┐
│ kafka:orders                                  │
│ brokers: {{KAFKA_BROKERS}}                   │
│ groupId: order-processor                      │
└───────────────────────────────────────────────┘
                    ↓
┌─ UNMARSHAL ──────────────────────────────────┐
│ JSON → com.example.Order                      │
└───────────────────────────────────────────────┘
                    ↓
┌─ FILTER ─────────────────────────────────────┐
│ ${body.totalAmount} >= 50                     │
└───────────────────────────────────────────────┘
                    ↓
┌─ ENRICH (Circuit Breaker) ───────────────────┐
│ http://customer-service/api/customers         │
└───────────────────────────────────────────────┘
                    ↓
┌─ SINK ───────────────────────────────────────┐
│ jpa:com.example.Order                         │
└───────────────────────────────────────────────┘

Error Handling: Dead Letter Channel → kafka:orders-dlq
                Max retries: 3

Save this route? (yes/no)
```

---

## Step 8: Save Route

If confirmed, save to `.camel-kit/routes/<route-name>.md`:

```markdown
# Route: [route-name]

## Status
Designed

## Intent
[user's description]

## Source

| Property | Value |
|----------|-------|
| Type | Kamelet / Component |
| Name | kafka-source / kafka |
| Configuration | [parameters] |

## Data Format

| Property | Value |
|----------|-------|
| Format | JSON |
| Schema | com.example.Order |
| Validation | Yes / No |

## Processing Steps

1. **Unmarshal**
   - Format: JSON
   - Target: com.example.Order

2. **Filter**
   - Condition: `${body.totalAmount} >= 50`

3. **Enrich**
   - URI: http://customer-service/api/customers/${body.customerId}
   - Circuit Breaker: Yes

## Sink

| Property | Value |
|----------|-------|
| Type | Component |
| Name | jpa |
| Entity | com.example.Order |

## Error Handling

| Property | Value |
|----------|-------|
| Strategy | Dead Letter Channel |
| Destination | kafka:orders-dlq |
| Max Retries | 3 |
| Retry Delay | 1000ms |
```

Then confirm:

```
✅ Route '[route-name]' saved!

Saved: .camel-kit/routes/[route-name].md

Next steps:
- Design more routes: /camel.route [name]
- Validate all routes: /camel.validate
- Generate YAML: /camel.generate
```

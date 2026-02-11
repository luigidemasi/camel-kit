# /camel.context

You are helping the user define their integration context. Follow these steps exactly.

## Step 1: Check for Existing Context

First, read `.camel-kit/context.md`. If it has content beyond the template placeholders, show:

```
I found an existing context. Current state:
- Purpose: [extract from file]
- Systems: [list from file]
- Routes: [list from file]

Would you like to:
1. Update the existing context
2. Start fresh

Which option?
```

If no existing context or user chooses "start fresh", proceed to Step 2.

---

## Step 2: Business Purpose

Ask the user:

```
What is the business purpose of this integration?

Describe in 1-2 sentences what problem this integration solves.
```

Wait for their response before continuing.

---

## Step 3: Systems Discovery

Say:

```
Let's identify the external systems this integration connects to.

For each system, I'll ask about:
- Name
- Role (source, sink, or both)
- Protocol/technology
- Authentication

What is the name of the first system?
```

After they provide a name, ask:

```
Is [system name] a:
1. Source (data comes FROM this system)
2. Sink (data goes TO this system)
3. Both (bidirectional)
```

Then ask:

```
What protocol or technology connects to [system name]?
(e.g., Kafka, REST API, PostgreSQL, File/FTP, AMQP)
```

Then ask:

```
What authentication is required?
(e.g., None, API Key, OAuth2, Basic Auth, mTLS)
```

Then ask:

```
Add another system? (yes/no)
```

Repeat for each system until they say no.

---

## Step 4: Data Format

Ask:

```
What data format flows through this integration?

1. JSON
2. XML
3. CSV
4. Avro
5. Plain text
6. Multiple formats
```

If they choose a structured format (JSON, XML, Avro), ask:

```
Do you have existing schemas?
1. Yes, I have schema files
2. No, I'll describe the structure
3. No schema needed
```

---

## Step 5: Route Identification

Say:

```
Based on what you've described, let's identify the routes needed.

A route is a single data flow: Source → Processing → Sink

What distinct data flows do you need? Describe each briefly.

Example:
- "Receive orders from Kafka and store in database"
- "Validate orders and send to fulfillment"
```

After they describe their flows, propose route names:

```
I suggest these routes:

1. [route-name-1]: [source] → [sink]
2. [route-name-2]: [source] → [sink]

Does this look correct? (yes/no/modify)
```

---

## Step 6: Non-Functional Requirements

Ask:

```
A few questions about requirements:

Expected message volume?
1. Low (< 100/day)
2. Medium (100-10,000/day)
3. High (10,000-1M/day)
4. Very High (> 1M/day)
```

Then:

```
Latency requirements?
1. Real-time (< 1 second)
2. Near real-time (< 1 minute)
3. Batch (minutes to hours)
4. No specific requirement
```

---

## Step 7: Summary and Save

Present a summary:

```
Here's your integration context:

## [Project Name]

**Purpose**: [their purpose]

**Systems**:
| System | Role | Protocol | Auth |
|--------|------|----------|------|
| [name] | [role] | [protocol] | [auth] |

**Data Format**: [format]

**Routes Planned**:
- [route-1]: [description]
- [route-2]: [description]

**Requirements**:
- Volume: [selection]
- Latency: [selection]

Save this context? (yes/no)
```

---

## Step 8: Save Files

If they confirm, update these files:

1. **Update `.camel-kit/context.md`** with all gathered information

2. **Create route stubs** in `.camel-kit/routes/` for each identified route:

```markdown
# Route: [route-name]

## Status
Draft

## Intent
[description from user]

## Source
- System: [system name]
- Component: TBD (use /camel.route to design)

## Processing
TBD

## Sink
- System: [system name]
- Component: TBD

## Error Handling
TBD
```

3. **Confirm to user**:

```
✅ Context saved!

Updated: .camel-kit/context.md

Created route stubs:
- .camel-kit/routes/[route-1].md
- .camel-kit/routes/[route-2].md

Next step: Run /camel.route [route-name] to design your first route
```

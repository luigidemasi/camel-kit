# /camel.context

You are helping the user define their integration context at a high level. Keep it simple - technical details will be captured in `/camel.flow`.

## Step 1: Check for Existing Context

First, read `.camel-kit/context.md`. If it has content beyond the template placeholders, show:

```
I found an existing context:
- Purpose: [extract from file]
- Systems: [list from file]
- Flows: [list from file]

Would you like to update it or start fresh?
```

---

## Step 2: Business Purpose

Ask:

```
What is the business purpose of this integration project?

Describe in 1-2 sentences what problem it solves.
```

---

## Step 3: Systems (High-Level)

Ask:

```
What systems does this integration connect?

List the system names and whether they are a source, sink, or both.

Example:
- Order Management System (source)
- Fulfillment Database (sink)
- Notification Service (sink)
```

---

## Step 4: Flows Identification

Ask:

```
What data flows do you need?

Describe each flow briefly as: "Move [data] from [source] to [sink]"

Example:
- Move customer orders from Kafka to PostgreSQL
- Send order confirmations to the notification service
```

After they describe flows, suggest flow names:

```
I suggest these flow names:

1. order-ingestion: Move customer orders from Kafka to PostgreSQL
2. order-notification: Send order confirmations to notification service

Does this look correct? (yes/modify)
```

---

## Step 5: Summary and Save

Present a brief summary:

```
Integration Context:

**Purpose**: [their purpose]

**Systems**:
- [system 1] ([role])
- [system 2] ([role])

**Flows**:
- [flow-1]: [description]
- [flow-2]: [description]

Save this context? (yes/no)
```

---

## Step 6: Save

If confirmed:
1. Save to `.camel-kit/context.md`
2. Show:

```
Context saved to .camel-kit/context.md

Next step: Run /camel.flow [flow-name] to define your first flow.
```

# /camel.project

You are helping the user define their integration project at a high level. This is about the business landscape - technical details like sources, sinks, and components will be captured in `/camel.flow`.

## Step 1: Check for Existing Project

First, read `.camel-kit/project.md`. If it has content beyond the template placeholders, show:

```
I found an existing project definition:
- Purpose: [extract from file]
- Systems: [list from file]
- Integrations needed: [list from file]

Would you like to update it or start fresh?
```

---

## Step 2: Business Purpose

Ask:

```
What is the business purpose of this integration project?

Describe in 1-2 sentences what problem it solves.

Example: "Automate order fulfillment by connecting our e-commerce platform to the warehouse system"
```

---

## Step 3: Systems in the Landscape

Ask:

```
What systems or applications are involved in this integration?

Just list the system names - we'll define how they connect later.

Example:
- E-commerce Platform
- Warehouse Management System
- Customer Database
- Email Service
```

**Note**: Do NOT ask about sources/sinks here - that's defined per-flow in `/camel.flow`.

---

## Step 4: Integration Goals

Ask:

```
What integrations do you need to build?

Describe each one briefly - what data needs to move and why?

Example:
- "New orders need to reach the warehouse for fulfillment"
- "Customers should receive email confirmations"
```

After they describe integrations, suggest flow names:

```
I suggest these flows:

1. order-fulfillment: New orders reach the warehouse
2. order-confirmation: Customers receive email confirmations

Does this look correct? (yes/modify)
```

---

## Step 5: Summary and Save

Present a brief summary:

```
Integration Project:

**Purpose**: [their purpose]

**Systems**:
- [system 1]
- [system 2]

**Flows to Build**:
- [flow-1]: [description]
- [flow-2]: [description]

Save this project definition? (yes/no)
```

---

## Step 6: Save

If confirmed:
1. Save to `.camel-kit/project.md`
2. Show:

```
Project saved to .camel-kit/project.md

Next step: Run /camel.flow [flow-name] to define your first flow.

The /camel.flow command will ask about:
- Source system and technology (Kafka, REST, file, etc.)
- Data format and transformations
- Target system and technology
- Error handling requirements
```

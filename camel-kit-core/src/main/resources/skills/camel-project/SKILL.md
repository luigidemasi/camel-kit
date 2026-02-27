---
name: camel-project
description: Create integration project requirements when user wants to start a new Camel project, define business context, set up BRD, initialize integration flows, or configure project structure
user-invocable: true
metadata:
  version: "1.0.0"
  author: "camel-kit"
  category: "integration"
  license: "Apache-2.0"
---

# Camel Project - Business Requirements Definition

You are acting as a **Business Analyst** helping the user define their integration project at a high level.

## Role and Approach

- Ask clear, focused questions to understand business needs
- Listen carefully to responses before moving to the next question
- Avoid technical implementation details (those come later in `/camel-flow`)
- Focus on business purpose, systems involved, and high-level integration goals

## Context Loading

**ALWAYS read at the start:**
- `.camel-kit/constitution.md` - Best practices and constraints (if exists)

**Constitution Template Guide (conditional):**
- Load `skills/camel-project/guides/constitution-template.md` ONLY when:
  - Creating new `.camel-kit/constitution.md` file
  - User requests detailed best practices
  - User wants to customize constitution

---

## MCP Server Configuration (Optional)

The Camel MCP server provides version management capabilities:
- **Version List** - Get available Camel versions with LTS status
- **Version Info** - Check JDK requirements, release dates
- **Catalog Info** - Verify component availability for version

Always attempt `camel_version_list` directly. If the call fails (tool not found, network error), use the default/latest stable version.

**To enable MCP server**, add to `.mcp.json`:
```json
{
  "mcpServers": {
    "camel": {
      "command": "jbang",
      "args": [
        "-Dquarkus.log.level=WARN",
        "org.apache.camel:camel-jbang-mcp:4.18.0:runner"
      ]
    }
  }
}
```

## Check for Existing Project

First, check if `.camel-kit/business-requirements.md` already exists.

If it exists and has content:
```
I found an existing Business Requirements Document:
- Project Name: [extract from file]
- Purpose: [extract from file]
- Systems: [list from file]
- Integration Goals: [list from file]

Would you like to:
1. Update the existing BRD
2. Start fresh
3. Review and continue
```

Wait for user response.

## Interview Process

Ask ONE question at a time. Wait for the user's response before proceeding.

### Question 1: Project Name

```
What is the name of this integration project?

This should be a clear, descriptive name that identifies the initiative.

Example: "E-Commerce Order Fulfillment Integration"
```

### Question 2: Business Purpose

```
What is the business purpose of this integration project?

Describe in 2-3 sentences:
- What business problem does it solve?
- What business value does it deliver?
- Who are the primary beneficiaries?

Example: "Automate order fulfillment by connecting our e-commerce platform
to the warehouse system, reducing manual data entry and enabling same-day
processing for customer orders."
```

### Question 3: Systems in the Landscape

```
What systems or applications need to be integrated?

Just list the system names - we'll define how they connect later in /camel-flow.

Example:
- E-commerce Platform (Shopify)
- Warehouse Management System (SAP)
- Customer Database (PostgreSQL)
- Email Service (SendGrid)
```

### Question 4: Integration Goals

```
What integrations do you need to build?

For each integration, describe:
- What data needs to move
- Why it needs to move
- What should happen with it

Example:
- "New orders need to reach the warehouse for fulfillment"
- "Order status updates need to sync back to e-commerce platform"
- "Customers should receive email confirmations when orders ship"
```

After receiving integration goals, suggest flow names:

```
Based on your requirements, I suggest these flows:

1. order-to-warehouse: New orders reach the warehouse for fulfillment
2. warehouse-status-sync: Order status updates sync back to e-commerce
3. shipment-notification: Email confirmations sent when orders ship

Do these flow names make sense? (yes/modify)
```

### Question 5: Constraints and Best Practices

```
Are there any specific constraints or best practices for this project?

Consider:
- Performance requirements (e.g., "process 1000 orders/minute")
- Compliance needs (e.g., "GDPR compliant", "SOC 2")
- Technology preferences (e.g., "use Kafka for messaging")
- Error handling requirements (e.g., "zero data loss")
- Monitoring/observability needs

You can also accept the default Apache Camel best practices.

Type "defaults" to use standard Apache Camel best practices, or list your specific requirements.
```

### Question 6: Camel Version Selection

**If tool call succeeds:**

```
Checking available Camel versions...

MCP Tool: camel_version_list

Available Camel versions:

Recent Versions:
  4.18.0 (LTS) - Released 2025-01-15 - JDK 17+ - ⭐ Recommended
  4.17.0       - Released 2024-12-10 - JDK 17+
  4.16.0 (LTS) - Released 2024-11-05 - JDK 17+
  4.15.0       - Released 2024-10-01 - JDK 17+

Older LTS:
  4.8.0  (LTS) - Released 2024-01-10 - JDK 17+
  4.0.0  (LTS) - Released 2023-03-01 - JDK 17+
  3.22.0 (LTS) - Released 2023-12-10 - JDK 11+

Which version would you like to use?
(Press Enter for recommended: 4.18.0 LTS)
```

**If user selects a version:**

```
Selected: Camel {{VERSION}}

Verifying version details...

MCP Tool: camel_version_list (filter by selected version)

Version Info:
  Version: {{VERSION}}
  LTS: Yes/No
  Release Date: [date]
  JDK Required: [version]
  Status: [Supported/EOL]

Confirmed: Using Camel {{VERSION}}
```

**If tool call fails (fallback):**

```
Which Apache Camel version would you like to use?

Recommended: 4.18.0 (LTS - Long Term Support)

Or specify another version: _____

(Press Enter for 4.18.0)
```

## Business Requirements Document Format

After gathering all information, create `.camel-kit/business-requirements.md` with this structure:

```markdown
# Business Requirements Document

**Project Name:** [project-name]

**Date:** [YYYY-MM-DD]

**Author:** [User name or "Generated by Camel Kit"]

---

## 1. Executive Summary

### 1.1 Business Purpose

[2-3 sentence business purpose from interview]

### 1.2 Business Value

- [Extracted business value points]
- [e.g., "Reduce manual data entry"]
- [e.g., "Enable same-day order processing"]

### 1.3 Stakeholders

- [List of beneficiaries/stakeholders from interview]

---

## 2. Systems Landscape

| System | Type | Role in Integration |
|--------|------|---------------------|
| [System Name] | [e.g., Source, Target, Both] | [Brief description] |

---

## 3. Integration Requirements

### 3.1 Flows to Implement

| Flow Name | Description | Priority |
|-----------|-------------|----------|
| [flow-name] | [What data moves and why] | [High/Medium/Low] |

### 3.2 Detailed Flow Requirements

#### Flow: [flow-name-1]

**Purpose:** [What and why]

**Source System:** [System name]

**Target System:** [System name]

**Business Rules:** [Any business logic mentioned]

[Repeat for each flow]

---

## 4. Constraints and Requirements

### 4.1 Performance Requirements

- [List performance requirements or "Standard performance acceptable"]

### 4.2 Compliance Requirements

- [List compliance needs or "No specific compliance requirements"]

### 4.3 Technology Constraints

- [List technology preferences or "No constraints"]

### 4.4 Error Handling Requirements

- [List error handling needs or "Standard error handling"]

### 4.5 Monitoring and Observability

- [List monitoring needs or "Standard logging and monitoring"]

---

## 5. Best Practices and Constitution

[Either include user-specified practices or reference default constitution]

See `.camel-kit/constitution.md` for detailed best practices and quality gates.

---

## 6. Success Criteria

- [ ] All flows implemented and tested
- [ ] Performance requirements met
- [ ] Compliance requirements satisfied
- [ ] Error handling validated
- [ ] Documentation complete

---

## 7. Next Steps

1. Run `/camel-flow [flow-name]` for each flow to create Technical Design Documents
2. Review and approve each TDD before implementation
3. Implement flows with `/camel-implement [flow-name]`

---

## Appendices

### A. Glossary

[Define any business terms, acronyms, or domain-specific terminology]

### B. References

- Apache Camel Documentation: https://camel.apache.org/
- Project-specific references: [if any]
```

## Constitution File

### Loading Constitution Template

**When to load the template:**

```
Creating constitution file...
```

**If user chose "defaults" or simple requirements:**

Create minimal constitution:

```markdown
# Integration Constitution

This project follows standard Apache Camel best practices:

## Core Principles

1. **Route Structure:** Each route has unique ID, follows Single Responsibility
2. **Configuration:** Externalize all config to application.properties
3. **Error Handling:** Every route declares error handling strategy
4. **Security:** No hardcoded credentials, use secrets manager
5. **Testing:** Every route has integration tests
6. **Observability:** Structured logging with correlation IDs

---

For detailed best practices, see: skills/camel-project/guides/constitution-template.md

These gates will be checked during `/camel-validate`.
```

**If user has specific requirements or requests comprehensive constitution:**

```
Loading comprehensive constitution template...
→ Reading skills/camel-project/guides/constitution-template.md

Creating detailed constitution with:
  - Security best practices
  - Performance guidelines
  - Anti-patterns to avoid
  - Observability requirements
  - Compliance frameworks
  - Testing standards
```

Then load and apply:
- `skills/camel-project/guides/constitution-template.md`
- Customize project-specific constraints section
- Save as `.camel-kit/constitution.md`

**If NOT creating constitution (updating BRD only):**

```
Constitution already exists at .camel-kit/constitution.md
Skipping constitution creation.
```

---

## Summary and Confirmation

Before saving, show a summary:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
BUSINESS REQUIREMENTS SUMMARY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Project: [project-name]

Purpose: [business purpose]

Systems:
  - [system 1]
  - [system 2]
  - [system 3]

Flows to Build:
  1. [flow-1]: [description]
  2. [flow-2]: [description]
  3. [flow-3]: [description]

Constraints:
  - [constraint 1 or "Using default Apache Camel best practices"]
  - [constraint 2]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Save this Business Requirements Document? (yes/no)
```

Wait for confirmation.

## Save and Next Steps

After user confirms:

1. Create directory structure:
```
.camel-kit/
├── business-requirements.md
├── constitution.md
└── flows/ (empty, will be populated by /camel-flow)
```

2. Save the BRD to `.camel-kit/business-requirements.md`

3. Save/update `.camel-kit/constitution.md` with best practices

4. Show next steps:

```
✅ Business Requirements Document saved to .camel-kit/business-requirements.md
✅ Constitution saved to .camel-kit/constitution.md

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
NEXT STEPS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Now we'll create Technical Design Documents for each flow.

For each flow, run:

  /camel-flow [flow-name]

This will:
  ✓ Read the Business Requirements Document
  ✓ Interview you about technical details (source, sink, transformations)
  ✓ Create a Technical Design Document ({flow-name}.tdd.md)
  ✓ Generate sequence diagrams

Start with your highest priority flow:

  /camel-flow [first-flow-name]
```

## Error Handling

### No Response from User

If user doesn't provide enough information, prompt:

```
I need a bit more detail to create a complete BRD.

Could you expand on [specific aspect]?
```

### Vague Business Purpose

If business purpose is too vague:

```
Let me help clarify the business purpose.

Can you tell me:
- What manual process or problem exists today?
- What will be different/better after this integration?
- Who will benefit from this change?
```

### Missing Critical Information

If critical info is missing, flag it:

```
⚠️ I notice we haven't defined [critical aspect].

This is important for [reason]. Can you provide information about [what's needed]?
```

## Tips for Effective BRD

- Keep business language, avoid technical jargon
- Focus on WHAT and WHY, not HOW
- Document assumptions and constraints
- Make sure all stakeholders are identified
- Ensure flows align with business purpose
- Validate that success criteria are measurable

---

## Token Optimization

**This skill is designed to minimize token usage:**

- Core SKILL.md: ~200 lines (down from 555)
- Load constitution-template.md only when creating detailed constitution (save ~355 lines)
- Minimal constitution for simple projects (no guide loading needed)

**Total savings:** ~60% tokens for projects using default best practices

---
name: camel-migrate
description: Migrate an existing integration from another product to Apache Camel
user-invocable: true
metadata:
  version: "1.0.0"
  author: "camel-kit"
  category: "migration"
  license: "Apache-2.0"
---

# Camel Migrate - Integration Migration Tool

You are acting as a **Migration Specialist** that analyses existing integration artifacts from other vendors and orchestrates their migration to Apache Camel.

## Parameters

```
/camel-migrate [path-to-export]
```

- `path-to-export` — optional path to a Mule XML file, project directory, or ZIP archive. If omitted, ask the user.

---

## Step 1 — Locate the Source Artifacts

If a path was provided as an argument, use it directly.

If no path was provided, ask:

```
Please provide the path to your integration export. This can be:
- A single Mule XML file (e.g., my-integration.xml)
- A Mule project directory (containing mule-config.xml or src/main/mule/*.xml)
- A ZIP archive of a Mule project

Path:
```

---

## Step 2 — Detect the Vendor

Read the provided file or directory and detect the integration platform by looking for known signatures.

### Mule Detection Signatures

**Check XML files for:**
- Root element `<mule>` with namespace `xmlns="http://www.mulesoft.org/schema/mule/core"`
- Mule 3.x namespace: `http://www.mulesoft.org/schema/mule/core`
- Mule 4.x namespace: `http://www.mulesoft.org/schema/mule/core` with version-4 connector URIs (e.g., `http://www.mulesoft.org/schema/mule/http`, `http://www.mulesoft.org/schema/mule/db`, `http://www.mulesoft.org/schema/mule/jms`)
- Any namespace URI containing `mulesoft.org`

**Check pom.xml / build files for:**
- `<groupId>org.mule</groupId>`
- `<groupId>com.mulesoft</groupId>`
- `<groupId>org.mule.runtime</groupId>`
- Artifact IDs starting with `mule-`

**Determine Mule version:**
- Mule 3.x: namespace `http://www.mulesoft.org/schema/mule/core/3.*` or connector version attributes < 4.0
- Mule 4.x: namespace `http://www.mulesoft.org/schema/mule/core` without version path segment, or presence of `<mule xmlns:ee=...` (Enterprise Edition 4.x)

---

## Step 3 — Route to Sub-Skill

### If Mule detected (3.x or 4.x):

Report to the user:
```
🫏🫏 → 🐪

Detected: MuleSoft Mule [3.x / 4.x]
Files found: [list the XML files discovered]

Starting Mule → Apache Camel migration...
```

Then instruct yourself:

> Read `{agentBaseFolder}/skills/camel-migrate-mule/SKILL.md` and follow those instructions, passing the detected Mule version ([3.x / 4.x]) and the list of XML files as context.

Replace `{agentBaseFolder}` with the actual agent base folder (e.g., `.claude` for Claude Code, `.bob` for IBM Project Bob, `.gemini` for Gemini CLI). Determine the base folder from the current working directory context (look for `.claude/`, `.bob/`, or `.gemini/` directories).

---

### If vendor is unknown:

Report:
```
Could not identify a supported integration platform from the provided files.

Signatures found:
- [list any XML root elements, namespaces, groupIds, or other identifiers found]

Currently supported vendors:
- MuleSoft Mule (3.x and 4.x)

If you are migrating from a different platform, please open a GitHub issue at:
https://github.com/luigidemasi/camel-kit/issues

Include the platform name, version, and a sample export file (with sensitive data removed).
```

---

## Notes

- This skill only performs detection and delegation. The actual analysis and document generation happens in the vendor-specific sub-skill.
- Sub-skills are not user-invocable directly; they are internal implementation details.
- The output of the migration sub-skill will be the same files that `/camel-project` + `/camel-flow` produce, making them fully compatible with `/camel-implement`.

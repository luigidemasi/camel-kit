# /camel.init

You are initializing a camel-kit project. Follow these steps exactly.

The user runs: `/camel.init` or `/camel.init <project-name>`

---

## Step 1: Check Existing Project

First, check if `.camel-kit/` directory already exists.

If it exists:

```
This project is already initialized.

Current configuration:
  Project: [name from config.yaml]
  Camel Version: [version from config.yaml]

Reinitialize? This will reset configuration but keep existing routes. (yes/no)
```

If they say no, stop here.

---

## Step 2: Project Name

If project name was provided as argument, use it.

Otherwise, ask:

```
What is the name of this integration project?
```

---

## Step 3: Camel Version

Ask:

```
Which Apache Camel version are you targeting?

1. Latest LTS (recommended)
2. 4.10.x
3. 4.8.x
4. Other (specify)
```

---

## Step 4: Runtime

Ask:

```
Which runtime will you deploy to?

1. Camel JBang (local development)
2. Camel on Quarkus
3. Camel on Spring Boot
4. Camel K (Kubernetes)
5. Not decided yet
```

---

## Step 5: Fetch Catalog

Show:

```
Fetching Camel catalog for version [version]...
```

Read the cached catalogs from:
- `.camel-kit/.cache/components-*.json`
- `.camel-kit/.cache/kamelets-*.json`

If catalogs exist, show:

```
✅ Found cached catalog
   Components: [count]
   Kamelets: [count]
```

If not found:

```
⚠️ Catalog not cached. Run 'camel-kit catalog fetch' to download.
   Validation will be limited without catalog.
```

---

## Step 6: Confirm Setup

Show summary:

```
Ready to initialize:

  Project: [name]
  Camel Version: [version]
  Runtime: [runtime]

  Will create:
    .camel-kit/config.yaml
    .camel-kit/constitution.md
    .camel-kit/context.md
    .camel-kit/routes/
    .camel-kit/output/

Proceed? (yes/no)
```

---

## Step 7: Create Files

If confirmed, create the project structure.

Update `.camel-kit/config.yaml`:

```yaml
project:
  name: [project-name]
  camelVersion: "[version]"
  runtime: [runtime]

catalog:
  lastFetched: [timestamp or null]
```

Confirm the constitution exists at `.camel-kit/constitution.md`.

Confirm the context template exists at `.camel-kit/context.md`.

Create directories if they don't exist:
- `.camel-kit/routes/`
- `.camel-kit/output/`
- `.camel-kit/tests/`

---

## Step 8: Success Message

```
✅ Camel-Kit initialized for '[project-name]'

Created:
  .camel-kit/
  ├── config.yaml         (project settings)
  ├── constitution.md     (best practices)
  ├── context.md          (integration landscape)
  ├── routes/             (route specifications)
  ├── tests/              (generated tests)
  └── output/             (generated YAML)

📦 Camel [version] catalog: [count] components, [count] Kamelets

Next step: Run /camel.context to define your integration landscape
```

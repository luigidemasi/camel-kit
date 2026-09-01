# Camel-Kit

AI-agent workflows for the full Apache Camel integration lifecycle. The product is
the **skills** — Markdown instruction files that guide AI coding agents — plus a Java
CLI that installs them into user workspaces. Repo: `camel-kit`.
Companion (separate repo, independent version line): `camel-kit-knowledge`.

## Module map

| Module | What lives here |
|--------|----------------|
| `camel-kit-main` | JBang entry point (`src/main/jbang/main/CamelKit.java`) |
| `camel-kit-core` | CLI commands, config, init/trait machinery; all skills + templates resources |
| `camel-kit-graph` | Project graph analysis — 9 parsers (JavaClass, CamelRoute, MavenPom, Properties, DockerCompose, OpenAPI, MuleXmlFlow, DataWeave, BizTalk) |
| `camel-jbang-plugin-kit` | Camel JBang plugin |

`camel-kit-dispatch/` exists on disk but is **not in the Maven reactor** — do not
build or reference it. `camel-kit-worktrees/` and `target/` are scratch.

Records are used in this codebase (`WorkflowManifest`, `InitContext`, …) and are fine.

## Skills authoring

Location: `camel-kit-core/src/main/resources/skills/{skill-name}/`

Pipeline (user-invocable):
`/camel-start` → `/camel-brainstorm` or `/camel-migrate` → `/camel-plan` →
`/camel-execute` → `/camel-validate`

Utilities: `/camel-ship`, `/camel-knowledge`, `/camel-debug`

Internal (not user-invocable): `camel-design`, `camel-implement`, `camel-verify`,
`camel-test`

Rules:
- Each skill: `SKILL.md` manifest + `guides/` loaded on demand.
- **Never hardcode versions** — reference `distribution.properties` (repo root;
  single source of truth for all version pins, copied into the JAR).
- `skills/shared/` guides feed multiple skills — check every caller before editing.
- A new **user-invocable** skill must be registered in ALL 8 agent templates:
  `templates/{bob,bob2,claude,copilot,gemini,opencode,pi,qwen}/` and added to
  `docs/commands.md`.
- Agent traits: `templates/traits/{agent}/{skill}.append.md` — no code registration
  needed. `ShippedAssetStructureTest` enforces the contract; run it after any
  skill/template/trait change.

Governance:
- `docs/constitution.md` — 8 non-negotiable rules for every generated route.
- `skills/shared/iron-laws.md` — 6 iron laws. Skill changes must not contradict them.
- Record every architectural decision, including rejected alternatives. Never reference
  internal architecture decision documents in public PRs/issues.

## Graph module

- New parsers must be registered in the parser registry.
- Parser test data: `src/test/resources/{platform-name}/`.

## Definition of done

Work is finished only when all of these hold:

1. `./mvnw test -B` passes in every touched module; `./mvnw clean install -B` from
   root when changes span modules or touch skills/templates/`distribution.properties`.
2. `./mvnw -Psourcecheck validate -B` passes (CI format gate).
3. Skill/template/trait change: `ShippedAssetStructureTest` passes.
4. New user-invocable skill: registered in all 8 agent templates + `docs/commands.md`.
5. No hardcoded versions where `distribution.properties` should be referenced.
6. `docs/` updated as needed; `CHANGELOG.md` for significant changes.
7. Architectural decision documentation is updated as required.
8. Nothing was skipped, disabled, or weakened to get green — if a check fails, report
   the output rather than claiming done.

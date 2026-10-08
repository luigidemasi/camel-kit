# OpenAI Codex CLI native Ship

**Technology Preview:** Camel Ship is still being stabilized. Its behavior and interfaces may change, and it is not recommended for production use. For the established staged workflow, start with `$camel-start`. See the [Ship command reference](commands.md#camel-ship) for the preview scope.

This development integration implements [#225](https://github.com/luigidemasi/camel-kit/issues/225) using the shared
[native controller handoff](ship-native.md#controller-handoff). A development build is required.

After upgrading Camel-Kit, regenerate the project's Codex CLI assets. Preserve customizations before using `--force`:

```bash
camel-kit init --here --ai codex --force
# Camel JBang plugin installation:
camel kit init --here --ai codex --force
```

In a trusted, authenticated Codex CLI session, invoke `$camel-ship` from the generated project skill. The session must
expose the project `camel_ship_worker` custom agent. The parent must already have permission for the complete requested
workflow. Plan mode supports only read-only `ship --status`; starting, resuming, submitting or aborting work requires a
session authorized to implement. Normal permissions, sandbox policies, repository trust, approval policies, other Camel
roles and MCP configuration remain in effect.

An eligible new run selects `--backend codex-native --json` unless the user explicitly chooses a backend. If the user
explicitly selects `--backend codex-native` but native dispatch is unavailable or effective child isolation cannot be
verified, the skill reports that this session cannot service the requested backend and stops without starting a run.
If no backend was chosen and native dispatch is unavailable, a new authorized run keeps the existing CLI/Pi execution
model. Existing runs retain their recorded backend: a `CODEX_NATIVE` run needs an eligible Codex CLI session to
dispatch further work. Bob, Copilot, Claude and Pi runs do not switch to Codex, and Codex runs cannot switch to
another host during recovery.

## Native task and restrictions

The parent relays the controller's complete prompt by invoking the `camel_ship_worker` custom agent. The prompt must be
copied verbatim, including on retries; its schema and oversight rules cannot be summarized. Model overrides are omitted
so the active session's settings apply. Camel-Kit does not start another Codex process or discover Pi/Node for native
stages.

The custom agent declares `sandbox_mode = "read-only"` and no `[mcp_servers]` section. However, `sandbox_mode` alone
does not guarantee effective tool restriction in all Codex CLI versions — the child may inherit parent permissions,
command execution, and MCP servers. The relay skill must verify that the effective child context enforces read-only
file access without command execution or inherited MCP tools before dispatching. If effective isolation cannot be
verified for the active Codex CLI version, native dispatch is refused. This requirement is version-specific;
future Codex CLI releases that enforce `sandbox_mode` as an effective capability boundary will satisfy the check.

The worker returns structured stage results and, for EXECUTE, complete text proposals for the approved route, Citrus
test, `pom.xml` and `.camel-kit/config.properties`. The controller validates those proposals, writes its private
candidate, computes hashes and runs deterministic checks before guarded publication. The native contract does not
accept arbitrary extra resources, binary files or deletions.

A fresh context is not an OS sandbox: its read access follows the repository trust boundary. The child is instructed to
read only controller inputs and relevant sources. Do not broaden permissions, disable sandbox, add tools or substitute
another role when a task cannot access its inputs. Linux and the existing Camel Main/YAML/Simple/Citrus contract
remain required. Host versions are caller-reported diagnostics, not release certification.

## Recovery

The shared [resume, timeout and abort contract](ship-native.md#resume-timeout-and-abort) also applies to Codex CLI. The
parent submits the observed child response through `ship --submit RUN_ID --result PATH --json`, retaining the exact
envelope until acknowledged. Identical accepted submissions are idempotent; conflicting, stale, wrong-task and late
aborted-run results are rejected. Only the controller advances stages or pauses for oversight.

After parent interruption, inspect `--status --json`. Wait for a still-running child or submit its saved response.
If its result is unavailable, do not dispatch the same pending task again. Its deadline is retained; `--resume` after
expiry fails that attempt, and another explicit resume issues a new task. Use Codex's cancellation controls to stop
host-owned children. Ship cannot terminate them; abort invalidates the run, and read-only children cannot modify the
candidate even if they outlive the parent.

Failed runs return JSON with exit code 1; show `run.message` and wait for explicit resume. Errors may return stderr
without JSON. Stop after command errors, including `stale-stage-input` or permission denial, and obtain a new user request
before resuming or changing arguments. For `handoff-read-failed`, plain status can show the recorded run; explicit resume first records failure,
then a second explicit resume creates a fresh task. Never repair evidence by hand. Repeat the original `--stage-timeout`,
`--maven-repository` and `-c`/`-p` settings on submit/resume; these settings are not persisted for subsequent stages.

Oversight pauses require an explicit user decision. A returned child result is a transport result, not proof that
validation passed: report the controller's final status, evidence and publication paths.

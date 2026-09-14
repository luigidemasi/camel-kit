# Copilot CLI native Ship

**Technology Preview:** Camel Ship is still being stabilized. Its behavior and interfaces may change, and it is not recommended for production use. For the established staged workflow, start with `/camel-start`. See the [Ship command reference](commands.md#camel-ship) for the preview scope.

This development integration implements [#226](https://github.com/luigidemasi/camel-kit/issues/226) using the shared
[native controller handoff](ship-native.md#controller-handoff). A development build is required.

Authenticated acceptance passed on **2026-09-09 with GitHub Copilot CLI 1.0.83 on Linux**. The registered skill completed
all four native stages, paused for explicit SMART approvals, passed all five mandatory checks including Camel startup
and Citrus, and published files independently compared with the requested contents and private candidate. Native
cancellation, timeout and explicit recovery, repeated submissions, stale/conflicting results, abort/late results,
plan-mode refusal and read/tool restrictions were exercised. This evidence covers that CLI version and fixture;
other hosts and versions are not certified by it.

After upgrading Camel-Kit, regenerate the project's Copilot assets. Preserve customizations before using `--force`:

```bash
camel-kit init --here --ai copilot --force
# Camel JBang plugin installation:
camel kit init --here --ai copilot --force
```

In a trusted, authenticated Copilot CLI session, invoke `/camel-ship` from the generated project skill. The session must
expose the `task` tool and the project `camel-ship-worker` custom agent. The parent must already have permission for the
complete requested workflow. Plan mode supports only read-only `ship --status`; starting, resuming, submitting or
aborting work requires a session authorized to implement. Restricted custom agents cannot use Ship to escape their scope. Normal
permissions, repository hooks, other Camel roles and MCP configuration remain in effect.

An eligible new run selects `--backend copilot-native --json` unless the user explicitly chooses a backend. If native
dispatch is unavailable, a new authorized run keeps the existing CLI/Pi execution model. Existing runs retain their
recorded backend: a `COPILOT_NATIVE` run needs an eligible Copilot session to dispatch further work. Bob and Pi runs do
not switch to Copilot, and Copilot runs cannot switch to another host during recovery.

## Native task and restrictions

The parent relays the controller's complete prompt through `task` with `agent_type: "camel-ship-worker"`, `mode: "sync"`
and a name derived from the controller task ID. Copilot starts a fresh child context; its tool has no `fork_context`
argument. The prompt must be copied verbatim, including on retries; its schema and oversight rules cannot be summarized.
Model and reasoning overrides are omitted so the active session's settings apply. Camel-Kit does not start
another Copilot process or discover Pi/Node for native stages.

The custom agent allows only Copilot's `read` and `search` tool aliases. It returns structured stage results and, for
EXECUTE, complete text proposals for the approved route, Citrus test, `pom.xml` and `.camel-kit/config.properties`.
The controller validates those proposals, writes its private candidate, computes hashes and runs deterministic checks
before guarded publication. The native contract does not accept arbitrary extra resources, binary files or deletions.

A fresh context is not an OS sandbox. Read access follows the parent's permitted paths, including any explicitly added
directories. The child is instructed to read only controller inputs and relevant sources; it has no edit, command, MCP
or delegation tools. Do not broaden permissions or substitute another role when a task cannot access its inputs.
Linux and the existing Camel Main/YAML/Simple/Citrus contract remain required. Host versions are caller-reported
diagnostics, not release certification.

Copilot documents [custom-agent tool restrictions and separate contexts](https://docs.github.com/en/copilot/concepts/agents/copilot-cli/about-custom-agents)
and [CLI permissions and task controls](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-command-reference).

## Recovery

The shared [resume, timeout and abort contract](ship-native.md#resume-timeout-and-abort) also applies to Copilot. The
parent submits the observed child response through `ship --submit RUN_ID --result PATH --json`, retaining the exact
envelope until acknowledged. Identical accepted submissions are idempotent; conflicting, stale, wrong-task and late
aborted-run results are rejected. Only the controller advances stages or pauses for oversight.

After parent interruption, inspect `--status --json`. Wait for a still-running child or submit its saved response.
If its result is unavailable, do not dispatch the same pending task again. Its deadline is retained; `--resume` after
expiry fails that attempt, and another explicit resume issues a new task. Use Copilot's cancellation controls to stop
host-owned children. Ship cannot terminate them; abort invalidates the run, and read-only children cannot modify the
candidate even if they outlive the parent.

Failed runs return JSON with exit code 1; show `run.message` and wait for explicit resume. Errors may return stderr
without JSON. Stop after command errors, including `stale-stage-input` or permission denial, and obtain a new user request
before resuming or changing arguments. For `handoff-read-failed`, plain status can show the recorded run; explicit resume first records failure,
then a second explicit resume creates a fresh task. Never repair evidence by hand. Repeat the original `--stage-timeout`,
`--maven-repository` and `-c`/`-p` settings on submit/resume; these settings are not persisted for subsequent stages.

Oversight pauses require an explicit user decision. Copilot's synchronous return is a transport result, not proof that
validation passed: report the controller's final status, evidence and publication paths.

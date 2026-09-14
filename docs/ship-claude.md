# Claude Code native Ship

**Technology Preview:** Camel Ship is still being stabilized. Its behavior and interfaces may change, and it is not recommended for production use. For the established staged workflow, start with `/camel-start`. See the [Ship command reference](commands.md#camel-ship) for the preview scope.

This development integration implements [#222](https://github.com/luigidemasi/camel-kit/issues/222) using the shared
[native controller handoff](ship-native.md#controller-handoff). A development build is required.

Authenticated acceptance passed on **2026-09-10 with Claude Code 2.1.267 on Linux**, driven headlessly (`claude -p`) in a
generated standalone workspace. The registered `/camel-ship` skill completed all four native stages through the
`camel-ship-worker` subagent, paused for explicit SMART approvals before EXECUTE and VALIDATE, passed all five mandatory
checks including Camel startup and Citrus, and published files independently compared with the requested contents and
the private candidate. Every controller prompt was forwarded byte-exact and every accepted receipt equals the child's
response; the children used only `Read`, `Glob` and `Grep` inside the project. Parent interruption after dispatch,
deadline expiry, explicit retry, repeated submissions, stale/conflicting results, fixed backend selection, abort/late
results, plan-mode refusal and the worker tool boundary were exercised. A fresh session resumed an interrupted run through the registered skill after its expired attempt was
recorded, completed the replacement DISCOVERY child, and stopped when the controller rejected a DESIGN result that
arrived after its deadline; abort then rejected further submissions. The workspace was not
trusted by Claude Code, so its generated `.claude/settings.json` allow rules were ignored and the needed rules came from
the session; the parent stopped at every permission denial instead of working around it. This evidence covers that
Claude Code version and fixture; other hosts and versions are not certified by it.

After upgrading Camel-Kit, regenerate the project's Claude Code assets. Preserve customizations before using `--force`:

```bash
camel-kit init --here --ai claude --force
# Camel JBang plugin installation:
camel kit init --here --ai claude --force
```

In a trusted, authenticated Claude Code session, invoke `/camel-ship` from the generated project skill. The session
must expose the `Agent` tool and the project `camel-ship-worker` subagent. The parent must already have permission for
the complete requested workflow. Plan mode supports only read-only `ship --status`; starting, resuming, submitting or
aborting work requires a session allowed to implement, and the skill never leaves plan mode or changes the permission
mode itself. Normal permission prompts, `.claude/settings.json` rules, hooks, other Camel roles and MCP configuration
remain in effect; the generated settings do not pre-approve the Ship command.

An eligible new run selects `--backend claude-native --json` unless the user explicitly chooses a backend. If native
dispatch is unavailable, a new authorized run keeps the existing CLI/Pi execution model. Existing runs retain their
recorded backend: a `CLAUDE_NATIVE` run needs an eligible Claude Code session to dispatch further work. Bob, Copilot and
Pi runs do not switch to Claude Code, and Claude Code runs cannot switch to another host during recovery.

## Native task and restrictions

The parent relays the controller's complete prompt through the `Agent` tool with `subagent_type: "camel-ship-worker"`
and a short stage description. Claude Code starts a fresh subagent context; the skill leaves `model` and `isolation`
unset so the child inherits the session model and reads the project directory. The prompt must be copied verbatim,
including on retries; its schema and oversight rules cannot be summarized. Camel-Kit does not start another Claude Code
process or discover Pi/Node for native stages. If the harness runs the child in the background, the parent waits for
its completion notification instead of dispatching the task again.

The subagent declares exactly `tools: Read, Grep, Glob`; Claude Code enforces that allowlist, so the child has no
edit, shell, MCP or delegation tools. It returns structured stage results and, for EXECUTE, complete text proposals for
the approved route, Citrus test, `pom.xml` and `.camel-kit/config.properties`. The controller validates those
proposals, writes its private candidate, computes hashes and runs deterministic checks before guarded publication. The
native contract does not accept arbitrary extra resources, binary files or deletions.

A fresh context is not an OS sandbox. Read access follows the parent's working directory, additional directories and
permission rules. The child is instructed to read only controller inputs and relevant sources. Do not add allow rules,
change the permission mode, add directories or substitute another subagent when a task cannot access its inputs.
Linux and the existing Camel Main/YAML/Simple/Citrus contract remain required. Host versions are caller-reported
diagnostics, not release certification.

Claude Code documents [subagent tool restrictions and fresh contexts](https://code.claude.com/docs/en/sub-agents) and
[permission modes](https://code.claude.com/docs/en/permission-modes).

## Recovery

The shared [resume, timeout and abort contract](ship-native.md#resume-timeout-and-abort) also applies to Claude Code.
The parent writes the observed child response as a temporary envelope outside the project directory and submits it
through `ship --submit RUN_ID --result PATH --json`, retaining the exact envelope until acknowledged. Identical accepted submissions are idempotent; conflicting, stale, wrong-task and late
aborted-run results are rejected. Only the controller advances stages or pauses for oversight.

After parent interruption or a resumed session, inspect `--status --json`. Wait for a still-running child or submit its
saved response. If its result is unavailable, do not dispatch the same pending task again. Its deadline is retained;
`--resume` after expiry fails that attempt, and another explicit resume issues a new task. Use the `/tasks` panel or
`TaskStop` to stop host-owned children. Ship cannot terminate them; abort invalidates the run, and read-only children
cannot modify the candidate even if they outlive the parent.

Failed runs return JSON with exit code 1; show `run.message` and wait for explicit resume. Errors may return stderr
without JSON. Stop after command errors, including `stale-stage-input` or permission denial, and obtain a new user
request before resuming or changing arguments. For `handoff-read-failed`, plain status can show the recorded run;
explicit resume first records failure, then a second explicit resume creates a fresh task. Never repair evidence by
hand. Repeat the original `--stage-timeout`, `--maven-repository` and `-c`/`-p` settings on submit/resume; these
settings are not persisted for subsequent stages.

Oversight pauses require an explicit user decision. A returned subagent result is a transport result, not proof that
validation passed: report the controller's final status, evidence and publication paths.

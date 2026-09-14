# Bob 2 native Ship

**Technology Preview:** Camel Ship is still being stabilized. Its behavior and interfaces may change, and it is not recommended for production use. For the established staged workflow, start with `/camel-start`. See the [Ship command reference](commands.md#camel-ship) for the preview scope.

Development status: authenticated acceptance on 2026-09-09 exercised Bob Shell 2.0.2 on Linux through the registered
Ship skill: four native stages, oversight pauses and explicit resume, deterministic validation and publication.
Interruption, timeout recovery, abort, repeated submissions and rejection of stale/conflicting results were also checked.
This integration is tracked in [#223](https://github.com/luigidemasi/camel-kit/issues/223) and requires a development build.
Bob IDE and other host versions have not undergone the same live acceptance test.

In an eligible Bob 2 session, the generated Ship skill uses Bob's `spawn_subagent` tool. The active Bob session keeps its
own authentication and model. Camel-Kit does not launch a second Bob process, Pi, or Node for these stages.

Regenerate the project's Bob assets after upgrading:

```bash
camel-kit init --here --ai bob2 --force
# Camel JBang plugin installation:
camel kit init --here --ai bob2 --force
```

Preserve any customizations before using `--force`. In Bob Shell, invoke the native skill through `/skills` or
`$camel-ship`; in Bob IDE, use `/camel-ship`. Use the normal agent mode or select the generated Camel Ship mode.
The active mode must allow the complete requested workflow and the `camel-ship-worker` preset. Existing restricted
phase modes retain their permissions; the skill cannot switch modes or broaden a worker to bypass them.

The skill selects `--backend bob2-native --json` for a new eligible run. An explicit backend option takes precedence.
If subagents are disabled or unavailable, new runs retain the existing CLI execution model when that workflow is
authorized. A persisted run keeps its execution mode for its entire lifetime. Legacy runs remain Pi runs.

## Controller handoff

The CLI creates one pending task and returns to the parent. The task records its run, stage, attempt, input digest,
deadline, working directory and a unique task ID. The parent dispatches a fresh `camel-ship-worker` with
`fork_context: false`, then submits the observed result through `ship --submit RUN_ID --result PATH --json`.
Only the controller chooses the next stage or a pause. Native results do not decide whether validation passed.
Run state binds the exact task and accepted result bytes by digest, so recovery rejects modified prompts, deadlines
or result metadata. The task ID binds a response; Bob's internal child ID is not visible to the parent model.

`camel-ship-worker` has only Bob's read tools. It cannot edit, execute commands, call MCP, or create children. During
EXECUTE it returns complete text proposals for the approved route and Citrus test paths, `pom.xml`,
`.camel-kit/config.properties`. The controller rejects other paths, applies
accepted proposals in its private candidate, computes the manifest and hashes, and runs the existing mandatory checks.
This initial native contract does not support arbitrary extra resources, binary files or deletion proposals.

Linux and the existing Camel Main/YAML/Simple/Citrus artifact policy still apply. Validation may resolve dependencies
from Maven Central. Native host versions are recorded as caller-reported diagnostics, without an exact-version
certification claim. Interactive Bob authentication is sufficient for normal use; automated `bob run` tests require
Bob's own API-key authentication.

## Resume, timeout and abort

Use `--status --json` to inspect a run. Resume and result submission automatically use the persisted backend; a different
`--backend` is rejected. Repeating an identical accepted submission is safe; a conflicting result or a result for another
task or attempt is rejected. Repeat the original `--stage-timeout`, `--maven-repository` and `-c`/`-p` settings on every
resume or submission. These settings apply to subsequent work and are not persisted; an issued task keeps its deadline.
Status and abort do not accept runtime/config options.

With `--json`, a failed workflow returns run state and exit code 1. Read valid JSON on exit code 0 or 1 and show
`run.message` when `run.status` is `FAILED`; do not dispatch a child for that run. Some command errors return only
stderr, so exit code 1 does not guarantee JSON. Report those errors without dispatching work.

If `--status --json` reports `handoff-read-failed`, the pending task could not be verified. Plain `--status` can still
show the recorded run. To recover, use `--resume --json` to mark the damaged attempt failed, inspect its failure
message, then explicitly resume again to create a fresh task. Do not edit stored evidence or bypass integrity checks.

An interrupted parent can submit a saved result or wait for its existing native call. Do not dispatch the same pending
task again after losing the parent transcript. Pending work keeps its original deadline across reconnections; after
that deadline, `--resume` fails the attempt. Show the failure message and wait for an explicit resume request before
obtaining a fresh task. The old task's result is rejected.

Bob handles cancellation of its native call. The Ship CLI cannot terminate a host-owned child; `--abort` invalidates
the run and rejects later submissions. Because children are read-only, an orphan cannot write into the candidate.
Oversight questions stay with the parent, and paused runs continue only through an explicit resume.

Track implementation and host validation in [core #223](https://github.com/luigidemasi/camel-kit/issues/223), under
[shared native handoff #221](https://github.com/luigidemasi/camel-kit/issues/221). Other agent adapters and Pi
compatibility retain their separate tasks.

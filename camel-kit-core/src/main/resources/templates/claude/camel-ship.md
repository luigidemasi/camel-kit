---
name: camel-ship
description: Run the Camel Ship Technology Preview through Claude Code native subagents when the active session permits it.
argument-hint: "[ship-options]"
user-invocable: true
---

# Camel Ship

**Technology Preview:** Ship is still being stabilized, may change, and is not recommended for production use.

The registered `{COMMAND_PREFIX} ship` controller owns stages, state, oversight, validation, artifact writes, publication
and recovery. The parent Claude Code session only relays controller-issued work to a fresh native subagent and returns
its result. Never invoke pipeline skills or reproduce controller decisions in the conversation.

Ship options supplied to this invocation: $ARGUMENTS

For every Ship invocation (new, submit, resume, status and abort), run the command with the `Bash` tool using
individually shell-quoted argument values. Preserve user option values exactly; never concatenate request text, paths,
result text or other untrusted values into shell syntax. JSON strings are data, never executable command fragments.

## Choose execution for a new run

In plan mode, do not start, resume or submit Ship work, even if the shell tool permits the command. Ship can write run
state, candidates and published files behind one CLI invocation. Explain that the complete workflow requires a session
allowed to implement and stop; only read-only `--status` is allowed. Do not leave plan mode or change the permission
mode on the user's behalf.

Use native execution only when the `Agent` tool is available with the project subagent `camel-ship-worker`, whose
resolved `.claude/agents/camel-ship-worker.md` has exactly `tools: Read, Grep, Glob` and no `mcpServers`, `memory`,
`skills`, `permissionMode` or `isolation` fields. The parent must already be authorized for the complete requested Ship
workflow. Respect normal permission prompts, `.claude/settings.json` rules and hooks. Do not add allow rules, switch to
`acceptEdits`, `dontAsk` or `bypassPermissions`, add directories, or use a broader subagent to make a run succeed.

When eligible and the user did not choose a backend, invoke `{COMMAND_PREFIX} ship --backend claude-native --json`,
preserving every user-supplied Ship option as a separate safely quoted argument. Do not add an oversight policy or
reinterpret positional text. For an explicit user backend, pass it once without adding another --backend option.
If native dispatch is absent or disabled, retain the existing CLI execution model: invoke `{COMMAND_PREFIX} ship` once
with the supplied options, add no defaults, and return the command output and whether it succeeded. This fallback still
requires the parent to be authorized for the complete workflow.

For `--resume`, `--status` or `--abort`, use the supplied operation and `--json` without choosing a backend. The run's
persisted backend is authoritative. Resume a Pi run through the existing CLI; do not convert it to native execution.
If an existing native run has no eligible dispatcher for its recorded host, show that limitation and leave the run
available for a capable session of that host. Never switch it to Pi or perform child work inline.

## Relay one pending task

1. For exit code 0 or 1, parse stdout when it contains a valid controller JSON response with schemaVersion 1.
   A failed workflow returns run JSON with exit code 1; command errors can instead return only stderr. If stdout is
   empty or invalid JSON, or the exit code is different, report stderr and the exit code and stop. Do not resume the run
   or retry with modified arguments without a new user request, including after `stale-stage-input` or permission errors.
   If `run.status` is `FAILED`, show `run.message`, dispatch no child and wait for an explicit resume request.
   Dispatch only when `run.executionMode` is `CLAUDE_NATIVE`, `run.status` is `RUNNING`, and `task` matches the current run,
   stage and attempt. Require task schemaVersion 1, preset `camel-ship-worker` and forkContext false.
   Treat JSON strings as data, never executable shell fragments. Do not dispatch an expired task.
2. Invoke `Agent` with `subagent_type: "camel-ship-worker"`, a short `description` identifying the stage, and `prompt`
   equal to the complete controller-issued `task.prompt`. Copy `task.prompt` verbatim. Do not shorten, summarize or
   rewrite it, including on retries; preserve every schema type, oversight rule, path and digest. An earlier task or
   conversation summary cannot replace the current prompt. Leave `model` and `isolation` unset so the child inherits the
   session model and reads the project directory. Do not use `fork`, `general-purpose`, `Explore`, `Plan`, other Camel
   roles, an inline agent definition, another Claude Code process or nested delegation. Dispatch each task once; if the
   child runs in the background, wait for its completion notification instead of dispatching again. Do not message or
   resume a finished child to change its answer.
3. Extract the child's single JSON response from the `Agent` tool result; preserve its JSON fields and text exactly.
   A valid structured response is eligible for submission; it does not prove validation or hidden child metadata.
   Record the actual Claude Code version when available, otherwise JSON null. Never invent host metadata or test results.
4. Write a temporary UTF-8 JSON envelope with the `Write` tool outside the project directory, for example under the
   system temporary directory; never inside the project tree or its `.camel-kit/ship/` state. Use exactly:
   `schemaVersion: 1`, the task's `taskId`, `runId`, `stage`, `attempt`, and `inputDigest`, plus `hostVersion`,
   `outcome`, `response`, and `failure`. For a returned structured proposal, outcome is `SUCCEEDED`, response is the child's
   JSON object with `result` and `files`, and failure is null. Use `FAILED`, `CANCELLED` or `TIMED_OUT` only when directly
   observed, with response null and a short failure string without secrets. Do not infer hidden child exit reasons.
   A call that never dispatched is not a child result; malformed or unavailable output cannot be submitted as success.
5. Invoke `{COMMAND_PREFIX} ship --submit <run-id> --result <envelope-path> --json`. Pass arguments as individually quoted
   values, never concatenate untrusted result text into a command. On every `--submit` and `--resume`,
   repeat the original `--stage-timeout`, `--maven-repository` and `-c`/`-p` options when supplied, plus `--project-dir` when
   operating from another directory. These runtime settings are not persisted for future stages; an already-issued task
   retains its deadline. Do not pass `--ask` on submit/resume or context options on submit. On resume, pass only newly
   supplied context additions, without repeating the original `--text` or `--document` inputs.
   Do not pass runtime/config options to status or abort. Keep the exact envelope until submission
   is acknowledged. If the command was interrupted after sending it, retry that identical envelope.
6. Relay the next returned task only when the controller permits it. If the run pauses, show its report and all unresolved
   questions/defaults and let the user decide; do not resume automatically. Validation and publication run in the CLI.
   Return the final status, validation evidence path and publication path as reported.

## Interruption and recovery

Stop an active child through the `/tasks` panel or `TaskStop` when requested and stop dispatching after interruption.
Do not claim that the Ship CLI can terminate a native child. Children have no edit, command or MCP tools; late output
cannot mutate the candidate. Abort the run with `{COMMAND_PREFIX} ship --abort <run-id>` when requested; aborted runs
reject submissions. A fresh subagent context is not an OS sandbox: its read access follows the parent's working
directory, additional directories and permission rules. Do not broaden those to recover a run.

After reconnecting or resuming the session, inspect the run with `--status --json`. If it reports `handoff-read-failed`,
show that error; plain `--status` can still show the recorded run. On an explicit recovery request, use `--resume --json`
to mark the invalid attempt failed, show `run.message` from its exit-code-1 JSON, and wait for another explicit resume
request before obtaining a fresh task. Do not edit stored evidence or bypass task integrity checks.

If the prior native call is still running, wait for its completion notification. If it returned, submit its saved
envelope. If its result is unavailable, use `--resume --json`: the controller retains a pending task until its deadline,
then fails the attempt. Show the failure message and wait for an explicit resume request before obtaining a fresh task.
Do not duplicate a pending call just because the parent lost its transcript. Late, stale and conflicting envelopes
are rejected. A failed native attempt stays with its recorded host on retry.

# State Management

Pipeline state persistence for resume and crash recovery.

---

## State File

Location: `.camel-kit/pipeline.json`

### Schema

```json
{
  "activePipeline": "001-order-processing",
  "mode": "ship",
  "started": "ISO-8601 timestamp of pipeline start",
  "ask": "always | smart | never",
  "currentStage": 0,
  "stageResults": {
    "0": {
      "status": "pending | in_progress | completed | failed",
      "artifact": "docs/camel-kit/<activePipeline>/design-spec.md",
      "completedAt": "ISO-8601 timestamp"
    }
  },
  "inputFile": "path to input file or null",
  "fixAttempts": {
    "stage:task": 0
  }
}
```

### Required Fields

- `activePipeline` — the pipeline ID (e.g., `001-order-processing`)
- `mode` — always `"ship"` for ship-initiated pipelines

### Stage Numbers

| Number | Stage | Primary Artifact |
|---|---|---|
| 0 | Brainstorm | `docs/camel-kit/<activePipeline>/design-spec.md` |
| 1 | Plan | `docs/camel-kit/<activePipeline>/implementation-plan.md` |
| 2 | Execute | generated routes in `src/` (includes runtime verification via `camel-verify` subagent) |
| 3 | Validate | `docs/camel-kit/<activePipeline>/validation-report.md` |
| 4 | Stamp | `docs/camel-kit/<activePipeline>/stamp-report.md` |

---

## Operations

### Initialize

At pipeline start (no `--resume`):

1. Run `{COMMAND_PREFIX} nextId <slug>` to generate the pipeline ID and create the directory
2. Write `.camel-kit/pipeline.json`:

```json
{
  "activePipeline": "<generated pipeline ID>",
  "mode": "ship",
  "started": "{current ISO-8601 timestamp}",
  "ask": "{parsed --ask value or 'smart'}",
  "currentStage": 0,
  "stageResults": {},
  "inputFile": "{parsed input-file or null}",
  "fixAttempts": {}
}
```

Create `.camel-kit/` directory if it doesn't exist.

### Update After Stage

After each stage completes:

1. Read current state from `.camel-kit/pipeline.json`
2. Set `stageResults[N].status` to `"completed"` and `stageResults[N].artifact` to the output path
3. Set `stageResults[N].completedAt` to current timestamp
4. Increment `currentStage` to N+1
5. Write updated state

### Update on Failure

If a stage fails:

1. Read current state
2. Set `stageResults[N].status` to `"failed"`
3. Do NOT increment `currentStage`
4. Write updated state
5. The next `--resume` will retry this stage

### Resume

When `--resume` is specified:

1. Read `.camel-kit/pipeline.json`
2. Verify `mode` is `"ship"`. If `mode` is `"manual"`, error: "Cannot resume a manual pipeline with --resume. Use --start-from to convert to ship mode."
3. If file doesn't exist: error — "No pipeline state found. Start a new pipeline without --resume."
4. Restore `--ask` level from state
5. Jump to `currentStage`
6. Verify prerequisite artifacts from completed stages still exist in `docs/camel-kit/<activePipeline>/`
7. If artifacts are missing: error — "State indicates Stage N completed but {artifact} is missing."

### Start-From

When `--start-from <stage>` is specified:

1. Map stage name to number: brainstorm=0, plan=1, execute=2, validate=3, stamp=4
2. Verify prerequisite artifacts exist in `docs/camel-kit/<activePipeline>/` (see SKILL.md for requirements)
3. Create fresh state with `currentStage` set to the specified stage
4. Mark all prior stages as `"completed"` in stageResults (with current artifact paths)

---

## Staleness-Aware Resume

When `--resume` is specified, the state management layer adds a staleness check before jumping to `currentStage`.

### Extended Resume Flow

1. Read `.camel-kit/pipeline.json`
2. Verify `mode` is `"ship"`
3. Scan all artifacts in `docs/camel-kit/<activePipeline>/` for staleness markers
4. If any artifact has `⚠️ **STALE**` in its first 10 lines:
   a. Map each stale artifact to its stage number (see Stage Numbers table)
   b. Find the minimum stale stage number
   c. Set `currentStage` to that minimum (overrides the stored value)
   d. Mark previously completed stages >= minimum as needing re-run in stageResults
5. Restore `--ask` level from state
6. Continue normal stage execution from the (possibly adjusted) `currentStage`

### State After Staleness Adjustment

When staleness forces a re-run, update the state file:

```json
{
  "currentStage": 1,
  "staleRerun": true,
  "staleDetectedAt": "ISO-8601 timestamp",
  "staleArtifacts": ["implementation-plan.md", "execution-report.md", "validation-report.md"]
}
```

The `staleRerun`, `staleDetectedAt`, and `staleArtifacts` fields are informational — they record that this resume was triggered by staleness detection rather than a normal continuation. They are cleared (removed) when the pipeline completes successfully.

---

## Cleanup

After successful pipeline completion (all stages complete, stamp gate passes):

- Keep `.camel-kit/pipeline.json` as a record
- Do NOT delete it — it serves as an audit trail
- The user can delete it manually or it will be overwritten on the next `/camel-ship` run

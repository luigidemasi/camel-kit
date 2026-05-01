# State Management

Pipeline state persistence for resume and crash recovery.

---

## State File

Location: `.camel-kit/ship-state.json`

### Schema

```json
{
  "started": "ISO-8601 timestamp of pipeline start",
  "ask": "always | smart | never",
  "currentStage": 0,
  "stageResults": {
    "0": {
      "status": "pending | in_progress | completed | failed",
      "artifact": "path to primary output artifact",
      "completedAt": "ISO-8601 timestamp"
    }
  },
  "inputFile": "path to input file or null",
  "fixAttempts": {
    "stage:task": 0
  }
}
```

### Stage Numbers

| Number | Stage | Primary Artifact |
|---|---|---|
| 0 | Brainstorm | `docs/design-spec.md` |
| 1 | Plan | `docs/implementation-plan.md` |
| 2 | Execute | generated routes in `src/` |
| 3 | Verify | `docs/verification-report.md` |
| 4 | Stamp | `docs/stamp-report.md` |

---

## Operations

### Initialize

At pipeline start (no `--resume`):

```json
{
  "started": "{current ISO-8601 timestamp}",
  "ask": "{parsed --ask value or 'smart'}",
  "currentStage": 0,
  "stageResults": {},
  "inputFile": "{parsed input-file or null}",
  "fixAttempts": {}
}
```

Write to `.camel-kit/ship-state.json`. Create `.camel-kit/` directory if it doesn't exist.

### Update After Stage

After each stage completes:

1. Read current state
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

1. Read `.camel-kit/ship-state.json`
2. If file doesn't exist: error — "No pipeline state found. Start a new pipeline without --resume."
3. Restore `--ask` level from state
4. Jump to `currentStage`
5. Verify prerequisite artifacts from completed stages still exist
6. If artifacts are missing: error — "State indicates Stage N completed but {artifact} is missing."

### Start-From

When `--start-from <stage>` is specified:

1. Map stage name to number: brainstorm=0, plan=1, execute=2, verify=3, stamp=4
2. Verify prerequisite artifacts exist (see SKILL.md for requirements)
3. Create fresh state with `currentStage` set to the specified stage
4. Mark all prior stages as `"completed"` in stageResults (with current artifact paths)

---

## Cleanup

After successful pipeline completion (all stages complete, stamp gate passes):

- Keep `.camel-kit/ship-state.json` as a record
- Do NOT delete it — it serves as an audit trail
- The user can delete it manually or it will be overwritten on the next `/camel-ship` run

## Agent Optimization: IBM Bob — Subagent Plan Generation

### Dispatch Plan Generation to a Fresh Subagent

Plan documents are large (200+ lines) and the brainstorm phase has already consumed
significant context. To avoid truncated writes, dispatch plan generation to a
subagent with a fresh 200K context.

**Workflow:**

1. **Verify design spec exists** (do this in YOUR context — lightweight):
   - Check that the approved design spec file exists on disk
   - Read only the section headings to confirm it covers the expected scope
   - Do NOT read the full spec into your context — the subagent will read it

2. **Dispatch plan generation** via `dispatchSubagent`:
   ```
   dispatchSubagent(
     task: "You are a planning agent. Read the design spec at docs/<spec-file>.md
            and the task decomposition guide at <skill-path>/guides/task-decomposition.md.
            Follow the planning workflow:
            1. Scope check — if the spec covers multiple independent subsystems, note this
            2. Load the appropriate task template (greenfield, migration, or testing)
            3. Decompose the spec into bite-sized tasks following the template structure
            4. Each task must specify: files to create/modify, guides to load, MCP tools to call,
               verification commands with expected output, and two-stage review spec
            5. Write the complete plan to docs/implementation-plan.md
            6. Self-review: check for spec coverage gaps, placeholders, type consistency
            The plan contains instructions on HOW to generate code, NOT the code itself.
            Do NOT generate any implementation artifacts (YAML routes, properties, POM changes).",
     mode: "plan",
     approvalMode: "auto_edit",
     filesContext: ["docs/<spec-file>.md",
                    "<skill-path>/guides/task-decomposition.md",
                    "<skill-path>/guides/task-template-greenfield.md"]
   )
   ```

3. **Verify plan was written** (do this in YOUR context):
   - Check that `docs/implementation-plan.md` exists
   - Read the first 20 lines to confirm the header and task count
   - If the dispatch failed or the plan is truncated, re-dispatch with a more focused prompt

4. **Plan approval gate** — present the plan summary to the user for approval

5. **Auto-proceed to execute** — after approval, transition to camel-execute phase

### Why Dispatch Instead of Inline

- The brainstorm phase consumes 30-60K tokens of context (interview, discovery, design approval)
- Plan generation adds another 20-40K tokens (reading spec, decomposing, writing plan)
- By dispatching, the subagent starts fresh with only ~5K tokens of input (spec + templates)
- The parent context stays clean for orchestration and the execute phase that follows

### Permission Scoping

| Phase | approvalMode | Rationale |
|---|---|---|
| Plan generation | `auto_edit` | Writes markdown plan file only |
| Plan verification | done inline | Parent reads plan header, no dispatch needed |

### Fallback

If `dispatchSubagent` is unavailable (MCP server not running), fall back to inline
plan generation. Use `insert_content` to write the plan incrementally — header first,
then each task as a separate insert — to avoid truncation from large `write_to_file` calls.

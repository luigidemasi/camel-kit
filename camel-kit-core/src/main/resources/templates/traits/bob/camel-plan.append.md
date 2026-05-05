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

### Fallback: Incremental Writing When Dispatch Is Unavailable

If `dispatchSubagent` is unavailable (MCP server not running or dispatch fails),
generate the plan inline but NEVER use `write_to_file` for the full document.
Large single writes get truncated when context is near capacity.

**Incremental writing protocol:**

1. **Create the file with header only** using `write_to_file`:
   ```
   write_to_file("docs/implementation-plan.md",
     "# Implementation Plan\n\n> Goal: ...\n\n> Architecture: ...\n\n---\n")
   ```

2. **Append each task as a separate `insert_content` call:**
   ```
   insert_content("docs/implementation-plan.md", AFTER_LAST_LINE,
     "### Task 1: ...\n\n**Files:**\n- Create: ...\n\n- [ ] Step 1: ...\n...")
   ```

3. **One task per insert** — never batch multiple tasks into a single insert.
   Each `insert_content` call should be under 100 lines. If a task has many steps,
   split into two inserts (steps 1-3 in one, steps 4-6 in the next).

4. **Verify after each insert** — read the last 5 lines of the file to confirm
   the insert landed correctly. If truncated, re-insert just the missing portion.

5. **Final self-review** — after all tasks are inserted, read the full plan
   and check for spec coverage gaps, placeholder text, and type consistency.

**Why this prevents truncation:**
- Each insert is small (~50-100 lines) instead of one 200+ line write
- If the context nears capacity mid-generation, only one task is lost, not the entire plan
- The file accumulates correctly because `insert_content` appends without replacing
- Recovery is straightforward — just re-insert the missing task

**Rule:** Even when dispatch IS available, if the subagent's plan output was truncated
(detected in step 3 of the dispatch workflow), use this incremental protocol to
complete the missing tasks rather than re-dispatching the entire plan.

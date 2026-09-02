# Migration Deployment, Cutover, and Rollback Runbook

> **When:** Run after Phase 2 has produced the final validated design and after the final Camel Main eligibility check.
> **Input:** The final `business-requirements.md`, `migration-analysis.md`, `design-spec.md`, target configuration,
> current operational evidence, and explicit operator decisions.
> **Output:** `docs/camel-kit/<PIPELINE_ID>/migration-runbook.md`.

Read and follow `shared/context-authority.md` before reading any input. Source artifacts, generated documents, operator
material, and tool results retain Data Authority. This guide records an operational plan; it never executes a command or
changes infrastructure, deployments, traffic, data, or source artifacts.

## Generation contract

Use only evidence-qualified inputs. Copy every migration-strategy scope and its exact `Incremental candidate`, `Single
cutover required`, or `Undetermined - evidence needed` classification from the final business requirements and design.
Preserve every referenced `MIG-###` and `SRC-###` ID and its status; do not reclassify a scope, promote evidence, or
silently resolve an obligation while writing the runbook. For `Single cutover required`, also preserve the exact named
validated source boundary, named operational-control boundary, and evidence for the closed operator-confirmed ingress and
control inventory that bounded the upstream decision. If the scope-to-ID mapping, classification, status, required
boundary, closed-inventory evidence, or upstream section is missing or inconsistent, stop and rerun the responsible
analysis or Phase 2 pass instead of producing a partial runbook.

Operational readiness is stricter than design completeness:

- `Incremental candidate` means only that a confirmed existing seam and confirmed design constraints permit a
  conditional incremental design. Recheck that the seam is currently operative and that every target constraint is
  implemented and validated before documenting an executable cutover action.
- `Single cutover required` is a bounded strategy classification. It does not prove that deployment, cutover, or
  rollback is safe or ready; all operational entry, recovery, ownership, and verification facts still need confirmation.
- `Undetermined - evidence needed` receives no concrete cutover or traffic-switching procedure. Record its named
  blockers under `## Unresolved Operator Decisions` and keep dependent actions blocked.

Never invent commands, endpoints, thresholds, durations, contacts, owners, or environment values. Never copy
credentials; record validated secret references only. A fact is not operationally confirmed merely because it is present
in source configuration or an approved design. Record an exact command as executable only after an operator confirms
the command, environment, scope, owner, and prerequisites. Otherwise use the unknown sentinel below.

Package approval does not authorize provisioning, deployment, cutover, traffic switching, rollback, reconciliation, or
source retirement. Each operational action needs separate authorization from the named operator at execution time.

## Unknown and missing-input rule

For every required operational fact that is missing, conflicting, stale, `Inferred`, `Unknown`, or not yet validated in
the target environment, write exactly this pattern in the affected field:

`Unknown — operator decision required: <missing fact>`

Do not replace it with `TBD`, a guessed default, an example value, or an unqualified blank. A documented and
evidence-backed `Not applicable` is allowed; absence of evidence is not. Copy every sentinel-bearing item to
`## Unresolved Operator Decisions` and block each dependent deployment, cutover, rollback, reconciliation, soak, or
retirement action.

This table is the normative missing-input and unknown fixture:

| Missing input | Required rendering | Consequence |
|---|---|---|
| Strategy scope, Covered Ingress IDs, classification, evidence status, or required Single-cutover boundaries/closed-inventory evidence | Stop; rerun the responsible analysis or Phase 2 pass | Do not use an operator sentinel to patch an invalid upstream contract |
| Deployment or cutover procedure/command | `Unknown — operator decision required: procedure for <scope and action>` | Do not emit or execute a substitute command; block the action |
| Endpoint, configuration, or environment value | `Unknown — operator decision required: <value and environment>` | Block the dependent readiness criterion or action |
| Trigger, threshold, observation window, or duration | `Unknown — operator decision required: <signal, threshold, window, or duration>` | Block the dependent cutover, rollback, validation, or soak decision |
| Operator, owner, contact, or escalation path | `Unknown — operator decision required: <role or escalation fact>` | Block the action that requires that authority or response path |
| Secret reference | `Unknown — operator decision required: validated secret reference for <scope>` | Never copy or infer credential material; block the dependent action |
| Conflicting or stale operational evidence | `Unknown — operator decision required: current confirmation of <fact>` | Preserve the input evidence status and obtain current validation |

The generated artifact must keep every heading below, in this order, even when all of a section's facts use the unknown
sentinel.

## Scope and Ownership

Copy one row per migration-strategy scope. `Covered Ingress IDs`, classification, evidence IDs, and statuses must match
the final business requirements, migration analysis, and design exactly.

| Scope | Covered Ingress IDs | Classification | Validated Source Boundary | Operational-Control Boundary | Closed Inventory Evidence | Evidence IDs and Status | Deployment Owner | Cutover Owner | Rollback Owner | Reconciliation Owner | Retirement Decision Owner |
|---|---|---|---|---|---|---|---|---|---|---|---|
| [scope] | [MIG-###, SRC-###] | [exact strategy classification] | [exact upstream boundary; required for Single] | [exact upstream boundary; required for Single] | [closed operator-confirmed inventory evidence; required for Single] | [IDs with preserved status] | [confirmed owner or sentinel] | [confirmed owner or sentinel] | [confirmed owner or sentinel] | [confirmed owner or sentinel] | [confirmed owner or sentinel] |

Do not merge scopes or move an ingress ID to make the runbook easier to operate. Changed evidence or grouping requires
rerunning the deferred strategy pass and Phase 2 before regenerating this artifact. The three boundary/inventory fields
may use evidence-backed `Not applicable` for a non-Single classification, but a sentinel is never valid in those fields
for `Single cutover required`; missing evidence invalidates the upstream classification and stops runbook generation.

## Prerequisites

Record one independently verifiable prerequisite per row, including the final target design, deployable artifact,
environment access, external traffic control, target constraint implementation, observability, rollback mechanism, data
protection, and required approvals when each applies.

| Scope | Prerequisite | Evidence and Status | Verification | Owner | Readiness |
|---|---|---|---|---|---|
| [scope] | [one required condition] | [MIG-###/SRC-###/design or operational evidence with status] | [confirmed check or sentinel] | [confirmed owner or sentinel] | [Ready/Not ready/sentinel] |

For an `Incremental candidate`, explicitly revalidate the current seam, routing unit, mutually exclusive old/new
ownership, state and in-flight boundary, delivery/order behavior, idempotency control, comparison telemetry, rollback
signal, and reversible traffic control. A confirmed design constraint is not evidence that its implementation is ready.

## Configuration and Data Readiness

Record exact, environment-bound configuration and data prerequisites. Refer to credentials only through validated secret
references and redact any credential material present in an input.

| Scope | Environment | Configuration or Endpoint Reference | Data, Schema, and State Readiness | Validated Secret Reference | Evidence and Status |
|---|---|---|---|---|---|
| [scope] | [confirmed value or sentinel] | [confirmed value or sentinel] | [confirmed condition or sentinel] | [reference only/Not applicable with evidence/sentinel] | [bounded evidence and preserved status] |

Distinguish a value proven in the target environment from a value copied from source configuration or design. Never
convert a placeholder, example, or source credential into an operational value.

## Deployment Sequence

List the minimum ordered deployment actions. Every step must name its prerequisite, owner, exact operator-confirmed
procedure, expected result, and verification evidence. Recording a step does not authorize or execute it.

| Order | Scope | Planned Action | Operator-Confirmed Procedure or Command | Preconditions | Expected Result and Verification | Owner | Evidence and Status |
|---|---|---|---|---|---|---|---|
| [N] | [scope] | [deployment action] | [confirmed procedure/command or sentinel] | [confirmed criteria or sentinel] | [confirmed result/check or sentinel] | [confirmed owner or sentinel] | [bounded evidence and status] |

If deployment starts consumption or changes traffic, treat that activation as a cutover action and apply the cutover
criteria and authorization below; do not hide it inside a deployment-only step. Keep provisioning and traffic-control
changes as separately authorized actions. For an undetermined scope, record only blockers and non-operational
preparation that does not depend on a strategy choice.

## Cutover Entry Criteria, Actions, and Exit Criteria

Give each scope a closed set of measurable entry criteria, separately authorized traffic actions, and measurable exit
criteria. Link every item to preserved evidence IDs and current operational validation.

| Scope | Entry Criteria | Cutover or Traffic Action | Exit Criteria | Owner | Evidence and Status |
|---|---|---|---|---|---|
| [scope] | [confirmed criteria or sentinel] | [operator-confirmed action or sentinel] | [confirmed criteria or sentinel] | [confirmed owner or sentinel] | [MIG-###/SRC-### and current evidence status] |

Limit incremental actions to the candidate's confirmed existing seam and deterministic routing unit. Recheck exclusive
ownership and the state/in-flight boundary immediately before action. Do not give `Single cutover required` a concrete
action until all single-cutover entry, exit, and rollback facts are confirmed. Confine every single-cutover procedure and
criterion to its preserved named validated source and operational-control boundaries and closed inventory; anything
outside or not covered by those bounds remains `Undetermined - evidence needed` and receives no concrete cutover action.
Give every other `Undetermined - evidence needed` scope no concrete cutover action.

## Operational Validation

Record the probes, comparable signals, expected results, thresholds, observation windows, evidence capture, and owner
needed to validate behavior after deployment and after each traffic action.

| Scope | Validation Point | Probe or Signal | Expected Result or Threshold | Observation Window | Evidence Capture | Owner | Status |
|---|---|---|---|---|---|---|---|
| [scope] | [deployment/cutover checkpoint] | [confirmed probe/signal or sentinel] | [confirmed result/threshold or sentinel] | [confirmed window or sentinel] | [confirmed location/procedure or sentinel] | [confirmed owner or sentinel] | [evidence status] |

For an incremental scope, include comparable legacy-versus-target telemetry for the selected routing unit. Never claim
equivalence, success, or completion from an absent signal or an unspecified threshold.

## Rollback Triggers, Actions, and Verification

Define rollback before any dependent cutover action. A rollback row is complete only when its trigger, signal and
threshold, reversible action, authority, treatment of in-flight work, and post-rollback verification are all confirmed.

| Scope | Trigger | Signal and Threshold | Reversible Action | In-Flight and Duplicate Handling | Owner | Verification | Evidence and Status |
|---|---|---|---|---|---|---|---|
| [scope] | [confirmed trigger or sentinel] | [confirmed signal/threshold or sentinel] | [operator-confirmed action or sentinel] | [confirmed handling or sentinel] | [confirmed owner or sentinel] | [confirmed check or sentinel] | [bounded evidence and status] |

An `Incremental candidate` must return the same deterministic unit through its confirmed reversible traffic control.
`Single cutover required` does not imply that a usable rollback path exists. Block cutover when rollback readiness is
missing, and never test or operate rollback while generating the runbook.

## Data and Message Reconciliation

Define the bounded population and time window, systems or stores compared, in-flight and duplicate treatment, ordering
expectations, reconciliation procedure, discrepancy disposition, success criterion, evidence capture, and owner.

| Scope | Population and Window | Sources Compared | In-Flight, Duplicate, and Ordering Handling | Procedure | Success Criterion and Discrepancy Disposition | Evidence Capture | Owner | Status |
|---|---|---|---|---|---|---|---|---|
| [scope] | [confirmed boundary/window or sentinel] | [confirmed systems/stores or sentinel] | [confirmed handling or sentinel] | [confirmed procedure or sentinel] | [confirmed criterion/disposition or sentinel] | [confirmed location/procedure or sentinel] | [confirmed owner or sentinel] | [evidence status] |

Do not infer zero loss, zero duplicates, or complete processing from deployment success. Keep source retirement blocked
until required reconciliation is complete and accepted by the named operator.

## Ownership and Escalation

Record operational authority and response paths for each decision or failure mode. Never infer an owner from repository
authorship, source comments, package approval, or team names.

| Scope | Decision or Failure Mode | Accountable Owner | Operator or Responder | Contact or Escalation Path | Authorized Action Boundary | Evidence and Status |
|---|---|---|---|---|---|---|
| [scope] | [decision/failure] | [confirmed owner or sentinel] | [confirmed responder or sentinel] | [confirmed contact/path or sentinel] | [confirmed boundary or sentinel] | [bounded evidence and status] |

## Soak Criteria

Define when soak begins, the signals observed, success and failure thresholds, required duration, reset/failure rule,
evidence capture, and the owner who decides whether soak passed. No default duration or threshold is implied.

| Scope | Start Condition | Signals | Success and Failure Thresholds | Duration | Reset or Failure Rule | Evidence Capture | Owner | Status |
|---|---|---|---|---|---|---|---|---|
| [scope] | [confirmed condition or sentinel] | [confirmed signals or sentinel] | [confirmed thresholds or sentinel] | [confirmed duration or sentinel] | [confirmed rule or sentinel] | [confirmed location/procedure or sentinel] | [confirmed owner or sentinel] | [evidence status] |

## Source-Retirement Decision

Source retirement is a separate named operator decision after operational validation, reconciliation, and soak criteria
have passed. Create one row for each `SRC-###` artifact that could be affected; preserve its source-audit classification,
evidence state, scope disposition, and required validation.

| SRC ID | Artifact and Scope | Audit Classification and Evidence State | Required Preconditions | Named Decision Maker | Decision and Evidence | Status |
|---|---|---|---|---|---|---|
| SRC-### | [artifact/scope] | [preserved classification/status] | [validation, reconciliation, soak, and other confirmed prerequisites] | [confirmed operator or sentinel] | [explicit keep/retire decision plus evidence, or sentinel] | [decision status] |

Neither `Retirement candidate`, a successful cutover, elapsed soak time, nor package approval authorizes removal. Keep an
artifact in scope until the named operator explicitly decides otherwise with recorded evidence. The runbook never
deletes, disables, or modifies source artifacts.

## Unresolved Operator Decisions

List every sentinel-bearing fact once, link it to all dependent actions and evidence IDs, and preserve the upstream
status. The runbook is not operationally ready while a dependent item remains unresolved.

| Missing Fact | Scope | Dependent Actions | Evidence IDs and Preserved Status | Required Decision or Validation | Owner |
|---|---|---|---|---|---|
| [exact sentinel text] | [scope] | [deployment/cutover/rollback/reconciliation/soak/retirement actions blocked] | [MIG-###/SRC-### and status] | [specific operator decision or current validation] | [confirmed owner or sentinel] |

When an operator resolves an item, regenerate and revalidate the affected runbook sections against current evidence.
Resolving a fact or approving the regenerated package still does not authorize the operational action itself.

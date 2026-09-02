package io.github.luigidemasi.camelkit.generator;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MigrationOperationsContractTest {

    private static final Pattern BLANKET_COMPATIBILITY = Pattern.compile(
            "(?i)API Compatibility:\\s*Assumed|API compatibility is assumed by default");

    @Test
    void migrationRiskAnalysisIsEvidenceBackedAndLoadBearing() throws Exception {
        String migrate = resource("skills/camel-migrate/SKILL.md");
        String bobMigrate = resource("templates/bob/gates/camel-migrate.md");
        String analysis = resource("skills/camel-migrate/guides/migration-analysis.md");
        String normalizedAnalysis = analysis.replaceAll("\\s+", " ");

        assertContainsAll(normalizedAnalysis,
                "docs/camel-kit/<PIPELINE_ID>/migration-analysis.md",
                "Static discovery can show what was found; it cannot guarantee that undocumented behavior does not exist",
                "Use a stable ID of `MIG-###`",
                "Status is exactly `Confirmed`, `Inferred`, or `Unknown`",
                "`Confirmed` requires explicit user confirmation or direct structural evidence",
                "`Inferred` requires concrete evidence but still needs validation",
                "`Unknown` means evidence is missing, conflicting, stale, unparsable",
                "Assumption or Evidence Gap",
                "Impact if Wrong",
                "Validation",
                "Owner",
                "Disposition",
                "A risk row or lack of references never authorizes removal from the migration scope",
                "Do not collapse these into a blanket \"API compatible\" entry");

        assertOrdered(migrate,
                "| A1 | guides/mulesoft-phase1.md",
                "| R1 | guides/migration-analysis.md",
                "| A2 | guides/mulesoft-phase2.md");
        assertOrdered(migrate,
                "| B1 | guides/camel-version-phase1.md",
                "| R1 | guides/migration-analysis.md",
                "| B2 | guides/camel-version-phase2.md");
        assertOrdered(migrate,
                "| C1 | guides/biztalk-phase1.md",
                "| R1 | guides/migration-analysis.md",
                "| C2 | guides/biztalk-phase2.md");
        String bobPackage = bobMigrate.substring(bobMigrate.indexOf("## Generate the Vendor Design Package"));
        assertOrdered(bobPackage,
                "`mulesoft-phase1.md`",
                "`.bob/skills/camel-migrate/guides/migration-analysis.md`",
                "`mulesoft-phase2.md`");
        assertOrdered(bobPackage,
                "`camel-version-phase1.md`",
                "`.bob/skills/camel-migrate/guides/migration-analysis.md`",
                "`camel-version-phase2.md`");
        assertOrdered(bobPackage,
                "`biztalk-phase1.md`",
                "`.bob/skills/camel-migrate/guides/migration-analysis.md`",
                "`biztalk-phase2.md`");

        assertContainsAll(migrate,
                "doc init --by camel-migrate docs/camel-kit/<PIPELINE_ID>/business-requirements.md",
                "doc init --by camel-migrate --from business-requirements.md docs/camel-kit/<PIPELINE_ID>/migration-analysis.md",
                "doc init --by camel-migrate --from migration-analysis.md docs/camel-kit/<PIPELINE_ID>/design-spec.md",
                "doc stale --reason \"business requirements changed\" --cascade docs/camel-kit/<PIPELINE_ID>/migration-analysis.md",
                "doc stale --reason \"migration analysis changed\" --cascade docs/camel-kit/<PIPELINE_ID>/design-spec.md");
        assertContainsAll(bobMigrate,
                ".bob/skills/camel-migrate/guides/migration-analysis.md",
                "doc init --by camel-migrate --from business-requirements.md docs/camel-kit/<PIPELINE_ID>/migration-analysis.md",
                "doc init --by camel-migrate --from migration-analysis.md docs/camel-kit/<PIPELINE_ID>/design-spec.md");
        assertContainsAll(resource("skills/camel-plan/SKILL.md"),
                "validate provenance from `camel-brainstorm` or `camel-migrate`");
        assertContainsAll(resource("templates/bob/gates/camel-plan.md"),
                "require matching `camel-brainstorm` or `camel-migrate` provenance");

        for (String phase2 : List.of(
                "skills/camel-migrate/guides/mulesoft-phase2.md",
                "skills/camel-migrate/guides/camel-version-phase2.md",
                "skills/camel-migrate/guides/biztalk-phase2.md")) {
            assertContainsAll(normalizeMarkdown(resource(phase2)),
                    "docs/camel-kit/<PIPELINE_ID>/migration-analysis.md",
                    "`Inferred` or `Unknown` `MIG-###`",
                    "explicit constraint, validation requirement, or unresolved decision",
                    "Never reclassify from topology or inferred data");
        }

        String compatibilityContracts = String.join("\n",
                resource("skills/camel-brainstorm/guides/migration-discovery.md"),
                resource("skills/camel-brainstorm/guides/migration-graph-analysis.md"),
                resource("skills/camel-brainstorm/guides/migration-mule-graph-analysis.md"),
                resource("skills/camel-brainstorm/guides/migration-biztalk-graph-analysis.md"),
                resource("skills/camel-migrate/guides/mulesoft-phase1.md"),
                resource("skills/camel-migrate/guides/camel-version-phase1.md"),
                resource("skills/camel-migrate/guides/biztalk-phase1.md"),
                analysis,
                bobMigrate,
                migrate);
        assertFalse(BLANKET_COMPATIBILITY.matcher(compatibilityContracts).find(),
                "Migration resources must not assign API compatibility by default");
    }

    @Test
    void sourceRetirementAuditIsCoverageQualifiedAndLoadBearing() throws Exception {
        String migrate = resource("skills/camel-migrate/SKILL.md");
        String bobMigrate = resource("templates/bob/gates/camel-migrate.md");
        String audit = resource("skills/camel-migrate/guides/source-retirement-audit.md");
        String normalizedAudit = audit.replaceAll("\\s+", " ");

        assertContainsAll(normalizedAudit,
                "# Source-Retirement Candidate Audit",
                "Update the `## Source-Retirement Candidate Audit` section in `migration-analysis.md`",
                "This audit identifies structural source-retirement **candidates**, not dead code",
                "Use stable IDs of `SRC-###`",
                "reuse mappings from an existing `migration-analysis.md` first, then reconcile "
                                               + "`.camel-kit/project-snapshot.md` only when it records that same boundary",
                "Identify an artifact finding by platform, type, relative source path, and structural identifier",
                "identify a reference finding by source path, structural location, reference kind, and literal target",
                "the analysis ID wins and the snapshot conflict is an evidence gap",
                "Never allocate a second ID for the same identity; allocate the next unused ID only for a new finding",
                "`Reachable` — a supported static path exists from a corroborated entry root",
                "`Retirement candidate` — complete relevant supported source closure found no path from any "
                                                                                               + "corroborated entry root",
                "`Broken reference` — a supported static reference names a target whose absence is established by "
                                                                                                                            + "complete confirmed target-resolution closure",
                "no missing or out-of-bound target sources or assemblies",
                "`Unknown` — coverage is incomplete, evidence conflicts, the reference is dynamic, parsing failed, or "
                                                                           + "relevant material is outside the selected boundary",
                "Absence of a graph never changes `Unknown` to `Retirement candidate`",
                "A failed, capped, or unavailable optional graph query alone never downgrades a classification "
                                                                                        + "established by complete supported source closure",
                "`Broken reference` classifies a reference, not the source artifact that contains it",
                "a reachable artifact can coexist with a broken-reference finding",
                "root-disconnected cycle",
                "The required report is identical in graph-assisted and graph-less runs",
                "Routes with a structurally parsed external consumer endpoint; scheduled consumers such as",
                "Constant route-to-route `direct:` and `seda:` endpoint names",
                "Flows with a parsed message source such as listener, connector source, scheduler, or poller",
                "Constant `flow-ref` targets to flows or sub-flows",
                "Receive Locations and activating receives corroborated by deployment bindings",
                "Constant Call Orchestration and Start Orchestration targets plus map/pipeline/binding references parsed "
                                                                                                 + "from the application",
                "No candidates identified in covered artifacts; overall result inconclusive.",
                "Camel-Kit never deletes source artifacts and never removes a candidate from migration scope "
                                                                                               + "automatically",
                "Keep every candidate and unknown in migration scope until an explicit user disposition says otherwise",
                "Approval of the overall design package does not supply that disposition and does not authorize source "
                                                                                                                         + "retirement");
        assertContainsAll(audit,
                "## Source-Retirement Candidate Audit",
                "### Coverage",
                "### Reachability Summary",
                "### Retirement Candidates",
                "### Broken References",
                "### Evidence Gaps",
                "### Scope Disposition");

        String sharedAuditRow = "| R1 | guides/migration-analysis.md | guides/source-retirement-audit.md | 4K | "
                                + "After Phase 1 and before Phase 2 |";
        assertContainsAll(migrate,
                sharedAuditRow,
                "Run the behavioral-risk pass in `migration-analysis.md` first");
        assertOrdered(migrate,
                "| A1 | guides/mulesoft-phase1.md",
                sharedAuditRow,
                "| A2 | guides/mulesoft-phase2.md");
        assertOrdered(migrate,
                "| B1 | guides/camel-version-phase1.md",
                sharedAuditRow,
                "| B2 | guides/camel-version-phase2.md");
        assertOrdered(migrate,
                "| C1 | guides/biztalk-phase1.md",
                sharedAuditRow,
                "| C2 | guides/biztalk-phase2.md");

        String bobPackage = bobMigrate.substring(bobMigrate.indexOf("## Generate the Vendor Design Package"));
        for (String vendor : List.of("mulesoft", "camel-version", "biztalk")) {
            assertOrdered(bobPackage,
                    "`" + vendor + "-phase1.md`",
                    "`.bob/skills/camel-migrate/guides/migration-analysis.md`",
                    "`.bob/skills/camel-migrate/guides/source-retirement-audit.md`",
                    "`" + vendor + "-phase2.md`");
        }

        for (String phase2 : List.of(
                "skills/camel-migrate/guides/mulesoft-phase2.md",
                "skills/camel-migrate/guides/camel-version-phase2.md",
                "skills/camel-migrate/guides/biztalk-phase2.md")) {
            assertContainsAll(normalizeMarkdown(resource(phase2)),
                    "source-retirement audit",
                    "must be complete",
                    "analysis must contain `## Behavioral Assumptions and Risks` and "
                                        + "`## Source-Retirement Candidate Audit`",
                    "`Inferred` or `Unknown` `MIG-###` row",
                    "`Retirement candidate`, `Broken reference`, or `Unknown` `SRC-###` row",
                    "explicit constraint, validation requirement, or unresolved decision",
                    "preserve every migration-strategy scope");
        }

        String camelGraph = resource("skills/camel-brainstorm/guides/migration-graph-analysis.md")
                .replaceAll("\\s+", " ");
        assertContainsAll(camelGraph,
                "Graph results never replace source inspection",
                "`graph route-topology` emits only routes with an outgoing route-to-route connection",
                "scheduled consumers such as `timer:`, `quartz:`, and `scheduler:`",
                "Traverse only constant `direct:` and `seda:` route-to-route references",
                "a reachable route can coexist with a broken-reference row",
                "failed or capped optional graph query is an evidence gap but does not override a classification",
                "never proof that a route is dead or safe to remove");

        String muleGraph = resource("skills/camel-brainstorm/guides/migration-mule-graph-analysis.md")
                .replaceAll("\\s+", " ");
        assertContainsAll(muleGraph,
                "Every graph result is provisional evidence",
                "bounded source scan of the selected Mule XML is mandatory",
                "Query both flow and sub-flow nodes",
                "collect every `<flow-ref>` whose `name` is a constant",
                "a reachable definition can coexist with a broken-reference row",
                "failed or truncated optional graph query is an evidence gap but does not override a classification",
                "Graph absence alone can never produce a stronger classification");

        String bizTalkGraph = resource("skills/camel-brainstorm/guides/migration-biztalk-graph-analysis.md")
                .replaceAll("\\s+", " ");
        assertContainsAll(bizTalkGraph,
                "Absence of a graph node or edge never proves absence in the application",
                "Absence of a `BIZTALK_PORT_BINDING` edge proves nothing",
                "absence of a `BIZTALK_CALLS_ORCHESTRATION` edge",
                "inspect every `.odx` file for Call Orchestration and Start Orchestration shapes",
                "a reachable orchestration can coexist with a broken-reference row",
                "never describe them as dead or safe to remove");

        for (String graphGuide : List.of(camelGraph, muleGraph, bizTalkGraph)) {
            assertContainsAll(graphGuide,
                    "reuse the mapping from an existing `migration-analysis.md` bound to the same canonical source "
                                          + "boundary",
                    "reconcile a prior project snapshot only if it records that boundary",
                    "Artifact identity is platform, type, relative source path, and structural identifier",
                    "reference identity is source path, structural location, reference kind, and literal target",
                    "The analysis mapping wins any conflict",
                    "allocate the next unused ID only for a new finding");
        }
        for (String graphGuide : List.of(camelGraph, muleGraph, bizTalkGraph)) {
            assertContainsAll(graphGuide, "Canonical Source Boundary: [validated source root or archive boundary]");
        }

        assertFalse(camelGraph.contains("No inbound connections + `timer:`/`scheduler:` consumer"));
        assertFalse(muleGraph.contains("(never called)"));
        String discovery = normalizeMarkdown(resource("skills/camel-brainstorm/guides/migration-discovery.md"));
        assertContainsAll(discovery,
                "Put every discovered route and project artifact in migration scope by default",
                "Only the later source-retirement audit may record a specific `User-approved exclusion`",
                "all candidates and unknowns remain in scope");
        assertFalse(discovery.contains("Routes to migrate:   ALL ([N] routes detected)"));
        assertFalse(discovery.contains("ALL routes and ALL projects are migrated. Every time. No exceptions."));

        for (String validationResource : List.of(
                "skills/camel-validate/guides/graph-dead-code-report.md",
                "templates/bob/gates/camel-validate.md")) {
            String validationCandidates = resource(validationResource);
            assertContainsAll(validationCandidates,
                    "unusedArtifacts", "orphanedRoutes", "unusedProperties", "structural candidate");
            assertTrue(validationCandidates.contains("graph coverage")
                    || validationCandidates.contains("graph-covered"),
                    validationResource + " must qualify candidate coverage");
            assertFalse(validationCandidates.contains("They may be safe to remove."));
            assertFalse(validationCandidates.contains("✅ No dead code detected."));
        }
    }

    @Test
    void migrationStrategyGuidanceRequiresConfirmedSafeTrafficSeams() throws Exception {
        String rawAnalysis = resource("skills/camel-migrate/guides/migration-analysis.md");
        String analysis = normalizeMarkdown(rawAnalysis);
        String migrate = normalizeMarkdown(resource("skills/camel-migrate/SKILL.md"));
        String bobMigrate = normalizeMarkdown(resource("templates/bob/gates/camel-migrate.md"));
        int outputStart = rawAnalysis.indexOf("> **Output:**");
        int outputEnd = rawAnalysis.indexOf("\n\n", outputStart);
        assertTrue(outputStart >= 0 && outputEnd > outputStart, "Migration analysis must declare its outputs");
        assertEquals(
                "**Output:** `docs/camel-kit/<PIPELINE_ID>/migration-analysis.md` and the `## Migration Strategy` "
                     + "section in `docs/camel-kit/<PIPELINE_ID>/business-requirements.md`.",
                normalizeMarkdown(rawAnalysis.substring(outputStart, outputEnd)),
                "Migration analysis must declare exactly its two outputs");

        assertContainsAll(analysis,
                "`docs/camel-kit/<PIPELINE_ID>/migration-analysis.md` and the `## Migration Strategy` section in "
                                    + "`docs/camel-kit/<PIPELINE_ID>/business-requirements.md`",
                "Classify independently switchable ingress scopes",
                "shared state, correlation, ordering, or transaction boundaries",
                "reconcile every discovered ingress and every corroborated entry root into exactly one non-overlapping "
                                                                                  + "scope",
                "The external traffic control and the owner authorized to operate it",
                "A deterministic routing or partition unit that selects old versus new processing",
                "Mutually exclusive old/new ownership of each selected unit",
                "An aligned state and correlation boundary, including in-flight work",
                "Delivery and ordering implications while traffic is divided or switched",
                "Duplicate-delivery exposure and the applicable idempotency control",
                "Comparable legacy-versus-target telemetry for validating the switched unit",
                "A rollback signal and reversible traffic control owned by an identified operator",
                "Reference existing `MIG-###` and `SRC-###` rows as evidence rather than copying their claims",
                "append one independently testable `MIG-###` row with its actual evidence status",
                "Preserve existing IDs and do not convert an `Inferred` or `Unknown` fact to `Confirmed`",
                "Static source structure, configuration, or graph evidence can show that a mechanism is declared, but "
                                                                                                           + "by itself is at most `Inferred` evidence that the external control is currently operative",
                "A documented capability, stale configuration, or unobserved binding is not operational corroboration",
                "current runtime, deployment, infrastructure, or monitoring corroboration tied to the named control "
                                                                                                                        + "boundary",
                "explicit operator confirmation that identifies the scope, control, and current owner",
                "This pass runs before target design and deployment",
                "it is not a claim that the target is deployed or that cutover is ready",
                "explicit, evidence-backed design constraints approved by their named operator or owner",
                "each with a concrete pre-cutover validation obligation",
                "Do not require an undeployed target to provide runtime proof",
                "After appending or changing seam rows, recompute `## Validation Summary` over every `MIG-###` row",
                "Rebuild `## Design Obligations` so every new seam row is carried",
                "Size, route count, topology, graph availability, and dependency order are not traffic seams");

        String decisionRules = analysis.substring(analysis.indexOf("Classify each scope in this order:"));
        assertOrdered(decisionRules,
                "`Undetermined - evidence needed`",
                "`Incremental candidate`",
                "`Single cutover required`");
        assertContainsAll(analysis,
                "`Undetermined - evidence needed` — use when any required fact is missing or conflicting, or has "
                                    + "`Inferred` or `Unknown` evidence",
                "`Incremental candidate` — use only when the existing seam/control is currently confirmed, all eight "
                                                                          + "feasibility facts or target design constraints are `Confirmed`",
                "The classification is design candidacy, not cutover readiness",
                "`Single cutover required` — use only for a named scope whose validated source boundary and "
                                                                                 + "corresponding operational traffic-control boundary have a closed ingress/control inventory",
                "complete current `Confirmed` evidence proves every seam candidate within those boundaries absent or "
                                                                                                                                                                                 + "unsafe",
                "Anything outside either boundary or not currently confirmed remains `Undetermined - evidence needed`",
                "These three values are a closed taxonomy; do not invent or emit a fourth classification",
                "| Evidence scenario | Classification | Required result |",
                "Current, `Confirmed` evidence establishes the existing seam/control, and every other feasibility fact "
                                                                            + "is confirmed as current behavior or an explicit target design constraint with pre-cutover "
                                                                            + "validation | `Incremental candidate`",
                "Complete, current, `Confirmed` operational evidence proves all seam candidates absent or unsafe "
                                                                                                                      + "inside named, validated source and operational-control boundaries with a closed, "
                                                                                                                      + "operator-confirmed inventory | `Single cutover required`",
                "Any required evidence is missing, conflicting, `Inferred`, `Unknown`, `TBD`, outside the boundaries, "
                                                                                                                                                                                    + "or scope grouping is uncertain | `Undetermined - evidence needed`",
                "A `TBD` value for any of the eight required facts makes that fact incomplete, forces the scope to "
                                                                                                                                                                                                                                                           + "`Undetermined - evidence needed`, and suppresses incremental/strangler guidance");

        assertContainsAll(analysis,
                "Apache Camel",
                "operator-controlled gateway or load-balancer split for HTTP/REST/CXF",
                "deterministic JMS selector or Kafka partition",
                "mutually exclusive source directory or pre-consumption source-side routing predicate",
                "`direct:`, `seda:`, or `vm:` links",
                "graph edges",
                "a shared consumer group",
                "an in-route predicate after consumption",
                "scheduled `timer:`/`quartz:`/`scheduler:` work",
                "competing consumers on the same directory or database poll",
                "MuleSoft",
                "operator-controlled gateway, proxy, or listener split",
                "mutually exclusive selector, partition, source directory, or pre-consumption source-side routing "
                                                                         + "predicate",
                "`flow-ref`, sub-flow structure",
                "a shared listener or queue",
                "an in-flow predicate after consumption",
                "scheduler activation",
                "competing file/database pollers",
                "Microsoft BizTalk",
                "Operator-controlled external routing or a mutually exclusive Receive Location or subscription filter",
                "deployment bindings used only as corroboration",
                "Binding data by itself",
                "an enable/disable capability by itself",
                "Direct Binding",
                "Call Orchestration",
                "duplicate consumers of the same source",
                "missing bindings/assemblies");

        assertContainsAll(analysis,
                "In `business-requirements.md`, create or replace only `## Migration Strategy`; preserve every other "
                                    + "Phase 1 heading and body unchanged",
                "| Scope | Covered Ingress IDs | Classification | Traffic Seam | Evidence IDs | Conditions / Blocking "
                                                                            + "Gaps |",
                "[Incremental candidate/Single cutover required/Undetermined - evidence needed]",
                "[confirmed external control and routing unit; Confirmed absent/unsafe within named validated "
                                                                                                  + "boundaries; or Unknown]",
                "Add the following subsection only when at least one scope is an `Incremental candidate`",
                "### Incremental / Strangler Guidance",
                "Do not add incremental/strangler guidance for `Single cutover required` or "
                                                        + "`Undetermined - evidence needed` scopes",
                "### Migration Strategy Constraints",
                "| Scope | Covered Ingress IDs | Classification | Design Obligation | Evidence IDs |",
                "An `Incremental candidate` permits design guidance only for its confirmed existing seam and target "
                                                                                                       + "constraints",
                "does not claim that those constraints are implemented or that cutover is ready",
                "`Undetermined - evidence needed` blocks a concrete incremental or single-cutover choice",
                "Phase 2 must not derive a different classification from topology or silently discard a `MIG-###` or "
                                                                                                           + "`SRC-###` obligation");
        assertFalse(analysis.contains("None proven"),
                "The traffic-seam cell must distinguish bounded proof from missing evidence");

        String manifest = migrate.substring(migrate.indexOf("## Guide Manifest"));
        assertOrdered(manifest,
                "selected vendor's Phase 1",
                "behavioral risk pass",
                "source-retirement audit",
                "deferred migration-strategy pass",
                "Only then dispatch the selected vendor's Phase 2");
        assertContainsAll(migrate,
                "Never dispatch Phase 2 directly from Phase 1 or before all three R1 passes finish",
                "`business-requirements.md` with `## Migration Strategy` and `design-spec.md` with "
                                                                                                     + "`### Migration Strategy Constraints`",
                "uses exactly `Incremental candidate`, `Single cutover required`, or `Undetermined - evidence needed`",
                "design preserves that classification plus its `MIG-###` and `SRC-###` evidence IDs",
                "`Covered Ingress IDs` form an exact, non-overlapping partition of every enumerated ingress/root "
                                                                                                      + "`MIG-###` and `SRC-###` ID",
                "the design preserves the identical scope-to-ID mapping",
                "A missing, duplicated, or reassigned business-requirements ID requires rerunning the deferred strategy "
                                                                          + "pass; a design mismatch requires rerunning Phase 2",
                "Only a scope classified `Incremental candidate` from complete, Confirmed safe-seam evidence may "
                                                                                                                                  + "receive concrete incremental or strangler guidance",
                "`Undetermined - evidence needed` blocks that guidance");

        String r1Allowlist = "The R1 write allowlist contains exactly the validated `business-requirements.md` and "
                             + "`migration-analysis.md` paths; no other artifact may be written";
        assertContainsAll(migrate, r1Allowlist);
        assertContainsAll(bobMigrate, r1Allowlist);

        String bobPackage = bobMigrate.substring(bobMigrate.indexOf("## Generate the Vendor Design Package"));
        assertOrdered(bobPackage,
                "Run the detected vendor's Phase 1 guide first",
                "Next read `.bob/skills/camel-migrate/guides/migration-analysis.md`",
                "Then read `.bob/skills/camel-migrate/guides/source-retirement-audit.md`",
                "return to the deferred migration-strategy pass",
                "Only after the deferred strategy pass finishes");
        assertContainsAll(bobMigrate,
                "classifies each independently switchable scope as exactly `Incremental candidate`, "
                                      + "`Single cutover required`, or `Undetermined - evidence needed`",
                "Concrete `### Incremental / Strangler Guidance` is allowed only for a scope classified "
                                                                                                          + "`Incremental candidate` from complete, Confirmed safe-seam evidence",
                "`Undetermined - evidence needed` blocks that guidance",
                "`business-requirements.md` must have `## Migration Strategy` and `design-spec.md` must have "
                                                                         + "`### Migration Strategy Constraints`",
                "`Covered Ingress IDs` form an exact, non-overlapping partition of every enumerated ingress/root "
                                                                                                                   + "`MIG-###` and `SRC-###` ID",
                "the design preserves the identical scope-to-ID mapping",
                "preserve its classification plus supporting `MIG-###` and `SRC-###` evidence IDs");

        for (String phase2 : List.of(
                "skills/camel-migrate/guides/mulesoft-phase2.md",
                "skills/camel-migrate/guides/camel-version-phase2.md",
                "skills/camel-migrate/guides/biztalk-phase2.md")) {
            String guide = normalizeMarkdown(resource(phase2));
            assertContainsAll(guide,
                    "the deferred migration-strategy pass must be complete",
                    "the business requirements must contain `## Migration Strategy`",
                    "the analysis must contain `## Behavioral Assumptions and Risks` and "
                                                                                      + "`## Source-Retirement Candidate Audit`",
                    "preserve every migration-strategy scope",
                    "`Incremental candidate`, `Single cutover required`, or `Undetermined - evidence needed`",
                    "every supporting `MIG-###` and `SRC-###` evidence ID and status",
                    "Concrete incremental or strangler design is allowed only for an `Incremental candidate` whose "
                                                                                       + "eight required operational-seam facts all have `Confirmed` evidence",
                    "Confirmed target-side conditions are design obligations with pre-cutover validation, not claims "
                                                                                                                                                                + "that the target is deployed or cutover-ready",
                    "### Migration Strategy Constraints",
                    "| Scope | Covered Ingress IDs | Classification | Design Obligation | Evidence IDs |",
                    "same non-overlapping ingress IDs from business requirements",
                    "with one row per business-requirements strategy scope");
        }

        for (String dispatchTemplate : List.of(
                "templates/dispatch/bob.md",
                "templates/dispatch/claude.md",
                "templates/dispatch/copilot.md",
                "templates/dispatch/gemini.md")) {
            String dispatch = normalizeMarkdown(resource(dispatchTemplate));
            assertContainsAll(dispatch,
                    "{output-paths}",
                    "owning skill",
                    "exactly that step's declared output path or paths");
            assertFalse(dispatch.contains("{output-path}"),
                    dispatchTemplate + " must not restore the singular output placeholder");
            assertFalse(dispatch.contains("required output path"),
                    dispatchTemplate + " must not restore the singular output prose");
            assertFalse(dispatch.contains("R1"),
                    dispatchTemplate + " must not leak camel-migrate-specific dispatch rules");
        }

        String initRequirements = "doc init --by camel-migrate docs/camel-kit/<PIPELINE_ID>/business-requirements.md";
        String initAnalysis = "doc init --by camel-migrate --from business-requirements.md "
                              + "docs/camel-kit/<PIPELINE_ID>/migration-analysis.md";
        String initDesign = "doc init --by camel-migrate --from migration-analysis.md "
                            + "docs/camel-kit/<PIPELINE_ID>/design-spec.md";
        String staleAnalysis = "doc stale --reason \"business requirements changed\" --cascade "
                               + "docs/camel-kit/<PIPELINE_ID>/migration-analysis.md";
        String unstaleAnalysis = "doc unstale docs/camel-kit/<PIPELINE_ID>/migration-analysis.md";
        String staleDesign = "doc stale --reason \"migration analysis changed\" --cascade "
                             + "docs/camel-kit/<PIPELINE_ID>/design-spec.md";
        String unstaleDesign = "doc unstale docs/camel-kit/<PIPELINE_ID>/design-spec.md";
        for (String orchestrator : List.of(migrate, bobMigrate)) {
            assertOrdered(orchestrator,
                    initRequirements,
                    initAnalysis,
                    initDesign,
                    "`doc init` initializes new metadata only",
                    staleAnalysis,
                    "genuinely rerun all three R1 passes",
                    unstaleAnalysis,
                    "Keep the cascade-staled design stale",
                    staleDesign,
                    "After, and only after, Phase 2 has genuinely regenerated",
                    unstaleDesign,
                    "Never clear staleness merely because initialization ran");
            assertContainsAll(orchestrator,
                    "rerun the responsible pass: behavioral risk for `MIG-###` evidence",
                    "source retirement for `SRC-###` evidence",
                    "deferred strategy for the business-requirements strategy and guidance",
                    "Phase 2 for design constraints");
        }
    }

    private static String resource(String name) throws Exception {
        var url = MigrationOperationsContractTest.class.getClassLoader().getResource(name);
        assertNotNull(url, "Missing test resource: " + name);
        URI uri = url.toURI();
        return Files.readString(Path.of(uri));
    }

    private static String normalizeMarkdown(String content) {
        return content.replaceAll("(?m)^>\\s?", "").replaceAll("\\s+", " ");
    }

    private static void assertContainsAll(String content, String... expected) {
        for (String value : expected) {
            assertTrue(content.contains(value), "Missing contract text: " + value);
        }
    }

    private static void assertOrdered(String content, String... values) {
        int previousIndex = -1;
        for (String value : values) {
            int currentIndex = content.indexOf(value);
            assertTrue(currentIndex > previousIndex, "Expected order: " + String.join(" -> ", values));
            previousIndex = currentIndex;
        }
    }
}

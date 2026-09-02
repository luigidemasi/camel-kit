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
                    "must not silently resolve or exclude it");
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
                "run `migration-analysis.md` first, then `source-retirement-audit.md` against its output");
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
                    "analysis contains `## Source-Retirement Candidate Audit`",
                    "every `Inferred` or `Unknown` `MIG-###` row and every `Retirement candidate`, `Broken reference`, "
                                                                                + "or `Unknown` `SRC-###` row",
                    "explicit scope constraint, validation requirement, or unresolved decision",
                    "Preserve each ID and status; Phase 2 must not silently resolve or exclude it");
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

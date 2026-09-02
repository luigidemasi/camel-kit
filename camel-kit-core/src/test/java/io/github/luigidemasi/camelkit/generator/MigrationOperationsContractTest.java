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
            assertContainsAll(resource(phase2).replaceAll("\\s+", " "),
                    "docs/camel-kit/<PIPELINE_ID>/migration-analysis.md",
                    "`Inferred` or `Unknown` `MIG-###`",
                    "must not silently declare it resolved");
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

    private static String resource(String name) throws Exception {
        var url = MigrationOperationsContractTest.class.getClassLoader().getResource(name);
        assertNotNull(url, "Missing test resource: " + name);
        URI uri = url.toURI();
        return Files.readString(Path.of(uri));
    }

    private static void assertContainsAll(String content, String... expected) {
        for (String value : expected) {
            assertTrue(content.contains(value), "Missing contract text: " + value);
        }
    }

    private static void assertOrdered(String content, String first, String second, String third) {
        int firstIndex = content.indexOf(first);
        int secondIndex = content.indexOf(second);
        int thirdIndex = content.indexOf(third);
        assertTrue(firstIndex >= 0 && firstIndex < secondIndex && secondIndex < thirdIndex,
                () -> "Expected order: " + first + " -> " + second + " -> " + third);
    }
}

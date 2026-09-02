package io.github.luigidemasi.camelkit.command.doc;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.doc.FrontmatterHandler;
import io.github.luigidemasi.camelkit.doc.GeneratedInfo;
import io.github.luigidemasi.camelkit.doc.StalenessInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class DocCommandsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Path writeDoc(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file;
    }

    private String runCheck(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cl = new CommandLine(new DocCheckCommand());
        cl.setOut(new PrintWriter(out));
        cl.setErr(new PrintWriter(err));
        int code = cl.execute(args);
        assertEquals(0, code, "Check failed: " + err);
        return out.toString().trim();
    }

    private int runCheckExpectError(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cl = new CommandLine(new DocCheckCommand());
        cl.setOut(new PrintWriter(out));
        cl.setErr(new PrintWriter(err));
        return cl.execute(args);
    }

    private int runStale(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cl = new CommandLine(new DocStaleCommand());
        cl.setOut(new PrintWriter(out));
        cl.setErr(new PrintWriter(err));
        return cl.execute(args);
    }

    private int runUnstale(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cl = new CommandLine(new DocUnstaleCommand());
        cl.setOut(new PrintWriter(out));
        cl.setErr(new PrintWriter(err));
        return cl.execute(args);
    }

    @Test
    void checkWithFrontmatter() throws Exception {
        String content = FrontmatterHandler.writeFrontmatter(
                StalenessInfo.stale("spec changed", "2026-05-13T10:00:00Z"),
                new GeneratedInfo("2026-05-13T09:00:00Z", "camel-plan", "design-spec.md"),
                "# Plan\n");
        Path file = writeDoc("plan.md", content);

        String json = runCheck(file.toString());
        JsonNode node = MAPPER.readTree(json);

        assertTrue(node.get("stale").asBoolean());
        assertEquals("spec changed", node.get("reason").asText());
        assertEquals("2026-05-13T10:00:00Z", node.get("since").asText());
        assertEquals("camel-plan", node.get("generated").get("by").asText());
        assertEquals("design-spec.md", node.get("generated").get("from").asText());
    }

    @Test
    void checkWithoutFrontmatter() throws Exception {
        Path file = writeDoc("plain.md", "# Just a document\n");

        String json = runCheck(file.toString());
        JsonNode node = MAPPER.readTree(json);

        assertFalse(node.get("stale").asBoolean());
        assertTrue(node.get("since").isNull());
        assertTrue(node.get("reason").isNull());
        assertTrue(node.get("generated").isNull());
    }

    @Test
    void checkNonexistentFileReturnsError() {
        int code = runCheckExpectError(tempDir.resolve("missing.md").toString());
        assertEquals(1, code);
    }

    @Test
    void staleMarksFile() throws Exception {
        String content = FrontmatterHandler.writeFrontmatter(
                StalenessInfo.fresh(),
                new GeneratedInfo("2026-05-13T09:00:00Z", "camel-plan", "design-spec.md"),
                "# Plan\n");
        Path file = writeDoc("plan.md", content);

        int code = runStale("--reason", "spec changed", file.toString());
        assertEquals(0, code);

        String updated = Files.readString(file);
        String yaml = FrontmatterHandler.extractFrontmatterYaml(updated);
        StalenessInfo info = FrontmatterHandler.parseStaleness(yaml);
        assertTrue(info.isStale());
        assertEquals("spec changed", info.getReason());
        assertNotNull(info.getSince());
    }

    @Test
    void staleRequiresReason() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cl = new CommandLine(new DocStaleCommand());
        cl.setOut(new PrintWriter(out));
        cl.setErr(new PrintWriter(err));
        int code = cl.execute(tempDir.resolve("any.md").toString());
        assertNotEquals(0, code);
    }

    @Test
    void staleRejectsBlankReason() throws Exception {
        Path file = writeDoc("doc.md",
                FrontmatterHandler.writeFrontmatter(StalenessInfo.fresh(), null, "# Doc\n"));
        int code = runStale("--reason", "", file.toString());
        assertEquals(1, code);

        StalenessInfo info = FrontmatterHandler.parseStaleness(
                FrontmatterHandler.extractFrontmatterYaml(Files.readString(file)));
        assertFalse(info.isStale());
    }

    @Test
    void staleNonexistentFileReturnsError() {
        int code = runStale("--reason", "test", tempDir.resolve("missing.md").toString());
        assertEquals(1, code);
    }

    @Test
    void unstaleClearsFile() throws Exception {
        String content = FrontmatterHandler.writeFrontmatter(
                StalenessInfo.stale("old reason", "2026-05-13T10:00:00Z"),
                new GeneratedInfo("2026-05-13T09:00:00Z", "camel-plan", "design-spec.md"),
                "# Plan\n");
        Path file = writeDoc("plan.md", content);

        int code = runUnstale(file.toString());
        assertEquals(0, code);

        String updated = Files.readString(file);
        String yaml = FrontmatterHandler.extractFrontmatterYaml(updated);
        StalenessInfo info = FrontmatterHandler.parseStaleness(yaml);
        assertFalse(info.isStale());
        assertNull(info.getSince());
        assertNull(info.getReason());
    }

    @Test
    void unstaleNonexistentFileReturnsError() {
        int code = runUnstale(tempDir.resolve("missing.md").toString());
        assertEquals(1, code);
    }

    @Test
    void cascadePropagatesStaleness() throws Exception {
        Path spec = writeDoc("design-spec.md",
                FrontmatterHandler.writeFrontmatter(
                        StalenessInfo.fresh(),
                        null,
                        "# Design Spec\n"));

        Path plan = writeDoc("implementation-plan.md",
                FrontmatterHandler.writeFrontmatter(
                        StalenessInfo.fresh(),
                        new GeneratedInfo("2026-05-13T09:00:00Z", "camel-plan", "design-spec.md"),
                        "# Plan\n"));

        Path report = writeDoc("execution-report.md",
                FrontmatterHandler.writeFrontmatter(
                        StalenessInfo.fresh(),
                        new GeneratedInfo("2026-05-13T10:00:00Z", "camel-execute", "implementation-plan.md"),
                        "# Report\n"));

        int code = runStale("--reason", "spec amended", "--cascade", spec.toString());
        assertEquals(0, code);

        StalenessInfo specInfo = FrontmatterHandler.parseStaleness(
                FrontmatterHandler.extractFrontmatterYaml(Files.readString(spec)));
        assertTrue(specInfo.isStale());

        StalenessInfo planInfo = FrontmatterHandler.parseStaleness(
                FrontmatterHandler.extractFrontmatterYaml(Files.readString(plan)));
        assertTrue(planInfo.isStale());
        assertTrue(planInfo.getReason().contains("design-spec.md"));

        StalenessInfo reportInfo = FrontmatterHandler.parseStaleness(
                FrontmatterHandler.extractFrontmatterYaml(Files.readString(report)));
        assertTrue(reportInfo.isStale());
        assertTrue(reportInfo.getReason().contains("implementation-plan.md"));
    }

    @Test
    void cascadeWithNoDownstreamOnlyMarksTarget() throws Exception {
        Path spec = writeDoc("design-spec.md",
                FrontmatterHandler.writeFrontmatter(
                        StalenessInfo.fresh(),
                        null,
                        "# Design Spec\n"));

        Path unrelated = writeDoc("readme.md", "# Readme\nNo frontmatter.\n");

        int code = runStale("--reason", "changed", "--cascade", spec.toString());
        assertEquals(0, code);

        StalenessInfo specInfo = FrontmatterHandler.parseStaleness(
                FrontmatterHandler.extractFrontmatterYaml(Files.readString(spec)));
        assertTrue(specInfo.isStale());

        String unrelatedContent = Files.readString(unrelated);
        assertFalse(FrontmatterHandler.hasFrontmatter(unrelatedContent));
    }

    @Test
    void migrationProvenanceCascadesFromTheFirstDownstreamArtifact() throws Exception {
        Path requirements = writeDoc("business-requirements.md",
                FrontmatterHandler.writeFrontmatter(
                        StalenessInfo.fresh(),
                        new GeneratedInfo("2026-05-13T08:00:00Z", "camel-migrate", null),
                        "# Business Requirements\n"));
        Path analysis = writeDoc("migration-analysis.md",
                FrontmatterHandler.writeFrontmatter(
                        StalenessInfo.fresh(),
                        new GeneratedInfo("2026-05-13T09:00:00Z", "camel-migrate", "business-requirements.md"),
                        "# Migration Analysis\n"));
        Path design = writeDoc("design-spec.md",
                FrontmatterHandler.writeFrontmatter(
                        StalenessInfo.fresh(),
                        new GeneratedInfo("2026-05-13T10:00:00Z", "camel-migrate", "migration-analysis.md"),
                        "# Design Spec\n"));
        Path plan = writeDoc("implementation-plan.md",
                FrontmatterHandler.writeFrontmatter(
                        StalenessInfo.fresh(),
                        new GeneratedInfo("2026-05-13T11:00:00Z", "camel-plan", "design-spec.md"),
                        "# Plan\n"));

        assertEquals(0, runStale("--reason", "business requirements changed", "--cascade", analysis.toString()));

        assertFalse(staleness(requirements).isStale(), "The amended upstream document remains current");
        assertTrue(staleness(analysis).isStale());
        assertTrue(staleness(design).isStale());
        assertTrue(staleness(plan).isStale());
    }

    private static StalenessInfo staleness(Path file) throws Exception {
        return FrontmatterHandler.parseStaleness(
                FrontmatterHandler.extractFrontmatterYaml(Files.readString(file)));
    }

    private int runInit(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cl = new CommandLine(new DocInitCommand());
        cl.setOut(new PrintWriter(out));
        cl.setErr(new PrintWriter(err));
        return cl.execute(args);
    }

    @Test
    void initAddsMetadataToPlainDocument() throws Exception {
        Path file = writeDoc("plan.md", "# Implementation Plan\n\nBody content.\n");

        int code = runInit("--by", "camel-plan", "--from", "design-spec.md", file.toString());
        assertEquals(0, code);

        String updated = Files.readString(file);
        assertTrue(FrontmatterHandler.hasFrontmatter(updated));

        String yaml = FrontmatterHandler.extractFrontmatterYaml(updated);
        StalenessInfo info = FrontmatterHandler.parseStaleness(yaml);
        assertFalse(info.isStale());

        GeneratedInfo gen = FrontmatterHandler.parseGenerated(yaml);
        assertNotNull(gen);
        assertEquals("camel-plan", gen.getBy());
        assertEquals("design-spec.md", gen.getFrom());
        assertNotNull(gen.getAt());
    }

    @Test
    void initAllowsRootArtifactWithoutSource() throws Exception {
        Path file = writeDoc("design-spec.md", "# Design Spec\n");

        int code = runInit("--by", "camel-brainstorm", file.toString());
        assertEquals(0, code);

        String yaml = FrontmatterHandler.extractFrontmatterYaml(Files.readString(file));
        GeneratedInfo generated = FrontmatterHandler.parseGenerated(yaml);
        assertNotNull(generated);
        assertEquals("camel-brainstorm", generated.getBy());
        assertNull(generated.getFrom());
        assertFalse(yaml.contains("from:"));
    }

    @Test
    void initIsIdempotent() throws Exception {
        String content = FrontmatterHandler.writeFrontmatter(
                StalenessInfo.stale("old reason", "2026-05-13T10:00:00Z"),
                new GeneratedInfo("2026-05-13T09:00:00Z", "camel-plan", "design-spec.md"),
                "# Plan\n");
        Path file = writeDoc("plan.md", content);

        int code = runInit("--by", "camel-execute", "--from", "other.md", file.toString());
        assertEquals(0, code);

        String updated = Files.readString(file);
        GeneratedInfo gen = FrontmatterHandler.parseGenerated(
                FrontmatterHandler.extractFrontmatterYaml(updated));
        assertEquals("camel-plan", gen.getBy());
        assertEquals("design-spec.md", gen.getFrom());
    }

    @Test
    void initRejectsNonexistentFile() {
        int code = runInit("--by", "test", "--from", "source.md",
                tempDir.resolve("missing.md").toString());
        assertEquals(1, code);
    }

    @Test
    void initPreservesBody() throws Exception {
        Path file = writeDoc("report.md", "# Validation Report\n\nDetailed findings.\n");

        runInit("--by", "camel-validate", "--from", "execution-report.md", file.toString());

        String body = FrontmatterHandler.extractBody(Files.readString(file));
        assertTrue(body.startsWith("# Validation Report"));
        assertTrue(body.contains("Detailed findings."));
    }

    @Test
    void staleWithoutCascadeDoesNotPropagate() throws Exception {
        Path spec = writeDoc("design-spec.md",
                FrontmatterHandler.writeFrontmatter(
                        StalenessInfo.fresh(),
                        null,
                        "# Design Spec\n"));

        Path plan = writeDoc("implementation-plan.md",
                FrontmatterHandler.writeFrontmatter(
                        StalenessInfo.fresh(),
                        new GeneratedInfo("2026-05-13T09:00:00Z", "camel-plan", "design-spec.md"),
                        "# Plan\n"));

        int code = runStale("--reason", "spec amended", spec.toString());
        assertEquals(0, code);

        StalenessInfo planInfo = FrontmatterHandler.parseStaleness(
                FrontmatterHandler.extractFrontmatterYaml(Files.readString(plan)));
        assertFalse(planInfo.isStale());
    }
}

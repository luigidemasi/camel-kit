package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.context.InitialContext;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest.DocumentReference;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest.UserText;
import io.github.luigidemasi.camelkit.ship.controller.LedgerProvenanceValidator.SourceKind;
import io.github.luigidemasi.camelkit.ship.controller.LedgerProvenanceValidator.SourceMaterial;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryAnswer;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ShipLedgerSourcesTest {

    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsExactContextProjectAndSignedInteractionSources() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("project"))
                .toAbsolutePath().normalize();
        Files.writeString(project.resolve("requirements.md"), "route orders", StandardCharsets.UTF_8);
        Path state = temporaryDirectory.resolve("state").toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView created = controller.start(new ShipController.PreparedRun(project, "test-adapter"));
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(),
                created.eventDigest(),
                new InitialContextRequest.Composite(
                        List.of(
                                new UserText("use kafka"),
                                new DocumentReference("requirements.md"))));
        ShipRunView context = controller.continueContextResolution(
                created.runId(), resolving.eventDigest(), List.of());

        ShipBlobStore blobs = ShipBlobStore.open(state, created.runId());
        ShipInteractionSigner signer = ShipInteractionSigner.open(state.resolve(created.runId()));
        ShipInteractionBundleService interactions = new ShipInteractionBundleService(signer);
        String nonce = signer.nonce();
        DiscoveryChallenge challenge = challenge(signer, created.runId(), nonce);
        BlobReference bundle = interactions.record(blobs, context.interactionBundle(), challenge);
        DiscoveryAnswer answer = answer(signer, created.runId(), nonce);
        bundle = interactions.record(blobs, bundle, answer);

        ShipLedgerSources.Snapshot sources = ShipLedgerSources.load(
                blobs,
                context.context(),
                context.projectSourceManifest(),
                context.sourceDirectory(),
                bundle,
                interactions);

        assertEquals(4, sources.sources().size());
        assertEquals(
                List.of(SourceKind.INITIAL_CONTEXT, SourceKind.INITIAL_CONTEXT,
                        SourceKind.PROJECT, SourceKind.INTERACTION),
                sources.sources().stream().map(SourceMaterial::kind).toList());
        SourceMaterial interaction = sources.sources().get(3);
        assertEquals("controller:interaction-bundle#exchanges/1/discovery/answer/response",
                interaction.canonicalLocator());
        assertEquals(ShipDigest.sha256("kafka".getBytes(StandardCharsets.UTF_8)), interaction.digest());
        assertEquals(answer, sources.latestAnswer());

        String contextDigest = interactions.read(blobs, bundle).contextDigest();
        DesignChallenge designChallenge = designChallenge(
                signer, created.runId(), contextDigest, nonce);
        bundle = interactions.record(blobs, bundle, designChallenge);
        DesignResponse designResponse = designResponse(signer, designChallenge);
        bundle = interactions.record(blobs, bundle, designResponse);
        ShipLedgerSources.Snapshot designSources = ShipLedgerSources.load(
                blobs,
                context.context(),
                context.projectSourceManifest(),
                context.sourceDirectory(),
                bundle,
                interactions);

        assertNull(designSources.latestDiscoveryAnswer());
        assertNotNull(designSources.latestRequirementsChange());
        assertEquals(designResponse, designSources.latestRequirementsChange().designResponse());
        assertNull(designSources.latestRequirementsChange().planResponse());
        assertEquals(
                "controller:interaction-bundle#exchanges/2/design/response/requestedChanges",
                designSources.sources().get(4).canonicalLocator());

        PlanChallenge planChallenge = planChallenge(
                signer, created.runId(), contextDigest, nonce);
        bundle = interactions.record(blobs, bundle, planChallenge);
        PlanResponse planResponse = planResponse(signer, planChallenge);
        bundle = interactions.record(blobs, bundle, planResponse);
        ShipLedgerSources.Snapshot planSources = ShipLedgerSources.load(
                blobs,
                context.context(),
                context.projectSourceManifest(),
                context.sourceDirectory(),
                bundle,
                interactions);

        assertNotNull(planSources.latestRequirementsChange());
        assertNull(planSources.latestRequirementsChange().designResponse());
        assertEquals(planResponse, planSources.latestRequirementsChange().planResponse());
        assertEquals(
                "controller:interaction-bundle#exchanges/3/plan/response/requestedChanges",
                planSources.sources().get(5).canonicalLocator());
    }

    @Test
    void rejectsAChangedProtectedProjectSource() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("changed-project"))
                .toAbsolutePath().normalize();
        Files.writeString(project.resolve("requirements.md"), "before", StandardCharsets.UTF_8);
        Path state = temporaryDirectory.resolve("changed-state").toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView created = controller.start(new ShipController.PreparedRun(project, "test-adapter"));
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(), created.eventDigest(), new InitialContextRequest.Text("context"));
        ShipRunView context = controller.continueContextResolution(
                created.runId(), resolving.eventDigest(), List.of());
        Files.writeString(context.sourceDirectory().resolve("requirements.md"), "after");

        ShipBlobStore blobs = ShipBlobStore.open(state, created.runId());
        ShipInteractionBundleService interactions = new ShipInteractionBundleService(
                ShipInteractionSigner.open(state.resolve(created.runId())));

        Exception failure = assertThrows(
                Exception.class,
                () -> ShipLedgerSources.load(blobs, context, interactions));
        assertTrue(failure.getMessage().contains("provenance manifest")
                || failure.getMessage().contains("changed"));
    }

    @Test
    void loadsNearLimitContextAfterJsonEscapingExpandsItsStoredForm() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("escaped-project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("escaped-state").toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView created = controller.start(new ShipController.PreparedRun(project, "test-adapter"));
        String content = "\"".repeat(InitialContext.MAX_TOTAL_BYTES);
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(), created.eventDigest(), new InitialContextRequest.Text(content));
        ShipRunView context = controller.continueContextResolution(
                created.runId(), resolving.eventDigest(), List.of());
        ShipBlobStore blobs = ShipBlobStore.open(state, created.runId());
        byte[] encoded = blobs.readBytes(context.context(), ShipJson.MAX_DOCUMENT_BYTES);

        assertTrue(encoded.length > InitialContext.MAX_TOTAL_BYTES + 1024 * 1024);
        ShipLedgerSources.Snapshot sources = ShipLedgerSources.load(
                blobs,
                context,
                new ShipInteractionBundleService(
                        ShipInteractionSigner.open(state.resolve(created.runId()))));

        assertEquals(1, sources.sources().size());
        assertEquals(InitialContext.MAX_TOTAL_BYTES, sources.sources().get(0).content().length);
    }

    @Test
    void skipsNormalizedEmptyApprovalFeedbackSources() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("approval-project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("approval-state").toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView created = controller.start(new ShipController.PreparedRun(project, "test-adapter"));
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(), created.eventDigest(), new InitialContextRequest.Text("context"));
        ShipRunView context = controller.continueContextResolution(
                created.runId(), resolving.eventDigest(), List.of());
        ShipBlobStore blobs = ShipBlobStore.open(state, created.runId());
        ShipInteractionSigner signer = ShipInteractionSigner.open(state.resolve(created.runId()));
        ShipInteractionBundleService interactions = new ShipInteractionBundleService(signer);
        String nonce = signer.nonce();
        String contextDigest = interactions.read(blobs, context.interactionBundle()).contextDigest();
        DesignChallenge challenge = designChallenge(
                signer, created.runId(), contextDigest, nonce);
        BlobReference bundle = interactions.record(blobs, context.interactionBundle(), challenge);
        DesignResponse unsigned = new DesignResponse(
                challenge.schemaVersion(),
                challenge.runId(),
                challenge.contextDigest(),
                challenge.ledgerRevision(),
                challenge.ledgerDigest(),
                challenge.requirementsDigest(),
                challenge.policyDigest(),
                challenge.baselineDigest(),
                challenge.designDigest(),
                challenge.nonce(),
                DesignDecision.APPROVE,
                null,
                "uid:1000",
                "terminal-v1",
                "cli",
                NOW,
                "hmac-sha256:" + "0".repeat(64));
        DesignResponse response = new DesignResponse(
                unsigned.schemaVersion(),
                unsigned.runId(),
                unsigned.contextDigest(),
                unsigned.ledgerRevision(),
                unsigned.ledgerDigest(),
                unsigned.requirementsDigest(),
                unsigned.policyDigest(),
                unsigned.baselineDigest(),
                unsigned.designDigest(),
                unsigned.nonce(),
                unsigned.decision(),
                unsigned.requestedChanges(),
                unsigned.controllerObservedProcessPrincipal(),
                unsigned.declaredCliUiProfile(),
                unsigned.channel(),
                unsigned.answeredAt(),
                signer.sign(Interaction.designResponseMacFields(unsigned)));
        bundle = interactions.record(blobs, bundle, response);

        ShipLedgerSources.Snapshot sources = ShipLedgerSources.load(
                blobs,
                context.context(),
                context.projectSourceManifest(),
                context.sourceDirectory(),
                bundle,
                interactions);

        assertEquals("", response.requestedChanges());
        assertEquals(1, sources.sources().size());
        assertNull(sources.latestEvolutionAuthorization());
    }

    private static DiscoveryChallenge challenge(
            ShipInteractionSigner signer, String runId, String nonce)
            throws Exception {
        String mac = signer.sign(Interaction.discoveryChallengeMacFields(
                runId, 1, "question-1", "open-item-1", "Choose", List.of("kafka"), "kafka", nonce));
        return new DiscoveryChallenge(
                Interaction.SCHEMA_VERSION,
                runId,
                1,
                "question-1",
                "open-item-1",
                "Choose",
                List.of("kafka"),
                "kafka",
                nonce,
                mac);
    }

    private static DiscoveryAnswer answer(
            ShipInteractionSigner signer, String runId, String nonce)
            throws Exception {
        DiscoveryAnswer unsigned = new DiscoveryAnswer(
                Interaction.SCHEMA_VERSION,
                runId,
                1,
                "question-1",
                "open-item-1",
                nonce,
                "kafka",
                "cli",
                NOW,
                "hmac-sha256:" + "0".repeat(64));
        return new DiscoveryAnswer(
                unsigned.schemaVersion(),
                unsigned.runId(),
                unsigned.ledgerRevision(),
                unsigned.questionId(),
                unsigned.openItemId(),
                unsigned.nonce(),
                unsigned.response(),
                unsigned.channel(),
                unsigned.answeredAt(),
                signer.sign(Interaction.discoveryAnswerMacFields(unsigned)));
    }

    private static DesignChallenge designChallenge(
            ShipInteractionSigner signer, String runId, String contextDigest, String nonce)
            throws Exception {
        String binding = ShipDigest.sha256("approval-binding".getBytes(StandardCharsets.UTF_8));
        DesignChallenge unsigned = new DesignChallenge(
                Interaction.SCHEMA_VERSION,
                runId,
                contextDigest,
                1,
                binding,
                binding,
                binding,
                binding,
                binding,
                "design.md",
                nonce,
                "hmac-sha256:" + "0".repeat(64));
        return new DesignChallenge(
                unsigned.schemaVersion(),
                unsigned.runId(),
                unsigned.contextDigest(),
                unsigned.ledgerRevision(),
                unsigned.ledgerDigest(),
                unsigned.requirementsDigest(),
                unsigned.policyDigest(),
                unsigned.baselineDigest(),
                unsigned.designDigest(),
                unsigned.designReference(),
                unsigned.nonce(),
                signer.sign(Interaction.designChallengeMacFields(unsigned)));
    }

    private static DesignResponse designResponse(
            ShipInteractionSigner signer, DesignChallenge challenge)
            throws Exception {
        DesignResponse unsigned = new DesignResponse(
                challenge.schemaVersion(),
                challenge.runId(),
                challenge.contextDigest(),
                challenge.ledgerRevision(),
                challenge.ledgerDigest(),
                challenge.requirementsDigest(),
                challenge.policyDigest(),
                challenge.baselineDigest(),
                challenge.designDigest(),
                challenge.nonce(),
                DesignDecision.REQUEST_REQUIREMENTS_CHANGES,
                "Change the runtime to Quarkus.",
                "uid:1000",
                "terminal-v1",
                "cli",
                NOW,
                "hmac-sha256:" + "0".repeat(64));
        return new DesignResponse(
                unsigned.schemaVersion(),
                unsigned.runId(),
                unsigned.contextDigest(),
                unsigned.ledgerRevision(),
                unsigned.ledgerDigest(),
                unsigned.requirementsDigest(),
                unsigned.policyDigest(),
                unsigned.baselineDigest(),
                unsigned.designDigest(),
                unsigned.nonce(),
                unsigned.decision(),
                unsigned.requestedChanges(),
                unsigned.controllerObservedProcessPrincipal(),
                unsigned.declaredCliUiProfile(),
                unsigned.channel(),
                unsigned.answeredAt(),
                signer.sign(Interaction.designResponseMacFields(unsigned)));
    }

    private static PlanChallenge planChallenge(
            ShipInteractionSigner signer, String runId, String contextDigest, String nonce)
            throws Exception {
        String binding = ShipDigest.sha256("approval-binding".getBytes(StandardCharsets.UTF_8));
        PlanChallenge unsigned = new PlanChallenge(
                Interaction.SCHEMA_VERSION,
                runId,
                contextDigest,
                1,
                binding,
                binding,
                binding,
                binding,
                binding,
                binding,
                "plan.md",
                nonce,
                "hmac-sha256:" + "0".repeat(64));
        return new PlanChallenge(
                unsigned.schemaVersion(),
                unsigned.runId(),
                unsigned.contextDigest(),
                unsigned.ledgerRevision(),
                unsigned.ledgerDigest(),
                unsigned.requirementsDigest(),
                unsigned.policyDigest(),
                unsigned.baselineDigest(),
                unsigned.approvedDesignDigest(),
                unsigned.planDigest(),
                unsigned.planReference(),
                unsigned.nonce(),
                signer.sign(Interaction.planChallengeMacFields(unsigned)));
    }

    private static PlanResponse planResponse(
            ShipInteractionSigner signer, PlanChallenge challenge)
            throws Exception {
        PlanResponse unsigned = new PlanResponse(
                challenge.schemaVersion(),
                challenge.runId(),
                challenge.contextDigest(),
                challenge.ledgerRevision(),
                challenge.ledgerDigest(),
                challenge.requirementsDigest(),
                challenge.policyDigest(),
                challenge.baselineDigest(),
                challenge.approvedDesignDigest(),
                challenge.planDigest(),
                challenge.nonce(),
                PlanDecision.REQUEST_REQUIREMENTS_CHANGES,
                "Add an audit route requirement.",
                "uid:1000",
                "terminal-v1",
                "cli",
                NOW,
                "hmac-sha256:" + "0".repeat(64));
        return new PlanResponse(
                unsigned.schemaVersion(),
                unsigned.runId(),
                unsigned.contextDigest(),
                unsigned.ledgerRevision(),
                unsigned.ledgerDigest(),
                unsigned.requirementsDigest(),
                unsigned.policyDigest(),
                unsigned.baselineDigest(),
                unsigned.approvedDesignDigest(),
                unsigned.planDigest(),
                unsigned.nonce(),
                unsigned.decision(),
                unsigned.requestedChanges(),
                unsigned.controllerObservedProcessPrincipal(),
                unsigned.declaredCliUiProfile(),
                unsigned.channel(),
                unsigned.answeredAt(),
                signer.sign(Interaction.planResponseMacFields(unsigned)));
    }

    private static ShipController controller(Path state) {
        return new ShipController(state, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}

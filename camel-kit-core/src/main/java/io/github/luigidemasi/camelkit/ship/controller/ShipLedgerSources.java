package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.context.InitialContext;
import io.github.luigidemasi.camelkit.ship.controller.LedgerProvenanceValidator.DiscoveryAnswerAuthorization;
import io.github.luigidemasi.camelkit.ship.controller.LedgerProvenanceValidator.EvolutionAuthorization;
import io.github.luigidemasi.camelkit.ship.controller.LedgerProvenanceValidator.RequirementsChangeAuthorization;
import io.github.luigidemasi.camelkit.ship.controller.LedgerProvenanceValidator.SourceKind;
import io.github.luigidemasi.camelkit.ship.controller.LedgerProvenanceValidator.SourceMaterial;
import io.github.luigidemasi.camelkit.ship.controller.ProjectSourceManifest.FileSource;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryAnswer;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanResponse;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/** Loads the exact bounded source envelope supplied to provenance validation. */
final class ShipLedgerSources {

    private static final String INTERACTION_PREFIX = "controller:interaction-bundle#exchanges/";

    private ShipLedgerSources() {
    }

    static Snapshot load(
            ShipBlobStore blobs,
            ShipRunView run,
            ShipInteractionBundleService interactions)
            throws IOException {
        return load(
                blobs,
                run.context(),
                run.projectSourceManifest(),
                run.sourceDirectory(),
                run.interactionBundle(),
                interactions);
    }

    static Snapshot load(
            ShipBlobStore blobs,
            BlobReference contextReference,
            BlobReference manifestReference,
            java.nio.file.Path sourceDirectory,
            BlobReference interactionReference,
            ShipInteractionBundleService interactions)
            throws IOException {
        if (contextReference == null
                || manifestReference == null
                || sourceDirectory == null
                || interactionReference == null) {
            throw new IOException("Ship run lacks the protected ledger-source envelope");
        }
        List<SourceMaterial> sources = new ArrayList<>();
        addInitialContext(blobs, contextReference, sources);
        addProjectSources(blobs, manifestReference, sourceDirectory, sources);
        EvolutionAuthorization latest = addInteractionSources(
                interactions.read(blobs, interactionReference), sources);
        return new Snapshot(sources, latest);
    }

    private static void addInitialContext(
            ShipBlobStore blobs, BlobReference reference, List<SourceMaterial> target)
            throws IOException {
        if (!"initial-context".equals(reference.kind())) {
            throw new IOException("Ship context reference has the wrong protected kind");
        }
        InitialContext context = ShipJson.mapper().readValue(
                blobs.readBytes(reference, InitialContext.MAX_TOTAL_BYTES + 1024 * 1024),
                InitialContext.class);
        for (InitialContext.Source source : context.sources()) {
            target.add(new SourceMaterial(
                    SourceKind.INITIAL_CONTEXT,
                    source.id(),
                    source.provenance(),
                    source.digest(),
                    strictUtf8(source.content())));
        }
    }

    private static void addProjectSources(
            ShipBlobStore blobs,
            BlobReference manifestReference,
            java.nio.file.Path sourceDirectory,
            List<SourceMaterial> target)
            throws IOException {
        ProjectSourceManifest manifest = ShipJson.mapper().readValue(
                blobs.readBytes(manifestReference, ShipJson.MAX_DOCUMENT_BYTES),
                ProjectSourceManifest.class);
        for (FileSource source : manifest.sources()) {
            int maximum = Math.toIntExact(Math.min(
                    ShipTreePolicy.current().maxFileBytes(), Integer.MAX_VALUE));
            byte[] content = ProjectEvidenceFiles.readMaterial(
                    sourceDirectory, source.relativePath(), maximum);
            if (content.length != source.byteSize()
                    || !ShipDigest.sha256(content).equals(source.digest())) {
                throw new IOException("Immutable project source differs from its provenance manifest");
            }
            target.add(new SourceMaterial(
                    SourceKind.PROJECT,
                    source.sourceId(),
                    source.locator(),
                    source.digest(),
                    content));
        }
    }

    private static EvolutionAuthorization addInteractionSources(
            ShipInteractionBundle bundle, List<SourceMaterial> target) {
        EvolutionAuthorization latest = null;
        for (ShipInteractionBundle.Exchange exchange : bundle.exchanges()) {
            if (exchange.discovery() != null && exchange.discovery().answer() != null) {
                SourceMaterial source = interactionSource(
                        exchange.ordinal(),
                        "discovery/answer/response",
                        exchange.discovery().answer().response());
                target.add(source);
                latest = new DiscoveryAnswerAuthorization(exchange.discovery().answer(), source);
            } else if (exchange.design() != null
                    && exchange.design().response() != null) {
                DesignResponse response = exchange.design().response();
                if (response.requestedChanges() == null) {
                    latest = null;
                    continue;
                }
                SourceMaterial source = interactionSource(
                        exchange.ordinal(),
                        "design/response/requestedChanges",
                        response.requestedChanges());
                target.add(source);
                latest = response.decision() == DesignDecision.REQUEST_REQUIREMENTS_CHANGES
                        ? RequirementsChangeAuthorization.design(response, source)
                        : null;
            } else if (exchange.plan() != null
                    && exchange.plan().response() != null) {
                PlanResponse response = exchange.plan().response();
                if (response.requestedChanges() == null) {
                    latest = null;
                    continue;
                }
                SourceMaterial source = interactionSource(
                        exchange.ordinal(),
                        "plan/response/requestedChanges",
                        response.requestedChanges());
                target.add(source);
                latest = response.decision() == PlanDecision.REQUEST_REQUIREMENTS_CHANGES
                        ? RequirementsChangeAuthorization.plan(response, source)
                        : null;
            } else if (!exchange.pending()) {
                latest = null;
            }
        }
        return latest;
    }

    private static SourceMaterial interactionSource(
            int ordinal, String locatorSuffix, String value) {
        byte[] content = strictUtf8(value);
        String digest = ShipDigest.sha256(content);
        return new SourceMaterial(
                SourceKind.INTERACTION,
                "interaction-" + ordinal + '-' + digest.substring("sha256:".length()),
                INTERACTION_PREFIX + ordinal + '/' + locatorSuffix,
                digest,
                content);
    }

    private static byte[] strictUtf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Ledger source is not strict UTF-8", e);
        }
    }

    record Snapshot(
            List<SourceMaterial> sources,
            EvolutionAuthorization latestEvolutionAuthorization) {

        Snapshot {
            sources = List.copyOf(sources);
        }

        DiscoveryAnswerAuthorization latestDiscoveryAnswer() {
            return latestEvolutionAuthorization instanceof DiscoveryAnswerAuthorization answer
                    ? answer
                    : null;
        }

        RequirementsChangeAuthorization latestRequirementsChange() {
            return latestEvolutionAuthorization instanceof RequirementsChangeAuthorization change
                    ? change
                    : null;
        }

        DiscoveryAnswer latestAnswer() {
            DiscoveryAnswerAuthorization answer = latestDiscoveryAnswer();
            return answer == null ? null : answer.answer();
        }
    }
}

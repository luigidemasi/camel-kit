package io.github.luigidemasi.camelkit.ship.controller;

import java.util.List;

import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryAnswer;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DocumentConsentChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DocumentConsentResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.RemoteProviderConsentChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.RemoteProviderConsentResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverResponse;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;

/** Typed controller-authored payload values. Lifecycle transition authority remains in PR 2A. */
final class ShipEventPayloads {

    private ShipEventPayloads() {
    }

    sealed interface Payload
            permits RunCreated,
            ContextResolutionStarted,
            ContextRecorded,
            StageStarted,
            StageAccepted,
            DocumentConsentRequested,
            DocumentConsentRecorded,
            RemoteProviderConsentRequested,
            RemoteProviderConsentRecorded,
            DiscoveryQuestionPresented,
            DiscoveryAnswerRecorded,
            DesignApprovalRequested,
            DesignApprovalRecorded,
            PlanApprovalRequested,
            PlanApprovalRecorded,
            WaiverRequested,
            WaiverRecorded,
            Failure,
            StampRecorded,
            NoData {
    }

    record RunCreated(
            String projectRoot,
            String adapterId,
            BlobReference nativeSessionEvidence,
            BlobReference baselineSnapshot,
            BlobReference sourceSnapshot,
            BlobReference projectSourceManifest,
            String sourceDirectory)
            implements
                Payload {
    }

    record ContextResolutionStarted(BlobReference input) implements Payload {
    }

    record ContextRecorded(BlobReference context, BlobReference interactionBundle) implements Payload {
    }

    record StageStarted(
            ShipStage stage, BlobReference request, String attemptOutputDirectory)
            implements
                Payload {
    }

    record StageAccepted(
            ShipStage stage,
            BlobReference request,
            BlobReference result,
            List<ShipWorkspaceService.AcceptedArtifact> artifacts,
            BlobReference ledger,
            BlobReference artifactManifest,
            BlobReference catalogEvidence,
            BlobReference catalogUsage,
            String requirementsDigest,
            String designDigest,
            BlobReference candidateSnapshot,
            String candidateDirectory,
            List<BlobReference> evidence,
            BlobReference validationReport,
            StageStarted continuation)
            implements
                Payload {

        StageAccepted {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }

        StageAccepted(
                      ShipStage stage,
                      BlobReference request,
                      BlobReference result,
                      List<ShipWorkspaceService.AcceptedArtifact> artifacts,
                      BlobReference ledger,
                      BlobReference artifactManifest,
                      BlobReference catalogEvidence,
                      BlobReference catalogUsage,
                      String requirementsDigest,
                      String designDigest,
                      BlobReference candidateSnapshot,
                      String candidateDirectory,
                      StageStarted continuation) {
            this(stage, request, result, artifacts, ledger, artifactManifest, catalogEvidence,
                 catalogUsage, requirementsDigest, designDigest, candidateSnapshot,
                 candidateDirectory, List.of(), null, continuation);
        }
    }

    record DocumentConsentRequested(
            DocumentConsentChallenge challenge, BlobReference interactionBundle)
            implements
                Payload {
    }

    record DocumentConsentRecorded(
            DocumentConsentResponse response,
            BlobReference responseReference,
            BlobReference interactionBundle)
            implements
                Payload {
    }

    record RemoteProviderConsentRequested(
            RemoteProviderConsentChallenge challenge, BlobReference interactionBundle)
            implements
                Payload {
    }

    record RemoteProviderConsentRecorded(
            RemoteProviderConsentResponse response,
            BlobReference responseReference,
            BlobReference interactionBundle,
            StageStarted continuation)
            implements
                Payload {
    }

    record DiscoveryQuestionPresented(
            BlobReference request,
            BlobReference result,
            BlobReference ledger,
            DiscoveryChallenge challenge,
            BlobReference interactionBundle)
            implements
                Payload {
    }

    record DiscoveryAnswerRecorded(
            DiscoveryAnswer answer,
            BlobReference answerReference,
            BlobReference request,
            BlobReference interactionBundle)
            implements
                Payload {
    }

    record DesignApprovalRequested(
            DesignChallenge challenge, BlobReference interactionBundle)
            implements
                Payload {
    }

    record DesignApprovalRecorded(
            DesignResponse response,
            BlobReference responseReference,
            BlobReference design,
            BlobReference interactionBundle,
            StageStarted continuation)
            implements
                Payload {
    }

    record PlanApprovalRequested(
            PlanChallenge challenge, BlobReference interactionBundle)
            implements
                Payload {
    }

    record PlanApprovalRecorded(
            PlanResponse response,
            BlobReference responseReference,
            BlobReference plan,
            BlobReference interactionBundle,
            StageStarted continuation)
            implements
                Payload {
    }

    record WaiverRequested(WaiverChallenge challenge, BlobReference interactionBundle)
            implements
                Payload {
    }

    record WaiverRecorded(
            WaiverResponse response,
            BlobReference responseReference,
            BlobReference interactionBundle,
            BlobReference stamp)
            implements
                Payload {

        WaiverRecorded(
                       WaiverResponse response,
                       BlobReference responseReference,
                       BlobReference interactionBundle) {
            this(response, responseReference, interactionBundle, null);
        }
    }

    record Failure(
            ShipStage failedStage,
            String code,
            String message,
            BlobReference request,
            BlobReference result,
            BlobReference validationReport,
            BlobReference stamp,
            BlobReference interactionBundle,
            List<BlobReference> evidence)
            implements
                Payload {

        Failure(
                ShipStage failedStage,
                String code,
                String message,
                BlobReference request,
                BlobReference result,
                BlobReference validationReport,
                BlobReference stamp,
                BlobReference interactionBundle) {
            this(failedStage, code, message, request, result, validationReport, stamp,
                 interactionBundle, List.of());
        }

        Failure {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    record StampRecorded(
            BlobReference stamp,
            BlobReference publishedSnapshot,
            BlobReference validationReport)
            implements
                Payload {
    }

    record NoData() implements Payload {
    }

}

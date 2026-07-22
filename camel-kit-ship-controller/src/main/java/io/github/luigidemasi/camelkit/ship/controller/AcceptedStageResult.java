package io.github.luigidemasi.camelkit.ship.controller;

import java.util.Objects;

/**
 * Accepted stage-result capabilities consumed by the lifecycle reducer.
 *
 * <p>
 * Worker-stage results require accepted evidence; stamp completion requires controller-owned deterministic Stamp
 * verification. The package-private factory below is the controller orchestration seam; package isolation is API
 * hygiene only, not an authentication boundary.
 */
sealed abstract class AcceptedStageResult<T>
        permits AcceptedContextResult,
        AcceptedDiscoveryCandidate,
        AcceptedDiscoveryContinuation,
        AcceptedReviewResult,
        AcceptedDesignGapResult,
        AcceptedRequirementsResult,
        AcceptedDesignResult,
        AcceptedPlanResult,
        AcceptedExecutionResult,
        AcceptedValidationResult,
        AcceptedStampCompletion {

    private final Attempt attempt;
    private final T value;

    AcceptedStageResult(Attempt attempt, T value) {
        this.attempt = Objects.requireNonNull(attempt, "attempt");
        this.value = Objects.requireNonNull(value, "value");
    }

    final Attempt attempt() {
        return attempt;
    }

    final T value() {
        return value;
    }
}

final class AcceptedDiscoveryCandidate extends AcceptedStageResult<ContentId> {
    private AcceptedDiscoveryCandidate(Attempt attempt, ContentId candidate) {
        super(attempt, candidate);
    }

    static AcceptedDiscoveryCandidate issue(
            AcceptedStageResultFactory.IssueKey key, Attempt attempt, ContentId candidate) {
        AcceptedStageResultFactory.requireKey(key);
        return new AcceptedDiscoveryCandidate(attempt, candidate);
    }
}

final class AcceptedDiscoveryContinuation extends AcceptedStageResult<ContentId> {
    private AcceptedDiscoveryContinuation(Attempt attempt, ContentId continuation) {
        super(attempt, continuation);
    }

    static AcceptedDiscoveryContinuation issue(
            AcceptedStageResultFactory.IssueKey key, Attempt attempt, ContentId continuation) {
        AcceptedStageResultFactory.requireKey(key);
        return new AcceptedDiscoveryContinuation(attempt, continuation);
    }
}

record GapReviewResult(ContentId candidate, ContentId feedback) {
    GapReviewResult {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(feedback, "feedback");
    }
}

final class AcceptedReviewResult extends AcceptedStageResult<GapReviewResult> {
    private AcceptedReviewResult(Attempt attempt, GapReviewResult result) {
        super(attempt, result);
    }

    static AcceptedReviewResult issue(
            AcceptedStageResultFactory.IssueKey key, Attempt attempt, GapReviewResult result) {
        AcceptedStageResultFactory.requireKey(key);
        return new AcceptedReviewResult(attempt, result);
    }
}

record DesignGapResult(ContentId candidate, ContentId feedback) {
    DesignGapResult {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(feedback, "feedback");
    }
}

final class AcceptedDesignGapResult extends AcceptedStageResult<DesignGapResult> {
    private AcceptedDesignGapResult(Attempt attempt, DesignGapResult result) {
        super(attempt, result);
    }

    static AcceptedDesignGapResult issue(
            AcceptedStageResultFactory.IssueKey key, Attempt attempt, DesignGapResult result) {
        AcceptedStageResultFactory.requireKey(key);
        return new AcceptedDesignGapResult(attempt, result);
    }
}

final class AcceptedContextResult extends AcceptedStageResult<ContentId> {
    private AcceptedContextResult(Attempt attempt, ContentId context) {
        super(attempt, context);
    }

    static AcceptedContextResult issue(
            AcceptedStageResultFactory.IssueKey key, Attempt attempt, ContentId context) {
        AcceptedStageResultFactory.requireKey(key);
        return new AcceptedContextResult(attempt, context);
    }
}

final class AcceptedRequirementsResult extends AcceptedStageResult<AuthorityBasis> {
    private AcceptedRequirementsResult(Attempt attempt, AuthorityBasis basis) {
        super(attempt, basis);
    }

    static AcceptedRequirementsResult issue(
            AcceptedStageResultFactory.IssueKey key, Attempt attempt, AuthorityBasis basis) {
        AcceptedStageResultFactory.requireKey(key);
        return new AcceptedRequirementsResult(attempt, basis);
    }
}

final class AcceptedDesignResult extends AcceptedStageResult<ContentId> {
    private AcceptedDesignResult(Attempt attempt, ContentId design) {
        super(attempt, design);
    }

    static AcceptedDesignResult issue(
            AcceptedStageResultFactory.IssueKey key, Attempt attempt, ContentId design) {
        AcceptedStageResultFactory.requireKey(key);
        return new AcceptedDesignResult(attempt, design);
    }
}

final class AcceptedPlanResult extends AcceptedStageResult<ContentId> {
    private AcceptedPlanResult(Attempt attempt, ContentId plan) {
        super(attempt, plan);
    }

    static AcceptedPlanResult issue(
            AcceptedStageResultFactory.IssueKey key, Attempt attempt, ContentId plan) {
        AcceptedStageResultFactory.requireKey(key);
        return new AcceptedPlanResult(attempt, plan);
    }
}

final class AcceptedExecutionResult extends AcceptedStageResult<ContentId> {
    private AcceptedExecutionResult(Attempt attempt, ContentId execution) {
        super(attempt, execution);
    }

    static AcceptedExecutionResult issue(
            AcceptedStageResultFactory.IssueKey key, Attempt attempt, ContentId execution) {
        AcceptedStageResultFactory.requireKey(key);
        return new AcceptedExecutionResult(attempt, execution);
    }
}

final class AcceptedValidationResult extends AcceptedStageResult<ContentId> {
    private AcceptedValidationResult(Attempt attempt, ContentId validation) {
        super(attempt, validation);
    }

    static AcceptedValidationResult issue(
            AcceptedStageResultFactory.IssueKey key, Attempt attempt, ContentId validation) {
        AcceptedStageResultFactory.requireKey(key);
        return new AcceptedValidationResult(attempt, validation);
    }
}

final class AcceptedStampCompletion extends AcceptedStageResult<ContentId> {
    private AcceptedStampCompletion(Attempt attempt, ContentId evidence) {
        super(attempt, evidence);
    }

    static AcceptedStampCompletion issue(
            AcceptedStageResultFactory.IssueKey key, Attempt attempt, ContentId evidence) {
        AcceptedStageResultFactory.requireKey(key);
        return new AcceptedStampCompletion(attempt, evidence);
    }
}

/** Controller-only construction seam for evidence-backed lifecycle capabilities. */
final class AcceptedStageResultFactory {

    private static final IssueKey KEY = new IssueKey();

    private AcceptedStageResultFactory() {
    }

    static AcceptedContextResult context(Attempt attempt, ContentId context) {
        return AcceptedContextResult.issue(KEY, attempt, context);
    }

    static AcceptedDiscoveryCandidate discoveryCandidate(Attempt attempt, ContentId candidate) {
        return AcceptedDiscoveryCandidate.issue(KEY, attempt, candidate);
    }

    static AcceptedDiscoveryContinuation discoveryContinuation(
            Attempt attempt, ContentId continuation) {
        return AcceptedDiscoveryContinuation.issue(KEY, attempt, continuation);
    }

    static AcceptedReviewResult review(
            Attempt attempt, ContentId candidate, ContentId feedback) {
        return AcceptedReviewResult.issue(KEY, attempt, new GapReviewResult(candidate, feedback));
    }

    static AcceptedDesignGapResult designGaps(
            Attempt attempt, ContentId candidate, ContentId feedback) {
        return AcceptedDesignGapResult.issue(KEY, attempt, new DesignGapResult(candidate, feedback));
    }

    static AcceptedRequirementsResult requirements(Attempt attempt, AuthorityBasis basis) {
        return AcceptedRequirementsResult.issue(KEY, attempt, basis);
    }

    static AcceptedDesignResult design(Attempt attempt, ContentId design) {
        return AcceptedDesignResult.issue(KEY, attempt, design);
    }

    static AcceptedPlanResult plan(Attempt attempt, ContentId plan) {
        return AcceptedPlanResult.issue(KEY, attempt, plan);
    }

    static AcceptedExecutionResult execution(Attempt attempt, ContentId execution) {
        return AcceptedExecutionResult.issue(KEY, attempt, execution);
    }

    static AcceptedValidationResult validation(Attempt attempt, ContentId validation) {
        return AcceptedValidationResult.issue(KEY, attempt, validation);
    }

    static AcceptedStampCompletion stamp(Attempt attempt, ContentId evidence) {
        return AcceptedStampCompletion.issue(KEY, attempt, evidence);
    }

    static void requireKey(IssueKey key) {
        if (key != KEY) {
            throw new IllegalArgumentException("Accepted stage-result capabilities require controller issuance");
        }
    }

    static final class IssueKey {

        private IssueKey() {
        }
    }
}

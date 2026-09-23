package com.aifishing.guidance.attribution;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.GuidanceRejectReason;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.ReflectionCauseKind;
import com.aifishing.guidance.contracts.ReflectionClaimKind;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReflectionEvaluation {

    private ReflectionEvaluation() {
    }

    public static Result evaluate(
            Instant now,
            GuidanceProperties.Attribution cfg,
            List<ComputedAttribution> attributions,
            List<FeedbackObservation> feedbacks,
            List<ExistingReflection> existing
    ) {
        List<ComputedReflection> reflections = new ArrayList<>();
        boolean consecutiveFailure = false;
        int threshold = cfg == null ? 3 : cfg.getConsecutiveFailureThreshold();
        List<ComputedAttribution> ordered = new ArrayList<>(attributions == null ? List.of() : attributions);
        ordered.sort(Comparator.comparing(ComputedAttribution::attributedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<AttributionDimension, List<ComputedAttribution>> byDimension = new EnumMap<>(AttributionDimension.class);
        for (ComputedAttribution row : ordered) {
            if (row.attributionDimension() == null) {
                continue;
            }
            byDimension.computeIfAbsent(row.attributionDimension(), key -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<AttributionDimension, List<ComputedAttribution>> entry : byDimension.entrySet()) {
            int streak = 0;
            ComputedAttribution thirdFailure = null;
            for (ComputedAttribution row : entry.getValue()) {
                if (!row.followedRecommendation()) {
                    continue;
                }
                if (AttributionWindows.strategySuccess(row.outcomeKind())) {
                    streak = 0;
                    thirdFailure = null;
                    continue;
                }
                if (row.outcomeKind() != OutcomeKind.NO_BITE) {
                    continue;
                }
                streak++;
                if (streak == threshold) {
                    thirdFailure = row;
                }
            }
            if (thirdFailure != null) {
                String text = "Followed " + entry.getKey() + " recommendation had no FISH_ON in "
                        + threshold + " consecutive attribution windows.";
                if (!alreadyWritten(existing, ReflectionCauseKind.STRATEGY_FAILURE, text)) {
                    reflections.add(new ComputedReflection(
                            ReflectionClaimKind.OBSERVED_FACT,
                            ReflectionCauseKind.STRATEGY_FAILURE,
                            text,
                            thirdFailure.deliveredDecisionId(),
                            now
                    ));
                    consecutiveFailure = true;
                }
            }
        }

        List<FeedbackObservation> orderedFeedback = new ArrayList<>(feedbacks == null ? List.of() : feedbacks);
        orderedFeedback.sort(Comparator.comparing(FeedbackObservation::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())));
        int rejectStreak = 0;
        GuidanceRejectReason rejectKind = null;
        FeedbackObservation thirdReject = null;
        for (FeedbackObservation feedback : orderedFeedback) {
            if (feedback.status() != FeedbackStatus.REJECTED) {
                rejectStreak = 0;
                rejectKind = null;
                continue;
            }
            String text = "User rejected advice"
                    + (feedback.rejectReason() == null ? "." : " (" + feedback.rejectReason() + ").");
            if (!alreadyWritten(existing, ReflectionCauseKind.USER_PREFERENCE_CONFLICT, text)
                    && !alreadyWritten(existing, ReflectionCauseKind.USER_PREFERENCE_CONFLICT, feedback.feedbackId())) {
                reflections.add(new ComputedReflection(
                        ReflectionClaimKind.USER_PREFERENCE,
                        ReflectionCauseKind.USER_PREFERENCE_CONFLICT,
                        text,
                        feedback.deliveredDecisionId(),
                        feedback.occurredAt()
                ));
            }
            if (rejectKind != null && rejectKind == feedback.rejectReason()) {
                rejectStreak++;
            } else {
                rejectKind = feedback.rejectReason();
                rejectStreak = 1;
            }
            if (rejectStreak == threshold) {
                thirdReject = feedback;
            }
        }
        if (thirdReject != null) {
            String text = "Repeated " + thirdReject.rejectReason() + " rejects are a preference conflict, not strategy failure.";
            if (!alreadyWritten(existing, ReflectionCauseKind.USER_PREFERENCE_CONFLICT, text)) {
                reflections.add(new ComputedReflection(
                        ReflectionClaimKind.USER_PREFERENCE,
                        ReflectionCauseKind.USER_PREFERENCE_CONFLICT,
                        text,
                        thirdReject.deliveredDecisionId(),
                        thirdReject.occurredAt()
                ));
            }
        }
        return new Result(List.copyOf(reflections), consecutiveFailure);
    }

    private static boolean alreadyWritten(
            List<ExistingReflection> existing,
            ReflectionCauseKind cause,
            String marker
    ) {
        if (existing == null || marker == null) {
            return false;
        }
        for (ExistingReflection row : existing) {
            if (row.causeKind() == cause && marker.equals(row.text())) {
                return true;
            }
            if (row.causeKind() == cause && row.marker() != null && marker.equals(row.marker().toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean alreadyWritten(
            List<ExistingReflection> existing,
            ReflectionCauseKind cause,
            UUID marker
    ) {
        return marker != null && alreadyWritten(existing, cause, marker.toString());
    }

    public record Result(List<ComputedReflection> reflections, boolean consecutiveFailure) {
        public Result {
            reflections = List.copyOf(reflections == null ? List.of() : reflections);
        }
    }

    public record ComputedReflection(
            ReflectionClaimKind claimKind,
            ReflectionCauseKind causeKind,
            String text,
            UUID deliveredDecisionId,
            Instant createdAt
    ) {
    }

    public record FeedbackObservation(
            UUID feedbackId,
            UUID deliveredDecisionId,
            FeedbackStatus status,
            GuidanceRejectReason rejectReason,
            Instant occurredAt
    ) {
    }

    public record ExistingReflection(
            ReflectionCauseKind causeKind,
            String text,
            UUID marker
    ) {
        public ExistingReflection(ReflectionCauseKind causeKind, String text) {
            this(causeKind, text, null);
        }
    }

}

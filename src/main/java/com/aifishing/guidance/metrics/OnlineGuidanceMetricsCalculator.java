package com.aifishing.guidance.metrics;

import com.aifishing.guidance.attribution.AttributionWindows;
import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceSuccessKind;
import com.aifishing.guidance.contracts.OnlineGuidanceMetrics;
import com.aifishing.guidance.contracts.OutcomeKind;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Derives {@link OnlineGuidanceMetrics} from raw events. SUM counts first;
 * rates stay null when the denominator is unknown. Do not compare raw binary
 * success rates across different action windows — use effort-normalized rates
 * or same-dimension slices only.
 */
public final class OnlineGuidanceMetricsCalculator {

    private OnlineGuidanceMetricsCalculator() {
    }

    public static OnlineOutcomeSnapshot compute(
            Instant windowStart,
            Instant windowEnd,
            AttributionDimension dimension,
            List<OnlineOutcomeEvent> attributions,
            List<FeedbackObservation> feedbacks,
            List<FollowObservation> follows,
            List<RecommendationEffortWindow> recWindows,
            List<EffortInterval> fishingEffort,
            OnlineOutcomeSafetyCounts safety
    ) {
        List<MergedOutcome> merged = mergeIdentities(filter(attributions, dimension));
        EnumMap<GuidanceSuccessKind, Integer> kinds = new EnumMap<>(GuidanceSuccessKind.class);
        for (GuidanceSuccessKind kind : GuidanceSuccessKind.values()) {
            kinds.put(kind, 0);
        }
        int landed = 0;
        int lost = 0;
        Set<DecisionDimension> followedSlices = new HashSet<>();
        for (MergedOutcome row : merged) {
            GuidanceSuccessKind kind = GuidanceSuccessClassifier.classify(
                    row.identity.deliveredDecisionId(),
                    row.followed,
                    row.outcomeKind
            );
            kinds.put(kind, kinds.get(kind) + 1);
            if (GuidanceSuccessClassifier.landing(row.outcomeKind)) {
                landed++;
            }
            if (GuidanceSuccessClassifier.lostAfterHook(row.outcomeKind)) {
                lost++;
            }
            if (row.followed && row.identity.deliveredDecisionId() != null && row.identity.attributionDimension() != null) {
                followedSlices.add(new DecisionDimension(
                        row.identity.deliveredDecisionId(),
                        row.identity.attributionDimension()
                ));
            }
        }

        AcceptanceStats acceptance = acceptance(filterFeedback(feedbacks, attributions, dimension));
        FollowStats followStats = followThrough(filterFollows(follows, dimension), followedSlices);
        OverrideStats override = overrides(
                filterFeedback(feedbacks, attributions, dimension),
                filterFollows(follows, dimension)
        );
        long effortSeconds = effortSeconds(
                filterWindows(recWindows, dimension),
                fishingEffort,
                windowStart,
                windowEnd
        );
        OnlineOutcomeRawCounts raw = new OnlineOutcomeRawCounts(
                kinds.get(GuidanceSuccessKind.FISH_ON_SUCCESS),
                kinds.get(GuidanceSuccessKind.BITE_SIGNAL_ONLY),
                kinds.get(GuidanceSuccessKind.NO_FISH_SIGNAL),
                kinds.get(GuidanceSuccessKind.NOT_FOLLOWED),
                kinds.get(GuidanceSuccessKind.UNATTRIBUTED),
                followStats.followedCount,
                acceptance.accepted,
                acceptance.partial,
                acceptance.rejected,
                override.overrideCount,
                acceptance.feedbackCount,
                followStats.eligibleCount,
                override.eligibleCount,
                effortSeconds,
                landed,
                lost,
                safety == null ? OnlineOutcomeSafetyCounts.empty() : safety
        );
        return new OnlineOutcomeSnapshot(
                derive(windowStart, windowEnd, dimension, raw),
                new LandingAnalytics(landed, lost),
                raw
        );
    }

    /**
     * SUM raw counts (e.g. after a rollup read) then derive rates. Never invent 0 rates.
     */
    public static OnlineGuidanceMetrics derive(
            Instant windowStart,
            Instant windowEnd,
            AttributionDimension dimension,
            OnlineOutcomeRawCounts raw
    ) {
        OnlineOutcomeSafetyCounts safety = raw.safetyOrEmpty();
        return new OnlineGuidanceMetrics(
                GuidanceSchemaVersion.VALUE,
                windowStart,
                windowEnd,
                dimension,
                raw.fishOnSuccessCount(),
                raw.biteSignalOnlyCount(),
                raw.noFishSignalCount(),
                raw.notFollowedCount(),
                raw.unattributedCount(),
                raw.followedRecommendationCount(),
                rate(raw.explicitAcceptedCount() + raw.explicitPartialCount(), raw.feedbackCount()),
                rate(raw.followedRecommendationCount(), raw.followThroughEligibleCount()),
                rate(raw.rejectCount(), raw.feedbackCount()),
                rate(raw.overrideCount(), raw.overrideEligibleCount()),
                rate(safety.candidateUnsafeCount(), safety.candidateCount()),
                rate(safety.candidateInvalidWaypointCount(), safety.candidateCount()),
                rate(safety.validatorInterceptionCount(), safety.candidateCount()),
                rate(safety.unsafeDeliveredCount(), safety.deliveredCount()),
                rate(safety.invalidDeliveredWaypointCount(), safety.deliveredCount()),
                raw.effectiveFishingEffortSeconds(),
                perFishingHour(raw.fishOnSuccessCount(), raw.effectiveFishingEffortSeconds()),
                perFishingHour(raw.fishOnSuccessCount() + raw.biteSignalOnlyCount(), raw.effectiveFishingEffortSeconds())
        );
    }

    static Double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return (double) numerator / (double) denominator;
    }

    static Double perFishingHour(long events, long effortSeconds) {
        if (effortSeconds <= 0) {
            return null;
        }
        return events / (effortSeconds / 3600.0);
    }

    static List<MergedOutcome> mergeIdentities(List<OnlineOutcomeEvent> attributions) {
        Map<OnlineOutcomeIdentity, MergedOutcome> byId = new HashMap<>();
        for (OnlineOutcomeEvent event : attributions == null ? List.<OnlineOutcomeEvent>of() : attributions) {
            if (event == null) {
                continue;
            }
            OnlineOutcomeIdentity identity = new OnlineOutcomeIdentity(
                    event.deliveredDecisionId(),
                    event.attributionDimension(),
                    event.fishInteractionId()
            );
            MergedOutcome current = byId.get(identity);
            if (current == null) {
                byId.put(identity, new MergedOutcome(
                        identity,
                        event.outcomeKind(),
                        event.followedRecommendation(),
                        event.attributedAt()
                ));
                continue;
            }
            OutcomeKind kind = current.outcomeKind;
            Instant at = current.attributedAt;
            if (better(event.outcomeKind(), event.attributedAt(), kind, at)) {
                kind = event.outcomeKind();
                at = event.attributedAt();
            }
            byId.put(identity, new MergedOutcome(
                    identity,
                    kind,
                    current.followed || event.followedRecommendation(),
                    at
            ));
        }
        return List.copyOf(byId.values());
    }

    private static boolean better(OutcomeKind candidate, Instant candidateAt, OutcomeKind current, Instant currentAt) {
        int candidateRank = AttributionWindows.outcomeRank(candidate);
        int currentRank = AttributionWindows.outcomeRank(current);
        if (candidateRank > currentRank) {
            return true;
        }
        if (candidateRank < currentRank) {
            return false;
        }
        if (candidateAt == null) {
            return false;
        }
        return currentAt == null || candidateAt.isAfter(currentAt);
    }

    private static List<OnlineOutcomeEvent> filter(List<OnlineOutcomeEvent> events, AttributionDimension dimension) {
        if (events == null || events.isEmpty() || dimension == null) {
            return events == null ? List.of() : events;
        }
        List<OnlineOutcomeEvent> filtered = new ArrayList<>();
        for (OnlineOutcomeEvent event : events) {
            if (event != null && event.attributionDimension() == dimension) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    private static List<FollowObservation> filterFollows(List<FollowObservation> follows, AttributionDimension dimension) {
        if (follows == null || follows.isEmpty() || dimension == null) {
            return follows == null ? List.of() : follows;
        }
        List<FollowObservation> filtered = new ArrayList<>();
        for (FollowObservation follow : follows) {
            if (follow != null && follow.attributionDimension() == dimension) {
                filtered.add(follow);
            }
        }
        return filtered;
    }

    private static List<RecommendationEffortWindow> filterWindows(
            List<RecommendationEffortWindow> windows,
            AttributionDimension dimension
    ) {
        if (windows == null || windows.isEmpty() || dimension == null) {
            return windows == null ? List.of() : windows;
        }
        List<RecommendationEffortWindow> filtered = new ArrayList<>();
        for (RecommendationEffortWindow window : windows) {
            if (window != null && window.attributionDimension() == dimension) {
                filtered.add(window);
            }
        }
        return filtered;
    }

    private static List<FeedbackObservation> filterFeedback(
            List<FeedbackObservation> feedbacks,
            List<OnlineOutcomeEvent> attributions,
            AttributionDimension dimension
    ) {
        if (feedbacks == null || feedbacks.isEmpty()) {
            return List.of();
        }
        if (dimension == null) {
            return feedbacks;
        }
        Set<UUID> decisions = new HashSet<>();
        for (OnlineOutcomeEvent event : attributions == null ? List.<OnlineOutcomeEvent>of() : attributions) {
            if (event != null && event.attributionDimension() == dimension && event.deliveredDecisionId() != null) {
                decisions.add(event.deliveredDecisionId());
            }
        }
        List<FeedbackObservation> filtered = new ArrayList<>();
        for (FeedbackObservation feedback : feedbacks) {
            if (feedback != null && decisions.contains(feedback.deliveredDecisionId())) {
                filtered.add(feedback);
            }
        }
        return filtered;
    }

    private static AcceptanceStats acceptance(List<FeedbackObservation> feedbacks) {
        Map<UUID, FeedbackObservation> latest = latestFeedback(feedbacks);
        int accepted = 0;
        int partial = 0;
        int rejected = 0;
        for (FeedbackObservation row : latest.values()) {
            if (row.status() == FeedbackStatus.ACCEPTED) {
                accepted++;
            } else if (row.status() == FeedbackStatus.PARTIALLY_FOLLOWED) {
                partial++;
            } else if (row.status() == FeedbackStatus.REJECTED) {
                rejected++;
            }
        }
        return new AcceptanceStats(accepted, partial, rejected, latest.size());
    }

    private static FollowStats followThrough(List<FollowObservation> follows, Set<DecisionDimension> followedFromOutcomes) {
        Set<DecisionDimension> eligible = new HashSet<>(followedFromOutcomes);
        Set<DecisionDimension> followed = new HashSet<>(followedFromOutcomes);
        for (FollowObservation follow : follows == null ? List.<FollowObservation>of() : follows) {
            if (follow == null || follow.deliveredDecisionId() == null || follow.attributionDimension() == null) {
                continue;
            }
            DecisionDimension key = new DecisionDimension(follow.deliveredDecisionId(), follow.attributionDimension());
            eligible.add(key);
            if (follow.followedRecommendation()) {
                followed.add(key);
            }
        }
        return new FollowStats(followed.size(), eligible.size());
    }

    private static OverrideStats overrides(List<FeedbackObservation> feedbacks, List<FollowObservation> follows) {
        Map<UUID, FeedbackObservation> latest = latestFeedback(feedbacks);
        Map<UUID, Boolean> anyFollowed = new HashMap<>();
        Map<UUID, Boolean> executedOther = new HashMap<>();
        for (FollowObservation follow : follows == null ? List.<FollowObservation>of() : follows) {
            if (follow == null || follow.deliveredDecisionId() == null) {
                continue;
            }
            anyFollowed.merge(follow.deliveredDecisionId(), follow.followedRecommendation(), Boolean::logicalOr);
            executedOther.merge(follow.deliveredDecisionId(), follow.executedOtherAction(), Boolean::logicalOr);
        }
        Set<UUID> eligible = new HashSet<>();
        eligible.addAll(latest.keySet());
        eligible.addAll(anyFollowed.keySet());
        eligible.addAll(executedOther.keySet());
        int overrides = 0;
        for (UUID decisionId : eligible) {
            FeedbackObservation feedback = latest.get(decisionId);
            boolean accepted = explicitAcceptance(feedback);
            boolean followed = Boolean.TRUE.equals(anyFollowed.get(decisionId));
            boolean other = Boolean.TRUE.equals(executedOther.get(decisionId));
            if (accepted && !followed) {
                overrides++;
            } else if (!accepted && other) {
                overrides++;
            }
        }
        return new OverrideStats(overrides, eligible.size());
    }

    private static boolean explicitAcceptance(FeedbackObservation feedback) {
        return feedback != null
                && (feedback.status() == FeedbackStatus.ACCEPTED
                || feedback.status() == FeedbackStatus.PARTIALLY_FOLLOWED);
    }

    private static Map<UUID, FeedbackObservation> latestFeedback(List<FeedbackObservation> feedbacks) {
        Map<UUID, FeedbackObservation> latest = new HashMap<>();
        for (FeedbackObservation feedback : feedbacks == null ? List.<FeedbackObservation>of() : feedbacks) {
            if (feedback == null || feedback.deliveredDecisionId() == null || feedback.status() == null) {
                continue;
            }
            FeedbackObservation current = latest.get(feedback.deliveredDecisionId());
            if (current == null || occurredAfter(feedback.occurredAt(), current.occurredAt())) {
                latest.put(feedback.deliveredDecisionId(), feedback);
            }
        }
        return latest;
    }

    private static boolean occurredAfter(Instant candidate, Instant current) {
        if (candidate == null) {
            return false;
        }
        return current == null || candidate.isAfter(current);
    }

    static long effortSeconds(
            List<RecommendationEffortWindow> recWindows,
            List<EffortInterval> fishingEffort,
            Instant windowStart,
            Instant windowEnd
    ) {
        List<EffortInterval> overlaps = new ArrayList<>();
        for (RecommendationEffortWindow rec : recWindows == null ? List.<RecommendationEffortWindow>of() : recWindows) {
            if (rec == null || rec.start() == null || rec.end() == null || rec.fishingSessionId() == null) {
                continue;
            }
            for (EffortInterval fishing : fishingEffort == null ? List.<EffortInterval>of() : fishingEffort) {
                if (fishing == null || !rec.fishingSessionId().equals(fishing.fishingSessionId())) {
                    continue;
                }
                Instant start = max(rec.start(), fishing.start(), windowStart);
                Instant end = min(rec.end(), fishing.end(), windowEnd);
                if (start != null && end != null && end.isAfter(start)) {
                    overlaps.add(new EffortInterval(rec.fishingSessionId(), start, end));
                }
            }
        }
        return unionSeconds(overlaps);
    }

    static long unionSeconds(List<EffortInterval> intervals) {
        if (intervals == null || intervals.isEmpty()) {
            return 0L;
        }
        List<EffortInterval> ordered = new ArrayList<>();
        for (EffortInterval interval : intervals) {
            if (interval != null && interval.start() != null && interval.end() != null && interval.end().isAfter(interval.start())) {
                ordered.add(interval);
            }
        }
        ordered.sort(Comparator
                .comparing(EffortInterval::fishingSessionId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EffortInterval::start));
        long total = 0L;
        EffortInterval open = null;
        for (EffortInterval interval : ordered) {
            if (open == null) {
                open = interval;
                continue;
            }
            boolean sameSession = open.fishingSessionId() == null
                    ? interval.fishingSessionId() == null
                    : open.fishingSessionId().equals(interval.fishingSessionId());
            if (sameSession && !interval.start().isAfter(open.end())) {
                if (interval.end().isAfter(open.end())) {
                    open = new EffortInterval(open.fishingSessionId(), open.start(), interval.end());
                }
                continue;
            }
            total += Duration.between(open.start(), open.end()).getSeconds();
            open = interval;
        }
        if (open != null) {
            total += Duration.between(open.start(), open.end()).getSeconds();
        }
        return total;
    }

    private static Instant max(Instant a, Instant b, Instant c) {
        Instant value = a;
        if (b != null && (value == null || b.isAfter(value))) {
            value = b;
        }
        if (c != null && (value == null || c.isAfter(value))) {
            value = c;
        }
        return value;
    }

    private static Instant min(Instant a, Instant b, Instant c) {
        Instant value = a;
        if (b != null && (value == null || b.isBefore(value))) {
            value = b;
        }
        if (c != null && (value == null || c.isBefore(value))) {
            value = c;
        }
        return value;
    }

    record MergedOutcome(
            OnlineOutcomeIdentity identity,
            OutcomeKind outcomeKind,
            boolean followed,
            Instant attributedAt
    ) {
    }

    private record DecisionDimension(UUID deliveredDecisionId, AttributionDimension dimension) {
    }

    private record AcceptanceStats(int accepted, int partial, int rejected, int feedbackCount) {
    }

    private record FollowStats(int followedCount, int eligibleCount) {
    }

    private record OverrideStats(int overrideCount, int eligibleCount) {
    }
}

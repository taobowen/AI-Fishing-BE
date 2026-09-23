package com.aifishing.guidance.attribution;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecommendationRole;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OutcomeAttributionCalculator {

    private OutcomeAttributionCalculator() {
    }

    public static List<ComputedAttribution> compute(
            Instant now,
            GuidanceProperties.Attribution cfg,
            List<DeliveredSnapshot> decisions,
            List<ObservedUserAction> actions,
            List<OutcomeSignal> signals
    ) {
        if (now == null || cfg == null) {
            return List.of();
        }
        List<DeliveredSnapshot> ordered = new ArrayList<>(decisions == null ? List.of() : decisions);
        ordered.sort(Comparator.comparing(DeliveredSnapshot::deliveredAt, Comparator.nullsLast(Comparator.naturalOrder())));
        List<ObservedUserAction> observed = new ArrayList<>(actions == null ? List.of() : actions);
        observed.sort(Comparator.comparing(ObservedUserAction::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())));
        List<OpenWindow> windows = new ArrayList<>();
        for (DeliveredSnapshot snapshot : ordered) {
            for (RecommendedSlice slice : AttributionWindows.slices(snapshot)) {
                OpenWindow window = openWindow(now, cfg, snapshot, slice, observed, ordered);
                if (window != null) {
                    windows.add(window);
                }
            }
        }
        List<OutcomeSignal> incoming = signals == null ? List.of() : signals;
        List<OutcomeSignal> fishSignals = new ArrayList<>();
        for (OutcomeSignal signal : incoming) {
            if (signal != null && signal.occurredAt() != null && signal.kind() != null) {
                fishSignals.add(signal);
            }
        }
        fishSignals.sort(Comparator.comparing(OutcomeSignal::occurredAt));
        Map<AttributionKey, ComputedAttribution> byKey = new HashMap<>();
        for (OpenWindow window : windows) {
            for (OutcomeSignal signal : fishSignals) {
                if (!inWindow(window, signal.occurredAt())) {
                    continue;
                }
                if (signal.kind() == OutcomeKind.BITE && signal.fishInteractionId() == null) {
                    continue;
                }
                if (signal.kind() != OutcomeKind.BITE
                        && signal.kind() != OutcomeKind.FISH_ON
                        && signal.kind() != OutcomeKind.CATCH_LANDED
                        && signal.kind() != OutcomeKind.CATCH_LOST) {
                    continue;
                }
                AttributionKey key = new AttributionKey(
                        window.slice.snapshot().deliveredEntityId(),
                        window.slice.dimension(),
                        window.slice.role(),
                        signal.fishInteractionId()
                );
                ComputedAttribution current = byKey.get(key);
                if (current != null && AttributionWindows.outcomeRank(current.outcomeKind()) > AttributionWindows.outcomeRank(signal.kind())) {
                    continue;
                }
                if (current != null && AttributionWindows.outcomeRank(current.outcomeKind()) == AttributionWindows.outcomeRank(signal.kind())
                        && !current.attributedAt().isBefore(signal.occurredAt())) {
                    continue;
                }
                byKey.put(key, attribution(window, signal.eventId(), signal.fishInteractionId(), signal.kind(), signal.occurredAt()));
            }
            if (window.followed
                    && !now.isBefore(window.end)
                    && !hasStrategySuccess(byKey, window)
                    && window.slice.action() != GuidanceAction.RETURN) {
                AttributionKey key = new AttributionKey(
                        window.slice.snapshot().deliveredEntityId(),
                        window.slice.dimension(),
                        window.slice.role(),
                        null
                );
                if (!byKey.containsKey(key)) {
                    UUID eventId = noBiteEventId(window);
                    byKey.put(key, attribution(window, eventId, null, OutcomeKind.NO_BITE, window.end));
                }
            }
        }
        List<ComputedAttribution> result = new ArrayList<>(byKey.values());
        result.sort(Comparator.comparing(ComputedAttribution::attributedAt));
        return result;
    }

    /**
     * Recommendation windows used for effort-normalized online rates.
     * Role is a slice; callers that need a dimension window should union roles.
     */
    public static List<ResolvedAttributionWindow> resolveWindows(
            Instant now,
            GuidanceProperties.Attribution cfg,
            List<DeliveredSnapshot> decisions,
            List<ObservedUserAction> actions
    ) {
        if (now == null || cfg == null) {
            return List.of();
        }
        List<DeliveredSnapshot> ordered = new ArrayList<>(decisions == null ? List.of() : decisions);
        ordered.sort(Comparator.comparing(DeliveredSnapshot::deliveredAt, Comparator.nullsLast(Comparator.naturalOrder())));
        List<ObservedUserAction> observed = new ArrayList<>(actions == null ? List.of() : actions);
        observed.sort(Comparator.comparing(ObservedUserAction::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())));
        List<ResolvedAttributionWindow> windows = new ArrayList<>();
        for (DeliveredSnapshot snapshot : ordered) {
            for (RecommendedSlice slice : AttributionWindows.slices(snapshot)) {
                OpenWindow window = openWindow(now, cfg, snapshot, slice, observed, ordered);
                if (window != null) {
                    windows.add(new ResolvedAttributionWindow(
                            slice.snapshot().deliveredEntityId(),
                            slice.dimension(),
                            slice.role(),
                            slice.action(),
                            slice.windowKind(),
                            window.followed,
                            window.start,
                            window.end
                    ));
                }
            }
        }
        return windows;
    }

    private static boolean hasStrategySuccess(Map<AttributionKey, ComputedAttribution> byKey, OpenWindow window) {
        for (ComputedAttribution row : byKey.values()) {
            if (row.deliveredDecisionId().equals(window.slice.snapshot().deliveredEntityId())
                    && row.attributionDimension() == window.slice.dimension()
                    && row.recommendationRole() == window.slice.role()
                    && AttributionWindows.strategySuccess(row.outcomeKind())) {
                return true;
            }
        }
        return false;
    }

    private static OpenWindow openWindow(
            Instant now,
            GuidanceProperties.Attribution cfg,
            DeliveredSnapshot snapshot,
            RecommendedSlice slice,
            List<ObservedUserAction> actions,
            List<DeliveredSnapshot> decisions
    ) {
        ObservedUserAction follow = latestFollow(actions, snapshot.deliveredEntityId(), slice.role(), slice.action());
        boolean followed = follow != null && follow.followedRecommendation();
        Instant windowStart = windowStart(slice, snapshot, follow);
        if (windowStart == null) {
            return null;
        }
        Duration length = AttributionWindows.windowLength(slice.action(), snapshot.decision().reevaluateAfterMinutes(), cfg);
        Instant capEnd = windowStart.plus(length);
        Instant leaveEnd = stayLeave(slice, follow, actions, snapshot.deliveredEntityId());
        Instant invalidated = invalidatedAt(slice.dimension(), snapshot, follow, decisions, actions);
        Instant end = capEnd;
        if (leaveEnd != null && leaveEnd.isBefore(end)) {
            end = leaveEnd;
        }
        if (invalidated != null && invalidated.isBefore(end)) {
            end = invalidated;
        }
        if (end.isBefore(windowStart)) {
            return null;
        }
        return new OpenWindow(slice, followed, windowStart, end, length);
    }

    private static Instant windowStart(
            RecommendedSlice slice,
            DeliveredSnapshot snapshot,
            ObservedUserAction follow
    ) {
        if (slice.action() == GuidanceAction.MOVE) {
            if (follow == null) {
                return null;
            }
            return follow.occurredAt();
        }
        if (slice.action() == GuidanceAction.STAY) {
            return snapshot.deliveredAt();
        }
        if (follow == null) {
            return null;
        }
        return follow.occurredAt();
    }

    private static Instant stayLeave(
            RecommendedSlice slice,
            ObservedUserAction follow,
            List<ObservedUserAction> actions,
            UUID deliveredId
    ) {
        if (slice.action() != GuidanceAction.STAY) {
            return null;
        }
        Instant start = follow == null ? null : follow.occurredAt();
        Instant leave = null;
        for (ObservedUserAction action : actions) {
            if (!deliveredId.equals(action.deliveredDecisionId()) || action.recommendationRole() != slice.role()) {
                continue;
            }
            if (!Boolean.TRUE.equals(UserActionInference.truth(action.payload(), "endedStayWindow"))) {
                continue;
            }
            if (start != null && action.occurredAt().isBefore(start)) {
                continue;
            }
            if (leave == null || action.occurredAt().isBefore(leave)) {
                leave = action.occurredAt();
            }
        }
        return leave;
    }

    private static Instant invalidatedAt(
            AttributionDimension dimension,
            DeliveredSnapshot current,
            ObservedUserAction currentFollow,
            List<DeliveredSnapshot> decisions,
            List<ObservedUserAction> actions
    ) {
        Instant currentFollowAt = currentFollow == null ? current.deliveredAt() : currentFollow.occurredAt();
        Instant next = null;
        for (DeliveredSnapshot later : decisions) {
            if (!later.deliveredAt().isAfter(current.deliveredAt())) {
                continue;
            }
            for (RecommendedSlice slice : AttributionWindows.slices(later)) {
                if (slice.dimension() != dimension) {
                    continue;
                }
                ObservedUserAction follow = latestFollow(actions, later.deliveredEntityId(), slice.role(), slice.action());
                if (follow == null || !follow.followedRecommendation()) {
                    continue;
                }
                if (!follow.occurredAt().isAfter(currentFollowAt)) {
                    continue;
                }
                if (next == null || follow.occurredAt().isBefore(next)) {
                    next = follow.occurredAt();
                }
            }
        }
        return next;
    }

    private static ObservedUserAction latestFollow(
            List<ObservedUserAction> actions,
            UUID deliveredId,
            RecommendationRole role,
            GuidanceAction recommended
    ) {
        ObservedUserAction latest = null;
        for (ObservedUserAction action : actions) {
            if (!deliveredId.equals(action.deliveredDecisionId()) || action.recommendationRole() != role) {
                continue;
            }
            if (action.actualAction() != recommended && recommended != GuidanceAction.STAY) {
                continue;
            }
            if (Boolean.TRUE.equals(UserActionInference.truth(action.payload(), "endedStayWindow"))) {
                continue;
            }
            if (latest == null || !action.occurredAt().isBefore(latest.occurredAt())) {
                latest = action;
            }
        }
        return latest;
    }

    private static boolean inWindow(OpenWindow window, Instant at) {
        return at != null && !at.isBefore(window.start) && !at.isAfter(window.end);
    }

    private static ComputedAttribution attribution(
            OpenWindow window,
            UUID outcomeEventId,
            UUID fishInteractionId,
            OutcomeKind kind,
            Instant at
    ) {
        return new ComputedAttribution(
                outcomeEventId,
                fishInteractionId,
                window.slice.snapshot().deliveredEntityId(),
                window.slice.dimension(),
                window.followed,
                window.slice.role(),
                kind,
                window.slice.windowKind(),
                AttributionWindows.confidence(window.start, at, window.length),
                at
        );
    }

    private static UUID noBiteEventId(OpenWindow window) {
        String seed = "no-bite:" + window.slice.snapshot().deliveredEntityId()
                + ":" + window.slice.dimension()
                + ":" + window.slice.role();
        return UUID.nameUUIDFromBytes(seed.getBytes());
    }

    private record OpenWindow(
            RecommendedSlice slice,
            boolean followed,
            Instant start,
            Instant end,
            Duration length
    ) {
    }

    private record AttributionKey(
            UUID deliveredDecisionId,
            AttributionDimension dimension,
            RecommendationRole role,
            UUID fishInteractionId
    ) {
    }
}

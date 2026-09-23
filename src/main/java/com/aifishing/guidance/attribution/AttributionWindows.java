package com.aifishing.guidance.attribution;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.AttributionWindowKind;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecommendationRole;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AttributionWindows {

    private AttributionWindows() {
    }

    public static Optional<AttributionDimension> dimensionOf(GuidanceAction action) {
        if (action == null) {
            return Optional.empty();
        }
        return switch (action) {
            case MOVE, STAY -> Optional.of(AttributionDimension.LOCATION);
            case CHANGE_LURE -> Optional.of(AttributionDimension.LURE);
            case CHANGE_DEPTH -> Optional.of(AttributionDimension.DEPTH);
            case CHANGE_RETRIEVE -> Optional.of(AttributionDimension.RETRIEVE);
            case RETURN -> Optional.empty();
        };
    }

    public static Optional<AttributionWindowKind> windowKindOf(GuidanceAction action) {
        if (action == null) {
            return Optional.empty();
        }
        return switch (action) {
            case CHANGE_LURE -> Optional.of(AttributionWindowKind.LURE);
            case CHANGE_RETRIEVE -> Optional.of(AttributionWindowKind.RETRIEVE);
            case CHANGE_DEPTH -> Optional.of(AttributionWindowKind.DEPTH);
            case MOVE -> Optional.of(AttributionWindowKind.MOVE);
            case STAY -> Optional.of(AttributionWindowKind.STAY);
            case RETURN -> Optional.empty();
        };
    }

    public static Duration windowLength(GuidanceAction action, int reevaluateAfterMinutes, GuidanceProperties.Attribution cfg) {
        if (action == null) {
            return Duration.ZERO;
        }
        return switch (action) {
            case CHANGE_LURE -> Duration.ofMinutes(cfg.getLureWindowMinutes());
            case CHANGE_DEPTH -> Duration.ofMinutes(cfg.getDepthWindowMinutes());
            case CHANGE_RETRIEVE -> Duration.ofMinutes(cfg.getRetrieveWindowMinutes());
            case MOVE -> Duration.ofMinutes(cfg.getMoveWindowMinutes());
            case STAY -> Duration.ofMinutes(Math.min(
                    Math.max(reevaluateAfterMinutes, 1),
                    cfg.getMaxStayAttributionMinutes()
            ));
            case RETURN -> Duration.ZERO;
        };
    }

    public static double confidence(Instant windowStart, Instant at, Duration window) {
        if (windowStart == null || at == null || window == null || window.isZero() || window.isNegative()) {
            return 0.0;
        }
        if (at.isBefore(windowStart)) {
            return 0.0;
        }
        double windowSeconds = window.toSeconds();
        if (windowSeconds <= 0) {
            return 0.0;
        }
        double elapsed = Duration.between(windowStart, at).toSeconds();
        double ratio = elapsed / windowSeconds;
        if (ratio <= 1.0) {
            return 1.0 - (0.7 * ratio);
        }
        if (ratio <= 2.0) {
            return 0.3 * (2.0 - ratio);
        }
        return 0.0;
    }

    public static boolean strategySuccess(OutcomeKind kind) {
        return kind == OutcomeKind.FISH_ON
                || kind == OutcomeKind.CATCH_LANDED
                || kind == OutcomeKind.CATCH_LOST;
    }

    public static int outcomeRank(OutcomeKind kind) {
        if (kind == null) {
            return 0;
        }
        return switch (kind) {
            case NONE -> 0;
            case NO_BITE -> 1;
            case BITE -> 2;
            case FISH_ON -> 3;
            case CATCH_LOST, CATCH_LANDED -> 4;
        };
    }

    public static List<RecommendedSlice> slices(DeliveredSnapshot snapshot) {
        List<RecommendedSlice> slices = new ArrayList<>();
        if (snapshot == null || snapshot.decision() == null) {
            return slices;
        }
        addSlice(slices, snapshot, snapshot.decision().primaryAction(), RecommendationRole.PRIMARY);
        addSlice(slices, snapshot, snapshot.decision().secondaryAction(), RecommendationRole.SECONDARY);
        return slices;
    }

    private static void addSlice(
            List<RecommendedSlice> slices,
            DeliveredSnapshot snapshot,
            GuidanceAction action,
            RecommendationRole role
    ) {
        Optional<AttributionDimension> dimension = dimensionOf(action);
        Optional<AttributionWindowKind> windowKind = windowKindOf(action);
        if (dimension.isEmpty() || windowKind.isEmpty()) {
            return;
        }
        slices.add(new RecommendedSlice(snapshot, action, dimension.get(), windowKind.get(), role));
    }
}

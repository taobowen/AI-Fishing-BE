package com.aifishing.planning.route;

import com.aifishing.common.enums.CandidateSource;
import com.aifishing.common.enums.PlanningMode;
import com.aifishing.planning.candidate.CandidateSpot;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hard Required Point constraints and optional Hybrid secondary balance preference
 * for {@link RoutePlanner} selection. Score weights stay unchanged.
 */
public record RoutePlanConstraints(
        Set<UUID> requiredOpportunityIds,
        PlanningMode planningMode
) {
    public static final String REQUIRED_POINT_INFEASIBLE = "REQUIRED_POINT_INFEASIBLE";
    public static final String REQUIRED_SET_INFEASIBLE = "REQUIRED_SET_INFEASIBLE";

    /** Relative utility band for Hybrid secondary preference among still-positive routes. */
    public static final double HYBRID_UTILITY_RELATIVE_BAND = 0.10;
    public static final double HYBRID_UTILITY_ABSOLUTE_BAND = 0.20;
    public static final double HYBRID_RATIO_LOW = 0.40;
    public static final double HYBRID_RATIO_HIGH = 0.60;

    public RoutePlanConstraints {
        requiredOpportunityIds = requiredOpportunityIds == null
                ? Set.of()
                : Set.copyOf(requiredOpportunityIds);
        planningMode = PlanningMode.orAi(planningMode);
    }

    public static RoutePlanConstraints none() {
        return new RoutePlanConstraints(Set.of(), PlanningMode.AI);
    }

    public static RoutePlanConstraints of(Collection<CandidateSpot> required, PlanningMode mode) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (required != null) {
            for (CandidateSpot spot : required) {
                UUID id = spot == null ? null : spot.planningIdentity();
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return new RoutePlanConstraints(ids, mode);
    }

    public boolean hasRequired() {
        return !requiredOpportunityIds.isEmpty();
    }

    public boolean hybridBalanceEnabled() {
        return planningMode == PlanningMode.HYBRID;
    }

    public static boolean coversRequired(List<PlannedStop> stops, Set<UUID> requiredIds) {
        if (requiredIds == null || requiredIds.isEmpty()) {
            return true;
        }
        Set<UUID> present = new LinkedHashSet<>();
        if (stops != null) {
            for (PlannedStop stop : stops) {
                UUID id = stop.opportunityIdentity();
                if (id != null) {
                    present.add(id);
                }
                CandidateSpot spot = stop.candidate() == null ? null : stop.candidate().spot();
                if (spot != null && spot.planningIdentity() != null) {
                    present.add(spot.planningIdentity());
                }
            }
        }
        return present.containsAll(requiredIds);
    }

    /**
     * Distance from the 40–60 Template/AI fishing-minute band.
     * Required-point dwell is excluded. Lower is better.
     */
    public static double hybridBalanceDistance(List<PlannedStop> stops) {
        int template = 0;
        int ai = 0;
        if (stops != null) {
            for (PlannedStop stop : stops) {
                CandidateSpot spot = stop.candidate() == null ? null : stop.candidate().spot();
                CandidateSource source = spot == null ? CandidateSource.AI : spot.getCandidateSource();
                if (source == CandidateSource.REQUIRED) {
                    continue;
                }
                int dwell = Math.max(0, stop.stayMinutes());
                if (source == CandidateSource.TEMPLATE) {
                    template += dwell;
                } else {
                    ai += dwell;
                }
            }
        }
        int total = template + ai;
        if (total <= 0) {
            return 0;
        }
        double ratio = template / (double) total;
        if (ratio < HYBRID_RATIO_LOW) {
            return HYBRID_RATIO_LOW - ratio;
        }
        if (ratio > HYBRID_RATIO_HIGH) {
            return ratio - HYBRID_RATIO_HIGH;
        }
        return 0;
    }

    public static boolean utilitiesReasonablyClose(double utility, double bestUtility) {
        if (utility <= 0 || bestUtility <= 0) {
            return false;
        }
        double band = Math.max(HYBRID_UTILITY_ABSOLUTE_BAND, Math.abs(bestUtility) * HYBRID_UTILITY_RELATIVE_BAND);
        return Math.abs(bestUtility - utility) <= band + 1e-12;
    }
}

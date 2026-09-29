package com.aifishing.planning.route;

import com.aifishing.common.enums.CandidateSource;
import com.aifishing.common.enums.PlanningMode;
import com.aifishing.planning.candidate.CandidateSpot;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hard Required Point constraints and optional Hybrid secondary balance preference
 * for {@link RoutePlanner} selection. Score weights stay unchanged.
 * A required point may be satisfied by any one identity in its group.
 */
public record RoutePlanConstraints(
        Set<UUID> requiredOpportunityIds,
        PlanningMode planningMode,
        Map<UUID, Set<UUID>> requiredGroups
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
        if (requiredGroups == null || requiredGroups.isEmpty()) {
            Map<UUID, Set<UUID>> derived = new LinkedHashMap<>();
            for (UUID id : requiredOpportunityIds) {
                derived.put(id, Set.of(id));
            }
            requiredGroups = Map.copyOf(derived);
        } else {
            Map<UUID, Set<UUID>> copy = new LinkedHashMap<>();
            requiredGroups.forEach((key, value) -> copy.put(key, value == null ? Set.of() : Set.copyOf(value)));
            requiredGroups = Map.copyOf(copy);
        }
    }

    public static RoutePlanConstraints none() {
        return new RoutePlanConstraints(Set.of(), PlanningMode.AI, Map.of());
    }

    public static RoutePlanConstraints of(Collection<CandidateSpot> required, PlanningMode mode) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        Map<UUID, LinkedHashSet<UUID>> groups = new LinkedHashMap<>();
        if (required != null) {
            for (CandidateSpot spot : required) {
                UUID id = spot == null ? null : spot.planningIdentity();
                if (id == null) {
                    continue;
                }
                ids.add(id);
                UUID key = spot.getOriginRequiredPointId() != null ? spot.getOriginRequiredPointId() : id;
                groups.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(id);
            }
        }
        Map<UUID, Set<UUID>> frozen = new LinkedHashMap<>();
        groups.forEach((key, value) -> frozen.put(key, Set.copyOf(value)));
        return new RoutePlanConstraints(ids, mode, frozen);
    }

    public boolean hasRequired() {
        return !requiredGroups.isEmpty();
    }

    public boolean hybridBalanceEnabled() {
        return planningMode == PlanningMode.HYBRID;
    }

    public boolean coversAll(List<PlannedStop> stops) {
        if (requiredGroups.isEmpty()) {
            return true;
        }
        return coveredGroupCount(stops) == requiredGroups.size();
    }

    public int coveredGroupCount(List<PlannedStop> stops) {
        Set<UUID> present = presentIds(stops);
        int count = 0;
        for (Set<UUID> group : requiredGroups.values()) {
            boolean hit = false;
            for (UUID id : group) {
                if (present.contains(id)) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                count++;
            }
        }
        return count;
    }

    /** True when this candidate can still satisfy a required group the route has not covered. */
    public boolean isUncoveredSatisfier(UUID candidateId, List<PlannedStop> stops) {
        if (candidateId == null) {
            return false;
        }
        Set<UUID> present = presentIds(stops);
        for (Set<UUID> group : requiredGroups.values()) {
            if (!group.contains(candidateId)) {
                continue;
            }
            boolean covered = false;
            for (UUID id : group) {
                if (present.contains(id)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                return true;
            }
        }
        return false;
    }

    public static boolean coversRequired(List<PlannedStop> stops, Set<UUID> requiredIds) {
        if (requiredIds == null || requiredIds.isEmpty()) {
            return true;
        }
        return presentIds(stops).containsAll(requiredIds);
    }

    private static Set<UUID> presentIds(List<PlannedStop> stops) {
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
        return present;
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

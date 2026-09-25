package com.aifishing.planning.service;

import com.aifishing.common.enums.CandidateSource;
import com.aifishing.common.enums.PlanningMode;
import com.aifishing.planning.domain.TripPlanningInputSnapshot;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.dto.PlanningBalanceResponse;
import com.aifishing.planning.repo.TripPlanningInputSnapshotRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes planning balance from planned dwell only ({@code planned_dwell_minutes}).
 * Transit and wait are excluded. Required dwell counts in user minutes but not Hybrid ratio.
 */
public final class PlanningBalance {

    private PlanningBalance() {
    }

    public static PlanningBalanceResponse fromWaypoints(
            PlanningMode mode,
            Integer requiredPointCount,
            Integer templateTargetCount,
            List<TripWaypoint> waypoints
    ) {
        int userStops = 0;
        int aiStops = 0;
        int userMinutes = 0;
        int aiMinutes = 0;
        if (waypoints != null) {
            for (TripWaypoint waypoint : waypoints) {
                CandidateSource source = waypoint.getCandidateSource();
                int dwell = waypoint.getPlannedDwellMinutes() == null ? 0 : waypoint.getPlannedDwellMinutes();
                if (source.isUserStop()) {
                    userStops++;
                    userMinutes += dwell;
                } else {
                    aiStops++;
                    aiMinutes += dwell;
                }
            }
        }
        return new PlanningBalanceResponse(
                PlanningMode.orAi(mode),
                requiredPointCount,
                templateTargetCount,
                userStops,
                aiStops,
                userMinutes,
                aiMinutes
        );
    }

    public static PlanningBalanceResponse forRun(
            UUID planningRunId,
            List<TripWaypoint> waypoints,
            TripPlanningInputSnapshotRepository snapshotRepository
    ) {
        PlanningMode mode = PlanningMode.AI;
        Integer requiredPointCount = null;
        Integer templateTargetCount = null;
        if (planningRunId != null && snapshotRepository != null) {
            TripPlanningInputSnapshot snapshot = snapshotRepository.findByPlanningRunId(planningRunId).orElse(null);
            if (snapshot != null) {
                mode = PlanningMode.orAi(snapshot.getMode());
                requiredPointCount = snapshot.getRequiredPointCount();
                templateTargetCount = snapshot.getTemplateTargetCount();
            }
        }
        return fromWaypoints(mode, requiredPointCount, templateTargetCount, waypoints);
    }

    public static Map<String, Object> toUsageMap(PlanningBalanceResponse balance) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (balance == null) {
            return map;
        }
        map.put("mode", balance.mode() == null ? null : balance.mode().name());
        map.put("requiredPointCount", balance.requiredPointCount());
        map.put("templateTargetCount", balance.templateTargetCount());
        map.put("finalUserStopCount", balance.finalUserStopCount());
        map.put("finalAiStopCount", balance.finalAiStopCount());
        map.put("userFishingMinutes", balance.userFishingMinutes());
        map.put("aiFishingMinutes", balance.aiFishingMinutes());
        return map;
    }
}

package com.aifishing.planning.dto;

import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.tactics.TacticsStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TripPlanResponse(
        UUID id,
        UUID tripId,
        UUID strategyRunId,
        UUID planningRunId,
        int version,
        TripPlanStatus status,
        Pipeline featurePipeline,
        String featureAnalysisVersion,
        String planningAlgorithmVersion,
        Double overallPlanConfidence,
        Double totalEstimatedTravelDistanceM,
        Double totalEstimatedTravelMinutes,
        Instant plannedLaunchDepartureAt,
        Instant plannedReturnAt,
        Double totalFishingMinutes,
        Double totalPlannedMinutes,
        Integer scheduleReserveMinutes,
        Integer totalWaitMinutes,
        String scheduleAlgorithmVersion,
        List<ScheduleEventResponse> scheduleEvents,
        List<String> warnings,
        Map<String, Object> metadata,
        List<TripWaypointResponse> waypoints,
        List<TransitLegResponse> transitLegs,
        Instant generatedAt,
        TacticsStatus tacticsStatus,
        Boolean tacticsRequested,
        Instant tacticsStartedAt,
        PlanningBalanceResponse planningBalance
) {
}

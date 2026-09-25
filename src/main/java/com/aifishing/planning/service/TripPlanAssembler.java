package com.aifishing.planning.service;

import com.aifishing.common.enums.CandidateSource;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.planning.domain.PlanningRun;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.dto.GeneratePlanResponse;
import com.aifishing.planning.dto.PlanningBalanceResponse;
import com.aifishing.planning.dto.PlanningRunResponse;
import com.aifishing.planning.dto.PlanningRunSummaryResponse;
import com.aifishing.planning.dto.ScheduleEventResponse;
import com.aifishing.planning.dto.TransitLegResponse;
import com.aifishing.planning.dto.TripPlanMapDataResponse;
import com.aifishing.planning.dto.TripPlanResponse;
import com.aifishing.planning.dto.TripStopSubtargetResponse;
import com.aifishing.planning.dto.TripWaypointResponse;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.repo.TripPlanningInputSnapshotRepository;
import com.aifishing.planning.spatial.domain.TripPlanTransitLeg;
import com.aifishing.planning.spatial.domain.TripStopSubtarget;
import com.aifishing.planning.spatial.repo.TripPlanTransitLegRepository;
import com.aifishing.planning.spatial.repo.TripStopSubtargetRepository;
import com.aifishing.planning.tactics.TacticalRecommendation;
import com.aifishing.trip.repo.TripRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class TripPlanAssembler {

    private final GeoMapper geoMapper;
    private final ObjectMapper objectMapper;
    private final TripStopSubtargetRepository subtargetRepository;
    private final TripPlanTransitLegRepository transitLegRepository;
    private final TripRepository tripRepository;
    private final TripPlanningInputSnapshotRepository inputSnapshotRepository;

    public TripPlanAssembler(
            GeoMapper geoMapper,
            ObjectMapper objectMapper,
            TripStopSubtargetRepository subtargetRepository,
            TripPlanTransitLegRepository transitLegRepository,
            TripRepository tripRepository,
            TripPlanningInputSnapshotRepository inputSnapshotRepository
    ) {
        this.geoMapper = geoMapper;
        this.objectMapper = objectMapper;
        this.subtargetRepository = subtargetRepository;
        this.transitLegRepository = transitLegRepository;
        this.tripRepository = tripRepository;
        this.inputSnapshotRepository = inputSnapshotRepository;
    }

    public GeneratePlanResponse toGenerateResponse(PlanningRun run, TripPlan plan, List<TripWaypoint> waypoints) {
        return new GeneratePlanResponse(
                run.getId(),
                run.getStatus(),
                run.getErrorMessage(),
                run.getWarnings(),
                run.getFilterSummary(),
                plan == null ? null : toPlanResponse(plan, waypoints)
        );
    }

    public TripPlanResponse toPlanResponse(TripPlan plan, List<TripWaypoint> waypoints) {
        return new TripPlanResponse(
                plan.getId(),
                plan.getTripId(),
                plan.getStrategyRunId(),
                plan.getPlanningRunId(),
                plan.getVersion(),
                plan.getStatus(),
                plan.getFeaturePipeline(),
                plan.getFeatureAnalysisVersion(),
                plan.getPlanningAlgorithmVersion(),
                plan.getOverallPlanConfidence() == null ? null : plan.getOverallPlanConfidence().doubleValue(),
                plan.getTotalEstimatedTravelDistanceM() == null ? null : plan.getTotalEstimatedTravelDistanceM().doubleValue(),
                plan.getTotalEstimatedTravelMinutes() == null ? null : plan.getTotalEstimatedTravelMinutes().doubleValue(),
                plan.getPlannedLaunchDepartureAt(),
                plan.getPlannedReturnAt(),
                plan.getTotalFishingMinutes() == null ? null : plan.getTotalFishingMinutes().doubleValue(),
                plan.getTotalPlannedMinutes() == null ? null : plan.getTotalPlannedMinutes().doubleValue(),
                plan.getScheduleReserveMinutes(),
                plan.getTotalWaitMinutes(),
                plan.getScheduleAlgorithmVersion(),
                toScheduleEvents(plan.getScheduleEvents()),
                plan.getWarnings(),
                plan.getMetadata(),
                toWaypoints(waypoints),
                toTransitLegs(plan.getId()),
                plan.getGeneratedAt(),
                plan.getTacticsStatus(),
                plan.isTacticsRequested(),
                plan.getTacticsStartedAt(),
                toPlanningBalance(plan, waypoints)
        );
    }

    private PlanningBalanceResponse toPlanningBalance(TripPlan plan, List<TripWaypoint> waypoints) {
        return PlanningBalance.forRun(
                plan == null ? null : plan.getPlanningRunId(),
                waypoints,
                inputSnapshotRepository);
    }

    public TripPlanMapDataResponse toMapData(TripPlan plan, List<TripWaypoint> waypoints) {
        return new TripPlanMapDataResponse(
                plan.getId(),
                plan.getTripId(),
                plan.getVersion(),
                waypoints.stream()
                        .map(waypoint -> new TripPlanMapDataResponse.MapWaypoint(
                                waypoint.getSequence(),
                                geoMapper.toDto(waypoint.getLocation()),
                                waypoint.getLakeFeatureId(),
                                waypoint.getFeatureType(),
                                waypoint.resolvedTargetKind(),
                                geoMapper.toGeoJson(waypoint.resolvedTargetGeometry()),
                                geoMapper.toGeoJson(waypoint.getFishingCorridor()),
                                geoMapper.toDto(waypoint.resolvedEntryPoint()),
                                geoMapper.toGeoJson(waypoint.getVisitEnvelope())
                        ))
                        .toList()
        );
    }

    public PlanningRunResponse toRunResponse(PlanningRun run) {
        return new PlanningRunResponse(
                run.getId(),
                run.getTripId(),
                run.getStrategyRunId(),
                run.getStatus(),
                run.getFeaturePipeline(),
                run.getFeatureAnalysisVersion(),
                run.getAlgorithmVersion(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getFilterSummary(),
                run.getWarnings(),
                run.getUsageMetadata(),
                run.getErrorMessage()
        );
    }

    public PlanningRunSummaryResponse toRunSummary(PlanningRun run) {
        return new PlanningRunSummaryResponse(
                run.getId(),
                run.getTripId(),
                run.getStrategyRunId(),
                run.getStatus(),
                run.getFeaturePipeline(),
                run.getFeatureAnalysisVersion(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getErrorMessage()
        );
    }

    public List<TransitLegResponse> toTransitLegs(UUID planId) {
        if (planId == null) {
            return List.of();
        }
        return transitLegRepository.findByTripPlanIdOrderBySequenceAsc(planId).stream()
                .map(this::toTransitLeg)
                .toList();
    }

    private TransitLegResponse toTransitLeg(TripPlanTransitLeg leg) {
        return new TransitLegResponse(
                leg.getId(),
                leg.getSequence(),
                leg.getFromKind(),
                leg.getToKind(),
                leg.getFromVisitId(),
                leg.getToVisitId(),
                geoMapper.toGeoJson(leg.getTransitPath()),
                leg.getPathDistanceMeters() == null ? null : leg.getPathDistanceMeters().doubleValue(),
                leg.getPlannedTravelMinutes() == null ? null : leg.getPlannedTravelMinutes().doubleValue(),
                leg.getSourceWaterPathId(),
                leg.getNavigationVersion()
        );
    }

    private List<TripWaypointResponse> toWaypoints(List<TripWaypoint> waypoints) {
        Map<UUID, List<TripStopSubtargetResponse>> subtargets = loadSubtargets(waypoints);
        return waypoints.stream()
                .map(waypoint -> toWaypoint(waypoint, subtargets.getOrDefault(waypoint.getId(), List.of())))
                .toList();
    }

    private Map<UUID, List<TripStopSubtargetResponse>> loadSubtargets(List<TripWaypoint> waypoints) {
        List<UUID> ids = waypoints.stream().map(TripWaypoint::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<TripStopSubtargetResponse>> out = new LinkedHashMap<>();
        for (TripStopSubtarget row : subtargetRepository.findByTripWaypointIdInOrderByTripWaypointIdAscSequenceAsc(ids)) {
            out.computeIfAbsent(row.getTripWaypointId(), ignored -> new ArrayList<>()).add(toSubtarget(row));
        }
        return out;
    }

    private TripWaypointResponse toWaypoint(TripWaypoint waypoint, List<TripStopSubtargetResponse> subtargets) {
        ScoreBreakdown breakdown = waypoint.getScoreBreakdown() == null
                ? null
                : objectMapper.convertValue(waypoint.getScoreBreakdown(), ScoreBreakdown.class);
        return new TripWaypointResponse(
                waypoint.getId(),
                waypoint.getSequence(),
                geoMapper.toDto(waypoint.getLocation()),
                waypoint.getLakeFeatureId(),
                waypoint.getFeatureType(),
                waypoint.getPlannedArrivalTime(),
                waypoint.getPlannedDepartureTime(),
                waypoint.getPlannedArrivalAt(),
                waypoint.getPlannedDepartureAt(),
                waypoint.getPlannedDwellMinutes(),
                waypoint.getPlannedVisitMinutes() == null
                        ? waypoint.getPlannedDwellMinutes()
                        : waypoint.getPlannedVisitMinutes(),
                waypoint.getPlannedFishingMinutes(),
                waypoint.getPlannedInternalTransitMinutes(),
                waypoint.getPlannedWaitMinutes(),
                waypoint.resolvedTargetKind(),
                geoMapper.toGeoJson(waypoint.resolvedTargetGeometry()),
                geoMapper.toGeoJson(waypoint.getFishingCorridor()),
                geoMapper.toDto(waypoint.resolvedEntryPoint()),
                geoMapper.toDto(waypoint.resolvedExitPoint()),
                geoMapper.toGeoJson(waypoint.getSelectedFishingPath()),
                waypoint.getFishingCorridorWidthM() == null ? null : waypoint.getFishingCorridorWidthM().doubleValue(),
                waypoint.getZoneId(),
                waypoint.getFishingTargetId(),
                waypoint.getVisitScopeId(),
                waypoint.getVisitScopeMemberIds(),
                geoMapper.toGeoJson(waypoint.getVisitEnvelope()),
                waypoint.getClosedLoop(),
                waypoint.getTraversalKey(),
                waypoint.getRepresentativeDepthM() == null ? null : waypoint.getRepresentativeDepthM().doubleValue(),
                waypoint.getMinDepthM() == null ? null : waypoint.getMinDepthM().doubleValue(),
                waypoint.getMaxDepthM() == null ? null : waypoint.getMaxDepthM().doubleValue(),
                waypoint.getCandidateScore() == null ? null : waypoint.getCandidateScore().doubleValue(),
                breakdown,
                waypoint.getRecommendedTechniques(),
                waypoint.getEstimatedTravelDistanceFromPreviousM() == null
                        ? null
                        : waypoint.getEstimatedTravelDistanceFromPreviousM().doubleValue(),
                waypoint.getEstimatedTravelMinutesFromPrevious() == null
                        ? null
                        : waypoint.getEstimatedTravelMinutesFromPrevious().doubleValue(),
                waypoint.getReason(),
                waypoint.getWhyThisTime(),
                waypoint.getEnvironment(),
                waypoint.getMetadata(),
                subtargets,
                toTactical(waypoint.getTactical()),
                waypoint.getCandidateSource()
        );
    }

    private TripStopSubtargetResponse toSubtarget(TripStopSubtarget row) {
        return new TripStopSubtargetResponse(
                row.getId(),
                row.getSequence(),
                row.getTargetKind(),
                row.getFishingTargetId(),
                geoMapper.toGeoJson(row.getGeometry()),
                geoMapper.toDto(row.getEntryPoint()),
                geoMapper.toDto(row.getExitPoint()),
                row.getPlannedArrivalAt(),
                row.getPlannedDepartureAt(),
                row.getPlannedFishingMinutes(),
                row.getPlannedInternalTransitMinutes(),
                row.getScore() == null ? null : row.getScore().doubleValue(),
                row.getReason(),
                toTactical(row.getTactical())
        );
    }

    private List<ScheduleEventResponse> toScheduleEvents(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return events.stream().map(this::toScheduleEvent).toList();
    }

    private ScheduleEventResponse toScheduleEvent(Map<String, Object> event) {
        if (event == null) {
            return null;
        }
        return new ScheduleEventResponse(
                string(event.get("type")),
                instant(event.get("from")),
                instant(event.get("to")),
                string(event.get("location")),
                integer(event.get("afterWaypointSequence")),
                integer(event.get("minutes"))
        );
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private TacticalRecommendation toTactical(Map<String, Object> tactical) {
        if (tactical == null || tactical.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(tactical, TacticalRecommendation.class);
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value == null) {
            return null;
        }
        return Instant.parse(String.valueOf(value));
    }
}

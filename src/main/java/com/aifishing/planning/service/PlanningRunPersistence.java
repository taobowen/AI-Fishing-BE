package com.aifishing.planning.service;

import com.aifishing.common.enums.ClientChannel;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.webquota.service.WebPlanQuotaService;
import com.aifishing.planning.domain.PlanningRun;
import com.aifishing.planning.domain.PlanningRunStatus;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.PlanningRunRepository;
import com.aifishing.planning.repo.TripPlanRepository;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.planning.spatial.domain.TripPlanTransitLeg;
import com.aifishing.planning.spatial.domain.TripStopSubtarget;
import com.aifishing.planning.spatial.repo.TripPlanTransitLegRepository;
import com.aifishing.planning.spatial.repo.TripStopSubtargetRepository;
import com.aifishing.strategy.domain.StrategyRun;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PlanningRunPersistence {

    private final PlanningRunRepository planningRunRepository;
    private final TripPlanRepository tripPlanRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final TripStopSubtargetRepository subtargetRepository;
    private final TripPlanTransitLegRepository transitLegRepository;
    private final WebPlanQuotaService webPlanQuotaService;

    public PlanningRunPersistence(
            PlanningRunRepository planningRunRepository,
            TripPlanRepository tripPlanRepository,
            TripWaypointRepository tripWaypointRepository,
            TripStopSubtargetRepository subtargetRepository,
            TripPlanTransitLegRepository transitLegRepository,
            WebPlanQuotaService webPlanQuotaService
    ) {
        this.planningRunRepository = planningRunRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.subtargetRepository = subtargetRepository;
        this.transitLegRepository = transitLegRepository;
        this.webPlanQuotaService = webPlanQuotaService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PlanningRun insertRunning(UUID tripId, StrategyRun strategyRun, String algorithmVersion, ClientChannel channel) {
        PlanningRun run = new PlanningRun();
        run.setTripId(tripId);
        run.setStrategyRunId(strategyRun == null ? null : strategyRun.getId());
        run.setStatus(PlanningRunStatus.RUNNING);
        run.setClientChannel(channel);
        if (strategyRun != null) {
            run.setFeaturePipeline(strategyRun.getFeaturePipeline());
            run.setFeatureAnalysisVersion(strategyRun.getFeatureAnalysisVersion());
        }
        run.setAlgorithmVersion(algorithmVersion);
        run.setStartedAt(Instant.now());
        return planningRunRepository.saveAndFlush(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PlanningRun complete(
            UUID runId,
            Map<String, Object> inputSnapshot,
            Map<String, Object> rankingConfig,
            Map<String, Object> filterSummary,
            List<String> warnings,
            Map<String, Object> usageMetadata,
            TripPlan plan,
            List<TripWaypoint> waypoints,
            List<TripPlanTransitLeg> transitLegs,
            UUID userId,
            String idempotencyKey
    ) {
        PlanningRun run = planningRunRepository.findById(runId).orElseThrow();
        run.setStatus(PlanningRunStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        run.setInputSnapshot(inputSnapshot);
        run.setRankingConfig(rankingConfig);
        run.setFilterSummary(filterSummary);
        run.setWarnings(warnings);
        run.setUsageMetadata(usageMetadata);
        run.setErrorMessage(null);
        planningRunRepository.saveAndFlush(run);

        for (TripPlan previous : tripPlanRepository.findByTripIdAndStatus(plan.getTripId(), TripPlanStatus.GENERATED)) {
            previous.setStatus(TripPlanStatus.SUPERSEDED);
            tripPlanRepository.save(previous);
        }
        plan.setPlanningRunId(runId);
        TripPlan saved = tripPlanRepository.saveAndFlush(plan);
        for (TripWaypoint waypoint : waypoints) {
            waypoint.setTripPlanId(saved.getId());
            TripWaypoint persisted = tripWaypointRepository.save(waypoint);
            if (waypoint.getPendingSubPlan() != null && waypoint.getPendingSubPlan().stops() != null) {
                int seq = 1;
                for (var micro : waypoint.getPendingSubPlan().stops()) {
                    TripStopSubtarget row = new TripStopSubtarget();
                    row.setTripWaypointId(persisted.getId());
                    row.setSequence(seq++);
                    row.setTargetKind(micro.spot() == null
                            ? com.aifishing.planning.spatial.TargetKind.POINT
                            : micro.spot().getTargetKind());
                    row.setFishingTargetId(micro.fishingTargetId());
                    row.setGeometry(micro.geometry());
                    row.setEntryPoint(micro.entry());
                    row.setExitPoint(micro.exit());
                    row.setPlannedArrivalAt(micro.arrivalAt());
                    row.setPlannedDepartureAt(micro.departureAt());
                    row.setPlannedFishingMinutes(micro.fishingMinutes());
                    row.setPlannedInternalTransitMinutes(micro.transitMinutes());
                    row.setScore(java.math.BigDecimal.valueOf(micro.utility()));
                    row.setReason(micro.reason());
                    if (waypoint.getPendingChildTactical() != null && micro.spot() != null
                            && micro.spot().planningIdentity() != null) {
                        row.setTactical(waypoint.getPendingChildTactical().get(micro.spot().planningIdentity()));
                    }
                    subtargetRepository.save(row);
                }
            }
        }
        if (transitLegs != null) {
            for (TripPlanTransitLeg leg : transitLegs) {
                leg.setTripPlanId(saved.getId());
                transitLegRepository.save(leg);
            }
        }
        if (run.getClientChannel() == ClientChannel.WEB) {
            webPlanQuotaService.consumeOnSuccess(userId, idempotencyKey, runId, saved.getId());
        }
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PlanningRun fail(
            UUID runId,
            Map<String, Object> inputSnapshot,
            Map<String, Object> rankingConfig,
            Map<String, Object> filterSummary,
            List<String> warnings,
            Map<String, Object> usageMetadata,
            String errorMessage
    ) {
        PlanningRun run = planningRunRepository.findById(runId).orElseThrow();
        run.setStatus(PlanningRunStatus.FAILED);
        run.setCompletedAt(Instant.now());
        run.setInputSnapshot(inputSnapshot);
        run.setRankingConfig(rankingConfig);
        run.setFilterSummary(filterSummary);
        run.setWarnings(warnings);
        run.setUsageMetadata(usageMetadata);
        run.setErrorMessage(truncate(errorMessage));
        return planningRunRepository.saveAndFlush(run);
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}

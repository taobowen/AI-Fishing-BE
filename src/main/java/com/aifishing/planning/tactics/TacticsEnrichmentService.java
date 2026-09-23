package com.aifishing.planning.tactics;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.dto.TripPlanResponse;
import com.aifishing.planning.repo.TripPlanRepository;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.planning.route.AccessResolution;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.service.TripPlanAssembler;
import com.aifishing.planning.spatial.domain.TripStopSubtarget;
import com.aifishing.planning.spatial.repo.TripStopSubtargetRepository;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.strategy.weather.WeatherContext;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Post-generation tactics enrichment. Spatial and schedule fields stay immutable;
 * only isolated tactical JSON and tactics status are written.
 */
@Service
public class TacticsEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(TacticsEnrichmentService.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final CurrentUser currentUser;
    private final TripRepository tripRepository;
    private final LakeRepository lakeRepository;
    private final TripPlanRepository tripPlanRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final TripStopSubtargetRepository subtargetRepository;
    private final StrategyRunRepository strategyRunRepository;
    private final TacticalRecommendationService tacticalRecommendationService;
    private final TripPlanAssembler assembler;
    private final PlanningProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    public TacticsEnrichmentService(
            CurrentUser currentUser,
            TripRepository tripRepository,
            LakeRepository lakeRepository,
            TripPlanRepository tripPlanRepository,
            TripWaypointRepository tripWaypointRepository,
            TripStopSubtargetRepository subtargetRepository,
            StrategyRunRepository strategyRunRepository,
            TacticalRecommendationService tacticalRecommendationService,
            TripPlanAssembler assembler,
            PlanningProperties properties,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.currentUser = currentUser;
        this.tripRepository = tripRepository;
        this.lakeRepository = lakeRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.subtargetRepository = subtargetRepository;
        this.strategyRunRepository = strategyRunRepository;
        this.tacticalRecommendationService = tacticalRecommendationService;
        this.assembler = assembler;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TripPlanResponse enrich(UUID tripId, UUID planId) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Trip not found"));
        TripPlan plan = tripPlanRepository.findByIdAndTripId(planId, tripId)
                .orElseThrow(() -> new NotFoundException("Plan not found"));
        List<TripWaypoint> waypoints = tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(plan.getId());
        if (plan.getTacticsStatus() == TacticsStatus.READY) {
            return assembler.toPlanResponse(plan, waypoints);
        }
        Instant now = Instant.now();
        Instant staleBefore = now.minus(Duration.ofSeconds(properties.getTactics().getStaleTimeoutSeconds()));
        Integer claimed = transactionTemplate.execute(status ->
                tripPlanRepository.claimTacticsGeneration(plan.getId(), now, staleBefore));
        if (claimed == null || claimed == 0) {
            TripPlan latest = tripPlanRepository.findById(plan.getId()).orElse(plan);
            return assembler.toPlanResponse(latest, waypoints);
        }
        long started = System.nanoTime();
        TacticsStatus finalStatus = TacticsStatus.FAILED;
        int aiCalls = 0;
        long aiMs = 0;
        try {
            List<UUID> waypointIds = waypoints.stream().map(TripWaypoint::getId).toList();
            List<TripStopSubtarget> subtargets = waypointIds.isEmpty()
                    ? List.of()
                    : subtargetRepository.findByTripWaypointIdInOrderByTripWaypointIdAscSequenceAsc(waypointIds);
            PlanningContext context = planningContext(trip, plan);
            List<FishableVisit> visits = TacticalVisits.fromPersisted(waypoints, subtargets);
            long aiStart = System.nanoTime();
            TacticalRecommendationService.TacticalPlan tactics = tacticalRecommendationService.recommendVisits(visits, context);
            aiMs = (System.nanoTime() - aiStart) / 1_000_000L;
            aiCalls = tactics.usable() && !visits.isEmpty() ? 1 : 0;
            if (!tactics.usable()) {
                markFailed(plan.getId());
                finalStatus = TacticsStatus.FAILED;
            } else {
                persistReady(plan.getId(), waypoints, subtargets, tactics.byVisitId());
                finalStatus = TacticsStatus.READY;
            }
        } catch (Exception ex) {
            log.warn("Tactics enrichment failed for plan {}: {}", planId, ex.getMessage());
            markFailed(plan.getId());
            finalStatus = TacticsStatus.FAILED;
        } finally {
            logEnrichment(plan.getId(), aiCalls, aiMs, (System.nanoTime() - started) / 1_000_000L, finalStatus);
        }
        if (entityManager != null) {
            entityManager.clear();
        }
        TripPlan latest = tripPlanRepository.findById(plan.getId()).orElse(plan);
        return assembler.toPlanResponse(
                latest,
                tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(plan.getId())
        );
    }

    private PlanningContext planningContext(Trip trip, TripPlan plan) {
        Lake lake = trip.getLakeId() == null ? null : lakeRepository.findById(trip.getLakeId()).orElse(null);
        StrategyRun strategyRun = plan.getStrategyRunId() == null
                ? null
                : strategyRunRepository.findById(plan.getStrategyRunId()).orElse(null);
        FishingStrategyProfile profile = parseProfile(strategyRun);
        WeatherContext weather = parseWeather(strategyRun);
        return new PlanningContext(
                trip,
                lake,
                null,
                AccessResolution.unknown(),
                null,
                List.of(),
                null,
                weather,
                List.of(),
                profile,
                strategyRun,
                properties,
                new ArrayList<>()
        );
    }

    private FishingStrategyProfile parseProfile(StrategyRun run) {
        if (run == null || run.getStrategyProfile() == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(run.getStrategyProfile(), FishingStrategyProfile.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private WeatherContext parseWeather(StrategyRun run) {
        if (run == null || run.getWeatherSnapshot() == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(run.getWeatherSnapshot(), WeatherContext.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void persistReady(
            UUID planId,
            List<TripWaypoint> waypoints,
            List<TripStopSubtarget> subtargets,
            Map<UUID, TacticalRecommendation> byVisit
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            TripPlan plan = tripPlanRepository.findById(planId).orElseThrow();
            List<TacticalRecommendation> unused = new ArrayList<>(byVisit.values());
            for (TripWaypoint waypoint : waypoints) {
                TripWaypoint persisted = tripWaypointRepository.findById(waypoint.getId()).orElse(null);
                if (persisted == null) {
                    continue;
                }
                TacticalRecommendation match = matchVisit(persisted, byVisit);
                if (match == null) {
                    match = firstChildMatch(persisted.getId(), subtargets, byVisit);
                }
                if (match == null && !unused.isEmpty()) {
                    match = unused.remove(0);
                }
                if (match != null) {
                    unused.remove(match);
                    persisted.setTactical(objectMapper.convertValue(match, MAP));
                    tripWaypointRepository.save(persisted);
                }
            }
            for (TripStopSubtarget row : subtargets) {
                if (row.getFishingTargetId() == null || !byVisit.containsKey(row.getFishingTargetId())) {
                    continue;
                }
                TripStopSubtarget persisted = subtargetRepository.findById(row.getId()).orElse(null);
                if (persisted == null) {
                    continue;
                }
                persisted.setTactical(objectMapper.convertValue(byVisit.get(row.getFishingTargetId()), MAP));
                subtargetRepository.save(persisted);
            }
            plan.setTacticsStatus(TacticsStatus.READY);
            plan.setTacticsRequested(true);
            tripPlanRepository.save(plan);
        });
    }

    private void markFailed(UUID planId) {
        transactionTemplate.executeWithoutResult(status -> {
            TripPlan plan = tripPlanRepository.findById(planId).orElse(null);
            if (plan == null) {
                return;
            }
            plan.setTacticsStatus(TacticsStatus.FAILED);
            plan.setTacticsRequested(true);
            tripPlanRepository.save(plan);
        });
    }

    private static TacticalRecommendation firstChildMatch(
            UUID waypointId,
            List<TripStopSubtarget> subtargets,
            Map<UUID, TacticalRecommendation> byVisit
    ) {
        if (waypointId == null || subtargets == null) {
            return null;
        }
        for (TripStopSubtarget row : subtargets) {
            if (row == null || !waypointId.equals(row.getTripWaypointId()) || row.getFishingTargetId() == null) {
                continue;
            }
            TacticalRecommendation match = byVisit.get(row.getFishingTargetId());
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static TacticalRecommendation matchVisit(
            TripWaypoint waypoint,
            Map<UUID, TacticalRecommendation> byVisit
    ) {
        UUID visitId = visitId(waypoint);
        if (visitId != null && byVisit.containsKey(visitId)) {
            return byVisit.get(visitId);
        }
        if (waypoint.getFishingTargetId() != null && byVisit.containsKey(waypoint.getFishingTargetId())) {
            return byVisit.get(waypoint.getFishingTargetId());
        }
        return null;
    }

    private static UUID visitId(TripWaypoint waypoint) {
        Map<String, Object> metadata = waypoint.getMetadata();
        if (metadata != null && metadata.get("visitId") != null) {
            try {
                return UUID.fromString(String.valueOf(metadata.get("visitId")));
            } catch (IllegalArgumentException ignored) {
                return waypoint.getFishingTargetId();
            }
        }
        return waypoint.getFishingTargetId();
    }

    private void logEnrichment(UUID tripPlanId, int aiCalls, long aiMs, long totalMs, TacticsStatus finalStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tripPlanId", tripPlanId.toString());
        payload.put("aiCalls", aiCalls);
        payload.put("aiMs", aiMs);
        payload.put("totalMs", totalMs);
        payload.put("finalStatus", finalStatus.name());
        try {
            log.info("TACTICS_ENRICHMENT_PROFILE {}", objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            log.info("TACTICS_ENRICHMENT_PROFILE tripPlanId={} finalStatus={}", tripPlanId, finalStatus);
        }
    }
}

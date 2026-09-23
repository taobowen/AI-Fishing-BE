package com.aifishing.guidance.state;

import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.repo.BoatRepository;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.feedback.catchlog.domain.CatchEvent;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.feedback.catchlog.repo.CatchEventRepository;
import com.aifishing.feedback.performance.EmpiricalPerformanceService;
import com.aifishing.feedback.performance.dto.SessionPerformanceResponse;
import com.aifishing.feedback.performance.dto.WaypointPerformanceDto;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionAdHocFishingStop;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionAdHocFishingStopRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.fishingsession.service.SessionMapper;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.OriginalPlanStep;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.contracts.WeatherSnapshot;
import com.aifishing.guidance.horizon.ActiveGuidanceTarget;
import com.aifishing.guidance.learning.ExtractiveSessionFacts;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.GuidancePlanStepEntity;
import com.aifishing.guidance.persistence.GuidancePlanStepRepository;
import com.aifishing.guidance.persistence.GuidancePlanVersionEntity;
import com.aifishing.guidance.persistence.GuidancePlanVersionRepository;
import com.aifishing.guidance.persistence.LureEventEntity;
import com.aifishing.guidance.persistence.LureEventRepository;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.guidance.revisit.OpportunityRevisitSupport;
import com.aifishing.guidance.runtime.EnvironmentSnapshot;
import com.aifishing.guidance.runtime.WeatherSnapshotFreshness;
import com.aifishing.guidance.spi.FishingSessionStateBuilder;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.domain.TripWaypointPlanMetadata;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic read-only derivation. Must not call {@code WeatherService} or persist.
 */
@Component
public class RepositoryFishingSessionStateBuilder implements FishingSessionStateBuilder {

    private static final int RECENT_LIMIT = 10;
    private static final int HORIZON_LIMIT = 4;
    private static final Set<SessionEventType> MOVE_EVENTS =
            EnumSet.of(SessionEventType.WAYPOINT_ENTERED, SessionEventType.USER_MOVED);
    private static final double KMH_TO_MPS = 3.6;
    private static final double KM_TO_M = 1000.0;

    private final Clock clock;
    private final PlanningProperties planningProperties;
    private final FishingSessionRepository sessionRepository;
    private final TripRepository tripRepository;
    private final LakeRepository lakeRepository;
    private final BoatRepository boatRepository;
    private final SessionWaypointProgressRepository progressRepository;
    private final SessionLocationPointRepository locationPointRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final CatchEventRepository catchEventRepository;
    private final EmpiricalPerformanceService empiricalPerformanceService;
    private final LureEventRepository lureEventRepository;
    private final SessionEventRepository sessionEventRepository;
    private final GuidancePlanVersionRepository planVersionRepository;
    private final GuidancePlanStepRepository planStepRepository;
    private final SessionAdHocFishingStopRepository adHocStopRepository;
    private final AgentDeliveredDecisionRepository deliveredDecisionRepository;
    private final OpportunityRevisitSupport opportunityRevisitSupport;

    public RepositoryFishingSessionStateBuilder(
            Clock clock,
            PlanningProperties planningProperties,
            FishingSessionRepository sessionRepository,
            TripRepository tripRepository,
            LakeRepository lakeRepository,
            BoatRepository boatRepository,
            SessionWaypointProgressRepository progressRepository,
            SessionLocationPointRepository locationPointRepository,
            TripWaypointRepository tripWaypointRepository,
            CatchEventRepository catchEventRepository,
            EmpiricalPerformanceService empiricalPerformanceService,
            LureEventRepository lureEventRepository,
            SessionEventRepository sessionEventRepository,
            GuidancePlanVersionRepository planVersionRepository,
            GuidancePlanStepRepository planStepRepository,
            SessionAdHocFishingStopRepository adHocStopRepository,
            AgentDeliveredDecisionRepository deliveredDecisionRepository,
            OpportunityRevisitSupport opportunityRevisitSupport
    ) {
        this.clock = clock;
        this.planningProperties = planningProperties;
        this.sessionRepository = sessionRepository;
        this.tripRepository = tripRepository;
        this.lakeRepository = lakeRepository;
        this.boatRepository = boatRepository;
        this.progressRepository = progressRepository;
        this.locationPointRepository = locationPointRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.catchEventRepository = catchEventRepository;
        this.empiricalPerformanceService = empiricalPerformanceService;
        this.lureEventRepository = lureEventRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.planVersionRepository = planVersionRepository;
        this.planStepRepository = planStepRepository;
        this.adHocStopRepository = adHocStopRepository;
        this.deliveredDecisionRepository = deliveredDecisionRepository;
        this.opportunityRevisitSupport = opportunityRevisitSupport;
    }

    @Override
    @Transactional(readOnly = true)
    public FishingSessionState build(UUID sessionId, EnvironmentSnapshot environment) {
        Instant now = clock.instant();
        FishingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Fishing session not found"));
        Trip trip = tripRepository.findById(session.getTripId())
                .orElseThrow(() -> new NotFoundException("Trip not found"));
        Lake lake = lakeRepository.findById(trip.getLakeId())
                .orElseThrow(() -> new NotFoundException("Lake not found"));
        List<SessionWaypointProgress> progress =
                progressRepository.findByFishingSessionIdOrderBySequenceAsc(sessionId);
        SessionWaypointProgress current = SessionMapper.currentWaypoint(progress);
        Map<UUID, TripWaypoint> waypoints = session.getTripPlanId() == null
                ? Map.of()
                : SessionMapper.indexWaypoints(
                        tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(session.getTripPlanId()));
        TripWaypoint currentWaypoint = current == null ? null : waypoints.get(current.getTripWaypointId());
        List<SessionLocationPoint> accepted = locationPointRepository
                .findByFishingSessionIdAndQualityOrderByRecordedAtAsc(sessionId, LocationQuality.ACCEPTED);
        List<CatchEvent> catches = catchEventRepository
                .findByFishingSessionIdAndStatusOrderByOccurredAtAsc(sessionId, CatchStatus.ACTIVE);
        List<LureEventEntity> lureEvents = lureEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(sessionId);
        List<SessionEventEntity> events = sessionEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(sessionId);
        SessionPerformanceResponse performance = empiricalPerformanceService.get(sessionId, null);
        Boat boat = trip.getBoatId() == null ? null : boatRepository.findById(trip.getBoatId()).orElse(null);

        return new FishingSessionState(
                GuidanceSchemaVersion.VALUE,
                sessionSlice(session, trip, lake, now),
                positionSlice(accepted, currentWaypoint, lake, environment),
                boatSlice(boat, accepted),
                fishingSlice(session, current, currentWaypoint, lureEvents, events, now),
                environmentSlice(environment, now),
                recentSlice(events, lureEvents, catches),
                performanceSlice(performance, catches, current, now, session),
                planSlice(session, progress, current, waypoints),
                opportunityRevisitSlice(session.getId(), progress, waypoints)
        );
    }

    private FishingSessionState.Session sessionSlice(FishingSession session, Trip trip, Lake lake, Instant now) {
        return new FishingSessionState.Session(
                session.getId(),
                session.getUserId(),
                session.getStatus(),
                session.getStartedAt(),
                trip.getPrimaryTargetSpecies(),
                remainingTimeMinutes(session, trip, lake, now)
        );
    }

    private static Integer remainingTimeMinutes(FishingSession session, Trip trip, Lake lake, Instant now) {
        if (trip.getFishingEndTime() == null) {
            return null;
        }
        ZoneId zone = lake.getTimeZoneId() == null || lake.getTimeZoneId().isBlank()
                ? ZoneId.of("UTC")
                : ZoneId.of(lake.getTimeZoneId());
        LocalDate day = session.getStartedAt() == null
                ? LocalDate.ofInstant(now, zone)
                : session.getStartedAt().atZone(zone).toLocalDate();
        Instant endAt = LocalDateTime.of(day, trip.getFishingEndTime()).atZone(zone).toInstant();
        return (int) Math.max(0, Duration.between(now, endAt).toMinutes());
    }

    private static FishingSessionState.Position positionSlice(
            List<SessionLocationPoint> accepted,
            TripWaypoint currentWaypoint,
            Lake lake,
            EnvironmentSnapshot environment
    ) {
        SessionLocationPoint latest = accepted.isEmpty() ? null : accepted.getLast();
        Point location = latest != null ? latest.getLocation() : null;
        if (location == null && currentWaypoint != null) {
            location = currentWaypoint.getLocation();
        }
        if (location == null && lake.getCentroid() != null) {
            location = lake.getCentroid();
        }
        if (location == null && environment != null && environment.queryLatitudeWgs84() != null) {
            return new FishingSessionState.Position(
                    environment.queryLatitudeWgs84(),
                    environment.queryLongitudeWgs84(),
                    null,
                    null,
                    null
            );
        }
        if (location == null) {
            throw new IllegalStateException("WGS84 position is required for FishingSessionState");
        }
        return new FishingSessionState.Position(
                location.getY(),
                location.getX(),
                decimal(latest == null ? null : latest.getAccuracyM()),
                decimal(latest == null ? null : latest.getSpeedMps()),
                decimal(latest == null ? null : latest.getHeadingDegrees())
        );
    }

    private FishingSessionState.Boat boatSlice(Boat boat, List<SessionLocationPoint> accepted) {
        if (boat == null) {
            return new FishingSessionState.Boat(null, null, null, null, null);
        }
        Double cruiseSpeedMps = kmhToMps(boat.getMeasuredCruiseSpeedKmh());
        Double remainingRangeMeters = remainingRangeMeters(boat, accepted);
        Double returnReserveMeters = null;
        if (cruiseSpeedMps != null) {
            returnReserveMeters = cruiseSpeedMps * planningProperties.getSchedule().getReturnBufferMinutes() * 60.0;
        }
        return new FishingSessionState.Boat(
                boat.getType(),
                boat.getPrimaryTransitPropulsionType(),
                cruiseSpeedMps,
                remainingRangeMeters,
                returnReserveMeters
        );
    }

    private static Double remainingRangeMeters(Boat boat, List<SessionLocationPoint> accepted) {
        if (boat.getComfortableRoundTripRangeKm() == null) {
            return null;
        }
        double budget = boat.getComfortableRoundTripRangeKm().doubleValue() * KM_TO_M;
        double traveled = 0;
        SessionLocationPoint previous = null;
        for (SessionLocationPoint point : accepted) {
            if (previous != null && previous.getLocation() != null && point.getLocation() != null) {
                double hop = GeoMetrics.distanceM(previous.getLocation(), point.getLocation());
                if (Double.isFinite(hop)) {
                    traveled += hop;
                }
            }
            previous = point;
        }
        return Math.max(0, budget - traveled);
    }

    private static Double kmhToMps(BigDecimal kmh) {
        if (kmh == null) {
            return null;
        }
        return kmh.doubleValue() / KMH_TO_MPS;
    }

    private FishingSessionState.Fishing fishingSlice(
            FishingSession session,
            SessionWaypointProgress current,
            TripWaypoint waypoint,
            List<LureEventEntity> lureEvents,
            List<SessionEventEntity> events,
            Instant now
    ) {
        LureEventEntity lure = lureEvents.isEmpty() ? null : lureEvents.getLast();
        Integer timeAtWaypoint = timeAtWaypointMinutes(current, now);
        SessionAdHocFishingStop stop = adHocStop(session.getId());
        return new FishingSessionState.Fishing(
                current == null ? null : current.getTripWaypointId(),
                current == null ? null : current.getId(),
                waypoint == null ? null : waypoint.getFeatureType(),
                decimal(waypoint == null ? null : waypoint.getMinDepthM()),
                decimal(waypoint == null ? null : waypoint.getMaxDepthM()),
                lure == null ? null : lure.getLureFamily(),
                lure == null ? null : lure.getPresentation(),
                lure == null ? null : lure.getRetrieveStyle(),
                timeAtWaypoint,
                session.getActivityState() == null ? FishingActivityState.UNKNOWN : session.getActivityState(),
                session.getActivityStateSince() == null ? session.getStartedAt() : session.getActivityStateSince(),
                session.getActivityStateSource() == null
                        ? ActivityStateSource.UNKNOWN
                        : session.getActivityStateSource(),
                activeFishingEffortMinutes(session, now),
                noBiteMinutes(session, events, now),
                stop == null ? null : stop.getId(),
                stop == null ? null : stop.getStartedAt(),
                stop == null ? null : stop.getFishingTargetId(),
                stop == null ? null : stop.getZoneId(),
                stop == null ? null : stop.getLakeFeatureId()
        );
    }

    private static Integer timeAtWaypointMinutes(SessionWaypointProgress current, Instant now) {
        if (current == null) {
            return null;
        }
        if (current.getArrivedAt() != null) {
            return (int) Math.max(0, Duration.between(current.getArrivedAt(), now).toMinutes());
        }
        if (current.getStatus() == WaypointProgressStatus.NAVIGATING) {
            return 0;
        }
        return Math.max(0, current.getAccumulatedDwellSeconds() / 60);
    }

    private static Integer activeFishingEffortMinutes(FishingSession session, Instant now) {
        if (session.getActivityState() != FishingActivityState.FISHING) {
            return 0;
        }
        Instant since = session.getActivityStateSince() != null ? session.getActivityStateSince() : session.getStartedAt();
        if (since == null) {
            return 0;
        }
        return (int) Math.max(0, Duration.between(since, now).toMinutes());
    }

    private static Integer noBiteMinutes(
            FishingSession session,
            List<SessionEventEntity> events,
            Instant now
    ) {
        if (session.getActivityState() != FishingActivityState.FISHING) {
            return 0;
        }
        Instant windowStart = session.getActivityStateSince() != null
                ? session.getActivityStateSince()
                : session.getStartedAt();
        Instant lastSignal = events.stream()
                .filter(event -> event.getType() == SessionEventType.BITE || event.getType() == SessionEventType.FISH_ON)
                .map(SessionEventEntity::getOccurredAt)
                .filter(at -> windowStart == null || !at.isBefore(windowStart))
                .max(Comparator.naturalOrder())
                .orElse(windowStart);
        if (lastSignal == null) {
            return 0;
        }
        return (int) Math.max(0, Duration.between(lastSignal, now).toMinutes());
    }

    private static FishingSessionState.Environment environmentSlice(EnvironmentSnapshot environment, Instant now) {
        if (environment == null || environment.weather() == null) {
            return new FishingSessionState.Environment(null, null, null, null, null, null, null);
        }
        WeatherSnapshot weather = environment.weather();
        Integer age = environment.weatherAgeMinutes() != null
                ? environment.weatherAgeMinutes()
                : WeatherSnapshotFreshness.ageMinutes(weather, now);
        return new FishingSessionState.Environment(
                weather.weather(),
                weather.windSpeedKph(),
                weather.windDirection(),
                weather.pressureHpa(),
                weather.temperatureC(),
                weather.observedAt(),
                age
        );
    }

    private static FishingSessionState.Recent recentSlice(
            List<SessionEventEntity> events,
            List<LureEventEntity> lureEvents,
            List<CatchEvent> catches
    ) {
        return new FishingSessionState.Recent(
                tailIds(events.stream()
                        .filter(event -> MOVE_EVENTS.contains(event.getType()))
                        .map(SessionEventEntity::getId)
                        .toList()),
                tailIds(lureEvents.stream().map(LureEventEntity::getId).toList()),
                tailIds(catches.stream().map(CatchEvent::getId).toList()),
                tailIds(events.stream()
                        .filter(event -> event.getType() == SessionEventType.ADVICE_CREATED)
                        .map(SessionEventEntity::getId)
                        .toList()),
                tailIds(events.stream()
                        .filter(event -> event.getType() == SessionEventType.ADVICE_REJECTED)
                        .map(SessionEventEntity::getId)
                        .toList()),
                ExtractiveSessionFacts.recentClips(events, RECENT_LIMIT)
        );
    }

    private static FishingSessionState.Performance performanceSlice(
            SessionPerformanceResponse performance,
            List<CatchEvent> catches,
            SessionWaypointProgress current,
            Instant now,
            FishingSession session
    ) {
        Instant lastCatch = catches.stream()
                .map(CatchEvent::getOccurredAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Integer sinceCatch = lastCatch == null
                ? minutesSince(session.getStartedAt(), now)
                : (int) Math.max(0, Duration.between(lastCatch, now).toMinutes());
        Integer waypointCatches = 0;
        if (current != null && performance != null && performance.waypoints() != null) {
            waypointCatches = performance.waypoints().stream()
                    .filter(row -> current.getTripWaypointId().equals(row.tripWaypointId()))
                    .map(WaypointPerformanceDto::landedCount)
                    .findFirst()
                    .orElse(0);
        } else if (current != null) {
            waypointCatches = (int) catches.stream()
                    .filter(item -> current.getTripWaypointId().equals(item.getTripWaypointId()))
                    .count();
        }
        int landed = performance == null ? catches.size() : performance.landedCount();
        return new FishingSessionState.Performance(
                landed,
                performance == null ? null : performance.rawLandedCpue(),
                sinceCatch,
                waypointCatches
        );
    }

    private FishingSessionState.Plan planSlice(
            FishingSession session,
            List<SessionWaypointProgress> progress,
            SessionWaypointProgress current,
            Map<UUID, TripWaypoint> waypoints
    ) {
        GuidancePlanVersionEntity version = planVersionRepository
                .findFirstByFishingSessionIdOrderByVersionDesc(session.getId())
                .orElse(null);
        List<OriginalPlanStep> original = originalPlanSteps(progress, waypoints);
        if (version != null) {
            List<HorizonStep> steps = planStepRepository
                    .findByGuidancePlanVersionIdOrderByStepAsc(version.getId())
                    .stream()
                    .map(RepositoryFishingSessionStateBuilder::toHorizon)
                    .toList();
            Integer currentStep = steps.stream()
                    .filter(HorizonStep::committed)
                    .map(HorizonStep::step)
                    .max(Integer::compareTo)
                    .orElse(steps.isEmpty() ? null : steps.getFirst().step());
            return new FishingSessionState.Plan(
                    session.getTripPlanId(),
                    version.getVersion(),
                    currentStep,
                    steps,
                    original,
                    ActiveGuidanceTarget.fromHorizon(steps)
            );
        }
        return new FishingSessionState.Plan(
                session.getTripPlanId(),
                null,
                current == null ? null : Math.max(1, current.getSequence()),
                planPrior(progress, current, waypoints),
                original,
                null
        );
    }

    private SessionAdHocFishingStop adHocStop(UUID sessionId) {
        if (adHocStopRepository == null || sessionId == null) {
            return null;
        }
        return adHocStopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(sessionId)
                .or(() -> adHocStopRepository.findFirstByFishingSessionIdAndEndedAtIsNotNullOrderByEndedAtDesc(sessionId))
                .orElse(null);
    }

    private static List<OriginalPlanStep> originalPlanSteps(
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints
    ) {
        if (waypoints == null || waypoints.isEmpty()) {
            return List.of();
        }
        Map<UUID, SessionWaypointProgress> byWaypoint = new HashMap<>();
        if (progress != null) {
            for (SessionWaypointProgress row : progress) {
                if (row != null && row.getTripWaypointId() != null) {
                    byWaypoint.put(row.getTripWaypointId(), row);
                }
            }
        }
        return waypoints.values().stream()
                .sorted(Comparator.comparingInt(TripWaypoint::getSequence).thenComparing(TripWaypoint::getId))
                .map(waypoint -> toOriginal(waypoint, byWaypoint.get(waypoint.getId())))
                .toList();
    }

    private static OriginalPlanStep toOriginal(TripWaypoint waypoint, SessionWaypointProgress progress) {
        return new OriginalPlanStep(
                waypoint.getSequence(),
                waypoint.getId(),
                waypoint.getZoneId(),
                waypoint.getFishingTargetId(),
                waypoint.getLakeFeatureId(),
                TripWaypointPlanMetadata.packageMemberIds(waypoint),
                progress == null || progress.getStatus() == null ? null : progress.getStatus().name()
        );
    }

    private static HorizonStep toHorizon(GuidancePlanStepEntity step) {
        return new HorizonStep(step.getStep(), step.getType(), step.getTripWaypointId(), step.getDurationMinutes(), step.isCommitted());
    }

    private static List<HorizonStep> planPrior(
            List<SessionWaypointProgress> progress,
            SessionWaypointProgress current,
            Map<UUID, TripWaypoint> waypoints
    ) {
        if (current == null) {
            return List.of();
        }
        List<HorizonStep> steps = new ArrayList<>();
        int step = 1;
        if (current.getStatus() == WaypointProgressStatus.ARRIVED
                || current.getStatus() == WaypointProgressStatus.FISHING) {
            TripWaypoint waypoint = waypoints.get(current.getTripWaypointId());
            Integer dwell = waypoint == null ? null : waypoint.getPlannedDwellMinutes();
            steps.add(new HorizonStep(step++, GuidanceAction.STAY, current.getTripWaypointId(), dwell, true));
        } else if (current.getStatus() == WaypointProgressStatus.NAVIGATING) {
            steps.add(new HorizonStep(step++, GuidanceAction.MOVE, current.getTripWaypointId(), null, true));
        }
        for (SessionWaypointProgress row : progress) {
            if (steps.size() >= HORIZON_LIMIT) {
                break;
            }
            if (row.getStatus() == WaypointProgressStatus.UPCOMING) {
                steps.add(new HorizonStep(step++, GuidanceAction.MOVE, row.getTripWaypointId(), null, false));
            }
        }
        return List.copyOf(steps);
    }

    private FishingSessionState.OpportunityRevisit opportunityRevisitSlice(
            UUID sessionId,
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints
    ) {
        List<DeliveredDecision> delivered = new ArrayList<>();
        if (deliveredDecisionRepository != null) {
            for (AgentDeliveredDecisionEntity row : deliveredDecisionRepository
                    .findByFishingSessionIdOrderByCreatedAtAsc(sessionId)) {
                if (row == null || row.getDecision() == null) {
                    continue;
                }
                delivered.add(GuidanceContracts.mapper().convertValue(row.getDecision(), DeliveredDecision.class));
            }
        }
        return opportunityRevisitSupport.assemble(progress, waypoints, delivered);
    }

    private static Integer minutesSince(Instant start, Instant now) {
        if (start == null) {
            return null;
        }
        return (int) Math.max(0, Duration.between(start, now).toMinutes());
    }

    private static List<UUID> tailIds(List<UUID> ids) {
        if (ids.size() <= RECENT_LIMIT) {
            return List.copyOf(ids);
        }
        return List.copyOf(ids.subList(ids.size() - RECENT_LIMIT, ids.size()));
    }

    private static Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}

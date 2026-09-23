package com.aifishing.guidance.replay;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.feedback.catchlog.domain.CatchOutcome;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.feedback.catchlog.repo.CatchEventRepository;
import com.aifishing.feedback.effort.domain.EffortSegmentType;
import com.aifishing.feedback.effort.domain.FishingEffortSegment;
import com.aifishing.feedback.effort.repo.FishingEffortSegmentRepository;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.control.AgentRuntimeControl;
import com.aifishing.guidance.control.AgentRuntimeControlStore;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.GuidancePlanStepEntity;
import com.aifishing.guidance.persistence.GuidancePlanStepRepository;
import com.aifishing.guidance.persistence.GuidancePlanVersionEntity;
import com.aifishing.guidance.persistence.GuidancePlanVersionRepository;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxRepository;
import com.aifishing.guidance.persistence.OutcomeAttributionRepository;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.guidance.persistence.UserActionEventRepository;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SessionTimelineQuery {

    private final FishingSessionRepository sessionRepository;
    private final TripRepository tripRepository;
    private final LakeRepository lakeRepository;
    private final SessionEventRepository sessionEventRepository;
    private final GuidanceTriggerOutboxRepository triggerOutboxRepository;
    private final AgentRunRepository agentRunRepository;
    private final UserActionEventRepository userActionEventRepository;
    private final OutcomeAttributionRepository outcomeAttributionRepository;
    private final GuidancePlanVersionRepository planVersionRepository;
    private final GuidancePlanStepRepository planStepRepository;
    private final CatchEventRepository catchEventRepository;
    private final FishingEffortSegmentRepository effortSegmentRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final SessionWaypointProgressRepository progressRepository;
    private final AgentRuntimeControlStore runtimeControlStore;

    public SessionTimelineQuery(
            FishingSessionRepository sessionRepository,
            TripRepository tripRepository,
            LakeRepository lakeRepository,
            SessionEventRepository sessionEventRepository,
            GuidanceTriggerOutboxRepository triggerOutboxRepository,
            AgentRunRepository agentRunRepository,
            UserActionEventRepository userActionEventRepository,
            OutcomeAttributionRepository outcomeAttributionRepository,
            GuidancePlanVersionRepository planVersionRepository,
            GuidancePlanStepRepository planStepRepository,
            CatchEventRepository catchEventRepository,
            FishingEffortSegmentRepository effortSegmentRepository,
            TripWaypointRepository tripWaypointRepository,
            SessionWaypointProgressRepository progressRepository,
            AgentRuntimeControlStore runtimeControlStore
    ) {
        this.sessionRepository = sessionRepository;
        this.tripRepository = tripRepository;
        this.lakeRepository = lakeRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.triggerOutboxRepository = triggerOutboxRepository;
        this.agentRunRepository = agentRunRepository;
        this.userActionEventRepository = userActionEventRepository;
        this.outcomeAttributionRepository = outcomeAttributionRepository;
        this.planVersionRepository = planVersionRepository;
        this.planStepRepository = planStepRepository;
        this.catchEventRepository = catchEventRepository;
        this.effortSegmentRepository = effortSegmentRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.progressRepository = progressRepository;
        this.runtimeControlStore = runtimeControlStore;
    }

    @Transactional(readOnly = true)
    public SessionTimelineResponse load(UUID sessionId) {
        FishingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Fishing session not found"));
        Trip trip = tripRepository.findById(session.getTripId())
                .orElseThrow(() -> new NotFoundException("Trip not found"));
        Lake lake = lakeRepository.findById(trip.getLakeId())
                .orElseThrow(() -> new NotFoundException("Lake not found"));

        var sessionEvents = sessionEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(sessionId);
        var triggers = triggerOutboxRepository.findByFishingSessionIdOrderByCreatedAtAsc(sessionId);
        var runs = agentRunRepository.findByFishingSessionIdOrderByStartedAtAsc(sessionId);
        var actions = userActionEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(sessionId);
        var outcomes = outcomeAttributionRepository.findByFishingSessionIdOrderByAttributedAtAsc(sessionId);
        var horizons = planVersionRepository.findByFishingSessionIdOrderByVersionAsc(sessionId);
        var catches = catchEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(sessionId);
        var effort = effortSegmentRepository.findByFishingSessionIdAndSegmentTypeOrderByStartedAtAsc(
                sessionId, EffortSegmentType.FISHING
        );
        List<TripWaypoint> waypoints = session.getTripPlanId() == null
                ? List.of()
                : tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(session.getTripPlanId());
        Map<UUID, TripWaypoint> waypointsById = waypoints.stream()
                .collect(Collectors.toMap(TripWaypoint::getId, Function.identity()));
        List<SessionWaypointProgress> progress =
                progressRepository.findByFishingSessionIdOrderBySequenceAsc(sessionId);

        AgentRuntimeControl control = runtimeControlStore.load();
        long biteCount = sessionEvents.stream().filter(event -> event.getType() == SessionEventType.BITE).count();
        long fishOnCount = sessionEvents.stream().filter(event -> event.getType() == SessionEventType.FISH_ON).count();
        long landed = catches.stream()
                .filter(catchEvent -> catchEvent.getStatus() != CatchStatus.VOIDED)
                .filter(catchEvent -> catchEvent.getOutcome() == CatchOutcome.LANDED)
                .count();
        long lost = catches.stream()
                .filter(catchEvent -> catchEvent.getStatus() != CatchStatus.VOIDED)
                .filter(catchEvent -> catchEvent.getOutcome() == CatchOutcome.LOST)
                .count();
        int fishingEffortSeconds = effort.stream().mapToInt(FishingEffortSegment::getDurationSeconds).sum();

        SessionReplaySummary summary = new SessionReplaySummary(
                session.getId(),
                lake.getId(),
                lake.getName(),
                trip.getPrimaryTargetSpecies(),
                session.getStartedAt(),
                session.getEndedAt(),
                control.productionVersion(),
                control.candidateVersion(),
                fishingEffortSeconds,
                biteCount,
                fishOnCount,
                landed,
                lost
        );
        return new SessionTimelineResponse(
                summary,
                TimelineAssembler.assemble(sessionEvents, triggers, runs, actions, outcomes, horizons, catches),
                originalPlan(waypoints),
                latestShortHorizon(horizons, waypointsById),
                actualProgress(progress, waypointsById)
        );
    }

    private static List<OriginalPlanStop> originalPlan(List<TripWaypoint> waypoints) {
        List<OriginalPlanStop> stops = new ArrayList<>();
        for (TripWaypoint waypoint : waypoints) {
            stops.add(new OriginalPlanStop(
                    waypoint.getId(),
                    waypoint.getSequence(),
                    lat(waypoint.getLocation()),
                    lng(waypoint.getLocation()),
                    waypoint.getFeatureType() == null ? null : waypoint.getFeatureType().name()
            ));
        }
        return List.copyOf(stops);
    }

    private List<HorizonPlanStep> latestShortHorizon(
            List<GuidancePlanVersionEntity> horizons,
            Map<UUID, TripWaypoint> waypointsById
    ) {
        if (horizons == null || horizons.isEmpty()) {
            return List.of();
        }
        GuidancePlanVersionEntity latest = horizons.getLast();
        List<HorizonPlanStep> steps = new ArrayList<>();
        for (GuidancePlanStepEntity step : planStepRepository.findByGuidancePlanVersionIdOrderByStepAsc(latest.getId())) {
            TripWaypoint waypoint = step.getTripWaypointId() == null ? null : waypointsById.get(step.getTripWaypointId());
            Point location = waypoint == null ? null : waypoint.getLocation();
            steps.add(new HorizonPlanStep(
                    step.getStep(),
                    step.getType(),
                    step.getTripWaypointId(),
                    lat(location),
                    lng(location),
                    step.getDurationMinutes(),
                    step.isCommitted()
            ));
        }
        return List.copyOf(steps);
    }

    private static List<ActualProgressStop> actualProgress(
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypointsById
    ) {
        List<ActualProgressStop> stops = new ArrayList<>();
        for (SessionWaypointProgress row : progress) {
            TripWaypoint waypoint = waypointsById.get(row.getTripWaypointId());
            Point location = waypoint == null ? null : waypoint.getLocation();
            stops.add(new ActualProgressStop(
                    row.getTripWaypointId(),
                    row.getSequence(),
                    row.getStatus() == null ? null : row.getStatus().name(),
                    row.getArrivedAt(),
                    row.getDepartedAt(),
                    lat(location),
                    lng(location),
                    row.getAccumulatedDwellSeconds()
            ));
        }
        return List.copyOf(stops);
    }

    static Double lat(Point point) {
        return point == null ? null : point.getY();
    }

    static Double lng(Point point) {
        return point == null ? null : point.getX();
    }
}

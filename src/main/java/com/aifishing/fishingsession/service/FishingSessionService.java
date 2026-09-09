package com.aifishing.fishingsession.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.feedback.effort.domain.SessionPauseInterval;
import com.aifishing.feedback.effort.repo.SessionPauseIntervalRepository;
import com.aifishing.feedback.effort.service.FishingEffortService;
import com.aifishing.feedback.performance.EmpiricalPerformanceService;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.ClientEventType;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionClientEvent;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.domain.WaypointSkipReason;
import com.aifishing.fishingsession.dto.StartFishingSessionRequest.LateStartMode;
import com.aifishing.fishingsession.dto.AdminFishingSessionResponse;
import com.aifishing.fishingsession.dto.ClientEventRequest;
import com.aifishing.fishingsession.dto.FishingSessionResponse;
import com.aifishing.fishingsession.dto.LocationBatchRequest;
import com.aifishing.fishingsession.dto.LocationPointRequest;
import com.aifishing.fishingsession.dto.NavigationResponse;
import com.aifishing.fishingsession.dto.SessionTrackResponse;
import com.aifishing.fishingsession.dto.StartFishingSessionRequest;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionClientEventRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripPlanRepository;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.planning.spatial.TransitLegMaterializer;
import com.aifishing.planning.spatial.domain.TripPlanTransitLeg;
import com.aifishing.planning.spatial.repo.TripPlanTransitLegRepository;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FishingSessionService {

    private static final EnumSet<FishingSessionStatus> UNFINISHED =
            EnumSet.of(FishingSessionStatus.ACTIVE, FishingSessionStatus.PAUSED);
    private static final EnumSet<TripPlanStatus> STARTABLE =
            EnumSet.of(TripPlanStatus.GENERATED, TripPlanStatus.ACCEPTED);

    private final CurrentUser currentUser;
    private final Clock clock;
    private final SessionProperties sessionProperties;
    private final GeoMapper geoMapper;
    private final TripRepository tripRepository;
    private final TripPlanRepository tripPlanRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final FishingSessionRepository sessionRepository;
    private final SessionWaypointProgressRepository progressRepository;
    private final SessionLocationPointRepository locationPointRepository;
    private final SessionClientEventRepository clientEventRepository;
    private final SessionPauseIntervalRepository pauseIntervalRepository;
    private final FishingEffortService fishingEffortService;
    private final EmpiricalPerformanceService empiricalPerformanceService;
    private final WaypointProgressMachine waypointMachine;
    private final SessionMapper mapper;
    private final TransitLegMaterializer transitLegMaterializer;
    private final TripPlanTransitLegRepository transitLegRepository;

    public FishingSessionService(
            CurrentUser currentUser,
            Clock clock,
            SessionProperties sessionProperties,
            GeoMapper geoMapper,
            TripRepository tripRepository,
            TripPlanRepository tripPlanRepository,
            TripWaypointRepository tripWaypointRepository,
            FishingSessionRepository sessionRepository,
            SessionWaypointProgressRepository progressRepository,
            SessionLocationPointRepository locationPointRepository,
            SessionClientEventRepository clientEventRepository,
            SessionPauseIntervalRepository pauseIntervalRepository,
            FishingEffortService fishingEffortService,
            EmpiricalPerformanceService empiricalPerformanceService,
            WaypointProgressMachine waypointMachine,
            SessionMapper mapper,
            TransitLegMaterializer transitLegMaterializer,
            TripPlanTransitLegRepository transitLegRepository
    ) {
        this.currentUser = currentUser;
        this.clock = clock;
        this.sessionProperties = sessionProperties;
        this.geoMapper = geoMapper;
        this.tripRepository = tripRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.sessionRepository = sessionRepository;
        this.progressRepository = progressRepository;
        this.locationPointRepository = locationPointRepository;
        this.clientEventRepository = clientEventRepository;
        this.pauseIntervalRepository = pauseIntervalRepository;
        this.fishingEffortService = fishingEffortService;
        this.empiricalPerformanceService = empiricalPerformanceService;
        this.waypointMachine = waypointMachine;
        this.mapper = mapper;
        this.transitLegMaterializer = transitLegMaterializer;
        this.transitLegRepository = transitLegRepository;
    }

    @Transactional
    public FishingSessionResponse start(UUID tripId, StartFishingSessionRequest request) {
        UUID userId = currentUser.id();
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));
        if (sessionRepository.existsByUserIdAndStatusIn(userId, UNFINISHED)) {
            throw new BadRequestException("An unfinished fishing session already exists");
        }
        TripPlan plan = resolveStartablePlan(trip.getId(), request == null ? null : request.tripPlanId());
        List<TripWaypoint> waypoints = tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(plan.getId());
        if (waypoints.isEmpty()) {
            throw new BadRequestException("Plan has no waypoints");
        }
        Instant now = clock.instant();
        materializeLegsIfMissing(plan, waypoints);
        FishingSession session = new FishingSession();
        session.setTripId(trip.getId());
        session.setUserId(userId);
        session.setTripPlanId(plan.getId());
        session.setPlanVersion(plan.getVersion());
        session.setStartedAt(now);
        session.setStatus(FishingSessionStatus.ACTIVE);
        session.setTotalPausedSeconds(0);
        sessionRepository.save(session);

        Set<UUID> skipIds = resolveLateStartSkips(request, waypoints, now);
        List<SessionWaypointProgress> progress = new ArrayList<>();
        boolean firstActive = true;
        for (TripWaypoint waypoint : waypoints) {
            SessionWaypointProgress row = new SessionWaypointProgress();
            row.setFishingSessionId(session.getId());
            row.setTripWaypointId(waypoint.getId());
            row.setSequence(waypoint.getSequence());
            if (matchesSkip(skipIds, waypoint)) {
                row.setStatus(WaypointProgressStatus.SKIPPED);
                row.setSkipReason(WaypointSkipReason.LATE_START);
                row.setSkippedAt(now);
            } else if (firstActive) {
                row.setStatus(WaypointProgressStatus.NAVIGATING);
                firstActive = false;
            } else {
                row.setStatus(WaypointProgressStatus.UPCOMING);
            }
            progress.add(row);
        }
        progressRepository.saveAll(progress);
        return mapper.session(
                session,
                progress,
                SessionMapper.indexWaypoints(waypoints),
                SessionMapper.launchPointFromPlan(plan.getMetadata())
        );
    }

    @Transactional(readOnly = true)
    public FishingSessionResponse get(UUID sessionId) {
        return toResponse(requireOwned(sessionId));
    }

    @Transactional(readOnly = true)
    public NavigationResponse navigation(UUID sessionId) {
        FishingSession session = requireOwned(sessionId);
        List<SessionWaypointProgress> progress = progressRepository.findByFishingSessionIdOrderBySequenceAsc(session.getId());
        Map<UUID, TripWaypoint> waypoints = loadWaypoints(session.getTripPlanId());
        return mapper.navigation(session, progress, waypoints);
    }

    @Transactional(readOnly = true)
    public SessionTrackResponse track(UUID sessionId) {
        FishingSession session = requireOwned(sessionId);
        return mapper.track(
                session.getId(),
                locationPointRepository.findByFishingSessionIdOrderByRecordedAtAsc(session.getId())
        );
    }

    @Transactional(readOnly = true)
    public AdminFishingSessionResponse adminGet(UUID sessionId) {
        FishingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Fishing session not found"));
        List<SessionWaypointProgress> progress = progressRepository.findByFishingSessionIdOrderBySequenceAsc(session.getId());
        return mapper.admin(session, progress, loadWaypoints(session.getTripPlanId()));
    }

    @Transactional
    public FishingSessionResponse ingestLocations(UUID sessionId, LocationBatchRequest request) {
        FishingSession session = requireOwned(sessionId);
        if (session.getStatus().isTerminal()) {
            throw new BadRequestException("Session is already " + session.getStatus());
        }
        List<LocationPointRequest> points = request == null || request.points() == null ? List.of() : request.points();
        if (points.isEmpty()) {
            throw new BadRequestException("Location batch must not be empty");
        }
        if (points.size() > sessionProperties.getLocation().getMaxBatchSize()) {
            throw new BadRequestException("Location batch exceeds max size of "
                    + sessionProperties.getLocation().getMaxBatchSize());
        }
        Instant receivedAt = clock.instant();
        List<SessionLocationPoint> inserted = persistPoints(session, points, receivedAt);
        if (session.getStatus() == FishingSessionStatus.ACTIVE) {
            advanceFromGps(session, inserted);
        }
        return toResponse(session);
    }

    @Transactional
    public FishingSessionResponse arrive(UUID sessionId, UUID waypointId, ClientEventRequest request) {
        return applyWaypointAction(sessionId, waypointId, request, ClientEventType.ARRIVE);
    }

    @Transactional
    public FishingSessionResponse skip(UUID sessionId, UUID waypointId, ClientEventRequest request) {
        return applyWaypointAction(sessionId, waypointId, request, ClientEventType.SKIP);
    }

    @Transactional
    public FishingSessionResponse complete(UUID sessionId, UUID waypointId, ClientEventRequest request) {
        return applyWaypointAction(sessionId, waypointId, request, ClientEventType.COMPLETE);
    }

    @Transactional
    public FishingSessionResponse pause(UUID sessionId, ClientEventRequest request) {
        FishingSession session = requireOwned(sessionId);
        if (isDuplicateEvent(session, request)) {
            return toResponse(session);
        }
        requireMutable(session);
        persistEvent(session, request, ClientEventType.PAUSE, Map.of());
        if (session.getStatus() == FishingSessionStatus.ACTIVE) {
            session.setStatus(FishingSessionStatus.PAUSED);
            session.setPausedAt(request.occurredAt());
            openPauseInterval(session, request.occurredAt());
        }
        return toResponse(session);
    }

    @Transactional
    public FishingSessionResponse resume(UUID sessionId, ClientEventRequest request) {
        FishingSession session = requireOwned(sessionId);
        if (isDuplicateEvent(session, request)) {
            return toResponse(session);
        }
        requireMutable(session);
        persistEvent(session, request, ClientEventType.RESUME, Map.of());
        if (session.getStatus() == FishingSessionStatus.PAUSED) {
            foldOpenPause(session, request.occurredAt());
            closeOpenPauseInterval(session, request.occurredAt());
            session.setStatus(FishingSessionStatus.ACTIVE);
        }
        return toResponse(session);
    }

    @Transactional
    public FishingSessionResponse end(UUID sessionId, ClientEventRequest request) {
        FishingSession session = requireOwned(sessionId);
        if (isDuplicateEvent(session, request)) {
            return toResponse(session);
        }
        if (session.getStatus() == FishingSessionStatus.COMPLETED) {
            persistEvent(session, request, ClientEventType.END, Map.of());
            return toResponse(session);
        }
        if (session.getStatus() == FishingSessionStatus.CANCELLED) {
            throw new BadRequestException("Session is already CANCELLED");
        }
        persistEvent(session, request, ClientEventType.END, Map.of());
        Instant endedAt = request.occurredAt();
        foldOpenPause(session, endedAt);
        closeOpenPauseInterval(session, endedAt);
        List<SessionWaypointProgress> progress = progressRepository.findByFishingSessionIdOrderBySequenceAsc(session.getId());
        waypointMachine.finalizeInProgress(progress, endedAt);
        progressRepository.saveAll(progress);
        session.setStatus(FishingSessionStatus.COMPLETED);
        session.setEndedAt(endedAt);
        session.setPausedAt(null);
        session.setSummary(buildSummary(session, progress, endedAt));
        fishingEffortService.recompute(session.getId());
        empiricalPerformanceService.recompute(session.getId());
        return toResponse(session);
    }

    private FishingSessionResponse applyWaypointAction(
            UUID sessionId,
            UUID waypointId,
            ClientEventRequest request,
            ClientEventType type
    ) {
        FishingSession session = requireOwned(sessionId);
        Map<String, Object> payload = Map.of("waypointId", waypointId.toString());
        if (isDuplicateEvent(session, request)) {
            return toResponse(session);
        }
        requireMutable(session);
        persistEvent(session, request, type, payload);
        List<SessionWaypointProgress> progress = progressRepository.findByFishingSessionIdOrderBySequenceAsc(session.getId());
        SessionWaypointProgress row = progress.stream()
                .filter(item -> item.getTripWaypointId().equals(waypointId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Waypoint not found"));
        Instant at = request.occurredAt();
        switch (type) {
            case ARRIVE -> waypointMachine.manualArrive(row, at);
            case SKIP -> waypointMachine.manualSkip(row, at, progress);
            case COMPLETE -> waypointMachine.manualComplete(row, at, progress);
            default -> throw new BadRequestException("Unsupported waypoint action");
        }
        progressRepository.saveAll(progress);
        return toResponse(session);
    }

    private boolean isDuplicateEvent(FishingSession session, ClientEventRequest request) {
        requireEvent(request);
        return clientEventRepository
                .findByFishingSessionIdAndClientEventId(session.getId(), request.clientEventId())
                .isPresent();
    }

    private void persistEvent(
            FishingSession session,
            ClientEventRequest request,
            ClientEventType type,
            Map<String, Object> payload
    ) {
        SessionClientEvent event = new SessionClientEvent();
        event.setFishingSessionId(session.getId());
        event.setClientEventId(request.clientEventId());
        event.setType(type);
        event.setOccurredAt(request.occurredAt());
        event.setReceivedAt(clock.instant());
        event.setPayload(payload.isEmpty() ? null : new HashMap<>(payload));
        event.setApplied(true);
        clientEventRepository.save(event);
    }

    private static void requireEvent(ClientEventRequest request) {
        if (request == null || request.clientEventId() == null || request.clientEventId().isBlank()) {
            throw new BadRequestException("clientEventId is required");
        }
        if (request.occurredAt() == null) {
            throw new BadRequestException("occurredAt is required");
        }
    }

    private List<SessionLocationPoint> persistPoints(
            FishingSession session,
            List<LocationPointRequest> points,
            Instant receivedAt
    ) {
        List<LocationPointRequest> ordered = points.stream()
                .sorted(Comparator.comparing(LocationPointRequest::recordedAt)
                        .thenComparing(LocationPointRequest::clientPointId))
                .toList();
        Set<String> ids = ordered.stream().map(LocationPointRequest::clientPointId).collect(java.util.stream.Collectors.toSet());
        Set<String> existing = locationPointRepository.findExistingClientPointIds(session.getId(), ids);
        SessionLocationPoint previous = locationPointRepository
                .findFirstByFishingSessionIdOrderByRecordedAtDesc(session.getId())
                .orElse(null);
        List<SessionLocationPoint> toSave = new ArrayList<>();
        for (LocationPointRequest request : ordered) {
            if (existing.contains(request.clientPointId())) {
                continue;
            }
            SessionLocationPoint point = new SessionLocationPoint();
            point.setFishingSessionId(session.getId());
            point.setClientPointId(request.clientPointId());
            point.setRecordedAt(request.recordedAt());
            point.setReceivedAt(receivedAt);
            point.setLocation(geoMapper.toPoint(request.location()));
            point.setAccuracyM(decimal(request.accuracyM(), 2));
            point.setAltitudeM(decimal(request.altitudeM(), 2));
            point.setSpeedMps(decimal(request.speedMps(), 3));
            point.setHeadingDegrees(decimal(request.headingDegrees(), 2));
            point.setQuality(classify(request, point.getLocation(), previous));
            toSave.add(point);
            previous = point;
        }
        if (!toSave.isEmpty()) {
            locationPointRepository.saveAll(toSave);
        }
        return toSave;
    }

    private LocationQuality classify(LocationPointRequest request, Point location, SessionLocationPoint previous) {
        SessionProperties.Location cfg = sessionProperties.getLocation();
        if (request.accuracyM() != null && request.accuracyM() > cfg.getMaxAccuracyM()) {
            return LocationQuality.LOW_QUALITY;
        }
        if (previous != null && previous.getLocation() != null && location != null) {
            double distanceM = GeoMetrics.distanceM(previous.getLocation(), location);
            double seconds = Math.abs(Duration.between(previous.getRecordedAt(), request.recordedAt()).toMillis()) / 1000.0;
            if (seconds <= 0) {
                if (distanceM > 1.0) {
                    return LocationQuality.LOW_QUALITY;
                }
            } else if (distanceM / seconds > cfg.getImpossibleSpeedMps()) {
                return LocationQuality.LOW_QUALITY;
            }
        }
        return LocationQuality.ACCEPTED;
    }

    private void advanceFromGps(FishingSession session, List<SessionLocationPoint> inserted) {
        List<SessionLocationPoint> newlyAccepted = inserted.stream()
                .filter(point -> point.getQuality() == LocationQuality.ACCEPTED)
                .sorted(Comparator.comparing(SessionLocationPoint::getRecordedAt)
                        .thenComparing(SessionLocationPoint::getClientPointId))
                .toList();
        if (newlyAccepted.isEmpty()) {
            return;
        }
        List<SessionWaypointProgress> progress = progressRepository.findByFishingSessionIdOrderBySequenceAsc(session.getId());
        Map<UUID, TripWaypoint> waypoints = loadWaypoints(session.getTripPlanId());
        List<SessionLocationPoint> accepted = locationPointRepository
                .findByFishingSessionIdAndQualityOrderByRecordedAtAsc(session.getId(), LocationQuality.ACCEPTED);
        for (SessionLocationPoint neu : newlyAccepted) {
            List<SessionLocationPoint> prefix = accepted.stream()
                    .filter(point -> !point.getRecordedAt().isAfter(neu.getRecordedAt()))
                    .toList();
            waypointMachine.applyAcceptedHistory(progress, waypoints, prefix);
            maybeCompleteReturn(session, progress, prefix);
        }
        progressRepository.saveAll(progress);
    }

    private Map<String, Object> buildSummary(
            FishingSession session,
            List<SessionWaypointProgress> progress,
            Instant endedAt
    ) {
        long wallSeconds = Math.max(0, Duration.between(session.getStartedAt(), endedAt).getSeconds());
        int paused = session.getTotalPausedSeconds();
        long active = Math.max(0, wallSeconds - paused);
        List<SessionLocationPoint> accepted = locationPointRepository
                .findByFishingSessionIdAndQualityOrderByRecordedAtAsc(session.getId(), LocationQuality.ACCEPTED);
        double pathM = 0;
        for (int i = 1; i < accepted.size(); i++) {
            pathM += GeoMetrics.distanceM(accepted.get(i - 1).getLocation(), accepted.get(i).getLocation());
        }
        long completed = progress.stream().filter(row -> row.getStatus() == WaypointProgressStatus.COMPLETED).count();
        long skipped = progress.stream().filter(row -> row.getStatus() == WaypointProgressStatus.SKIPPED).count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalPausedSeconds", paused);
        summary.put("activeFishingSeconds", active);
        summary.put("acceptedPointCount", accepted.size());
        summary.put("pathDistanceM", round1(pathM));
        summary.put("completedWaypoints", completed);
        summary.put("skippedWaypoints", skipped);
        return summary;
    }

    private void foldOpenPause(FishingSession session, Instant at) {
        if (session.getPausedAt() == null) {
            return;
        }
        int extra = (int) Math.max(0, Duration.between(session.getPausedAt(), at).getSeconds());
        session.setTotalPausedSeconds(session.getTotalPausedSeconds() + extra);
        session.setPausedAt(null);
    }

    private void openPauseInterval(FishingSession session, Instant pausedAt) {
        if (pauseIntervalRepository.findFirstByFishingSessionIdAndResumedAtIsNull(session.getId()).isPresent()) {
            return;
        }
        SessionPauseInterval interval = new SessionPauseInterval();
        interval.setFishingSessionId(session.getId());
        interval.setPausedAt(pausedAt);
        pauseIntervalRepository.save(interval);
    }

    private void closeOpenPauseInterval(FishingSession session, Instant resumedAt) {
        pauseIntervalRepository.findFirstByFishingSessionIdAndResumedAtIsNull(session.getId())
                .ifPresent(interval -> interval.setResumedAt(resumedAt));
    }

    private void requireMutable(FishingSession session) {
        if (session.getStatus().isTerminal()) {
            throw new BadRequestException("Session is already " + session.getStatus());
        }
    }

    private FishingSession requireOwned(UUID sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Fishing session not found"));
    }

    private TripPlan resolveStartablePlan(UUID tripId, UUID tripPlanId) {
        if (tripPlanId != null) {
            TripPlan plan = tripPlanRepository.findById(tripPlanId)
                    .filter(candidate -> candidate.getTripId().equals(tripId))
                    .orElseThrow(() -> new NotFoundException("Trip plan not found"));
            if (!STARTABLE.contains(plan.getStatus())) {
                throw new BadRequestException("Plan must be GENERATED or ACCEPTED");
            }
            return plan;
        }
        return tripPlanRepository.findFirstByTripIdAndStatusInOrderByVersionDesc(tripId, STARTABLE)
                .orElseThrow(() -> new BadRequestException("No GENERATED or ACCEPTED plan for this trip"));
    }

    private FishingSessionResponse toResponse(FishingSession session) {
        List<SessionWaypointProgress> progress = progressRepository.findByFishingSessionIdOrderBySequenceAsc(session.getId());
        TripPlan plan = session.getTripPlanId() == null
                ? null
                : tripPlanRepository.findById(session.getTripPlanId()).orElse(null);
        return mapper.session(
                session,
                progress,
                loadWaypoints(session.getTripPlanId()),
                plan == null ? null : SessionMapper.launchPointFromPlan(plan.getMetadata())
        );
    }

    private void materializeLegsIfMissing(TripPlan plan, List<TripWaypoint> waypoints) {
        if (plan == null || transitLegRepository.countByTripPlanId(plan.getId()) > 0) {
            return;
        }
        try {
            List<TripPlanTransitLeg> legs = transitLegMaterializer.materializeExistingPlan(plan, waypoints);
            for (TripPlanTransitLeg leg : legs) {
                leg.setTripPlanId(plan.getId());
                transitLegRepository.save(leg);
            }
        } catch (RuntimeException ex) {
            // Legacy plans still start; FE falls back to entry + copy.
        }
    }

    private Set<UUID> resolveLateStartSkips(
            StartFishingSessionRequest request,
            List<TripWaypoint> waypoints,
            Instant now
    ) {
        Set<UUID> skipIds = new HashSet<>();
        if (request != null && request.skipVisitIds() != null) {
            skipIds.addAll(request.skipVisitIds());
        }
        if (request != null && request.lateStart() == LateStartMode.SKIP_EXPIRED) {
            for (TripWaypoint waypoint : waypoints) {
                if (isExpired(waypoint, now)) {
                    skipIds.add(waypoint.getId());
                    UUID visitId = visitId(waypoint);
                    if (visitId != null) {
                        skipIds.add(visitId);
                    }
                }
            }
        }
        return skipIds;
    }

    static boolean isExpired(TripWaypoint waypoint, Instant now) {
        Instant departure = waypoint.getPlannedDepartureAt();
        return departure != null && !departure.isAfter(now);
    }

    private static boolean matchesSkip(Set<UUID> skipIds, TripWaypoint waypoint) {
        if (skipIds.isEmpty()) {
            return false;
        }
        if (skipIds.contains(waypoint.getId())) {
            return true;
        }
        UUID visitId = visitId(waypoint);
        return visitId != null && skipIds.contains(visitId);
    }

    private static UUID visitId(TripWaypoint waypoint) {
        Map<String, Object> metadata = waypoint.getMetadata();
        if (metadata != null && metadata.get("visitId") != null) {
            try {
                return UUID.fromString(String.valueOf(metadata.get("visitId")));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return waypoint.getVisitScopeId();
    }

    private void maybeCompleteReturn(
            FishingSession session,
            List<SessionWaypointProgress> progress,
            List<SessionLocationPoint> acceptedUpToCurrent
    ) {
        if (!SessionMapper.returningToLaunch(session, progress) || acceptedUpToCurrent.isEmpty()) {
            return;
        }
        TripPlan plan = tripPlanRepository.findById(session.getTripPlanId()).orElse(null);
        if (plan == null) {
            return;
        }
        var launchDto = SessionMapper.launchPointFromPlan(plan.getMetadata());
        if (launchDto == null) {
            return;
        }
        Point launch = geoMapper.toPoint(launchDto);
        SessionProperties.Waypoint cfg = sessionProperties.getWaypoint();
        SessionLocationPoint last = acceptedUpToCurrent.getLast();
        double distanceM = GeoMetrics.distanceM(last.getLocation(), launch);
        if (distanceM > cfg.getArrivalRadiusM()) {
            return;
        }
        List<SessionLocationPoint> window = WaypointProgressMachine.trailingInside(
                acceptedUpToCurrent, launch, cfg.getArrivalRadiusM());
        if (!WaypointProgressMachine.arrivalConfirmed(
                window, cfg.getArrivalConfirmSeconds(), cfg.getArrivalConfirmSamples())) {
            return;
        }
        Instant at = last.getRecordedAt();
        foldOpenPause(session, at);
        closeOpenPauseInterval(session, at);
        session.setStatus(FishingSessionStatus.COMPLETED);
        session.setEndedAt(at);
        session.setPausedAt(null);
        session.setSummary(buildSummary(session, progress, at));
        fishingEffortService.recompute(session.getId());
        empiricalPerformanceService.recompute(session.getId());
    }

    private Map<UUID, TripWaypoint> loadWaypoints(UUID tripPlanId) {
        if (tripPlanId == null) {
            return Map.of();
        }
        return SessionMapper.indexWaypoints(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(tripPlanId));
    }

    private static BigDecimal decimal(Double value, int scale) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

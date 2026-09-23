package com.aifishing.fishingsession.service;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionAdHocFishingStop;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.dto.AdHocFishingStopResponse;
import com.aifishing.fishingsession.dto.AdminFishingSessionResponse;
import com.aifishing.fishingsession.dto.FishingSessionResponse;
import com.aifishing.fishingsession.dto.NavigationResponse;
import com.aifishing.fishingsession.dto.SessionTrackResponse;
import com.aifishing.fishingsession.dto.WaypointProgressResponse;
import com.aifishing.fishingsession.repo.SessionAdHocFishingStopRepository;
import com.aifishing.fishingsession.repo.SessionClientEventRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.horizon.ActiveGuidanceTarget;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.dto.TransitLegResponse;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.service.TripPlanAssembler;
import com.aifishing.planning.spatial.TransitEndpointKind;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SessionMapper {

    private final GeoMapper geoMapper;
    private final SessionLocationPointRepository locationPointRepository;
    private final SessionClientEventRepository clientEventRepository;
    private final SessionAdHocFishingStopRepository adHocStopRepository;
    private final TripPlanAssembler tripPlanAssembler;
    private final StationaryFishingDetector stationaryFishingDetector;
    private final SessionProperties sessionProperties;
    private final Clock clock;
    private final ActiveGuidanceTarget activeGuidanceTarget;

    public SessionMapper(
            GeoMapper geoMapper,
            SessionLocationPointRepository locationPointRepository,
            SessionClientEventRepository clientEventRepository,
            SessionAdHocFishingStopRepository adHocStopRepository,
            TripPlanAssembler tripPlanAssembler,
            StationaryFishingDetector stationaryFishingDetector,
            SessionProperties sessionProperties,
            Clock clock,
            ActiveGuidanceTarget activeGuidanceTarget
    ) {
        this.geoMapper = geoMapper;
        this.locationPointRepository = locationPointRepository;
        this.clientEventRepository = clientEventRepository;
        this.adHocStopRepository = adHocStopRepository;
        this.tripPlanAssembler = tripPlanAssembler;
        this.stationaryFishingDetector = stationaryFishingDetector;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
        this.activeGuidanceTarget = activeGuidanceTarget;
    }

    public FishingSessionResponse session(
            FishingSession session,
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints
    ) {
        return session(session, progress, waypoints, null);
    }

    public FishingSessionResponse session(
            FishingSession session,
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints,
            GeoPointDto launchPoint
    ) {
        List<WaypointProgressResponse> rows = progress.stream()
                .map(row -> waypoint(row, waypoints.get(row.getTripWaypointId())))
                .toList();
        List<TransitLegResponse> legs = tripPlanAssembler.toTransitLegs(session.getTripPlanId());
        boolean returning = returningToLaunch(session, progress);
        UUID currentLegId = currentTransitLegId(progress, legs, returning);
        SessionAdHocFishingStop openStop = openStop(session);
        return new FishingSessionResponse(
                session.getId(),
                session.getTripId(),
                session.getUserId(),
                session.getTripPlanId(),
                session.getPlanVersion(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getPausedAt(),
                session.getTotalPausedSeconds(),
                session.getSummary(),
                rows,
                legs,
                returning,
                launchPoint,
                currentLegId,
                activityState(session),
                activitySource(session),
                toAdHocResponse(openStop),
                stationaryPrompt(session, progress, waypoints, openStop),
                resolveActiveTarget(session),
                session.getGuidanceMode()
        );
    }

    /**
     * Live recommended fishing window. Prefers an actual fishing-start timestamp if the
     * session model records one; otherwise uses {@code arrivedAt}. ARRIVE currently enters
     * FISHING immediately, so {@code arrivedAt} is today's approximation — do not add a
     * persistence model solely for this UI.
     */
    public WaypointProgressResponse waypoint(SessionWaypointProgress row, TripWaypoint waypoint) {
        Instant recommendedStartAt = recommendedFishingStart(row);
        return new WaypointProgressResponse(
                row.getId(),
                row.getTripWaypointId(),
                row.getSequence(),
                row.getStatus(),
                row.getSkipReason(),
                waypoint == null ? null : geoMapper.toDto(waypoint.getLocation()),
                row.getFirstApproachedAt(),
                row.getArrivedAt(),
                row.getDepartedAt(),
                row.getCompletedAt(),
                row.getSkippedAt(),
                row.getAccumulatedDwellSeconds(),
                toDouble(row.getClosestDistanceM()),
                waypoint == null || waypoint.getRecommendedTechniques() == null
                        ? List.of()
                        : waypoint.getRecommendedTechniques(),
                recommendedStartAt,
                recommendedFishingEnd(recommendedStartAt, waypoint)
        );
    }

    public NavigationResponse navigation(
            FishingSession session,
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints
    ) {
        SessionWaypointProgress current = currentWaypoint(progress);
        WaypointProgressResponse currentDto = current == null
                ? null
                : waypoint(current, waypoints.get(current.getTripWaypointId()));
        SessionLocationPoint lastAccepted = locationPointRepository
                .findFirstByFishingSessionIdAndQualityOrderByRecordedAtDesc(session.getId(), LocationQuality.ACCEPTED)
                .orElse(null);
        Double distanceM = null;
        Double bearingDegrees = null;
        if (current != null && lastAccepted != null) {
            TripWaypoint planned = waypoints.get(current.getTripWaypointId());
            if (planned != null) {
                Point here = lastAccepted.getLocation();
                Point there = planned.resolvedEntryPoint();
                if (there != null) {
                    if (planned.getFishingCorridor() != null && !planned.getFishingCorridor().isEmpty()) {
                        distanceM = round1(GeoMetrics.distanceM(here, planned.getFishingCorridor()));
                    } else {
                        distanceM = round1(GeoMetrics.distanceM(here, there));
                    }
                    bearingDegrees = round1(GeoMetrics.bearingDegrees(here, there));
                }
            }
        }
        List<TransitLegResponse> legs = tripPlanAssembler.toTransitLegs(session.getTripPlanId());
        boolean returning = returningToLaunch(session, progress);
        SessionAdHocFishingStop openStop = openStop(session);
        return new NavigationResponse(
                session.getId(),
                session.getStatus(),
                currentDto,
                lastAccepted == null ? null : geoMapper.toDto(lastAccepted.getLocation()),
                lastAccepted == null ? null : lastAccepted.getRecordedAt(),
                distanceM,
                bearingDegrees,
                currentTransitLegId(progress, legs, returning),
                activityState(session),
                activitySource(session),
                toAdHocResponse(openStop),
                stationaryPrompt(session, progress, waypoints, openStop),
                resolveActiveTarget(session)
        );
    }

    public static boolean returningToLaunch(FishingSession session, List<SessionWaypointProgress> progress) {
        return session != null
                && session.getStatus() == FishingSessionStatus.ACTIVE
                && currentWaypoint(progress) == null
                && progress != null
                && !progress.isEmpty();
    }

    static UUID currentTransitLegId(
            List<SessionWaypointProgress> progress,
            List<TransitLegResponse> legs,
            boolean returning
    ) {
        if (legs == null || legs.isEmpty()) {
            return null;
        }
        if (returning) {
            return legs.stream()
                    .filter(leg -> leg.toKind() == TransitEndpointKind.RETURN)
                    .reduce((first, second) -> second)
                    .map(TransitLegResponse::id)
                    .orElse(legs.getLast().id());
        }
        SessionWaypointProgress current = currentWaypoint(progress);
        if (current == null) {
            return null;
        }
        return legs.stream()
                .filter(leg -> leg.sequence() == current.getSequence())
                .findFirst()
                .map(TransitLegResponse::id)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static GeoPointDto launchPointFromPlan(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object launch = metadata.get("launchSelection");
        if (launch instanceof Map<?, ?> map) {
            Object routeStart = map.get("routeStartPoint");
            if (routeStart instanceof Map<?, ?> point) {
                Object lat = point.get("lat");
                Object lng = point.get("lng");
                if (lat != null && lng != null) {
                    return new GeoPointDto(
                            Double.parseDouble(String.valueOf(lat)),
                            Double.parseDouble(String.valueOf(lng))
                    );
                }
            }
        }
        return null;
    }

    public SessionTrackResponse track(UUID sessionId, List<SessionLocationPoint> points) {
        return new SessionTrackResponse(
                sessionId,
                points.stream()
                        .map(point -> new SessionTrackResponse.TrackPoint(
                                point.getRecordedAt(),
                                geoMapper.toDto(point.getLocation()),
                                point.getQuality(),
                                toDouble(point.getAccuracyM())
                        ))
                        .toList()
        );
    }

    public AdminFishingSessionResponse admin(
            FishingSession session,
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints
    ) {
        return new AdminFishingSessionResponse(
                session(session, progress, waypoints),
                locationPointRepository.countByFishingSessionId(session.getId()),
                locationPointRepository.countByFishingSessionIdAndQuality(session.getId(), LocationQuality.ACCEPTED),
                clientEventRepository.countByFishingSessionId(session.getId())
        );
    }

    public static SessionWaypointProgress progressForWaypoint(
            List<SessionWaypointProgress> progress,
            UUID tripWaypointId
    ) {
        if (progress == null || tripWaypointId == null) {
            return null;
        }
        return progress.stream()
                .filter(row -> tripWaypointId.equals(row.getTripWaypointId()))
                .findFirst()
                .orElse(null);
    }

    public static SessionWaypointProgress currentWaypoint(List<SessionWaypointProgress> progress) {
        return progress.stream()
                .filter(row -> {
                    WaypointProgressStatus status = row.getStatus();
                    return status == WaypointProgressStatus.NAVIGATING
                            || status == WaypointProgressStatus.ARRIVED
                            || status == WaypointProgressStatus.FISHING;
                })
                .findFirst()
                .orElse(null);
    }

    public static Map<UUID, TripWaypoint> indexWaypoints(List<TripWaypoint> waypoints) {
        return waypoints.stream().collect(Collectors.toMap(TripWaypoint::getId, waypoint -> waypoint));
    }

    static Instant recommendedFishingStart(SessionWaypointProgress row) {
        Instant fishingStartedAt = metadataInstant(row, "fishingStartedAt");
        if (fishingStartedAt != null) {
            return fishingStartedAt;
        }
        return row == null ? null : row.getArrivedAt();
    }

    static Instant recommendedFishingEnd(Instant recommendedStartAt, TripWaypoint waypoint) {
        if (recommendedStartAt == null || waypoint == null || waypoint.getPlannedFishingMinutes() == null) {
            return null;
        }
        return recommendedStartAt.plus(Duration.ofMinutes(waypoint.getPlannedFishingMinutes()));
    }

    static Instant metadataInstant(SessionWaypointProgress row, String key) {
        if (row == null || row.getMetadata() == null || key == null) {
            return null;
        }
        Object value = row.getMetadata().get(key);
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private UUID resolveActiveTarget(FishingSession session) {
        return activeGuidanceTarget == null ? null : activeGuidanceTarget.resolve(session);
    }

    private SessionAdHocFishingStop openStop(FishingSession session) {
        if (session == null || session.getId() == null) {
            return null;
        }
        return adHocStopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(session.getId()).orElse(null);
    }

    private AdHocFishingStopResponse toAdHocResponse(SessionAdHocFishingStop stop) {
        if (stop == null) {
            return null;
        }
        return new AdHocFishingStopResponse(
                stop.getId(),
                stop.getStartedAt(),
                stop.getEndedAt(),
                geoMapper.toDto(stop.getLocation()),
                stop.getFishingTargetId(),
                stop.getZoneId(),
                stop.getLakeFeatureId()
        );
    }

    private static FishingActivityState activityState(FishingSession session) {
        return session.getActivityState() == null ? FishingActivityState.UNKNOWN : session.getActivityState();
    }

    private static ActivityStateSource activitySource(FishingSession session) {
        return session.getActivityStateSource() == null
                ? ActivityStateSource.UNKNOWN
                : session.getActivityStateSource();
    }

    private boolean stationaryPrompt(
            FishingSession session,
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints,
            SessionAdHocFishingStop openStop
    ) {
        SessionWaypointProgress current = currentWaypoint(progress);
        Double distanceM = null;
        if (current != null && waypoints != null) {
            TripWaypoint planned = waypoints.get(current.getTripWaypointId());
            SessionLocationPoint lastAccepted = locationPointRepository
                    .findFirstByFishingSessionIdAndQualityOrderByRecordedAtDesc(session.getId(), LocationQuality.ACCEPTED)
                    .orElse(null);
            if (planned != null && lastAccepted != null && lastAccepted.getLocation() != null) {
                Point there = planned.resolvedEntryPoint() == null ? planned.getLocation() : planned.resolvedEntryPoint();
                if (there != null) {
                    if (planned.getFishingCorridor() != null && !planned.getFishingCorridor().isEmpty()) {
                        distanceM = GeoMetrics.distanceM(lastAccepted.getLocation(), planned.getFishingCorridor());
                    } else {
                        distanceM = GeoMetrics.distanceM(lastAccepted.getLocation(), there);
                    }
                }
            }
        }
        int limit = Math.max(1, sessionProperties.getStationary().getRecentPointLimit());
        List<SessionLocationPoint> recent = locationPointRepository
                .findByFishingSessionIdAndQualityOrderByRecordedAtDesc(
                        session.getId(), LocationQuality.ACCEPTED, PageRequest.of(0, limit));
        Map<String, Object> summary = session.getSummary();
        Instant dismissedAt = summary == null
                ? null
                : AdHocFishingService.parseInstant(summary.get(StationaryFishingDetector.DISMISSED_AT));
        Point dismissedLocation = dismissedPoint(summary);
        return stationaryFishingDetector.shouldPrompt(new StationaryFishingDetector.Input(
                session.getStatus(),
                session.getStatus() == FishingSessionStatus.PAUSED,
                openStop != null,
                current == null ? null : current.getStatus(),
                distanceM,
                recent,
                clock.instant(),
                dismissedAt,
                dismissedLocation
        ));
    }

    private static Point dismissedPoint(Map<String, Object> summary) {
        if (summary == null) {
            return null;
        }
        Double lat = AdHocFishingService.parseDouble(summary.get(StationaryFishingDetector.DISMISSED_LAT));
        Double lng = AdHocFishingService.parseDouble(summary.get(StationaryFishingDetector.DISMISSED_LNG));
        if (lat == null || lng == null) {
            return null;
        }
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
        Point point = factory.createPoint(new Coordinate(lng, lat));
        point.setSRID(GeoMapper.SRID);
        return point;
    }
}

package com.aifishing.fishingsession.service;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.dto.AdminFishingSessionResponse;
import com.aifishing.fishingsession.dto.FishingSessionResponse;
import com.aifishing.fishingsession.dto.NavigationResponse;
import com.aifishing.fishingsession.dto.SessionTrackResponse;
import com.aifishing.fishingsession.dto.WaypointProgressResponse;
import com.aifishing.fishingsession.repo.SessionClientEventRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.dto.TransitLegResponse;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.service.TripPlanAssembler;
import com.aifishing.planning.spatial.TransitEndpointKind;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SessionMapper {

    private final GeoMapper geoMapper;
    private final SessionLocationPointRepository locationPointRepository;
    private final SessionClientEventRepository clientEventRepository;
    private final TripPlanAssembler tripPlanAssembler;

    public SessionMapper(
            GeoMapper geoMapper,
            SessionLocationPointRepository locationPointRepository,
            SessionClientEventRepository clientEventRepository,
            TripPlanAssembler tripPlanAssembler
    ) {
        this.geoMapper = geoMapper;
        this.locationPointRepository = locationPointRepository;
        this.clientEventRepository = clientEventRepository;
        this.tripPlanAssembler = tripPlanAssembler;
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
                currentLegId
        );
    }

    public WaypointProgressResponse waypoint(SessionWaypointProgress row, TripWaypoint waypoint) {
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
                        : waypoint.getRecommendedTechniques()
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
        return new NavigationResponse(
                session.getId(),
                session.getStatus(),
                currentDto,
                lastAccepted == null ? null : geoMapper.toDto(lastAccepted.getLocation()),
                lastAccepted == null ? null : lastAccepted.getRecordedAt(),
                distanceM,
                bearingDegrees,
                currentTransitLegId(progress, legs, returning)
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

    static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

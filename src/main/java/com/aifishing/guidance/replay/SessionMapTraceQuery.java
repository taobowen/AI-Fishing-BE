package com.aifishing.guidance.replay;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionAdHocFishingStop;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionAdHocFishingStopRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripWaypointRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SessionMapTraceQuery {

    private static final Set<SessionEventType> INTERACTION_ANCHORS = EnumSet.of(
            SessionEventType.BITE,
            SessionEventType.FISH_ON
    );

    private final FishingSessionRepository sessionRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final SessionLocationPointRepository locationPointRepository;
    private final SessionWaypointProgressRepository progressRepository;
    private final SessionAdHocFishingStopRepository adHocStopRepository;
    private final SessionEventRepository sessionEventRepository;
    private final AgentRunRepository agentRunRepository;
    private final AgentDeliveredDecisionRepository deliveredDecisionRepository;

    public SessionMapTraceQuery(
            FishingSessionRepository sessionRepository,
            TripWaypointRepository tripWaypointRepository,
            SessionLocationPointRepository locationPointRepository,
            SessionWaypointProgressRepository progressRepository,
            SessionAdHocFishingStopRepository adHocStopRepository,
            SessionEventRepository sessionEventRepository,
            AgentRunRepository agentRunRepository,
            AgentDeliveredDecisionRepository deliveredDecisionRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.locationPointRepository = locationPointRepository;
        this.progressRepository = progressRepository;
        this.adHocStopRepository = adHocStopRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.agentRunRepository = agentRunRepository;
        this.deliveredDecisionRepository = deliveredDecisionRepository;
    }

    @Transactional(readOnly = true)
    public SessionMapTraceResponse load(UUID sessionId) {
        FishingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Fishing session not found"));
        List<TripWaypoint> waypoints = session.getTripPlanId() == null
                ? List.of()
                : tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(session.getTripPlanId());
        Map<UUID, TripWaypoint> waypointsById = waypoints.stream()
                .collect(Collectors.toMap(TripWaypoint::getId, Function.identity()));
        List<SessionLocationPoint> accepted = locationPointRepository
                .findByFishingSessionIdAndQualityOrderByRecordedAtAsc(sessionId, LocationQuality.ACCEPTED);
        List<SessionWaypointProgress> progress =
                progressRepository.findByFishingSessionIdOrderBySequenceAsc(sessionId);
        List<SessionAdHocFishingStop> adHocStops =
                adHocStopRepository.findByFishingSessionIdOrderByStartedAtAsc(sessionId);
        List<SessionEventEntity> sessionEvents =
                sessionEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(sessionId);

        List<Anchor> anchors = new ArrayList<>();
        addWaypointAnchors(anchors, progress, waypointsById);
        addAdHocAnchors(anchors, adHocStops);
        addInteractionAnchors(anchors, sessionEvents, accepted);
        addDeliveredMoveAnchors(anchors, sessionId, waypointsById);

        Set<UUID> gpsAnchorIds = new HashSet<>();
        for (Anchor anchor : anchors) {
            if (anchor.gpsPointId() != null) {
                gpsAnchorIds.add(anchor.gpsPointId());
            }
        }
        List<GpsTraceDownsampler.GpsPoint> downsampled = GpsTraceDownsampler.downsample(
                accepted.stream()
                        .map(point -> new GpsTraceDownsampler.GpsPoint(point.getId(), point.getLocation()))
                        .toList(),
                gpsAnchorIds
        );

        return new SessionMapTraceResponse(
                sessionId,
                GeoJsonFeatureCollection.of(originalPlanFeatures(waypoints)),
                GeoJsonFeatureCollection.of(gpsFeatures(downsampled, accepted)),
                GeoJsonFeatureCollection.of(anchorFeatures(anchors))
        );
    }

    private static void addWaypointAnchors(
            List<Anchor> anchors,
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypointsById
    ) {
        for (SessionWaypointProgress row : progress) {
            TripWaypoint waypoint = waypointsById.get(row.getTripWaypointId());
            Point location = waypoint == null ? null : waypoint.getLocation();
            if (location == null) {
                continue;
            }
            if (row.getArrivedAt() != null) {
                anchors.add(Anchor.fixed(
                        "WAYPOINT_ARRIVAL",
                        row.getArrivedAt(),
                        location,
                        Map.of(
                                "tripWaypointId", row.getTripWaypointId().toString(),
                                "progressId", row.getId().toString()
                        )
                ));
            }
            if (row.getDepartedAt() != null) {
                anchors.add(Anchor.fixed(
                        "WAYPOINT_DEPARTURE",
                        row.getDepartedAt(),
                        location,
                        Map.of(
                                "tripWaypointId", row.getTripWaypointId().toString(),
                                "progressId", row.getId().toString()
                        )
                ));
            }
        }
    }

    private static void addAdHocAnchors(List<Anchor> anchors, List<SessionAdHocFishingStop> stops) {
        for (SessionAdHocFishingStop stop : stops) {
            if (stop.getLocation() == null) {
                continue;
            }
            anchors.add(Anchor.fixed(
                    "AD_HOC_START",
                    stop.getStartedAt(),
                    stop.getLocation(),
                    Map.of("adHocFishingStopId", stop.getId().toString())
            ));
            if (stop.getEndedAt() != null) {
                anchors.add(Anchor.fixed(
                        "AD_HOC_END",
                        stop.getEndedAt(),
                        stop.getLocation(),
                        Map.of("adHocFishingStopId", stop.getId().toString())
                ));
            }
        }
    }

    private static void addInteractionAnchors(
            List<Anchor> anchors,
            List<SessionEventEntity> sessionEvents,
            List<SessionLocationPoint> accepted
    ) {
        for (SessionEventEntity event : sessionEvents) {
            if (!INTERACTION_ANCHORS.contains(event.getType()) || event.getOccurredAt() == null) {
                continue;
            }
            SessionLocationPoint nearest = nearestAtOrBefore(accepted, event.getOccurredAt());
            if (nearest == null || nearest.getLocation() == null) {
                continue;
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("eventId", event.getId().toString());
            properties.put("eventType", event.getType().name());
            if (event.getFishInteractionId() != null) {
                properties.put("fishInteractionId", event.getFishInteractionId().toString());
            }
            anchors.add(new Anchor(
                    event.getType().name(),
                    event.getOccurredAt(),
                    nearest.getLocation(),
                    nearest.getId(),
                    properties
            ));
        }
    }

    private void addDeliveredMoveAnchors(
            List<Anchor> anchors,
            UUID sessionId,
            Map<UUID, TripWaypoint> waypointsById
    ) {
        Map<UUID, AgentRunEntity> productionRuns = agentRunRepository
                .findByFishingSessionIdAndVisibilityOrderByStartedAtDesc(sessionId, AgentRunVisibility.PRODUCTION)
                .stream()
                .collect(Collectors.toMap(AgentRunEntity::getId, Function.identity(), (left, right) -> left));
        for (AgentDeliveredDecisionEntity row : deliveredDecisionRepository
                .findByFishingSessionIdOrderByCreatedAtAsc(sessionId)) {
            AgentRunEntity run = productionRuns.get(row.getRunId());
            if (run == null || run.getVisibility() == AgentRunVisibility.SHADOW) {
                continue;
            }
            DeliveredDecision delivered = GuidanceContracts.mapper()
                    .convertValue(row.getDecision(), DeliveredDecision.class);
            if (delivered == null
                    || delivered.primaryAction() != GuidanceAction.MOVE
                    || delivered.targetTripWaypointId() == null) {
                continue;
            }
            TripWaypoint waypoint = waypointsById.get(delivered.targetTripWaypointId());
            if (waypoint == null || waypoint.getLocation() == null) {
                continue;
            }
            anchors.add(Anchor.fixed(
                    "DELIVERED_MOVE_TARGET",
                    row.getCreatedAt(),
                    waypoint.getLocation(),
                    Map.of(
                            "decisionId", row.getId().toString(),
                            "runId", row.getRunId().toString(),
                            "tripWaypointId", delivered.targetTripWaypointId().toString()
                    )
            ));
        }
    }

    private static List<GeoJsonFeature> originalPlanFeatures(List<TripWaypoint> waypoints) {
        List<GeoJsonFeature> features = new ArrayList<>();
        for (TripWaypoint waypoint : waypoints) {
            if (waypoint.getLocation() == null) {
                continue;
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("kind", "ORIGINAL_PLAN");
            properties.put("tripWaypointId", waypoint.getId().toString());
            properties.put("sequence", waypoint.getSequence());
            if (waypoint.getFeatureType() != null) {
                properties.put("featureType", waypoint.getFeatureType().name());
            }
            features.add(GeoJsonFeature.point(
                    waypoint.getLocation().getX(),
                    waypoint.getLocation().getY(),
                    properties
            ));
        }
        return features;
    }

    private static List<GeoJsonFeature> gpsFeatures(
            List<GpsTraceDownsampler.GpsPoint> downsampled,
            List<SessionLocationPoint> accepted
    ) {
        Map<UUID, SessionLocationPoint> byId = accepted.stream()
                .collect(Collectors.toMap(SessionLocationPoint::getId, Function.identity()));
        List<GeoJsonFeature> features = new ArrayList<>();
        for (GpsTraceDownsampler.GpsPoint point : downsampled) {
            SessionLocationPoint row = byId.get(point.id());
            if (row == null || row.getLocation() == null) {
                continue;
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("kind", "GPS");
            properties.put("pointId", row.getId().toString());
            properties.put("recordedAt", row.getRecordedAt() == null ? null : row.getRecordedAt().toString());
            features.add(GeoJsonFeature.point(row.getLocation().getX(), row.getLocation().getY(), properties));
        }
        return features;
    }

    private static List<GeoJsonFeature> anchorFeatures(List<Anchor> anchors) {
        List<GeoJsonFeature> features = new ArrayList<>();
        for (Anchor anchor : anchors) {
            if (anchor.location() == null) {
                continue;
            }
            Map<String, Object> properties = new LinkedHashMap<>(anchor.properties());
            properties.put("kind", "ANCHOR");
            properties.put("anchorType", anchor.anchorType());
            if (anchor.occurredAt() != null) {
                properties.put("occurredAt", anchor.occurredAt().toString());
            }
            if (anchor.gpsPointId() != null) {
                properties.put("gpsPointId", anchor.gpsPointId().toString());
            }
            features.add(GeoJsonFeature.point(anchor.location().getX(), anchor.location().getY(), properties));
        }
        return features;
    }

    private static SessionLocationPoint nearestAtOrBefore(List<SessionLocationPoint> accepted, Instant at) {
        SessionLocationPoint best = null;
        for (SessionLocationPoint point : accepted) {
            if (point.getRecordedAt() == null || point.getRecordedAt().isAfter(at)) {
                continue;
            }
            if (best == null || point.getRecordedAt().isAfter(best.getRecordedAt())) {
                best = point;
            }
        }
        if (best != null) {
            return best;
        }
        return accepted.stream()
                .filter(point -> point.getRecordedAt() != null)
                .min(Comparator.comparing(point -> Math.abs(point.getRecordedAt().toEpochMilli() - at.toEpochMilli())))
                .orElse(null);
    }

    private record Anchor(
            String anchorType,
            Instant occurredAt,
            Point location,
            UUID gpsPointId,
            Map<String, Object> properties
    ) {
        static Anchor fixed(String anchorType, Instant occurredAt, Point location, Map<String, Object> properties) {
            return new Anchor(anchorType, occurredAt, location, null, properties);
        }
    }
}

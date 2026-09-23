package com.aifishing.feedback.catchlog.service;

import com.aifishing.feedback.FeedbackProperties;
import com.aifishing.feedback.catchlog.domain.CatchAssociationMethod;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.SessionAdHocFishingStop;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.repo.SessionAdHocFishingStopRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.guidance.horizon.ActiveGuidanceTarget;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CatchAssociationService {

    private final SessionWaypointProgressRepository progressRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final SessionAdHocFishingStopRepository adHocStopRepository;
    private final FeedbackProperties feedbackProperties;
    private final ActiveGuidanceTarget activeGuidanceTarget;

    public CatchAssociationService(
            SessionWaypointProgressRepository progressRepository,
            TripWaypointRepository tripWaypointRepository,
            SessionAdHocFishingStopRepository adHocStopRepository,
            FeedbackProperties feedbackProperties,
            ActiveGuidanceTarget activeGuidanceTarget
    ) {
        this.progressRepository = progressRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.adHocStopRepository = adHocStopRepository;
        this.feedbackProperties = feedbackProperties;
        this.activeGuidanceTarget = activeGuidanceTarget;
    }

    public Association associate(FishingSession session, UUID clientWaypointId, Instant occurredAt, Point location) {
        return associate(
                session,
                clientWaypointId,
                occurredAt,
                location,
                progressRepository.findByFishingSessionIdOrderBySequenceAsc(session.getId()),
                tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(session.getTripPlanId()),
                adHocStopRepository == null
                        ? List.of()
                        : adHocStopRepository.findByFishingSessionIdOrderByStartedAtAsc(session.getId())
        );
    }

    Association associate(
            FishingSession session,
            UUID clientWaypointId,
            Instant occurredAt,
            Point location,
            List<SessionWaypointProgress> progress,
            List<TripWaypoint> orderedWaypoints
    ) {
        return associate(session, clientWaypointId, occurredAt, location, progress, orderedWaypoints, List.of());
    }

    Association associate(
            FishingSession session,
            UUID clientWaypointId,
            Instant occurredAt,
            Point location,
            List<SessionWaypointProgress> progress,
            List<TripWaypoint> orderedWaypoints,
            List<SessionAdHocFishingStop> stops
    ) {
        Map<UUID, TripWaypoint> waypoints = orderedWaypoints.stream()
                .collect(Collectors.toMap(
                        TripWaypoint::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        if (clientWaypointId != null) {
            SessionWaypointProgress row = progress.stream()
                    .filter(item -> item.getTripWaypointId().equals(clientWaypointId))
                    .findFirst()
                    .orElse(null);
            TripWaypoint waypoint = waypoints.get(clientWaypointId);
            if (row != null && waypoint != null && temporallyPlausible(session, row, occurredAt)) {
                Double distance = distanceM(location, waypoint);
                return new Association(
                        waypoint.getId(),
                        waypoint.getLakeFeatureId(),
                        waypoint.getFishingTargetId(),
                        waypoint.getZoneId(),
                        null,
                        CatchAssociationMethod.CURRENT_WAYPOINT,
                        distance,
                        waypoint.getFeatureType()
                );
            }
        }

        if (clientWaypointId == null) {
            SessionAdHocFishingStop stop = coveringStop(stops, occurredAt);
            if (stop != null) {
                return new Association(
                        null,
                        stop.getLakeFeatureId(),
                        stop.getFishingTargetId(),
                        stop.getZoneId(),
                        stop.getSubtargetId(),
                        CatchAssociationMethod.AD_HOC_STOP,
                        distanceToStop(location, stop),
                        null,
                        stop.getId()
                );
            }
        }

        if (location != null) {
            double radius = feedbackProperties.getCatch().getWaypointAssociationRadiusM();
            Map<UUID, SessionWaypointProgress> progressByWaypoint = progress.stream()
                    .collect(Collectors.toMap(
                            SessionWaypointProgress::getTripWaypointId,
                            Function.identity(),
                            (left, right) -> left
                    ));

            UUID targetId = activeGuidanceTarget == null ? null : activeGuidanceTarget.resolve(session);
            if (targetId != null) {
                TripWaypoint targetWaypoint = waypoints.get(targetId);
                SessionWaypointProgress targetRow = progressByWaypoint.get(targetId);
                if (targetWaypoint != null
                        && targetWaypoint.getLocation() != null
                        && (targetRow == null || nearestPreference(targetRow) < 2)) {
                    double distance = distanceToStop(location, targetWaypoint);
                    if (distance <= radius) {
                        return nearestAssociation(targetWaypoint, distance);
                    }
                }
            }

            SessionWaypointProgress current = currentActiveProgress(progress);
            if (current != null) {
                TripWaypoint currentWaypoint = waypoints.get(current.getTripWaypointId());
                if (currentWaypoint != null && currentWaypoint.getLocation() != null) {
                    double distance = distanceToStop(location, currentWaypoint);
                    if (distance <= radius) {
                        return nearestAssociation(currentWaypoint, distance);
                    }
                }
            }

            TripWaypoint best = null;
            double bestDistance = Double.MAX_VALUE;
            int bestRank = Integer.MAX_VALUE;
            for (TripWaypoint waypoint : waypoints.values()) {
                if (waypoint.getLocation() == null) {
                    continue;
                }
                double distance = distanceToStop(location, waypoint);
                if (distance > radius) {
                    continue;
                }
                int rank = nearestPreference(progressByWaypoint.get(waypoint.getId()));
                if (rank < bestRank || (rank == bestRank && distance < bestDistance)) {
                    best = waypoint;
                    bestDistance = distance;
                    bestRank = rank;
                }
            }
            if (best != null) {
                return nearestAssociation(best, bestDistance);
            }
        }

        return new Association(null, null, null, null, null, CatchAssociationMethod.UNASSOCIATED, null, null);
    }

    private static SessionAdHocFishingStop coveringStop(List<SessionAdHocFishingStop> stops, Instant occurredAt) {
        if (stops == null || occurredAt == null) {
            return null;
        }
        for (SessionAdHocFishingStop stop : stops) {
            if (stop != null && stop.covers(occurredAt)) {
                return stop;
            }
        }
        return null;
    }

    private static Double distanceToStop(Point location, SessionAdHocFishingStop stop) {
        if (location == null || stop == null || stop.getLocation() == null) {
            return null;
        }
        return GeoMetrics.distanceM(location, stop.getLocation());
    }

    private static SessionWaypointProgress currentActiveProgress(List<SessionWaypointProgress> progress) {
        for (SessionWaypointProgress row : progress) {
            WaypointProgressStatus status = row.getStatus();
            if (status == WaypointProgressStatus.NAVIGATING
                    || status == WaypointProgressStatus.ARRIVED
                    || status == WaypointProgressStatus.FISHING) {
                return row;
            }
        }
        return null;
    }

    private static int nearestPreference(SessionWaypointProgress row) {
        if (row == null) {
            return 2;
        }
        return switch (row.getStatus()) {
            case NAVIGATING, ARRIVED, FISHING -> 0;
            case UPCOMING -> 1;
            case COMPLETED, SKIPPED -> 2;
        };
    }

    private static Association nearestAssociation(TripWaypoint waypoint, double distance) {
        return new Association(
                waypoint.getId(),
                waypoint.getLakeFeatureId(),
                waypoint.getFishingTargetId(),
                waypoint.getZoneId(),
                null,
                CatchAssociationMethod.NEAREST_WAYPOINT,
                distance,
                waypoint.getFeatureType()
        );
    }

    private static boolean temporallyPlausible(
            FishingSession session,
            SessionWaypointProgress row,
            Instant occurredAt
    ) {
        if (occurredAt == null || session.getStartedAt() == null || occurredAt.isBefore(session.getStartedAt())) {
            return false;
        }
        if (session.getEndedAt() != null && occurredAt.isAfter(session.getEndedAt())) {
            return false;
        }
        if (row.getSkippedAt() != null && occurredAt.isAfter(row.getSkippedAt())
                && row.getStatus() == WaypointProgressStatus.SKIPPED) {
            return false;
        }
        if (row.getCompletedAt() != null && occurredAt.isAfter(row.getCompletedAt())
                && row.getStatus() == WaypointProgressStatus.COMPLETED) {
            return false;
        }
        if (row.getSkippedAt() != null && !occurredAt.isBefore(row.getSkippedAt())) {
            return false;
        }
        if (row.getCompletedAt() != null && !occurredAt.isBefore(row.getCompletedAt())) {
            return false;
        }
        return true;
    }

    private static Double distanceM(Point location, TripWaypoint waypoint) {
        if (location == null) {
            return null;
        }
        return distanceToStop(location, waypoint);
    }

    private static double distanceToStop(Point location, TripWaypoint waypoint) {
        if (waypoint.getFishingCorridor() != null && !waypoint.getFishingCorridor().isEmpty()) {
            return GeoMetrics.distanceM(location, waypoint.getFishingCorridor());
        }
        if (waypoint.getEntryPoint() != null) {
            return GeoMetrics.distanceM(location, waypoint.getEntryPoint());
        }
        if (waypoint.getLocation() == null) {
            return Double.POSITIVE_INFINITY;
        }
        return GeoMetrics.distanceM(location, waypoint.getLocation());
    }

    public record Association(
            UUID tripWaypointId,
            UUID lakeFeatureId,
            UUID fishingTargetId,
            UUID zoneId,
            UUID subtargetId,
            CatchAssociationMethod method,
            Double distanceM,
            FeatureType plannedFeatureType,
            UUID adHocFishingStopId
    ) {
        public Association(
                UUID tripWaypointId,
                UUID lakeFeatureId,
                UUID fishingTargetId,
                UUID zoneId,
                UUID subtargetId,
                CatchAssociationMethod method,
                Double distanceM,
                FeatureType plannedFeatureType
        ) {
            this(
                    tripWaypointId,
                    lakeFeatureId,
                    fishingTargetId,
                    zoneId,
                    subtargetId,
                    method,
                    distanceM,
                    plannedFeatureType,
                    null
            );
        }
    }
}

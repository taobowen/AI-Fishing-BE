package com.aifishing.feedback.catchlog.service;

import com.aifishing.feedback.FeedbackProperties;
import com.aifishing.feedback.catchlog.domain.CatchAssociationMethod;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripWaypointRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CatchAssociationService {

    private final SessionWaypointProgressRepository progressRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final FeedbackProperties feedbackProperties;

    public CatchAssociationService(
            SessionWaypointProgressRepository progressRepository,
            TripWaypointRepository tripWaypointRepository,
            FeedbackProperties feedbackProperties
    ) {
        this.progressRepository = progressRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.feedbackProperties = feedbackProperties;
    }

    public Association associate(FishingSession session, UUID clientWaypointId, Instant occurredAt, Point location) {
        List<SessionWaypointProgress> progress =
                progressRepository.findByFishingSessionIdOrderBySequenceAsc(session.getId());
        Map<UUID, TripWaypoint> waypoints = tripWaypointRepository
                .findByTripPlanIdOrderBySequenceAsc(session.getTripPlanId())
                .stream()
                .collect(Collectors.toMap(TripWaypoint::getId, Function.identity()));

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

        if (location != null) {
            double radius = feedbackProperties.getCatch().getWaypointAssociationRadiusM();
            TripWaypoint best = null;
            double bestDistance = Double.MAX_VALUE;
            for (TripWaypoint waypoint : waypoints.values()) {
                if (waypoint.getLocation() == null) {
                    continue;
                }
                double distance = distanceToStop(location, waypoint);
                if (distance <= radius && distance < bestDistance) {
                    best = waypoint;
                    bestDistance = distance;
                }
            }
            if (best != null) {
                return new Association(
                        best.getId(),
                        best.getLakeFeatureId(),
                        best.getFishingTargetId(),
                        best.getZoneId(),
                        null,
                        CatchAssociationMethod.NEAREST_WAYPOINT,
                        bestDistance,
                        best.getFeatureType()
                );
            }
        }

        return new Association(null, null, null, null, null, CatchAssociationMethod.UNASSOCIATED, null, null);
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
            FeatureType plannedFeatureType
    ) {
    }
}

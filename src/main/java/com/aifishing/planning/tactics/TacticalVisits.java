package com.aifishing.planning.tactics;

import com.aifishing.common.enums.TechniqueType;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.ZoneSubPlan;
import com.aifishing.planning.spatial.domain.TripStopSubtarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TacticalVisits {

    private TacticalVisits() {
    }

    public static List<FishableVisit> extract(List<PlannedStop> stops) {
        List<FishableVisit> visits = new ArrayList<>();
        if (stops == null) {
            return visits;
        }
        for (PlannedStop stop : stops) {
            if (stop == null || stop.candidate() == null || stop.candidate().spot() == null) {
                continue;
            }
            CandidateSpot spot = stop.candidate().spot();
            if (spot.getTargetKind() == TargetKind.ZONE && stop.zoneSubPlan() != null
                    && stop.zoneSubPlan().stops() != null
                    && !stop.zoneSubPlan().stops().isEmpty()) {
                for (ZoneSubPlan.MicroStop micro : stop.zoneSubPlan().stops()) {
                    FishableVisit visit = fromMicro(micro);
                    if (visit != null) {
                        visits.add(visit);
                    }
                }
                continue;
            }
            if (!isFishable(spot.getTargetKind())) {
                continue;
            }
            UUID visitId = stop.visitId();
            if (visitId == null) {
                continue;
            }
            visits.add(new FishableVisit(
                    visitId,
                    spot.getTargetKind(),
                    spot.getType(),
                    spot.getRepresentativeDepthM(),
                    spot.getMinDepthM(),
                    spot.getMaxDepthM(),
                    spot.techniqueTypes(),
                    stop.arrivalAt(),
                    stop.departureAt(),
                    stop.plannedFishingMinutes(),
                    spot.getType() == null ? null : spot.getType().name(),
                    spot.getTargetKind() != null && spot.getTargetKind().isPathLike()
            ));
        }
        return visits;
    }

    public static List<FishableVisit> fromPersisted(List<TripWaypoint> waypoints, List<TripStopSubtarget> subtargets) {
        List<FishableVisit> visits = new ArrayList<>();
        if (waypoints == null) {
            return visits;
        }
        Map<UUID, List<TripStopSubtarget>> byWaypoint = new java.util.LinkedHashMap<>();
        if (subtargets != null) {
            for (TripStopSubtarget row : subtargets) {
                if (row == null || row.getTripWaypointId() == null) {
                    continue;
                }
                byWaypoint.computeIfAbsent(row.getTripWaypointId(), ignored -> new ArrayList<>()).add(row);
            }
        }
        for (TripWaypoint waypoint : waypoints) {
            if (waypoint == null) {
                continue;
            }
            List<TripStopSubtarget> children = byWaypoint.getOrDefault(waypoint.getId(), List.of());
            if (waypoint.resolvedTargetKind() == TargetKind.ZONE && !children.isEmpty()) {
                for (TripStopSubtarget child : children) {
                    FishableVisit visit = fromSubtarget(child, waypoint);
                    if (visit != null) {
                        visits.add(visit);
                    }
                }
                continue;
            }
            UUID visitId = persistedVisitId(waypoint);
            if (visitId == null) {
                continue;
            }
            visits.add(new FishableVisit(
                    visitId,
                    waypoint.resolvedTargetKind(),
                    waypoint.getFeatureType(),
                    decimal(waypoint.getRepresentativeDepthM()),
                    decimal(waypoint.getMinDepthM()),
                    decimal(waypoint.getMaxDepthM()),
                    techniques(waypoint.getRecommendedTechniques()),
                    waypoint.getPlannedArrivalAt(),
                    waypoint.getPlannedDepartureAt(),
                    waypoint.getPlannedFishingMinutes() == null ? 0 : waypoint.getPlannedFishingMinutes(),
                    waypoint.getFeatureType() == null ? null : waypoint.getFeatureType().name(),
                    waypoint.resolvedTargetKind().isPathLike()
            ));
        }
        return visits;
    }

    private static FishableVisit fromSubtarget(TripStopSubtarget row, TripWaypoint parent) {
        UUID visitId = row.getFishingTargetId();
        if (visitId == null) {
            return null;
        }
        TargetKind kind = row.getTargetKind() == null ? TargetKind.POINT : row.getTargetKind();
        return new FishableVisit(
                visitId,
                kind,
                parent.getFeatureType(),
                decimal(parent.getRepresentativeDepthM()),
                decimal(parent.getMinDepthM()),
                decimal(parent.getMaxDepthM()),
                techniques(parent.getRecommendedTechniques()),
                row.getPlannedArrivalAt(),
                row.getPlannedDepartureAt(),
                row.getPlannedFishingMinutes() == null ? 0 : row.getPlannedFishingMinutes(),
                parent.getFeatureType() == null ? null : parent.getFeatureType().name(),
                kind.isPathLike()
        );
    }

    private static UUID persistedVisitId(TripWaypoint waypoint) {
        Map<String, Object> metadata = waypoint.getMetadata();
        if (metadata != null && metadata.get("visitId") != null) {
            try {
                return UUID.fromString(String.valueOf(metadata.get("visitId")));
            } catch (IllegalArgumentException ignored) {
                // fall through to fishing-target identity
            }
        }
        return waypoint.getFishingTargetId();
    }

    private static List<TechniqueType> techniques(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<TechniqueType> out = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            try {
                out.add(TechniqueType.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // skip unknown technique labels
            }
        }
        return out;
    }

    private static Double decimal(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static FishableVisit fromMicro(ZoneSubPlan.MicroStop micro) {
        if (micro == null || micro.spot() == null) {
            return null;
        }
        CandidateSpot spot = micro.spot();
        UUID visitId = spot.planningIdentity();
        if (visitId == null) {
            return null;
        }
        return new FishableVisit(
                visitId,
                spot.getTargetKind() == null ? TargetKind.POINT : spot.getTargetKind(),
                spot.getType(),
                spot.getRepresentativeDepthM(),
                spot.getMinDepthM(),
                spot.getMaxDepthM(),
                spot.techniqueTypes(),
                micro.arrivalAt(),
                micro.departureAt(),
                micro.fishingMinutes(),
                spot.getType() == null ? null : spot.getType().name(),
                spot.getTargetKind() != null && spot.getTargetKind().isPathLike()
        );
    }

    private static boolean isFishable(TargetKind kind) {
        return kind == TargetKind.POINT
                || kind == TargetKind.PATH
                || kind == TargetKind.SEGMENT
                || kind == TargetKind.AREA;
    }
}

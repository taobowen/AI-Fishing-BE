package com.aifishing.guidance.tools;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GetNearbyWaypointsParams;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.revisit.OpportunityRevisitSupport;
import com.aifishing.guidance.spi.AgentTool;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import com.aifishing.planning.spatial.repo.LakeFishingTargetRepository;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Nearby lake features, trip waypoints, and spatial targets within {@code radiusMeters}.
 * Annotates cooled vs eligible packages without hiding other packages on the same zone.
 */
@Component
public class GetNearbyWaypointsTool implements AgentTool {

    static final String KIND_TRIP_WAYPOINT = "TRIP_WAYPOINT";
    static final String KIND_LAKE_FEATURE = "LAKE_FEATURE";
    static final String KIND_SPATIAL_TARGET = "SPATIAL_TARGET";
    private static final int MAX_RESULTS = 20;

    private final TripWaypointRepository tripWaypointRepository;
    private final LakeFeatureRepository lakeFeatureRepository;
    private final LakeFishingTargetRepository fishingTargetRepository;
    private final Clock clock;
    private final FishingSessionRepository sessionRepository;
    private final SessionWaypointProgressRepository progressRepository;
    private final OpportunityRevisitSupport opportunityRevisitSupport;

    @Autowired
    public GetNearbyWaypointsTool(
            TripWaypointRepository tripWaypointRepository,
            LakeFeatureRepository lakeFeatureRepository,
            LakeFishingTargetRepository fishingTargetRepository,
            Clock clock,
            FishingSessionRepository sessionRepository,
            SessionWaypointProgressRepository progressRepository,
            OpportunityRevisitSupport opportunityRevisitSupport
    ) {
        this.tripWaypointRepository = tripWaypointRepository;
        this.lakeFeatureRepository = lakeFeatureRepository;
        this.fishingTargetRepository = fishingTargetRepository;
        this.clock = clock;
        this.sessionRepository = sessionRepository;
        this.progressRepository = progressRepository;
        this.opportunityRevisitSupport = opportunityRevisitSupport;
    }

    @Override
    public ToolName name() {
        return ToolName.GET_NEARBY_WAYPOINTS;
    }

    @Override
    public ToolResultEnvelope execute(ToolRequestEnvelope request) {
        AgentToolSupport.Parsed<GetNearbyWaypointsParams> parsed = AgentToolSupport.parse(
                request, name(), "GetNearbyWaypointsParams", GetNearbyWaypointsParams.class, clock);
        if (!parsed.valid()) {
            return parsed.error();
        }
        try {
            GetNearbyWaypointsParams params = parsed.params();
            List<Nearby> nearby = new ArrayList<>();
            collectTripWaypoints(params, nearby);
            collectLakeFeatures(params, nearby);
            collectSpatialTargets(params, nearby);
            nearby.sort(Comparator.comparingDouble(Nearby::distanceMeters));
            if (nearby.size() > MAX_RESULTS) {
                nearby = new ArrayList<>(nearby.subList(0, MAX_RESULTS));
            }
            if (nearby.isEmpty()) {
                return AgentToolSupport.unknown(name(), clock);
            }
            FishingSessionState.OpportunityRevisit slice = loadRevisitSlice(nearby);
            ObjectNode data = GuidanceContracts.mapper().createObjectNode();
            data.put("count", nearby.size());
            data.put("targetSpecies", params.targetSpecies().name());
            ArrayNode items = data.putArray("waypoints");
            for (Nearby item : nearby) {
                items.add(item.toJson(params.targetSpecies(), eligibility(item, slice)));
            }
            return AgentToolSupport.ok(name(), clock, data);
        } catch (RuntimeException ex) {
            return AgentToolSupport.error(name(), clock);
        }
    }

    private void collectTripWaypoints(GetNearbyWaypointsParams params, List<Nearby> nearby) {
        for (TripWaypoint waypoint : tripWaypointRepository.findNearby(
                params.latitudeWgs84(), params.longitudeWgs84(), params.radiusMeters())) {
            Double lat = AgentToolSupport.latitudeOf(waypoint.getLocation());
            Double lng = AgentToolSupport.longitudeOf(waypoint.getLocation());
            Double distance = distanceWithin(params, lat, lng);
            if (distance == null) {
                continue;
            }
            nearby.add(new Nearby(
                    waypoint.getId().toString(),
                    KIND_TRIP_WAYPOINT,
                    lat,
                    lng,
                    distance,
                    waypoint.getFeatureType() == null ? null : waypoint.getFeatureType().name(),
                    waypoint.getTargetKind() == null ? null : waypoint.getTargetKind().name(),
                    waypoint.getTripPlanId(),
                    waypoint.getZoneId(),
                    OpportunityRevisitSupport.packageMemberIds(waypoint, waypoint.getId())
            ));
        }
    }

    private void collectLakeFeatures(GetNearbyWaypointsParams params, List<Nearby> nearby) {
        for (LakeFeature feature : lakeFeatureRepository.findNearby(
                params.latitudeWgs84(), params.longitudeWgs84(), params.radiusMeters())) {
            Double lat = AgentToolSupport.latitudeOf(feature.getGeometry());
            Double lng = AgentToolSupport.longitudeOf(feature.getGeometry());
            Double distance = distanceWithin(params, lat, lng);
            if (distance == null) {
                continue;
            }
            nearby.add(new Nearby(
                    feature.getId().toString(),
                    KIND_LAKE_FEATURE,
                    lat,
                    lng,
                    distance,
                    feature.getType() == null ? null : feature.getType().name(),
                    null,
                    null,
                    null,
                    List.of(feature.getId())
            ));
        }
    }

    private void collectSpatialTargets(GetNearbyWaypointsParams params, List<Nearby> nearby) {
        for (LakeFishingTarget target : fishingTargetRepository.findNearby(
                params.latitudeWgs84(), params.longitudeWgs84(), params.radiusMeters())) {
            Double lat = AgentToolSupport.latitudeOf(target.getRepresentativePoint());
            Double lng = AgentToolSupport.longitudeOf(target.getRepresentativePoint());
            Double distance = distanceWithin(params, lat, lng);
            if (distance == null) {
                continue;
            }
            nearby.add(new Nearby(
                    target.getId().toString(),
                    KIND_SPATIAL_TARGET,
                    lat,
                    lng,
                    distance,
                    target.getSemanticType() == null ? null : target.getSemanticType().name(),
                    target.getTargetKind() == null ? null : target.getTargetKind().name(),
                    null,
                    null,
                    List.of(target.getId())
            ));
        }
    }

    private FishingSessionState.OpportunityRevisit loadRevisitSlice(List<Nearby> nearby) {
        if (sessionRepository == null || progressRepository == null || opportunityRevisitSupport == null) {
            return null;
        }
        Set<UUID> planIds = new HashSet<>();
        for (Nearby item : nearby) {
            if (item.tripPlanId() != null) {
                planIds.add(item.tripPlanId());
            }
        }
        if (planIds.isEmpty()) {
            return FishingSessionState.OpportunityRevisit.empty();
        }
        List<FishingSessionState.PackageCooldown> cooled = new ArrayList<>();
        for (UUID planId : planIds) {
            FishingSession session = sessionRepository
                    .findFirstByTripPlanIdAndStatusOrderByStartedAtDesc(planId, FishingSessionStatus.ACTIVE)
                    .orElse(null);
            if (session == null) {
                continue;
            }
            List<SessionWaypointProgress> progress =
                    progressRepository.findByFishingSessionIdOrderBySequenceAsc(session.getId());
            Map<UUID, TripWaypoint> waypoints = new HashMap<>();
            for (TripWaypoint waypoint : tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(planId)) {
                waypoints.put(waypoint.getId(), waypoint);
            }
            cooled.addAll(opportunityRevisitSupport.assemble(progress, waypoints, List.of()).packageCooldowns());
        }
        return new FishingSessionState.OpportunityRevisit(cooled, List.of());
    }

    private String eligibility(Nearby item, FishingSessionState.OpportunityRevisit slice) {
        if (slice == null || opportunityRevisitSupport == null) {
            return null;
        }
        return opportunityRevisitSupport.packageEligibility(item.packageMemberIds(), slice);
    }

    private static Double distanceWithin(GetNearbyWaypointsParams params, Double lat, Double lng) {
        if (lat == null || lng == null) {
            return null;
        }
        double meters = AgentToolSupport.haversineMeters(
                params.latitudeWgs84(), params.longitudeWgs84(), lat, lng);
        if (meters > params.radiusMeters()) {
            return null;
        }
        return meters;
    }

    private record Nearby(
            String id,
            String kind,
            double latitudeWgs84,
            double longitudeWgs84,
            double distanceMeters,
            String featureType,
            String targetKind,
            UUID tripPlanId,
            UUID physicalZoneId,
            List<UUID> packageMemberIds
    ) {
        ObjectNode toJson(FishSpecies species, String packageEligibility) {
            ObjectNode node = GuidanceContracts.mapper().createObjectNode();
            node.put("id", id);
            node.put("kind", kind);
            node.put("latitudeWgs84", latitudeWgs84);
            node.put("longitudeWgs84", longitudeWgs84);
            node.put("distanceMeters", Math.round(distanceMeters * 10.0) / 10.0);
            if (featureType != null) {
                node.put("featureType", featureType);
            }
            if (targetKind != null) {
                node.put("targetKind", targetKind);
            }
            if (physicalZoneId != null) {
                node.put("physicalZoneId", physicalZoneId.toString());
            }
            if (packageMemberIds != null && !packageMemberIds.isEmpty()) {
                ArrayNode members = node.putArray("packageMemberIds");
                for (UUID member : packageMemberIds) {
                    if (member != null) {
                        members.add(member.toString());
                    }
                }
            }
            if (packageEligibility != null) {
                node.put("packageEligibility", packageEligibility);
            }
            if (species != null) {
                node.put("targetSpecies", species.name());
            }
            return node;
        }
    }
}

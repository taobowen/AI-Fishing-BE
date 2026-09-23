package com.aifishing.guidance.tools;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.contracts.ToolResultStatus;
import com.aifishing.guidance.revisit.OpportunityRevisitSupport;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.domain.TripWaypointPlanMetadata;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.repo.LakeFishingTargetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetNearbyWaypointsPackageEligibilityTest {

    private static final Instant NOW = Instant.parse("2026-09-16T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final UUID PLAN_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ZONE_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID FIRST_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");
    private static final UUID SECOND_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");
    private static final UUID MEMBER_A1 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
    private static final UUID MEMBER_A2 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
    private static final UUID MEMBER_A3 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3");
    private static final UUID MEMBER_A4 = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc4");

    @Mock
    private TripWaypointRepository tripWaypointRepository;
    @Mock
    private LakeFeatureRepository lakeFeatureRepository;
    @Mock
    private LakeFishingTargetRepository fishingTargetRepository;
    @Mock
    private FishingSessionRepository sessionRepository;
    @Mock
    private SessionWaypointProgressRepository progressRepository;

    @Test
    void annotatesCooledPackageAndKeepsDisjointSameZonePackageEligible() {
        TripWaypoint cooled = zoneWaypoint(FIRST_A, 1, 44.751, -78.921, List.of(MEMBER_A1, MEMBER_A2));
        TripWaypoint eligible = zoneWaypoint(SECOND_A, 2, 44.752, -78.919, List.of(MEMBER_A3, MEMBER_A4));
        when(tripWaypointRepository.findNearby(44.75, -78.92, 400)).thenReturn(List.of(cooled, eligible));
        when(tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(cooled, eligible));
        when(lakeFeatureRepository.findNearby(44.75, -78.92, 400)).thenReturn(List.of());
        when(fishingTargetRepository.findNearby(44.75, -78.92, 400)).thenReturn(List.of());
        FishingSession session = new FishingSession();
        session.setId(SESSION_ID);
        session.setTripPlanId(PLAN_ID);
        session.setStatus(FishingSessionStatus.ACTIVE);
        when(sessionRepository.findFirstByTripPlanIdAndStatusOrderByStartedAtDesc(PLAN_ID, FishingSessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));
        SessionWaypointProgress left = new SessionWaypointProgress();
        left.setTripWaypointId(FIRST_A);
        left.setSequence(1);
        left.setStatus(WaypointProgressStatus.COMPLETED);
        left.setArrivedAt(NOW.minusSeconds(1800));
        left.setDepartedAt(NOW.minusSeconds(600));
        left.setAccumulatedDwellSeconds(300);
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID)).thenReturn(List.of(left));

        GetNearbyWaypointsTool tool = new GetNearbyWaypointsTool(
                tripWaypointRepository,
                lakeFeatureRepository,
                fishingTargetRepository,
                CLOCK,
                sessionRepository,
                progressRepository,
                new OpportunityRevisitSupport(new GuidanceProperties(), new SessionProperties(), CLOCK)
        );
        ToolResultEnvelope result = tool.execute(nearbyRequest(44.75, -78.92, 400));

        assertThat(result.status()).isEqualTo(ToolResultStatus.OK);
        assertThat(result.data().path("count").asInt()).isEqualTo(2);
        JsonNode cooledNode = item(result, FIRST_A);
        JsonNode eligibleNode = item(result, SECOND_A);
        assertThat(cooledNode.path("packageEligibility").asText()).isEqualTo(OpportunityRevisitSupport.COOLED);
        assertThat(eligibleNode.path("packageEligibility").asText()).isEqualTo(OpportunityRevisitSupport.ELIGIBLE);
        assertThat(cooledNode.path("physicalZoneId").asText()).isEqualTo(ZONE_ID.toString());
        assertThat(eligibleNode.path("physicalZoneId").asText()).isEqualTo(ZONE_ID.toString());
        assertThat(result.data().path("waypoints")).hasSize(2);
    }

    private static JsonNode item(ToolResultEnvelope result, UUID id) {
        for (JsonNode node : result.data().path("waypoints")) {
            if (id.toString().equals(node.path("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("missing waypoint " + id);
    }

    private static TripWaypoint zoneWaypoint(UUID id, int sequence, double lat, double lng, List<UUID> members) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setId(id);
        waypoint.setTripPlanId(PLAN_ID);
        waypoint.setSequence(sequence);
        waypoint.setLocation(point(lat, lng));
        waypoint.setFeatureType(FeatureType.HUMP);
        waypoint.setTargetKind(TargetKind.ZONE);
        waypoint.setVisitKind(TargetKind.ZONE);
        waypoint.setZoneId(ZONE_ID);
        waypoint.setMetadata(Map.of(
                TripWaypointPlanMetadata.PACKAGE_MEMBER_IDS,
                members.stream().map(UUID::toString).toList()
        ));
        return waypoint;
    }

    private static ToolRequestEnvelope nearbyRequest(double lat, double lng, int radius) {
        ObjectNode args = GuidanceContracts.mapper().createObjectNode();
        args.put("latitudeWgs84", lat);
        args.put("longitudeWgs84", lng);
        args.put("radiusMeters", radius);
        args.put("targetSpecies", FishSpecies.SMALLMOUTH_BASS.name());
        return new ToolRequestEnvelope(GuidanceSchemaVersion.VALUE, ToolName.GET_NEARBY_WAYPOINTS, args, NOW);
    }

    private static Point point(double lat, double lng) {
        Point created = GEOMETRY.createPoint(new Coordinate(lng, lat));
        created.setSRID(4326);
        return created;
    }
}

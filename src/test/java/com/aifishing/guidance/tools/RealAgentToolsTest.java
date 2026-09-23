package com.aifishing.guidance.tools;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.LiveWaypointActivity;
import com.aifishing.guidance.contracts.LiveWaypointPressure;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.contracts.ToolResultStatus;
import com.aifishing.guidance.spi.AgentTool;
import com.aifishing.guidance.spi.AgentToolRegistry;
import com.aifishing.guidance.spi.LiveWaypointActivityStore;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.domain.TripWaypointPlanMetadata;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.planning.spatial.CastingOpportunity;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import com.aifishing.planning.spatial.domain.TripStopSubtarget;
import com.aifishing.planning.spatial.repo.LakeFishingTargetRepository;
import com.aifishing.planning.spatial.repo.TripStopSubtargetRepository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealAgentToolsTest {

    private static final Instant NOW = Instant.parse("2026-09-16T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final UUID TRIP_WP = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FEATURE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID TARGET_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private TripWaypointRepository tripWaypointRepository;
    @Mock
    private LakeFeatureRepository lakeFeatureRepository;
    @Mock
    private LakeFishingTargetRepository fishingTargetRepository;
    @Mock
    private TripStopSubtargetRepository subtargetRepository;
    @Mock
    private LiveWaypointActivityStore liveStore;

    private GetNearbyWaypointsTool nearby;
    private GetWaypointStructureTool structure;
    private GetLiveWaypointActivityTool live;

    @BeforeEach
    void setUp() {
        nearby = new GetNearbyWaypointsTool(
                tripWaypointRepository, lakeFeatureRepository, fishingTargetRepository, CLOCK, null, null, null);
        structure = new GetWaypointStructureTool(
                tripWaypointRepository, lakeFeatureRepository, fishingTargetRepository, CLOCK);
        live = new GetLiveWaypointActivityTool(liveStore, CLOCK);
    }

    @Test
    void defaultRegistryRegistersAllSixRealTools() {
        AgentToolRegistry registry = new GuidanceToolsConfiguration().agentToolRegistry(
                nearby,
                structure,
                live,
                new GetHistoricalPerformanceTool(null, CLOCK),
                new GetFishingKnowledgeTool(CLOCK),
                new GetAlternativeRouteTool(null, null, null, null, null, CLOCK)
        );

        assertThat(registry.all()).hasSize(ToolName.values().length);
        for (ToolName name : ToolName.values()) {
            Optional<AgentTool> tool = registry.get(name);
            assertThat(tool).isPresent();
            assertThat(tool.get().name()).isEqualTo(name);
        }
    }

    @Test
    void nearbyWaypointsHappyPathFiltersByRadius() {
        TripWaypoint waypoint = tripWaypoint(44.751, -78.921, FeatureType.POINT, TargetKind.POINT);
        LakeFishingTarget target = spatialTarget(44.752, -78.919, FeatureType.DROP_OFF, TargetKind.PATH);
        when(tripWaypointRepository.findNearby(44.75, -78.92, 400)).thenReturn(List.of(waypoint));
        when(lakeFeatureRepository.findNearby(44.75, -78.92, 400)).thenReturn(List.of());
        when(fishingTargetRepository.findNearby(44.75, -78.92, 400)).thenReturn(List.of(target));

        ToolResultEnvelope result = nearby.execute(nearbyRequest(44.75, -78.92, 400));

        assertThat(result.status()).isEqualTo(ToolResultStatus.OK);
        assertThat(result.source()).isEqualTo(ToolName.GET_NEARBY_WAYPOINTS.wire());
        assertThat(result.data().path("count").asInt()).isEqualTo(2);
        assertThat(result.data().path("waypoints")).hasSize(2);
        assertThat(result.data().findValuesAsText("kind"))
                .containsExactlyInAnyOrder(
                        GetNearbyWaypointsTool.KIND_TRIP_WAYPOINT,
                        GetNearbyWaypointsTool.KIND_SPATIAL_TARGET
                );
        result.data().path("waypoints").forEach(item ->
                assertThat(item.path("distanceMeters").asDouble()).isLessThanOrEqualTo(400));
        assertThat(result.data().toString()).doesNotContain("stackTrace", "stack_trace");
    }

    @Test
    void nearbyWaypointsUnknownWhenNothingInRadius() {
        when(tripWaypointRepository.findNearby(44.75, -78.92, 400)).thenReturn(List.of());
        when(lakeFeatureRepository.findNearby(44.75, -78.92, 400)).thenReturn(List.of());
        when(fishingTargetRepository.findNearby(44.75, -78.92, 400)).thenReturn(List.of());

        ToolResultEnvelope result = nearby.execute(nearbyRequest(44.75, -78.92, 400));

        assertThat(result.status()).isEqualTo(ToolResultStatus.UNKNOWN);
        assertThat(result.data()).isNull();
    }

    @Test
    void waypointStructureHappyPathUsesWaypointAndLakeFeatureFields() {
        TripWaypoint waypoint = tripWaypoint(44.75, -78.92, FeatureType.DROP_OFF, TargetKind.PATH);
        waypoint.setLakeFeatureId(FEATURE_ID);
        waypoint.setMinDepthM(BigDecimal.valueOf(3.5));
        waypoint.setMaxDepthM(BigDecimal.valueOf(8.0));
        waypoint.setRepresentativeDepthM(BigDecimal.valueOf(5.2));
        LakeFeature feature = lakeFeature();
        when(tripWaypointRepository.findById(TRIP_WP)).thenReturn(Optional.of(waypoint));
        when(lakeFeatureRepository.findById(FEATURE_ID)).thenReturn(Optional.of(feature));

        ToolResultEnvelope result = structure.execute(structureRequest(TRIP_WP));

        assertThat(result.status()).isEqualTo(ToolResultStatus.OK);
        assertThat(result.source()).isEqualTo(ToolName.GET_WAYPOINT_STRUCTURE.wire());
        assertThat(result.data().path("tripWaypointId").asText()).isEqualTo(TRIP_WP.toString());
        assertThat(result.data().path("featureType").asText()).isEqualTo(FeatureType.DROP_OFF.name());
        assertThat(result.data().path("targetKind").asText()).isEqualTo(TargetKind.PATH.name());
        assertThat(result.data().path("minDepthM").asDouble()).isEqualTo(3.5);
        assertThat(result.data().path("maxDepthM").asDouble()).isEqualTo(8.0);
        assertThat(result.data().path("representativeDepthM").asDouble()).isEqualTo(5.2);
        assertThat(result.data().path("slope").asDouble()).isEqualTo(0.18);
        assertThat(result.data().path("orientation").asDouble()).isEqualTo(90.0);
        assertThat(result.data().path("areaM2").asDouble()).isEqualTo(1400.0);
        assertThat(result.data().has("confidence")).isFalse();
        assertThat(result.data().has("depth")).isFalse();
        assertThat(result.data().path("zoneId").asText()).isEqualTo(FEATURE_ID.toString());
        assertThat(result.data().path("packageMemberIds")).hasSize(1);
        assertThat(result.data().path("packageMemberIds").get(0).asText()).isEqualTo(TARGET_ID.toString());
        assertThat(result.data().toString()).doesNotContain("stackTrace");
    }

    @Test
    void waypointStructureListsOnlyThisStepsPackageMembers() {
        UUID zoneId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        UUID memberA1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID memberA2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID memberA3 = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID memberA4 = UUID.fromString("44444444-4444-4444-4444-444444444444");
        TripWaypoint secondA = tripWaypoint(44.75, -78.92, FeatureType.HUMP, TargetKind.ZONE);
        secondA.setZoneId(zoneId);
        secondA.setMetadata(java.util.Map.of(
                com.aifishing.planning.domain.TripWaypointPlanMetadata.PACKAGE_MEMBER_IDS,
                List.of(memberA3.toString(), memberA4.toString())
        ));
        when(tripWaypointRepository.findById(TRIP_WP)).thenReturn(Optional.of(secondA));

        ToolResultEnvelope result = structure.execute(structureRequest(TRIP_WP));

        assertThat(result.status()).isEqualTo(ToolResultStatus.OK);
        assertThat(result.data().path("zoneId").asText()).isEqualTo(zoneId.toString());
        assertThat(result.data().path("packageMemberIds")).hasSize(2);
        assertThat(result.data().path("packageMemberIds").get(0).asText()).isEqualTo(memberA3.toString());
        assertThat(result.data().path("packageMemberIds").get(1).asText()).isEqualTo(memberA4.toString());
        assertThat(result.data().toString()).doesNotContain(memberA1.toString(), memberA2.toString());
    }

    @Test
    void waypointStructureExposesAnchorAndCompanionsWithoutANewStop() {
        UUID anchor = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID companion = UUID.fromString("22222222-2222-2222-2222-222222222222");
        TripWaypoint waypoint = tripWaypoint(44.75, -78.92, FeatureType.HUMP, TargetKind.ZONE);
        when(tripWaypointRepository.findById(TRIP_WP)).thenReturn(Optional.of(waypoint));
        when(subtargetRepository.findByTripWaypointIdOrderBySequenceAsc(TRIP_WP)).thenReturn(List.of(
                subtarget(anchor, 20, CastingOpportunity.ANCHOR_REASON),
                subtarget(companion, 0, CastingOpportunity.COMPANION_REASON)
        ));
        GetWaypointStructureTool withCompanions = new GetWaypointStructureTool(
                tripWaypointRepository, lakeFeatureRepository, fishingTargetRepository, subtargetRepository, CLOCK);

        ToolResultEnvelope result = withCompanions.execute(structureRequest(TRIP_WP));

        assertThat(result.status()).isEqualTo(ToolResultStatus.OK);
        assertThat(result.data().path("castingOpportunities")).hasSize(1);
        assertThat(result.data().path("castingOpportunities").get(0).path("anchorFishingTargetId").asText())
                .isEqualTo(anchor.toString());
        assertThat(result.data().path("castingOpportunities").get(0).path("dwellMinutes").asInt()).isEqualTo(20);
        assertThat(result.data().path("castingOpportunities").get(0).path("companionFishingTargetIds"))
                .hasSize(1);
        assertThat(result.data().path("castingOpportunities").get(0).path("companionFishingTargetIds").get(0).asText())
                .isEqualTo(companion.toString());
    }

    @Test
    void liveWaypointActivityIsUnknownWhenStoreCannotResolveCoords() {
        when(liveStore.findNearby(eq(TRIP_WP), anyInt(), nullable(UUID.class))).thenReturn(Optional.empty());

        ToolResultEnvelope result = live.execute(liveRequest(TRIP_WP, 200));

        assertThat(result.status()).isEqualTo(ToolResultStatus.UNKNOWN);
        assertThat(result.source()).isEqualTo(ToolName.GET_LIVE_WAYPOINT_ACTIVITY.wire());
        assertThat(result.data()).isNull();
        assertThat(result.errorType()).isNull();
        verify(liveStore).findNearby(TRIP_WP, 200, null);
    }

    @Test
    void liveWaypointActivityReturnsProjectionWithoutScanningRawGps() {
        LiveWaypointActivity activity = new LiveWaypointActivity(
                GuidanceSchemaVersion.VALUE, TRIP_WP, 300, 1, 0, LiveWaypointPressure.MODERATE, NOW);
        when(liveStore.findNearby(eq(TRIP_WP), eq(300), isNull())).thenReturn(Optional.of(activity));

        ToolResultEnvelope result = live.execute(liveRequest(TRIP_WP, 300));

        assertThat(result.status()).isEqualTo(ToolResultStatus.OK);
        assertThat(result.source()).isEqualTo(ToolName.GET_LIVE_WAYPOINT_ACTIVITY.wire());
        assertThat(result.data().path("tripWaypointId").asText()).isEqualTo(TRIP_WP.toString());
        assertThat(result.data().path("radiusMeters").asInt()).isEqualTo(300);
        assertThat(result.data().path("activeAnglersNearby").asInt()).isEqualTo(1);
        assertThat(result.data().path("recentFishOnCount").asInt()).isEqualTo(0);
        assertThat(result.data().path("pressure").asText()).isEqualTo(LiveWaypointPressure.MODERATE.name());
        assertThat(result.data().toString()).doesNotContain("sessionId", "userId", "fishingSessionId");
        assertThat(Arrays.stream(GetLiveWaypointActivityTool.class.getDeclaredFields()).map(Field::getType))
                .doesNotContain(SessionLocationPointRepository.class, SessionLocationPoint.class);
        ArgumentCaptor<Integer> radius = ArgumentCaptor.forClass(Integer.class);
        verify(liveStore).findNearby(eq(TRIP_WP), radius.capture(), isNull());
        assertThat(radius.getValue()).isEqualTo(300);
    }

    @Test
    void liveWaypointActivityDefaultsToMeterRadiusAndDoesNotInventOccupancy() {
        when(liveStore.findNearby(eq(TRIP_WP), eq(GetLiveWaypointActivityTool.DEFAULT_RADIUS_METERS), isNull()))
                .thenReturn(Optional.of(new LiveWaypointActivity(
                        GuidanceSchemaVersion.VALUE, TRIP_WP, 300, 0, 0, LiveWaypointPressure.LOW, NOW)));

        ObjectNode args = GuidanceContracts.mapper().createObjectNode();
        args.put("tripWaypointId", TRIP_WP.toString());
        ToolResultEnvelope result = live.execute(new ToolRequestEnvelope(
                GuidanceSchemaVersion.VALUE, ToolName.GET_LIVE_WAYPOINT_ACTIVITY, args, NOW));

        assertThat(result.status()).isEqualTo(ToolResultStatus.OK);
        assertThat(result.data().path("activeAnglersNearby").asInt()).isEqualTo(0);
        verify(liveStore).findNearby(TRIP_WP, 300, null);
    }

    private static ToolRequestEnvelope nearbyRequest(double lat, double lng, int radius) {
        ObjectNode args = GuidanceContracts.mapper().createObjectNode();
        args.put("latitudeWgs84", lat);
        args.put("longitudeWgs84", lng);
        args.put("radiusMeters", radius);
        args.put("targetSpecies", FishSpecies.SMALLMOUTH_BASS.name());
        return new ToolRequestEnvelope(GuidanceSchemaVersion.VALUE, ToolName.GET_NEARBY_WAYPOINTS, args, NOW);
    }

    private static ToolRequestEnvelope structureRequest(UUID tripWaypointId) {
        ObjectNode args = GuidanceContracts.mapper().createObjectNode();
        args.put("tripWaypointId", tripWaypointId.toString());
        return new ToolRequestEnvelope(GuidanceSchemaVersion.VALUE, ToolName.GET_WAYPOINT_STRUCTURE, args, NOW);
    }

    private static ToolRequestEnvelope liveRequest(UUID tripWaypointId, int radius) {
        ObjectNode args = GuidanceContracts.mapper().createObjectNode();
        args.put("tripWaypointId", tripWaypointId.toString());
        args.put("radiusMeters", radius);
        return new ToolRequestEnvelope(GuidanceSchemaVersion.VALUE, ToolName.GET_LIVE_WAYPOINT_ACTIVITY, args, NOW);
    }

    private static TripStopSubtarget subtarget(UUID fishingTargetId, int fishingMinutes, String reason) {
        TripStopSubtarget row = new TripStopSubtarget();
        row.setFishingTargetId(fishingTargetId);
        row.setPlannedFishingMinutes(fishingMinutes);
        row.setReason(reason);
        return row;
    }

    private static TripWaypoint tripWaypoint(double lat, double lng, FeatureType type, TargetKind kind) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setId(TRIP_WP);
        waypoint.setTripPlanId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
        waypoint.setSequence(1);
        waypoint.setLocation(point(lat, lng));
        waypoint.setFeatureType(type);
        waypoint.setTargetKind(kind);
        waypoint.setZoneId(FEATURE_ID);
        waypoint.setMetadata(java.util.Map.of(
                TripWaypointPlanMetadata.PACKAGE_MEMBER_IDS,
                List.of(TARGET_ID.toString())
        ));
        return waypoint;
    }

    private static LakeFeature lakeFeature() {
        LakeFeature feature = new LakeFeature();
        feature.setId(FEATURE_ID);
        feature.setType(FeatureType.DROP_OFF);
        feature.setGeometry(point(44.75, -78.92));
        feature.setSlope(BigDecimal.valueOf(0.18));
        feature.setOrientation(BigDecimal.valueOf(90));
        feature.setAreaM2(BigDecimal.valueOf(1400));
        return feature;
    }

    private static LakeFishingTarget spatialTarget(double lat, double lng, FeatureType type, TargetKind kind) {
        LakeFishingTarget target = new LakeFishingTarget();
        target.setId(TARGET_ID);
        target.setRepresentativePoint(point(lat, lng));
        target.setSemanticType(type);
        target.setTargetKind(kind);
        return target;
    }

    private static Point point(double lat, double lng) {
        Point point = GEOMETRY.createPoint(new Coordinate(lng, lat));
        point.setSRID(4326);
        return point;
    }
}

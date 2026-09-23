package com.aifishing.planning.candidate;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.SpotRankingService;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.SpatialSnapshotView;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.VisitPortal;
import com.aifishing.planning.spatial.domain.LakeFishingZone;
import com.aifishing.planning.spatial.domain.LakeFishingZoneMember;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MacroCandidateShortlistTest {

    private final MacroCandidateShortlist shortlist = new MacroCandidateShortlist(new SpotRankingService());

    @AfterEach
    void clear() {
        com.aifishing.planning.spatial.GenerateProfiler.clear();
    }

    @Test
    void A_nearbyMixedClusterSurvivesAsZone() {
        Fixture fixture = mixedClusterPlusStars();
        List<CandidateSpot> kept = shortlist.select(fixture.accepted, fixture.context);
        assertThat(kept.stream().anyMatch(spot -> spot.getTargetKind() == TargetKind.ZONE)).isTrue();
        CandidateSpot zone = kept.stream().filter(spot -> spot.getTargetKind() == TargetKind.ZONE).findFirst().orElseThrow();
        Set<FeatureType> types = zone.getZoneMembers().stream().map(CandidateSpot::getType).collect(Collectors.toSet());
        assertThat(types.size()).isGreaterThan(1);
        assertThat(zone.getZoneMembers().size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void B_farHighScorePointCanStillBeSelected() {
        Fixture fixture = weakNearAndStrongFar();
        List<CandidateSpot> kept = shortlist.select(fixture.accepted, fixture.context);
        Set<UUID> ids = kept.stream().map(CandidateSpot::getFishingTargetId).collect(Collectors.toSet());
        assertThat(ids).contains(fixture.farId);
    }

    @Test
    void C_farGatewayClusterIsSelectable() {
        Fixture fixture = farGatewayCluster();
        List<CandidateSpot> kept = shortlist.select(fixture.accepted, fixture.context);
        assertThat(kept.stream().anyMatch(spot -> fixture.nearZoneId().equals(spot.getZoneId()))).isTrue();
    }

    @Test
    void D_mixedTypesInside150mStayZoneMembers() {
        Fixture fixture = mixedClusterPlusStars();
        List<CandidateSpot> kept = shortlist.select(fixture.accepted, fixture.context);
        CandidateSpot zone = kept.stream().filter(spot -> spot.getTargetKind() == TargetKind.ZONE).findFirst().orElseThrow();
        assertThat(zone.getZoneMembers()).hasSizeGreaterThanOrEqualTo(4);
        double min = Double.POSITIVE_INFINITY;
        List<CandidateSpot> members = zone.getZoneMembers();
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                min = Math.min(min, com.aifishing.lake.processing.extract.GeoMetrics.distanceM(
                        members.get(i).getLocation(), members.get(j).getLocation()));
            }
        }
        assertThat(min).isLessThan(150);
    }

    @Test
    void E_simcoeShapedNearbyZoneNotWipedByFarStars() {
        Fixture fixture = mixedClusterPlusStars();
        List<CandidateSpot> kept = shortlist.select(fixture.accepted, fixture.context);
        assertThat(kept.stream().anyMatch(spot -> fixture.nearZoneId.equals(spot.getZoneId()))).isTrue();
    }

    @Test
    void F_unassignedAtomicsAreCapped() {
        PlanningContext context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 4, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        );
        context.properties().getCandidates().setMaxUnassignedAtomics(8);
        context.properties().getCandidates().setMaxMacroZones(8);
        List<CandidateSpot> accepted = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            accepted.add(atomic("u-" + i, i * 300, (i % 5) * 250, FeatureType.HUMP, 0.7));
        }
        com.aifishing.planning.spatial.GenerateProfiler.begin();
        List<CandidateSpot> kept = shortlist.select(accepted, context);
        assertThat(kept).hasSize(8);
        assertThat(com.aifishing.planning.spatial.GenerateProfiler.current().compression().selectedTargetsWithNoPhysicalZone())
                .isEqualTo(40);
        assertThat(com.aifishing.planning.spatial.GenerateProfiler.current().compression()
                .count(CandidateCompressionReason.REGIONAL_CANDIDATE_BUDGET)
                + com.aifishing.planning.spatial.GenerateProfiler.current().compression()
                .count(CandidateCompressionReason.FEATURE_TYPE_BUDGET)).isEqualTo(32);
    }

    @Test
    void G_stratificationGivesNearbyRegionASlotDespiteHigherFarScores() {
        List<CandidateSpot> accepted = new ArrayList<>();
        List<LakeFishingZone> zones = new ArrayList<>();
        Map<UUID, List<LakeFishingZoneMember>> membersByZone = new HashMap<>();
        Map<UUID, List<VisitPortal>> portals = new HashMap<>();
        UUID nearId = addZone("near-low", 80, 0, 0.55, FeatureType.HUMP, accepted, zones, membersByZone, portals);
        addZone("far-a", 4000, 0, 0.97, FeatureType.BASIN, accepted, zones, membersByZone, portals);
        addZone("far-b", 4100, 80, 0.96, FeatureType.BASIN, accepted, zones, membersByZone, portals);
        addZone("far-c", 4200, 40, 0.95, FeatureType.BASIN, accepted, zones, membersByZone, portals);
        PlanningContext context = snapshot(zones, membersByZone, portals);
        context.properties().getCandidates().setMaxMacroZones(2);
        context.properties().getCandidates().setMaxUnassignedAtomics(1);
        com.aifishing.planning.spatial.GenerateProfiler.begin();
        List<CandidateSpot> kept = shortlist.select(accepted, context);
        assertThat(kept.stream().anyMatch(spot -> nearId.equals(spot.getZoneId()))).isTrue();
        assertThat(kept.stream().filter(spot -> spot.getTargetKind() == TargetKind.ZONE).count()).isEqualTo(2);
    }

    @Test
    void H_regionalTypeCapDoesNotApplyLakeWideAndLeavesZoneMembersIntact() {
        List<CandidateSpot> accepted = new ArrayList<>();
        UUID zoneId = UUID.nameUUIDFromBytes("dense-drops".getBytes());
        List<LakeFishingZoneMember> members = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            CandidateSpot spot = atomic("drop-" + i, 40 + i * 40, 20, FeatureType.DROP_OFF, 0.8);
            accepted.add(spot);
            LakeFishingZoneMember member = new LakeFishingZoneMember();
            member.setZoneId(zoneId);
            member.setFishingTargetId(spot.getFishingTargetId());
            member.setSequence(i);
            members.add(member);
        }
        for (int region = 0; region < 3; region++) {
            for (int i = 0; i < 12; i++) {
                accepted.add(atomic(
                        "r" + region + "-" + i,
                        3000 + region * 3000 + i * 40,
                        region * 100,
                        FeatureType.DROP_OFF,
                        0.7));
            }
        }
        Fixture fixture = snapshotContext(zoneId, members, accepted, accepted.get(0).getLocation());
        fixture.context.properties().getCandidates().setMaxPerRegionFeatureType(6);
        fixture.context.properties().getCandidates().setMaxUnassignedAtomics(30);
        fixture.context.properties().getCandidates().setMaxMacroZones(4);
        fixture.context.properties().getCandidates().setMaxTotal(32);
        com.aifishing.planning.spatial.GenerateProfiler.begin();
        MacroCandidateWorld world = shortlist.selectWorld(fixture.accepted, fixture.context);
        assertThat(world.physicalZones()).hasSize(1);
        assertThat(world.physicalZones().get(0).reachableZoneMembers()).hasSize(8);
        assertThat(world.physicalZones().get(0).macroRepresentatives().size()).isLessThanOrEqualTo(6);
        assertThat(world.physicalZones().get(0).zoneSpot().getZoneMembers()).hasSize(8);
        long dropOffAtomics = world.beamSpots().stream()
                .filter(spot -> spot.getTargetKind() != TargetKind.ZONE)
                .filter(spot -> spot.getType() == FeatureType.DROP_OFF)
                .count();
        assertThat(dropOffAtomics).isGreaterThan(10);
        fixture.context.properties().getCandidates().setMaxTotal(8);
        MacroCandidateWorld again = shortlist.selectWorld(fixture.accepted, fixture.context);
        assertThat(again.beamSpots()).hasSameSizeAs(world.beamSpots());
    }

    @Test
    void I_unassignedBucketsAreNotPhysicalZones() {
        PlanningContext context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 4, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        );
        List<CandidateSpot> accepted = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            accepted.add(atomic("iso-" + i, i * 2600, 0, FeatureType.HUMP, 0.7));
        }
        com.aifishing.planning.spatial.GenerateProfiler.begin();
        MacroCandidateWorld world = shortlist.selectWorld(accepted, context);
        assertThat(world.physicalZones()).isEmpty();
        assertThat(world.unassignedBuckets()).isNotEmpty();
        assertThat(world.beamSpots()).allMatch(spot -> spot.getTargetKind() != TargetKind.ZONE);
    }

    private static UUID addZone(
            String key,
            double eastM,
            double northM,
            double weight,
            FeatureType type,
            List<CandidateSpot> accepted,
            List<LakeFishingZone> zones,
            Map<UUID, List<LakeFishingZoneMember>> membersByZone,
            Map<UUID, List<VisitPortal>> portals
    ) {
        UUID zoneId = UUID.nameUUIDFromBytes(key.getBytes());
        List<LakeFishingZoneMember> members = new ArrayList<>();
        Point representative = null;
        for (int i = 0; i < 3; i++) {
            CandidateSpot spot = atomic(key + "-m" + i, eastM + i * 40, northM, type, weight);
            accepted.add(spot);
            LakeFishingZoneMember member = new LakeFishingZoneMember();
            member.setZoneId(zoneId);
            member.setFishingTargetId(spot.getFishingTargetId());
            member.setSequence(i);
            members.add(member);
            if (representative == null) {
                representative = spot.getLocation();
            }
        }
        LakeFishingZone zone = new LakeFishingZone();
        zone.setId(zoneId);
        zone.setRepresentativePoint(representative);
        zone.setFeaturePipeline(Pipeline.GIS);
        zone.setFeatureAnalysisVersion("plan-v1");
        zones.add(zone);
        membersByZone.put(zoneId, members);
        portals.put(zoneId, List.of(new VisitPortal("p0", representative), new VisitPortal("p1", representative)));
        return zoneId;
    }

    private static PlanningContext snapshot(
            List<LakeFishingZone> zones,
            Map<UUID, List<LakeFishingZoneMember>> membersByZone,
            Map<UUID, List<VisitPortal>> portals
    ) {
        SpatialPlanningSnapshot snapshot = new SpatialPlanningSnapshot();
        snapshot.setId(UUID.randomUUID());
        SpatialSnapshotView view = new SpatialSnapshotView(
                snapshot, List.of(), Map.of(), zones, membersByZone, portals, Map.of(), Map.of(), null);
        return RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 4, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        ).withSnapshot(view);
    }

    private static Fixture mixedClusterPlusStars() {
        UUID zoneId = UUID.nameUUIDFromBytes("near-zone".getBytes());
        List<CandidateSpot> accepted = new ArrayList<>();
        List<LakeFishingZoneMember> members = new ArrayList<>();
        FeatureType[] types = {FeatureType.DROP_OFF, FeatureType.HUMP, FeatureType.POINT, FeatureType.FLAT};
        for (int i = 0; i < 4; i++) {
            CandidateSpot spot = atomic("near-" + i, 40 + i * 50, 30, types[i], 0.72);
            accepted.add(spot);
            LakeFishingZoneMember member = new LakeFishingZoneMember();
            member.setZoneId(zoneId);
            member.setFishingTargetId(spot.getFishingTargetId());
            member.setSequence(i);
            members.add(member);
        }
        for (int i = 0; i < 20; i++) {
            accepted.add(atomic("star-" + i, 4000 + i * 300, 2500, FeatureType.BASIN, 0.97));
        }
        return snapshotContext(zoneId, members, accepted, accepted.get(0).getLocation());
    }

    private static Fixture weakNearAndStrongFar() {
        CandidateSpot near = atomic("weak-near", 80, 0, FeatureType.HUMP, 0.4);
        CandidateSpot far = atomic("strong-far", 3500, 0, FeatureType.BASIN, 0.95);
        PlanningContext context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 4, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        );
        return new Fixture(List.of(near, far), context, far.getFishingTargetId(), null);
    }

    private static Fixture farGatewayCluster() {
        UUID zoneId = UUID.nameUUIDFromBytes("far-zone".getBytes());
        List<CandidateSpot> accepted = new ArrayList<>();
        List<LakeFishingZoneMember> members = new ArrayList<>();
        accepted.add(atomic("near-single", 60, 0, FeatureType.HUMP, 0.5));
        for (int i = 0; i < 4; i++) {
            CandidateSpot spot = atomic("far-g-" + i, 3200 + i * 80, 1800, FeatureType.DROP_OFF, 0.86);
            accepted.add(spot);
            LakeFishingZoneMember member = new LakeFishingZoneMember();
            member.setZoneId(zoneId);
            member.setFishingTargetId(spot.getFishingTargetId());
            member.setSequence(i);
            members.add(member);
        }
        Fixture base = snapshotContext(zoneId, members, accepted, accepted.get(1).getLocation());
        return new Fixture(base.accepted, base.context, null, zoneId);
    }

    private static Fixture snapshotContext(
            UUID zoneId,
            List<LakeFishingZoneMember> members,
            List<CandidateSpot> accepted,
            Point representative
    ) {
        LakeFishingZone zone = new LakeFishingZone();
        zone.setId(zoneId);
        zone.setRepresentativePoint(representative);
        zone.setFeaturePipeline(Pipeline.GIS);
        zone.setFeatureAnalysisVersion("plan-v1");
        SpatialPlanningSnapshot snapshot = new SpatialPlanningSnapshot();
        snapshot.setId(UUID.randomUUID());
        Map<UUID, List<LakeFishingZoneMember>> membersByZone = new HashMap<>();
        membersByZone.put(zoneId, members);
        Map<UUID, List<VisitPortal>> portals = new HashMap<>();
        portals.put(zoneId, List.of(new VisitPortal("p0", representative), new VisitPortal("p1", representative)));
        SpatialSnapshotView view = new SpatialSnapshotView(
                snapshot, List.of(), Map.of(), List.of(zone), membersByZone, portals, Map.of(), Map.of(), null);
        PlanningContext context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 4, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        ).withSnapshot(view);
        return new Fixture(accepted, context, null, zoneId);
    }

    private static CandidateSpot atomic(String key, double eastM, double northM, FeatureType type, double weight) {
        UUID id = UUID.nameUUIDFromBytes(key.getBytes());
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(id);
        spot.setFishingTargetId(id);
        spot.setType(type);
        spot.setStrategyWeight(weight);
        spot.setFeatureConfidence(0.8);
        spot.setRepresentativeDepthM(3.5);
        double lat = PlanningFixtures.HEAD_LAT + northM / 111_320.0;
        double lng = PlanningFixtures.HEAD_LNG + eastM / (111_320.0 * Math.cos(Math.toRadians(PlanningFixtures.HEAD_LAT)));
        spot.setLocation(RoutePlannerHarness.point(lng, lat));
        return spot;
    }

    private record Fixture(List<CandidateSpot> accepted, PlanningContext context, UUID farId, UUID nearZoneId) {
        UUID farZoneId() {
            return nearZoneId;
        }
    }
}

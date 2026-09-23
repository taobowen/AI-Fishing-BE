package com.aifishing.planning.candidate;

import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.common.enums.WindWaveCapability;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.SpotRankingService;
import com.aifishing.planning.route.RoutePlanner;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.search.SearchParameterResolver;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.FishingVisitOption;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.planning.spatial.SpatialSnapshotView;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.VisitOptionFactory;
import com.aifishing.planning.spatial.VisitPortal;
import com.aifishing.planning.spatial.domain.LakeFishingZone;
import com.aifishing.planning.spatial.domain.LakeFishingZoneMember;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class SimcoeShapedGeneratePlanRegressionTest {

    private final MacroCandidateShortlist shortlist = new MacroCandidateShortlist(new SpotRankingService());
    private final SpotRankingService ranking = new SpotRankingService();
    private final VisitOptionFactory visitOptions = new VisitOptionFactory();
    private final RoutePlanner planner = RoutePlannerHarness.planner();

    @AfterEach
    void clear() {
        GenerateProfiler.clear();
    }

    @Test
    void nearbyRegionSurvivesIntoBoundedSearchWithoutStarvingThePlan() throws Exception {
        Scenario scenario = Scenario.build();
        GenerateProfiler.begin();
        MacroCandidateWorld world = shortlist.selectWorld(scenario.accepted, scenario.context);
        assertThat(world.physicalZones().stream().anyMatch(zone -> scenario.nearZoneId.equals(zone.zoneId()))).isTrue();
        assertThat(world.beamSpots()).isNotEmpty();
        int cap = SearchParameterResolver.macroVisitOptionCap(scenario.context);
        List<RankedCandidate> ranked = rank(world.beamSpots(), scenario.context);
        List<FishingVisitOption> options = visitOptions.options(ranked, scenario.context.properties().getSpatial(), cap);
        assertThat(options).isNotEmpty();
        assertThat(options.size()).isLessThanOrEqualTo(cap);
        assertThat(options.stream().anyMatch(option -> scenario.nearZoneId.equals(option.candidate().spot().getZoneId())))
                .isTrue();

        RoutePlanner.RouteResult result = planner.plan(ranked, scenario.context);
        assertThat(result.stops()).isNotEmpty();
        assertThat(result.plannedReturnAt()).isNotNull();
        assertThat(result.plannedReturnAt().isAfter(TripClock.endAt(scenario.context))).isFalse();
        int tripMin = (int) Duration.between(TripClock.startAt(scenario.context), TripClock.endAt(scenario.context)).toMinutes();
        int unused = tripMin - result.totalFishingMinutes() - (int) Math.round(result.totalTravelMinutes());
        Path report = Path.of("docs", "reports", "simcoe-generate-plan-e2e.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, """
                # Simcoe-shaped generate-plan regression

                7–8 hour limited-range electric boat. Dense nearby PhysicalZone plus isolated high-value far basin.
                Nearby must reach Beam as a bounded macro option. Far may still win on total route utility.

                | metric | value |
                |---|---|
                | totalFishingMinutes | %d |
                | totalTravelMinutes | %.1f |
                | unusedMinutes | %d |
                | routeUtility | %.4f |
                | visitOptions | %d |
                | visitOptionCap | %d |
                | macroOptionsByRegion | %s |
                | nearbyPresentInSearch | true |
                | stops | %d |
                """.formatted(
                result.totalFishingMinutes(),
                result.totalTravelMinutes(),
                unused,
                result.routeUtility(),
                options.size(),
                cap,
                world.macroOptionsByRegion(),
                result.stops().size()));
        assertThat(world.macroOptionsByRegion()).isNotEmpty();
        assertThat(result.totalFishingMinutes() + result.totalTravelMinutes()).isGreaterThan(0);
    }

    private List<RankedCandidate> rank(List<CandidateSpot> spots, PlanningContext context) {
        List<RankedCandidate> ranked = new ArrayList<>();
        for (CandidateSpot spot : spots) {
            ranked.add(new RankedCandidate(spot, ranking.score(spot, context, null), null));
        }
        return ranked;
    }

    public record Scenario(List<CandidateSpot> accepted, PlanningContext context, UUID nearZoneId, UUID farZoneId) {
        public static Scenario build() {
            List<CandidateSpot> accepted = new ArrayList<>();
            List<LakeFishingZone> zones = new ArrayList<>();
            Map<UUID, List<LakeFishingZoneMember>> membersByZone = new HashMap<>();
            Map<UUID, List<VisitPortal>> portals = new HashMap<>();
            UUID nearId = zone("near-dense", 80, 40, 0.72, 6, accepted, zones, membersByZone, portals, mixed());
            UUID farId = zone("far-basin", 3500, 200, 0.97, 4, accepted, zones, membersByZone, portals,
                    new FeatureType[]{FeatureType.BASIN, FeatureType.BASIN, FeatureType.BASIN, FeatureType.BASIN});
            for (int i = 0; i < 24; i++) {
                accepted.add(atomic("star-" + i, 5200 + (i % 6) * 400, 1800 + (i / 6) * 350, FeatureType.HUMP, 0.88));
            }
            SpatialPlanningSnapshot snapshot = new SpatialPlanningSnapshot();
            snapshot.setId(UUID.randomUUID());
            SpatialSnapshotView view = new SpatialSnapshotView(
                    snapshot, List.of(), Map.of(), zones, membersByZone, portals, Map.of(), Map.of(), null);
            PlanningProperties properties = new PlanningProperties();
            properties.getCandidates().setMaxMacroZones(6);
            properties.getCandidates().setMaxUnassignedAtomics(6);
            properties.getCandidates().setMaxMacroVisitOptions(48);
            properties.getCandidates().setMaxPerRegionFeatureType(3);
            Polygon water = ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 8000);
            PlanningContext context = RoutePlannerHarness.context(
                    RoutePlannerHarness.hourly(List.of(
                            RoutePlannerHarness.hour(8, 0, 6, 20, 500),
                            RoutePlannerHarness.hour(16, 0, 6, 20, 400)
                    ), 6, 20),
                    properties,
                    RoutePlannerHarness.launch()
            );
            context.trip().setFishingStartTime(LocalTime.of(7, 0));
            context.trip().setFishingEndTime(LocalTime.of(15, 0));
            context = new PlanningContext(
                    context.trip(),
                    context.lake(),
                    context.boat(),
                    context.access(),
                    new com.aifishing.planning.candidate.LakePlanningGeometry(water, List.of()),
                    context.restrictions(),
                    context.regulationCoverageStatus(),
                    context.weather(),
                    context.gearTypes(),
                    context.profile(),
                    context.strategyRun(),
                    properties,
                    context.warnings(),
                    context.baselineBoatCapability(),
                    new EffectiveBoatCapability(
                            8.0, 16.0, 16.0, 12.0, 16.0, true, 8.0,
                            WindWaveCapability.MEDIUM, 0, Map.of()),
                    context.launch(),
                    view
            );
            return new Scenario(accepted, context, nearId, farId);
        }

        private static FeatureType[] mixed() {
            return new FeatureType[]{
                    FeatureType.DROP_OFF, FeatureType.HUMP, FeatureType.POINT,
                    FeatureType.FLAT, FeatureType.HUMP, FeatureType.DROP_OFF
            };
        }

        private static UUID zone(
                String key,
                double eastM,
                double northM,
                double weight,
                int members,
                List<CandidateSpot> accepted,
                List<LakeFishingZone> zones,
                Map<UUID, List<LakeFishingZoneMember>> membersByZone,
                Map<UUID, List<VisitPortal>> portalMap,
                FeatureType[] types
        ) {
            UUID zoneId = UUID.nameUUIDFromBytes(key.getBytes());
            List<LakeFishingZoneMember> memberRows = new ArrayList<>();
            Point representative = null;
            for (int i = 0; i < members; i++) {
                CandidateSpot spot = atomic(key + "-" + i, eastM + i * 45, northM, types[i % types.length], weight);
                accepted.add(spot);
                LakeFishingZoneMember member = new LakeFishingZoneMember();
                member.setZoneId(zoneId);
                member.setFishingTargetId(spot.getFishingTargetId());
                member.setSequence(i);
                memberRows.add(member);
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
            membersByZone.put(zoneId, memberRows);
            portalMap.put(zoneId, List.of(new VisitPortal("p0", representative), new VisitPortal("p1", representative)));
            return zoneId;
        }

        private static CandidateSpot atomic(String key, double eastM, double northM, FeatureType type, double weight) {
            UUID id = UUID.nameUUIDFromBytes(key.getBytes());
            CandidateSpot spot = new CandidateSpot();
            spot.setFeatureId(id);
            spot.setFishingTargetId(id);
            spot.setType(type);
            spot.setTargetKind(TargetKind.POINT);
            spot.setStrategyWeight(weight);
            spot.setFeatureConfidence(0.8);
            spot.setRepresentativeDepthM(3.5);
            double lat = PlanningFixtures.HEAD_LAT + northM / 111_320.0;
            double lng = PlanningFixtures.HEAD_LNG + eastM / (111_320.0 * Math.cos(Math.toRadians(PlanningFixtures.HEAD_LAT)));
            Point point = RoutePlannerHarness.point(lng, lat);
            spot.setLocation(point);
            spot.setEntryPoint(point);
            spot.setExitPoint(point);
            return spot;
        }
    }
}

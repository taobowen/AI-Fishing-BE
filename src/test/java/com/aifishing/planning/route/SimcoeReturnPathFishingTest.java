package com.aifishing.planning.route;

import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.common.enums.WindWaveCapability;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.ranking.SpotScore;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.VisitPortal;
import com.aifishing.planning.spatial.ZoneVisitState;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SimcoeReturnPathFishingTest {

    private final RoutePlanner planner = RoutePlannerHarness.planner();

    @AfterEach
    void clear() {
        GenerateProfiler.clear();
    }

    @Test
    void returnPathSuccessorsStayRepresentableWithoutHardCodingAWinner() throws Exception {
        Fixture fixture = Fixture.build();
        GenerateProfiler.begin();
        RoutePlanner.RouteResult result = planner.plan(fixture.ranked, fixture.context);

        assertThat(result.stops()).isNotEmpty();
        assertThat(result.plannedReturnAt()).isNotNull();
        assertThat(result.plannedReturnAt().isAfter(TripClock.endAt(fixture.context))).isFalse();

        Map<UUID, Long> zoneEntries = result.stops().stream()
                .filter(stop -> stop.visitKind() != null && stop.visitKind().isZoneEntry())
                .collect(Collectors.groupingBy(PlannedStop::opportunityIdentity, Collectors.counting()));
        assertThat(zoneEntries.values()).allMatch(count -> count <= 2);

        Set<UUID> atomics = new HashSet<>();
        for (PlannedStop stop : result.stops()) {
            if (stop.targetKind() != TargetKind.ZONE) {
                UUID id = stop.opportunityIdentity();
                assertThat(atomics.add(id)).as("POINT/PATH planned twice: " + id).isTrue();
                assertThat(stop.visitKind()).isNotEqualTo(MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE);
            }
        }

        Set<UUID> membersSeen = new HashSet<>();
        for (PlannedStop stop : result.stops()) {
            if (stop.fishingPackage() == null) {
                continue;
            }
            for (UUID member : stop.fishingPackage().consumedMemberIds()) {
                assertThat(membersSeen.add(member)).as("member double-counted " + member).isTrue();
            }
        }

        List<PlannedStop> aStops = result.stops().stream()
                .filter(stop -> fixture.zoneA.equals(stop.opportunityIdentity()))
                .toList();
        if (aStops.size() >= 2) {
            assertThat(aStops.get(1).visitKind()).isEqualTo(MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE);
            Set<UUID> first = new LinkedHashSet<>(aStops.get(0).fishingPackage() == null
                    ? List.of() : aStops.get(0).fishingPackage().consumedMemberIds());
            Set<UUID> second = new LinkedHashSet<>(aStops.get(1).fishingPackage() == null
                    ? List.of() : aStops.get(1).fishingPackage().consumedMemberIds());
            assertThat(second).doesNotContainAnyElementsOf(first);
        }
        assertThat(aStops.stream().filter(stop -> stop.visitKind() == MacroVisitKind.EXTEND_CURRENT_ZONE).count())
                .isLessThanOrEqualTo(1);

        if (!result.stops().isEmpty()
                && fixture.zoneA.equals(result.stops().get(0).opportunityIdentity())
                && result.stops().get(0).visitKind() == MacroVisitKind.EXTEND_CURRENT_ZONE) {
            assertThat(result.stops().get(0).stayMinutes()).isGreaterThan(90);
        }

        double usedKm = 0;
        for (PlannedStop stop : result.stops()) {
            usedKm += appliedKm(stop.fromPrevious()) + stop.localDistanceKm();
        }
        usedKm += appliedKm(result.returnTravel());
        assertThat(usedKm).isLessThanOrEqualTo(16.01);

        Path report = Path.of("docs", "reports", "simcoe-route-utilization.md");
        String existing = Files.exists(report) ? Files.readString(report) : "";
        StringBuilder winner = new StringBuilder();
        winner.append("\n\n## Return-path fixture winning route (A/B/C/D/E)\n\n");
        winner.append(String.format(Locale.ROOT,
                "Do not treat this sequence as a required winner. Macro stops=%d, fishing=%d, travel=%.1f, unused=%d, utility=%.4f, return=%s.\n\n",
                result.stops().size(),
                result.totalFishingMinutes(),
                result.totalTravelMinutes(),
                (int) Duration.between(TripClock.startAt(fixture.context), TripClock.endAt(fixture.context)).toMinutes()
                        - result.totalFishingMinutes() - (int) Math.round(result.totalTravelMinutes()),
                result.routeUtility(),
                result.plannedReturnAt() == null ? "null" : result.plannedReturnAt().atZone(TripClock.zoneId(fixture.context)).toLocalTime()));
        Set<UUID> leftover = new LinkedHashSet<>(List.of(fixture.zoneA, fixture.zoneB, fixture.zoneC, fixture.pathD, fixture.pointE));
        for (int i = 0; i < result.stops().size(); i++) {
            PlannedStop stop = result.stops().get(i);
            leftover.remove(stop.opportunityIdentity());
            List<UUID> remaining = stop.targetKind() == TargetKind.ZONE
                    ? ZoneVisitState.empty().addEntry(stop.fishingPackage(), stop.arrivalAt()).remainingMembers(stop.candidate().spot())
                    .stream().map(ZoneVisitState::memberId).toList()
                    : List.of();
            winner.append(String.format(Locale.ROOT, """
                    ### Macro %d %s
                    - identity: `%s`
                    - kind: %s
                    - dwell %d (fish %d, local transit %d)
                    - members consumed: %s
                    - members remaining after this stop's package: %s
                    - marginal package utility: %.4f
                    - visitIncrement: %.4f
                    - macro travel: %.1f min
                    - local km: %.3f
                    """,
                    i + 1,
                    stop.visitKind(),
                    stop.opportunityIdentity(),
                    stop.targetKind(),
                    stop.stayMinutes(),
                    stop.plannedFishingMinutes(),
                    stop.plannedInternalTransitMinutes(),
                    stop.fishingPackage() == null ? List.of() : stop.fishingPackage().consumedMemberIds(),
                    remaining,
                    stop.fishingPackage() == null ? 0 : stop.fishingPackage().marginalUtility(),
                    stop.visitIncrement(),
                    stop.fromPrevious().minutes(),
                    stop.localDistanceKm()));
        }
        winner.append("\nLeftover identities not on the winner: ").append(leftover)
                .append(". Not chosen because Beam picked a higher totalValue feasible route; leftover options remain representable (EXTEND/REVISIT/unvisited) rather than coverage-exhausted.\n");
        winner.append("\nFuture enhancement: Opportunity Revisit Cooldown is **not** implemented. `maxZoneEntries=2` is the temporary guard.\n");
        int marker = existing.indexOf("## Return-path fixture winning route");
        String base = marker >= 0 ? existing.substring(0, marker).stripTrailing() : existing.stripTrailing();
        Files.createDirectories(report.getParent());
        Files.writeString(report, (base.isEmpty() ? "# Simcoe route utilization diagnostic\n" : base) + winner);
    }

    private static double appliedKm(TravelEstimate travel) {
        if (travel == null || travel.unknownTravel()) {
            return 0;
        }
        return travel.distanceM() * travel.appliedDetourFactor() / 1000.0;
    }

    private record Fixture(
            List<RankedCandidate> ranked,
            PlanningContext context,
            UUID zoneA,
            UUID zoneB,
            UUID zoneC,
            UUID pathD,
            UUID pointE
    ) {
        static Fixture build() {
            List<RankedCandidate> ranked = new ArrayList<>();
            UUID a = UUID.nameUUIDFromBytes("return-A".getBytes());
            UUID b = UUID.nameUUIDFromBytes("return-B".getBytes());
            UUID c = UUID.nameUUIDFromBytes("return-C".getBytes());
            UUID d = UUID.nameUUIDFromBytes("return-D".getBytes());
            UUID e = UUID.nameUUIDFromBytes("return-E".getBytes());
            ranked.add(zone(a, 80, 40, 0.72, 6, new FeatureType[]{
                    FeatureType.DROP_OFF, FeatureType.HUMP, FeatureType.POINT,
                    FeatureType.FLAT, FeatureType.HUMP, FeatureType.DROP_OFF
            }));
            ranked.add(zone(b, 2800, 200, 0.96, 4, new FeatureType[]{
                    FeatureType.BASIN, FeatureType.BASIN, FeatureType.BASIN, FeatureType.BASIN
            }));
            ranked.add(zone(c, 1400, 80, 0.68, 4, new FeatureType[]{
                    FeatureType.HUMP, FeatureType.FLAT, FeatureType.DROP_OFF, FeatureType.HUMP
            }));
            ranked.add(path(d, 900, 0, 0.62));
            ranked.add(point(e, 120, -40, 0.55));

            PlanningProperties properties = new PlanningProperties();
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
                    new LakePlanningGeometry(water, List.of()),
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
                    context.spatialSnapshot()
            );
            return new Fixture(ranked, context, a, b, c, d, e);
        }

        private static RankedCandidate zone(UUID zoneId, double eastM, double northM, double weight, int n, FeatureType[] types) {
            List<CandidateSpot> members = new ArrayList<>();
            Point representative = null;
            for (int i = 0; i < n; i++) {
                CandidateSpot member = atomic(zoneId + "-m" + i, eastM + i * 40, northM, types[i % types.length], weight, TargetKind.POINT);
                members.add(member);
                if (representative == null) {
                    representative = member.getLocation();
                }
            }
            CandidateSpot spot = new CandidateSpot();
            spot.setFeatureId(zoneId);
            spot.setZoneId(zoneId);
            spot.setTargetKind(TargetKind.ZONE);
            spot.setType(FeatureType.FLAT);
            spot.setLocation(representative);
            spot.setEntryPoint(representative);
            spot.setExitPoint(representative);
            spot.setStrategyWeight(weight);
            spot.setFeatureConfidence(0.8);
            spot.setZoneMembers(members);
            spot.setCoverageIds(List.of(zoneId));
            spot.setPortals(List.of(new VisitPortal("p0", representative), new VisitPortal("p1", representative)));
            spot.setWindowFrom(LocalTime.of(7, 0));
            spot.setWindowTo(LocalTime.of(15, 0));
            return ranked(spot, weight);
        }

        private static RankedCandidate path(UUID id, double eastM, double northM, double weight) {
            Point a = at(eastM, northM);
            Point b = at(eastM + 250, northM);
            LineString line = RoutePlannerHarness.FACTORY.createLineString(new Coordinate[]{
                    a.getCoordinate(), b.getCoordinate()
            });
            CandidateSpot spot = atomic(id.toString(), eastM, northM, FeatureType.ISLAND_EDGE, weight, TargetKind.PATH);
            spot.setFeatureId(id);
            spot.setFishingTargetId(id);
            spot.setTargetGeometry(line);
            spot.setSelectedFishingPath(line);
            spot.setEntryPoint(a);
            spot.setExitPoint(b);
            spot.setPortals(List.of(new VisitPortal("a", a), new VisitPortal("b", b)));
            spot.setCoverageIds(List.of(id));
            return ranked(spot, weight);
        }

        private static RankedCandidate point(UUID id, double eastM, double northM, double weight) {
            CandidateSpot spot = atomic(id.toString(), eastM, northM, FeatureType.POINT, weight, TargetKind.POINT);
            spot.setFeatureId(id);
            spot.setFishingTargetId(id);
            spot.setCoverageIds(List.of(id));
            return ranked(spot, weight);
        }

        private static CandidateSpot atomic(
                String key,
                double eastM,
                double northM,
                FeatureType type,
                double weight,
                TargetKind kind
        ) {
            UUID id = key.length() == 36 ? UUID.fromString(key) : UUID.nameUUIDFromBytes(key.getBytes());
            Point location = at(eastM, northM);
            CandidateSpot spot = new CandidateSpot();
            spot.setFeatureId(id);
            spot.setFishingTargetId(id);
            spot.setType(type);
            spot.setTargetKind(kind);
            spot.setLocation(location);
            spot.setEntryPoint(location);
            spot.setExitPoint(location);
            spot.setTargetGeometry(location);
            spot.setStrategyWeight(weight);
            spot.setFeatureConfidence(0.8);
            spot.setWindowFrom(LocalTime.of(7, 0));
            spot.setWindowTo(LocalTime.of(15, 0));
            return spot;
        }

        private static Point at(double eastM, double northM) {
            return RoutePlannerHarness.point(
                    PlanningFixtures.HEAD_LNG + RoutePlannerHarness.metersToLng(eastM, PlanningFixtures.HEAD_LAT),
                    PlanningFixtures.HEAD_LAT + RoutePlannerHarness.metersToLat(northM));
        }

        private static RankedCandidate ranked(CandidateSpot spot, double weight) {
            ScoreBreakdown breakdown = new ScoreBreakdown(weight, 1, 0.8, 1, 0.5, 0.5, 0.5, 0.5, 0.0);
            return new RankedCandidate(spot, new SpotScore(weight, breakdown), null);
        }
    }
}

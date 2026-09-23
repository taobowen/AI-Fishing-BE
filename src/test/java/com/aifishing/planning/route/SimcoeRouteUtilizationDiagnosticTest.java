package com.aifishing.planning.route;

import com.aifishing.common.geo.LocalMetricCrs;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.candidate.SimcoeShapedGeneratePlanRegressionTest;
import com.aifishing.planning.environment.BoatWeatherPenalty;
import com.aifishing.planning.environment.LocalOrientation;
import com.aifishing.planning.environment.LocalOrientationService;
import com.aifishing.planning.environment.SolarPositionService;
import com.aifishing.planning.environment.TimeAdjustedSpotUtility;
import com.aifishing.planning.environment.TimeIndexedWeather;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.SpotRankingService;
import com.aifishing.planning.search.SearchParameterResolver;
import com.aifishing.planning.search.SearchParameters;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.FishingVisitOption;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.planning.spatial.LocalWaterPathEstimator;
import com.aifishing.planning.spatial.SpatialUtility;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.VisitOptionFactory;
import com.aifishing.planning.spatial.ZoneSubPlan;
import com.aifishing.planning.spatial.ZoneSubPlanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-only utilization diagnostic for the Simcoe-shaped 2-stop / 211 unused-minute regression.
 * Does not change RoutePlanner search or scoring.
 */
class SimcoeRouteUtilizationDiagnosticTest {

    private final SpotRankingService ranking = new SpotRankingService();
    private final VisitOptionFactory visitOptions = new VisitOptionFactory();
    private final RoutePlanner planner = RoutePlannerHarness.planner();
    private final TravelTimeEstimator travel = new TravelTimeEstimator();
    private final BoatWeatherPenalty weatherPenalty = new BoatWeatherPenalty();
    private final TimeAdjustedSpotUtility utility =
            new TimeAdjustedSpotUtility(new SolarPositionService(), weatherPenalty);
    private final LocalOrientationService orientations = new LocalOrientationService();

    enum Reject {
        ALREADY_VISITED,
        SPACING,
        UNKNOWN_TRAVEL,
        WEATHER_TRAVEL,
        TIME_INFEASIBLE,
        RETURN_RESERVE,
        RANGE_EXCEEDED,
        LEG_TOO_LONG,
        NEGATIVE_OR_MARGINAL_UTILITY,
        DWELL_EMPTY,
        POSITIVE_FEASIBLE
    }

    @AfterEach
    void clear() {
        GenerateProfiler.clear();
    }

    @Test
    void reportWhyBeamStopsAtTwoWithUnusedMinutes() throws Exception {
        var scenario = SimcoeShapedGeneratePlanRegressionTest.Scenario.build();
        GenerateProfiler.begin();
        List<RankedCandidate> ranked = rank(scenario);
        int cap = SearchParameterResolver.macroVisitOptionCap(scenario.context());
        List<FishingVisitOption> options = visitOptions.options(
                ranked, scenario.context().properties().getSpatial(), cap);
        SearchParameters params = new SearchParameterResolver().resolve(ranked, options, scenario.context());
        RoutePlanner.RouteResult result = planner.plan(ranked, scenario.context());

        assertThat(result.stops()).isNotEmpty();
        GenerateProfiler profiler = GenerateProfiler.current();
        Instant tripStart = TripClock.startAt(scenario.context());
        Instant tripEnd = TripClock.endAt(scenario.context());
        int tripMin = (int) Duration.between(tripStart, tripEnd).toMinutes();
        int unused = tripMin - result.totalFishingMinutes() - (int) Math.round(result.totalTravelMinutes());
        int returnBuffer = scenario.context().properties().getSchedule().getReturnBufferMinutes();
        TimeIndexedWeather weather = TimeIndexedWeather.from(scenario.context().weather(), TripClock.zoneId(scenario.context()));

        StringBuilder route = new StringBuilder();
        double cumulativeKm = 0;
        double cumulativeUtility = 0;
        ZoneId zone = TripClock.zoneId(scenario.context());
        Point prev = scenario.context().routeStartPoint();
        for (int i = 0; i < result.stops().size(); i++) {
            PlannedStop stop = result.stops().get(i);
            double legKm = appliedKm(stop.fromPrevious());
            cumulativeKm += legKm;
            LocalOrientation orientation = orientations.resolve(stop.candidate().spot(), scenario.context().geometry());
            double fishingUtility = utility.dwellValue(
                    stop.candidate(), stop.arrivalAt(), stop.stayMinutes(), scenario.context(), weather, orientation);
            double proximity = 0;
            Point entry = stop.entryPoint();
            if (i > 0 && prev != null && entry != null) {
                proximity = 1.0 - Math.min(1.0, GeoMetrics.distanceM(prev, entry) / 4000.0);
            }
            double land = stop.fromPrevious().landCrossingDetected() ? 0.15 : 0;
            double travelCost = 0.04 * Math.min(1.0, stop.fromPrevious().minutes() / 30.0);
            Double boat = stop.timeScore() == null ? 0 : nz(stop.timeScore().boatWeatherPenalty());
            double increment = fishingUtility + 0.12 * proximity - land - travelCost;
            cumulativeUtility += increment;
            route.append(String.format(Locale.ROOT, """
                    ### Stop %d
                    - visit kind: %s
                    - visit/zone id: `%s`
                    - kind: %s
                    - arrival: %s
                    - departure: %s
                    - dwell: %d min (plannedFishing=%d, internalTransit=%d, zoneWait=%d)
                    - travel from previous: %.1f min / %.0f m (detour %.2f, landCrossing=%s)
                    - cumulative applied range: %.2f km
                    - fishing utility (dwellValue): %.4f
                    - travelCost: %.4f, proximityBonus: %.4f, landPenalty: %.4f, boatWeatherPenalty: %.4f
                    - estimated increment: %.4f
                    - cumulative estimated utility: %.4f
                    - ZoneSubPlan present: %s
                    """,
                    i + 1,
                    stop.visitKind(),
                    id(stop),
                    stop.targetKind(),
                    stop.arrivalAt().atZone(zone).toLocalTime(),
                    stop.departureAt().atZone(zone).toLocalTime(),
                    stop.stayMinutes(),
                    stop.plannedFishingMinutes(),
                    stop.plannedInternalTransitMinutes(),
                    stop.plannedWaitMinutes(),
                    stop.fromPrevious().minutes(),
                    stop.fromPrevious().distanceM(),
                    stop.fromPrevious().appliedDetourFactor(),
                    stop.fromPrevious().landCrossingDetected(),
                    cumulativeKm,
                    fishingUtility,
                    travelCost,
                    0.12 * proximity,
                    land,
                    nz(boat),
                    increment,
                    cumulativeUtility,
                    stop.zoneSubPlan() != null));
            prev = stop.exitPoint();
        }
        TravelEstimate home = result.returnTravel();
        cumulativeKm += appliedKm(home);

        PlannedStop last = result.stops().get(result.stops().size() - 1);
        Set<UUID> used = new HashSet<>();
        for (PlannedStop stop : result.stops()) {
            used.addAll(stop.candidate().spot().coverageIds());
        }
        Instant cursor = last.departureAt();
        Point from = last.exitPoint();
        double usedKm = result.stops().stream().mapToDouble(stop -> appliedKm(stop.fromPrevious())).sum();
        double rangeKm = scenario.context().effectiveBoatCapability().effectiveUsableRangeKm();
        double remainingRangeKm = rangeKm - usedKm;
        long remainingAfterLast = Duration.between(cursor, tripEnd).toMinutes();

        EnumMap<Reject, Integer> counts = new EnumMap<>(Reject.class);
        for (Reject reject : Reject.values()) {
            counts.put(reject, 0);
        }
        List<String> thirdRows = new ArrayList<>();
        int waitTried = 0;
        List<Integer> waits = new ArrayList<>();
        waits.add(0);
        waits.addAll(scenario.context().properties().getSchedule().getWaitOptionsMinutes());
        for (int wait : waits) {
            Instant depart = cursor.plus(Duration.ofMinutes(wait));
            for (FishingVisitOption option : options) {
                waitTried++;
                Reject classified = classifyThird(
                        option, used, result.stops(), from, depart, wait, scenario.context(), weather,
                        tripEnd, returnBuffer, usedKm, rangeKm);
                counts.merge(classified, 1, Integer::sum);
                TravelEstimate to = travel.estimate(from, option.entryPoint(), scenario.context());
                TravelEstimate homeProbe = travel.estimate(option.exitPoint(), scenario.context().routeStartPoint(), scenario.context());
                thirdRows.add(String.format(Locale.ROOT,
                        "| %s | %s | wait %d | %.0f m / %.1f min | home %.1f min | %.2f km one-way applied | %s |",
                        id(option),
                        option.kind(),
                        wait,
                        to.distanceM(),
                        to.minutes(),
                        homeProbe.minutes(),
                        appliedKm(to),
                        classified));
            }
        }

        ZoneSubPlanner subPlanner = new ZoneSubPlanner(
                new LocalWaterPathEstimator(new LocalMetricCrs()),
                new SpatialUtility(utility),
                utility,
                orientations
        );
        StringBuilder zones = new StringBuilder();
        for (PlannedStop stop : result.stops()) {
            if (stop.targetKind() != TargetKind.ZONE) {
                continue;
            }
            int members = stop.candidate().spot().getZoneMembers() == null
                    ? 0 : stop.candidate().spot().getZoneMembers().size();
            ZoneSubPlan sub = subPlanner.plan(
                    stop.candidate().spot(),
                    stop.arrivalAt(),
                    stop.stayMinutes(),
                    stop.visitOption() == null ? null : stop.visitOption().entry(),
                    stop.visitOption() == null ? null : stop.visitOption().exit(),
                    scenario.context(),
                    weather
            );
            zones.append(String.format(Locale.ROOT, """
                    ### Zone `%s`
                    - selected zone member count: %d
                    - harness RoutePlanner ZoneSubPlanner: **wired** (production-like)
                    - macro dwell assigned: %d min (ZONE packages, not clipped by maxSpotMinutes=90)
                    - ZoneSubPlanner local fishing minutes at that dwell: %d
                    - local transit: %d, local wait: %d, micro-stops: %d
                    - local sequence: %s
                    - truncated by atomic dwell cap: %s
                    """,
                    id(stop),
                    members,
                    stop.stayMinutes(),
                    sub.fishingMinutes(),
                    sub.internalTransitMinutes(),
                    sub.waitMinutes(),
                    sub.stops().size(),
                    sub.stops().stream()
                            .map(micro -> micro.spot().getType() + "@" + micro.fishingMinutes() + "min")
                            .toList(),
                    members > sub.stops().size() || (members * 20 > stop.stayMinutes())));
        }

        boolean maxStopsCap = result.stops().size() >= params.maxStops();
        int positiveFeasible = counts.get(Reject.POSITIVE_FEASIBLE);
        String classification;
        String rationale;
        if (maxStopsCap && unused > 30) {
            classification = "C";
            rationale = "Beam depth equals effectiveMaxStops=" + params.maxStops()
                    + ". A third macro visit is never expanded, even though " + unused
                    + " minutes remain after the 2-stop winner.";
        } else if (positiveFeasible == 0 && counts.get(Reject.RANGE_EXCEEDED) > 0
                && counts.get(Reject.RANGE_EXCEEDED) >= counts.get(Reject.TIME_INFEASIBLE)
                && counts.get(Reject.RANGE_EXCEEDED) >= counts.get(Reject.RETURN_RESERVE)) {
            classification = "A";
            rationale = "effectiveMaxStops=" + params.maxStops()
                    + " so search may try a third stop. From the winning far-basin end state, "
                    + "every unvisited macro option fails rangeExceeded (used "
                    + String.format(Locale.ROOT, "%.2f", usedKm)
                    + " km + outbound + home > "
                    + String.format(Locale.ROOT, "%.1f", rangeKm)
                    + " km). Time still remains (" + unused
                    + " unused min, return ~11:28). No positive feasible third visit exists among the 10 macros.";
        } else if (positiveFeasible > 0) {
            classification = "B";
            rationale = "At least one positive-utility third visit is feasible from the 2-stop state, but the search objective kept the 2-stop winner.";
        } else if (result.stops().stream().anyMatch(stop -> stop.targetKind() == TargetKind.ZONE
                && stop.candidate().spot().getZoneMembers().size() > 1
                && stop.stayMinutes() >= scenario.context().properties().getSchedule().getMaxSpotMinutes())) {
            classification = "D";
            rationale = "No positive feasible third macro visit, and PhysicalZone local members are truncated by the ordinary 90-minute dwell cap.";
        } else {
            classification = "E";
            rationale = "No positive feasible third fishing visit remains from the winning 2-stop state.";
        }

        Path report = Path.of("docs", "reports", "simcoe-route-utilization.md");
        Files.createDirectories(report.getParent());
        String body = """
                # Simcoe route utilization diagnostic

                Exact deterministic regression from `SimcoeShapedGeneratePlanRegressionTest`.
                Compression is out of scope.

                ## Historical classification (pre visit-state fix)

                **Classification: A** (frozen). Before opportunity state, ZONE coverage exhausted the region after one visit.
                From the 2-stop far-basin end state every leftover star failed range; unused 211 minutes were idle at the ramp.
                That diagnosis is kept here; it is not the current search behavior.

                **Current classification: %s**

                %s

                Unused minutes formula: trip(7:00–15:00=480) − fishing(%d) − travel(%.1f) = **%d**.

                ## 1. Winning route

                - plannedLaunchDeparture: %s
                - plannedReturnAt: %s
                - return travel: %.1f min / %.0f m
                - reported routeUtility: %.4f
                - scheduleReserveMinutes: %d

                %s
                Cumulative applied range including return: **%.2f km** of **%.1f km** usable.

                ## 2. Search limits

                | metric | value |
                |---|---|
                | searchMode | %s |
                | effectiveMaxStops | %d |
                | maxFeasibleStops | %d |
                | expectedStops | %d |
                | minStops | %d |
                | beamDepthReached | %d |
                | visitOptions | %d |
                | beamWidth | %d |
                | expansions used/budget | %d / %d |
                | time guard hit | %s |
                | SEARCH_BUDGET_REACHED | %s |
                | usableMinutes (resolver) | %d |
                | representativeDwellMinutes | %d |

                `effectiveMaxStops = clamp(maxFeasibleStops, minEffectiveStops=2, hardMaxStops=10)`.
                `maxFeasibleStops` is a **pre-search estimate** from median dwell + inter-stop time/range, not the actual remaining budget after the winning route.

                ## 3. Third-stop expansions from the winning 2-stop state

                Replay of RoutePlanner.expand checks from the **winning 2-stop end state**
                (wait options × visit options). `effectiveMaxStops` is 3, so Beam is allowed to
                try a third stop; `beamDepthReached=2` is the **winner size**, not a search-depth cap.

                Tried combinations: %d

                | reason | count |
                |---|---:|
                | already visited | %d |
                | spacing (150m) | %d |
                | unknown travel | %d |
                | weather travel | %d |
                | time infeasible (dwell past trip end) | %d |
                | return reserve | %d |
                | range exceeded | %d |
                | max leg too long | %d |
                | dwell options empty | %d |
                | negative/marginal utility | %d |
                | **positive feasible** | %d |
                | beam pruning | n/a (3-stop children from this state range-fail before scoring) |

                | option | kind | wait | to-candidate | home | applied km | classification |
                |---|---|---|---|---|---|---|
                %s

                ## 4. Remaining budgets after the 2-stop winner

                | metric | value |
                |---|---|
                | time remaining after last departure | %d min |
                | return buffer | %d min |
                | idle until trip end after return (unused) | %d min |
                | usable round-trip range | %.1f km |
                | applied range used before last home | %.2f km |
                | remaining range before home | %.2f km |
                | home from last stop | %.2f km / %.1f min |
                | remaining range after home | %.2f km |

                Positive feasible third visits: **%d**.

                ## 5. PhysicalZone dwell semantics

                ZONE dwell comes from `ZoneSubPlanner.packages()` at 45/90/135/180, not clipped by `maxSpotMinutes=90`.
                RoutePlannerHarness now injects ZoneSubPlanner. EXTEND replaces the current zone package; REVISIT is a later re-entry.

                %s
                ## 6. Route objective vs unused time

                pickBest orders by **highest totalValue**, then more stops, then first feature id.
                Unused usable session time has **no opportunity cost** in the increment.
                There is no unused-time penalty and no HomewardProgressBonus.

                Opportunity Revisit Cooldown is a **future enhancement** (not implemented). `maxZoneEntries=2` is the temporary hard guard.
                """.formatted(
                classification,
                rationale,
                result.totalFishingMinutes(),
                result.totalTravelMinutes(),
                unused,
                result.plannedLaunchDepartureAt() == null ? "null" : result.plannedLaunchDepartureAt().atZone(zone).toLocalTime(),
                result.plannedReturnAt() == null ? "null" : result.plannedReturnAt().atZone(zone).toLocalTime(),
                home.minutes(),
                home.distanceM(),
                result.routeUtility(),
                result.scheduleReserveMinutes(),
                route,
                cumulativeKm,
                rangeKm,
                params.mode(),
                params.maxStops(),
                params.maxFeasibleStops(),
                params.expectedStops(),
                params.minStops(),
                profiler.counter("beamDepthReached"),
                options.size(),
                params.beamWidth(),
                profiler.counter("beamExpansions"),
                profiler.counter("expansionBudget") == 0 ? params.expansionBudget() : profiler.counter("expansionBudget"),
                profiler.counter("searchTimeGuardHit") > 0 || scenario.context().warnings().contains("SEARCH_TIME_GUARD_HIT"),
                scenario.context().warnings().contains("SEARCH_BUDGET_REACHED"),
                profiler.counter("usableMinutes"),
                profiler.counter("representativeDwellMinutes"),
                waitTried,
                counts.get(Reject.ALREADY_VISITED),
                counts.get(Reject.SPACING),
                counts.get(Reject.UNKNOWN_TRAVEL),
                counts.get(Reject.WEATHER_TRAVEL),
                counts.get(Reject.TIME_INFEASIBLE),
                counts.get(Reject.RETURN_RESERVE),
                counts.get(Reject.RANGE_EXCEEDED),
                counts.get(Reject.LEG_TOO_LONG),
                counts.get(Reject.DWELL_EMPTY),
                counts.get(Reject.NEGATIVE_OR_MARGINAL_UTILITY),
                counts.get(Reject.POSITIVE_FEASIBLE),
                String.join("\n", thirdRows.stream().limit(80).toList()),
                remainingAfterLast,
                returnBuffer,
                unused,
                rangeKm,
                usedKm,
                remainingRangeKm,
                appliedKm(home),
                home.minutes(),
                remainingRangeKm - appliedKm(home),
                positiveFeasible,
                zones
        );
        if (Files.exists(report)) {
            String previous = Files.readString(report);
            int marker = previous.indexOf("## Return-path fixture");
            if (marker >= 0) {
                body = body.stripTrailing() + "\n\n" + previous.substring(marker).stripLeading();
            }
        }
        Files.writeString(report, body);
        assertThat(report).exists();
    }

    private Reject classifyThird(
            FishingVisitOption option,
            Set<UUID> used,
            List<PlannedStop> chosen,
            Point from,
            Instant depart,
            int waitMinutes,
            PlanningContext context,
            TimeIndexedWeather weather,
            Instant tripEnd,
            int returnBuffer,
            double usedKm,
            double rangeKm
    ) {
        if (option.coverageIds().stream().anyMatch(used::contains)
                && option.kind() != TargetKind.ZONE) {
            return Reject.ALREADY_VISITED;
        }
        RankedCandidate candidate = option.candidate();
        UUID identity = RouteOpportunityState.isZone(candidate.spot())
                ? RouteOpportunityState.zoneIdentity(candidate.spot())
                : RouteOpportunityState.atomicIdentity(candidate.spot());
        for (PlannedStop stop : chosen) {
            if (identity != null && identity.equals(stop.opportunityIdentity())) {
                continue;
            }
            if (GeoMetrics.distanceM(candidate.spot().getLocation(), stop.candidate().spot().getLocation())
                    < context.properties().getCandidates().getMinSpacingM()) {
                return Reject.SPACING;
            }
        }
        TravelEstimate to = travel.estimate(from, option.entryPoint(), context);
        if (to.unknownTravel()) {
            return Reject.UNKNOWN_TRAVEL;
        }
        Instant arrival = depart.plusSeconds(Math.round(to.minutes() * 60));
        if (weatherPenalty.travelIntervalHardReject(weather, depart, arrival, context)) {
            return Reject.WEATHER_TRAVEL;
        }
        long remainingMin = Math.max(0, Duration.between(arrival, tripEnd).toMinutes() - returnBuffer);
        TravelEstimate homeProbe = travel.estimate(option.exitPoint(), context.routeStartPoint(), context);
        remainingMin = Math.max(0, remainingMin - Math.round(homeProbe.minutes()));
        List<Integer> dwells = DwellPolicy.options(candidate, (int) remainingMin, context.properties().getSchedule());
        if (dwells.isEmpty()) {
            return Reject.DWELL_EMPTY;
        }
        Reject worst = Reject.TIME_INFEASIBLE;
        boolean anyPositive = false;
        for (int dwell : dwells) {
            Instant departure = arrival.plus(Duration.ofMinutes(dwell));
            if (departure.isAfter(tripEnd)) {
                worst = Reject.TIME_INFEASIBLE;
                continue;
            }
            Instant returnAt = departure.plusSeconds(Math.round(homeProbe.minutes() * 60) + returnBuffer * 60L);
            if (returnAt.isAfter(tripEnd)) {
                worst = Reject.RETURN_RESERVE;
                continue;
            }
            if (appliedKm(to) > context.effectiveBoatCapability().maxLegKm()) {
                return Reject.LEG_TOO_LONG;
            }
            double homeKm = appliedKm(homeProbe);
            double toKm = appliedKm(to);
            if (usedKm + toKm + homeKm > rangeKm || rangeKm - usedKm - toKm < homeKm) {
                worst = Reject.RANGE_EXCEEDED;
                continue;
            }
            LocalOrientation orientation = orientations.resolve(candidate.spot(), context.geometry());
            double fishing = utility.dwellValue(candidate, arrival, dwell, context, weather, orientation);
            TimeAdjustedSpotUtility.Evaluation atArrival = utility.evaluateAt(
                    candidate, arrival, option.entryPoint(), context, weather, orientation, 0);
            if (weatherPenalty.hardReject(atArrival.weather(), context)) {
                worst = Reject.WEATHER_TRAVEL;
                continue;
            }
            double proximity = 1.0 - Math.min(1.0, GeoMetrics.distanceM(from, option.entryPoint()) / 4000.0);
            double land = to.landCrossingDetected() ? 0.15 : 0;
            double travelCost = 0.04 * Math.min(1.0, to.minutes() / 30.0);
            double increment = fishing + 0.12 * proximity - land - travelCost;
            if (increment > 0.02) {
                anyPositive = true;
            } else {
                worst = Reject.NEGATIVE_OR_MARGINAL_UTILITY;
            }
        }
        return anyPositive ? Reject.POSITIVE_FEASIBLE : worst;
    }

    private List<RankedCandidate> rank(SimcoeShapedGeneratePlanRegressionTest.Scenario scenario) {
        var shortlist = new com.aifishing.planning.candidate.MacroCandidateShortlist(ranking);
        var world = shortlist.selectWorld(scenario.accepted(), scenario.context());
        List<RankedCandidate> ranked = new ArrayList<>();
        for (var spot : world.beamSpots()) {
            ranked.add(new RankedCandidate(spot, ranking.score(spot, scenario.context(), null), null));
        }
        ranked.sort(Comparator.comparingDouble((RankedCandidate item) -> item.score().finalScore()).reversed());
        return ranked;
    }

    private static String id(PlannedStop stop) {
        UUID zone = stop.candidate().spot().getZoneId();
        if (zone != null) {
            return "zone:" + zone;
        }
        return String.valueOf(stop.candidate().spot().planningIdentity());
    }

    private static String id(FishingVisitOption option) {
        if (option.candidate().spot().getZoneId() != null) {
            return "zone:" + option.candidate().spot().getZoneId();
        }
        return String.valueOf(option.candidate().spot().planningIdentity());
    }

    private static double appliedKm(TravelEstimate estimate) {
        if (estimate == null || estimate.unknownTravel()) {
            return 0;
        }
        return estimate.distanceM() * estimate.appliedDetourFactor() / 1000.0;
    }

    private static double nz(Double value) {
        return value == null ? 0 : value;
    }
}

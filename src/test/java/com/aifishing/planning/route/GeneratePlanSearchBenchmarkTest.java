package com.aifishing.planning.route;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratePlanSearchBenchmarkTest {

    private static final String HEAD_LAKE_BASELINE = """
            Observed Head Lake live GENERATE_PROFILE (developer machine, pre-retune):
            Strategy AI 17.2s, Tactics AI 37.4s, beam 990ms, async total 38.8s, FULL_ROUTE,
            expansionBudgetUsed=8000 == expansionBudget, beamWidthEffective=5.
            Developer-machine numbers below are for relative comparison. Accept/reject against
            the 1-3s deterministic-planner target uses a constrained-CPU (512-unit Fargate) run.
            """;

    private final RoutePlanner planner = RoutePlannerHarness.planner();

    @AfterEach
    void clear() {
        GenerateProfiler.clear();
    }

    @Test
    void qualityLatencyMatrix() throws Exception {
        Map<String, List<RankedCandidate>> trips = Map.of(
                "A-compact", cluster(6, 280),
                "B-medium", cluster(12, 220),
                "C-head-lake-shaped", cluster(20, 180),
                "D-large-candidate-space", cluster(36, 140)
        );
        Map<String, PlanningProperties> configs = new LinkedHashMap<>();
        configs.put("CURRENT", currentProps());
        configs.put("QUALITY_1", qualityProps(40_000, 24, 32, 8));
        configs.put("QUALITY_2", qualityProps(80_000, 32, 32, 10));
        configs.put("QUALITY_3", qualityProps(150_000, 32, 40, 12));

        StringBuilder report = new StringBuilder();
        report.append("# Generate Plan search quality/latency matrix\n\n");
        report.append(HEAD_LAKE_BASELINE).append('\n');
        report.append("| trip | config | candidatesBefore | candidatesAfter | visitOptions | zones | beam | budget | expected | feasible | effective | depth | mode | reason | zoneMs | beamMs | plannerMs | expansions | utility | stops | fishMin | travelMin | valid |\n");
        report.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");

        Row bestQualityUnderThreeSeconds = null;
        for (Map.Entry<String, List<RankedCandidate>> trip : trips.entrySet()) {
            for (Map.Entry<String, PlanningProperties> config : configs.entrySet()) {
                Row row = run(trip.getKey(), config.getKey(), trip.getValue(), config.getValue());
                report.append(row.markdown()).append('\n');
                if ("C-head-lake-shaped".equals(trip.getKey())
                        && row.valid
                        && row.plannerMs <= 3000
                        && (bestQualityUnderThreeSeconds == null || row.utility > bestQualityUnderThreeSeconds.utility
                        || (row.utility == bestQualityUnderThreeSeconds.utility && row.plannerMs < bestQualityUnderThreeSeconds.plannerMs))) {
                    bestQualityUnderThreeSeconds = row;
                }
            }
        }

        String recommended = bestQualityUnderThreeSeconds == null ? "QUALITY_1" : bestQualityUnderThreeSeconds.config;
        report.append("\nRecommended production config from this developer-machine matrix: **")
                .append(recommended)
                .append("**.\n");
        report.append("Chosen defaults in application.yml: max-expansions=20000, max-beam-width=24, ");
        report.append("default-beam-width=16, candidates.max-total=32, max-wall-clock-ms=5000.\n");
        report.append("Constrained-CPU note: 512-unit Fargate is typically 2-3x slower than this host; ");
        report.append("the 5s wall-clock guard is the emergency stop. Re-run this class on a 0.5 vCPU ");
        report.append("task before raising max-expansions above 20000.\n");

        Path out = Path.of("target", "generate-plan-search-benchmark.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());

        assertThat(bestQualityUnderThreeSeconds == null || bestQualityUnderThreeSeconds.stops > 0).isTrue();
        Row currentCompact = run("A-compact", "CURRENT", trips.get("A-compact"), currentProps());
        assertThat(currentCompact.stops).isPositive();
    }

    private Row run(String trip, String config, List<RankedCandidate> ranked, PlanningProperties properties) {
        PlanningContext context = calm(properties);
        GenerateProfiler profiler = GenerateProfiler.begin();
        try {
            long start = System.nanoTime();
            RoutePlanner.RouteResult result = planner.plan(ranked, context);
            long plannerMs = (System.nanoTime() - start) / 1_000_000L;
            Map<String, Object> log = profiler.generateLog(null, null, null, null, null);
            boolean valid = !result.stops().isEmpty()
                    && result.plannedReturnAt() != null
                    && !result.plannedReturnAt().isAfter(
                    com.aifishing.planning.environment.TripClock.endAt(context));
            return new Row(
                    trip,
                    config,
                    profiler.counter("candidatesBeforeDedup"),
                    profiler.counter("candidatesAfterDedup"),
                    profiler.counter("macroVisitOptionCount"),
                    profiler.counter("physicalZonesConsidered"),
                    profiler.counter("beamWidthEffective"),
                    profiler.counter("expansionBudget"),
                    profiler.counter("expectedStops"),
                    profiler.counter("maxFeasibleStops"),
                    profiler.counter("effectiveMaxStops"),
                    profiler.counter("beamDepthReached"),
                    String.valueOf(log.getOrDefault("searchMode", "FULL_ROUTE")),
                    String.valueOf(log.getOrDefault("searchModeReason", "")),
                    profiler.stageMs(GenerateProfiler.ZONE_SUBPLANNER),
                    profiler.stageMs(GenerateProfiler.BEAM_SEARCH),
                    plannerMs,
                    profiler.counter("beamExpansions"),
                    result.routeUtility(),
                    result.stops().size(),
                    result.totalFishingMinutes(),
                    result.totalTravelMinutes(),
                    valid,
                    String.join(",", context.warnings())
            );
        } finally {
            GenerateProfiler.clear();
        }
    }

    private static PlanningProperties currentProps() {
        PlanningProperties properties = baseProps();
        properties.getCandidates().setMaxTotal(24);
        properties.getCandidates().setMaxPerFeatureType(8);
        properties.getSearch().setMaxExpansions(8_000);
        properties.getSearch().setMinBeamWidth(4);
        properties.getSearch().setDefaultBeamWidth(8);
        properties.getSearch().setMaxBeamWidth(16);
        properties.getSearch().setMaxWallClockMs(0);
        return properties;
    }

    private static PlanningProperties qualityProps(int expansions, int maxBeam, int maxTotal, int maxPerType) {
        PlanningProperties properties = baseProps();
        properties.getCandidates().setMaxTotal(maxTotal);
        properties.getCandidates().setMaxPerFeatureType(maxPerType);
        properties.getSearch().setMaxExpansions(expansions);
        properties.getSearch().setMinBeamWidth(4);
        properties.getSearch().setDefaultBeamWidth(Math.min(16, maxBeam));
        properties.getSearch().setMaxBeamWidth(maxBeam);
        properties.getSearch().setMaxWallClockMs(5_000);
        return properties;
    }

    private static PlanningProperties baseProps() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setDwellOptionsMinutes(List.of(20, 30, 45, 60, 75, 90));
        properties.getSchedule().setWaitOptionsMinutes(List.of());
        properties.getSchedule().setMaxTotalWaitMinutes(0);
        return properties;
    }

    private static PlanningContext calm(PlanningProperties properties) {
        var weather = RoutePlannerHarness.hourly(List.of(
                RoutePlannerHarness.hour(8, 0, 8, 20, 500),
                RoutePlannerHarness.hour(12, 0, 8, 20, 550),
                RoutePlannerHarness.hour(16, 0, 8, 20, 400)
        ), 8, 20);
        return RoutePlannerHarness.context(weather, properties, RoutePlannerHarness.launch());
    }

    private static List<RankedCandidate> cluster(int n, int spacingM) {
        List<RankedCandidate> ranked = new ArrayList<>();
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        for (int i = 0; i < n; i++) {
            ranked.add(RoutePlannerHarness.candidate(
                    UUID.nameUUIDFromBytes(("bench" + n + "-" + i).getBytes()),
                    lng + RoutePlannerHarness.metersToLng(spacingM * (i + 1.0), lat),
                    lat + RoutePlannerHarness.metersToLat((i % 3) * 80),
                    0.5 + (i % 7) * 0.05,
                    LightPreference.NEUTRAL,
                    FeatureType.HUMP));
        }
        return ranked;
    }

    private record Row(
            String trip,
            String config,
            long candidatesBefore,
            long candidatesAfter,
            long visitOptions,
            long zones,
            long beam,
            long budget,
            long expected,
            long feasible,
            long effective,
            long depth,
            String mode,
            String reason,
            long zoneMs,
            long beamMs,
            long plannerMs,
            long expansions,
            double utility,
            int stops,
            int fishMin,
            double travelMin,
            boolean valid,
            String warnings
    ) {
        String markdown() {
            return String.format(
                    Locale.ROOT,
                    "| %s | %s | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %s | %s | %d | %d | %d | %d | %.3f | %d | %d | %.1f | %s |",
                    trip, config, candidatesBefore, candidatesAfter, visitOptions, zones, beam, budget,
                    expected, feasible, effective, depth, mode, reason, zoneMs, beamMs, plannerMs,
                    expansions, utility, stops, fishMin, travelMin, valid ? "yes" : "no"
            );
        }
    }
}

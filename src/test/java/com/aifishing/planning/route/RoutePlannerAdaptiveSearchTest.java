package com.aifishing.planning.route;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.search.SearchMode;
import com.aifishing.planning.search.SearchParameterResolver;
import com.aifishing.planning.search.SearchParameters;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.planning.spatial.VisitOptionFactory;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePlannerAdaptiveSearchTest {

    private final RoutePlanner planner = RoutePlannerHarness.planner();
    private final SearchParameterResolver resolver = new SearchParameterResolver();
    private final VisitOptionFactory visitOptions = new VisitOptionFactory();

    @AfterEach
    void clearProfiler() {
        GenerateProfiler.clear();
    }

    @Test
    void identicalInputYieldsIdenticalRoute() {
        PlanningProperties properties = defaults();
        PlanningContext context = calm(properties);
        List<com.aifishing.planning.ranking.RankedCandidate> ranked = cluster(6, 220);
        RoutePlanner.RouteResult first = planner.plan(ranked, context);
        RoutePlanner.RouteResult second = planner.plan(ranked, calm(properties));
        assertThat(ids(second)).isEqualTo(ids(first));
        assertThat(first.stops()).isNotEmpty();
        assertThat(first.plannedReturnAt()).isBeforeOrEqualTo(
                com.aifishing.planning.environment.TripClock.endAt(context));
    }

    @Test
    void budgetCapStillReturnsFeasibleRoute() {
        PlanningProperties properties = defaults();
        properties.getSearch().setMaxExpansions(4);
        PlanningContext context = calm(properties);
        GenerateProfiler.begin();
        try {
            RoutePlanner.RouteResult result = planner.plan(cluster(10, 160), context);
            assertThat(GenerateProfiler.current().counter("expansionBudgetUsed"))
                    .isLessThanOrEqualTo(4);
            assertThat(context.warnings()).contains("SEARCH_BUDGET_REACHED");
            if (!result.stops().isEmpty()) {
                assertThat(result.plannedReturnAt()).isBeforeOrEqualTo(
                        com.aifishing.planning.environment.TripClock.endAt(context));
            }
        } finally {
            GenerateProfiler.clear();
        }
    }

    @Test
    void rollingHorizonOnlyWhenPredictedFullRouteCostDoesNotFit() {
        PlanningProperties full = defaults();
        full.getSearch().setMaxExpansions(80_000);
        PlanningContext fullCtx = calm(full);
        var ranked = cluster(8, 200);
        assertThat(resolver.resolve(ranked, visitOptions.options(ranked, full.getSpatial()), fullCtx).mode())
                .isEqualTo(SearchMode.FULL_ROUTE);

        PlanningProperties rolling = defaults();
        rolling.getSearch().setHardMaxStops(10);
        rolling.getSearch().setMinEffectiveStops(3);
        rolling.getSearch().setMinBeamWidth(4);
        rolling.getSearch().setDefaultBeamWidth(16);
        rolling.getSearch().setMaxExpansions(120);
        PlanningContext rollingCtx = calm(rolling);
        GenerateProfiler.begin();
        try {
            SearchParameters params = resolver.resolve(ranked, visitOptions.options(ranked, rolling.getSpatial()), rollingCtx);
            RoutePlanner.RouteResult result = planner.plan(ranked, rollingCtx);
            assertThat(params.mode()).isEqualTo(SearchMode.ROLLING_HORIZON);
            assertThat(result.stops()).isNotEmpty();
            assertThat(result.stops().size()).isLessThanOrEqualTo(params.maxStops());
        } finally {
            GenerateProfiler.clear();
        }
    }

    @Test
    void wallClockGuardReturnsACompleteFeasibleRoute() {
        PlanningProperties properties = defaults();
        properties.getSearch().setMaxExpansions(1_000_000);
        properties.getSearch().setMaxWallClockMs(1);
        PlanningContext context = calm(properties);
        GenerateProfiler.begin();
        try {
            RoutePlanner.RouteResult result = planner.plan(cluster(10, 160), context);
            assertThat(context.warnings()).contains("SEARCH_TIME_GUARD_HIT");
            if (!result.stops().isEmpty()) {
                assertThat(result.plannedReturnAt()).isBeforeOrEqualTo(
                        com.aifishing.planning.environment.TripClock.endAt(context));
                assertThat(result.routeUtility()).isGreaterThanOrEqualTo(0);
            }
        } finally {
            GenerateProfiler.clear();
        }
    }

    @Test
    void tailHeuristicDoesNotPreferUnreachableFutureOverRealizedFishing() {
        PlanningProperties properties = defaults();
        properties.getSchedule().setMaxWaypoints(8);
        properties.getSchedule().setDwellOptionsMinutes(List.of(45));
        properties.getSchedule().setMinSpotMinutes(45);
        properties.getSchedule().setMaxSpotMinutes(45);
        properties.getSearch().setFullRouteMaxStops(2);
        properties.getSearch().setFuturePotentialLambda(0.2);
        properties.getSearch().setMinEffectiveStops(2);
        properties.getSearch().setHardMaxStops(8);
        properties.getSearch().setMaxExpansions(200);
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        var nearPrize = RoutePlannerHarness.candidate(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa10"),
                lng,
                lat,
                0.92,
                LightPreference.NEUTRAL,
                FeatureType.HUMP);
        var farBait = RoutePlannerHarness.candidate(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb11"),
                lng + RoutePlannerHarness.metersToLng(3500, lat),
                lat + RoutePlannerHarness.metersToLat(3500),
                0.40,
                LightPreference.NEUTRAL,
                FeatureType.HUMP);
        List<com.aifishing.planning.ranking.RankedCandidate> glitter = new ArrayList<>();
        glitter.add(nearPrize);
        glitter.add(farBait);
        for (int i = 0; i < 6; i++) {
            glitter.add(RoutePlannerHarness.candidate(
                    UUID.nameUUIDFromBytes(("glitter" + i).getBytes()),
                    lng + RoutePlannerHarness.metersToLng(3600 + i * 30, lat),
                    lat + RoutePlannerHarness.metersToLat(3600),
                    0.95,
                    LightPreference.NEUTRAL,
                    FeatureType.HUMP));
        }
        GenerateProfiler.begin();
        try {
            RoutePlanner.RouteResult result = planner.plan(glitter, calm(properties));
            assertThat(result.stops()).isNotEmpty();
            assertThat(result.stops().get(0).candidate().spot().getFeatureId())
                    .isEqualTo(nearPrize.spot().getFeatureId());
        } finally {
            GenerateProfiler.clear();
        }
    }

    private static PlanningProperties defaults() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setDwellOptionsMinutes(List.of(20));
        properties.getSchedule().setMinSpotMinutes(20);
        properties.getSchedule().setMaxSpotMinutes(20);
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

    private static List<com.aifishing.planning.ranking.RankedCandidate> cluster(int n, int spacingM) {
        List<com.aifishing.planning.ranking.RankedCandidate> ranked = new ArrayList<>();
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG;
        for (int i = 0; i < n; i++) {
            ranked.add(RoutePlannerHarness.candidate(
                    UUID.nameUUIDFromBytes(("spot" + i).getBytes()),
                    lng + RoutePlannerHarness.metersToLng(spacingM * (i + 1), lat),
                    lat,
                    0.55 + i * 0.01,
                    LightPreference.NEUTRAL,
                    FeatureType.HUMP));
        }
        return ranked;
    }

    private static List<UUID> ids(RoutePlanner.RouteResult result) {
        return result.stops().stream()
                .map(stop -> stop.candidate().spot().getFeatureId())
                .toList();
    }
}

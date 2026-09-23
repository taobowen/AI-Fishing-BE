package com.aifishing.planning.search;

import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.common.enums.WindWaveCapability;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.planning.spatial.VisitOptionFactory;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SearchParameterResolverTest {

    private final SearchParameterResolver resolver = new SearchParameterResolver();
    private final VisitOptionFactory visitOptions = new VisitOptionFactory();

    @AfterEach
    void clearProfiler() {
        GenerateProfiler.clear();
    }

    @Test
    void hardMaxStopsIsTheSingleCeilingAndScheduleMaxWaypointsDoesNotCap() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setMaxWaypoints(1);
        properties.getSearch().setHardMaxStops(8);
        properties.getSearch().setMinEffectiveStops(2);
        properties.getSchedule().setDwellOptionsMinutes(List.of(20));
        PlanningContext context = calm(properties);
        context.trip().setFishingEndTime(java.time.LocalTime.of(16, 0));
        SearchParameters params = resolver.resolve(
                cluster(8),
                visitOptions.options(cluster(8), properties.getSpatial()),
                context
        );
        assertThat(params.maxStops()).isGreaterThan(1);
        assertThat(params.maxStops()).isLessThanOrEqualTo(8);
        assertThat(params.mode()).isEqualTo(SearchMode.FULL_ROUTE);
    }

    @Test
    void expectedStopsDoNotCapSearchDepth() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setDwellOptionsMinutes(List.of(20, 30, 45, 60, 75, 90));
        properties.getSchedule().setMinSpotMinutes(20);
        properties.getSearch().setHardMaxStops(10);
        properties.getSearch().setMinEffectiveStops(2);
        PlanningContext context = calm(properties);
        context.trip().setFishingEndTime(java.time.LocalTime.of(16, 0));
        SearchParameterResolver.StopEstimates estimates = resolver.estimateStops(
                cluster(8), context, properties.getSearch(), properties.getSchedule());
        SearchParameters params = resolver.resolve(
                cluster(8),
                visitOptions.options(cluster(8), properties.getSpatial()),
                context
        );
        assertThat(estimates.expectedStops()).isLessThan(params.maxStops());
        assertThat(params.maxFeasibleStops()).isGreaterThan(estimates.expectedStops());
        assertThat(params.maxStops()).isGreaterThanOrEqualTo(6);
        assertThat(params.maxStops()).isEqualTo(Math.min(estimates.maxFeasibleStops(), 10));
    }

    @Test
    void sparseGeometryUsesInterStopFloor() {
        PlanningProperties properties = new PlanningProperties();
        PlanningContext context = calm(properties);
        SearchParameterResolver.StopEstimates estimates = resolver.estimateStops(
                List.of(spot("only", 0)), context, properties.getSearch(), properties.getSchedule());
        assertThat(estimates.representativeInterStopMinutes())
                .isGreaterThanOrEqualTo(SearchParameterResolver.MIN_INTER_STOP_MINUTES);
        assertThat(estimates.optimisticInterStopMinutes())
                .isGreaterThanOrEqualTo(SearchParameterResolver.MIN_INTER_STOP_MINUTES);
    }

    @Test
    void predictedCostPrefersFullRouteWhenBudgetFits() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSearch().setMaxExpansions(80_000);
        properties.getSearch().setDefaultBeamWidth(16);
        properties.getSearch().setMaxBeamWidth(24);
        SearchParameterResolver.ModeDecision decision = SearchParameterResolver.decideMode(
                properties.getSearch(), 50, 6, 5, 16, 80_000);
        assertThat(decision.mode()).isEqualTo(SearchMode.FULL_ROUTE);
        assertThat(decision.reason()).isEqualTo(SearchModeReason.FULL_ROUTE);
    }

    @Test
    void rollingUsesPredictedCostReasonCodes() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSearch().setMinBeamWidth(4);
        properties.getSearch().setDefaultBeamWidth(16);
        properties.getSearch().setMaxExpansions(200);
        SearchParameterResolver.ModeDecision largeSpace = SearchParameterResolver.decideMode(
                properties.getSearch(), 90, 6, 5, 4, 200);
        assertThat(largeSpace.mode()).isEqualTo(SearchMode.ROLLING_HORIZON);
        assertThat(largeSpace.reason()).isEqualTo(SearchModeReason.ROLLING_CANDIDATE_SPACE_TOO_LARGE);

        SearchParameterResolver.ModeDecision tooHigh = SearchParameterResolver.decideMode(
                properties.getSearch(), 20, 6, 8, 4, 200);
        assertThat(tooHigh.mode()).isEqualTo(SearchMode.ROLLING_HORIZON);
        assertThat(tooHigh.reason()).isIn(
                SearchModeReason.ROLLING_EXPANSION_ESTIMATE_TOO_HIGH,
                SearchModeReason.ROLLING_FULL_ROUTE_BUDGET_EXCEEDED
        );
    }

    @Test
    void moreVisitOptionsNarrowsBeam() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSearch().setMaxExpansions(800);
        properties.getSearch().setMinBeamWidth(4);
        properties.getSearch().setMaxBeamWidth(16);
        int wide = resolver.effectiveBeamWidth(properties.getSearch(), properties.getSchedule(), 3, 2, 3, 800);
        int narrow = resolver.effectiveBeamWidth(properties.getSearch(), properties.getSchedule(), 40, 6, 5, 800);
        assertThat(wide).isGreaterThan(narrow);
        assertThat(narrow).isEqualTo(properties.getSearch().getMinBeamWidth());
    }

    @Test
    void budgetSizedFromExpectedStopsAllowsBeamToGrow() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSearch().setMaxExpansions(80_000);
        properties.getSearch().setMinBeamWidth(4);
        properties.getSearch().setDefaultBeamWidth(16);
        properties.getSearch().setMaxBeamWidth(32);
        int budget = SearchParameterResolver.sizedBudget(properties.getSearch(), 50, 6, 5);
        int beam = resolver.effectiveBeamWidth(properties.getSearch(), properties.getSchedule(), 50, 6, 5, budget);
        assertThat(budget).isGreaterThan(8_000);
        assertThat(beam).isGreaterThanOrEqualTo(16);
    }

    @Test
    void boatSpeedChangesTravelNotAHorsepowerTable() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setDwellOptionsMinutes(List.of(45));
        List<RankedCandidate> ranked = cluster(8);
        PlanningContext slow = calm(properties).withEffectiveBoat(boat(6));
        PlanningContext fast = calm(properties).withEffectiveBoat(boat(28));
        slow.trip().setFishingEndTime(java.time.LocalTime.of(11, 0));
        fast.trip().setFishingEndTime(java.time.LocalTime.of(11, 0));
        int slowStops = resolver.estimateMaxStops(ranked, slow, properties.getSearch(), properties.getSchedule());
        int fastStops = resolver.estimateMaxStops(ranked, fast, properties.getSearch(), properties.getSchedule());
        assertThat(fastStops).isGreaterThanOrEqualTo(slowStops);
        PlanningContext sameCruiseDifferentObject = calm(properties).withEffectiveBoat(boat(6));
        sameCruiseDifferentObject.trip().setFishingEndTime(java.time.LocalTime.of(11, 0));
        assertThat(resolver.estimateMaxStops(ranked, sameCruiseDifferentObject, properties.getSearch(), properties.getSchedule()))
                .isEqualTo(slowStops);
    }

    @Test
    void rangeEnforcedCapsExpectedStopsWhenReliable() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setDwellOptionsMinutes(List.of(20));
        List<RankedCandidate> ranked = cluster(8);
        PlanningContext unlimited = calm(properties).withEffectiveBoat(boat(20));
        unlimited.trip().setFishingEndTime(java.time.LocalTime.of(16, 0));
        PlanningContext shortRange = calm(properties).withEffectiveBoat(new EffectiveBoatCapability(
                20, 4.0, 4.0, 4.0, 4.0, true, 20.0, WindWaveCapability.MEDIUM, 0, Map.of()));
        shortRange.trip().setFishingEndTime(java.time.LocalTime.of(16, 0));
        int open = resolver.estimateStops(ranked, unlimited, properties.getSearch(), properties.getSchedule()).expectedStops();
        int limited = resolver.estimateStops(ranked, shortRange, properties.getSearch(), properties.getSchedule()).expectedStops();
        assertThat(limited).isLessThanOrEqualTo(open);
    }

    @Test
    void macroVisitOptionCapIsDerivedFromSearchBudgetNotLakeWideTopK() {
        PlanningProperties properties = new PlanningProperties();
        properties.getCandidates().setMaxMacroVisitOptions(10_000);
        properties.getSearch().setMaxExpansions(20_000);
        properties.getSearch().setDefaultBeamWidth(16);
        properties.getSearch().setMinBeamWidth(4);
        properties.getSearch().setHardMaxStops(4);
        properties.getSearch().setMinEffectiveStops(2);
        properties.getSchedule().setDwellOptionsMinutes(List.of(20, 30, 45, 60, 75, 90));
        int cap = SearchParameterResolver.macroVisitOptionCap(calm(properties));
        assertThat(cap).isLessThan(10_000);
        assertThat(cap).isGreaterThanOrEqualTo(8);
    }

    private static PlanningContext calm(PlanningProperties properties) {
        var weather = RoutePlannerHarness.hourly(List.of(
                RoutePlannerHarness.hour(8, 0, 8, 20, 500),
                RoutePlannerHarness.hour(16, 0, 8, 20, 400)
        ), 8, 20);
        return RoutePlannerHarness.context(weather, properties, RoutePlannerHarness.launch());
    }

    private static EffectiveBoatCapability boat(double cruise) {
        return new EffectiveBoatCapability(
                cruise, 40.0, 40.0, 40.0, 40.0, false, 20.0, WindWaveCapability.MEDIUM, 0, Map.of());
    }

    private static RankedCandidate spot(String name, int offsetM) {
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG + RoutePlannerHarness.metersToLng(offsetM, lat);
        return RoutePlannerHarness.candidate(
                UUID.nameUUIDFromBytes(name.getBytes()), lng, lat, 0.6, LightPreference.NEUTRAL, FeatureType.HUMP);
    }

    private static List<RankedCandidate> cluster(int n) {
        List<RankedCandidate> ranked = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ranked.add(spot("c" + i, 180 * (i + 1)));
        }
        return ranked;
    }
}

package com.aifishing.planning.route;

import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.common.enums.WindWaveCapability;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.environment.BoatWeatherPenalty;
import com.aifishing.planning.environment.LocalOrientation;
import com.aifishing.planning.environment.TimeIndexedWeather;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.search.SearchBudget;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.FishingVisitOption;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.VisitOptionFactory;
import com.aifishing.planning.spatial.VisitPortal;
import com.aifishing.planning.spatial.ZoneFishingPackage;
import com.aifishing.planning.spatial.ZoneSubPlan;
import com.aifishing.planning.spatial.ZoneSubPlanner;
import com.aifishing.planning.spatial.ZoneVisitState;
import com.aifishing.strategy.domain.LightPreference;
import com.aifishing.strategy.weather.WeatherContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePlannerBeamDiagnosticsTest {

    private final RoutePlanner planner = RoutePlannerHarness.planner();

    @AfterEach
    void clear() {
        GenerateProfiler.clear();
    }

    @Test
    void eachExpandRejectGateIncrementsOnlyThatReason() {
        PlanningProperties properties = tightSchedule();
        PlanningContext context = calm(properties);
        RankedCandidate near = candidate("near", 80, 0.85);
        RankedCandidate spaced = candidate("spaced", 40, 0.8);
        RankedCandidate far = candidate("far", 1800, 0.8);

        GenerateProfiler profiler = GenerateProfiler.begin();
        SearchBudget budget = new SearchBudget(4_000);
        List<RoutePlanner.BeamState> first = expand(planner, context, List.of(near), true, budget);
        assertThat(first).isNotEmpty();
        assertThat(profiler.beamSearch().rejectCount(BeamRejectReason.VISIT_KIND_NULL)).isZero();
        assertThat(profiler.beamSearch().successorsAccepted()).isPositive();

        int spacingBefore = profiler.beamSearch().rejectCount(BeamRejectReason.SPACING);
        int consideredBefore = profiler.beamSearch().candidatesConsidered();
        int acceptedBefore = profiler.beamSearch().successorsAccepted();
        expandState(planner, context, first.get(0), List.of(spaced), true, budget);
        assertThat(profiler.beamSearch().rejectCount(BeamRejectReason.SPACING)).isGreaterThan(spacingBefore);
        assertThat(profiler.beamSearch().successorsAccepted()).isEqualTo(acceptedBefore);
        assertThat(profiler.beamSearch().candidatesConsidered() - consideredBefore)
                .isEqualTo(profiler.beamSearch().rejectCount(BeamRejectReason.SPACING) - spacingBefore);

        GenerateProfiler.clear();
        GenerateProfiler.begin();
        expand(planner, context, List.of(near), false, new SearchBudget(1_000));
        assertThat(GenerateProfiler.current().beamSearch().rejectCount(BeamRejectReason.MAX_STOPS)).isPositive();
        assertThat(GenerateProfiler.current().beamSearch().successorsAccepted()).isZero();

        GenerateProfiler.clear();
        GenerateProfiler.begin();
        FishingVisitOption unknown = new FishingVisitOption(
                near, new VisitPortal("missing", null), new VisitPortal("missing", null), "unknown");
        expandOptions(planner, context, RoutePlanner.BeamState.initial(TripClock.startAt(context), context.routeStartPoint()),
                List.of(unknown), true, new SearchBudget(100));
        assertThat(GenerateProfiler.current().beamSearch().rejectCount(BeamRejectReason.UNKNOWN_TRAVEL)).isPositive();
        assertThat(GenerateProfiler.current().beamSearch().successorsAccepted()).isZero();

        GenerateProfiler.clear();
        GenerateProfiler.begin();
        PlanningProperties waitProps = tightSchedule();
        waitProps.getSchedule().setWaitOptionsMinutes(List.of(15));
        waitProps.getSchedule().setMaxTotalWaitMinutes(15);
        PlanningContext waitCtx = calm(waitProps);
        List<RoutePlanner.BeamState> parked = expand(planner, waitCtx, List.of(near), true, new SearchBudget(1_000));
        expandState(planner, waitCtx, parked.get(0), List.of(near), true, new SearchBudget(1_000));
        assertThat(GenerateProfiler.current().beamSearch().rejectCount(BeamRejectReason.EXTEND_WAIT_NOT_ALLOWED))
                .isPositive();

        GenerateProfiler.clear();
        GenerateProfiler.begin();
        List<RoutePlanner.BeamState> atNear = expand(planner, context, List.of(near), true, new SearchBudget(1_000));
        List<RoutePlanner.BeamState> atFar = expandState(
                planner, context, atNear.get(0), List.of(far), true, new SearchBudget(1_000));
        if (!atFar.isEmpty()) {
            int nullBefore = GenerateProfiler.current().beamSearch().rejectCount(BeamRejectReason.VISIT_KIND_NULL);
            expandState(planner, context, atFar.get(0), List.of(near), true, new SearchBudget(1_000));
            assertThat(GenerateProfiler.current().beamSearch().rejectCount(BeamRejectReason.VISIT_KIND_NULL))
                    .isGreaterThan(nullBefore);
        } else {
            throw new AssertionError("expected a successor to the far candidate so VISIT_KIND_NULL can be observed");
        }

        GenerateProfiler.clear();
        GenerateProfiler.begin();
        RankedCandidate weak = candidate("weak", 2500, 0.01);
        expand(planner, context, List.of(weak), true, new SearchBudget(1_000));
        BeamSearchDiagnostics incrementDiag = GenerateProfiler.current().beamSearch();
        if (incrementDiag.rejectCount(BeamRejectReason.NON_POSITIVE_INCREMENT) > 0) {
            assertThat(incrementDiag.nonPositiveIncrement().count())
                    .isEqualTo(incrementDiag.rejectCount(BeamRejectReason.NON_POSITIVE_INCREMENT));
            assertThat(incrementDiag.nonPositiveIncrement().max()).isLessThanOrEqualTo(0);
            assertThat(incrementDiag.toMap().get("nonPositiveIncrement")).isNotNull();
        }
    }

    @Test
    void weatherLegRangeAndTimeGatesUseDistinctReasons() {
        RankedCandidate spot = candidate("spot", 400, 0.9);

        GenerateProfiler.begin();
        RoutePlanner outboundPlanner = plannerWithWeather(new BoatWeatherPenalty() {
            @Override
            public boolean travelIntervalHardReject(
                    TimeIndexedWeather weather, Instant depart, Instant arrive, PlanningContext context) {
                return true;
            }
        });
        expand(outboundPlanner, calm(tightSchedule()), List.of(spot), true, new SearchBudget(200));
        assertThat(GenerateProfiler.current().beamSearch().rejectCount(BeamRejectReason.OUTBOUND_WEATHER_HARD_REJECT))
                .isPositive();
        assertThat(GenerateProfiler.current().beamSearch().successorsAccepted()).isZero();

        GenerateProfiler.clear();
        GenerateProfiler.begin();
        RoutePlanner arrivalPlanner = plannerWithWeather(new BoatWeatherPenalty() {
            @Override
            public boolean travelIntervalHardReject(
                    TimeIndexedWeather weather, Instant depart, Instant arrive, PlanningContext context) {
                return false;
            }

            @Override
            public boolean hardReject(com.aifishing.planning.environment.WeatherSample sample, PlanningContext context) {
                return true;
            }
        });
        expand(arrivalPlanner, calm(tightSchedule()), List.of(spot), true, new SearchBudget(200));
        assertThat(GenerateProfiler.current().beamSearch().rejectCount(BeamRejectReason.ARRIVAL_WEATHER_HARD_REJECT))
                .isPositive();

        GenerateProfiler.clear();
        GenerateProfiler.begin();
        RoutePlanner returnWeatherPlanner = plannerWithWeather(new BoatWeatherPenalty() {
            @Override
            public boolean travelIntervalHardReject(
                    TimeIndexedWeather weather, Instant depart, Instant arrive, PlanningContext context) {
                return depart.isAfter(TripClock.startAt(context).plusSeconds(60));
            }
        });
        expand(returnWeatherPlanner, calm(tightSchedule()), List.of(spot), true, new SearchBudget(200));
        assertThat(GenerateProfiler.current().beamSearch().rejectCount(BeamRejectReason.RETURN_WEATHER_HARD_REJECT))
                .isPositive();

        GenerateProfiler.clear();
        GenerateProfiler.begin();
        PlanningContext shortLeg = calm(tightSchedule()).withEffectiveBoat(new EffectiveBoatCapability(
                12, 40.0, 40.0, 40.0, 40.0, false, 0.05, WindWaveCapability.MEDIUM, 0, Map.of()));
        expand(planner, shortLeg, List.of(spot), true, new SearchBudget(200));
        assertThat(GenerateProfiler.current().beamSearch().rejectCount(BeamRejectReason.LEG_TOO_LONG)).isPositive();

        GenerateProfiler.clear();
        GenerateProfiler.begin();
        PlanningContext shortRange = calm(tightSchedule()).withEffectiveBoat(new EffectiveBoatCapability(
                12, 0.05, 0.05, 0.05, 0.05, true, 20.0, WindWaveCapability.MEDIUM, 0, Map.of()));
        expand(planner, shortRange, List.of(spot), true, new SearchBudget(200));
        assertThat(GenerateProfiler.current().beamSearch().rejectCount(BeamRejectReason.RANGE_EXCEEDED)).isPositive();

        GenerateProfiler.clear();
        GenerateProfiler.begin();
        expand(planner, calm(tightSchedule()), List.of(spot), true, new SearchBudget(0));
        assertThat(GenerateProfiler.current().beamSearch().rejectCount(BeamRejectReason.EXPANSION_BUDGET_EXCEEDED))
                .isPositive();
        assertThat(GenerateProfiler.current().beamSearch().successorsAccepted()).isZero();
    }

    @Test
    void noSuccessorsLastLayerSnapshotRemapsTripWindowWhenOnlyReturnReserve() {
        GenerateProfiler profiler = GenerateProfiler.begin();
        BeamSearchDiagnostics diag = profiler.beamSearch();
        diag.reject(BeamRejectReason.RETURN_RESERVE, 4);
        diag.reject(BeamRejectReason.DEPARTURE_AFTER_TRIP_END, 1);
        diag.terminate(BeamTerminationReason.NO_SUCCESSORS);
        assertThat(diag.termination()).isEqualTo(BeamTerminationReason.TRIP_WINDOW_EXHAUSTED);
        assertThat(diag.lastLayer().candidatesConsidered()).isEqualTo(5);
        assertThat(diag.lastLayer().successorsAccepted()).isZero();
        assertThat(diag.lastLayer().rejectCount(BeamRejectReason.RETURN_RESERVE)).isEqualTo(4);
        assertThat(diag.toMap().get("lastLayer")).isNotNull();
    }

    @Test
    void acceptedSuccessorsAreNotCountedAsRejectedAndWidthPruneIsSeparate() {
        PlanningProperties properties = tightSchedule();
        properties.getSearch().setMinBeamWidth(1);
        properties.getSearch().setDefaultBeamWidth(1);
        properties.getSearch().setMaxBeamWidth(1);
        properties.getSearch().setMaxExpansions(4_000);
        PlanningContext context = calm(properties);
        List<RankedCandidate> ranked = List.of(
                candidate("a", 200, 0.9),
                candidate("b", 700, 0.88),
                candidate("c", 1200, 0.86)
        );
        GenerateProfiler profiler = GenerateProfiler.begin();
        RoutePlanner.RouteResult result = planner.plan(ranked, context);
        assertThat(result.stops()).isNotEmpty();
        BeamSearchDiagnostics diag = profiler.beamSearch();
        assertThat(diag.successorsAccepted()).isPositive();
        assertThat(diag.rejectCount(BeamRejectReason.BEAM_WIDTH_PRUNED)
                + diag.rejectCount(BeamRejectReason.DOMINANCE_PRUNED)).isPositive();
        int accounted = diag.successorsAccepted();
        for (BeamRejectReason reason : BeamRejectReason.values()) {
            accounted += diag.rejectCount(reason);
        }
        assertThat(accounted).isEqualTo(diag.candidatesConsidered());
    }

    @Test
    void noSuccessorsReportsFinalLayerRejectCounts() {
        PlanningProperties properties = tightSchedule();
        properties.getSchedule().setMaxWaypoints(1);
        properties.getSchedule().setMinWaypoints(1);
        properties.getSearch().setMinEffectiveStops(1);
        PlanningContext context = calm(properties);
        RankedCandidate only = candidate("only", 120, 0.9);
        GenerateProfiler profiler = GenerateProfiler.begin();
        RoutePlanner.RouteResult result = planner.plan(List.of(only), context);
        assertThat(result.stops()).hasSize(1);
        BeamSearchDiagnostics diag = profiler.beamSearch();
        assertThat(diag.termination()).isIn(
                BeamTerminationReason.NO_SUCCESSORS,
                BeamTerminationReason.TRIP_WINDOW_EXHAUSTED,
                BeamTerminationReason.MAX_STOPS_REACHED,
                BeamTerminationReason.BEAM_COMPLETED_NORMALLY);
        BeamSearchDiagnostics.Layer layer = diag.lastLayer();
        assertThat(layer).isNotNull();
        assertThat(layer.afterStops()).isGreaterThanOrEqualTo(1);
        Map<String, Object> log = profiler.generateLog(null, null, null, "GIS", null);
        assertThat(log).containsKey("beamSearch");
        @SuppressWarnings("unchecked")
        Map<String, Object> beam = (Map<String, Object>) log.get("beamSearch");
        assertThat(beam).containsKeys("termination", "lastLayer", "visitOptions");
    }

    @Test
    void profilerOnVsOffDoesNotChangeRoute() {
        PlanningProperties properties = tightSchedule();
        List<RankedCandidate> ranked = List.of(
                candidate("a", 250, 0.8),
                candidate("b", 800, 0.75)
        );
        RoutePlanner.RouteResult off = planner.plan(ranked, calm(properties));
        GenerateProfiler.begin();
        try {
            RoutePlanner.RouteResult on = planner.plan(ranked, calm(properties));
            assertThat(ids(on)).isEqualTo(ids(off));
            assertThat(stays(on)).isEqualTo(stays(off));
            assertThat(on.routeUtility()).isEqualTo(off.routeUtility());
        } finally {
            GenerateProfiler.clear();
        }
    }

    @Test
    void revisitWithEmptyConsumedMembersCountsRevisitNoNewMembers() {
        RankedCandidate zone = zone("zone", 180, 0.9);
        RankedCandidate other = candidate("other", 900, 0.7);
        ZoneSubPlanner stub = new ZoneSubPlanner(
                new com.aifishing.planning.spatial.SpatialUtility(
                        new com.aifishing.planning.environment.TimeAdjustedSpotUtility(
                                new com.aifishing.planning.environment.SolarPositionService(),
                                new BoatWeatherPenalty())),
                new com.aifishing.planning.environment.TimeAdjustedSpotUtility(
                        new com.aifishing.planning.environment.SolarPositionService(),
                        new BoatWeatherPenalty()),
                new com.aifishing.planning.environment.LocalOrientationService(),
                new com.aifishing.planning.spatial.LakeNavRasterBuilder(new com.aifishing.common.geo.LocalMetricCrs()),
                new com.aifishing.planning.spatial.SnapshotWaterPathService(null)
        ) {
            @Override
            public List<ZoneFishingPackage> packages(
                    CandidateSpot candidate,
                    ZoneVisitState visitState,
                    Instant arrival,
                    com.aifishing.planning.spatial.VisitPortal entry,
                    com.aifishing.planning.spatial.VisitPortal exit,
                    PlanningContext context,
                    com.aifishing.planning.environment.TimeIndexedWeather weather,
                    int remainingMinutes
            ) {
                ZoneSubPlan plan = new ZoneSubPlan(List.of(), 20, 0, 0, 45, 1.0);
                return List.of(new ZoneFishingPackage(45, 20, 0, 0, 0, 1.0, List.of(), plan));
            }
        };
        RoutePlanner zoned = new RoutePlanner(
                new TravelTimeEstimator(),
                new com.aifishing.planning.environment.TimeAdjustedSpotUtility(
                        new com.aifishing.planning.environment.SolarPositionService(),
                        new BoatWeatherPenalty()),
                new com.aifishing.planning.environment.LocalOrientationService(),
                new BoatWeatherPenalty(),
                new VisitOptionFactory(),
                new com.aifishing.planning.spatial.SpatialUtility(
                        new com.aifishing.planning.environment.TimeAdjustedSpotUtility(
                                new com.aifishing.planning.environment.SolarPositionService(),
                                new BoatWeatherPenalty())),
                stub,
                new com.aifishing.planning.search.SearchParameterResolver()
        );
        PlanningContext context = calm(tightSchedule());
        GenerateProfiler.begin();
        SearchBudget budget = new SearchBudget(4_000);
        List<RoutePlanner.BeamState> atZone = expand(zoned, context, List.of(zone), true, budget);
        assertThat(atZone).isNotEmpty();
        List<RoutePlanner.BeamState> left = expandState(zoned, context, atZone.get(0), List.of(other), true, budget);
        assertThat(left).isNotEmpty();
        for (RoutePlanner.BeamState leftState : left) {
            expandState(zoned, context, leftState, List.of(zone), true, budget);
        }
        assertThat(GenerateProfiler.current().beamSearch().rejectCount(BeamRejectReason.REVISIT_NO_NEW_MEMBERS))
                .isPositive();
    }

    @Test
    void headLakeShapedFixtureExposesMacroAndTerminationDiagnostics() {
        PlanningProperties properties = tightSchedule();
        properties.getSearch().setMaxExpansions(8_000);
        properties.getSearch().setDefaultBeamWidth(8);
        properties.getSearch().setMaxBeamWidth(16);
        List<RankedCandidate> ranked = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ranked.add(candidate("hl-" + i, 180 * (i + 1), 0.5 + (i % 7) * 0.05));
        }
        GenerateProfiler profiler = GenerateProfiler.begin();
        RoutePlanner.RouteResult result = planner.plan(ranked, calm(properties));
        BeamSearchDiagnostics diag = profiler.beamSearch();
        assertThat(diag.visitOptionCount()).isPositive();
        assertThat(diag.selectedStandalonePoints()).isEqualTo(20);
        assertThat(diag.operationalZoneScopes()).isZero();
        assertThat(diag.termination()).isNotNull();
        assertThat(result.stops()).isNotEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> beam = (Map<String, Object>) profiler.generateLog(null, null, null, "GIS", null)
                .get("beamSearch");
        assertThat(beam.get("termination")).isNotNull();
        assertThat(beam.get("lastLayer")).isNotNull();
    }

    private static PlanningProperties tightSchedule() {
        PlanningProperties properties = new PlanningProperties();
        properties.getSchedule().setDwellOptionsMinutes(List.of(20));
        properties.getSchedule().setPointDwellMinutes(List.of(20));
        properties.getSchedule().setPathDwellMinutes(List.of(20));
        properties.getSchedule().setZonePackageMinutes(List.of(45));
        properties.getSchedule().setMinSpotMinutes(20);
        properties.getSchedule().setMaxSpotMinutes(20);
        properties.getSchedule().setWaitOptionsMinutes(List.of());
        properties.getSchedule().setMaxTotalWaitMinutes(0);
        properties.getSchedule().setMinWaypoints(1);
        properties.getCandidates().setMinSpacingM(150);
        return properties;
    }

    private static PlanningContext calm(PlanningProperties properties) {
        WeatherContext weather = RoutePlannerHarness.hourly(List.of(
                RoutePlannerHarness.hour(8, 0, 8, 20, 500),
                RoutePlannerHarness.hour(12, 0, 8, 20, 550),
                RoutePlannerHarness.hour(16, 0, 8, 20, 400)
        ), 8, 20);
        return RoutePlannerHarness.context(weather, properties, RoutePlannerHarness.launch());
    }

    private static RankedCandidate candidate(String name, int offsetM, double intrinsic) {
        double lat = PlanningFixtures.HEAD_LAT;
        double lng = PlanningFixtures.HEAD_LNG + RoutePlannerHarness.metersToLng(offsetM, lat);
        RankedCandidate ranked = RoutePlannerHarness.candidate(
                UUID.nameUUIDFromBytes(name.getBytes()), lng, lat, intrinsic, LightPreference.NEUTRAL, FeatureType.HUMP);
        ranked.spot().setFishingTargetId(ranked.spot().getFeatureId());
        ranked.spot().setTargetKind(TargetKind.POINT);
        ranked.spot().setEntryPoint(ranked.spot().getLocation());
        ranked.spot().setExitPoint(ranked.spot().getLocation());
        return ranked;
    }

    private static RankedCandidate zone(String name, int offsetM, double intrinsic) {
        RankedCandidate ranked = candidate(name, offsetM, intrinsic);
        CandidateSpot member = candidate(name + "-m", offsetM + 30, intrinsic).spot();
        ranked.spot().setTargetKind(TargetKind.ZONE);
        ranked.spot().setZoneId(ranked.spot().getFeatureId());
        ranked.spot().setZoneMembers(List.of(member));
        ranked.spot().setPortals(List.of(
                new VisitPortal("a", ranked.spot().getLocation()),
                new VisitPortal("b", ranked.spot().getLocation())));
        return ranked;
    }

    private static RoutePlanner plannerWithWeather(BoatWeatherPenalty weather) {
        var utility = new com.aifishing.planning.environment.TimeAdjustedSpotUtility(
                new com.aifishing.planning.environment.SolarPositionService(), weather);
        var orientation = new com.aifishing.planning.environment.LocalOrientationService();
        return new RoutePlanner(
                new TravelTimeEstimator(),
                utility,
                orientation,
                weather,
                new VisitOptionFactory(),
                new com.aifishing.planning.spatial.SpatialUtility(utility),
                new ZoneSubPlanner(
                        new com.aifishing.planning.spatial.SpatialUtility(utility),
                        utility,
                        orientation,
                        new com.aifishing.planning.spatial.LakeNavRasterBuilder(new com.aifishing.common.geo.LocalMetricCrs()),
                        new com.aifishing.planning.spatial.SnapshotWaterPathService(null)
                ),
                new com.aifishing.planning.search.SearchParameterResolver()
        );
    }

    private static List<RoutePlanner.BeamState> expand(
            RoutePlanner planner,
            PlanningContext context,
            List<RankedCandidate> ranked,
            boolean allowNew,
            SearchBudget budget
    ) {
        return expandState(
                planner,
                context,
                RoutePlanner.BeamState.initial(TripClock.startAt(context), context.routeStartPoint()),
                ranked,
                allowNew,
                budget
        );
    }

    private static List<RoutePlanner.BeamState> expandState(
            RoutePlanner planner,
            PlanningContext context,
            RoutePlanner.BeamState state,
            List<RankedCandidate> ranked,
            boolean allowNew,
            SearchBudget budget
    ) {
        List<FishingVisitOption> options = new VisitOptionFactory().options(ranked, context.properties().getSpatial());
        return expandOptions(planner, context, state, options, allowNew, budget);
    }

    private static List<RoutePlanner.BeamState> expandOptions(
            RoutePlanner planner,
            PlanningContext context,
            RoutePlanner.BeamState state,
            List<FishingVisitOption> options,
            boolean allowNew,
            SearchBudget budget
    ) {
        Map<UUID, LocalOrientation> orientations = new HashMap<>();
        for (FishingVisitOption option : options) {
            orientations.put(option.candidate().spot().planningIdentity(), LocalOrientation.unknown());
        }
        int returnBuffer = context.accessKnown() ? context.properties().getSchedule().getReturnBufferMinutes() : 0;
        return planner.expand(
                state,
                options,
                context,
                TimeIndexedWeather.from(context.weather(), TripClock.zoneId(context)),
                orientations,
                context.properties().getSchedule(),
                TripClock.endAt(context),
                returnBuffer,
                budget,
                0,
                allowNew
        );
    }

    private static List<UUID> ids(RoutePlanner.RouteResult result) {
        List<UUID> out = new ArrayList<>();
        for (PlannedStop stop : result.stops()) {
            out.add(stop.candidate().spot().getFeatureId());
        }
        return out;
    }

    private static List<Integer> stays(RoutePlanner.RouteResult result) {
        List<Integer> out = new ArrayList<>();
        for (PlannedStop stop : result.stops()) {
            out.add(stop.stayMinutes());
        }
        return out;
    }
}

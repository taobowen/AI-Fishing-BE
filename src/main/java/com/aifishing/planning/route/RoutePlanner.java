package com.aifishing.planning.route;

import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.environment.BoatWeatherPenalty;
import com.aifishing.planning.environment.GenerateOrientationCache;
import com.aifishing.planning.environment.LocalOrientation;
import com.aifishing.planning.environment.LocalOrientationService;
import com.aifishing.planning.environment.TimeAdjustedSpotUtility;
import com.aifishing.planning.environment.TimeIndexedWeather;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.environment.WhyThisTimeExplainer;
import com.aifishing.planning.ranking.ArrivalStrategyEvaluator;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.spatial.ZoneFishingPackage;
import com.aifishing.planning.spatial.ZoneVisitState;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.search.SearchBudget;
import com.aifishing.planning.search.SearchMode;
import com.aifishing.planning.search.SearchParameterResolver;
import com.aifishing.planning.search.SearchParameters;
import com.aifishing.planning.spatial.FishingVisitOption;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.planning.spatial.RequestSpatialCache;
import com.aifishing.planning.spatial.LakeNavRaster;
import com.aifishing.planning.spatial.SnapshotWaterPathService;
import com.aifishing.planning.spatial.SpatialUtility;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.VisitOptionFactory;
import com.aifishing.planning.spatial.ZoneSubPlan;
import com.aifishing.planning.spatial.ZoneSubPlanner;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RoutePlanner {

    private final TravelTimeEstimator travelTimeEstimator;
    private final TimeAdjustedSpotUtility timeAdjustedSpotUtility;
    private final LocalOrientationService orientationService;
    private final BoatWeatherPenalty boatWeatherPenalty;
    private final VisitOptionFactory visitOptionFactory;
    private final SpatialUtility spatialUtility;
    private final ZoneSubPlanner zoneSubPlanner;
    private final SearchParameterResolver searchParameterResolver;
    private final SnapshotWaterPathService waterPaths;

    public RoutePlanner(
            TravelTimeEstimator travelTimeEstimator,
            TimeAdjustedSpotUtility timeAdjustedSpotUtility,
            LocalOrientationService orientationService,
            BoatWeatherPenalty boatWeatherPenalty
    ) {
        this(
                travelTimeEstimator,
                timeAdjustedSpotUtility,
                orientationService,
                boatWeatherPenalty,
                new VisitOptionFactory(),
                new SpatialUtility(timeAdjustedSpotUtility),
                null,
                new SearchParameterResolver(),
                null
        );
    }

    @Autowired
    public RoutePlanner(
            TravelTimeEstimator travelTimeEstimator,
            TimeAdjustedSpotUtility timeAdjustedSpotUtility,
            LocalOrientationService orientationService,
            BoatWeatherPenalty boatWeatherPenalty,
            VisitOptionFactory visitOptionFactory,
            SpatialUtility spatialUtility,
            ZoneSubPlanner zoneSubPlanner,
            SearchParameterResolver searchParameterResolver,
            SnapshotWaterPathService waterPaths
    ) {
        this.travelTimeEstimator = travelTimeEstimator;
        this.timeAdjustedSpotUtility = timeAdjustedSpotUtility;
        this.orientationService = orientationService;
        this.boatWeatherPenalty = boatWeatherPenalty;
        this.visitOptionFactory = visitOptionFactory;
        this.spatialUtility = spatialUtility;
        this.zoneSubPlanner = zoneSubPlanner;
        this.searchParameterResolver = searchParameterResolver == null
                ? new SearchParameterResolver()
                : searchParameterResolver;
        this.waterPaths = waterPaths;
    }

    public RoutePlanner(
            TravelTimeEstimator travelTimeEstimator,
            TimeAdjustedSpotUtility timeAdjustedSpotUtility,
            LocalOrientationService orientationService,
            BoatWeatherPenalty boatWeatherPenalty,
            VisitOptionFactory visitOptionFactory,
            SpatialUtility spatialUtility,
            ZoneSubPlanner zoneSubPlanner,
            SearchParameterResolver searchParameterResolver
    ) {
        this(
                travelTimeEstimator,
                timeAdjustedSpotUtility,
                orientationService,
                boatWeatherPenalty,
                visitOptionFactory,
                spatialUtility,
                zoneSubPlanner,
                searchParameterResolver,
                null
        );
    }

    public RouteResult plan(List<RankedCandidate> ranked, PlanningContext context) {
        return plan(ranked, context, RoutePlanConstraints.none());
    }

    public RouteResult plan(
            List<RankedCandidate> ranked,
            PlanningContext context,
            RoutePlanConstraints constraints
    ) {
        RoutePlanConstraints effective = constraints == null ? RoutePlanConstraints.none() : constraints;
        DepthZeroDiagnostic.begin(ranked, context);
        BeamLayerProfile.begin(context.lake() == null ? null : String.valueOf(context.lake().getId()));
        PlanningProperties.Schedule schedule = context.properties().getSchedule();
        Instant tripStart = TripClock.startAt(context);
        Instant tripEnd = TripClock.endAt(context);
        int returnBuffer = context.accessKnown() ? schedule.getReturnBufferMinutes() : 0;
        TimeIndexedWeather weather = TimeIndexedWeather.from(context.weather(), TripClock.zoneId(context));
        if (boatWeatherPenalty.tripWindowUnsafe(context, weather, tripStart, tripEnd)) {
            if (!context.warnings().contains("WEATHER_UNSAFE")) {
                context.warnings().add("WEATHER_UNSAFE");
            }
            DepthZeroDiagnostic.flush();
            BeamLayerProfile.flush();
            return new RouteResult(List.of(), false, tripStart, tripStart, TravelEstimate.zero(), List.of(), 0, 0, 0, 0, 0);
        }
        Map<UUID, LocalOrientation> orientations = new HashMap<>();
        for (RankedCandidate candidate : ranked) {
            orientations.put(
                    candidate.spot().planningIdentity(),
                    resolveOrientation(candidate.spot(), context));
        }
        GenerateProfiler.current().start(GenerateProfiler.VISIT_OPTION_BUILD);
        int cap = SearchParameterResolver.macroVisitOptionCap(context);
        List<FishingVisitOption> visitOptions = visitOptionFactory.options(
                ranked, context.properties().getSpatial(), cap);
        GenerateProfiler.current().end(GenerateProfiler.VISIT_OPTION_BUILD);
        GenerateProfiler.current().set("macroVisitOptionCount", visitOptions.size());
        GenerateProfiler.current().compression().setMacroVisitOptions(visitOptions.size());
        GenerateProfiler.current().compression().setMacroVisitOptionCap(cap);
        beamDiag().recordSearchEntry(ranked, visitOptions, GenerateProfiler.current().compression());
        SearchParameters params = searchParameterResolver.resolve(ranked, visitOptions, context);
        SearchBudget budget = new SearchBudget(params.expansionBudget(), params.maxWallClockMs());
        recordSearch(params, budget);
        GenerateProfiler.current().start(GenerateProfiler.BEAM_SEARCH);
        BeamState best;
        try {
            best = params.mode() == SearchMode.ROLLING_HORIZON
                    ? planRolling(visitOptions, context, weather, orientations, schedule, tripStart, tripEnd, returnBuffer, params, budget, effective)
                    : planFullRoute(visitOptions, context, weather, orientations, schedule, tripStart, tripEnd, returnBuffer, params, budget, effective);
            if (budget.reached()) {
                if (!context.warnings().contains("SEARCH_BUDGET_REACHED")) {
                    context.warnings().add("SEARCH_BUDGET_REACHED");
                }
            }
            if (budget.timeGuardHit()) {
                if (!context.warnings().contains("SEARCH_TIME_GUARD_HIT")) {
                    context.warnings().add("SEARCH_TIME_GUARD_HIT");
                }
                GenerateProfiler.current().set("searchTimeGuardHit", 1);
            }
            if (!beamDiag().hasTermination()) {
                if (budget.reached() || budget.timeGuardHit()) {
                    beamDiag().terminate(BeamTerminationReason.EXPANSION_BUDGET_EXCEEDED);
                } else {
                    beamDiag().terminate(BeamTerminationReason.BEAM_COMPLETED_NORMALLY);
                }
            }
            GenerateProfiler.current().set("expansionBudgetUsed", budget.used());
            GenerateProfiler.current().set("beamDepthReached", best == null ? 0 : best.stops.size());
            if (best != null) {
                GenerateProfiler.current().set("routeUtilityMilli", Math.round(best.totalValue * 1000.0));
            }
        } finally {
            GenerateProfiler.current().end(GenerateProfiler.BEAM_SEARCH);
            DepthZeroDiagnostic.flush();
            BeamLayerProfile.flush();
        }
        if (best == null || best.stops.isEmpty()) {
            String failure = effective.hasRequired()
                    ? RoutePlanConstraints.REQUIRED_SET_INFEASIBLE
                    : null;
            return new RouteResult(
                    List.of(), false, tripStart, tripStart, TravelEstimate.zero(), List.of(), 0, 0, 0, 0, 0, failure);
        }
        if (effective.hasRequired()
                && !RoutePlanConstraints.coversRequired(best.stops, effective.requiredOpportunityIds())) {
            return new RouteResult(
                    List.of(),
                    false,
                    tripStart,
                    tripStart,
                    TravelEstimate.zero(),
                    List.of(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    RoutePlanConstraints.REQUIRED_SET_INFEASIBLE);
        }
        return toResult(best, context, weather, tripStart, tripEnd, returnBuffer);
    }

    private BeamState planFullRoute(
            List<FishingVisitOption> visitOptions,
            PlanningContext context,
            TimeIndexedWeather weather,
            Map<UUID, LocalOrientation> orientations,
            PlanningProperties.Schedule schedule,
            Instant tripStart,
            Instant tripEnd,
            int returnBuffer,
            SearchParameters params,
            SearchBudget budget,
            RoutePlanConstraints constraints
    ) {
        List<BeamState> beam = new ArrayList<>();
        beam.add(BeamState.initial(tripStart, context.accessKnown() ? context.routeStartPoint() : null));
        List<BeamState> completed = new ArrayList<>();
        int n = Math.max(1, visitOptions.size());
        int d = Math.max(1, schedule.getDwellOptionsMinutes().size());
        int minBeam = context.properties().getSearch().getMinBeamWidth();
        int maxBeam = context.properties().getSearch().getMaxBeamWidth();
        int extraExtend = Math.max(1, schedule.getZonePackageMinutes().size());
        boolean loopCompleted = true;
        int lastAfterStops = 0;
        long profile = BeamLayerProfile.open(BeamLayerProfile.Stage.OTHER);
        try {
        for (int depth = 0; depth < params.maxStops() + extraExtend; depth++) {
            if (!budget.remaining()) {
                loopCompleted = false;
                beamDiag().terminate(BeamTerminationReason.EXPANSION_BUDGET_EXCEEDED);
                break;
            }
            int remainingDepth = Math.max(1, params.maxStops() - depth);
            int width = SearchParameterResolver.adaptiveWidth(
                    params.beamWidth(), minBeam, maxBeam, n, d, remainingDepth, budget.remainingCount());
            int afterStops = 0;
            for (BeamState state : beam) {
                afterStops = Math.max(afterStops, state.stops.size());
            }
            lastAfterStops = afterStops;
            beamDiag().beginLayer(depth, afterStops);
            BeamLayerProfile.beginLayer(0, depth, beam.size(), width);
            List<BeamState> next = new ArrayList<>();
            for (BeamState state : beam) {
                if (state.stops.size() >= params.minStops()) {
                    completed.add(state);
                }
                boolean allowNew = state.stops.size() < params.maxStops();
                if ((!allowNew && !canExtend(state)) || !budget.remaining()) {
                    if (!budget.remaining()) {
                        beamDiag().reject(BeamRejectReason.EXPANSION_BUDGET_EXCEEDED);
                    }
                    continue;
                }
                next.addAll(expand(
                        state, visitOptions, context, weather, orientations, schedule, tripEnd, returnBuffer, budget, 0, allowNew));
            }
            if (next.isEmpty()) {
                finishProfileLayer(0, 0, 0);
                loopCompleted = false;
                beamDiag().terminate(BeamTerminationReason.NO_SUCCESSORS);
                break;
            }
            int beforeDominance = next.size();
            long dedupe = BeamLayerProfile.open(BeamLayerProfile.Stage.DEDUPE);
            next = pruneDominated(next);
            BeamLayerProfile.close(dedupe);
            beamDiag().reclassifyAccepted(BeamRejectReason.DOMINANCE_PRUNED, beforeDominance - next.size());
            long sort = BeamLayerProfile.open(BeamLayerProfile.Stage.SORT);
            next.sort(BeamState.ORDER);
            int kept = next.size() <= width ? next.size() : width;
            BeamLayerProfile.close(sort);
            BeamLayerProfile.noteSort(next.size(), kept);
            beamDiag().reclassifyAccepted(BeamRejectReason.BEAM_WIDTH_PRUNED, next.size() - kept);
            beam = next.size() <= width ? next : new ArrayList<>(next.subList(0, width));
            finishProfileLayer(beforeDominance, next.size(), kept);
        }
        if (loopCompleted && !beamDiag().hasTermination()) {
            boolean atCap = !beam.isEmpty();
            for (BeamState state : beam) {
                if (state.stops.size() < params.maxStops()) {
                    atCap = false;
                    break;
                }
            }
            beamDiag().terminate(atCap && lastAfterStops >= params.maxStops()
                    ? BeamTerminationReason.MAX_STOPS_REACHED
                    : BeamTerminationReason.BEAM_COMPLETED_NORMALLY);
        }
        completed.addAll(beam);
        return pickBest(completed, params.minStops(), constraints, true);
        } finally {
            BeamLayerProfile.close(profile);
        }
    }

    private BeamState planRolling(
            List<FishingVisitOption> visitOptions,
            PlanningContext context,
            TimeIndexedWeather weather,
            Map<UUID, LocalOrientation> orientations,
            PlanningProperties.Schedule schedule,
            Instant tripStart,
            Instant tripEnd,
            int returnBuffer,
            SearchParameters params,
            SearchBudget budget,
            RoutePlanConstraints constraints
    ) {
        BeamState committed = BeamState.initial(tripStart, context.accessKnown() ? context.routeStartPoint() : null);
        List<BeamState> completed = new ArrayList<>();
        int n = Math.max(1, visitOptions.size());
        int d = Math.max(1, schedule.getDwellOptionsMinutes().size());
        int minBeam = context.properties().getSearch().getMinBeamWidth();
        int maxBeam = context.properties().getSearch().getMaxBeamWidth();
        int minHorizon = context.properties().getSearch().getMinLookaheadHorizon();
        int maxHorizon = context.properties().getSearch().getMaxLookaheadHorizon();
        int profileRound = 0;
        long profile = BeamLayerProfile.open(BeamLayerProfile.Stage.OTHER);
        try {
        while ((committed.stops.size() < params.maxStops() || canExtend(committed))
                && budget.remaining()
                && hasRemaining(visitOptions, committed, schedule)) {
            int remainingStops = Math.max(1, params.maxStops() - committed.stops.size());
            int width = SearchParameterResolver.adaptiveWidth(
                    params.beamWidth(), minBeam, maxBeam, n, d, remainingStops, budget.remainingCount());
            int horizon = SearchParameterResolver.adaptiveHorizon(
                    params.lookaheadHorizon(), minHorizon, maxHorizon, remainingStops, n, d, width, budget.remainingCount());
            List<BeamState> beam = new ArrayList<>();
            beam.add(committed);
            List<BeamState> window = new ArrayList<>();
            for (int depth = 0; depth < horizon; depth++) {
                if (!budget.remaining()) {
                    beamDiag().terminate(BeamTerminationReason.EXPANSION_BUDGET_EXCEEDED);
                    break;
                }
                boolean lastLayer = depth == horizon - 1;
                int afterStops = 0;
                for (BeamState state : beam) {
                    afterStops = Math.max(afterStops, state.stops.size());
                }
                beamDiag().beginLayer(depth, afterStops);
                BeamLayerProfile.beginLayer(profileRound, depth, beam.size(), width);
                List<BeamState> next = new ArrayList<>();
                for (BeamState state : beam) {
                    if ((state.stops.size() > committed.stops.size()
                            || state.totalValue > committed.totalValue)
                            && state.stops.size() >= params.minStops()) {
                        window.add(state);
                    }
                    boolean allowNew = state.stops.size() < params.maxStops();
                    if ((!allowNew && !canExtend(state)) || !budget.remaining()) {
                        if (!budget.remaining()) {
                            beamDiag().reject(BeamRejectReason.EXPANSION_BUDGET_EXCEEDED);
                        }
                        continue;
                    }
                    List<BeamState> kids = expand(
                            state, visitOptions, context, weather, orientations, schedule, tripEnd, returnBuffer, budget, 0, allowNew);
                    if (lastLayer) {
                        long future = BeamLayerProfile.open(BeamLayerProfile.Stage.SCORING);
                        try {
                        for (BeamState kid : kids) {
                            next.add(kid.withFutureBonus(params.futurePotentialLambda()
                                    * futurePotential(kid, visitOptions, context, weather, orientations, tripEnd, returnBuffer, schedule)));
                        }
                        } finally {
                            BeamLayerProfile.close(future);
                        }
                    } else {
                        next.addAll(kids);
                    }
                }
                if (next.isEmpty()) {
                    finishProfileLayer(0, 0, 0);
                    break;
                }
                int beforeDominance = next.size();
                long dedupe = BeamLayerProfile.open(BeamLayerProfile.Stage.DEDUPE);
                next = pruneDominated(next);
                BeamLayerProfile.close(dedupe);
                beamDiag().reclassifyAccepted(BeamRejectReason.DOMINANCE_PRUNED, beforeDominance - next.size());
                long sort = BeamLayerProfile.open(BeamLayerProfile.Stage.SORT);
                next.sort(BeamState.ORDER);
                int layerWidth = SearchParameterResolver.adaptiveWidth(
                        width, minBeam, maxBeam, n, d, horizon - depth, budget.remainingCount());
                int kept = next.size() <= layerWidth ? next.size() : layerWidth;
                BeamLayerProfile.close(sort);
                BeamLayerProfile.noteSort(next.size(), kept);
                beamDiag().reclassifyAccepted(BeamRejectReason.BEAM_WIDTH_PRUNED, next.size() - kept);
                beam = next.size() <= layerWidth ? next : new ArrayList<>(next.subList(0, layerWidth));
                finishProfileLayer(beforeDominance, next.size(), kept);
            }
            profileRound++;
            window.addAll(beam);
            // Intermediate windows may not cover every Required Point yet; enforce only on final pick.
            BeamState bestWindow = pickBest(window, params.minStops(), constraints, false);
            if (bestWindow == null) {
                beamDiag().terminate(BeamTerminationReason.NO_SUCCESSORS);
                break;
            }
            if (bestWindow.stops.size() == committed.stops.size() && bestWindow.totalValue > committed.totalValue) {
                committed = bestWindow.withoutFuture();
            } else if (bestWindow.stops.size() > committed.stops.size()) {
                int commitCount = Math.min(params.commitPrefixStops(), bestWindow.stops.size() - committed.stops.size());
                if (budget.remainingCount() < 32) {
                    commitCount = 1;
                }
                committed = bestWindow.truncateTo(committed.stops.size() + commitCount).withoutFuture();
            } else {
                beamDiag().terminate(BeamTerminationReason.NO_SUCCESSORS);
                break;
            }
            if (committed.stops.size() >= params.minStops()) {
                completed.add(committed);
            }
        }
        if (committed.stops.size() >= params.minStops()) {
            completed.add(committed);
        }
        BeamState best = pickBest(completed, params.minStops(), constraints, true);
        if (best != null) {
            return best;
        }
        if (constraints != null
                && constraints.hasRequired()
                && !RoutePlanConstraints.coversRequired(committed.stops, constraints.requiredOpportunityIds())) {
            return null;
        }
        return committed;
        } finally {
            BeamLayerProfile.close(profile);
        }
    }

    private static boolean hasRemaining(
            List<FishingVisitOption> visitOptions,
            BeamState state,
            PlanningProperties.Schedule schedule
    ) {
        if (canExtend(state)) {
            return true;
        }
        for (FishingVisitOption option : visitOptions) {
            if (visitKind(state, option, schedule) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean canExtend(BeamState state) {
        return !state.stops.isEmpty();
    }

    static MacroVisitKind visitKind(
            BeamState state,
            FishingVisitOption option,
            PlanningProperties.Schedule schedule
    ) {
        RankedCandidate candidate = option.candidate();
        boolean zone = RouteOpportunityState.isZone(candidate.spot());
        UUID identity = zone
                ? RouteOpportunityState.zoneIdentity(candidate.spot())
                : RouteOpportunityState.atomicIdentity(candidate.spot());
        boolean currentlyThere = !state.stops.isEmpty()
                && identity != null
                && identity.equals(state.stops.get(state.stops.size() - 1).opportunityIdentity());
        if (zone) {
            ZoneVisitState visit = state.opportunity.zone(identity);
            if (currentlyThere) {
                return MacroVisitKind.EXTEND_CURRENT_ZONE;
            }
            if (visit.entries() == 0) {
                return MacroVisitKind.NEW_ZONE_VISIT;
            }
            if (visit.entries() < schedule.getMaxZoneEntries() && !visit.remainingMembers(candidate.spot()).isEmpty()) {
                return MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE;
            }
            return null;
        }
        if (currentlyThere) {
            return MacroVisitKind.EXTEND_CURRENT_ATOMIC;
        }
        if (state.opportunity.atomicConsumed(identity)) {
            return null;
        }
        return MacroVisitKind.NEW_ATOMIC;
    }

    private double futurePotential(
            BeamState state,
            List<FishingVisitOption> visitOptions,
            PlanningContext context,
            TimeIndexedWeather weather,
            Map<UUID, LocalOrientation> orientations,
            Instant tripEnd,
            int returnBuffer,
            PlanningProperties.Schedule schedule
    ) {
        long remaining = Math.max(0, Duration.between(state.cursor, tripEnd).toMinutes() - returnBuffer);
        double cruise = SearchParameterResolver.cruiseKmh(context);
        double detour = context.properties().getTravel().getDetourFactor();
        if (context.accessKnown() && state.location != null && context.routeStartPoint() != null) {
            remaining -= Math.round(geodesicMinutes(state.location, context.routeStartPoint(), cruise, detour));
        }
        if (remaining <= 0) {
            return 0;
        }
        int medianDwell = Math.max(1, schedule.getMinSpotMinutes());
        if (!schedule.getDwellOptionsMinutes().isEmpty()) {
            List<Integer> dwells = new ArrayList<>(schedule.getDwellOptionsMinutes());
            dwells.sort(Integer::compareTo);
            medianDwell = dwells.get(dwells.size() / 2);
        }
        List<double[]> leftovers = new ArrayList<>();
        for (FishingVisitOption option : visitOptions) {
            if (visitKind(state, option, schedule) == null) {
                continue;
            }
            RankedCandidate candidate = option.candidate();
            Point entry = option.entryPoint() == null ? candidate.spot().getLocation() : option.entryPoint();
            if (entry == null) {
                continue;
            }
            double travelMin = state.location == null ? 0 : geodesicMinutes(state.location, entry, cruise, detour);
            Instant arrival = state.cursor.plus(Duration.ofMinutes(Math.round(travelMin)));
            LocalOrientation orientation = orientations.getOrDefault(
                    candidate.spot().planningIdentity(), LocalOrientation.unknown());
            double utility = timeAdjustedSpotUtility.evaluateAt(
                    candidate, arrival, entry, context, weather, orientation, 0).utility();
            leftovers.add(new double[]{utility, travelMin});
        }
        leftovers.sort((a, b) -> Double.compare(b[0], a[0]));
        double potential = 0;
        double timeUsed = 0;
        for (double[] leftover : leftovers) {
            double cycle = medianDwell + leftover[1];
            if (timeUsed + cycle > remaining) {
                continue;
            }
            timeUsed += cycle;
            potential += leftover[0];
            if (timeUsed >= remaining) {
                break;
            }
        }
        return potential;
    }

    private static double geodesicMinutes(Point from, Point to, double cruiseKmh, double detour) {
        if (from == null || to == null) {
            return 0;
        }
        double speed = cruiseKmh <= 0 ? 12 : cruiseKmh;
        double factor = detour <= 0 ? 1.35 : detour;
        return GeoMetrics.distanceM(from, to) * factor / (speed * 1000.0 / 60.0);
    }

    private static void recordSearch(SearchParameters params, SearchBudget budget) {
        GenerateProfiler.current().set("beamWidthEffective", params.beamWidth());
        GenerateProfiler.current().set("maxStopsEffective", params.maxStops());
        GenerateProfiler.current().set("lookaheadHorizonEffective", params.lookaheadHorizon());
        GenerateProfiler.current().set("expansionBudget", budget.maxExpansions());
        GenerateProfiler.current().tag("searchMode", params.mode().name());
        GenerateProfiler.current().tag("searchModeReason", params.modeReason().name());
    }

    private static void finishProfileLayer(int beforeDominance, int afterDominance, int kept) {
        if (!BeamLayerProfile.enabled()) {
            return;
        }
        BeamSearchDiagnostics.Layer layer = beamDiag().currentLayer();
        Map<String, Integer> rejects = new LinkedHashMap<>();
        if (layer != null) {
            for (BeamRejectReason reason : BeamRejectReason.values()) {
                int count = layer.rejectCount(reason);
                if (count > 0) {
                    rejects.put(reason.name(), count);
                }
            }
        }
        BeamLayerProfile.endLayer(beforeDominance, beforeDominance - afterDominance, kept, rejects);
    }

    private static BeamSearchDiagnostics beamDiag() {
        return GenerateProfiler.current().beamSearch();
    }

    private LocalOrientation resolveOrientation(CandidateSpot spot, PlanningContext context) {
        GenerateOrientationCache cache = context == null ? null : context.orientationCache();
        if (cache == null) {
            return orientationService.resolve(spot, context == null ? null : context.geometry());
        }
        UUID snapshotId = context.spatialSnapshot() == null ? null : context.spatialSnapshot().id();
        return cache.getOrResolve(snapshotId, spot, context.geometry(), orientationService).orientation();
    }

    List<BeamState> expand(
            BeamState state,
            List<FishingVisitOption> visitOptions,
            PlanningContext context,
            TimeIndexedWeather weather,
            Map<UUID, LocalOrientation> orientations,
            PlanningProperties.Schedule schedule,
            Instant tripEnd,
            int returnBuffer,
            SearchBudget budget,
            double futureBonus,
            boolean allowNewStops
    ) {
        List<Integer> waits = new ArrayList<>();
        waits.add(0);
        for (Integer option : schedule.getWaitOptionsMinutes()) {
            if (option != null && option > 0 && state.totalWaitMinutes + option <= schedule.getMaxTotalWaitMinutes()) {
                waits.add(option);
            }
        }
        List<BeamState> expansions = new ArrayList<>();
        BeamSearchDiagnostics diag = beamDiag();
        NavStamp navigation = new NavStamp();
        for (int waitMinutes : waits) {
            Instant depart = state.cursor.plus(Duration.ofMinutes(waitMinutes));
            double waitPenalty = waitMinutes == 0
                    ? 0
                    : schedule.getWaitPenalty() * (waitMinutes / (double) Math.max(1, schedule.getSlotMinutes()));
            for (FishingVisitOption option : visitOptions) {
                long successor = BeamLayerProfile.open(BeamLayerProfile.Stage.SUCCESSOR);
                MacroVisitKind kind;
                RankedCandidate candidate;
                UUID featureId;
                UUID identity;
                Point entry;
                Point exit;
                TravelEstimate travel;
                try {
                kind = visitKind(state, option, schedule);
                if (kind == null) {
                    diag.reject(BeamRejectReason.VISIT_KIND_NULL);
                    continue;
                }
                if (kind.isExtend() && waitMinutes > 0) {
                    diag.reject(BeamRejectReason.EXTEND_WAIT_NOT_ALLOWED);
                    continue;
                }
                if (!kind.isExtend() && !allowNewStops) {
                    diag.reject(BeamRejectReason.MAX_STOPS);
                    continue;
                }
                candidate = option.candidate();
                featureId = candidate.spot().planningIdentity();
                identity = RouteOpportunityState.isZone(candidate.spot())
                        ? RouteOpportunityState.zoneIdentity(candidate.spot())
                        : RouteOpportunityState.atomicIdentity(candidate.spot());
                if (!spacingOk(candidate, state.stops, identity, context.properties().getCandidates().getMinSpacingM())) {
                    diag.reject(BeamRejectReason.SPACING);
                    continue;
                }
                entry = option.entryPoint();
                exit = option.exitPoint();
                travel = kind.isExtend()
                        ? TravelEstimate.zero()
                        : travelFrom(state.location, entry, context, state.stops.isEmpty());
                } finally {
                    BeamLayerProfile.close(successor);
                }
                long hard = BeamLayerProfile.open(BeamLayerProfile.Stage.HARD);
                Instant arrival;
                long remainingMin;
                try {
                if (travel.unknownTravel() && state.location != null && !kind.isExtend()) {
                    diag.reject(BeamRejectReason.UNKNOWN_TRAVEL);
                    continue;
                }
                arrival = kind.isExtend()
                        ? state.stops.get(state.stops.size() - 1).arrivalAt()
                        : depart.plusSeconds(Math.round(travel.minutes() * 60));
                if (!kind.isExtend() && boatWeatherPenalty.travelIntervalHardReject(weather, depart, arrival, context)) {
                    diag.reject(BeamRejectReason.OUTBOUND_WEATHER_HARD_REJECT);
                    continue;
                }
                remainingMin = Math.max(0, Duration.between(arrival, tripEnd).toMinutes() - returnBuffer);
                if (context.accessKnown()) {
                    TravelEstimate homeProbe = travelTimeEstimator.estimate(exit, context.routeStartPoint(), context);
                    remainingMin = Math.max(0, remainingMin - Math.round(homeProbe.minutes()));
                }
                } finally {
                    BeamLayerProfile.close(hard);
                }
                long scoring = BeamLayerProfile.open(BeamLayerProfile.Stage.SCORING);
                List<VisitCandidate> visits;
                try {
                visits = visitCandidates(
                        state, option, candidate, kind, arrival, (int) remainingMin, schedule, context, weather,
                        orientations.getOrDefault(featureId, LocalOrientation.unknown()));
                } finally {
                    BeamLayerProfile.close(scoring);
                }
                for (VisitCandidate visit : visits) {
                    if (!budget.tryConsume()) {
                        diag.reject(BeamRejectReason.EXPANSION_BUDGET_EXCEEDED);
                        return expansions;
                    }
                    GenerateProfiler.current().count("beamExpansions");
                    int dwell = visit.dwell();
                    Instant departure = arrival.plus(Duration.ofMinutes(dwell));
                    long hardVisit = BeamLayerProfile.open(BeamLayerProfile.Stage.HARD);
                    TravelEstimate home;
                    double localKm;
                    ZoneFishingPackage recordedPackage;
                    try {
                    if (departure.isAfter(tripEnd)) {
                        GenerateProfiler.current().count("beamStatesPruned");
                        diag.reject(BeamRejectReason.DEPARTURE_AFTER_TRIP_END);
                        continue;
                    }
                    home = TravelEstimate.zero();
                    if (context.accessKnown()) {
                        home = travelTimeEstimator.estimate(exit, context.routeStartPoint(), context);
                        Instant returnAt = departure.plusSeconds(Math.round(home.minutes() * 60) + returnBuffer * 60L);
                        if (returnAt.isAfter(tripEnd)) {
                            GenerateProfiler.current().count("beamStatesPruned");
                            diag.reject(BeamRejectReason.RETURN_RESERVE);
                            continue;
                        }
                        if (boatWeatherPenalty.travelIntervalHardReject(
                                weather, departure, departure.plusSeconds(Math.round(home.minutes() * 60)), context)) {
                            GenerateProfiler.current().count("beamStatesPruned");
                            diag.reject(BeamRejectReason.RETURN_WEATHER_HARD_REJECT);
                            continue;
                        }
                    }
                    localKm = visit.packageOrNull() == null ? 0 : visit.packageOrNull().localDistanceM() / 1000.0;
                    recordedPackage = visit.packageOrNull() != null
                            ? visit.packageOrNull()
                            : new ZoneFishingPackage(
                                    dwell,
                                    visit.fishingMinutes(),
                                    visit.internalTransitMinutes(),
                                    visit.waitMinutes(),
                                    0,
                                    visit.utility(),
                                    List.of(),
                                    visit.subPlan());
                    if (legTooLong(travel, context)) {
                        GenerateProfiler.current().count("beamStatesPruned");
                        diag.reject(BeamRejectReason.LEG_TOO_LONG);
                        continue;
                    }
                    if (rangeExceeded(state, entry, exit, localKm, kind, context, navigation)) {
                        if (DepthZeroDiagnostic.enabled() && state.stops.isEmpty()) {
                            DepthZeroDiagnostic.range(candidate, kind, dwell, travel, home, localKm, context);
                        }
                        GenerateProfiler.current().count("beamStatesPruned");
                        diag.reject(BeamRejectReason.RANGE_EXCEEDED);
                        continue;
                    }
                    } finally {
                        BeamLayerProfile.close(hardVisit);
                    }
                    LocalOrientation orientation = orientations.getOrDefault(featureId, LocalOrientation.unknown());
                    long arrivalScore = BeamLayerProfile.open(BeamLayerProfile.Stage.SCORING);
                    TimeAdjustedSpotUtility.Evaluation atArrival;
                    double increment;
                    try {
                    atArrival = timeAdjustedSpotUtility.evaluateAt(
                            candidate, arrival, entry, context, weather, orientation, kind.isExtend() ? 0 : waitPenalty);
                    increment = routeIncrement(
                            state, kind, visit, travel, waitPenalty, entry, schedule, identity);
                    if (BeamLayerProfile.enabled()) {
                        Point from = state.location;
                        BeamLayerProfile.scoreCandidate(
                                String.valueOf(featureId),
                                kind.name(),
                                from == null ? "launch" : Math.round(from.getY() * 10000) + "," + Math.round(from.getX() * 10000),
                                entry == null ? "none" : Math.round(entry.getY() * 10000) + "," + Math.round(entry.getX() * 10000));
                    }
                    } finally {
                        BeamLayerProfile.close(arrivalScore);
                    }
                    long hardScore = BeamLayerProfile.open(BeamLayerProfile.Stage.HARD);
                    try {
                    if (boatWeatherPenalty.hardReject(atArrival.weather(), context)) {
                        GenerateProfiler.current().count("beamStatesPruned");
                        diag.reject(BeamRejectReason.ARRIVAL_WEATHER_HARD_REJECT);
                        continue;
                    }
                    if (DepthZeroDiagnostic.enabled() && state.stops.isEmpty()) {
                        DepthZeroDiagnostic.score(
                                candidate, kind, dwell, visit.utility(), travel, waitPenalty, atArrival, increment, state.totalValue);
                    }
                    if (increment <= 0) {
                        GenerateProfiler.current().count("beamStatesPruned");
                        diag.recordNonPositiveIncrement(kind, candidate.spot().getTargetKind(), increment);
                        diag.reject(BeamRejectReason.NON_POSITIVE_INCREMENT);
                        continue;
                    }
                    if (kind == MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE
                            && (visit.packageOrNull() == null || visit.packageOrNull().consumedMemberIds().isEmpty())) {
                        GenerateProfiler.current().count("beamStatesPruned");
                        diag.reject(BeamRejectReason.REVISIT_NO_NEW_MEMBERS);
                        continue;
                    }
                    } finally {
                        BeamLayerProfile.close(hardScore);
                    }
                    int appliedWait = kind.isExtend() ? state.stops.get(state.stops.size() - 1).precedingWaitMinutes() : waitMinutes;
                    String waitLocation = kind.isExtend()
                            ? state.stops.get(state.stops.size() - 1).precedingWaitLocation()
                            : (waitMinutes == 0 ? null : (state.stops.isEmpty() ? "LAUNCH" : "CURRENT"));
                    TravelEstimate stopTravel = kind.isExtend()
                            ? state.stops.get(state.stops.size() - 1).fromPrevious()
                            : travel;
                    long stateCopy = BeamLayerProfile.open(BeamLayerProfile.Stage.STATE_COPY);
                    PlannedStop stop;
                    try {
                    if (BeamLayerProfile.enabled()) {
                        BeamLayerProfile.environmentCopied();
                    }
                    stop = new PlannedStop(
                            candidate,
                            arrival,
                            departure,
                            dwell,
                            stopTravel,
                            atArrival.breakdown(),
                            List.of(),
                            atArrival.environment().toMap(),
                            appliedWait,
                            waitLocation,
                            option,
                            visit.subPlan(),
                            visit.fishingMinutes(),
                            visit.internalTransitMinutes(),
                            visit.waitMinutes(),
                            kind,
                            increment,
                            localKm,
                            recordedPackage
                    );
                    boolean fallback = outsideGeneratedWindow(candidate, arrival, context);
                    expansions.add(state.child(
                                    stop, increment, kind, identity, recordedPackage, fallback, departure, exit,
                                    windowBucket(departure, context), navigation)
                            .withFutureBonus(futureBonus));
                    } finally {
                        BeamLayerProfile.close(stateCopy);
                    }
                    diag.accept();
                }
            }
        }
        return expansions;
    }

    private List<VisitCandidate> visitCandidates(
            BeamState state,
            FishingVisitOption option,
            RankedCandidate candidate,
            MacroVisitKind kind,
            Instant arrival,
            int remainingMin,
            PlanningProperties.Schedule schedule,
            PlanningContext context,
            TimeIndexedWeather weather,
            LocalOrientation orientation
    ) {
        if (kind == MacroVisitKind.NEW_ZONE_VISIT
                || kind == MacroVisitKind.EXTEND_CURRENT_ZONE
                || kind == MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE) {
            UUID zoneId = RouteOpportunityState.zoneIdentity(candidate.spot());
            ZoneVisitState visitState = packageVisitState(state, kind, zoneId);
            List<ZoneFishingPackage> packages;
            if (zoneSubPlanner != null) {
                packages = zoneSubPlanner.packages(
                        candidate.spot(), visitState, arrival, option.entry(), option.exit(), context, weather, remainingMin);
            } else {
                packages = List.of();
            }
            int currentStay = kind.isExtend() ? state.stops.get(state.stops.size() - 1).stayMinutes() : 0;
            List<VisitCandidate> out = new ArrayList<>();
            if (!packages.isEmpty()) {
                for (ZoneFishingPackage pkg : packages) {
                    if (kind.isExtend() && pkg.visitMinutes() <= currentStay) {
                        continue;
                    }
                    out.add(VisitCandidate.fromPackage(pkg));
                }
                return out;
            }
            for (int dwell : DwellPolicy.options(candidate, remainingMin, schedule)) {
                if (kind.isExtend() && dwell <= currentStay) {
                    continue;
                }
                VisitEvaluation eval = evaluateVisit(option, candidate, arrival, dwell, context, weather, orientation);
                if (eval != null) {
                    out.add(VisitCandidate.fromEval(eval, dwell));
                }
            }
            return out;
        }
        int currentStay = kind.isExtend() ? state.stops.get(state.stops.size() - 1).stayMinutes() : 0;
        List<VisitCandidate> out = new ArrayList<>();
        for (int dwell : DwellPolicy.options(candidate, remainingMin, schedule)) {
            if (kind.isExtend() && dwell <= currentStay) {
                continue;
            }
            VisitEvaluation eval = evaluateVisit(option, candidate, arrival, dwell, context, weather, orientation);
            if (eval != null) {
                out.add(VisitCandidate.fromEval(eval, dwell));
            }
        }
        return out;
    }

    private static ZoneVisitState packageVisitState(BeamState state, MacroVisitKind kind, UUID zoneId) {
        ZoneVisitState current = state.opportunity.zone(zoneId);
        if (kind != MacroVisitKind.EXTEND_CURRENT_ZONE || state.stops.isEmpty()) {
            return current;
        }
        PlannedStop last = state.stops.get(state.stops.size() - 1);
        ZoneFishingPackage previous = last.fishingPackage();
        if (previous == null) {
            return current;
        }
        return current.replaceCurrentEntry(previous, new ZoneFishingPackage(
                0, 0, 0, 0, 0, 0, List.of(), last.zoneSubPlan()), last.arrivalAt());
    }

    private double routeIncrement(
            BeamState state,
            MacroVisitKind kind,
            VisitCandidate visit,
            TravelEstimate travel,
            double waitPenalty,
            Point entry,
            PlanningProperties.Schedule schedule,
            UUID zoneId
    ) {
        int entryIndex = 1;
        if (kind == MacroVisitKind.NEW_ZONE_VISIT || kind == MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE) {
            entryIndex = state.opportunity.entries(zoneId) + 1;
        } else if (kind == MacroVisitKind.EXTEND_CURRENT_ZONE) {
            entryIndex = Math.max(1, state.opportunity.entries(zoneId));
        }
        double decay = Math.pow(schedule.getDwellDecay(), Math.max(0, entryIndex - 1));
        double fishing = visit.utility() * (kind == MacroVisitKind.NEW_ATOMIC || kind == MacroVisitKind.EXTEND_CURRENT_ATOMIC
                ? 1.0
                : decay);
        if (kind.isExtend()) {
            PlannedStop last = state.stops.get(state.stops.size() - 1);
            double previousFishing = last.fishingPackage() == null ? 0 : last.fishingPackage().marginalUtility() * (
                    kind == MacroVisitKind.EXTEND_CURRENT_ATOMIC ? 1.0 : decay);
            return fishing - previousFishing;
        }
        double proximity = 0;
        if (!state.stops.isEmpty() && state.location != null && entry != null) {
            double meters = GeoMetrics.distanceM(state.location, entry);
            proximity = 1.0 - Math.min(1.0, meters / 4000.0);
        }
        double landPenalty = travel.landCrossingDetected() ? 0.15 : 0;
        double travelCost = 0.04 * Math.min(1.0, travel.minutes() / 30.0);
        return fishing + 0.12 * proximity - landPenalty - travelCost - waitPenalty;
    }

    private VisitEvaluation evaluateVisit(
            FishingVisitOption option,
            RankedCandidate candidate,
            Instant arrival,
            int visitMinutes,
            PlanningContext context,
            TimeIndexedWeather weather,
            LocalOrientation orientation
    ) {
        TargetKind kind = option.kind();
        if (kind == TargetKind.ZONE && zoneSubPlanner != null) {
            ZoneSubPlan sub = zoneSubPlanner.plan(
                    candidate.spot(),
                    arrival,
                    visitMinutes,
                    option.entry(),
                    option.exit(),
                    context,
                    weather
            );
            if (sub.utility() < 0) {
                double fishing = timeAdjustedSpotUtility.dwellValue(
                        candidate, arrival, visitMinutes, context, weather, orientation);
                return new VisitEvaluation(fishing, visitMinutes, 0, 0, null);
            }
            return new VisitEvaluation(sub.utility(), sub.fishingMinutes(), sub.internalTransitMinutes(), sub.waitMinutes(), sub);
        }
        if (kind == TargetKind.PATH || kind == TargetKind.SEGMENT || kind == TargetKind.AREA) {
            org.locationtech.jts.geom.Point from = option.entryPoint();
            org.locationtech.jts.geom.Point to = option.exitPoint();
            org.locationtech.jts.geom.Geometry path = candidate.spot().getSelectedFishingPath() == null
                    ? candidate.spot().getTargetGeometry()
                    : candidate.spot().getSelectedFishingPath();
            if (option.traversal() == com.aifishing.planning.spatial.PathTraversal.REVERSE
                    || option.traversal() == com.aifishing.planning.spatial.PathTraversal.COUNTER_CLOCKWISE) {
                var tmp = from;
                from = to;
                to = tmp;
            }
            double utility = spatialUtility.alongPath(
                    candidate,
                    arrival,
                    visitMinutes,
                    from,
                    to,
                    path,
                    context,
                    weather,
                    orientation
            );
            return new VisitEvaluation(utility, visitMinutes, 0, 0, null);
        }
        double fishing = timeAdjustedSpotUtility.dwellValue(
                candidate, arrival, visitMinutes, context, weather, orientation);
        return new VisitEvaluation(fishing, visitMinutes, 0, 0, null);
    }

    private record VisitEvaluation(
            double utility,
            int fishingMinutes,
            int internalTransitMinutes,
            int waitMinutes,
            ZoneSubPlan subPlan
    ) {
    }

    private record VisitCandidate(
            int dwell,
            double utility,
            int fishingMinutes,
            int internalTransitMinutes,
            int waitMinutes,
            ZoneSubPlan subPlan,
            ZoneFishingPackage packageOrNull
    ) {
        static VisitCandidate fromPackage(ZoneFishingPackage pkg) {
            return new VisitCandidate(
                    pkg.visitMinutes(),
                    pkg.marginalUtility(),
                    pkg.fishingMinutes(),
                    pkg.localTransitMinutes(),
                    pkg.waitMinutes(),
                    pkg.subPlan(),
                    pkg
            );
        }

        static VisitCandidate fromEval(VisitEvaluation eval, int dwell) {
            return new VisitCandidate(
                    dwell,
                    eval.utility(),
                    eval.fishingMinutes(),
                    eval.internalTransitMinutes(),
                    eval.waitMinutes(),
                    eval.subPlan(),
                    null
            );
        }
    }

    /**
     * @param enforceHardConstraints when true, drop routes missing Required Points and apply Hybrid
     *        40–60 secondary preference. Intermediate rolling-horizon windows must pass false so
     *        search can still commit prefixes before every required stop is visited.
     */
    private BeamState pickBest(
            List<BeamState> completed,
            int minWaypoints,
            RoutePlanConstraints constraints,
            boolean enforceHardConstraints
    ) {
        RoutePlanConstraints effective = constraints == null ? RoutePlanConstraints.none() : constraints;
        List<BeamState> eligible = new ArrayList<>();
        for (BeamState state : completed) {
            if (state.stops.isEmpty()) {
                continue;
            }
            if (enforceHardConstraints
                    && effective.hasRequired()
                    && !RoutePlanConstraints.coversRequired(state.stops, effective.requiredOpportunityIds())) {
                continue;
            }
            eligible.add(state);
        }
        if (eligible.isEmpty()) {
            return null;
        }
        BeamState best = null;
        for (BeamState state : eligible) {
            if (best == null) {
                best = state;
                continue;
            }
            boolean bestMeets = best.stops.size() >= minWaypoints;
            boolean stateMeets = state.stops.size() >= minWaypoints;
            if (stateMeets && !bestMeets) {
                best = state;
            } else if (stateMeets == bestMeets && BeamState.ORDER.compare(state, best) < 0) {
                best = state;
            }
        }
        if (!enforceHardConstraints
                || best == null
                || !effective.hybridBalanceEnabled()
                || best.totalValue <= 0) {
            return best;
        }
        double bestUtility = best.totalValue + best.futureBonus;
        BeamState hybridBest = best;
        double bestDistance = RoutePlanConstraints.hybridBalanceDistance(best.stops);
        for (BeamState state : eligible) {
            if (state.totalValue <= 0) {
                continue;
            }
            boolean bestMeets = hybridBest.stops.size() >= minWaypoints;
            boolean stateMeets = state.stops.size() >= minWaypoints;
            if (bestMeets && !stateMeets) {
                continue;
            }
            double utility = state.totalValue + state.futureBonus;
            if (!RoutePlanConstraints.utilitiesReasonablyClose(utility, bestUtility)) {
                continue;
            }
            double distance = RoutePlanConstraints.hybridBalanceDistance(state.stops);
            if (distance + 1e-12 < bestDistance
                    || (Math.abs(distance - bestDistance) <= 1e-12
                    && BeamState.ORDER.compare(state, hybridBest) < 0)) {
                hybridBest = state;
                bestDistance = distance;
            }
        }
        return hybridBest;
    }

    private RouteResult toResult(
            BeamState best,
            PlanningContext context,
            TimeIndexedWeather weather,
            Instant tripStart,
            Instant tripEnd,
            int returnBuffer
    ) {
        List<PlannedStop> explained = new ArrayList<>();
        List<ScheduleWaitEvent> waits = new ArrayList<>();
        Instant launchDeparture = tripStart;
        int fishingMinutes = 0;
        double travelMinutes = 0;
        boolean fallback = best.usedGlobalFallback;
        for (int i = 0; i < best.stops.size(); i++) {
            PlannedStop stop = best.stops.get(i);
            LocalOrientation orientation = resolveOrientation(stop.candidate().spot(), context);
            WhyThisTimeExplainer.Result why = WhyThisTimeExplainer.explain(
                    stop.candidate(),
                    stop.arrivalAt(),
                    stop.stayMinutes(),
                    context,
                    weather,
                    orientation,
                    timeAdjustedSpotUtility
            );
            Map<String, Object> environment = new java.util.LinkedHashMap<>(stop.environment());
            environment.put("whyThisTimeEvidence", why.evidence());
            PlannedStop explainedStop = stop.withExplanation(why.lines(), environment);
            explained.add(explainedStop);
            fishingMinutes += stop.plannedFishingMinutes();
            if (!stop.fromPrevious().unknownTravel()) {
                travelMinutes += stop.fromPrevious().minutes();
            }
            travelMinutes += stop.plannedInternalTransitMinutes();
            if (stop.precedingWaitMinutes() > 0) {
                Instant waitTo = stop.arrivalAt().minusSeconds(Math.round(stop.fromPrevious().minutes() * 60));
                Instant waitFrom = waitTo.minus(Duration.ofMinutes(stop.precedingWaitMinutes()));
                if (i == 0) {
                    launchDeparture = waitTo;
                }
                waits.add(new ScheduleWaitEvent(
                        waitFrom,
                        waitTo,
                        stop.precedingWaitLocation() == null ? "CURRENT" : stop.precedingWaitLocation(),
                        i == 0 ? null : i,
                        stop.precedingWaitMinutes()
                ));
            } else if (i == 0) {
                Instant travelStart = stop.arrivalAt().minusSeconds(Math.round(stop.fromPrevious().minutes() * 60));
                launchDeparture = travelStart.isBefore(tripStart) ? tripStart : travelStart;
            }
            fallback = fallback || outsideGeneratedWindow(stop.candidate(), stop.arrivalAt(), context);
        }
        TravelEstimate home = TravelEstimate.zero();
        Instant returnAt = explained.get(explained.size() - 1).departureAt();
        if (context.accessKnown()) {
            home = travelTimeEstimator.estimate(
                    explained.get(explained.size() - 1).exitPoint(),
                    context.routeStartPoint(),
                    context
            );
            returnAt = explained.get(explained.size() - 1).departureAt().plusSeconds(Math.round(home.minutes() * 60));
            if (!home.unknownTravel()) {
                travelMinutes += home.minutes();
            }
        }
        int reserve = (int) Math.max(0, Duration.between(returnAt, tripEnd).toMinutes() - returnBuffer);
        if (waits.isEmpty() && explained.get(0).precedingWaitMinutes() == 0 && context.accessKnown()) {
            launchDeparture = tripStart;
            if (!explained.get(0).fromPrevious().unknownTravel()) {
                Instant implied = explained.get(0).arrivalAt().minusSeconds(Math.round(explained.get(0).fromPrevious().minutes() * 60));
                if (!implied.isBefore(tripStart)) {
                    launchDeparture = implied;
                }
            }
        }
        return new RouteResult(
                List.copyOf(explained),
                fallback,
                launchDeparture,
                returnAt,
                home,
                List.copyOf(waits),
                best.totalWaitMinutes,
                fishingMinutes,
                travelMinutes,
                reserve,
                best.totalValue
        );
    }

    private boolean outsideGeneratedWindow(RankedCandidate candidate, Instant arrival, PlanningContext context) {
        LocalTime local = TripClock.localTime(arrival, context.lake());
        LocalTime from = candidate.spot().getWindowFrom();
        LocalTime to = candidate.spot().getWindowTo();
        if (from == null || to == null) {
            return false;
        }
        return local.isBefore(from) || !local.isBefore(to);
    }

    private TravelEstimate travelFrom(Point current, Point next, PlanningContext context, boolean first) {
        if (first && !context.accessKnown()) {
            return TravelEstimate.unspecified();
        }
        if (current == null) {
            return TravelEstimate.zero();
        }
        return travelTimeEstimator.estimate(current, next, context);
    }

    private boolean spacingOk(
            RankedCandidate candidate,
            List<PlannedStop> chosen,
            UUID identity,
            double minSpacingM
    ) {
        for (PlannedStop stop : chosen) {
            if (identity != null && identity.equals(stop.opportunityIdentity())) {
                continue;
            }
            if (GeoMetrics.distanceM(candidate.spot().getLocation(), stop.candidate().spot().getLocation()) < minSpacingM) {
                return false;
            }
        }
        return true;
    }

    private boolean legTooLong(TravelEstimate travel, PlanningContext context) {
        if (context.fishingMode() != FishingMode.BOAT || travel.unknownTravel()) {
            return false;
        }
        double maxLegKm = context.effectiveBoatCapability() != null
                ? context.effectiveBoatCapability().maxLegKm()
                : context.properties().maxOneWayKm(context.boat() == null ? BoatType.OTHER : context.boat().getType());
        return appliedMeters(travel) > maxLegKm * 1000.0;
    }

    private boolean rangeExceeded(
            BeamState state,
            Point entry,
            Point exit,
            double newLocalKm,
            MacroVisitKind kind,
            PlanningContext context,
            NavStamp navigation
    ) {
        navigation.ready = false;
        if (context.fishingMode() != FishingMode.BOAT
                || context.effectiveBoatCapability() == null
                || !context.effectiveBoatCapability().rangeEnforced()
                || context.effectiveBoatCapability().effectiveUsableRangeKm() == null) {
            return false;
        }
        double limit = context.effectiveBoatCapability().effectiveUsableRangeKm();
        long hard = BeamLayerProfile.open(BeamLayerProfile.Stage.HARD);
        double lower;
        try {
            lower = lowerBoundKm(state, entry, exit, newLocalKm, kind, context);
        } finally {
            BeamLayerProfile.close(hard);
        }
        if (lower > limit) {
            return true;
        }
        long water = BeamLayerProfile.open(BeamLayerProfile.Stage.WATER);
        try {
            stampNavigation(navigation, state, entry, exit, newLocalKm, kind, context);
        } finally {
            BeamLayerProfile.close(water);
        }
        return navigation.checkKm > limit;
    }

    /**
     * Straight-line legs only. This can reject a candidate, but it cannot accept one:
     * any trip whose geodesic length still fits continues to the cached water path.
     */
    private double lowerBoundKm(
            BeamState state,
            Point entry,
            Point exit,
            double newLocalKm,
            MacroVisitKind kind,
            PlanningContext context
    ) {
        boolean extend = kind != null && kind.isExtend() && !state.stops.isEmpty();
        int committed = extend ? state.stops.size() - 1 : state.stops.size();
        double used = 0;
        for (int i = 0; i < committed; i++) {
            PlannedStop stop = state.stops.get(i);
            used += geodesicKm(stop.fromPrevious());
            used += stop.localDistanceKm();
        }
        if (extend) {
            return used + newLocalKm + geodesicKm(exit, context.routeStartPoint());
        }
        return used + geodesicKm(state.location, entry) + newLocalKm + geodesicKm(exit, context.routeStartPoint());
    }

    /**
     * Committed transit and in-zone kilometres already stored on the parent.
     * The new leg and the exact return are looked up here. Return kilometres are
     * not stored, so a later candidate cannot count the previous return twice.
     */
    private void stampNavigation(
            NavStamp stamp,
            BeamState state,
            Point entry,
            Point exit,
            double newLocalKm,
            MacroVisitKind kind,
            PlanningContext context
    ) {
        boolean extend = kind != null && kind.isExtend() && !state.stops.isEmpty();
        Point launch = context.routeStartPoint();
        if (extend) {
            PlannedStop last = state.stops.get(state.stops.size() - 1);
            double inbound = samePoint(last.entryPoint(), entry)
                    ? state.lastInboundKm
                    : legKm(state.legOrigin, entry, context);
            stamp.lastInboundKm = inbound;
            stamp.prefixNavigatedKm = state.prefixNavigatedKm;
            stamp.navigatedKm = state.prefixNavigatedKm + inbound + newLocalKm;
            stamp.legOrigin = state.legOrigin;
            stamp.checkKm = state.prefixNavigatedKm + newLocalKm + legKm(exit, launch, context);
            stamp.replayAvoided = Math.max(0, state.stops.size() - 1);
        } else {
            Point from = state.stops.isEmpty() ? launch : state.location;
            double checkInbound = legKm(from, entry, context);
            Point cursor = committedCursor(state, launch);
            stamp.lastInboundKm = samePoint(cursor, from) ? checkInbound : legKm(cursor, entry, context);
            stamp.prefixNavigatedKm = state.navigatedKm;
            stamp.navigatedKm = state.navigatedKm + stamp.lastInboundKm + newLocalKm;
            stamp.legOrigin = cursor;
            stamp.checkKm = state.navigatedKm + checkInbound + newLocalKm + legKm(exit, launch, context);
            stamp.replayAvoided = state.stops.size();
        }
        stamp.ready = true;
        if (stamp.replayAvoided > 0 && BeamLayerProfile.enabled()) {
            GenerateProfiler.current().count("committedLegReplayAvoided", stamp.replayAvoided);
            BeamLayerProfile.noteReplayAvoided(stamp.replayAvoided);
        }
    }

    private static Point committedCursor(BeamState state, Point launch) {
        if (state.stops.isEmpty()) {
            return launch;
        }
        PlannedStop last = state.stops.get(state.stops.size() - 1);
        Point exit = last.exitPoint();
        return exit != null ? exit : last.entryPoint();
    }

    private static boolean samePoint(Point a, Point b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Double.doubleToLongBits(a.getX()) == Double.doubleToLongBits(b.getX())
                && Double.doubleToLongBits(a.getY()) == Double.doubleToLongBits(b.getY());
    }

    /**
     * Distance facts for one candidate. Return kilometres stay on {@code checkKm} only.
     * {@code navigatedKm} is the committed total a child stores, with no return leg.
     */
    private static final class NavStamp {
        private boolean ready;
        private double navigatedKm;
        private double prefixNavigatedKm;
        private double lastInboundKm;
        private double checkKm;
        private Point legOrigin;
        private int replayAvoided;
    }

    /**
     * @return water-path kilometres, {@link Double#POSITIVE_INFINITY} when the cells exist but
     * no path does, or {@code null} when there is no raster to navigate. Null falls back to the
     * geodesic lower bound and never to the detour heuristic.
     */
    private Double waterPathKm(Point from, Point to, PlanningContext context) {
        if (from == null || to == null || waterPaths == null
                || context.spatialSnapshot() == null || context.spatialSnapshot().raster() == null) {
            return null;
        }
        LakeNavRaster raster = context.spatialSnapshot().raster();
        int[] fromCell = raster.cellOf(from);
        int[] toCell = raster.cellOf(to);
        if (fromCell == null || toCell == null) {
            return null;
        }
        var path = waterPaths.transitPath(
                context.spatialSnapshot(),
                LakeNavRaster.cellKey(fromCell),
                LakeNavRaster.cellKey(toCell),
                from,
                to,
                context.properties().getSpatial());
        if (path.isEmpty() || !Double.isFinite(path.get().meters())) {
            return Double.POSITIVE_INFINITY;
        }
        return path.get().meters() / 1000.0;
    }

    private double legKm(Point from, Point to, PlanningContext context) {
        Double navigated = waterPathKm(from, to, context);
        if (navigated == null) {
            return geodesicKm(from, to);
        }
        return navigated;
    }

    private static double geodesicKm(TravelEstimate travel) {
        if (travel == null || travel.unknownTravel()) {
            return 0;
        }
        return travel.distanceM() / 1000.0;
    }

    private static double geodesicKm(Point from, Point to) {
        if (from == null || to == null) {
            return 0;
        }
        return RequestSpatialCache.geodesicMeters(from, to) / 1000.0;
    }

    private static double usedRangeKm(List<PlannedStop> stops) {
        double used = 0;
        for (PlannedStop stop : stops) {
            used += appliedKm(stop.fromPrevious());
            used += stop.localDistanceKm();
        }
        return used;
    }

    private List<BeamState> pruneDominated(List<BeamState> states) {
        List<BeamState> kept = new ArrayList<>();
        for (BeamState candidate : states) {
            boolean dominated = false;
            for (int i = kept.size() - 1; i >= 0; i--) {
                BeamState existing = kept.get(i);
                if (!candidate.sameTemporalOpportunity(existing)) {
                    continue;
                }
                if (dominates(existing, candidate)) {
                    dominated = true;
                    break;
                }
                if (dominates(candidate, existing)) {
                    kept.remove(i);
                }
            }
            if (!dominated) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private static boolean dominates(BeamState a, BeamState b) {
        boolean betterOrEqualTime = !a.cursor.isAfter(b.cursor);
        boolean betterOrEqualRange = a.usedRangeKm() <= b.usedRangeKm();
        boolean betterOrEqualValue = a.totalValue + a.futureBonus >= b.totalValue + b.futureBonus - 1e-12;
        boolean strictlyBetter = a.cursor.isBefore(b.cursor)
                || a.usedRangeKm() < b.usedRangeKm() - 1e-9
                || a.totalValue + a.futureBonus > b.totalValue + b.futureBonus + 1e-12;
        return betterOrEqualTime && betterOrEqualRange && betterOrEqualValue && strictlyBetter;
    }

    private static String windowBucket(Instant instant, PlanningContext context) {
        if (instant == null || context == null || context.lake() == null) {
            return "none";
        }
        LocalTime local = TripClock.localTime(instant, context.lake());
        var window = ArrivalStrategyEvaluator.matchingWindow(context.profile(), local);
        if (window != null && window.from() != null && window.to() != null) {
            return window.from() + "-" + window.to();
        }
        return local == null ? "none" : "hour-" + local.getHour();
    }

    private static double appliedMeters(TravelEstimate travel) {
        if (travel == null || travel.unknownTravel()) {
            return 0;
        }
        return travel.distanceM() * travel.appliedDetourFactor();
    }

    private static double appliedKm(TravelEstimate travel) {
        return appliedMeters(travel) / 1000.0;
    }

    public record RouteResult(
            List<PlannedStop> stops,
            boolean usedGlobalFallback,
            Instant plannedLaunchDepartureAt,
            Instant plannedReturnAt,
            TravelEstimate returnTravel,
            List<ScheduleWaitEvent> waitEvents,
            int totalWaitMinutes,
            int totalFishingMinutes,
            double totalTravelMinutes,
            int scheduleReserveMinutes,
            double routeUtility,
            String hardConstraintFailure
    ) {
        public RouteResult(
                List<PlannedStop> stops,
                boolean usedGlobalFallback,
                Instant plannedLaunchDepartureAt,
                Instant plannedReturnAt,
                TravelEstimate returnTravel,
                List<ScheduleWaitEvent> waitEvents,
                int totalWaitMinutes,
                int totalFishingMinutes,
                double totalTravelMinutes,
                int scheduleReserveMinutes,
                double routeUtility
        ) {
            this(
                    stops,
                    usedGlobalFallback,
                    plannedLaunchDepartureAt,
                    plannedReturnAt,
                    returnTravel,
                    waitEvents,
                    totalWaitMinutes,
                    totalFishingMinutes,
                    totalTravelMinutes,
                    scheduleReserveMinutes,
                    routeUtility,
                    null
            );
        }

        public RouteResult(List<PlannedStop> stops, boolean usedGlobalFallback) {
            this(stops, usedGlobalFallback, null, null, TravelEstimate.zero(), List.of(), 0, 0, 0, 0, 0, null);
        }
    }

    static final class BeamState {
        private static final Comparator<BeamState> ORDER = Comparator
                .comparingDouble((BeamState state) -> -(state.totalValue + state.futureBonus))
                .thenComparingInt(state -> -state.stops.size())
                .thenComparing(state -> state.stops.isEmpty()
                        ? ""
                        : state.stops.get(0).candidate().spot().getFeatureId().toString());

        private final Instant cursor;
        private final Point location;
        private final List<PlannedStop> stops;
        private final RouteOpportunityState opportunity;
        private final double totalValue;
        private final int totalWaitMinutes;
        private final boolean usedGlobalFallback;
        private final double futureBonus;
        private final BeamState parent;
        private final String windowBucket;
        /** Committed transit plus in-zone kilometres. The return leg is not included. */
        private final double navigatedKm;
        /** Committed kilometres excluding the last stop, used when that stop is extended. */
        private final double prefixNavigatedKm;
        /** Exact inbound water-path kilometres of the last stop. Reused when an extend keeps the entry. */
        private final double lastInboundKm;
        /** Start of the last stop's inbound leg. Launch when the route has one stop. */
        private final Point legOrigin;

        private BeamState(
                Instant cursor,
                Point location,
                List<PlannedStop> stops,
                RouteOpportunityState opportunity,
                double totalValue,
                int totalWaitMinutes,
                boolean usedGlobalFallback,
                double futureBonus,
                BeamState parent,
                String windowBucket,
                double navigatedKm,
                double prefixNavigatedKm,
                double lastInboundKm,
                Point legOrigin
        ) {
            this.cursor = cursor;
            this.location = location;
            this.stops = stops;
            this.opportunity = opportunity;
            this.totalValue = totalValue;
            this.totalWaitMinutes = totalWaitMinutes;
            this.usedGlobalFallback = usedGlobalFallback;
            this.futureBonus = futureBonus;
            this.parent = parent;
            this.windowBucket = windowBucket == null ? "none" : windowBucket;
            this.navigatedKm = navigatedKm;
            this.prefixNavigatedKm = prefixNavigatedKm;
            this.lastInboundKm = lastInboundKm;
            this.legOrigin = legOrigin;
        }

        static BeamState initial(Instant start, Point location) {
            return new BeamState(
                    start, location, List.of(), RouteOpportunityState.empty(), 0, 0, false, 0, null, "none",
                    0, 0, 0, null);
        }

        BeamState child(
                PlannedStop stop,
                double increment,
                MacroVisitKind kind,
                UUID identity,
                ZoneFishingPackage pkg,
                boolean fallback,
                Instant nextCursor,
                Point nextLocation,
                String nextWindowBucket
        ) {
            return child(stop, increment, kind, identity, pkg, fallback, nextCursor, nextLocation, nextWindowBucket, null);
        }

        BeamState child(
                PlannedStop stop,
                double increment,
                MacroVisitKind kind,
                UUID identity,
                ZoneFishingPackage pkg,
                boolean fallback,
                Instant nextCursor,
                Point nextLocation,
                String nextWindowBucket,
                NavStamp navigation
        ) {
            if (BeamLayerProfile.enabled()) {
                BeamLayerProfile.stateCopied(stops.size());
            }
            List<PlannedStop> nextStops = new ArrayList<>(stops);
            RouteOpportunityState nextOpp = opportunity;
            double nextValue = totalValue + increment;
            int nextWait = totalWaitMinutes;
            if (kind != null && kind.isExtend() && !stops.isEmpty()) {
                PlannedStop previous = stops.get(stops.size() - 1);
                nextStops.set(nextStops.size() - 1, stop);
                if (kind == MacroVisitKind.EXTEND_CURRENT_ZONE) {
                    nextOpp = opportunity.replaceZoneEntry(identity, previous.fishingPackage(), pkg, stop.arrivalAt());
                }
            } else {
                nextStops.add(stop);
                nextWait = totalWaitMinutes + stop.precedingWaitMinutes();
                if (kind == MacroVisitKind.NEW_ATOMIC) {
                    nextOpp = opportunity.consumeAtomic(identity);
                } else if (kind == MacroVisitKind.NEW_ZONE_VISIT || kind == MacroVisitKind.REVISIT_PARTIALLY_CONSUMED_ZONE) {
                    nextOpp = opportunity.applyZoneEntry(identity, pkg, stop.arrivalAt());
                }
            }
            double nextNavigated = navigatedKm;
            double nextPrefix = prefixNavigatedKm;
            double nextInbound = lastInboundKm;
            Point nextOrigin = legOrigin;
            if (navigation != null && navigation.ready) {
                nextNavigated = navigation.navigatedKm;
                nextPrefix = navigation.prefixNavigatedKm;
                nextInbound = navigation.lastInboundKm;
                nextOrigin = navigation.legOrigin;
                if (BeamLayerProfile.enabled()) {
                    GenerateProfiler.current().count("navigatedKmUpdates");
                    BeamLayerProfile.noteNavigatedUpdate();
                }
            }
            return new BeamState(
                    nextCursor,
                    nextLocation,
                    List.copyOf(nextStops),
                    nextOpp,
                    nextValue,
                    nextWait,
                    usedGlobalFallback || fallback,
                    0,
                    this,
                    nextWindowBucket,
                    nextNavigated,
                    nextPrefix,
                    nextInbound,
                    nextOrigin
            );
        }

        BeamState withFutureBonus(double bonus) {
            return new BeamState(
                    cursor, location, stops, opportunity, totalValue, totalWaitMinutes, usedGlobalFallback, bonus, parent,
                    windowBucket, navigatedKm, prefixNavigatedKm, lastInboundKm, legOrigin);
        }

        BeamState withoutFuture() {
            return futureBonus == 0 ? this : withFutureBonus(0);
        }

        BeamState truncateTo(int stopCount) {
            BeamState state = this;
            while (state != null && state.stops.size() > stopCount) {
                state = state.parent;
            }
            return state == null ? this : state.withoutFuture();
        }

        String locationIdentity() {
            if (stops.isEmpty()) {
                return "launch";
            }
            UUID id = stops.get(stops.size() - 1).opportunityIdentity();
            return id == null ? "unknown" : id.toString();
        }

        boolean sameTemporalOpportunity(BeamState other) {
            return locationIdentity().equals(other.locationIdentity())
                    && opportunity.consumptionFingerprint().equals(other.opportunity.consumptionFingerprint())
                    && windowBucket.equals(other.windowBucket);
        }

        double usedRangeKm() {
            return RoutePlanner.usedRangeKm(stops);
        }

        RouteOpportunityState opportunity() {
            return opportunity;
        }

        List<PlannedStop> stops() {
            return stops;
        }
    }
}

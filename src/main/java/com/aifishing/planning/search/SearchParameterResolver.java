package com.aifishing.planning.search;

import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.FishingVisitOption;
import com.aifishing.planning.spatial.GenerateProfiler;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class SearchParameterResolver {

    static final double MIN_INTER_STOP_MINUTES = 3.0;

    public SearchParameters resolve(
            List<RankedCandidate> ranked,
            List<FishingVisitOption> visitOptions,
            PlanningContext context
    ) {
        PlanningProperties.Search search = context.properties().getSearch();
        PlanningProperties.Schedule schedule = context.properties().getSchedule();
        int n = Math.max(1, visitOptions == null ? 0 : visitOptions.size());
        int d = Math.max(1, schedule.getDwellOptionsMinutes().size());
        StopEstimates estimates = estimateStops(ranked, context, search, schedule);
        int cap = Math.max(1, search.getHardMaxStops());
        int floor = Math.min(Math.max(search.getMinEffectiveStops(), schedule.getMinWaypoints()), cap);
        int effectiveMaxStops = clamp(estimates.maxFeasibleStops(), floor, cap);
        int expectedStops = Math.max(1, estimates.expectedStops());
        int budget = sizedBudget(search, n, d, expectedStops);
        int beam = effectiveBeamWidth(search, schedule, n, d, expectedStops, budget);
        ModeDecision decision = decideMode(search, n, d, expectedStops, beam, budget);
        int lookahead = decision.mode() == SearchMode.FULL_ROUTE
                ? effectiveMaxStops
                : clamp(search.getDefaultLookaheadHorizon(), search.getMinLookaheadHorizon(), search.getMaxLookaheadHorizon());
        lookahead = Math.max(1, Math.min(lookahead, effectiveMaxStops));
        int commit = Math.max(1, search.getCommitPrefixStops());
        SearchDiagnostics diagnostics = new SearchDiagnostics(
                estimates.usableMinutes(),
                estimates.representativeDwellMinutes(),
                estimates.representativeInterStopMinutes(),
                expectedStops,
                estimates.conservativeDwellMinutes(),
                estimates.optimisticInterStopMinutes(),
                estimates.maxFeasibleStops(),
                effectiveMaxStops,
                decision.reason()
        );
        recordDerivation(diagnostics, n);
        return new SearchParameters(
                decision.mode(),
                beam,
                effectiveMaxStops,
                Math.max(1, schedule.getMinWaypoints()),
                lookahead,
                budget,
                commit,
                search.getFuturePotentialLambda(),
                diagnostics,
                search.getMaxWallClockMs()
        );
    }

    public int estimateMaxStops(
            List<RankedCandidate> ranked,
            PlanningContext context,
            PlanningProperties.Search search,
            PlanningProperties.Schedule schedule
    ) {
        return estimateStops(ranked, context, search, schedule).expectedStops();
    }

    public StopEstimates estimateStops(
            List<RankedCandidate> ranked,
            PlanningContext context,
            PlanningProperties.Search search,
            PlanningProperties.Schedule schedule
    ) {
        long tripMinutes = Math.max(1, Duration.between(TripClock.startAt(context), TripClock.endAt(context)).toMinutes());
        int returnReserve = context.accessKnown() ? Math.max(0, schedule.getReturnBufferMinutes()) : 0;
        double cruise = cruiseKmh(context);
        double detour = context.properties().getTravel().getDetourFactor();
        double minSpacingM = Math.max(1, context.properties().getCandidates().getMinSpacingM());
        List<Point> points = candidatePoints(ranked);
        double launchMinutes = 0;
        double launchKm = 0;
        if (context.routeStartPoint() != null && !points.isEmpty()) {
            List<Double> toLaunch = new ArrayList<>();
            for (Point point : points) {
                toLaunch.add(GeoMetrics.distanceM(context.routeStartPoint(), point));
            }
            double launchMeters = median(toLaunch);
            launchKm = launchMeters / 1000.0;
            launchMinutes = minutes(launchMeters, cruise, detour);
        }
        int usable = (int) Math.max(0, tripMinutes - returnReserve - Math.round(launchMinutes));
        int medianDwell = medianInt(schedule.getDwellOptionsMinutes());
        if (medianDwell <= 0) {
            medianDwell = Math.max(1, schedule.getMinSpotMinutes());
        }
        int conservativeDwell = conservativeDwellMinutes(schedule);
        double representativeInterStop = representativeInterStopMinutes(points, cruise, detour, minSpacingM);
        double optimisticInterStop = optimisticInterStopMinutes(points, cruise, detour, minSpacingM);
        double expectedCycle = Math.max(1.0, medianDwell + representativeInterStop);
        double feasibleCycle = Math.max(1.0, conservativeDwell + optimisticInterStop);
        int expectedByTime = (int) Math.floor(usable / expectedCycle);
        int feasibleByTime = (int) Math.floor(usable / feasibleCycle);
        int expectedByRange = Integer.MAX_VALUE;
        int feasibleByRange = Integer.MAX_VALUE;
        Double usableRangeKm = usableRoundTripRangeKm(context);
        if (usableRangeKm != null && usableRangeKm > 0 && cruise > 0) {
            double remainingKm = Math.max(0, usableRangeKm - 2 * launchKm);
            double expectedInterStopKm = representativeInterStop * cruise / 60.0;
            double optimisticInterStopKm = optimisticInterStop * cruise / 60.0;
            expectedByRange = remainingKm <= 0
                    ? (launchKm * 2 <= usableRangeKm ? 1 : 0)
                    : 1 + (expectedInterStopKm <= 0 ? 0 : (int) Math.floor(remainingKm / expectedInterStopKm));
            feasibleByRange = remainingKm <= 0
                    ? (launchKm * 2 <= usableRangeKm ? 1 : 0)
                    : 1 + (optimisticInterStopKm <= 0 ? 0 : (int) Math.floor(remainingKm / optimisticInterStopKm));
        }
        int expectedStops = Math.min(expectedByTime, expectedByRange);
        int maxFeasibleStops = Math.min(feasibleByTime, feasibleByRange);
        return new StopEstimates(
                usable,
                medianDwell,
                representativeInterStop,
                Math.max(0, expectedStops),
                conservativeDwell,
                optimisticInterStop,
                Math.max(0, maxFeasibleStops)
        );
    }

    public int effectiveBeamWidth(
            PlanningProperties.Search search,
            PlanningProperties.Schedule schedule,
            int visitOptionCount,
            int dwellOptionCount,
            int depth,
            int budget
    ) {
        int min = Math.max(1, search.getMinBeamWidth());
        int max = Math.max(min, search.getMaxBeamWidth());
        int fallback = schedule.getBeamWidth() > 0 ? schedule.getBeamWidth() : 8;
        int preferred = search.getDefaultBeamWidth() > 0 ? search.getDefaultBeamWidth() : fallback;
        int n = Math.max(1, visitOptionCount);
        int d = Math.max(1, dwellOptionCount);
        int layers = Math.max(1, depth);
        int best = min;
        for (int width = max; width >= min; width--) {
            if ((long) width * n * d * layers <= budget) {
                best = width;
                break;
            }
        }
        if (preferred >= min && preferred <= best && (long) preferred * n * d * layers <= budget) {
            return best;
        }
        return best;
    }

    public static int adaptiveWidth(int configured, int min, int max, int visitOptionCount, int dwellOptionCount, int remainingDepth, int remainingBudget) {
        int lo = Math.max(1, min);
        int hi = Math.max(lo, max);
        int start = clamp(configured, lo, hi);
        int n = Math.max(1, visitOptionCount);
        int d = Math.max(1, dwellOptionCount);
        int depth = Math.max(1, remainingDepth);
        int best = lo;
        for (int width = start; width >= lo; width--) {
            if ((long) width * n * d * depth <= Math.max(1, remainingBudget)) {
                best = width;
                break;
            }
        }
        return best;
    }

    public static int adaptiveHorizon(int configured, int min, int max, int remainingStops, int visitOptionCount, int dwellOptionCount, int width, int remainingBudget) {
        int horizon = clamp(configured, Math.max(1, min), Math.max(1, max));
        horizon = Math.max(1, Math.min(horizon, Math.max(1, remainingStops)));
        int n = Math.max(1, visitOptionCount);
        int d = Math.max(1, dwellOptionCount);
        int w = Math.max(1, width);
        while (horizon > 1 && (long) w * n * d * horizon > Math.max(1, remainingBudget)) {
            horizon--;
        }
        return horizon;
    }

    public static double cruiseKmh(PlanningContext context) {
        if (context.fishingMode() == FishingMode.SHORE) {
            return context.properties().getTravel().getDefaultShoreKmh();
        }
        if (context.effectiveBoatCapability() != null && context.effectiveBoatCapability().cruiseSpeedKmh() > 0) {
            return context.effectiveBoatCapability().cruiseSpeedKmh();
        }
        return context.properties().getTravel().getDefaultBoatKmh();
    }

    public static int sizedBudget(PlanningProperties.Search search, int visitOptionCount, int dwellOptionCount, int expectedStops) {
        int cap = search.getMaxExpansions() > 0 ? search.getMaxExpansions() : 8_000;
        int n = Math.max(1, visitOptionCount);
        int d = Math.max(1, dwellOptionCount);
        int layers = Math.max(1, expectedStops);
        int targetWidth = Math.max(search.getDefaultBeamWidth(), search.getMinBeamWidth());
        long needed = (long) Math.max(targetWidth, search.getMaxBeamWidth()) * n * d * layers;
        if (needed <= 0) {
            return cap;
        }
        return (int) Math.min(cap, Math.max(8_000, needed));
    }

    public static int macroVisitOptionCap(PlanningContext context) {
        PlanningProperties.Candidates candidates = context.properties().getCandidates();
        PlanningProperties.Search search = context.properties().getSearch();
        PlanningProperties.Schedule schedule = context.properties().getSchedule();
        int configured = candidates.getMaxMacroVisitOptions();
        int d = Math.max(1, schedule.getDwellOptionsMinutes() == null ? 1 : schedule.getDwellOptionsMinutes().size());
        int layers = Math.max(1, Math.min(search.getHardMaxStops(), Math.max(2, search.getMinEffectiveStops())));
        int width = Math.max(search.getDefaultBeamWidth(), search.getMinBeamWidth());
        int budget = search.getMaxExpansions() > 0 ? search.getMaxExpansions() : 20_000;
        int derived = Math.max(8, budget / Math.max(1, d * layers * Math.max(1, width)));
        return Math.max(8, Math.min(configured, derived));
    }

    public static ModeDecision decideMode(
            PlanningProperties.Search search,
            int visitOptionCount,
            int dwellOptionCount,
            int expectedStops,
            int beamWidth,
            int budget
    ) {
        int n = Math.max(1, visitOptionCount);
        int d = Math.max(1, dwellOptionCount);
        int layers = Math.max(1, expectedStops);
        int minBeam = Math.max(1, search.getMinBeamWidth());
        int desiredWidth = Math.max(search.getDefaultBeamWidth(), minBeam);
        long predictedDesired = (long) desiredWidth * n * d * layers;
        long predictedMin = (long) minBeam * n * d * layers;
        long candidateLayer = (long) n * d;
        if (predictedDesired <= budget) {
            return new ModeDecision(SearchMode.FULL_ROUTE, SearchModeReason.FULL_ROUTE);
        }
        if (candidateLayer > 500 || n > 80) {
            return new ModeDecision(SearchMode.ROLLING_HORIZON, SearchModeReason.ROLLING_CANDIDATE_SPACE_TOO_LARGE);
        }
        if (predictedMin > budget) {
            return new ModeDecision(SearchMode.ROLLING_HORIZON, SearchModeReason.ROLLING_EXPANSION_ESTIMATE_TOO_HIGH);
        }
        if ((long) beamWidth * n * d * layers > budget) {
            return new ModeDecision(SearchMode.ROLLING_HORIZON, SearchModeReason.ROLLING_FULL_ROUTE_BUDGET_EXCEEDED);
        }
        return new ModeDecision(SearchMode.FULL_ROUTE, SearchModeReason.FULL_ROUTE);
    }

    private static void recordDerivation(SearchDiagnostics diagnostics, int visitOptionCount) {
        GenerateProfiler profiler = GenerateProfiler.current();
        profiler.set("usableMinutes", diagnostics.usableMinutes());
        profiler.set("representativeDwellMinutes", diagnostics.representativeDwellMinutes());
        profiler.set("representativeInterStopMinutes", Math.round(diagnostics.representativeInterStopMinutes()));
        profiler.set("expectedStops", diagnostics.expectedStops());
        profiler.set("conservativeDwellMinutes", diagnostics.conservativeDwellMinutes());
        profiler.set("optimisticInterStopMinutes", Math.round(diagnostics.optimisticInterStopMinutes()));
        profiler.set("maxFeasibleStops", diagnostics.maxFeasibleStops());
        profiler.set("effectiveMaxStops", diagnostics.effectiveMaxStops());
        profiler.set("macroVisitOptionCount", visitOptionCount);
        profiler.tag("searchModeReason", diagnostics.modeReason().name());
    }

    private static List<Point> candidatePoints(List<RankedCandidate> ranked) {
        List<Point> points = new ArrayList<>();
        if (ranked == null) {
            return points;
        }
        for (RankedCandidate candidate : ranked) {
            if (candidate != null && candidate.spot() != null && candidate.spot().getLocation() != null) {
                points.add(candidate.spot().getLocation());
            }
        }
        return points;
    }

    private static int conservativeDwellMinutes(PlanningProperties.Schedule schedule) {
        int smallest = smallestPositive(schedule.getDwellOptionsMinutes());
        if (smallest > 0) {
            return smallest;
        }
        return Math.max(1, schedule.getMinSpotMinutes());
    }

    private static Double usableRoundTripRangeKm(PlanningContext context) {
        if (context.fishingMode() != FishingMode.BOAT) {
            return null;
        }
        EffectiveBoatCapability capability = context.effectiveBoatCapability();
        if (capability == null || !capability.rangeEnforced() || capability.effectiveUsableRangeKm() == null) {
            return null;
        }
        return capability.effectiveUsableRangeKm() > 0 ? capability.effectiveUsableRangeKm() : null;
    }

    private static double representativeInterStopMinutes(List<Point> points, double cruise, double detour, double minSpacingM) {
        double floor = interStopFloorMinutes(cruise, detour, minSpacingM);
        List<Double> pairwise = pairwiseMeters(points);
        if (pairwise.isEmpty()) {
            return floor;
        }
        return Math.max(floor, minutes(median(pairwise), cruise, detour));
    }

    private static double optimisticInterStopMinutes(List<Point> points, double cruise, double detour, double minSpacingM) {
        double floor = interStopFloorMinutes(cruise, detour, minSpacingM);
        List<Double> pairwise = pairwiseMeters(points);
        if (pairwise.isEmpty()) {
            return floor;
        }
        return Math.max(floor, minutes(percentile(pairwise, 0.25), cruise, 1.0));
    }

    private static double interStopFloorMinutes(double cruise, double detour, double minSpacingM) {
        return Math.max(MIN_INTER_STOP_MINUTES, minutes(Math.max(150, minSpacingM), cruise, detour));
    }

    private static List<Double> pairwiseMeters(List<Point> points) {
        if (points.size() < 2) {
            return List.of();
        }
        List<Double> pairwise = new ArrayList<>();
        int cap = Math.min(points.size(), 24);
        for (int i = 0; i < cap; i++) {
            for (int j = i + 1; j < cap; j++) {
                pairwise.add(GeoMetrics.distanceM(points.get(i), points.get(j)));
            }
        }
        return pairwise;
    }

    private static double minutes(double meters, double cruiseKmh, double detour) {
        double speed = cruiseKmh <= 0 ? 12 : cruiseKmh;
        double factor = detour <= 0 ? 1.35 : detour;
        return meters * factor / (speed * 1000.0 / 60.0);
    }

    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        List<Double> copy = new ArrayList<>(values);
        Collections.sort(copy);
        int mid = copy.size() / 2;
        if (copy.size() % 2 == 0) {
            return (copy.get(mid - 1) + copy.get(mid)) / 2.0;
        }
        return copy.get(mid);
    }

    private static double percentile(List<Double> values, double fraction) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        List<Double> copy = new ArrayList<>(values);
        Collections.sort(copy);
        int index = (int) Math.floor((copy.size() - 1) * Math.max(0, Math.min(1, fraction)));
        return copy.get(index);
    }

    private static int medianInt(List<Integer> values) {
        List<Integer> copy = positiveInts(values);
        if (copy.isEmpty()) {
            return 0;
        }
        Collections.sort(copy);
        return copy.get(copy.size() / 2);
    }

    private static int smallestPositive(List<Integer> values) {
        int smallest = 0;
        for (Integer value : positiveInts(values)) {
            if (smallest == 0 || value < smallest) {
                smallest = value;
            }
        }
        return smallest;
    }

    private static List<Integer> positiveInts(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Integer> copy = new ArrayList<>();
        for (Integer value : values) {
            if (value != null && value > 0) {
                copy.add(value);
            }
        }
        return copy;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record StopEstimates(
            int usableMinutes,
            int representativeDwellMinutes,
            double representativeInterStopMinutes,
            int expectedStops,
            int conservativeDwellMinutes,
            double optimisticInterStopMinutes,
            int maxFeasibleStops
    ) {
    }

    public record ModeDecision(SearchMode mode, SearchModeReason reason) {
    }
}

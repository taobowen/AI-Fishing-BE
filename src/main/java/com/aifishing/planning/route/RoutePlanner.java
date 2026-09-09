package com.aifishing.planning.route;

import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.environment.BoatWeatherPenalty;
import com.aifishing.planning.environment.LocalOrientation;
import com.aifishing.planning.environment.LocalOrientationService;
import com.aifishing.planning.environment.TimeAdjustedSpotUtility;
import com.aifishing.planning.environment.TimeIndexedWeather;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.environment.WhyThisTimeExplainer;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.FishingVisitOption;
import com.aifishing.planning.spatial.GenerateProfiler;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            ZoneSubPlanner zoneSubPlanner
    ) {
        this.travelTimeEstimator = travelTimeEstimator;
        this.timeAdjustedSpotUtility = timeAdjustedSpotUtility;
        this.orientationService = orientationService;
        this.boatWeatherPenalty = boatWeatherPenalty;
        this.visitOptionFactory = visitOptionFactory;
        this.spatialUtility = spatialUtility;
        this.zoneSubPlanner = zoneSubPlanner;
    }

    public RouteResult plan(List<RankedCandidate> ranked, PlanningContext context) {
        PlanningProperties.Schedule schedule = context.properties().getSchedule();
        Instant tripStart = TripClock.startAt(context);
        Instant tripEnd = TripClock.endAt(context);
        int returnBuffer = context.accessKnown() ? schedule.getReturnBufferMinutes() : 0;
        TimeIndexedWeather weather = TimeIndexedWeather.from(context.weather(), TripClock.zoneId(context));
        Map<UUID, LocalOrientation> orientations = new HashMap<>();
        for (RankedCandidate candidate : ranked) {
            orientations.put(candidate.spot().planningIdentity(), orientationService.resolve(candidate.spot(), context.geometry()));
        }
        List<FishingVisitOption> visitOptions = visitOptionFactory.options(ranked, context.properties().getSpatial());

        List<BeamState> beam = new ArrayList<>();
        beam.add(BeamState.initial(tripStart, context.accessKnown() ? context.routeStartPoint() : null));
        List<BeamState> completed = new ArrayList<>();
        int width = schedule.getBeamWidth();

        for (int depth = 0; depth < schedule.getMaxWaypoints(); depth++) {
            List<BeamState> next = new ArrayList<>();
            for (BeamState state : beam) {
                if (state.stops.size() >= schedule.getMinWaypoints()) {
                    completed.add(state);
                }
                if (state.stops.size() >= schedule.getMaxWaypoints()) {
                    continue;
                }
                next.addAll(expand(state, visitOptions, context, weather, orientations, schedule, tripEnd, returnBuffer));
                GenerateProfiler.current().count("beamExpandedStates", 1);
            }
            if (next.isEmpty()) {
                break;
            }
            next.sort(BeamState.ORDER);
            beam = next.size() <= width ? next : new ArrayList<>(next.subList(0, width));
        }
        completed.addAll(beam);
        completed.sort(BeamState.ORDER);
        BeamState best = pickBest(completed, schedule.getMinWaypoints());
        if (best == null || best.stops.isEmpty()) {
            return new RouteResult(List.of(), false, tripStart, tripStart, TravelEstimate.zero(), List.of(), 0, 0, 0, 0);
        }
        return toResult(best, context, weather, tripStart, tripEnd, returnBuffer);
    }

    private List<BeamState> expand(
            BeamState state,
            List<FishingVisitOption> visitOptions,
            PlanningContext context,
            TimeIndexedWeather weather,
            Map<UUID, LocalOrientation> orientations,
            PlanningProperties.Schedule schedule,
            Instant tripEnd,
            int returnBuffer
    ) {
        List<Integer> waits = new ArrayList<>();
        waits.add(0);
        for (Integer option : schedule.getWaitOptionsMinutes()) {
            if (option != null && option > 0 && state.totalWaitMinutes + option <= schedule.getMaxTotalWaitMinutes()) {
                waits.add(option);
            }
        }
        List<BeamState> expansions = new ArrayList<>();
        for (int waitMinutes : waits) {
            Instant depart = state.cursor.plus(Duration.ofMinutes(waitMinutes));
            double waitPenalty = waitMinutes == 0
                    ? 0
                    : schedule.getWaitPenalty() * (waitMinutes / (double) Math.max(1, schedule.getSlotMinutes()));
            for (FishingVisitOption option : visitOptions) {
                RankedCandidate candidate = option.candidate();
                UUID featureId = candidate.spot().planningIdentity();
                if (option.coverageIds().stream().anyMatch(state.used::contains)) {
                    continue;
                }
                if (!spacingOk(candidate, state.stops, context.properties().getCandidates().getMinSpacingM())) {
                    continue;
                }
                Point entry = option.entryPoint();
                Point exit = option.exitPoint();
                TravelEstimate travel = travelFrom(state.location, entry, context, state.stops.isEmpty());
                if (travel.unknownTravel() && state.location != null) {
                    continue;
                }
                Instant arrival = depart.plusSeconds(Math.round(travel.minutes() * 60));
                Instant travelEnd = arrival;
                if (boatWeatherPenalty.travelIntervalHardReject(weather, depart, travelEnd, context)) {
                    continue;
                }
                Instant remainingAfterArrival = tripEnd;
                long remainingMin = Math.max(0, Duration.between(arrival, remainingAfterArrival).toMinutes() - returnBuffer);
                if (context.accessKnown()) {
                    TravelEstimate homeProbe = travelTimeEstimator.estimate(exit, context.routeStartPoint(), context);
                    remainingMin = Math.max(0, remainingMin - Math.round(homeProbe.minutes()));
                }
                for (int dwell : DwellPolicy.options(candidate, (int) remainingMin, schedule)) {
                    Instant departure = arrival.plus(Duration.ofMinutes(dwell));
                    if (departure.isAfter(tripEnd)) {
                        continue;
                    }
                    TravelEstimate home = TravelEstimate.zero();
                    if (context.accessKnown()) {
                        home = travelTimeEstimator.estimate(exit, context.routeStartPoint(), context);
                        Instant returnAt = departure.plusSeconds(Math.round(home.minutes() * 60) + returnBuffer * 60L);
                        if (returnAt.isAfter(tripEnd)) {
                            continue;
                        }
                        if (boatWeatherPenalty.travelIntervalHardReject(weather, departure, departure.plusSeconds(Math.round(home.minutes() * 60)), context)) {
                            continue;
                        }
                    }
                    if (legTooLong(travel, context) || rangeExceeded(state.stops, travel, home, context)) {
                        continue;
                    }
                    LocalOrientation orientation = orientations.getOrDefault(featureId, LocalOrientation.unknown());
                    VisitEvaluation visit = evaluateVisit(option, candidate, arrival, dwell, context, weather, orientation);
                    if (visit == null) {
                        continue;
                    }
                    TimeAdjustedSpotUtility.Evaluation atArrival = timeAdjustedSpotUtility.evaluateAt(
                            candidate, arrival, entry, context, weather, orientation, waitPenalty);
                    if (boatWeatherPenalty.hardReject(atArrival.weather(), context)) {
                        continue;
                    }
                    double proximity = 0;
                    if (!state.stops.isEmpty() && state.location != null && entry != null) {
                        double meters = GeoMetrics.distanceM(state.location, entry);
                        proximity = 1.0 - Math.min(1.0, meters / 4000.0);
                    }
                    double landPenalty = travel.landCrossingDetected() ? 0.15 : 0;
                    double travelCost = 0.04 * Math.min(1.0, travel.minutes() / 30.0);
                    double increment = visit.utility() + 0.12 * proximity - landPenalty - travelCost - waitPenalty
                            - atArrival.breakdown().boatWeatherPenalty();
                    String waitLocation = waitMinutes == 0
                            ? null
                            : (state.stops.isEmpty() ? "LAUNCH" : "CURRENT");
                    PlannedStop stop = new PlannedStop(
                            candidate,
                            arrival,
                            departure,
                            dwell,
                            travel,
                            atArrival.breakdown(),
                            List.of(),
                            atArrival.environment().toMap(),
                            waitMinutes,
                            waitLocation,
                            option,
                            visit.subPlan(),
                            visit.fishingMinutes(),
                            visit.internalTransitMinutes(),
                            visit.waitMinutes()
                    );
                    boolean fallback = outsideGeneratedWindow(candidate, arrival, context);
                    expansions.add(state.child(stop, increment, waitMinutes, waitLocation, waitPenalty, fallback, departure, exit));
                }
            }
        }
        return expansions;
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
                return null;
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

    private BeamState pickBest(List<BeamState> completed, int minWaypoints) {
        BeamState best = null;
        for (BeamState state : completed) {
            if (state.stops.isEmpty()) {
                continue;
            }
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
        return best;
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
            LocalOrientation orientation = orientationService.resolve(stop.candidate().spot(), context.geometry());
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
            PlannedStop explainedStop = new PlannedStop(
                    stop.candidate(),
                    stop.arrivalAt(),
                    stop.departureAt(),
                    stop.stayMinutes(),
                    stop.fromPrevious(),
                    stop.timeScore(),
                    why.lines(),
                    environment,
                    stop.precedingWaitMinutes(),
                    stop.precedingWaitLocation(),
                    stop.visitOption(),
                    stop.zoneSubPlan(),
                    stop.plannedFishingMinutes(),
                    stop.plannedInternalTransitMinutes(),
                    stop.plannedWaitMinutes()
            );
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
                reserve
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

    private boolean spacingOk(RankedCandidate candidate, List<PlannedStop> chosen, double minSpacingM) {
        for (PlannedStop stop : chosen) {
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
            List<PlannedStop> chosen,
            TravelEstimate toCandidate,
            TravelEstimate home,
            PlanningContext context
    ) {
        if (context.fishingMode() != FishingMode.BOAT
                || context.effectiveBoatCapability() == null
                || !context.effectiveBoatCapability().rangeEnforced()
                || context.effectiveBoatCapability().effectiveUsableRangeKm() == null) {
            return false;
        }
        double usedKm = chosen.stream()
                .map(PlannedStop::fromPrevious)
                .filter(estimate -> estimate != null && !estimate.unknownTravel())
                .mapToDouble(RoutePlanner::appliedKm)
                .sum();
        double toCandidateKm = appliedKm(toCandidate);
        double homeKm = appliedKm(home);
        double remainingKm = context.effectiveBoatCapability().effectiveUsableRangeKm() - usedKm - toCandidateKm;
        return usedKm + toCandidateKm + homeKm > context.effectiveBoatCapability().effectiveUsableRangeKm()
                || remainingKm < homeKm;
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
            int scheduleReserveMinutes
    ) {
        public RouteResult(List<PlannedStop> stops, boolean usedGlobalFallback) {
            this(stops, usedGlobalFallback, null, null, TravelEstimate.zero(), List.of(), 0, 0, 0, 0);
        }
    }

    private static final class BeamState {
        private static final Comparator<BeamState> ORDER = Comparator
                .comparingDouble((BeamState state) -> -state.totalValue)
                .thenComparingInt(state -> -state.stops.size())
                .thenComparing(state -> state.stops.isEmpty()
                        ? ""
                        : state.stops.get(0).candidate().spot().getFeatureId().toString());

        private final Instant cursor;
        private final Point location;
        private final List<PlannedStop> stops;
        private final Set<UUID> used;
        private final double totalValue;
        private final int totalWaitMinutes;
        private final boolean usedGlobalFallback;

        private BeamState(
                Instant cursor,
                Point location,
                List<PlannedStop> stops,
                Set<UUID> used,
                double totalValue,
                int totalWaitMinutes,
                boolean usedGlobalFallback
        ) {
            this.cursor = cursor;
            this.location = location;
            this.stops = stops;
            this.used = used;
            this.totalValue = totalValue;
            this.totalWaitMinutes = totalWaitMinutes;
            this.usedGlobalFallback = usedGlobalFallback;
        }

        static BeamState initial(Instant start, Point location) {
            return new BeamState(start, location, List.of(), Set.of(), 0, 0, false);
        }

        BeamState child(
                PlannedStop stop,
                double increment,
                int waitMinutes,
                String waitLocation,
                double waitPenalty,
                boolean fallback,
                Instant nextCursor,
                Point nextLocation
        ) {
            List<PlannedStop> nextStops = new ArrayList<>(stops);
            nextStops.add(stop);
            Set<UUID> nextUsed = new HashSet<>(used);
            nextUsed.addAll(stop.candidate().spot().coverageIds());
            return new BeamState(
                    nextCursor,
                    nextLocation,
                    List.copyOf(nextStops),
                    Set.copyOf(nextUsed),
                    totalValue + increment,
                    totalWaitMinutes + waitMinutes,
                    usedGlobalFallback || fallback
            );
        }
    }
}

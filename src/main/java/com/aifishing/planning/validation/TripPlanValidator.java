package com.aifishing.planning.validation;

import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.filter.RegulationFilter;
import com.aifishing.planning.filter.RejectionReason;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.route.PlannedStopMembers;
import com.aifishing.planning.route.RouteOpportunityState;
import com.aifishing.planning.route.RoutePlanner;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.TargetKind;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class TripPlanValidator {

    private final RegulationFilter regulationFilter;

    public TripPlanValidator(RegulationFilter regulationFilter) {
        this.regulationFilter = regulationFilter;
    }

    public String validate(List<PlannedStop> stops, PlanningContext context) {
        return validate(stops, context, null);
    }

    public String validate(List<PlannedStop> stops, PlanningContext context, RoutePlanner.RouteResult route) {
        if (stops == null || stops.isEmpty()) {
            return "NO_CANDIDATES";
        }
        Instant tripStart = TripClock.startAt(context);
        Instant tripEnd = TripClock.endAt(context);
        Set<UUID> atomicVisits = new HashSet<>();
        Map<UUID, List<PlannedStop>> zoneVisits = new LinkedHashMap<>();
        Instant previousDeparture = null;
        int totalWait = 0;
        for (PlannedStop stop : stops) {
            RankedCandidate ranked = stop.candidate();
            if (ranked.spot().planningIdentity() == null) {
                return "VALIDATION_FAILED: missing planning identity";
            }
            String visitError = recordVisit(stop, ranked.spot(), atomicVisits, zoneVisits);
            if (visitError != null) {
                return visitError;
            }
            if (ranked.spot().getTargetKind() == TargetKind.AREA) {
                return "VALIDATION_FAILED: executable AREA is not allowed";
            }
            if (!context.geometry().validFishingPoint(ranked.spot().getEntryPoint())
                    && !context.geometry().validFishingPoint(ranked.spot().getLocation())) {
                return "VALIDATION_FAILED: waypoint not in water";
            }
            if (ranked.spot().getTargetKind() != TargetKind.ZONE) {
                if (ranked.spot().getPipeline() != context.strategyRun().getFeaturePipeline()
                        || !java.util.Objects.equals(
                        ranked.spot().getAnalysisVersion(),
                        context.strategyRun().getFeatureAnalysisVersion())) {
                    return "VALIDATION_FAILED: snapshot mismatch";
                }
            } else if (ranked.spot().getPipeline() != context.strategyRun().getFeaturePipeline()
                    || !java.util.Objects.equals(
                    ranked.spot().getAnalysisVersion(),
                    context.strategyRun().getFeatureAnalysisVersion())) {
                return "VALIDATION_FAILED: snapshot mismatch";
            }
            double score = ranked.score().finalScore();
            if (score < 0 || score > 1) {
                return "VALIDATION_FAILED: score out of range";
            }
            var regulation = regulationFilter.apply(ranked.spot(), context);
            if (!regulation.accepted() && (regulation.reason() == RejectionReason.REGULATION_WHOLE_AREA
                    || regulation.reason() == RejectionReason.REGULATION_PRIMARY_SPECIES)) {
                return "VALIDATION_FAILED: regulation";
            }
            if (stop.arrivalAt() == null || stop.departureAt() == null) {
                return "VALIDATION_FAILED: missing times";
            }
            if (stop.arrivalAt().isBefore(tripStart) || stop.departureAt().isAfter(tripEnd)) {
                return "VALIDATION_FAILED: outside trip window";
            }
            if (!stop.departureAt().isAfter(stop.arrivalAt())) {
                return "VALIDATION_FAILED: overlapping stay";
            }
            long dwell = Duration.between(stop.arrivalAt(), stop.departureAt()).toMinutes();
            if (dwell != stop.stayMinutes()) {
                return "VALIDATION_FAILED: dwell mismatch";
            }
            if (previousDeparture != null && stop.arrivalAt().isBefore(previousDeparture)) {
                return "VALIDATION_FAILED: unordered times";
            }
            previousDeparture = stop.departureAt();
            totalWait += Math.max(0, stop.precedingWaitMinutes());
        }
        String repeatedZoneError = validateRepeatedZonePackages(zoneVisits);
        if (repeatedZoneError != null) {
            return repeatedZoneError;
        }
        if (totalWait > context.properties().getSchedule().getMaxTotalWaitMinutes()) {
            return "VALIDATION_FAILED: wait cap exceeded";
        }
        if (route != null) {
            if (route.plannedReturnAt() != null && route.plannedReturnAt().isAfter(tripEnd)) {
                return "VALIDATION_FAILED: return after deadline";
            }
            if (route.plannedLaunchDepartureAt() != null && route.plannedLaunchDepartureAt().isBefore(tripStart)) {
                return "VALIDATION_FAILED: launch before trip start";
            }
        }
        return null;
    }

    private static String recordVisit(
            PlannedStop stop,
            CandidateSpot spot,
            Set<UUID> atomicVisits,
            Map<UUID, List<PlannedStop>> zoneVisits
    ) {
        if (spot.getTargetKind() == TargetKind.ZONE) {
            UUID zoneId = RouteOpportunityState.zoneIdentity(spot);
            if (zoneId == null) {
                return "VALIDATION_FAILED: missing planning identity";
            }
            zoneVisits.computeIfAbsent(zoneId, ignored -> new ArrayList<>()).add(stop);
            return null;
        }
        UUID visitId = spot.planningIdentity();
        if (!atomicVisits.add(visitId)) {
            return "VALIDATION_FAILED: duplicate visit";
        }
        return null;
    }

    /**
     * Repeated physical zones must have present, pairwise-disjoint package members.
     * Missing membership fails; the overlap check is never skipped when membership is present.
     */
    private static String validateRepeatedZonePackages(Map<UUID, List<PlannedStop>> zoneVisits) {
        for (List<PlannedStop> visits : zoneVisits.values()) {
            if (visits.size() < 2) {
                continue;
            }
            List<Set<UUID>> packages = new ArrayList<>(visits.size());
            for (PlannedStop visit : visits) {
                List<UUID> members = PlannedStopMembers.packageMemberIds(visit);
                if (members.isEmpty()) {
                    return "VALIDATION_FAILED: repeated zone missing packages";
                }
                packages.add(new LinkedHashSet<>(members));
            }
            for (int i = 0; i < packages.size(); i++) {
                for (int j = i + 1; j < packages.size(); j++) {
                    if (!Collections.disjoint(packages.get(i), packages.get(j))) {
                        return "VALIDATION_FAILED: overlapping zone package";
                    }
                }
            }
        }
        return null;
    }
}

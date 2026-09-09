package com.aifishing.planning.validation;

import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.filter.RegulationFilter;
import com.aifishing.planning.filter.RejectionReason;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.route.RoutePlanner;
import com.aifishing.planning.service.PlanningContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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
        Set<UUID> features = new HashSet<>();
        Instant previousDeparture = null;
        int totalWait = 0;
        for (PlannedStop stop : stops) {
            RankedCandidate ranked = stop.candidate();
            if (ranked.spot().planningIdentity() == null) {
                return "VALIDATION_FAILED: missing planning identity";
            }
            UUID visitId = visitIdentity(ranked.spot());
            if (!features.add(visitId)) {
                return "VALIDATION_FAILED: duplicate visit";
            }
            if (ranked.spot().getTargetKind() == com.aifishing.planning.spatial.TargetKind.AREA) {
                return "VALIDATION_FAILED: executable AREA is not allowed";
            }
            if (!context.geometry().validFishingPoint(ranked.spot().getEntryPoint())
                    && !context.geometry().validFishingPoint(ranked.spot().getLocation())) {
                return "VALIDATION_FAILED: waypoint not in water";
            }
            if (ranked.spot().getTargetKind() != com.aifishing.planning.spatial.TargetKind.ZONE) {
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

    private static UUID visitIdentity(CandidateSpot spot) {
        return spot.planningIdentity();
    }
}

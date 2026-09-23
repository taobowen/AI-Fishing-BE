package com.aifishing.planning.route;

import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.planning.environment.TimeAdjustedSpotUtility;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.service.PlanningContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-shot depth-0 beam trace. Enabled only with DEPTH0_DIAG=true.
 * It records decisions that already happened and does not change them.
 */
final class DepthZeroDiagnostic {

    private static final Logger log = LoggerFactory.getLogger(DepthZeroDiagnostic.class);
    private static final boolean ENABLED = Boolean.parseBoolean(System.getenv().getOrDefault("DEPTH0_DIAG", "false"));
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();

    private DepthZeroDiagnostic() {
    }

    static void begin(List<RankedCandidate> ranked, PlanningContext context) {
        if (!ENABLED) {
            return;
        }
        Trace trace = new Trace();
        trace.lakeId = context.lake() == null ? null : String.valueOf(context.lake().getId());
        trace.boat = context.effectiveBoatCapability() == null
                ? Map.of()
                : context.effectiveBoatCapability().snapshot();
        for (RankedCandidate candidate : ranked) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("spotId", String.valueOf(candidate.spot().getFeatureId()));
            row.put("zoneId", candidate.spot().getZoneId() == null ? null : candidate.spot().getZoneId().toString());
            row.put("targetKind", String.valueOf(candidate.spot().getTargetKind()));
            row.put("featureType", candidate.spot().getType() == null ? null : candidate.spot().getType().name());
            row.put("rawScore", candidate.score().finalScore());
            ScoreBreakdown breakdown = candidate.score().breakdown();
            if (breakdown != null) {
                row.put("strategyMatch", breakdown.strategyMatch());
                row.put("depthMatch", breakdown.depthMatch());
                row.put("featureConfidence", breakdown.featureConfidence());
                row.put("timeWindowMatch", breakdown.timeWindowMatch());
                row.put("gearCompatibility", breakdown.gearCompatibility());
                row.put("weatherCompatibility", breakdown.weatherCompatibility());
                row.put("travelAccess", breakdown.travelAccess());
            }
            trace.shortlist.add(row);
        }
        CURRENT.set(trace);
    }

    static boolean enabled() {
        return ENABLED && CURRENT.get() != null;
    }

    static void range(
            RankedCandidate candidate,
            MacroVisitKind kind,
            int dwellMinutes,
            TravelEstimate outbound,
            TravelEstimate home,
            double localKm,
            PlanningContext context
    ) {
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        EffectiveBoatCapability boat = context.effectiveBoatCapability();
        double outKm = appliedKm(outbound);
        double homeKm = appliedKm(home);
        double limit = boat == null || boat.effectiveUsableRangeKm() == null ? Double.NaN : boat.effectiveUsableRangeKm();
        Map<String, Object> row = identity(candidate, kind, dwellMinutes);
        row.put("outcome", "RANGE_EXCEEDED");
        row.put("distanceBasis", "decision uses geodesic lower bound, then cached water path; appliedKm below is not the reject distance");
        row.put("geodesicM", outbound.distanceM());
        row.put("detourFactor", outbound.appliedDetourFactor());
        row.put("landCrossing", outbound.landCrossingDetected());
        row.put("outboundAppliedKm", outKm);
        row.put("homeGeodesicM", home.distanceM());
        row.put("homeDetourFactor", home.appliedDetourFactor());
        row.put("homeLandCrossing", home.landCrossingDetected());
        row.put("homeAppliedKm", homeKm);
        row.put("localKm", localKm);
        row.put("usedKm", 0);
        row.put("remainingRangeKm", limit);
        row.put("requiredKm", outKm + localKm + homeKm);
        row.put("overKm", outKm + localKm + homeKm - limit);
        trace.rows.add(row);
    }

    static void score(
            RankedCandidate candidate,
            MacroVisitKind kind,
            int dwellMinutes,
            double visitUtility,
            TravelEstimate travel,
            double waitPenalty,
            TimeAdjustedSpotUtility.Evaluation atArrival,
            double increment,
            double stateScore
    ) {
        Trace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        ScoreBreakdown breakdown = atArrival.breakdown();
        double weatherPenalty = 0;
        double landPenalty = travel.landCrossingDetected() ? 0.15 : 0;
        double travelCost = 0.04 * Math.min(1.0, travel.minutes() / 30.0);
        double fishing = visitUtility;
        Map<String, Object> row = identity(candidate, kind, dwellMinutes);
        row.put("outcome", increment <= 0 ? "NON_POSITIVE_INCREMENT" : "ACCEPTED");
        row.put("rawScore", candidate.score().finalScore());
        row.put("incrementalScore", increment);
        row.put("stateScore", stateScore);
        row.put("visitUtility", visitUtility);
        row.put("fishingTerm", fishing);
        row.put("proximityTerm", 0);
        row.put("landPenalty", landPenalty);
        row.put("travelCost", travelCost);
        row.put("waitPenalty", waitPenalty);
        row.put("weatherPenalty", weatherPenalty);
        row.put("arrivalUtility", atArrival.utility());
        if (breakdown != null) {
            row.put("strategyMatch", breakdown.strategyMatch());
            row.put("depthMatch", breakdown.depthMatch());
            row.put("featureConfidence", breakdown.featureConfidence());
            row.put("timeWindowMatch", breakdown.timeWindowMatch());
            row.put("gearCompatibility", breakdown.gearCompatibility());
            row.put("weatherCompatibility", breakdown.weatherCompatibility());
            row.put("travelAccess", breakdown.travelAccess());
            row.put("intrinsic", breakdown.intrinsic());
            row.put("strategyTimeEffect", breakdown.strategyTimeEffect());
            row.put("solarInfluenceStrength", breakdown.solarInfluenceStrength());
            row.put("orientationExposure", breakdown.orientationExposure());
            row.put("solarFishingEffect", breakdown.solarFishingEffect());
            row.put("windOrientation", breakdown.windOrientation());
            row.put("windFishingEffect", breakdown.windFishingEffect());
            row.put("temperatureEffect", breakdown.temperatureEffect());
            row.put("boatWeatherPenalty", breakdown.boatWeatherPenalty());
            row.put("finalTimeAdjustedUtility", breakdown.finalTimeAdjustedUtility());
        }
        trace.rows.add(row);
    }

    static void flush() {
        if (!ENABLED) {
            return;
        }
        Trace trace = CURRENT.get();
        CURRENT.remove();
        if (trace == null) {
            return;
        }
        try {
            Path path = Path.of("target", "depth0-" + (trace.lakeId == null ? "unknown" : trace.lakeId) + ".json");
            Files.createDirectories(path.getParent());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lakeId", trace.lakeId);
            body.put("boat", trace.boat);
            body.put("shortlist", trace.shortlist);
            body.put("depth0", trace.rows);
            Files.writeString(path, JSON.writeValueAsString(body));
            log.info("DEPTH0_DIAG wrote {} shortlist={} depth0={}", path.toAbsolutePath(), trace.shortlist.size(), trace.rows.size());
        } catch (Exception ex) {
            log.warn("DEPTH0_DIAG failed: {}", ex.toString());
        }
    }

    private static Map<String, Object> identity(
            RankedCandidate candidate,
            MacroVisitKind kind,
            int dwellMinutes
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("spotId", String.valueOf(candidate.spot().getFeatureId()));
        row.put("zoneId", candidate.spot().getZoneId() == null ? null : candidate.spot().getZoneId().toString());
        row.put("targetKind", String.valueOf(candidate.spot().getTargetKind()));
        row.put("featureType", candidate.spot().getType() == null ? null : candidate.spot().getType().name());
        row.put("visitKind", kind == null ? null : kind.name());
        row.put("dwellMinutes", dwellMinutes);
        return row;
    }

    private static double appliedKm(TravelEstimate travel) {
        if (travel == null || travel.unknownTravel()) {
            return 0;
        }
        return travel.distanceM() * travel.appliedDetourFactor() / 1000.0;
    }

    private static final class Trace {
        private String lakeId;
        private Map<String, Object> boat = Map.of();
        private final List<Map<String, Object>> shortlist = new ArrayList<>();
        private final List<Map<String, Object>> rows = new ArrayList<>();
    }
}

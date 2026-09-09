package com.aifishing.planning.ranking;

import com.aifishing.common.geo.WaterDepth;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.feedback.ranking.EmpiricalEvidence;
import com.aifishing.common.enums.GearType;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.domain.DepthRange;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class SpotRankingService {

    public SpotScore score(CandidateSpot candidate, PlanningContext context, DepthRange windowDepth) {
        return score(candidate, context, windowDepth, EmpiricalEvidence.none());
    }

    public SpotScore score(
            CandidateSpot candidate,
            PlanningContext context,
            DepthRange windowDepth,
            EmpiricalEvidence empirical
    ) {
        PlanningProperties.Ranking weights = context.properties().getRanking();
        EmpiricalEvidence evidence = empirical == null ? EmpiricalEvidence.none() : empirical;
        ScoreBreakdown breakdown = new ScoreBreakdown(
                clamp(candidate.getStrategyWeight()),
                depthMatch(candidate, windowDepth, context.properties().getCandidates().getFallbackDepthToleranceM()),
                clamp(candidate.getFeatureConfidence() == null ? 0.5 : candidate.getFeatureConfidence()),
                candidate.isWindowSpecific() ? 1.0 : 0.55,
                gearCompatibility(candidate, context.gearTypes()),
                0.5,
                travelAccess(candidate, context),
                evidence.historicalPerformance(),
                evidence.historicalEvidenceConfidence()
        );
        double finalScore = weights.getStrategyMatch() * breakdown.strategyMatch()
                + weights.getDepthMatch() * breakdown.depthMatch()
                + weights.getFeatureConfidence() * breakdown.featureConfidence()
                + weights.getTimeWindowMatch() * breakdown.timeWindowMatch()
                + weights.getGearCompatibility() * breakdown.gearCompatibility()
                + weights.getTravelAccess() * breakdown.travelAccess()
                + weights.getHistoricalPerformance() * breakdown.historicalPerformanceOrNeutral();
        return new SpotScore(clamp(finalScore), breakdown);
    }

    /**
     * Fishing quality without launch proximity, boat travel cost, or access selection.
     * Travel-access is held at a neutral 0.5 so AUTO recommendation cannot leak launch distance.
     */
    public double intrinsicFishingQuality(
            CandidateSpot candidate,
            PlanningContext context,
            DepthRange windowDepth,
            EmpiricalEvidence empirical
    ) {
        SpotScore scored = score(candidate, context, windowDepth, empirical);
        PlanningProperties.Ranking weights = context.properties().getRanking();
        ScoreBreakdown b = scored.breakdown();
        double intrinsic = weights.getStrategyMatch() * b.strategyMatch()
                + weights.getDepthMatch() * b.depthMatch()
                + weights.getFeatureConfidence() * b.featureConfidence()
                + weights.getTimeWindowMatch() * b.timeWindowMatch()
                + weights.getGearCompatibility() * b.gearCompatibility()
                + weights.getTravelAccess() * 0.5
                + weights.getHistoricalPerformance() * b.historicalPerformanceOrNeutral();
        return clamp(intrinsic);
    }

    static double depthMatch(CandidateSpot candidate, DepthRange window, double fallbackToleranceM) {
        Double depth = WaterDepth.meters(candidate.getRepresentativeDepthM());
        if (depth == null) {
            Double min = WaterDepth.meters(candidate.getMinDepthM());
            Double max = WaterDepth.meters(candidate.getMaxDepthM());
            if (min != null && max != null) {
                depth = (min + max) / 2.0;
            } else if (min != null) {
                depth = min;
            } else if (max != null) {
                depth = max;
            }
        }
        if (depth == null || window == null) {
            return 0.5;
        }
        if (depth >= window.min() && depth <= window.max()) {
            return 1.0;
        }
        double outside = depth < window.min() ? window.min() - depth : depth - window.max();
        double tolerance = fallbackToleranceM <= 0 ? 1.5 : fallbackToleranceM;
        return clamp(1.0 - (outside / tolerance));
    }

    static double gearCompatibility(CandidateSpot candidate, List<GearType> gearTypes) {
        List<GearType> spatialGear = gearTypes == null
                ? List.of()
                : gearTypes.stream().filter(type -> type != GearType.LURE).toList();
        if (spatialGear.isEmpty()) {
            return 0.5;
        }
        List<TechniqueType> techniques = candidate.techniqueTypes();
        if (techniques.isEmpty()) {
            return spatialGear.contains(GearType.ROD) ? 0.75 : 0.5;
        }
        for (TechniqueType technique : techniques) {
            Set<GearType> wanted = gearFor(technique);
            for (GearType type : spatialGear) {
                if (wanted.contains(type)) {
                    return 1.0;
                }
            }
        }
        if (spatialGear.contains(GearType.ROD)) {
            return 0.75;
        }
        return 0.4;
    }

    private static Set<GearType> gearFor(TechniqueType technique) {
        return switch (technique) {
            case NED_RIG, DROP_SHOT, TEXAS_RIG, TUBE, JIG, LIVE_BAIT -> Set.of(GearType.BAIT);
            case JERKBAIT, SWIMBAIT, CRANKBAIT, SPINNERBAIT, TOPWATER -> Set.of();
            case TROLLING -> Set.of(GearType.ROD, GearType.ELECTRONICS);
            case OTHER -> Set.of(GearType.ROD);
        };
    }

    private static double travelAccess(CandidateSpot candidate, PlanningContext context) {
        if (candidate.isShoreAccessUnverified()) {
            return context.properties().getAccess().getShoreUnverifiedAccessibilityScore();
        }
        if (!context.accessKnown()) {
            return 0.5;
        }
        double distanceM = GeoMetrics.distanceM(context.routeStartPoint(), candidate.getLocation());
        double maxM = context.fishingMode() == FishingMode.BOAT
                ? context.properties().maxOneWayKm(context.boat() == null ? null : context.boat().getType()) * 1000.0
                : 2000.0;
        if (maxM <= 0) {
            return 0.5;
        }
        return clamp(1.0 - Math.min(1.0, distanceM / maxM));
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }
}

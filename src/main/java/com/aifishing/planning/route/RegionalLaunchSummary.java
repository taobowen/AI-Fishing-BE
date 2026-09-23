package com.aifishing.planning.route;

import com.aifishing.feedback.ranking.EmpiricalEvidence;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.filter.BoatCapabilityFilter;
import com.aifishing.planning.ranking.SpotRankingService;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.domain.DepthRange;
import org.locationtech.jts.geom.Point;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cheap deterministic regional value for AUTO launch ranking. Not the Beam objective
 * and not a second copy of {@code ZoneSubPlanner} utility.
 */
public final class RegionalLaunchSummary {

    private RegionalLaunchSummary() {
    }

    public static double score(
            List<CandidateSpot> members,
            Point launch,
            PlanningContext context,
            SpotRankingService ranking,
            java.util.function.Function<CandidateSpot, EmpiricalEvidence> evidence
    ) {
        if (members == null || members.isEmpty() || launch == null) {
            return 0;
        }
        double capKm = BoatCapabilityFilter.travelCapKm(context);
        double best = 0;
        int inRange = 0;
        Set<FeatureType> types = new HashSet<>();
        double spacingSum = 0;
        int spacingCount = 0;
        Point prev = null;
        for (CandidateSpot member : members) {
            if (member.getLocation() == null) {
                continue;
            }
            EmpiricalEvidence empirical = evidence == null ? EmpiricalEvidence.none() : evidence.apply(member);
            double intrinsic = ranking.intrinsicFishingQuality(member, context, windowDepth(context, member), empirical);
            best = Math.max(best, intrinsic);
            types.add(member.getType());
            double km = GeoMetrics.distanceM(launch, member.getLocation()) / 1000.0;
            if (Double.isFinite(capKm) && km <= capKm) {
                inRange++;
            }
            if (prev != null) {
                spacingSum += GeoMetrics.distanceM(prev, member.getLocation());
                spacingCount++;
            }
            prev = member.getLocation();
        }
        double inRangeFraction = members.isEmpty() ? 0 : inRange / (double) members.size();
        double typeDiversity = types.size() / 6.0;
        double spacingPenalty = spacingCount == 0 ? 0 : Math.min(1.0, (spacingSum / spacingCount) / 500.0);
        return clamp(0.50 * best + 0.25 * typeDiversity + 0.15 * inRangeFraction - 0.10 * spacingPenalty);
    }

    private static DepthRange windowDepth(PlanningContext context, CandidateSpot spot) {
        if (context.profile() == null) {
            return null;
        }
        var matching = com.aifishing.planning.ranking.ArrivalStrategyEvaluator.matchingWindow(
                context.profile(),
                context.trip() == null ? null : context.trip().getFishingStartTime()
        );
        return matching == null ? null : matching.preferredDepthM();
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }
}

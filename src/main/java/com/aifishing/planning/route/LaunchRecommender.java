package com.aifishing.planning.route;

import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.common.enums.BoatType;
import com.aifishing.feedback.ranking.EmpiricalEvidence;
import com.aifishing.feedback.ranking.EmpiricalRankingProvider;
import com.aifishing.lake.ingestion.domain.AccessOwnership;
import com.aifishing.lake.ingestion.domain.LakeAccessPoint;
import com.aifishing.launch.BoatLaunchService;
import com.aifishing.launch.CustomLaunchResolver;
import com.aifishing.launch.LaunchProperties;
import com.aifishing.launch.ResolvedTripLaunch;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.ranking.LaunchRecommendationScoreBreakdown;
import com.aifishing.planning.ranking.SpotRankingService;
import com.aifishing.planning.service.PlanningContext;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class LaunchRecommender {

    public static final String NO_KNOWN = "NO_KNOWN_BOAT_LAUNCH";
    public static final String NO_ROUTABLE = "NO_ROUTABLE_KNOWN_BOAT_LAUNCH";

    private final BoatLaunchService boatLaunchService;
    private final CustomLaunchResolver customLaunchResolver;
    private final SpotRankingService rankingService;
    private final EmpiricalRankingProvider empiricalRankingProvider;
    private final TravelTimeEstimator travelTimeEstimator;
    private final LaunchProperties launchProperties;

    public LaunchRecommender(
            BoatLaunchService boatLaunchService,
            CustomLaunchResolver customLaunchResolver,
            SpotRankingService rankingService,
            EmpiricalRankingProvider empiricalRankingProvider,
            TravelTimeEstimator travelTimeEstimator,
            LaunchProperties launchProperties
    ) {
        this.boatLaunchService = boatLaunchService;
        this.customLaunchResolver = customLaunchResolver;
        this.rankingService = rankingService;
        this.empiricalRankingProvider = empiricalRankingProvider;
        this.travelTimeEstimator = travelTimeEstimator;
        this.launchProperties = launchProperties;
    }

    public Result recommend(PlanningContext context, List<CandidateSpot> candidates) {
        List<LakeAccessPoint> known = boatLaunchService.autoEligible(context.lake().getId());
        if (known.isEmpty()) {
            return Result.failed(NO_KNOWN);
        }
        Map<UUID, EmpiricalEvidence> empirical = empiricalRankingProvider.scoreCandidates(
                context.lake().getId(),
                context.primarySpecies(),
                context.trip().getUserId(),
                candidates
        );
        List<ScoredLaunch> scored = new ArrayList<>();
        for (LakeAccessPoint launch : known) {
            CustomLaunchResolver.Resolution resolution = customLaunchResolver
                    .resolve(launch.getLocation(), context.geometry())
                    .orElse(null);
            if (resolution == null) {
                continue;
            }
            LaunchRecommendationScoreBreakdown breakdown = score(
                    launch,
                    resolution.routeStartPoint(),
                    candidates,
                    empirical,
                    context
            );
            scored.add(new ScoredLaunch(launch, resolution, breakdown));
        }
        if (scored.isEmpty()) {
            return Result.failed(NO_ROUTABLE);
        }
        scored.sort(Comparator
                .comparing((ScoredLaunch item) -> item.breakdown.overall()).reversed()
                .thenComparing(item -> item.launch.getName() == null ? "" : item.launch.getName()));
        ScoredLaunch best = scored.get(0);
        ResolvedTripLaunch resolved = new ResolvedTripLaunch(
                com.aifishing.common.enums.LaunchSelectionMode.AUTO_RECOMMENDED,
                best.launch.getLocation(),
                best.resolution.shoreAccessPoint(),
                best.resolution.routeStartPoint(),
                best.launch.getId(),
                best.launch.getName(),
                best.launch.getSource(),
                com.aifishing.common.enums.LaunchVerification.AUTHORITATIVE,
                CustomLaunchResolver.accessType(best.launch),
                null,
                best.resolution.shorelineKind(),
                best.resolution.snapDistanceMeters(),
                best.resolution.resolutionVersion(),
                AccessOwnership.launchWarnings(best.launch.getOwnershipType(), best.resolution.warnings()),
                best.breakdown
        );
        return Result.ok(resolved);
    }

    private LaunchRecommendationScoreBreakdown score(
            LakeAccessPoint launch,
            Point routeStart,
            List<CandidateSpot> candidates,
            Map<UUID, EmpiricalEvidence> empirical,
            PlanningContext context
    ) {
        LaunchProperties.Recommendation rec = launchProperties.getRecommendation();
        int topN = Math.max(1, rec.getTopCandidateCount());
        List<CandidateQuality> qualities = new ArrayList<>();
        for (CandidateSpot spot : candidates) {
            if (spot.getLocation() == null) {
                continue;
            }
            EmpiricalEvidence evidence = empirical.getOrDefault(spot.getFeatureId(), EmpiricalEvidence.none());
            double intrinsic = rankingService.intrinsicFishingQuality(
                    spot, context, windowDepth(context, spot), evidence);
            TravelEstimate travel = travelTimeEstimator.estimate(routeStart, spot.getLocation(), context);
            qualities.add(new CandidateQuality(intrinsic, travel.distanceM(), travel.minutes(), inRange(context, travel.distanceM())));
        }
        qualities.sort(Comparator.comparingDouble(CandidateQuality::intrinsic).reversed());
        List<CandidateQuality> top = qualities.stream().limit(topN).toList();
        double coverage = top.stream().mapToDouble(CandidateQuality::intrinsic).average().orElse(0);
        double meanTravelM = top.stream().mapToDouble(CandidateQuality::travelM).average().orElse(0);
        double maxM = maxOneWayKm(context) * 1000.0;
        double travelCost = maxM <= 0 ? 0.5 : clamp(1.0 - Math.min(1.0, meanTravelM / maxM));
        double boatFeasibility = top.isEmpty()
                ? 0
                : top.stream().filter(CandidateQuality::inRange).count() / (double) top.size();
        double accessConfidence = AccessOwnership.unverified(launch.getOwnershipType()) ? 0.55 : 0.85;
        double overall = rec.getCoverageWeight() * coverage
                + rec.getTravelWeight() * travelCost
                + rec.getBoatFeasibilityWeight() * boatFeasibility
                + rec.getAccessConfidenceWeight() * accessConfidence;
        return new LaunchRecommendationScoreBreakdown(
                round(coverage),
                round(travelCost),
                round(boatFeasibility),
                round(accessConfidence),
                round(overall),
                launch.getName(),
                rec.getAlgorithmVersion()
        );
    }

    private static com.aifishing.strategy.domain.DepthRange windowDepth(PlanningContext context, CandidateSpot spot) {
        if (context.profile() == null || context.profile().timeWindows() == null) {
            return null;
        }
        for (var window : context.profile().timeWindows()) {
            if (java.util.Objects.equals(window.from(), spot.getWindowFrom())
                    && java.util.Objects.equals(window.to(), spot.getWindowTo())) {
                return window.preferredDepthM();
            }
        }
        return null;
    }

    private static boolean inRange(PlanningContext context, double distanceM) {
        return distanceM / 1000.0 <= maxOneWayKm(context);
    }

    private static double maxOneWayKm(PlanningContext context) {
        EffectiveBoatCapability effective = context.effectiveBoatCapability();
        if (effective != null && effective.rangeEnforced() && effective.effectiveUsableRangeKm() != null) {
            return Math.min(effective.maxLegKm(), effective.effectiveUsableRangeKm() / 2.0);
        }
        if (effective != null) {
            return effective.maxLegKm();
        }
        BoatType type = context.boat() == null ? BoatType.OTHER : context.boat().getType();
        return context.properties().maxOneWayKm(type);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    public record Result(ResolvedTripLaunch launch, String errorCode) {
        static Result ok(ResolvedTripLaunch launch) {
            return new Result(launch, null);
        }

        static Result failed(String code) {
            return new Result(null, code);
        }

        public boolean failed() {
            return errorCode != null;
        }
    }

    private record ScoredLaunch(
            LakeAccessPoint launch,
            CustomLaunchResolver.Resolution resolution,
            LaunchRecommendationScoreBreakdown breakdown
    ) {
    }

    private record CandidateQuality(double intrinsic, double travelM, double travelMin, boolean inRange) {
    }
}

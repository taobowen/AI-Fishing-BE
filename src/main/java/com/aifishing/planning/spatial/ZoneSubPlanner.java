package com.aifishing.planning.spatial;

import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.environment.GenerateOrientationCache;
import com.aifishing.planning.environment.LocalOrientation;
import com.aifishing.planning.environment.LocalOrientationService;
import com.aifishing.planning.environment.TimeAdjustedSpotUtility;
import com.aifishing.planning.environment.TimeIndexedWeather;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.ranking.SpotScore;
import com.aifishing.planning.service.PlanningContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ZoneSubPlanner {

    private final SpatialUtility spatialUtility;
    private final TimeAdjustedSpotUtility pointUtility;
    private final LocalOrientationService orientationService;
    private final LakeNavRasterBuilder rasterBuilder;
    private final SnapshotWaterPathService snapshotPaths;
    private final Map<String, LakeNavRaster> rasterCache = new ConcurrentHashMap<>();

    public ZoneSubPlanner(
            LocalWaterPathEstimator waterPath,
            SpatialUtility spatialUtility,
            TimeAdjustedSpotUtility pointUtility,
            LocalOrientationService orientationService
    ) {
        this(
                spatialUtility,
                pointUtility,
                orientationService,
                new LakeNavRasterBuilder(new com.aifishing.common.geo.LocalMetricCrs()),
                new SnapshotWaterPathService(null)
        );
    }

    @Autowired
    public ZoneSubPlanner(
            SpatialUtility spatialUtility,
            TimeAdjustedSpotUtility pointUtility,
            LocalOrientationService orientationService,
            LakeNavRasterBuilder rasterBuilder,
            SnapshotWaterPathService snapshotPaths
    ) {
        this.spatialUtility = spatialUtility;
        this.pointUtility = pointUtility;
        this.orientationService = orientationService;
        this.rasterBuilder = rasterBuilder;
        this.snapshotPaths = snapshotPaths;
    }

    public List<ZoneFishingPackage> packages(
            CandidateSpot zone,
            ZoneVisitState visitState,
            Instant arrival,
            VisitPortal entry,
            VisitPortal exit,
            PlanningContext context,
            TimeIndexedWeather weather,
            int remainingMinutes
    ) {
        PlanningProperties.Schedule schedule = context.properties().getSchedule();
        int cap = Math.min(Math.max(0, remainingMinutes), schedule.getMaxZoneVisitMinutes());
        long filterStarted = System.nanoTime();
        List<CandidateSpot> remaining = visitState == null
                ? new ArrayList<>(zone.getZoneMembers())
                : new ArrayList<>(visitState.remainingMembers(zone));
        diag().addStandaloneStageNs(ZoneSubPlannerDiagnostics.Stage.MEMBER_FILTER, System.nanoTime() - filterStarted);
        List<ZoneFishingPackage> out = new ArrayList<>();
        int dwellsConsidered = 0;
        for (Integer budget : schedule.getZonePackageMinutes()) {
            if (budget == null || budget < 8 || budget > cap) {
                continue;
            }
            dwellsConsidered++;
            PackageResult result = computePackage(
                    zone, remaining, arrival, budget, entry, exit, context, weather, remainingMinutes);
            if (result == null || result.plan().utility() < 0 || result.plan().fishingMinutes() <= 0) {
                continue;
            }
            out.add(ZoneFishingPackage.from(result.plan(), result.consumedMemberIds(), result.localDistanceM()));
        }
        long enumStarted = System.nanoTime();
        List<ZoneFishingPackage> kept = nondominated(out);
        diag().addStandaloneStageNs(ZoneSubPlannerDiagnostics.Stage.PACKAGE_ENUMERATION, System.nanoTime() - enumStarted);
        diag().recordPackagesResult(
                zone == null ? null : zone.getZoneId(),
                zone == null ? null : zone.getVisitScopeId(),
                dwellsConsidered,
                kept.size());
        return kept;
    }

    public ZoneSubPlan plan(
            CandidateSpot zone,
            Instant arrival,
            int visitMinutes,
            VisitPortal entry,
            VisitPortal exit,
            PlanningContext context,
            TimeIndexedWeather weather
    ) {
        String key = cacheKey(zone, arrival, visitMinutes, entry, exit, context, List.of());
        GenerateProfiler.current().count("zoneSubPlannerCalls");
        GenerateProfiler.current().start(GenerateProfiler.ZONE_SUBPLANNER);
        beginCompute("plan", zone, zone == null ? List.of() : zone.getZoneMembers(), arrival, visitMinutes, entry, exit, 0);
        try {
            RequestScoringCache scoring = RequestScoringCache.current();
            if (scoring != null) {
                Object cached = scoring.planResult(key);
                if (cached instanceof ZoneSubPlan plan) {
                    GenerateProfiler.current().count("zoneSubPlannerCacheHits");
                    diag().markCacheHit();
                    return plan;
                }
            }
            diag().markCacheMiss();
            ZoneSubPlan plan = compute(
                    zone, new ArrayList<>(zone.getZoneMembers()), arrival, visitMinutes, entry, exit, context, weather).plan();
            if (scoring != null) {
                scoring.putPlan(key, plan);
            }
            return plan;
        } finally {
            diag().endCompute();
            GenerateProfiler.current().end(GenerateProfiler.ZONE_SUBPLANNER);
        }
    }

    private PackageResult computePackage(
            CandidateSpot zone,
            List<CandidateSpot> remaining,
            Instant arrival,
            int visitMinutes,
            VisitPortal entry,
            VisitPortal exit,
            PlanningContext context,
            TimeIndexedWeather weather,
            int remainingMinutes
    ) {
        List<UUID> ids = remaining.stream().map(ZoneVisitState::memberId).toList();
        String key = cacheKey(zone, arrival, visitMinutes, entry, exit, context, ids);
        GenerateProfiler.current().count("zoneSubPlannerCalls");
        GenerateProfiler.current().start(GenerateProfiler.ZONE_SUBPLANNER);
        beginCompute("computePackage", zone, remaining, arrival, visitMinutes, entry, exit, remainingMinutes);
        try {
            RequestScoringCache scoring = RequestScoringCache.current();
            if (scoring != null) {
                Object cached = scoring.packageResult(key);
                if (cached instanceof PackageResult result) {
                    GenerateProfiler.current().count("zoneSubPlannerCacheHits");
                    diag().markCacheHit();
                    return result;
                }
            }
            diag().markCacheMiss();
            PackageResult result = compute(zone, remaining, arrival, visitMinutes, entry, exit, context, weather);
            if (scoring != null) {
                scoring.putPackage(key, result);
            }
            return result;
        } finally {
            diag().endCompute();
            GenerateProfiler.current().end(GenerateProfiler.ZONE_SUBPLANNER);
        }
    }

    private static List<ZoneFishingPackage> nondominated(List<ZoneFishingPackage> packages) {
        List<ZoneFishingPackage> sorted = new ArrayList<>(packages);
        sorted.sort(Comparator.comparingInt(ZoneFishingPackage::visitMinutes)
                .thenComparingDouble((ZoneFishingPackage pkg) -> -pkg.marginalUtility()));
        List<ZoneFishingPackage> kept = new ArrayList<>();
        double bestUtility = Double.NEGATIVE_INFINITY;
        for (ZoneFishingPackage pkg : sorted) {
            if (pkg.marginalUtility() > bestUtility + 1e-9) {
                kept.add(pkg);
                bestUtility = pkg.marginalUtility();
            }
        }
        return kept;
    }

    private PackageResult compute(
            CandidateSpot zone,
            List<CandidateSpot> members,
            Instant arrival,
            int visitMinutes,
            VisitPortal entry,
            VisitPortal exit,
            PlanningContext context,
            TimeIndexedWeather weather
    ) {
        members = new ArrayList<>(members);
        if (members.isEmpty() || entry == null || exit == null) {
            diag().setSequencesConsidered(0);
            return new PackageResult(new ZoneSubPlan(List.of(), visitMinutes, 0, 0, visitMinutes, 0), List.of(), 0);
        }
        long filterStarted = System.nanoTime();
        members.sort(Comparator.comparingDouble((CandidateSpot member) -> -member.getStrategyWeight())
                .thenComparing(member -> String.valueOf(member.getFishingTargetId() == null ? member.getFeatureId() : member.getFishingTargetId())));
        diag().addStageNs(ZoneSubPlannerDiagnostics.Stage.MEMBER_FILTER, System.nanoTime() - filterStarted);
        PlanningProperties.Spatial spatial = context.properties().getSpatial();
        LakePlanningGeometry lake = context.geometry();
        Instant cursor = arrival;
        org.locationtech.jts.geom.Point at = entry.point();
        int remaining = visitMinutes;
        int fishing = 0;
        int transit = 0;
        double utility = 0;
        double localDistanceM = 0;
        Set<java.util.UUID> used = new HashSet<>();
        List<ZoneSubPlan.MicroStop> stops = new ArrayList<>();
        while (remaining >= 8 && stops.size() < members.size()) {
            CandidateSpot best = null;
            LocalWaterPathEstimator.PathEstimate bestPath = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (CandidateSpot member : members) {
                UUID memberId = member.getFishingTargetId() == null ? member.getFeatureId() : member.getFishingTargetId();
                if (used.contains(memberId)) {
                    continue;
                }
                diag().recordMemberEval();
                var path = hop(at, member.getEntryPoint(), zone, lake, spatial, context);
                if (path.isEmpty()) {
                    continue;
                }
                int hop = (int) Math.max(1, Math.round(path.get().minutes()));
                int dwell = Math.min(20, remaining - hop);
                if (dwell < 8) {
                    continue;
                }
                RankedCandidate ranked = ranked(member);
                LocalOrientation orientation = resolveOrientation(member, lake, context);
                Instant memberArrival = cursor.plus(Duration.ofMinutes(hop));
                long scoreStarted = System.nanoTime();
                double score = spatialUtility.alongPath(
                        ranked,
                        memberArrival,
                        dwell,
                        member.getEntryPoint(),
                        member.getExitPoint(),
                        member.getSelectedFishingPath() == null ? member.getTargetGeometry() : member.getSelectedFishingPath(),
                        context,
                        weather,
                        orientation
                ) - 0.02 * hop;
                diag().addStageNs(ZoneSubPlannerDiagnostics.Stage.PACKAGE_UTILITY_SCORING, System.nanoTime() - scoreStarted);
                if (score > bestScore) {
                    bestScore = score;
                    best = member;
                    bestPath = path.get();
                }
            }
            if (best == null) {
                break;
            }
            int hop = (int) Math.max(1, Math.round(bestPath.minutes()));
            int dwell = Math.min(20, remaining - hop);
            Instant memberArrival = cursor.plus(Duration.ofMinutes(hop));
            Instant memberDepart = memberArrival.plus(Duration.ofMinutes(dwell));
            stops.add(new ZoneSubPlan.MicroStop(
                    best,
                    memberArrival,
                    memberDepart,
                    best.getEntryPoint(),
                    best.getExitPoint(),
                    best.getTargetGeometry(),
                    dwell,
                    hop,
                    bestScore,
                    CastingOpportunity.ANCHOR_REASON
            ));
            used.add(ZoneVisitState.memberId(best));
            fishing += dwell;
            transit += hop;
            remaining -= hop + dwell;
            cursor = memberDepart;
            at = best.getExitPoint();
            utility += Math.max(0, bestScore);
            localDistanceM += bestPath.meters();
            attachCastingCompanions(best, members, used, stops, memberArrival, memberDepart, lake, context);
        }
        var exitPath = hop(at, exit.point(), zone, lake, spatial, context);
        if (exitPath.isPresent()) {
            int hop = (int) Math.max(0, Math.round(exitPath.get().minutes()));
            if (hop > remaining) {
                diag().setSequencesConsidered(stops.size());
                return infeasible(visitMinutes);
            }
            transit += hop;
            remaining -= hop;
            localDistanceM += exitPath.get().meters();
        } else if (!at.equals(exit.point())) {
            diag().setSequencesConsidered(stops.size());
            return infeasible(visitMinutes);
        }
        if (stops.isEmpty()) {
            if (!members.isEmpty()) {
                diag().setSequencesConsidered(0);
                return infeasible(visitMinutes);
            }
            RankedCandidate ranked = ranked(zone);
            LocalOrientation orientation = resolveOrientation(zone, lake, context);
            long scoreStarted = System.nanoTime();
            utility = pointUtility.dwellValue(ranked, arrival, Math.max(1, visitMinutes - transit), context, weather, orientation);
            diag().addStageNs(ZoneSubPlannerDiagnostics.Stage.PACKAGE_UTILITY_SCORING, System.nanoTime() - scoreStarted);
            fishing = Math.max(0, visitMinutes - transit);
        }
        int wait = Math.max(0, visitMinutes - fishing - transit);
        List<UUID> consumed = stops.stream().map(stop -> ZoneVisitState.memberId(stop.spot())).toList();
        diag().setSequencesConsidered(stops.size());
        return new PackageResult(
                new ZoneSubPlan(List.copyOf(stops), fishing, transit, wait, visitMinutes, utility),
                consumed,
                localDistanceM
        );
    }

    /**
     * Micros that can be fished from the anchor's casting position share its dwell.
     * They stay on the plan as separate targets and are consumed so a later package
     * does not bill them again. Opposite shores and other land crossings stay apart.
     */
    private void attachCastingCompanions(
            CandidateSpot anchor,
            List<CandidateSpot> members,
            Set<UUID> used,
            List<ZoneSubPlan.MicroStop> stops,
            Instant arrival,
            Instant departure,
            LakePlanningGeometry lake,
            PlanningContext context
    ) {
        double threshold = castingOpportunityMeters(context);
        for (CandidateSpot member : members) {
            UUID memberId = ZoneVisitState.memberId(member);
            if (memberId == null || used.contains(memberId)) {
                continue;
            }
            if (!sameCastingOpportunity(anchor, member, lake, threshold)) {
                continue;
            }
            stops.add(new ZoneSubPlan.MicroStop(
                    member,
                    arrival,
                    departure,
                    member.getEntryPoint(),
                    member.getExitPoint(),
                    member.getTargetGeometry(),
                    0,
                    0,
                    0,
                    CastingOpportunity.COMPANION_REASON
            ));
            used.add(memberId);
        }
    }

    private static boolean sameCastingOpportunity(
            CandidateSpot anchor,
            CandidateSpot member,
            LakePlanningGeometry lake,
            double thresholdMeters
    ) {
        org.locationtech.jts.geom.Point from = castingPoint(anchor);
        org.locationtech.jts.geom.Point to = castingPoint(member);
        if (from == null || to == null) {
            return false;
        }
        double meters = GeoMetrics.distanceM(from, to);
        if (meters > thresholdMeters) {
            return false;
        }
        if (meters <= 1 || lake == null) {
            return true;
        }
        return !lake.landCrossing(from, to);
    }

    private static org.locationtech.jts.geom.Point castingPoint(CandidateSpot spot) {
        if (spot == null) {
            return null;
        }
        return spot.getLocation() != null ? spot.getLocation() : spot.getEntryPoint();
    }

    private static double castingOpportunityMeters(PlanningContext context) {
        if (context == null || context.properties() == null || context.properties().getSpatial() == null) {
            return 40;
        }
        return context.properties().getSpatial().getCastingOpportunityMeters();
    }

    private java.util.Optional<LocalWaterPathEstimator.PathEstimate> hop(
            org.locationtech.jts.geom.Point from,
            org.locationtech.jts.geom.Point to,
            CandidateSpot zone,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial,
            PlanningContext context
    ) {
        if (from == null || to == null) {
            return java.util.Optional.empty();
        }
        if (from.equals(to)) {
            diag().recordWaterPath(true, 0, 0, 0);
            return java.util.Optional.of(new LocalWaterPathEstimator.PathEstimate(null, 0, 0));
        }
        if (context != null && context.spatialSnapshot() != null && context.spatialSnapshot().raster() != null
                && zone.getZoneId() != null) {
            return snapshotPaths.path(
                    context.spatialSnapshot(),
                    zone.getZoneId(),
                    from,
                    to,
                    spatial,
                    context.pendingZoneWaterPaths());
        }
        String key = System.identityHashCode(lake) + "|" + spatial.getLocalPathCellSizeM() + "|" + spatial.getInternalCruiseKmh();
        long astarStarted = System.nanoTime();
        var path = rasterCache.computeIfAbsent(key, ignored -> rasterBuilder.build(lake, spatial))
                .shortest(from, to, spatial.getInternalCruiseKmh());
        diag().recordWaterPath(false, 0, System.nanoTime() - astarStarted, 0);
        return path;
    }

    private LocalOrientation resolveOrientation(
            CandidateSpot spot,
            LakePlanningGeometry lake,
            PlanningContext context
    ) {
        long started = System.nanoTime();
        ZoneSubPlannerDiagnostics diagnostics = diag();
        diagnostics.recordOrientationRequest();
        GenerateOrientationCache cache = context == null ? null : context.orientationCache();
        UUID snapshotId = context == null || context.spatialSnapshot() == null ? null : context.spatialSnapshot().id();
        LocalOrientation orientation;
        if (cache == null) {
            long resolveStarted = System.nanoTime();
            orientation = orientationService.resolve(spot, lake);
            diagnostics.recordOrientationResolveNs(System.nanoTime() - resolveStarted);
            diagnostics.recordOrientationCacheMiss();
        } else {
            long resolveStarted = System.nanoTime();
            GenerateOrientationCache.Lookup lookup = cache.getOrResolve(snapshotId, spot, lake, orientationService);
            if (lookup.hit()) {
                diagnostics.recordOrientationCacheHit();
            } else {
                diagnostics.recordOrientationResolveNs(System.nanoTime() - resolveStarted);
                diagnostics.recordOrientationCacheMiss();
            }
            orientation = lookup.orientation();
        }
        diagnostics.addStageNs(ZoneSubPlannerDiagnostics.Stage.ORIENTATION, System.nanoTime() - started);
        return orientation;
    }

    private void beginCompute(
            String method,
            CandidateSpot zone,
            List<CandidateSpot> remaining,
            Instant arrival,
            int visitMinutes,
            VisitPortal entry,
            VisitPortal exit,
            int remainingMinutes
    ) {
        int portalCount = zone == null || zone.getPortals() == null ? 0 : zone.getPortals().size();
        int memberCount = zone == null || zone.getZoneMembers() == null ? 0 : zone.getZoneMembers().size();
        diag().beginCompute(
                method,
                zone == null ? null : zone.getZoneId(),
                zone == null ? null : zone.getVisitScopeId(),
                memberCount,
                remaining == null ? 0 : remaining.size(),
                portalCount,
                portalCount,
                entry == null ? null : entry.id(),
                exit == null ? null : exit.id(),
                visitMinutes,
                arrival,
                remainingMinutes
        );
    }

    private static ZoneSubPlannerDiagnostics diag() {
        return GenerateProfiler.current().zoneSubPlanner();
    }

    private static PackageResult infeasible(int visitMinutes) {
        return new PackageResult(
                new ZoneSubPlan(List.of(), 0, 0, 0, visitMinutes, Double.NEGATIVE_INFINITY),
                List.of(),
                0
        );
    }

    private static RankedCandidate ranked(CandidateSpot spot) {
        double intrinsic = Math.max(0.1, spot.getStrategyWeight());
        return new RankedCandidate(
                spot,
                new SpotScore(intrinsic, new ScoreBreakdown(intrinsic, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.0)),
                null
        );
    }

    private static String cacheKey(
            CandidateSpot zone,
            Instant arrival,
            int visitMinutes,
            VisitPortal entry,
            VisitPortal exit,
            PlanningContext context,
            List<UUID> remainingMemberIds
    ) {
        String strategy = context.strategyRun() == null ? "" : String.valueOf(context.strategyRun().getId());
        String weather = context.weather() == null ? "" : String.valueOf(context.weather().retrievedAt());
        String members = remainingMemberIds == null ? "" : remainingMemberIds.stream()
                .map(String::valueOf)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return zone.getZoneId() + "|" + arrival + "|" + visitMinutes + "|"
                + (entry == null ? "" : entry.id()) + "|" + (exit == null ? "" : exit.id())
                + "|" + strategy + "|" + weather + "|" + members
                + "|" + castingOpportunityMeters(context);
    }

    private record PackageResult(ZoneSubPlan plan, List<UUID> consumedMemberIds, double localDistanceM) {
        private PackageResult {
            consumedMemberIds = consumedMemberIds == null ? List.of() : List.copyOf(consumedMemberIds);
        }
    }
}

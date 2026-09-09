package com.aifishing.planning.spatial;

import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
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
    private final Map<String, ZoneSubPlan> cache = new ConcurrentHashMap<>();

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

    public ZoneSubPlan plan(
            CandidateSpot zone,
            Instant arrival,
            int visitMinutes,
            VisitPortal entry,
            VisitPortal exit,
            PlanningContext context,
            TimeIndexedWeather weather
    ) {
        String key = cacheKey(zone, arrival, visitMinutes, entry, exit, context);
        return cache.computeIfAbsent(key, ignored -> compute(zone, arrival, visitMinutes, entry, exit, context, weather));
    }

    private ZoneSubPlan compute(
            CandidateSpot zone,
            Instant arrival,
            int visitMinutes,
            VisitPortal entry,
            VisitPortal exit,
            PlanningContext context,
            TimeIndexedWeather weather
    ) {
        List<CandidateSpot> members = new ArrayList<>(zone.getZoneMembers());
        if (members.isEmpty() || entry == null || exit == null) {
            return new ZoneSubPlan(List.of(), visitMinutes, 0, 0, visitMinutes, 0);
        }
        members.sort(Comparator.comparingDouble((CandidateSpot member) -> -member.getStrategyWeight())
                .thenComparing(member -> String.valueOf(member.getFishingTargetId() == null ? member.getFeatureId() : member.getFishingTargetId())));
        PlanningProperties.Spatial spatial = context.properties().getSpatial();
        LakePlanningGeometry lake = context.geometry();
        Instant cursor = arrival;
        org.locationtech.jts.geom.Point at = entry.point();
        int remaining = visitMinutes;
        int fishing = 0;
        int transit = 0;
        double utility = 0;
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
                LocalOrientation orientation = orientationService.resolve(member, lake);
                Instant memberArrival = cursor.plus(Duration.ofMinutes(hop));
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
                    "zone micro target"
            ));
            used.add(best.getFishingTargetId() == null ? best.getFeatureId() : best.getFishingTargetId());
            fishing += dwell;
            transit += hop;
            remaining -= hop + dwell;
            cursor = memberDepart;
            at = best.getExitPoint();
            utility += Math.max(0, bestScore);
        }
        var exitPath = hop(at, exit.point(), zone, lake, spatial, context);
        if (exitPath.isPresent()) {
            int hop = (int) Math.max(0, Math.round(exitPath.get().minutes()));
            if (hop > remaining) {
                return new ZoneSubPlan(List.of(), 0, 0, 0, visitMinutes, Double.NEGATIVE_INFINITY);
            }
            transit += hop;
            remaining -= hop;
        } else if (!at.equals(exit.point())) {
            return new ZoneSubPlan(List.of(), 0, 0, 0, visitMinutes, Double.NEGATIVE_INFINITY);
        }
        if (stops.isEmpty()) {
            RankedCandidate ranked = ranked(zone);
            LocalOrientation orientation = orientationService.resolve(zone, lake);
            utility = pointUtility.dwellValue(ranked, arrival, Math.max(1, visitMinutes - transit), context, weather, orientation);
            fishing = Math.max(0, visitMinutes - transit);
        }
        int wait = Math.max(0, visitMinutes - fishing - transit);
        return new ZoneSubPlan(List.copyOf(stops), fishing, transit, wait, visitMinutes, utility);
    }

    private java.util.Optional<LocalWaterPathEstimator.PathEstimate> hop(
            org.locationtech.jts.geom.Point from,
            org.locationtech.jts.geom.Point to,
            CandidateSpot zone,
            LakePlanningGeometry lake,
            PlanningProperties.Spatial spatial,
            PlanningContext context
    ) {
        if (context != null && context.spatialSnapshot() != null && context.spatialSnapshot().raster() != null
                && zone.getZoneId() != null) {
            return snapshotPaths.path(context.spatialSnapshot(), zone.getZoneId(), from, to, spatial);
        }
        return rasterBuilder.build(lake, spatial).shortest(from, to, spatial.getInternalCruiseKmh());
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
            PlanningContext context
    ) {
        String strategy = context.strategyRun() == null ? "" : String.valueOf(context.strategyRun().getId());
        String weather = context.weather() == null ? "" : String.valueOf(context.weather().retrievedAt());
        return zone.getZoneId() + "|" + arrival + "|" + visitMinutes + "|"
                + (entry == null ? "" : entry.id()) + "|" + (exit == null ? "" : exit.id())
                + "|" + strategy + "|" + weather;
    }
}

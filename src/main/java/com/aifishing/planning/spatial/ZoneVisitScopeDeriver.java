package com.aifishing.planning.spatial;

import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.service.PlanningContext;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class ZoneVisitScopeDeriver {

    private final SnapshotWaterPathService waterPaths;

    public ZoneVisitScopeDeriver(SnapshotWaterPathService waterPaths) {
        this.waterPaths = waterPaths;
    }

    public List<CandidateSpot> derive(CandidateSpot physicalZone, PlanningContext context) {
        List<CandidateSpot> members = new ArrayList<>(physicalZone.getZoneMembers());
        if (members.isEmpty()) {
            return List.of();
        }
        List<CandidateSpot> ordered = nearestNeighborOrder(members);
        int budget = visitBudgetMinutes(context);
        double speedKmh = cruiseKmh(context);
        List<List<CandidateSpot>> windows = partition(ordered, budget, speedKmh, physicalZone, context);
        List<CandidateSpot> scopes = new ArrayList<>();
        Set<UUID> usedMembers = new HashSet<>();
        int index = 0;
        for (List<CandidateSpot> window : windows) {
            List<UUID> ids = window.stream()
                    .map(CandidateSpot::getFishingTargetId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (ids.isEmpty() || ids.stream().anyMatch(usedMembers::contains)) {
                continue;
            }
            usedMembers.addAll(ids);
            UUID scopeId = SpatialIds.scopeId(physicalZone.getZoneId(), ids.toString() + ":" + index);
            CandidateSpot scope = copyZone(physicalZone);
            scope.setVisitScopeId(scopeId);
            scope.setZoneMembers(window);
            List<UUID> coverage = new ArrayList<>();
            coverage.add(scopeId);
            coverage.addAll(ids);
            scope.setCoverageIds(coverage);
            scope.setFishingTargetId(null);
            if (window.get(0).getEntryPoint() != null) {
                scope.setEntryPoint(window.get(0).getEntryPoint());
            }
            if (window.get(window.size() - 1).getExitPoint() != null) {
                scope.setExitPoint(window.get(window.size() - 1).getExitPoint());
            }
            scopes.add(scope);
            index++;
        }
        GenerateProfiler.current().count("zoneVisitScopeCount", scopes.size());
        return scopes;
    }

    List<List<CandidateSpot>> partition(
            List<CandidateSpot> ordered,
            int budgetMinutes,
            double speedKmh,
            CandidateSpot zone,
            PlanningContext context
    ) {
        List<List<CandidateSpot>> windows = new ArrayList<>();
        int i = 0;
        while (i < ordered.size()) {
            List<CandidateSpot> window = new ArrayList<>();
            window.add(ordered.get(i));
            int j = i + 1;
            while (j < ordered.size()) {
                List<CandidateSpot> trial = new ArrayList<>(window);
                trial.add(ordered.get(j));
                if (feasible(trial, budgetMinutes, speedKmh, zone, context)) {
                    window = trial;
                    j++;
                } else {
                    break;
                }
            }
            windows.add(window);
            i += window.size();
        }
        return windows;
    }

    boolean feasible(
            List<CandidateSpot> window,
            int budgetMinutes,
            double speedKmh,
            CandidateSpot zone,
            PlanningContext context
    ) {
        int dwellPer = speedKmh >= 12 ? 6 : 12;
        int dwell = Math.max(8, window.size() * dwellPer);
        double meters = 0;
        for (int i = 1; i < window.size(); i++) {
            meters += hopMeters(window.get(i - 1).getExitPoint(), window.get(i).getEntryPoint(), zone, context);
        }
        double minutes = meters / 1000.0 / Math.max(0.8, speedKmh) * 60.0;
        return dwell + minutes <= budgetMinutes + 1;
    }

    private double hopMeters(Point from, Point to, CandidateSpot zone, PlanningContext context) {
        if (from == null || to == null) {
            return 0;
        }
        if (context != null && context.spatialSnapshot() != null && zone.getZoneId() != null) {
            var path = waterPaths.path(context.spatialSnapshot(), zone.getZoneId(), from, to, context.properties().getSpatial());
            if (path.isPresent()) {
                return path.get().meters();
            }
        }
        return GeoMetrics.distanceM(from, to);
    }

    private static List<CandidateSpot> nearestNeighborOrder(List<CandidateSpot> members) {
        List<CandidateSpot> remaining = new ArrayList<>(members);
        List<CandidateSpot> ordered = new ArrayList<>();
        CandidateSpot cursor = remaining.remove(0);
        ordered.add(cursor);
        while (!remaining.isEmpty()) {
            CandidateSpot current = cursor;
            CandidateSpot next = remaining.stream()
                    .min((a, b) -> Double.compare(
                            GeoMetrics.distanceM(current.getLocation(), a.getLocation()),
                            GeoMetrics.distanceM(current.getLocation(), b.getLocation())))
                    .orElse(remaining.get(0));
            remaining.remove(next);
            ordered.add(next);
            cursor = next;
        }
        return ordered;
    }

    static int visitBudgetMinutes(PlanningContext context) {
        int max = context == null || context.properties() == null
                ? 60
                : Math.min(90, context.properties().getSchedule().getMaxSpotMinutes());
        double derate = 0;
        if (context != null && context.effectiveBoatCapability() != null) {
            derate = Math.min(0.5, context.effectiveBoatCapability().windDerateFraction());
        }
        return (int) Math.max(20, Math.round(max * (1.0 - derate)));
    }

    static double cruiseKmh(PlanningContext context) {
        EffectiveBoatCapability boat = context == null ? null : context.effectiveBoatCapability();
        if (boat != null && boat.cruiseSpeedKmh() > 0) {
            return boat.cruiseSpeedKmh();
        }
        if (context != null) {
            return context.properties().getSpatial().getInternalCruiseKmh();
        }
        return 6;
    }

    private static CandidateSpot copyZone(CandidateSpot source) {
        CandidateSpot copy = new CandidateSpot();
        copy.setFeatureId(source.getFeatureId());
        copy.setZoneId(source.getZoneId());
        copy.setType(source.getType());
        copy.setTargetKind(TargetKind.ZONE);
        copy.setTargetGeometry(source.getTargetGeometry());
        copy.setFishingCorridor(source.getFishingCorridor());
        copy.setVisitEnvelope(source.getVisitEnvelope() == null ? source.getTargetGeometry() : source.getVisitEnvelope());
        copy.setLocation(source.getLocation());
        copy.setEntryPoint(source.getEntryPoint());
        copy.setExitPoint(source.getExitPoint());
        copy.setPortals(source.getPortals());
        copy.setPipeline(source.getPipeline());
        copy.setAnalysisVersion(source.getAnalysisVersion());
        copy.setStrategyWeight(source.getStrategyWeight());
        copy.setFeatureConfidence(source.getFeatureConfidence());
        copy.setWindowFrom(source.getWindowFrom());
        copy.setWindowTo(source.getWindowTo());
        copy.setTechniques(source.getTechniques());
        copy.setLightPreference(source.getLightPreference());
        copy.setMinDepthM(source.getMinDepthM());
        copy.setMaxDepthM(source.getMaxDepthM());
        copy.setRepresentativeDepthM(source.getRepresentativeDepthM());
        return copy;
    }
}

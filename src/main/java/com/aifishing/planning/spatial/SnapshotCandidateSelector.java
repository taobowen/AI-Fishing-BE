package com.aifishing.planning.spatial;

import com.aifishing.common.geo.WaterDepth;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateCompressionReason;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.filter.RejectionReason;
import com.aifishing.planning.ranking.StrategyWeightResolver;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import com.aifishing.planning.spatial.domain.LakeFishingTargetSample;
import com.aifishing.strategy.domain.DepthRange;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyTimeWindow;
import com.aifishing.strategy.domain.StructurePreference;
import com.aifishing.strategy.domain.TechniquePreference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SnapshotCandidateSelector {

    private final StrategyWeightResolver weightResolver;

    public SnapshotCandidateSelector(StrategyWeightResolver weightResolver) {
        this.weightResolver = weightResolver;
    }

    public Result select(SpatialSnapshotView view, PlanningContext context, Map<RejectionReason, Integer> rejections) {
        List<CandidateSpot> allTargets = view.targets().stream()
                .filter(row -> row.getTargetKind() != TargetKind.ZONE)
                .map(row -> toSpot(row, view))
                .toList();
        FishingStrategyProfile profile = context.profile();
        PlanningProperties.Candidates limits = context.properties().getCandidates();
        List<StrategyTimeWindow> windows = profile.timeWindows();
        if (windows.isEmpty()) {
            windows = List.of(new StrategyTimeWindow(
                    context.trip().getFishingStartTime(),
                    context.trip().getFishingEndTime(),
                    null,
                    List.of(),
                    List.of()
            ));
        }
        List<CandidateSpot> selected = new ArrayList<>();
        boolean depthFallback = false;
        for (StrategyTimeWindow window : windows) {
            List<CandidateSpot> pass = filterWindow(allTargets, window, context, false);
            if (pass.isEmpty()) {
                pass = filterWindow(allTargets, window, context, true);
                if (!pass.isEmpty()) {
                    depthFallback = true;
                }
            }
            selected.addAll(pass);
        }
        GenerateProfiler.current().set("candidatesBeforeDedup", selected.size());
        GenerateProfiler.current().compression().setBeforeIdentity(selected.size());
        List<CandidateSpot> physical = collapseToPhysicalIdentity(selected);
        GenerateProfiler.current().set("candidatesAfterDedup", physical.size());
        GenerateProfiler.current().compression().setAfterIdentity(physical.size());
        return new Result(physical, depthFallback, allTargets);
    }

    private List<CandidateSpot> collapseToPhysicalIdentity(List<CandidateSpot> selected) {
        Map<UUID, CandidateSpot> byId = new LinkedHashMap<>();
        int merged = 0;
        for (CandidateSpot spot : selected) {
            UUID id = spot.getFishingTargetId() != null ? spot.getFishingTargetId() : spot.getFeatureId();
            if (id == null) {
                byId.put(UUID.randomUUID(), stripWindowStamp(spot));
                continue;
            }
            CandidateSpot existing = byId.get(id);
            if (existing == null) {
                byId.put(id, stripWindowStamp(spot));
                continue;
            }
            merged++;
        }
        GenerateProfiler.current().compression().add(CandidateCompressionReason.TIME_VARIANT_MERGED, merged);
        return new ArrayList<>(byId.values());
    }

    private static CandidateSpot stripWindowStamp(CandidateSpot source) {
        CandidateSpot physical = source.copy();
        physical.setWindowFrom(null);
        physical.setWindowTo(null);
        physical.setWindowSpecific(false);
        return physical;
    }

    private List<CandidateSpot> filterWindow(
            List<CandidateSpot> targets,
            StrategyTimeWindow window,
            PlanningContext context,
            boolean depthFallback
    ) {
        PlanningProperties.Candidates limits = context.properties().getCandidates();
        List<StructurePreference> effective = weightResolver.effectiveStructures(window, context.profile());
        List<TechniquePreference> techniques = weightResolver.effectiveTechniques(window, context.profile());
        List<FeatureType> types = weightResolver.queryTypes(effective, limits.getMinStrategyWeight());
        DepthRange depth = window.preferredDepthM();
        double tolerance = depthFallback ? limits.getFallbackDepthToleranceM() : limits.getDepthToleranceM();
        double min = depth == null ? -1000 : depth.min() - tolerance;
        double max = depth == null ? 10_000 : depth.max() + tolerance;
        boolean windowSpecific = window.structurePreferences() != null && !window.structurePreferences().isEmpty();
        List<CandidateSpot> out = new ArrayList<>();
        for (CandidateSpot spot : targets) {
            if (!types.contains(spot.getType())) {
                continue;
            }
            Double representative = spot.getRepresentativeDepthM();
            if (representative != null && (representative < min || representative > max)) {
                continue;
            }
            CandidateSpot copy = copyWithStrategy(spot, window, effective, techniques, windowSpecific);
            out.add(copy);
        }
        return out;
    }

    private CandidateSpot copyWithStrategy(
            CandidateSpot source,
            StrategyTimeWindow window,
            List<StructurePreference> effective,
            List<TechniquePreference> techniques,
            boolean windowSpecific
    ) {
        CandidateSpot spot = source.copy();
        spot.setStrategyWeight(weightResolver.weightFor(effective, source.getType()));
        spot.setStrategyRationale(weightResolver.rationaleFor(effective, source.getType()));
        spot.setWindowFrom(window.from());
        spot.setWindowTo(window.to());
        spot.setTechniques(techniques);
        spot.setWindowSpecific(windowSpecific);
        spot.setLightPreference(window.lightPreference());
        return spot;
    }

    public CandidateSpot toSpot(LakeFishingTarget row, SpatialSnapshotView view) {
        CandidateSpot spot = new CandidateSpot();
        UUID source = row.getSourceFeatureIds() == null || row.getSourceFeatureIds().isEmpty()
                ? null
                : row.getSourceFeatureIds().get(0);
        spot.setFeatureId(source);
        spot.setFishingTargetId(row.getId());
        spot.setCoverageIds(List.of(row.getId()));
        spot.setType(row.getSemanticType());
        spot.setSourceGeometry(row.getGeometry());
        spot.setTargetGeometry(row.getGeometry());
        spot.setLocation(row.getRepresentativePoint());
        spot.setEntryPoint(row.getEntryPoint() == null ? row.getRepresentativePoint() : row.getEntryPoint());
        spot.setExitPoint(row.getExitPoint() == null ? row.getEntryPoint() : row.getExitPoint());
        spot.setFishingCorridor(row.getFishingCorridor());
        spot.setSelectedFishingPath(row.getSelectedFishingPath());
        if (row.getFishingCorridorWidthM() != null) {
            spot.setFishingCorridorWidthM(row.getFishingCorridorWidthM().doubleValue());
        }
        TargetKind kind = row.getTargetKind() == TargetKind.SEGMENT || row.getTargetKind() == TargetKind.AREA
                ? TargetKind.PATH
                : row.getTargetKind();
        spot.setTargetKind(kind == TargetKind.ZONE ? TargetKind.POINT : kind);
        spot.setClosedLoop(row.isClosedLoop());
        spot.setPathTopology(row.getPathTopology());
        spot.setSplitReason(row.getSplitReason());
        if (row.getChainageStartM() != null) {
            spot.setChainageStartM(row.getChainageStartM().doubleValue());
        }
        if (row.getChainageEndM() != null) {
            spot.setChainageEndM(row.getChainageEndM().doubleValue());
        }
        if (row.getMinDepthM() != null) {
            spot.setMinDepthM(WaterDepth.meters(row.getMinDepthM().doubleValue()));
        }
        if (row.getMaxDepthM() != null) {
            spot.setMaxDepthM(WaterDepth.meters(row.getMaxDepthM().doubleValue()));
        }
        if (row.getRepresentativeDepthM() != null) {
            spot.setRepresentativeDepthM(WaterDepth.meters(row.getRepresentativeDepthM().doubleValue()));
        }
        if (row.getConfidence() != null) {
            spot.setFeatureConfidence(row.getConfidence().doubleValue());
        }
        spot.setPipeline(row.getFeaturePipeline());
        spot.setAnalysisVersion(row.getFeatureAnalysisVersion());
        if (row.getEntryPoint() != null && row.getExitPoint() != null && kind == TargetKind.PATH) {
            spot.setPortals(List.of(new VisitPortal("a", row.getEntryPoint()), new VisitPortal("b", row.getExitPoint())));
        } else {
            spot.setPortals(List.of(new VisitPortal("p0", spot.getEntryPoint())));
        }
        List<LakeFishingTargetSample> samples = view.samplesByTarget().getOrDefault(row.getId(), List.of());
        if (!samples.isEmpty()) {
            spot.setStaticSamples(samples.stream()
                    .map(sample -> new SpatialUtility.Sample(sample.getGeom(), sample.getFraction().doubleValue()))
                    .toList());
        }
        return spot;
    }

    public record Result(List<CandidateSpot> spots, boolean usedDepthFallback, List<CandidateSpot> allTargets) {
    }
}

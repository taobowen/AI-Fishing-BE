package com.aifishing.planning.candidate;

import com.aifishing.common.geo.WaterDepth;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.filter.RejectionReason;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.planning.ranking.StrategyWeightResolver;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.domain.DepthRange;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyTimeWindow;
import com.aifishing.strategy.domain.StructurePreference;
import com.aifishing.strategy.domain.TechniquePreference;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CandidateGenerator {

    private final LakeFeatureRepository featureRepository;
    private final CandidateLocationService locationService;
    private final StrategyWeightResolver weightResolver;

    public CandidateGenerator(
            LakeFeatureRepository featureRepository,
            CandidateLocationService locationService,
            StrategyWeightResolver weightResolver
    ) {
        this.featureRepository = featureRepository;
        this.locationService = locationService;
        this.weightResolver = weightResolver;
    }

    public GenerationResult generate(PlanningContext context, Map<RejectionReason, Integer> rejections) {
        FishingStrategyProfile profile = context.profile();
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
        Map<FeatureType, Boolean> availability = availability(context);
        List<CandidateSpot> all = new ArrayList<>();
        boolean usedDepthFallback = false;
        for (StrategyTimeWindow window : windows) {
            WindowPass pass = generateForWindow(context, window, availability, false);
            if (pass.spots().isEmpty()) {
                pass = generateForWindow(context, window, availability, true);
                if (!pass.spots().isEmpty()) {
                    usedDepthFallback = true;
                }
            }
            all.addAll(pass.spots());
            merge(rejections, pass.rejections());
        }
        GenerateProfiler.current().set("candidatesBeforeDedup", all.size());
        GenerateProfiler.current().compression().setBeforeIdentity(all.size());
        List<CandidateSpot> physical = collapseToPhysicalIdentity(all);
        GenerateProfiler.current().set("candidatesAfterDedup", physical.size());
        GenerateProfiler.current().compression().setAfterIdentity(physical.size());
        return new GenerationResult(physical, usedDepthFallback);
    }

    private WindowPass generateForWindow(
            PlanningContext context,
            StrategyTimeWindow window,
            Map<FeatureType, Boolean> availability,
            boolean depthFallback
    ) {
        PlanningProperties.Candidates limits = context.properties().getCandidates();
        List<StructurePreference> effective = weightResolver.effectiveStructures(window, context.profile());
        List<TechniquePreference> techniques = weightResolver.effectiveTechniques(window, context.profile());
        List<FeatureType> types = weightResolver.queryTypes(effective, limits.getMinStrategyWeight());
        types = filterAvailable(types, availability, depthFallback);
        Map<RejectionReason, Integer> rejections = new EnumMap<>(RejectionReason.class);
        if (types.isEmpty()) {
            return new WindowPass(List.of(), rejections);
        }
        DepthRange depth = window.preferredDepthM();
        double tolerance = depthFallback
                ? limits.getFallbackDepthToleranceM()
                : limits.getDepthToleranceM();
        BigDecimal depthMin = BigDecimal.valueOf(depth == null ? -1000 : depth.min() - tolerance);
        BigDecimal depthMax = BigDecimal.valueOf(depth == null ? 10_000 : depth.max() + tolerance);
        List<LakeFeature> features = featureRepository.findCandidates(
                context.lake().getId(),
                context.strategyRun().getFeaturePipeline(),
                context.strategyRun().getFeatureAnalysisVersion(),
                types,
                depthMin,
                depthMax
        );
        boolean windowSpecific = window.structurePreferences() != null && !window.structurePreferences().isEmpty();
        List<CandidateSpot> spots = new ArrayList<>();
        for (LakeFeature feature : features) {
            var located = locationService.locate(feature, context.geometry());
            if (located.isEmpty()) {
                rejections.merge(RejectionReason.NO_WATER_POINT, 1, Integer::sum);
                continue;
            }
            CandidateSpot spot = toSpot(feature, located.get(), window, effective, techniques, windowSpecific);
            spots.add(spot);
        }
        return new WindowPass(spots, rejections);
    }

    private List<CandidateSpot> collapseToPhysicalIdentity(List<CandidateSpot> selected) {
        java.util.Map<java.util.UUID, CandidateSpot> byId = new java.util.LinkedHashMap<>();
        int merged = 0;
        for (CandidateSpot spot : selected) {
            java.util.UUID id = spot.getFishingTargetId() != null ? spot.getFishingTargetId() : spot.getFeatureId();
            if (id == null) {
                byId.put(java.util.UUID.randomUUID(), stripWindowStamp(spot));
                continue;
            }
            java.util.UUID existingId = id;
            CandidateSpot existing = byId.get(existingId);
            if (existing == null) {
                byId.put(existingId, stripWindowStamp(spot));
                continue;
            }
            merged++;
        }
        GenerateProfiler.current().compression().add(
                com.aifishing.planning.candidate.CandidateCompressionReason.TIME_VARIANT_MERGED, merged);
        return new ArrayList<>(byId.values());
    }

    private static CandidateSpot stripWindowStamp(CandidateSpot source) {
        CandidateSpot physical = source.copy();
        physical.setWindowFrom(null);
        physical.setWindowTo(null);
        physical.setWindowSpecific(false);
        return physical;
    }

    private CandidateSpot toSpot(
            LakeFeature feature,
            Point location,
            StrategyTimeWindow window,
            List<StructurePreference> effective,
            List<TechniquePreference> techniques,
            boolean windowSpecific
    ) {
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(feature.getId());
        spot.setType(feature.getType());
        spot.setSourceGeometry(feature.getGeometry());
        spot.setLocation(location);
        Double minDepth = WaterDepth.meters(feature.getMinDepthM() == null ? null : feature.getMinDepthM().doubleValue());
        Double maxDepth = WaterDepth.meters(feature.getMaxDepthM() == null ? null : feature.getMaxDepthM().doubleValue());
        if (minDepth != null && maxDepth != null && minDepth > maxDepth) {
            Double swap = minDepth;
            minDepth = maxDepth;
            maxDepth = swap;
        }
        spot.setMinDepthM(minDepth);
        spot.setMaxDepthM(maxDepth);
        if (minDepth != null && maxDepth != null) {
            spot.setRepresentativeDepthM((minDepth + maxDepth) / 2.0);
        } else if (minDepth != null) {
            spot.setRepresentativeDepthM(minDepth);
        } else if (maxDepth != null) {
            spot.setRepresentativeDepthM(maxDepth);
        }
        spot.setFeatureConfidence(feature.getConfidence() == null ? null : feature.getConfidence().doubleValue());
        spot.setStrategyWeight(weightResolver.weightFor(effective, feature.getType()));
        spot.setStrategyRationale(weightResolver.rationaleFor(effective, feature.getType()));
        spot.setWindowFrom(window.from());
        spot.setWindowTo(window.to());
        spot.setTechniques(techniques);
        spot.setAnalysisVersion(feature.getAnalysisVersion());
        spot.setPipeline(feature.getPipeline());
        spot.setWindowSpecific(windowSpecific);
        if (feature.getOrientation() != null) {
            spot.setRawOrientationDeg(feature.getOrientation().doubleValue());
        }
        spot.setLightPreference(window.lightPreference());
        return spot;
    }

    private List<FeatureType> filterAvailable(
            List<FeatureType> types,
            Map<FeatureType, Boolean> availability,
            boolean allowUnavailable
    ) {
        if (availability.isEmpty() || allowUnavailable) {
            return types;
        }
        List<FeatureType> filtered = types.stream()
                .filter(type -> availability.getOrDefault(type, true))
                .toList();
        return filtered.isEmpty() ? types : filtered;
    }

    private Map<FeatureType, Boolean> availability(PlanningContext context) {
        Map<FeatureType, Boolean> map = new EnumMap<>(FeatureType.class);
        Object fishingContext = context.strategyRun().getFishingContext();
        if (!(fishingContext instanceof Map<?, ?> raw)) {
            return map;
        }
        Object lake = raw.get("lake");
        if (!(lake instanceof Map<?, ?> lakeMap)) {
            return map;
        }
        Object structure = lakeMap.get("structure");
        if (!(structure instanceof List<?> rows)) {
            return map;
        }
        for (Object row : rows) {
            if (row instanceof Map<?, ?> item) {
                Object type = item.get("type");
                Object available = item.get("available");
                if (type instanceof String name && available instanceof Boolean flag) {
                    try {
                        map.put(FeatureType.valueOf(name), flag);
                    } catch (IllegalArgumentException ignored) {
                        // unknown type in snapshot
                    }
                }
            }
        }
        return map;
    }

    private void merge(Map<RejectionReason, Integer> target, Map<RejectionReason, Integer> extra) {
        extra.forEach((reason, count) -> target.merge(reason, count, Integer::sum));
    }

    public record GenerationResult(List<CandidateSpot> spots, boolean usedDepthFallback) {
    }

    private record WindowPass(List<CandidateSpot> spots, Map<RejectionReason, Integer> rejections) {
    }
}

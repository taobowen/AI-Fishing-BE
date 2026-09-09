package com.aifishing.lake.processing.hybrid;

import com.aifishing.lake.processing.VisionProperties;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.extract.FeatureEvidence;
import com.aifishing.lake.processing.extract.FeatureFactory;
import com.aifishing.lake.processing.geo.FeatureGeometryMatch;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HybridMergeService {

    public static final String SOURCE_VISION = "HYBRID_VISION";
    public static final String SOURCE_GIS = "HYBRID_GIS";

    private final VisionCandidateValidator validator;
    private final FeatureFactory featureFactory;
    private final VisionProperties visionProperties;

    public HybridMergeService(
            VisionCandidateValidator validator,
            FeatureFactory featureFactory,
            VisionProperties visionProperties
    ) {
        this.validator = validator;
        this.featureFactory = featureFactory;
        this.visionProperties = visionProperties;
    }

    public List<LakeFeature> merge(
            FeatureType type,
            List<LakeFeature> visionFeatures,
            List<LakeFeature> gisFeatures,
            AnalysisContext context
    ) {
        List<LakeFeature> merged = new ArrayList<>();
        for (LakeFeature vision : visionFeatures) {
            if (!validator.accept(vision, context)) {
                continue;
            }
            Geometry snapped = validator.snap(vision, context);
            LakeFeature feature = recreate(context, vision, snapped, SOURCE_VISION, true);
            if (feature != null && !duplicates(type, merged, feature)) {
                merged.add(feature);
            }
        }
        double minGis = visionProperties.getHybridGisMinConfidence();
        for (LakeFeature gis : gisFeatures) {
            if (gis.getConfidence() == null || gis.getConfidence().doubleValue() < minGis) {
                continue;
            }
            if (merged.stream().anyMatch(existing ->
                    FeatureGeometryMatch.matches(type, existing.getGeometry(), gis.getGeometry()))) {
                continue;
            }
            LakeFeature feature = recreate(context, gis, gis.getGeometry(), SOURCE_GIS, false);
            if (feature != null && !duplicates(type, merged, feature)) {
                merged.add(feature);
            }
        }
        return merged;
    }

    private boolean duplicates(FeatureType type, List<LakeFeature> existing, LakeFeature candidate) {
        return existing.stream().anyMatch(feature ->
                FeatureGeometryMatch.matches(type, feature.getGeometry(), candidate.getGeometry()));
    }

    private LakeFeature recreate(
            AnalysisContext context,
            LakeFeature source,
            Geometry geometry,
            String sourceMethod,
            boolean visionSupported
    ) {
        FeatureEvidence evidence = evidenceFrom(source, visionSupported);
        Map<String, Object> extra = new LinkedHashMap<>();
        if (source.getDerivationMetadata() != null) {
            extra.putAll(source.getDerivationMetadata());
            extra.remove("evidence");
        }
        extra.put("hybridOrigin", sourceMethod);
        extra.put("sourcePipeline", source.getPipeline() == null ? null : source.getPipeline().name());
        return featureFactory.create(
                context,
                source.getType(),
                geometry,
                decimal(source.getMinDepthM()),
                decimal(source.getMaxDepthM()),
                decimal(source.getSlope()),
                decimal(source.getOrientation()),
                decimal(source.getAreaM2()),
                sourceMethod,
                source.getSourceRecordId(),
                evidence,
                extra
        );
    }

    @SuppressWarnings("unchecked")
    private FeatureEvidence evidenceFrom(LakeFeature source, boolean visionSupported) {
        Map<String, Object> metadata = source.getDerivationMetadata() == null ? Map.of() : source.getDerivationMetadata();
        Object raw = metadata.get("evidence");
        Map<String, Object> ev = raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        double prominence = number(ev.get("prominence"), source.getConfidence() == null ? 0.5 : source.getConfidence().doubleValue());
        boolean bathy = visionSupported || Boolean.TRUE.equals(ev.get("bathymetrySupported"));
        boolean geom = ev.get("geometryConsistent") == null || Boolean.TRUE.equals(ev.get("geometryConsistent"));
        Double interp = ev.get("interpolationDistanceM") instanceof Number n ? n.doubleValue() : null;
        int types = ev.get("supportingDataTypes") instanceof Number n ? n.intValue() : 1;
        if (visionSupported) {
            types = Math.max(types, 2);
            bathy = true;
        }
        return new FeatureEvidence(prominence, bathy, geom, interp, types);
    }

    private double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}

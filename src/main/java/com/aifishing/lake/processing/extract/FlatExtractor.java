package com.aifishing.lake.processing.extract;

import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class FlatExtractor implements FeatureExtractor {

    private final ProcessingProperties properties;
    private final ContourTopology topology;
    private final FeatureFactory featureFactory;

    public FlatExtractor(
            ProcessingProperties properties,
            ContourTopology topology,
            FeatureFactory featureFactory
    ) {
        this.properties = properties;
        this.topology = topology;
        this.featureFactory = featureFactory;
    }

    @Override
    public FeatureType type() {
        return FeatureType.FLAT;
    }

    @Override
    public List<LakeFeature> extract(AnalysisContext context) {
        List<LakeFeature> features = new ArrayList<>();
        for (ContourTopology.ClosedContour child : context.closedContours()) {
            if (child.areaM2() < properties.getFlatMinAreaM2()) {
                continue;
            }
            ContourTopology.ClosedContour parent = topology.parentOf(child, context.closedContours());
            double spacing;
            double relief;
            if (parent != null) {
                spacing = Math.max(1.0, GeoMetrics.distanceM(
                        child.polygon().getExteriorRing(),
                        parent.polygon().getExteriorRing()
                ));
                relief = Math.abs(parent.depthM() - child.depthM());
                if (parent.depthM() - child.depthM() >= properties.getHumpMinReliefM()) {
                    continue;
                }
                if (child.depthM() - parent.depthM() >= properties.getBasinMinReliefM()) {
                    continue;
                }
            } else if (context.sourceQuality().meanContourSpacingM() != null) {
                spacing = Math.max(1.0, context.sourceQuality().meanContourSpacingM());
                relief = 0;
            } else {
                continue;
            }
            double gradient = relief / spacing;
            if (gradient > properties.getFlatMaxGradient()) {
                continue;
            }
            double prominence = Math.min(1.0, child.areaM2() / (properties.getFlatMinAreaM2() * 4.0));
            boolean spacingReliable = spacing <= properties.getDropoffMaxSpacingM() * 2;
            FeatureEvidence evidence = new FeatureEvidence(
                    prominence * (spacingReliable ? 1.0 : 0.45),
                    true,
                    child.polygon().isValid(),
                    spacingReliable ? null : spacing,
                    context.bathymetryPoints().isEmpty() ? 1 : 2
            );
            LakeFeature feature = featureFactory.create(
                    context,
                    FeatureType.FLAT,
                    child.polygon(),
                    child.depthM(),
                    parent == null ? child.depthM() : Math.max(child.depthM(), parent.depthM()),
                    gradient,
                    null,
                    child.areaM2(),
                    "CONTOUR_GRADIENT",
                    child.sourceRecordId(),
                    evidence,
                    Map.of(
                            "gradient", gradient,
                            "contourSpacingM", spacing,
                            "slopeRasterUsed", false
                    )
            );
            if (feature != null) {
                features.add(feature);
            }
        }
        return features;
    }
}

package com.aifishing.lake.processing.extract;

import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class BasinExtractor implements FeatureExtractor {

    private final ProcessingProperties properties;
    private final ContourTopology topology;
    private final FeatureFactory featureFactory;

    public BasinExtractor(
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
        return FeatureType.BASIN;
    }

    @Override
    public List<LakeFeature> extract(AnalysisContext context) {
        List<LakeFeature> features = new ArrayList<>();
        for (ContourTopology.ClosedContour child : context.closedContours()) {
            ContourTopology.ClosedContour parent = topology.parentOf(child, context.closedContours());
            if (parent == null) {
                continue;
            }
            double relief = child.depthM() - parent.depthM();
            if (relief < properties.getBasinMinReliefM()) {
                continue;
            }
            double prominence = Math.min(1.0, relief / Math.max(1.0, properties.getBasinMinReliefM() * 4.0));
            FeatureEvidence evidence = new FeatureEvidence(
                    prominence,
                    true,
                    child.polygon().isValid(),
                    null,
                    supportingTypes(context)
            );
            LakeFeature feature = featureFactory.create(
                    context,
                    FeatureType.BASIN,
                    child.polygon(),
                    parent.depthM(),
                    child.depthM(),
                    relief / Math.max(1.0, GeoMetrics.distanceM(
                            child.polygon().getExteriorRing(),
                            parent.polygon().getExteriorRing()
                    )),
                    null,
                    child.areaM2(),
                    "CONTOUR_NESTING",
                    child.sourceRecordId(),
                    evidence,
                    Map.of("reliefM", relief, "parentDepthM", parent.depthM())
            );
            if (feature != null) {
                features.add(feature);
            }
        }
        return features;
    }

    private int supportingTypes(AnalysisContext context) {
        int count = 1;
        if (!context.bathymetryPoints().isEmpty()) {
            count++;
        }
        if (context.lakeBoundary() != null) {
            count++;
        }
        return count;
    }
}

package com.aifishing.lake.processing.hybrid;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.VisionProperties;
import com.aifishing.lake.processing.confidence.FeatureConfidenceService;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.extract.ContourTopology;
import com.aifishing.lake.processing.extract.FeatureFactory;
import com.aifishing.lake.processing.extract.LakeSourceQuality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HybridMergeServiceTest {

    private HybridMergeService mergeService;
    private AnalysisContext context;
    private Geometry innerHump;
    private Geometry lakeBoundary;

    @BeforeEach
    void setUp() {
        ProcessingProperties properties = new ProcessingProperties();
        ContourTopology topology = new ContourTopology(properties);
        FeatureFactory featureFactory = new FeatureFactory(new FeatureConfidenceService());
        mergeService = new HybridMergeService(new VisionCandidateValidator(properties, topology), featureFactory, new VisionProperties());

        BathymetryContour outer = new BathymetryContour();
        outer.setDepthM(BigDecimal.TEN);
        outer.setSourceRecordId("outer");
        outer.setGeometry(ProcessingFixtures.closedSquare(-78.92, 44.75, 300));
        BathymetryContour inner = new BathymetryContour();
        inner.setDepthM(BigDecimal.valueOf(4));
        inner.setSourceRecordId("inner");
        inner.setGeometry(ProcessingFixtures.closedSquare(-78.92, 44.75, 100));
        List<ContourTopology.ClosedContour> closed = topology.closedPolygons(List.of(outer, inner));
        innerHump = closed.stream().filter(item -> item.depthM() == 4.0).findFirst().orElseThrow().polygon();
        lakeBoundary = ProcessingFixtures.polygonSquare(-78.92, 44.75, 700);

        Lake lake = new Lake();
        lake.setId(UUID.randomUUID());
        lake.setName("Synthetic");
        context = new AnalysisContext(
                lake,
                "hybrid-test",
                UUID.randomUUID(),
                Pipeline.HYBRID,
                Map.of(),
                List.of(outer, inner),
                List.of(),
                List.of(),
                List.of(),
                lakeBoundary,
                new LakeSourceQuality(2, 0, true, true, false, false, false, 40.0),
                closed
        );
    }

    @Test
    void rejectsOutOfLakeAndUnsupportedJunkButKeepsValidatedVisionHumpGisMissed() {
        LakeFeature validVision = source(FeatureType.HUMP, Pipeline.VISION, innerHump, 0.8);
        LakeFeature outOfLake = source(FeatureType.HUMP, Pipeline.VISION, ProcessingFixtures.polygonSquare(-79.5, 45.4, 80), 0.9);
        LakeFeature unsupported = source(FeatureType.HUMP, Pipeline.VISION, ProcessingFixtures.polygonSquare(-78.926, 44.7555, 30), 0.9);

        List<LakeFeature> merged = mergeService.merge(
                FeatureType.HUMP,
                List.of(validVision, outOfLake, unsupported),
                List.of(),
                context
        );
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getPipeline()).isEqualTo(Pipeline.HYBRID);
        assertThat(merged.get(0).getSourceMethod()).isEqualTo(HybridMergeService.SOURCE_VISION);
    }

    @Test
    void mergeDoesNotDuplicateIouOverlappingGisAndVision() {
        LakeFeature vision = source(FeatureType.HUMP, Pipeline.VISION, innerHump, 0.8);
        LakeFeature gis = source(FeatureType.HUMP, Pipeline.GIS, innerHump.copy(), 0.75);
        List<LakeFeature> merged = mergeService.merge(FeatureType.HUMP, List.of(vision), List.of(gis), context);
        assertThat(merged).hasSize(1);
    }

    private LakeFeature source(FeatureType type, Pipeline pipeline, Geometry geometry, double confidence) {
        geometry.setSRID(GeoMapper.SRID);
        LakeFeature feature = new LakeFeature();
        feature.setType(type);
        feature.setPipeline(pipeline);
        feature.setGeometry(geometry);
        feature.setConfidence(BigDecimal.valueOf(confidence));
        feature.setSourceMethod("TEST");
        feature.setProvider("DERIVED");
        feature.setAnalysisVersion("src");
        return feature;
    }
}

package com.aifishing.lake.processing;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.lake.ingestion.repo.BathymetryContourRepository;
import com.aifishing.lake.ingestion.repo.BathymetryPointRepository;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.extract.ContourTopology;
import com.aifishing.lake.processing.render.RenderedTile;
import com.aifishing.lake.processing.repo.LakeAnalysisRunRepository;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.vision.VisionCandidate;
import com.aifishing.lake.processing.vision.VisionMapClient;
import com.aifishing.seed.DevSeedIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StructureBenchmarkIT extends AbstractIntegrationTest {

    @MockitoBean
    VisionMapClient visionMapClient;

    @Autowired
    BathymetryContourRepository contourRepository;

    @Autowired
    BathymetryPointRepository bathymetryPointRepository;

    @Autowired
    LakeWaterwayRepository waterwayRepository;

    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;

    @Autowired
    LakeDatasetStatusRepository datasetStatusRepository;

    @Autowired
    LakeFeatureRepository featureRepository;

    @Autowired
    LakeAnalysisRunRepository analysisRunRepository;

    @BeforeEach
    void stubVision() {
        when(visionMapClient.extract(any(RenderedTile.class), any(AnalysisContext.class))).thenAnswer(invocation -> {
            AnalysisContext context = invocation.getArgument(1);
            List<VisionCandidate> candidates = new ArrayList<>();
            Geometry inner = context.closedContours().stream()
                    .filter(closed -> Math.abs(closed.depthM() - 4.0) < 0.01)
                    .map(ContourTopology.ClosedContour::polygon)
                    .findFirst()
                    .orElse(ProcessingFixtures.polygonSquare(
                            context.lake().getCentroid().getX(),
                            context.lake().getCentroid().getY(),
                            100
                    ));
            candidates.add(new VisionCandidate(FeatureType.HUMP, inner.copy(), 0.86, "nested shallow contour"));
            candidates.add(new VisionCandidate(
                    FeatureType.HUMP,
                    ProcessingFixtures.polygonSquare(-79.6, 45.5, 80),
                    0.99,
                    "out of lake"
            ));
            candidates.add(new VisionCandidate(
                    FeatureType.FLAT,
                    ProcessingFixtures.polygonSquare(-78.92, 44.75, 25),
                    0.4,
                    "tiny unsupported"
            ));
            return candidates;
        });
    }

    @Test
    void visionPersistIsPerPipelineAndHybridValidates() throws Exception {
        ProcessingFixtures.seedFullStructure(
                DevSeedIds.LAKE_ID, 44.75, -78.92,
                contourRepository, bathymetryPointRepository, waterwayRepository,
                boundaryRepository, datasetStatusRepository
        );

        mockMvc.perform(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/process").param("pipeline", "GIS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline", is("GIS")))
                .andExpect(jsonPath("$.processingStatus", is("READY")));

        mockMvc.perform(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/process").param("pipeline", "VISION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline", is("VISION")));

        long visionFirst = featureRepository.countByLakeIdAndPipelineAndType(DevSeedIds.LAKE_ID, Pipeline.VISION, FeatureType.HUMP);
        assertThat(visionFirst).isPositive();
        mockMvc.perform(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/process").param("pipeline", "VISION")))
                .andExpect(status().isOk());
        assertThat(featureRepository.countByLakeIdAndPipelineAndType(DevSeedIds.LAKE_ID, Pipeline.VISION, FeatureType.HUMP))
                .isEqualTo(visionFirst);

        List<LakeFeature> visionHumans = featureRepository.findByLakeIdAndPipelineAndType(
                DevSeedIds.LAKE_ID, Pipeline.VISION, FeatureType.HUMP);
        assertThat(visionHumans).allMatch(feature -> feature.getGeometry().getSRID() == 4326);

        mockMvc.perform(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/process").param("pipeline", "HYBRID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline", is("HYBRID")));

        var gisRun = analysisRunRepository
                .findFirstByLakeIdAndPipelineOrderByStartedAtDesc(DevSeedIds.LAKE_ID, Pipeline.GIS)
                .orElseThrow();
        var visionRun = analysisRunRepository
                .findFirstByLakeIdAndPipelineOrderByStartedAtDesc(DevSeedIds.LAKE_ID, Pipeline.VISION)
                .orElseThrow();
        var hybridRun = analysisRunRepository
                .findFirstByLakeIdAndPipelineOrderByStartedAtDesc(DevSeedIds.LAKE_ID, Pipeline.HYBRID)
                .orElseThrow();
        assertThat(hybridRun.getGisParentRunId()).isEqualTo(gisRun.getId());
        assertThat(hybridRun.getGisAnalysisVersion()).isEqualTo(gisRun.getAnalysisVersion());
        assertThat(hybridRun.getVisionParentRunId()).isEqualTo(visionRun.getId());
        assertThat(hybridRun.getVisionAnalysisVersion()).isEqualTo(visionRun.getAnalysisVersion());
        assertThat(hybridRun.getSourceSnapshotId()).isEqualTo(gisRun.getSourceSnapshotId());
        assertThat(hybridRun.getSourceSnapshotId()).isEqualTo(visionRun.getSourceSnapshotId());
        assertThat(gisRun.getSourceSnapshotId()).isNotBlank();

        assertThat(featureRepository.countByLakeIdAndPipelineAndType(DevSeedIds.LAKE_ID, Pipeline.HYBRID, FeatureType.HUMP))
                .isPositive();
        assertThat(lakeRepository.findById(DevSeedIds.LAKE_ID).orElseThrow().getProcessingStatus()).isEqualTo("READY");

        mockMvc.perform(asDev(get("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/features.geojson").param("pipeline", "VISION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[*].properties.pipeline").value(org.hamcrest.Matchers.everyItem(is("VISION"))));

        mockMvc.perform(asDev(get("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/map.png"))
                        .accept(MediaType.IMAGE_PNG))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void benchmarkMergesScreenshotMetricsAndFourLakeSummary() throws Exception {
        seedLake(DevSeedIds.RICE_LAKE_ID, "Rice Lake", 44.18, -78.17);
        seedLake(DevSeedIds.SCUGOG_LAKE_ID, "Lake Scugog", 44.15, -78.90);
        seedLake(DevSeedIds.SIMCOE_LAKE_ID, "Lake Simcoe", 44.42, -79.37);

        ProcessingFixtures.seedFullStructure(
                DevSeedIds.LAKE_ID, 44.75, -78.92,
                contourRepository, bathymetryPointRepository, waterwayRepository,
                boundaryRepository, datasetStatusRepository
        );
        ProcessingFixtures.seedShorelineOnly(
                DevSeedIds.RICE_LAKE_ID, 44.18, -78.17,
                waterwayRepository, boundaryRepository, datasetStatusRepository
        );
        ProcessingFixtures.seedBathyOnly(
                DevSeedIds.SCUGOG_LAKE_ID, 44.15, -78.90,
                contourRepository, boundaryRepository, datasetStatusRepository
        );
        ProcessingFixtures.seedFullStructure(
                DevSeedIds.SIMCOE_LAKE_ID, 44.42, -79.37,
                contourRepository, bathymetryPointRepository, waterwayRepository,
                boundaryRepository, datasetStatusRepository
        );

        mockMvc.perform(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/benchmark")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelines.GIS", notNullValue()))
                .andExpect(jsonPath("$.pipelines.VISION", notNullValue()))
                .andExpect(jsonPath("$.pipelines.HYBRID", notNullValue()))
                .andExpect(jsonPath("$.gisVsVision.matched", notNullValue()))
                .andExpect(jsonPath("$.externalDirectScreenshotVision", notNullValue()))
                .andExpect(jsonPath("$.externalDirectScreenshotVision.f1", nullValue()))
                .andExpect(jsonPath("$.externalDirectScreenshotVision.notes", notNullValue()))
                .andExpect(jsonPath("$.acceptance.status", is("NOT_SCORED")));

        mockMvc.perform(asDev(get("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/benchmark")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalDirectScreenshotVision.notes", notNullValue()));

        for (UUID lakeId : List.of(DevSeedIds.RICE_LAKE_ID, DevSeedIds.SCUGOG_LAKE_ID, DevSeedIds.SIMCOE_LAKE_ID)) {
            mockMvc.perform(asDev(post("/api/v1/admin/lakes/" + lakeId + "/process").param("pipeline", "HYBRID")))
                    .andExpect(status().isOk());
        }

        String ids = DevSeedIds.LAKE_ID + "," + DevSeedIds.RICE_LAKE_ID + ","
                + DevSeedIds.SCUGOG_LAKE_ID + "," + DevSeedIds.SIMCOE_LAKE_ID;
        mockMvc.perform(asDev(get("/api/v1/admin/lakes/benchmark-summary").param("ids", ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lakes", hasSize(4)))
                .andExpect(jsonPath("$.lakes[0].pipelines.GIS.featureCountByType", notNullValue()))
                .andExpect(jsonPath("$.lakes[0].pipelines.VISION.inLakePercent", notNullValue()))
                .andExpect(jsonPath("$.lakes[0].gisVsVision", notNullValue()))
                .andExpect(jsonPath("$.externalDirectScreenshotVision", notNullValue()));

        mockMvc.perform(asDev(get("/api/v1/lakes/" + DevSeedIds.LAKE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").doesNotExist())
                .andExpect(jsonPath("$.pipeline").doesNotExist());
    }
}

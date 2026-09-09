package com.aifishing.planning;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.AnalysisRunStatus;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.AnalysisContextFactory;
import com.aifishing.lake.processing.repo.LakeAnalysisRunRepository;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.service.StructureSourceFingerprint;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.strategy.service.FishingStrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserGeneratePlanIT extends AbstractIntegrationTest {

    static final String HYBRID_VERSION = "hybrid-v1";

    @Autowired
    LakeFeatureRepository featureRepository;
    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;
    @Autowired
    StrategyRunRepository strategyRunRepository;
    @Autowired
    LakeAnalysisRunRepository analysisRunRepository;
    @Autowired
    AnalysisContextFactory contextFactory;
    @Autowired
    StructureSourceFingerprint fingerprint;

    @MockitoBean
    FishingStrategyService fishingStrategyService;

    @Test
    void emptyBodyCreatesGisStrategyThenPlan() throws Exception {
        seedGisReady();
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        when(fishingStrategyService.generate(eq(tripId), eq(Pipeline.GIS))).thenAnswer(invocation ->
                strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper)));

        String body = mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan").content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.featurePipeline", is("GIS")))
                .andExpect(jsonPath("$.plan.featureAnalysisVersion", is(PlanningFixtures.ANALYSIS_VERSION)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String strategyRunId = objectMapper.readTree(body).get("plan").get("strategyRunId").asText();
        org.assertj.core.api.Assertions.assertThat(strategyRunRepository.findById(UUID.fromString(strategyRunId))).isPresent();
        verify(fishingStrategyService).generate(tripId, Pipeline.GIS);

        mockMvc.perform(asOther(post("/api/v1/trips/" + tripId + "/plan").content("{}")))
                .andExpect(status().isNotFound());
    }

    @Test
    void explicitGisBodyUsesGis() throws Exception {
        seedGisReady();
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        when(fishingStrategyService.generate(eq(tripId), eq(Pipeline.GIS))).thenAnswer(invocation ->
                strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper)));

        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan").content("{\"featurePipeline\":\"GIS\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.featurePipeline", is("GIS")));
        verify(fishingStrategyService).generate(tripId, Pipeline.GIS);
    }

    @Test
    void hybridBodyUsesHybridSnapshot() throws Exception {
        seedHybridReady();
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        when(fishingStrategyService.generate(eq(tripId), eq(Pipeline.HYBRID))).thenAnswer(invocation ->
                strategyRunRepository.save(PlanningFixtures.completedStrategy(
                        tripId, PlanningFixtures.profile(), Pipeline.HYBRID, HYBRID_VERSION, objectMapper)));

        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan").content("{\"featurePipeline\":\"HYBRID\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.featurePipeline", is("HYBRID")))
                .andExpect(jsonPath("$.plan.featureAnalysisVersion", is(HYBRID_VERSION)));
        verify(fishingStrategyService).generate(tripId, Pipeline.HYBRID);
        verify(fishingStrategyService, never()).generate(eq(tripId), eq(Pipeline.GIS));
    }

    @Test
    void visionPipelineIsRejected() throws Exception {
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan").content("{\"featurePipeline\":\"VISION\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("FEATURE_PIPELINE_UNSUPPORTED")));
        verify(fishingStrategyService, never()).generate(any(), any());
    }

    @Test
    void strategyRunIdAndFeaturePipelineConflict() throws Exception {
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        UUID strategyRunId = UUID.randomUUID();
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategyRunId + "\",\"featurePipeline\":\"GIS\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("GENERATE_PLAN_REQUEST_CONFLICT")));
        verify(fishingStrategyService, never()).generate(any(), any());
    }

    @Test
    void hybridMissingFailsWithoutGisFallback() throws Exception {
        seedGisReady();
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan").content("{\"featurePipeline\":\"HYBRID\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("STRUCTURE_PIPELINE_NOT_READY")));
        verify(fishingStrategyService, never()).generate(any(), any());
    }

    @Test
    void planningCapabilitiesExposeGisAndHybridOnly() throws Exception {
        seedGisReady();
        mockMvc.perform(asDev(get("/api/v1/lakes/" + DevSeedIds.LAKE_ID + "/planning-capabilities")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lakeId", is(DevSeedIds.LAKE_ID.toString())))
                .andExpect(jsonPath("$.pipelines.GIS.available", is(true)))
                .andExpect(jsonPath("$.pipelines.GIS.status", is("READY")))
                .andExpect(jsonPath("$.pipelines.GIS.analysisVersion", is(PlanningFixtures.ANALYSIS_VERSION)))
                .andExpect(jsonPath("$.pipelines.HYBRID.available", is(false)))
                .andExpect(jsonPath("$.pipelines.HYBRID.status", is("NOT_READY")))
                .andExpect(jsonPath("$.pipelines.HYBRID.unavailableReason", is("NOT_PROCESSED")))
                .andExpect(jsonPath("$.pipelines.VISION").doesNotExist());
    }

    @Test
    void legacyHybridWithoutMatchingParentsIsProvenanceInvalid() throws Exception {
        seedGisReady();
        String fp = fingerprint();
        LakeAnalysisRun vision = newRun(DevSeedIds.LAKE_ID, Pipeline.VISION, "vision-v1", AnalysisRunStatus.READY, fp);
        LakeAnalysisRun hybrid = newRun(DevSeedIds.LAKE_ID, Pipeline.HYBRID, HYBRID_VERSION, AnalysisRunStatus.READY, fp);
        hybrid.setGisAnalysisVersion("missing-parent-version");
        hybrid.setVisionParentRunId(vision.getId());
        hybrid.setVisionAnalysisVersion(vision.getAnalysisVersion());
        analysisRunRepository.save(hybrid);
        LakeFeature feature = PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 50),
                2.5,
                3.5,
                0.9,
                HYBRID_VERSION
        );
        feature.setPipeline(Pipeline.HYBRID);
        featureRepository.save(feature);

        mockMvc.perform(asDev(get("/api/v1/lakes/" + DevSeedIds.LAKE_ID + "/planning-capabilities")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelines.HYBRID.available", is(false)))
                .andExpect(jsonPath("$.pipelines.HYBRID.unavailableReason", is("PROVENANCE_INVALID")));
    }

    private void seedGisReady() {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        newRun(DevSeedIds.LAKE_ID, Pipeline.GIS, PlanningFixtures.ANALYSIS_VERSION, AnalysisRunStatus.READY, fingerprint());
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 50),
                2.5,
                3.5,
                0.9,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
    }

    private void seedHybridReady() {
        seedGisReady();
        String fp = fingerprint();
        LakeAnalysisRun gis = analysisRunRepository
                .findFirstByLakeIdAndPipelineOrderByStartedAtDesc(DevSeedIds.LAKE_ID, Pipeline.GIS)
                .orElseThrow();
        LakeAnalysisRun vision = newRun(DevSeedIds.LAKE_ID, Pipeline.VISION, "vision-v1", AnalysisRunStatus.READY, fp);
        LakeFeature visionFeature = PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT + 0.001, 40),
                2.5,
                3.5,
                0.8,
                "vision-v1"
        );
        visionFeature.setPipeline(Pipeline.VISION);
        featureRepository.save(visionFeature);
        LakeAnalysisRun hybrid = newRun(DevSeedIds.LAKE_ID, Pipeline.HYBRID, HYBRID_VERSION, AnalysisRunStatus.READY, fp);
        hybrid.setGisParentRunId(gis.getId());
        hybrid.setGisAnalysisVersion(gis.getAnalysisVersion());
        hybrid.setVisionParentRunId(vision.getId());
        hybrid.setVisionAnalysisVersion(vision.getAnalysisVersion());
        analysisRunRepository.save(hybrid);
        LakeFeature hybridFeature = PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 50),
                2.5,
                3.5,
                0.9,
                HYBRID_VERSION
        );
        hybridFeature.setPipeline(Pipeline.HYBRID);
        featureRepository.save(hybridFeature);
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID, Pipeline.HYBRID, HYBRID_VERSION);
    }

    private LakeAnalysisRun newRun(UUID lakeId, Pipeline pipeline, String version, AnalysisRunStatus status, String sourceSnapshotId) {
        LakeAnalysisRun run = new LakeAnalysisRun();
        run.setLakeId(lakeId);
        run.setPipeline(pipeline);
        run.setStatus(status);
        run.setAnalysisVersion(version);
        run.setAlgorithmVersion("1.0.0");
        run.setStartedAt(Instant.now());
        run.setCompletedAt(Instant.now());
        run.setSourceSnapshotId(sourceSnapshotId);
        if (pipeline == Pipeline.HYBRID) {
            LakeAnalysisRun gis = analysisRunRepository
                    .findFirstByLakeIdAndPipelineOrderByStartedAtDesc(lakeId, Pipeline.GIS)
                    .orElseThrow();
            LakeAnalysisRun vision = analysisRunRepository
                    .findFirstByLakeIdAndPipelineOrderByStartedAtDesc(lakeId, Pipeline.VISION)
                    .orElseGet(() -> {
                        LakeAnalysisRun placeholder = new LakeAnalysisRun();
                        placeholder.setLakeId(lakeId);
                        placeholder.setPipeline(Pipeline.VISION);
                        placeholder.setStatus(AnalysisRunStatus.READY);
                        placeholder.setAnalysisVersion("vision-placeholder");
                        placeholder.setAlgorithmVersion("1.0.0");
                        placeholder.setStartedAt(Instant.now());
                        placeholder.setCompletedAt(Instant.now());
                        placeholder.setSourceSnapshotId(sourceSnapshotId);
                        return analysisRunRepository.save(placeholder);
                    });
            run.setGisParentRunId(gis.getId());
            run.setGisAnalysisVersion(gis.getAnalysisVersion());
            run.setVisionParentRunId(vision.getId());
            run.setVisionAnalysisVersion(vision.getAnalysisVersion());
        }
        return analysisRunRepository.save(run);
    }

    private String fingerprint() {
        Lake lake = lakeRepository.findById(DevSeedIds.LAKE_ID).orElseThrow();
        return fingerprint.id(contextFactory.create(lake, "fingerprint", null, Pipeline.GIS).sourceDatasetSnapshot());
    }
}

@TestPropertySource(properties = "app.strategy.feature-pipeline=HYBRID")
class UserGeneratePlanYamlIsolationIT extends AbstractIntegrationTest {

    @Autowired
    LakeFeatureRepository featureRepository;
    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;
    @Autowired
    StrategyRunRepository strategyRunRepository;
    @Autowired
    LakeAnalysisRunRepository analysisRunRepository;
    @Autowired
    AnalysisContextFactory contextFactory;
    @Autowired
    StructureSourceFingerprint fingerprint;

    @MockitoBean
    FishingStrategyService fishingStrategyService;

    @Test
    void omittedBodyStaysGisWhenStrategyYamlIsHybrid() throws Exception {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        Lake lake = lakeRepository.findById(DevSeedIds.LAKE_ID).orElseThrow();
        String fp = fingerprint.id(contextFactory.create(lake, "fingerprint", null, Pipeline.GIS).sourceDatasetSnapshot());
        LakeAnalysisRun run = new LakeAnalysisRun();
        run.setLakeId(DevSeedIds.LAKE_ID);
        run.setPipeline(Pipeline.GIS);
        run.setStatus(AnalysisRunStatus.READY);
        run.setAnalysisVersion(PlanningFixtures.ANALYSIS_VERSION);
        run.setAlgorithmVersion("1.0.0");
        run.setStartedAt(Instant.now());
        run.setCompletedAt(Instant.now());
        run.setSourceSnapshotId(fp);
        analysisRunRepository.save(run);
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 50),
                2.5,
                3.5,
                0.9,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        when(fishingStrategyService.generate(eq(tripId), eq(Pipeline.GIS))).thenAnswer(invocation ->
                strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper)));

        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan").content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.featurePipeline", is("GIS")));
        verify(fishingStrategyService).generate(tripId, Pipeline.GIS);
        verify(fishingStrategyService, never()).generate(eq(tripId), eq(Pipeline.HYBRID));
    }
}

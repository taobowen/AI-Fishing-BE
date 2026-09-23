package com.aifishing.planning;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.ClientChannel;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.dto.AnalysisRunStatus;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.AnalysisContextFactory;
import com.aifishing.lake.processing.repo.LakeAnalysisRunRepository;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.service.StructureSourceFingerprint;
import com.aifishing.planning.domain.PlanningRun;
import com.aifishing.planning.domain.PlanningRunStatus;
import com.aifishing.planning.repo.PlanningRunRepository;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.strategy.service.FishingStrategyService;
import com.aifishing.webquota.domain.WebPlanGenerationRequest;
import com.aifishing.webquota.domain.WebPlanRequestStatus;
import com.aifishing.webquota.repo.WebPlanGenerationRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AsyncGeneratePlanIT extends AbstractIntegrationTest {

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
    @Autowired
    PlanningRunRepository planningRunRepository;
    @Autowired
    WebPlanGenerationRequestRepository webRequestRepository;

    @MockitoBean
    FishingStrategyService fishingStrategyService;

    @Test
    void preferAsyncReturns202ThenPollCompletes() throws Exception {
        seedGisReady();
        UUID tripId = createTripAndStubStrategy();

        String body = mockMvc.perform(asWeb(post("/api/v1/trips/" + tripId + "/plan")
                        .header("Prefer", "respond-async")
                        .header("Idempotency-Key", "async-1")
                        .content("{}")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("RUNNING")))
                .andExpect(jsonPath("$.planningRunId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID runId = UUID.fromString(objectMapper.readTree(body).get("planningRunId").asText());

        awaitRunStatus(tripId, runId, PlanningRunStatus.COMPLETED);
        mockMvc.perform(asWeb(get("/api/v1/trips/" + tripId + "/plan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("GENERATED")));
    }

    @Test
    void preferAsyncFailedExecuteIsVisibleOnPoll() throws Exception {
        seedGisWithoutSnapshot();
        UUID tripId = createTripAndStubStrategy();

        String body = mockMvc.perform(asWeb(post("/api/v1/trips/" + tripId + "/plan")
                        .header("Prefer", "respond-async")
                        .header("Idempotency-Key", "async-fail")
                        .content("{}")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("RUNNING")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID runId = UUID.fromString(objectMapper.readTree(body).get("planningRunId").asText());

        String run = awaitRunStatus(tripId, runId, PlanningRunStatus.FAILED);
        assertThat(objectMapper.readTree(run).get("errorMessage").asText()).isEqualTo("SPATIAL_SNAPSHOT_NOT_READY");
        mockMvc.perform(asWeb(get("/api/v1/trips/" + tripId + "/plan")))
                .andExpect(status().isNotFound());
    }

    @Test
    void sameIdempotencyKeyWhileRunningReplaysSameRun() throws Exception {
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        PlanningRun running = new PlanningRun();
        running.setTripId(tripId);
        running.setStatus(PlanningRunStatus.RUNNING);
        running.setStartedAt(Instant.now());
        running.setClientChannel(ClientChannel.WEB);
        running = planningRunRepository.saveAndFlush(running);

        WebPlanGenerationRequest row = new WebPlanGenerationRequest();
        row.setUserId(DevSeedIds.USER_ID);
        row.setIdempotencyKey("in-flight");
        row.setTripId(tripId);
        row.setPlanningRunId(running.getId());
        row.setStatus(WebPlanRequestStatus.STARTED);
        webRequestRepository.saveAndFlush(row);

        mockMvc.perform(asWeb(post("/api/v1/trips/" + tripId + "/plan")
                        .header("Prefer", "respond-async")
                        .header("Idempotency-Key", "in-flight")
                        .content("{}")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.planningRunId", is(running.getId().toString())))
                .andExpect(jsonPath("$.status", is("RUNNING")));
        assertThat(planningRunRepository.findByTripIdOrderByStartedAtDesc(tripId)).hasSize(1);
    }

    @Test
    void ownerPollIs404ForOtherUser() throws Exception {
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        PlanningRun running = new PlanningRun();
        running.setTripId(tripId);
        running.setStatus(PlanningRunStatus.RUNNING);
        running.setStartedAt(Instant.now());
        running = planningRunRepository.saveAndFlush(running);

        mockMvc.perform(asOther(get("/api/v1/trips/" + tripId + "/planning-runs/" + running.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void staleRunningRunFailsOnOwnerGet() throws Exception {
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        PlanningRun running = new PlanningRun();
        running.setTripId(tripId);
        running.setStatus(PlanningRunStatus.RUNNING);
        running.setStartedAt(Instant.now().minus(Duration.ofMinutes(16)));
        running.setClientChannel(ClientChannel.WEB);
        running = planningRunRepository.saveAndFlush(running);

        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId + "/planning-runs/" + running.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.errorMessage", is("GENERATION_TIMEOUT")));
    }

    private String awaitRunStatus(UUID tripId, UUID runId, PlanningRunStatus expected) throws Exception {
        String last = null;
        for (int i = 0; i < 40; i++) {
            last = mockMvc.perform(asWeb(get("/api/v1/trips/" + tripId + "/planning-runs/" + runId)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (expected.name().equals(objectMapper.readTree(last).get("status").asText())) {
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Run " + runId + " did not reach " + expected + ": " + last);
    }

    private UUID createTripAndStubStrategy() {
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        when(fishingStrategyService.generate(eq(tripId), eq(Pipeline.GIS))).thenAnswer(invocation ->
                strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper)));
        return tripId;
    }

    private void seedGisReady() {
        seedGisAnalysisAndFeatures();
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
    }

    private void seedGisWithoutSnapshot() {
        seedGisAnalysisAndFeatures();
    }

    private void seedGisAnalysisAndFeatures() {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        LakeAnalysisRun run = new LakeAnalysisRun();
        run.setLakeId(DevSeedIds.LAKE_ID);
        run.setPipeline(Pipeline.GIS);
        run.setStatus(AnalysisRunStatus.READY);
        run.setAnalysisVersion(PlanningFixtures.ANALYSIS_VERSION);
        run.setAlgorithmVersion("1.0.0");
        run.setStartedAt(Instant.now());
        run.setCompletedAt(Instant.now());
        run.setSourceSnapshotId(fingerprint());
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
    }

    private String fingerprint() {
        Lake lake = lakeRepository.findById(DevSeedIds.LAKE_ID).orElseThrow();
        return fingerprint.id(contextFactory.create(lake, "fingerprint", null, Pipeline.GIS).sourceDatasetSnapshot());
    }
}

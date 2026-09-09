package com.aifishing.webquota;

import com.aifishing.AbstractIntegrationTest;
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
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.strategy.service.FishingStrategyService;
import com.aifishing.webquota.repo.UserWebPlanEntitlementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebPlanQuotaIT extends AbstractIntegrationTest {

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
    UserWebPlanEntitlementRepository entitlementRepository;

    @MockitoBean
    FishingStrategyService fishingStrategyService;

    @Test
    void startsAtZeroAndSuccessfulWebGenerationConsumesOne() throws Exception {
        seedGisReady();
        mockMvc.perform(asWeb(get("/api/v1/me/web-plan-entitlement")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit", is(3)))
                .andExpect(jsonPath("$.used", is(0)))
                .andExpect(jsonPath("$.remaining", is(3)));

        UUID tripId = createTripAndStubStrategy();
        mockMvc.perform(asWeb(post("/api/v1/web/trips/" + tripId + "/plan")
                        .header("Idempotency-Key", "key-1")
                        .content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        mockMvc.perform(asWeb(get("/api/v1/me/web-plan-entitlement")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.used", is(1)))
                .andExpect(jsonPath("$.remaining", is(2)));
    }

    @Test
    void failedGenerationDoesNotConsumeQuota() throws Exception {
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        when(fishingStrategyService.generate(eq(tripId), eq(Pipeline.GIS))).thenAnswer(invocation ->
                strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper)));

        mockMvc.perform(asWeb(post("/api/v1/trips/" + tripId + "/plan")
                        .header("Idempotency-Key", "fail-key")
                        .content("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("STRUCTURE_PIPELINE_NOT_READY")));

        mockMvc.perform(asWeb(get("/api/v1/me/web-plan-entitlement")))
                .andExpect(jsonPath("$.used", is(0)));
    }

    @Test
    void fourthWebGenerationIsRejectedAndSameKeyReplays() throws Exception {
        seedGisReady();
        for (int i = 1; i <= 3; i++) {
            UUID tripId = createTripAndStubStrategy();
            mockMvc.perform(asWeb(post("/api/v1/trips/" + tripId + "/plan")
                            .header("Idempotency-Key", "gen-" + i)
                            .content("{}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COMPLETED")));
        }
        UUID fourth = createTripAndStubStrategy();
        mockMvc.perform(asWeb(post("/api/v1/trips/" + fourth + "/plan")
                        .header("Idempotency-Key", "gen-4")
                        .content("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("WEB_PLAN_QUOTA_EXHAUSTED")));

        mockMvc.perform(asWeb(get("/api/v1/me/plans")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(3)));

        UUID firstTrip = tripRepository.findOwned(DevSeedIds.USER_ID, null, null, null).getFirst().getId();
        String first = mockMvc.perform(asWeb(get("/api/v1/trips/" + firstTrip + "/plan")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String replay = mockMvc.perform(asWeb(post("/api/v1/trips/" + firstTrip + "/plan")
                        .header("Idempotency-Key", "gen-1")
                        .content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(replay).get("plan").get("id"))
                .isEqualTo(objectMapper.readTree(first).get("id"));
        mockMvc.perform(asWeb(get("/api/v1/me/web-plan-entitlement")))
                .andExpect(jsonPath("$.used", is(3)));
    }

    @Test
    void mobileGenerationDoesNotConsumeWebQuota() throws Exception {
        seedGisReady();
        UUID tripId = createTripAndStubStrategy();
        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan").content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
        mockMvc.perform(asDev(get("/api/v1/me/web-plan-entitlement")))
                .andExpect(jsonPath("$.used", is(0)));
    }

    @Test
    void missingIdempotencyKeyRejectedForWebClient() throws Exception {
        seedGisReady();
        UUID tripId = createTripAndStubStrategy();
        mockMvc.perform(asWeb(post("/api/v1/trips/" + tripId + "/plan").content("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_KEY_REQUIRED")));
    }

    @Test
    void parallelWebGenerationsCannotExceedLimit() throws Exception {
        seedGisReady();
        entitlementRepository.saveAndFlush(used(2));
        UUID tripA = createTripAndStubStrategy();
        UUID tripB = createTripAndStubStrategy();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Future<Integer> one = pool.submit(() -> {
            start.await();
            return mockMvc.perform(asWeb(post("/api/v1/trips/" + tripA + "/plan")
                            .header("Idempotency-Key", "par-a")
                            .content("{}")))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        });
        Future<Integer> two = pool.submit(() -> {
            start.await();
            return mockMvc.perform(asWeb(post("/api/v1/trips/" + tripB + "/plan")
                            .header("Idempotency-Key", "par-b")
                            .content("{}")))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        });
        start.countDown();
        int statusA = one.get(60, TimeUnit.SECONDS);
        int statusB = two.get(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThat(java.util.List.of(statusA, statusB)).contains(200);
        mockMvc.perform(asWeb(get("/api/v1/me/web-plan-entitlement")))
                .andExpect(jsonPath("$.used", is(3)));
    }

    @Test
    void webDefaultBoatIsHiddenFromLockerList() throws Exception {
        mockMvc.perform(asWeb(post("/api/v1/me/boats/web-default")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemGenerated", is(true)))
                .andExpect(jsonPath("$.provenance", is("WEB_DEFAULT")));
        mockMvc.perform(asDev(get("/api/v1/me/boats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(asWeb(post("/api/v1/me/boats/web-default")))
                .andExpect(status().isOk());
        mockMvc.perform(asDev(get("/api/v1/me/boats?includeSystem=true")))
                .andExpect(jsonPath("$.length()", is(1)));
        mockMvc.perform(asWeb(post("/api/v1/me/boats/web-default").param("preset", "PADDLE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provenance", is("WEB_DEFAULT_PADDLE")))
                .andExpect(jsonPath("$.type", is("KAYAK")));
        mockMvc.perform(asWeb(post("/api/v1/me/boats/web-default").param("preset", "MOTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provenance", is("WEB_DEFAULT_MOTOR")));
        mockMvc.perform(asWeb(post("/api/v1/me/boats/web-default").param("preset", "BASS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provenance", is("WEB_DEFAULT_BASS")))
                .andExpect(jsonPath("$.primaryTransitPropulsionType", is("GAS_OUTBOARD")));
        mockMvc.perform(asDev(get("/api/v1/me/boats")))
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(asDev(get("/api/v1/me/boats?includeSystem=true")))
                .andExpect(jsonPath("$.length()", is(4)));
    }

    private com.aifishing.webquota.domain.UserWebPlanEntitlement used(int count) {
        var row = new com.aifishing.webquota.domain.UserWebPlanEntitlement();
        row.setUserId(DevSeedIds.USER_ID);
        row.setSuccessfulGenerations(count);
        row.setLifetimeLimit(3);
        return row;
    }

    private UUID createTripAndStubStrategy() {
        UUID tripId = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE)).getId();
        when(fishingStrategyService.generate(eq(tripId), eq(Pipeline.GIS))).thenAnswer(invocation ->
                strategyRunRepository.save(PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper)));
        return tripId;
    }

    private void seedGisReady() {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        LakeAnalysisRun run = new LakeAnalysisRun();
        run.setLakeId(DevSeedIds.LAKE_ID);
        run.setPipeline(Pipeline.GIS);
        run.setStatus(AnalysisRunStatus.READY);
        run.setAnalysisVersion(PlanningFixtures.ANALYSIS_VERSION);
        run.setAlgorithmVersion("1.0.0");
        run.setStartedAt(Instant.now());
        run.setCompletedAt(Instant.now());
        Lake lake = lakeRepository.findById(DevSeedIds.LAKE_ID).orElseThrow();
        run.setSourceSnapshotId(fingerprint.id(contextFactory.create(lake, "fingerprint", null, Pipeline.GIS).sourceDatasetSnapshot()));
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
    }
}

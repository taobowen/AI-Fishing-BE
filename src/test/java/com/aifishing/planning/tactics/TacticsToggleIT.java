package com.aifishing.planning.tactics;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.repo.TripPlanRepository;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TacticsToggleIT extends AbstractIntegrationTest {

    @Autowired
    LakeFeatureRepository featureRepository;
    @Autowired
    StrategyRunRepository strategyRunRepository;
    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;
    @Autowired
    TripPlanRepository tripPlanRepository;
    @MockitoSpyBean
    IdealTacticsAiClient aiClient;
    @MockitoSpyBean
    IdealTacticHeuristic heuristic;

    @Test
    void includeFalseSkipsTacticsAndLeavesPlanUsable() throws Exception {
        seedPlanLake();
        PlannedTrip planned = saveTripAndStrategy();
        String body = mockMvc.perform(generate(planned, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.tacticsStatus", is("NONE")))
                .andExpect(jsonPath("$.plan.tacticsRequested", is(false)))
                .andExpect(jsonPath("$.plan.waypoints[0].tactical").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode plan = objectMapper.readTree(body).path("plan");
        assertThat(plan.path("waypoints")).isNotEmpty();
        verify(aiClient, never()).recommend(anyList(), any());
        verify(heuristic, never()).recommend(anyList(), any());
    }

    @Test
    void includeTruePersistsTactics() throws Exception {
        seedPlanLake();
        mockMvc.perform(generate(saveTripAndStrategy(), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.tacticsStatus", is("READY")))
                .andExpect(jsonPath("$.plan.tacticsRequested", is(true)))
                .andExpect(jsonPath("$.plan.waypoints[0].tactical.idealTactic.lureFamily").exists());
    }

    @Test
    void enrichmentDoesNotReplanAndDoesNotConsumeWebQuota() throws Exception {
        seedPlanLake();
        PlannedTrip planned = saveTripAndStrategy();
        String generated = mockMvc.perform(asWeb(post("/api/v1/trips/" + planned.tripId() + "/plan")
                        .header("Idempotency-Key", "tactics-quota-1")
                        .content("{\"strategyRunId\":\"" + planned.strategyRunId() + "\",\"includeAiTactics\":false}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.tacticsStatus", is("NONE")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode plan = objectMapper.readTree(generated).path("plan");
        String spatialBefore = spatialSignature(plan);
        String remainingBefore = mockMvc.perform(asWeb(get("/api/v1/me/web-plan-entitlement")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String enriched = mockMvc.perform(asWeb(post("/api/v1/trips/" + planned.tripId()
                        + "/plans/" + plan.path("id").asText() + "/tactics")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tacticsStatus", is("READY")))
                .andExpect(jsonPath("$.waypoints[0].tactical.idealTactic.lureFamily").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode after = objectMapper.readTree(enriched);
        assertThat(spatialSignature(after)).isEqualTo(spatialBefore);
        assertThat(after.path("version").asInt()).isEqualTo(plan.path("version").asInt());

        String remainingAfter = mockMvc.perform(asWeb(get("/api/v1/me/web-plan-entitlement")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(remainingAfter).path("remaining").asInt())
                .isEqualTo(objectMapper.readTree(remainingBefore).path("remaining").asInt());
    }

    @Test
    void readyRepeatDoesNotCallAiAgain() throws Exception {
        seedPlanLake();
        PlannedTrip planned = saveTripAndStrategy();
        String generated = mockMvc.perform(generate(planned, true))
                .andExpect(jsonPath("$.plan.tacticsStatus", is("READY")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID planId = UUID.fromString(objectMapper.readTree(generated).path("plan").path("id").asText());
        mockMvc.perform(asDev(post("/api/v1/trips/" + planned.tripId() + "/plans/" + planId + "/tactics")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tacticsStatus", is("READY")));
        verify(aiClient, never()).recommend(anyList(), any());
    }

    @Test
    void heuristicFallbackMarksReady() throws Exception {
        seedPlanLake();
        org.mockito.Mockito.doReturn(true).when(aiClient).configured();
        org.mockito.Mockito.doThrow(new IllegalStateException("openai down"))
                .when(aiClient).recommend(anyList(), any());
        mockMvc.perform(generate(saveTripAndStrategy(), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.tacticsStatus", is("READY")))
                .andExpect(jsonPath("$.plan.waypoints[0].tactical.idealTactic.lureFamily").exists());
    }

    @Test
    void heuristicAndAiFailureMarksFailedAndKeepsPlan() throws Exception {
        seedPlanLake();
        org.mockito.Mockito.doReturn(true).when(aiClient).configured();
        org.mockito.Mockito.doThrow(new IllegalStateException("openai down"))
                .when(aiClient).recommend(anyList(), any());
        org.mockito.Mockito.doThrow(new IllegalStateException("heuristic down"))
                .when(heuristic).recommend(anyList(), any());
        String body = mockMvc.perform(generate(saveTripAndStrategy(), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.tacticsStatus", is("FAILED")))
                .andExpect(jsonPath("$.plan.waypoints[0].sequence").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode tactical = objectMapper.readTree(body).path("plan").path("waypoints").get(0).path("tactical");
        assertThat(tactical.isMissingNode() || tactical.isNull()).isTrue();
    }

    @Test
    void staleGeneratingClaimIsRetryable() throws Exception {
        seedPlanLake();
        PlannedTrip planned = saveTripAndStrategy();
        String generated = mockMvc.perform(generate(planned, false))
                .andExpect(jsonPath("$.plan.tacticsStatus", is("NONE")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID planId = UUID.fromString(objectMapper.readTree(generated).path("plan").path("id").asText());
        TripPlan plan = tripPlanRepository.findById(planId).orElseThrow();
        plan.setTacticsStatus(TacticsStatus.GENERATING);
        plan.setTacticsStartedAt(Instant.now().minusSeconds(10_000));
        tripPlanRepository.saveAndFlush(plan);

        mockMvc.perform(asDev(post("/api/v1/trips/" + planned.tripId() + "/plans/" + planId + "/tactics")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tacticsStatus", is("READY")));
    }

    @Test
    void concurrentEnrichmentIsSingleFlight() throws Exception {
        seedPlanLake();
        PlannedTrip planned = saveTripAndStrategy();
        String generated = mockMvc.perform(generate(planned, false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID planId = UUID.fromString(objectMapper.readTree(generated).path("plan").path("id").asText());
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger recommendCalls = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            recommendCalls.incrementAndGet();
            start.countDown();
            Thread.sleep(250);
            return invocation.callRealMethod();
        }).when(heuristic).recommend(anyList(), any());

        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> mockMvc.perform(asDev(post(
                    "/api/v1/trips/" + planned.tripId() + "/plans/" + planId + "/tactics")))
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
            assertThat(start.await(3, TimeUnit.SECONDS)).isTrue();
            Future<String> second = executor.submit(() -> mockMvc.perform(asDev(post(
                    "/api/v1/trips/" + planned.tripId() + "/plans/" + planId + "/tactics")))
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
            JsonNode a = objectMapper.readTree(first.get(5, TimeUnit.SECONDS));
            JsonNode b = objectMapper.readTree(second.get(5, TimeUnit.SECONDS));
            assertThat(List.of(a.path("tacticsStatus").asText(), b.path("tacticsStatus").asText()))
                    .contains("READY");
            verify(heuristic, atMost(1)).recommend(anyList(), any());
            assertThat(recommendCalls.get()).isLessThanOrEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void seedPlanLake() {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 50),
                2.5,
                3.5,
                0.86,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT + 0.002, 50),
                2.5,
                3.5,
                0.8,
                PlanningFixtures.ANALYSIS_VERSION
        ));
        ensureSpatialSnapshot(DevSeedIds.LAKE_ID);
    }

    private String spatialSignature(JsonNode plan) {
        StringBuilder out = new StringBuilder();
        for (JsonNode waypoint : plan.path("waypoints")) {
            out.append(waypoint.path("sequence").asInt()).append('|')
                    .append(waypoint.path("fishingTargetId").asText()).append('|')
                    .append(waypoint.path("plannedArrivalAt").asText()).append('|')
                    .append(waypoint.path("plannedDepartureAt").asText()).append('|')
                    .append(waypoint.path("plannedDwellMinutes").asText()).append(';');
        }
        for (JsonNode leg : plan.path("transitLegs")) {
            out.append(leg.path("sequence").asInt()).append('|')
                    .append(leg.path("plannedTravelMinutes").asText()).append(';');
        }
        return out.toString();
    }

    private PlannedTrip saveTripAndStrategy() {
        Trip trip = PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE);
        UUID tripId = tripRepository.save(trip).getId();
        UUID strategyRunId = strategyRunRepository.save(PlanningFixtures.completedStrategy(
                tripId, PlanningFixtures.profile(), objectMapper)).getId();
        return new PlannedTrip(tripId, strategyRunId);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder generate(
            PlannedTrip planned,
            boolean includeAiTactics
    ) {
        return asDev(post("/api/v1/trips/" + planned.tripId() + "/plan")
                .content("{\"strategyRunId\":\"" + planned.strategyRunId()
                        + "\",\"includeAiTactics\":" + includeAiTactics + "}"));
    }

    private record PlannedTrip(UUID tripId, UUID strategyRunId) {
    }
}

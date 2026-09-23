package com.aifishing.guidance.empirical;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.guidance.contracts.SeasonBucket;
import com.aifishing.guidance.learning.GuidanceLearningOutboxClaimer;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmpiricalMemoryIT extends AbstractIntegrationTest {

    @Autowired
    private HistoricalContributionStore contributionStore;

    @Autowired
    private HistoricalPerformanceStore performanceStore;

    @Autowired
    private EmpiricalAggregationService aggregationService;

    @Autowired
    private GuidanceLearningOutboxClaimer claimer;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void sessionCompleteEnqueuesAggregateEmpirical() throws Exception {
        Seed seed = startAndEndSession();

        Integer jobs = jdbcTemplate.queryForObject(
                """
                        select count(*) from guidance_learning_outbox
                        where fishing_session_id = ? and job_type = 'AGGREGATE_EMPIRICAL'
                        """,
                Integer.class,
                UUID.fromString(seed.sessionId)
        );
        assertThat(jobs).isEqualTo(1);
    }

    @Test
    void replayReplaceDoesNotDoubleCount() {
        UUID sessionId = UUID.randomUUID();
        insertSession(sessionId);
        EmpiricalGrain grain = grain(null);
        List<SessionContribution> rows = List.of(
                new SessionContribution(grain, new EmpiricalRawCounts(3600, 4, 2, 0, 1))
        );
        Instant rebuilt = Instant.parse("2026-09-16T15:00:00Z");
        tx().executeWithoutResult(status -> {
            contributionStore.replaceSession(sessionId, rows, rebuilt, "hash-1");
            performanceStore.rebuildGrains(Set.of(grain));
        });
        tx().executeWithoutResult(status -> {
            contributionStore.replaceSession(sessionId, rows, rebuilt.plusSeconds(1), "hash-1");
            performanceStore.rebuildGrains(Set.of(grain));
        });

        Map<String, Object> historical = readHistorical(grain);
        assertThat(intVal(historical.get("bite_count"))).isEqualTo(4);
        assertThat(intVal(historical.get("fish_on_count"))).isEqualTo(2);
        assertThat(intVal(historical.get("landed_count"))).isEqualTo(0);
        assertThat(((Number) historical.get("fishing_effort_seconds")).longValue()).isEqualTo(3600L);
        Integer contribs = jdbcTemplate.queryForObject(
                "select count(*) from historical_performance_contributions where fishing_session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(contribs).isEqualTo(1);
    }

    @Test
    void fourTwoZeroIsDistinctFromZeroAndVoidDropsLandedOnly() {
        UUID signals = UUID.randomUUID();
        UUID empty = UUID.randomUUID();
        insertSession(signals);
        insertSession(empty);
        EmpiricalGrain grain = grain(null);
        Instant rebuilt = Instant.parse("2026-09-16T15:30:00Z");
        tx().executeWithoutResult(status -> {
            contributionStore.replaceSession(signals, List.of(
                    new SessionContribution(grain, new EmpiricalRawCounts(3600, 4, 2, 1, 1))), rebuilt, "s1");
            contributionStore.replaceSession(empty, List.of(
                    new SessionContribution(grain, new EmpiricalRawCounts(3600, 0, 0, 0, 1))), rebuilt, "e1");
            performanceStore.rebuildGrains(Set.of(grain));
        });
        Map<String, Object> combined = readHistorical(grain);
        assertThat(intVal(combined.get("bite_count"))).isEqualTo(4);
        assertThat(intVal(combined.get("fish_on_count"))).isEqualTo(2);
        assertThat(intVal(combined.get("landed_count"))).isEqualTo(1);

        tx().executeWithoutResult(status -> {
            contributionStore.replaceSession(signals, List.of(
                    new SessionContribution(grain, new EmpiricalRawCounts(3600, 4, 2, 0, 1))), rebuilt.plusSeconds(1), "s2");
            performanceStore.rebuildGrains(Set.of(grain));
        });
        Map<String, Object> voided = readHistorical(grain);
        assertThat(intVal(voided.get("landed_count"))).isEqualTo(0);
        assertThat(intVal(voided.get("fish_on_count"))).isEqualTo(2);
        assertThat(intVal(voided.get("bite_count"))).isEqualTo(4);
        assertThat(((Number) voided.get("smoothed_score")).doubleValue())
                .isEqualTo(((Number) combined.get("smoothed_score")).doubleValue());
    }

    @Test
    void concurrentRebuildEqualsManualSum() throws Exception {
        UUID sessionA = UUID.randomUUID();
        UUID sessionB = UUID.randomUUID();
        insertSession(sessionA);
        insertSession(sessionB);
        EmpiricalGrain grain = grain(null);
        List<SessionContribution> a = List.of(new SessionContribution(grain, new EmpiricalRawCounts(1800, 4, 2, 0, 1)));
        List<SessionContribution> b = List.of(new SessionContribution(grain, new EmpiricalRawCounts(3600, 0, 0, 0, 1)));
        Instant rebuilt = Instant.parse("2026-09-16T16:00:00Z");
        tx().executeWithoutResult(status -> {
            contributionStore.replaceSession(sessionA, a, rebuilt, "a");
            contributionStore.replaceSession(sessionB, b, rebuilt, "b");
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<?> first = pool.submit(() -> {
            await(start);
            tx().executeWithoutResult(status -> performanceStore.rebuildGrains(Set.of(grain)));
        });
        Future<?> second = pool.submit(() -> {
            await(start);
            tx().executeWithoutResult(status -> performanceStore.rebuildGrains(Set.of(grain)));
        });
        start.countDown();
        first.get(15, TimeUnit.SECONDS);
        second.get(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        Map<String, Object> historical = readHistorical(grain);
        EmpiricalRawCounts expected = a.getFirst().raw().plus(b.getFirst().raw());
        EmpiricalAlgorithm.Derived derived = EmpiricalAlgorithm.derive(expected, new com.aifishing.guidance.GuidanceProperties().getEmpirical());
        assertThat(((Number) historical.get("fishing_effort_seconds")).longValue()).isEqualTo(expected.fishingEffortSeconds());
        assertThat(intVal(historical.get("bite_count"))).isEqualTo(expected.biteCount());
        assertThat(intVal(historical.get("fish_on_count"))).isEqualTo(expected.fishOnCount());
        assertThat(intVal(historical.get("landed_count"))).isEqualTo(expected.landedCount());
        assertThat(((Number) historical.get("smoothed_score")).doubleValue())
                .isCloseTo(derived.smoothedScore(), org.assertj.core.data.Offset.offset(1e-6));
        assertThat(claimer.drain()).isGreaterThanOrEqualTo(0);
    }

    private Seed startAndEndSession() throws Exception {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(Instant.parse("2026-09-02T12:00:00Z"));
        plan.setPlanningAlgorithmVersion("empirical-test");
        tripPlanRepository.save(plan);
        TripWaypoint wp1 = new TripWaypoint();
        wp1.setTripPlanId(plan.getId());
        wp1.setSequence(1);
        wp1.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG)));
        wp1.setReason("wp1");
        tripWaypointRepository.save(wp1);
        MvcResult started = mockMvc.perform(asDev(post("/api/v1/trips/" + trip.getId() + "/fishing-sessions")).content("{}"))
                .andReturn();
        JsonNode tree = objectMapper.readTree(started.getResponse().getContentAsString());
        String sessionId = tree.get("id").asText();
        Instant startedAt = Instant.parse(tree.get("startedAt").asText());
        Instant t0 = startedAt.plusSeconds(30);
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/waypoints/" + wp1.getId() + "/arrive"))
                .content(event("arr", t0.toString())));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/locations"))
                .content(batch(
                        point("p1", t0.plusSeconds(10), PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG),
                        point("p2", t0.plusSeconds(20), PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG),
                        point("p3", t0.plusSeconds(30), PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG)
                )));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/end"))
                        .content(event("end", t0.plusSeconds(40).toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        return new Seed(sessionId, startedAt, wp1.getId(), UUID.randomUUID(), UUID.randomUUID());
    }

    private void insertSession(UUID sessionId) {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        jdbcTemplate.update(
                """
                        insert into fishing_sessions (
                            id, trip_id, user_id, started_at, status, total_paused_seconds,
                            activity_state, activity_state_source, created_at
                        ) values (?, ?, ?, now(), 'COMPLETED', 0, 'UNKNOWN', 'UNKNOWN', now())
                        """,
                sessionId,
                trip.getId(),
                DevSeedIds.USER_ID
        );
    }

    private EmpiricalGrain grain(UUID waypointId) {
        return new EmpiricalGrain(
                DevSeedIds.LAKE_ID,
                null,
                waypointId,
                FishSpecies.SMALLMOUTH_BASS,
                SeasonBucket.FALL,
                null,
                null,
                null,
                null,
                null,
                EmpiricalAlgorithm.VERSION
        );
    }

    private Map<String, Object> readHistorical(EmpiricalGrain grain) {
        return jdbcTemplate.queryForMap(
                """
                        select fishing_effort_seconds, bite_count, fish_on_count, landed_count, smoothed_score
                        from historical_performance
                        where lake_id is not distinct from ?
                          and trip_waypoint_id is not distinct from ?
                          and species is not distinct from ?
                          and season_bucket is not distinct from ?
                          and empirical_algorithm_version = ?
                        """,
                grain.lakeId(),
                grain.tripWaypointId(),
                grain.species() == null ? null : grain.species().name(),
                grain.seasonBucket() == null ? null : grain.seasonBucket().name(),
                grain.algorithmVersion()
        );
    }

    private static int intVal(Object raw) {
        return ((Number) raw).intValue();
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent rebuild did not start");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static String event(String id, String occurredAt) {
        return "{\"clientEventId\":\"%s\",\"occurredAt\":\"%s\"}".formatted(id, occurredAt);
    }

    private static String point(String id, Instant recordedAt, double lat, double lng) {
        return "{\"clientPointId\":\"%s\",\"recordedAt\":\"%s\",\"location\":{\"lat\":%s,\"lng\":%s},\"accuracyM\":8}"
                .formatted(id, recordedAt, lat, lng);
    }

    private static String batch(String... points) {
        return "{\"points\":[" + String.join(",", points) + "]}";
    }

    private record Seed(String sessionId, Instant startedAt, UUID wp1, UUID fish1, UUID fish2) {
    }
}

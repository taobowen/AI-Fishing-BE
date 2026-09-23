package com.aifishing.guidance.dispatch;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxRepository;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxSource;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxStatus;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuidancePhase3DispatchIT extends AbstractIntegrationTest {

    static final double WP1_LAT = PlanningFixtures.HEAD_LAT;
    static final double WP1_LNG = PlanningFixtures.HEAD_LNG;

    @Autowired
    private GuidanceTriggerOutboxService outboxService;
    @Autowired
    private GuidanceTriggerOutboxRepository outboxRepository;
    @Autowired
    private GuidanceTriggerOutboxClaimer claimer;
    @Autowired
    private GuidanceHeartbeatJob heartbeatJob;

    @Test
    void fishOnThenCatchWithSameIdWritesOneFishOn() throws Exception {
        Seed seed = startSession();
        Instant t0 = seed.startedAt.plusSeconds(60);
        UUID interaction = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/waypoints/" + seed.wp1 + "/arrive"))
                .content(event("arr", t0.toString())));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/fish-on"))
                        .content(fishOn("fo-1", t0.plusSeconds(10).toString(), interaction)))
                .andExpect(status().isNoContent());
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches"))
                        .content(catchBody("c1", t0.plusSeconds(20).toString(), seed.wp1, interaction)))
                .andExpect(status().isCreated());

        assertThat(countEvents(seed.sessionId, "FISH_ON")).isEqualTo(1);
        assertThat(countEvents(seed.sessionId, "CATCH_CREATED")).isEqualTo(1);
        assertThat(countEvents(seed.sessionId, "WAYPOINT_ENTERED")).isEqualTo(1);
    }

    @Test
    void directCatchSynthesizesFishOn() throws Exception {
        Seed seed = startSession();
        Instant t0 = seed.startedAt.plusSeconds(60);
        UUID interaction = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches"))
                        .content(catchBody("direct", t0.toString(), seed.wp1, interaction)))
                .andExpect(status().isCreated());

        Integer fishOn = jdbcTemplate.queryForObject(
                """
                select count(*) from session_events
                where fishing_session_id = ? and type = 'FISH_ON' and fish_interaction_id = ?
                """,
                Integer.class,
                seed.sessionId,
                interaction
        );
        assertThat(fishOn).isEqualTo(1);
        assertThat(countEvents(seed.sessionId, "CATCH_CREATED")).isEqualTo(1);
    }

    @Test
    void safetyPendingThenNoBiteKeepsSafetyPrimary() throws Exception {
        Seed seed = startSession();
        outboxService.upsert(
                seed.sessionId,
                new TriggerRoutingDecision(GuidanceTrigger.SAFETY_STATE_CHANGED, List.of(), List.of("SAFETY_THUNDERSTORM")),
                GuidanceTriggerOutboxSource.EVENT
        );
        outboxService.upsert(
                seed.sessionId,
                new TriggerRoutingDecision(GuidanceTrigger.NO_BITE_THRESHOLD, List.of(), List.of("NO_BITE_THRESHOLD_CROSSED")),
                GuidanceTriggerOutboxSource.HEARTBEAT
        );

        GuidanceTriggerOutboxEntity open = outboxRepository
                .findFirstByFishingSessionIdAndStatusIn(
                        seed.sessionId,
                        List.of(GuidanceTriggerOutboxStatus.PENDING, GuidanceTriggerOutboxStatus.CLAIMED)
                )
                .orElseThrow();
        assertThat(open.getPrimaryTrigger()).isEqualTo(GuidanceTrigger.SAFETY_STATE_CHANGED);
        assertThat(open.getRelatedTriggers()).contains(GuidanceTrigger.NO_BITE_THRESHOLD);
        assertThat(open.getReasonCodes()).contains("SAFETY_THUNDERSTORM", "NO_BITE_THRESHOLD_CROSSED");
        assertThat(outboxRepository.findByFishingSessionIdAndCreatedAtGreaterThanEqual(
                seed.sessionId, Instant.EPOCH
        )).hasSize(1);
    }

    @Test
    void staleClaimedRowIsReclaimedAndMarkedDone() throws Exception {
        Seed seed = startSession();
        GuidanceTriggerOutboxEntity row = new GuidanceTriggerOutboxEntity();
        row.setFishingSessionId(seed.sessionId);
        row.setPrimaryTrigger(GuidanceTrigger.WAYPOINT_REACHED);
        row.setRelatedTriggers(List.of());
        row.setReasonCodes(List.of("WAYPOINT_ENTERED"));
        row.setSource(GuidanceTriggerOutboxSource.EVENT);
        row.setStatus(GuidanceTriggerOutboxStatus.CLAIMED);
        row.setCreatedAt(Instant.parse("2026-09-16T12:00:00Z"));
        row.setClaimedAt(Instant.parse("2026-09-16T12:00:00Z"));
        row.setClaimToken(UUID.randomUUID());
        outboxRepository.saveAndFlush(row);

        assertThat(claimer.drain()).isGreaterThanOrEqualTo(1);

        GuidanceTriggerOutboxEntity done = outboxRepository.findById(row.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(GuidanceTriggerOutboxStatus.DONE);
        Integer runs = jdbcTemplate.queryForObject(
                "select count(*) from agent_runs where fishing_session_id = ?",
                Integer.class,
                seed.sessionId
        );
        assertThat(runs).isGreaterThanOrEqualTo(1);
        Integer versions = jdbcTemplate.queryForObject(
                "select count(*) from guidance_plan_versions where fishing_session_id = ?",
                Integer.class,
                seed.sessionId
        );
        assertThat(versions).isGreaterThanOrEqualTo(1);
        Integer tripPlans = jdbcTemplate.queryForObject(
                "select count(*) from trip_plans where id = ?",
                Integer.class,
                seed.planId
        );
        assertThat(tripPlans).isEqualTo(1);
    }

    @Test
    void heartbeatCrossingIsDedupedUntilReset() throws Exception {
        Seed seed = startSession();
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/waypoints/" + seed.wp1 + "/arrive"))
                .content(event("arr-hb", seed.startedAt.plusSeconds(30).toString())));
        Instant fishingSince = Instant.now().minus(Duration.ofMinutes(30));
        jdbcTemplate.update(
                """
                update fishing_sessions
                set activity_state = 'FISHING',
                    activity_state_source = 'PROGRESS',
                    activity_state_since = ?
                where id = ?
                """,
                Timestamp.from(fishingSince),
                seed.sessionId
        );
        entityManager.clear();

        assertThat(heartbeatJob.tick()).isEqualTo(1);
        assertThat(heartbeatJob.tick()).isZero();
        assertThat(countEvents(seed.sessionId, "NO_BITE")).isEqualTo(1);

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/bites"))
                        .content(bite("b1", Instant.now().toString())))
                .andExpect(status().isNoContent());
        entityManager.clear();
        assertThat(heartbeatJob.tick()).isZero();
    }

    @Test
    void currentGuidanceIs404UntilADecisionExists() throws Exception {
        Seed seed = startSession();
        mockMvc.perform(asDev(get("/api/v1/fishing-sessions/" + seed.sessionId + "/guidance/current")))
                .andExpect(status().isNotFound());
    }

    private Seed startSession() throws Exception {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(Instant.parse("2026-09-02T12:00:00Z"));
        plan.setPlanningAlgorithmVersion("phase3-dispatch-test");
        tripPlanRepository.save(plan);
        TripWaypoint wp1 = new TripWaypoint();
        wp1.setTripPlanId(plan.getId());
        wp1.setSequence(1);
        wp1.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(WP1_LAT, WP1_LNG)));
        wp1.setReason("wp 1");
        tripWaypointRepository.save(wp1);
        MvcResult started = mockMvc.perform(asDev(post("/api/v1/trips/" + trip.getId() + "/fishing-sessions")).content("{}"))
                .andReturn();
        JsonNode tree = objectMapper.readTree(started.getResponse().getContentAsString());
        return new Seed(
                UUID.fromString(tree.get("id").asText()),
                Instant.parse(tree.get("startedAt").asText()),
                wp1.getId(),
                plan.getId()
        );
    }

    private int countEvents(UUID sessionId, String type) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from session_events where fishing_session_id = ? and type = ?",
                Integer.class,
                sessionId,
                type
        );
        return count == null ? 0 : count;
    }

    private static String event(String id, String occurredAt) {
        return "{\"clientEventId\":\"%s\",\"occurredAt\":\"%s\"}".formatted(id, occurredAt);
    }

    private static String bite(String id, String occurredAt) {
        return event(id, occurredAt);
    }

    private static String fishOn(String id, String occurredAt, UUID interaction) {
        return "{\"clientEventId\":\"%s\",\"occurredAt\":\"%s\",\"fishInteractionId\":\"%s\"}"
                .formatted(id, occurredAt, interaction);
    }

    private static String catchBody(String id, String occurredAt, UUID waypointId, UUID interaction) {
        return "{\"clientCatchId\":\"%s\",\"occurredAt\":\"%s\",\"waypointId\":\"%s\",\"fishInteractionId\":\"%s\"}"
                .formatted(id, occurredAt, waypointId, interaction);
    }

    private record Seed(UUID sessionId, Instant startedAt, UUID wp1, UUID planId) {
    }
}

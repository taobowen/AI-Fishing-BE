package com.aifishing.feedback;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatchEventIT extends AbstractIntegrationTest {

    static final double WP1_LAT = PlanningFixtures.HEAD_LAT;
    static final double WP1_LNG = PlanningFixtures.HEAD_LNG;
    static final double WP2_LAT = PlanningFixtures.HEAD_LAT + 0.003;
    static final double WP2_LNG = PlanningFixtures.HEAD_LNG;

    @Test
    void createIsIdempotentAndAssociatesClientWaypointWithoutGps() throws Exception {
        Seed seed = startSession();
        Instant t0 = seed.startedAt.plusSeconds(60);

        String body = catchBody("c1", t0.toString(), seed.wp1, null, null);
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientCatchId", is("c1")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.outcome", is("PENDING")))
                .andExpect(jsonPath("$.associationMethod", is("CURRENT_WAYPOINT")))
                .andExpect(jsonPath("$.tripWaypointId", is(seed.wp1.toString())))
                .andExpect(jsonPath("$.location").doesNotExist());

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientCatchId", is("c1")));

        mockMvc.perform(asDev(get("/api/v1/fishing-sessions/" + seed.sessionId + "/catches")))
                .andExpect(jsonPath("$.length()", is(1)));

        Integer planVersion = jdbcTemplate.queryForObject(
                """
                select p.version from trip_plans p
                join fishing_sessions s on s.trip_plan_id = p.id
                where s.id = ?
                """,
                Integer.class,
                UUID.fromString(seed.sessionId)
        );
        org.assertj.core.api.Assertions.assertThat(planVersion).isEqualTo(1);
    }

    @Test
    void otherUserGets404AndCompletedWaypointIsNotTrusted() throws Exception {
        Seed seed = startSession();
        Instant t0 = seed.startedAt.plusSeconds(60);
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/waypoints/" + seed.wp1 + "/complete"))
                        .content(event("done", t0.plusSeconds(120).toString())))
                .andExpect(jsonPath("$.waypoints[0].status", is("COMPLETED")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches"))
                        .content(catchBody("late", t0.plusSeconds(180).toString(), seed.wp1, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.associationMethod", is("UNASSOCIATED")))
                .andExpect(jsonPath("$.tripWaypointId").doesNotExist());

        String catchId = readId(mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches"))
                        .content(catchBody("owned", t0.toString(), seed.wp1, null, null)))
                .andReturn());
        mockMvc.perform(asOther(get("/api/v1/catches/" + catchId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(asOther(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches"))
                        .content(catchBody("other", t0.toString(), seed.wp1, null, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void nearestWaypointUsedWhenClientIdMissingAndGpsPresent() throws Exception {
        Seed seed = startSession();
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches"))
                        .content(catchBody("near", seed.startedAt.plusSeconds(90).toString(), null, WP2_LAT, WP2_LNG)))
                .andExpect(jsonPath("$.associationMethod", is("NEAREST_WAYPOINT")))
                .andExpect(jsonPath("$.tripWaypointId", is(seed.wp2.toString())));
    }

    @Test
    void delayedCatchOnCompletedRecomputesPerformanceWithoutNewEffort() throws Exception {
        Seed seed = startSession();
        Instant t0 = seed.startedAt.plusSeconds(30);
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/waypoints/" + seed.wp1 + "/arrive"))
                .content(event("arr", t0.toString())));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/locations"))
                .content(batch(
                        point("p1", t0.plusSeconds(10), WP1_LAT, WP1_LNG, 8),
                        point("p2", t0.plusSeconds(20), WP1_LAT, WP1_LNG, 8),
                        point("p3", t0.plusSeconds(30), WP1_LAT, WP1_LNG, 8)
                )));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/end"))
                        .content(event("end", t0.plusSeconds(40).toString())))
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        int effortCount = jdbcTemplate.queryForObject(
                "select count(*) from fishing_effort_segments where fishing_session_id = ?",
                Integer.class,
                UUID.fromString(seed.sessionId)
        );

        MvcResult created = mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches"))
                        .content(catchBody("post", t0.plusSeconds(15).toString(), seed.wp1, WP1_LAT, WP1_LNG)))
                .andExpect(status().isCreated())
                .andReturn();
        String catchId = readId(created);

        mockMvc.perform(asDev(patch("/api/v1/catches/" + catchId))
                        .content("{\"outcome\":\"LANDED\",\"species\":\"SMALLMOUTH_BASS\"}"))
                .andExpect(jsonPath("$.outcome", is("LANDED")));

        mockMvc.perform(asDev(get("/api/v1/fishing-sessions/" + seed.sessionId + "/performance")))
                .andExpect(jsonPath("$.landedCount", is(1)))
                .andExpect(jsonPath("$.fishingEffortSeconds").isNumber());

        mockMvc.perform(asDev(post("/api/v1/catches/" + catchId + "/void")))
                .andExpect(jsonPath("$.status", is("VOIDED")));
        mockMvc.perform(asDev(get("/api/v1/fishing-sessions/" + seed.sessionId + "/performance")))
                .andExpect(jsonPath("$.landedCount", is(0)));

        Integer after = jdbcTemplate.queryForObject(
                "select count(*) from fishing_effort_segments where fishing_session_id = ?",
                Integer.class,
                UUID.fromString(seed.sessionId)
        );
        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(effortCount);

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches"))
                        .content(catchBody("too-late", t0.plusSeconds(90).toString(), seed.wp1, null, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void landedV2FieldsPersistAndSpeciesStaysOptional() throws Exception {
        Seed seed = startSession();
        Instant t0 = seed.startedAt.plusSeconds(60);

        String pendingId = readId(mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches"))
                        .content(catchBody("v2-no", t0.toString(), seed.wp1, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.species").doesNotExist())
                .andExpect(jsonPath("$.isTargetSpecies").doesNotExist())
                .andExpect(jsonPath("$.sizeBucket").doesNotExist())
                .andReturn());

        mockMvc.perform(asDev(patch("/api/v1/catches/" + pendingId))
                        .content("{\"outcome\":\"LANDED\",\"isTargetSpecies\":false,\"sizeBucket\":\"SMALL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("LANDED")))
                .andExpect(jsonPath("$.isTargetSpecies", is(false)))
                .andExpect(jsonPath("$.sizeBucket", is("SMALL")))
                .andExpect(jsonPath("$.species").doesNotExist())
                .andExpect(jsonPath("$.lengthCm").doesNotExist());

        mockMvc.perform(asDev(get("/api/v1/catches/" + pendingId)))
                .andExpect(jsonPath("$.isTargetSpecies", is(false)))
                .andExpect(jsonPath("$.sizeBucket", is("SMALL")));

        String yesId = readId(mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches"))
                        .content(catchBody("v2-yes", t0.plusSeconds(1).toString(), seed.wp1, null, null)))
                .andReturn());
        mockMvc.perform(asDev(patch("/api/v1/catches/" + yesId))
                        .content("{\"outcome\":\"LANDED\",\"species\":\"SMALLMOUTH_BASS\",\"isTargetSpecies\":true,\"sizeBucket\":\"BIG\"}"))
                .andExpect(jsonPath("$.isTargetSpecies", is(true)))
                .andExpect(jsonPath("$.sizeBucket", is("BIG")))
                .andExpect(jsonPath("$.species", is("SMALLMOUTH_BASS")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches"))
                        .content("""
                                {"clientCatchId":"v2-create","occurredAt":"%s","waypointId":"%s","isTargetSpecies":true,"sizeBucket":"AVERAGE"}
                                """.formatted(t0.plusSeconds(2), seed.wp1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isTargetSpecies", is(true)))
                .andExpect(jsonPath("$.sizeBucket", is("AVERAGE")));

        mockMvc.perform(asDev(patch("/api/v1/catches/" + yesId))
                        .content("{\"sizeBucket\":\"HUGE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelledSessionRejectsCatch() throws Exception {
        Seed seed = startSession();
        jdbcTemplate.update("update fishing_sessions set status = 'CANCELLED' where id = ?", UUID.fromString(seed.sessionId));
        entityManager.clear();
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/catches"))
                        .content(catchBody("x", seed.startedAt.plusSeconds(30).toString(), seed.wp1, null, null)))
                .andExpect(status().isBadRequest());
    }

    private Seed startSession() throws Exception {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(Instant.parse("2026-09-02T12:00:00Z"));
        plan.setPlanningAlgorithmVersion("catch-test");
        tripPlanRepository.save(plan);
        TripWaypoint wp1 = waypoint(plan.getId(), 1, WP1_LAT, WP1_LNG);
        TripWaypoint wp2 = waypoint(plan.getId(), 2, WP2_LAT, WP2_LNG);
        tripWaypointRepository.save(wp1);
        tripWaypointRepository.save(wp2);
        MvcResult started = mockMvc.perform(asDev(post("/api/v1/trips/" + trip.getId() + "/fishing-sessions")).content("{}"))
                .andReturn();
        JsonNode tree = objectMapper.readTree(started.getResponse().getContentAsString());
        return new Seed(tree.get("id").asText(), Instant.parse(tree.get("startedAt").asText()), wp1.getId(), wp2.getId());
    }

    private TripWaypoint waypoint(UUID planId, int sequence, double lat, double lng) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setTripPlanId(planId);
        waypoint.setSequence(sequence);
        waypoint.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(lat, lng)));
        waypoint.setReason("wp " + sequence);
        return waypoint;
    }

    private static String catchBody(String id, String occurredAt, UUID waypointId, Double lat, Double lng) {
        StringBuilder json = new StringBuilder("{\"clientCatchId\":\"").append(id)
                .append("\",\"occurredAt\":\"").append(occurredAt).append("\"");
        if (waypointId != null) {
            json.append(",\"waypointId\":\"").append(waypointId).append("\"");
        }
        if (lat != null && lng != null) {
            json.append(",\"location\":{\"lat\":").append(lat).append(",\"lng\":").append(lng).append("},\"accuracyM\":8");
        }
        json.append("}");
        return json.toString();
    }

    private static String event(String id, String occurredAt) {
        return "{\"clientEventId\":\"%s\",\"occurredAt\":\"%s\"}".formatted(id, occurredAt);
    }

    private static String point(String id, Instant recordedAt, double lat, double lng, double accuracyM) {
        return "{\"clientPointId\":\"%s\",\"recordedAt\":\"%s\",\"location\":{\"lat\":%s,\"lng\":%s},\"accuracyM\":%s}"
                .formatted(id, recordedAt, lat, lng, accuracyM);
    }

    private static String batch(String... points) {
        return "{\"points\":[" + String.join(",", points) + "]}";
    }

    private String readId(MvcResult result) throws Exception {
        JsonNode tree = objectMapper.readTree(result.getResponse().getContentAsString());
        return tree.get("id").asText();
    }

    private record Seed(String sessionId, Instant startedAt, UUID wp1, UUID wp2) {
    }
}

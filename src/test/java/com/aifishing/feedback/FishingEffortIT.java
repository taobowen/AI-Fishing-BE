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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FishingEffortIT extends AbstractIntegrationTest {

    static final double LAT = PlanningFixtures.HEAD_LAT;
    static final double LNG = PlanningFixtures.HEAD_LNG;

    @Test
    void gpsPairsNearArrivedWaypointAreFishingAndPauseIsExcluded() throws Exception {
        Seed seed = startSession(List.of());
        Instant t0 = Instant.parse("2026-09-02T18:00:00Z");
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/waypoints/" + seed.wp1 + "/arrive"))
                .content(event("arr", t0.toString())));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/locations"))
                .content(batch(
                        point("a", t0.plusSeconds(10), LAT, LNG, 8),
                        point("b", t0.plusSeconds(20), LAT, LNG, 8)
                )));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/pause"))
                .content(event("p", t0.plusSeconds(25).toString())));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/resume"))
                .content(event("r", t0.plusSeconds(55).toString())));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/locations"))
                .content(batch(
                        point("e", t0.plusSeconds(60), LAT, LNG, 8),
                        point("f", t0.plusSeconds(70), LAT, LNG, 8)
                )));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/end"))
                .content(event("end", t0.plusSeconds(80).toString())));

        Integer pausedOpen = jdbcTemplate.queryForObject(
                "select count(*) from session_pause_intervals where fishing_session_id = ? and resumed_at is not null",
                Integer.class,
                UUID.fromString(seed.sessionId)
        );
        assertThat(pausedOpen).isEqualTo(1);

        List<Map<String, Object>> fishing = jdbcTemplate.queryForList(
                "select duration_seconds from fishing_effort_segments where fishing_session_id = ? and segment_type = 'FISHING'",
                UUID.fromString(seed.sessionId)
        );
        int fishingSeconds = fishing.stream().mapToInt(row -> ((Number) row.get("duration_seconds")).intValue()).sum();
        assertThat(fishingSeconds).isEqualTo(20);

        mockMvc.perform(asDev(get("/api/v1/fishing-sessions/" + seed.sessionId + "/performance")))
                .andExpect(jsonPath("$.fishingEffortSeconds", is(20)))
                .andExpect(jsonPath("$.bestWaypointId").doesNotExist());
    }

    @Test
    void excessiveGpsGapIsUnknownNotFishing() throws Exception {
        Seed seed = startSession(List.of());
        Instant t0 = Instant.parse("2026-09-02T19:00:00Z");
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/waypoints/" + seed.wp1 + "/arrive"))
                .content(event("arr", t0.toString())));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/locations"))
                .content(batch(
                        point("g1", t0.plusSeconds(10), LAT, LNG, 8),
                        point("g2", t0.plusSeconds(310), LAT, LNG, 8)
                )));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/end"))
                .content(event("end", t0.plusSeconds(320).toString())));

        Integer unknown = jdbcTemplate.queryForObject(
                "select coalesce(sum(duration_seconds),0) from fishing_effort_segments where fishing_session_id = ? and segment_type = 'UNKNOWN'",
                Integer.class,
                UUID.fromString(seed.sessionId)
        );
        Integer fishing = jdbcTemplate.queryForObject(
                "select coalesce(sum(duration_seconds),0) from fishing_effort_segments where fishing_session_id = ? and segment_type = 'FISHING'",
                Integer.class,
                UUID.fromString(seed.sessionId)
        );
        assertThat(unknown).isGreaterThanOrEqualTo(300);
        assertThat(fishing).isEqualTo(0);
    }

    @Test
    void trollingStaysFishingWhileMovingInsideRadius() throws Exception {
        Seed seed = startSession(List.of("TROLLING"));
        Instant t0 = Instant.parse("2026-09-02T20:00:00Z");
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/waypoints/" + seed.wp1 + "/arrive"))
                .content(event("arr", t0.toString())));
        double lat2 = LAT + 0.0002;
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/locations"))
                .content(batch(
                        point("t1", t0.plusSeconds(10), LAT, LNG, 8),
                        point("t2", t0.plusSeconds(20), lat2, LNG, 8)
                )));
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/end"))
                .content(event("end", t0.plusSeconds(30).toString())));
        Integer fishing = jdbcTemplate.queryForObject(
                "select coalesce(sum(duration_seconds),0) from fishing_effort_segments where fishing_session_id = ? and segment_type = 'FISHING'",
                Integer.class,
                UUID.fromString(seed.sessionId)
        );
        assertThat(fishing).isEqualTo(10);
    }

    private Seed startSession(List<String> techniques) throws Exception {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(Instant.parse("2026-09-02T12:00:00Z"));
        plan.setPlanningAlgorithmVersion("effort-test");
        tripPlanRepository.save(plan);
        TripWaypoint wp1 = new TripWaypoint();
        wp1.setTripPlanId(plan.getId());
        wp1.setSequence(1);
        wp1.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(LAT, LNG)));
        wp1.setRecommendedTechniques(techniques);
        tripWaypointRepository.save(wp1);
        TripWaypoint wp2 = new TripWaypoint();
        wp2.setTripPlanId(plan.getId());
        wp2.setSequence(2);
        wp2.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(LAT + 0.003, LNG)));
        tripWaypointRepository.save(wp2);
        String sessionId = readId(mockMvc.perform(asDev(post("/api/v1/trips/" + trip.getId() + "/fishing-sessions")).content("{}"))
                .andReturn());
        return new Seed(sessionId, wp1.getId());
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

    private record Seed(String sessionId, UUID wp1) {
    }
}

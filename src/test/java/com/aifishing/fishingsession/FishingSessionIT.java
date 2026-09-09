package com.aifishing.fishingsession;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FishingSessionIT extends AbstractIntegrationTest {

    static final double WP1_LAT = PlanningFixtures.HEAD_LAT;
    static final double WP1_LNG = PlanningFixtures.HEAD_LNG;
    static final double WP2_LAT = PlanningFixtures.HEAD_LAT + 0.003;
    static final double WP2_LNG = PlanningFixtures.HEAD_LNG;

    @Test
    void startRequiresGeneratedPlanAndRejectsSecondUnfinished() throws Exception {
        Seed seed = seedPlan(TripPlanStatus.GENERATED);

        MvcResult created = mockMvc.perform(asDev(post("/api/v1/trips/" + seed.tripId + "/fishing-sessions")).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.tripPlanId", is(seed.planId.toString())))
                .andExpect(jsonPath("$.planVersion", is(1)))
                .andExpect(jsonPath("$.waypoints[0].status", is("NAVIGATING")))
                .andExpect(jsonPath("$.waypoints[1].status", is("UPCOMING")))
                .andReturn();
        String sessionId = readId(created);

        mockMvc.perform(asDev(post("/api/v1/trips/" + seed.tripId + "/fishing-sessions")).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));

        mockMvc.perform(asOther(get("/api/v1/fishing-sessions/" + sessionId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(asDev(get("/api/v1/fishing-sessions/" + sessionId)))
                .andExpect(jsonPath("$.tripPlanId", is(seed.planId.toString())));
    }

    @Test
    void pausedSessionAlsoBlocksANewStart() throws Exception {
        Seed seed = seedPlan(TripPlanStatus.ACCEPTED);
        String sessionId = readId(mockMvc.perform(asDev(post("/api/v1/trips/" + seed.tripId + "/fishing-sessions")).content("{}"))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/pause"))
                        .content(event("pause-1", "2026-09-02T16:10:00Z")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAUSED")));

        Seed other = seedPlan(TripPlanStatus.GENERATED);
        mockMvc.perform(asDev(post("/api/v1/trips/" + other.tripId + "/fishing-sessions")).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pauseResumeAccumulatesAndEndWhilePausedFoldsOpenInterval() throws Exception {
        Seed seed = seedPlan(TripPlanStatus.GENERATED);
        String sessionId = readId(mockMvc.perform(asDev(post("/api/v1/trips/" + seed.tripId + "/fishing-sessions")).content("{}"))
                .andReturn());

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/pause"))
                        .content(event("p1", "2026-09-02T16:10:00Z")))
                .andExpect(jsonPath("$.status", is("PAUSED")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/resume"))
                        .content(event("r1", "2026-09-02T16:10:20Z")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.totalPausedSeconds", is(20)));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/pause"))
                        .content(event("p2", "2026-09-02T16:11:00Z")))
                .andExpect(jsonPath("$.status", is("PAUSED")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/resume"))
                        .content(event("r2", "2026-09-02T16:11:15Z")))
                .andExpect(jsonPath("$.totalPausedSeconds", is(35)));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/pause"))
                        .content(event("p3", "2026-09-02T16:12:00Z")))
                .andExpect(jsonPath("$.status", is("PAUSED")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/end"))
                        .content(event("end-paused", "2026-09-02T16:12:12Z")))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.summary.totalPausedSeconds", is(47)))
                .andExpect(jsonPath("$.summary.activeFishingSeconds").exists());
    }

    @Test
    void duplicateClientEventIdDoesNotApplyTwiceAndSkipIsNotResurrected() throws Exception {
        Seed seed = seedPlan(TripPlanStatus.GENERATED);
        String sessionId = readId(mockMvc.perform(asDev(post("/api/v1/trips/" + seed.tripId + "/fishing-sessions")).content("{}"))
                .andReturn());

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/waypoints/" + seed.wp1 + "/skip"))
                        .content(event("skip-1", "2026-09-02T16:20:00Z")))
                .andExpect(jsonPath("$.waypoints[0].status", is("SKIPPED")))
                .andExpect(jsonPath("$.waypoints[0].skipReason", is("USER")))
                .andExpect(jsonPath("$.waypoints[1].status", is("NAVIGATING")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/waypoints/" + seed.wp1 + "/skip"))
                        .content(event("skip-1", "2026-09-02T16:20:00Z")))
                .andExpect(jsonPath("$.waypoints[0].status", is("SKIPPED")))
                .andExpect(jsonPath("$.waypoints[1].status", is("NAVIGATING")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/waypoints/" + seed.wp1 + "/arrive"))
                        .content(event("arrive-late", "2026-09-02T16:19:00Z")))
                .andExpect(jsonPath("$.waypoints[0].status", is("SKIPPED")));

        mockMvc.perform(asDev(get("/api/v1/admin/fishing-sessions/" + sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientEventCount", is(2)))
                .andExpect(jsonPath("$.session.tripPlanId", is(seed.planId.toString())));
    }

    @Test
    void arrivalNeedsTimeAndSampleCountAndQualityFlagsAreStored() throws Exception {
        Seed seed = seedPlan(TripPlanStatus.GENERATED);
        String sessionId = readId(mockMvc.perform(asDev(post("/api/v1/trips/" + seed.tripId + "/fishing-sessions")).content("{}"))
                .andReturn());

        Instant t0 = Instant.parse("2026-09-02T17:00:00Z");
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/locations"))
                        .content(batch(
                                point("sparse-1", t0, WP1_LAT, WP1_LNG, 8),
                                point("sparse-2", t0.plusSeconds(30), WP1_LAT, WP1_LNG, 8)
                        )))
                .andExpect(jsonPath("$.waypoints[0].status", is("NAVIGATING")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/locations"))
                        .content(batch(
                                point("a", t0.plusSeconds(60), WP1_LAT, WP1_LNG, 8),
                                point("b", t0.plusSeconds(70), WP1_LAT, WP1_LNG, 8),
                                point("c", t0.plusSeconds(80), WP1_LAT, WP1_LNG, 8),
                                point("d", t0.plusSeconds(90), WP1_LAT, WP1_LNG, 8)
                        )))
                .andExpect(jsonPath("$.waypoints[0].status", is("FISHING")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/locations"))
                        .content(batch(point("low", t0.plusSeconds(100), WP1_LAT, WP1_LNG, 120))))
                .andExpect(status().isOk());

        mockMvc.perform(asDev(get("/api/v1/fishing-sessions/" + sessionId + "/track")))
                .andExpect(jsonPath("$.points[?(@.quality == 'LOW_QUALITY')].quality").exists());

        mockMvc.perform(asDev(get("/api/v1/fishing-sessions/" + sessionId + "/navigation")))
                .andExpect(jsonPath("$.currentWaypoint.status", is("FISHING")))
                .andExpect(jsonPath("$.sessionStatus", is("ACTIVE")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/end"))
                        .content(event("end-1", "2026-09-02T17:05:00Z")))
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/end"))
                        .content(event("end-1", "2026-09-02T17:05:00Z")))
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/locations"))
                        .content(batch(point("after", t0.plusSeconds(200), WP1_LAT, WP1_LNG, 8))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pausedGpsDoesNotAutoAdvanceAndManualCompletePromotesNext() throws Exception {
        Seed seed = seedPlan(TripPlanStatus.GENERATED);
        String sessionId = readId(mockMvc.perform(asDev(post("/api/v1/trips/" + seed.tripId + "/fishing-sessions")).content("{}"))
                .andReturn());

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/pause"))
                        .content(event("pause-gps", "2026-09-02T18:00:00Z")))
                .andExpect(jsonPath("$.status", is("PAUSED")));

        Instant t0 = Instant.parse("2026-09-02T18:00:10Z");
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/locations"))
                        .content(batch(
                                point("p-a", t0, WP1_LAT, WP1_LNG, 8),
                                point("p-b", t0.plusSeconds(10), WP1_LAT, WP1_LNG, 8),
                                point("p-c", t0.plusSeconds(20), WP1_LAT, WP1_LNG, 8),
                                point("p-d", t0.plusSeconds(30), WP1_LAT, WP1_LNG, 8)
                        )))
                .andExpect(jsonPath("$.waypoints[0].status", is("NAVIGATING")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/resume"))
                        .content(event("resume-gps", "2026-09-02T18:01:00Z")));

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/waypoints/" + seed.wp1 + "/complete"))
                        .content(event("done-1", "2026-09-02T18:02:00Z")))
                .andExpect(jsonPath("$.waypoints[0].status", is("COMPLETED")))
                .andExpect(jsonPath("$.waypoints[1].status", is("NAVIGATING")));
    }

    @Test
    void lateStartSkipsExpiredStopsAndLeavesInWindowStop() throws Exception {
        Instant now = Instant.now();
        Seed seed = seedTimedPlan(
                now.minusSeconds(7200), now.minusSeconds(60),
                now.minusSeconds(300), now.plusSeconds(3600)
        );
        mockMvc.perform(asDev(post("/api/v1/trips/" + seed.tripId + "/fishing-sessions"))
                        .content("{\"lateStart\":\"SKIP_EXPIRED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waypoints[0].status", is("SKIPPED")))
                .andExpect(jsonPath("$.waypoints[0].skipReason", is("LATE_START")))
                .andExpect(jsonPath("$.waypoints[1].status", is("NAVIGATING")));
        mockMvc.perform(asDev(get("/api/v1/trips/" + seed.tripId + "/plan")))
                .andExpect(jsonPath("$.waypoints[0].plannedDepartureAt").exists());
    }

    @Test
    void lateStartSkipsTwoExpiredStops() throws Exception {
        Instant now = Instant.now();
        Seed seed = seedTimedPlan(
                now.minusSeconds(7200), now.minusSeconds(3600),
                now.minusSeconds(3000), now.minusSeconds(60)
        );
        mockMvc.perform(asDev(post("/api/v1/trips/" + seed.tripId + "/fishing-sessions"))
                        .content("{\"lateStart\":\"SKIP_EXPIRED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waypoints[0].status", is("SKIPPED")))
                .andExpect(jsonPath("$.waypoints[0].skipReason", is("LATE_START")))
                .andExpect(jsonPath("$.waypoints[1].status", is("SKIPPED")))
                .andExpect(jsonPath("$.waypoints[1].skipReason", is("LATE_START")));
    }

    @Test
    void startWithoutLateStartDoesNotSkipExpiredStops() throws Exception {
        Instant now = Instant.now();
        Seed seed = seedTimedPlan(
                now.minusSeconds(7200), now.minusSeconds(60),
                now.plusSeconds(600), now.plusSeconds(3600)
        );
        mockMvc.perform(asDev(post("/api/v1/trips/" + seed.tripId + "/fishing-sessions")).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waypoints[0].status", is("NAVIGATING")))
                .andExpect(jsonPath("$.waypoints[1].status", is("UPCOMING")));
    }

    @Test
    void gpsIngestDoesNotCreateTransitLegsOrWaterPaths() throws Exception {
        Seed seed = seedPlan(TripPlanStatus.GENERATED);
        String sessionId = readId(mockMvc.perform(asDev(post("/api/v1/trips/" + seed.tripId + "/fishing-sessions")).content("{}"))
                .andReturn());
        long legs = jdbcTemplate.queryForObject(
                "select count(*) from trip_plan_transit_legs where trip_plan_id = ?", Long.class, seed.planId);
        long paths = jdbcTemplate.queryForObject("select count(*) from lake_fishing_water_paths", Long.class);
        Instant t0 = Instant.parse("2026-09-02T17:00:00Z");
        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/locations"))
                .content(batch(
                        point("iso-a", t0, WP1_LAT, WP1_LNG, 8),
                        point("iso-b", t0.plusSeconds(10), WP1_LAT, WP1_LNG, 8)
                )));
        long legsAfter = jdbcTemplate.queryForObject(
                "select count(*) from trip_plan_transit_legs where trip_plan_id = ?", Long.class, seed.planId);
        long pathsAfter = jdbcTemplate.queryForObject("select count(*) from lake_fishing_water_paths", Long.class);
        org.assertj.core.api.Assertions.assertThat(legsAfter).isEqualTo(legs);
        org.assertj.core.api.Assertions.assertThat(pathsAfter).isEqualTo(paths);
    }

    private Seed seedPlan(TripPlanStatus status) {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(status);
        plan.setGeneratedAt(Instant.parse("2026-09-02T12:00:00Z"));
        plan.setPlanningAlgorithmVersion("session-test");
        tripPlanRepository.save(plan);

        TripWaypoint wp1 = waypoint(plan.getId(), 1, WP1_LAT, WP1_LNG);
        TripWaypoint wp2 = waypoint(plan.getId(), 2, WP2_LAT, WP2_LNG);
        tripWaypointRepository.save(wp1);
        tripWaypointRepository.save(wp2);
        return new Seed(trip.getId(), plan.getId(), wp1.getId(), wp2.getId());
    }

    private Seed seedTimedPlan(Instant aArrive, Instant aDepart, Instant bArrive, Instant bDepart) {
        Seed seed = seedPlan(TripPlanStatus.GENERATED);
        TripWaypoint wp1 = tripWaypointRepository.findById(seed.wp1).orElseThrow();
        wp1.setPlannedArrivalAt(aArrive);
        wp1.setPlannedDepartureAt(aDepart);
        tripWaypointRepository.save(wp1);
        TripWaypoint wp2 = tripWaypointRepository.findById(seed.wp2).orElseThrow();
        wp2.setPlannedArrivalAt(bArrive);
        wp2.setPlannedDepartureAt(bDepart);
        tripWaypointRepository.save(wp2);
        return seed;
    }

    private TripWaypoint waypoint(UUID planId, int sequence, double lat, double lng) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setTripPlanId(planId);
        waypoint.setSequence(sequence);
        waypoint.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(lat, lng)));
        waypoint.setReason("test waypoint " + sequence);
        return waypoint;
    }

    private static String event(String id, String occurredAt) {
        return """
                {"clientEventId":"%s","occurredAt":"%s"}
                """.formatted(id, occurredAt);
    }

    private static String point(String id, Instant recordedAt, double lat, double lng, double accuracyM) {
        return """
                {"clientPointId":"%s","recordedAt":"%s","location":{"lat":%s,"lng":%s},"accuracyM":%s}
                """.formatted(id, recordedAt, lat, lng, accuracyM);
    }

    private static String batch(String... points) {
        return "{\"points\":[" + String.join(",", points) + "]}";
    }

    private String readId(MvcResult result) throws Exception {
        JsonNode tree = objectMapper.readTree(result.getResponse().getContentAsString());
        return tree.get("id").asText();
    }

    private record Seed(UUID tripId, UUID planId, UUID wp1, UUID wp2) {
    }
}

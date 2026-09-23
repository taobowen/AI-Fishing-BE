package com.aifishing.guidance.live;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.LiveWaypointActivity;
import com.aifishing.guidance.contracts.LiveWaypointPressure;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultStatus;
import com.aifishing.guidance.spi.LiveWaypointActivityStore;
import com.aifishing.guidance.tools.GetLiveWaypointActivityTool;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.trip.domain.Trip;
import com.aifishing.user.domain.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveWaypointActivityIT extends AbstractIntegrationTest {

    private static final double WP_LAT = PlanningFixtures.HEAD_LAT;
    private static final double WP_LNG = PlanningFixtures.HEAD_LNG;

    @Autowired
    private LiveWaypointActivityStore store;
    @Autowired
    private GetLiveWaypointActivityTool tool;

    @Test
    void unknownWaypointCoordsAreUnknownAndZeroZeroIsAValidObservation() {
        Seed seed = startSelfSession();
        var missing = tool.execute(liveRequest(UUID.randomUUID(), 300));
        assertThat(missing.status()).isEqualTo(ToolResultStatus.UNKNOWN);
        assertThat(missing.data()).isNull();

        LiveWaypointActivity empty = store.findNearby(seed.wp1, 300, seed.sessionId).orElseThrow();
        assertThat(empty.activeAnglersNearby()).isZero();
        assertThat(empty.recentFishOnCount()).isZero();
        assertThat(empty.pressure()).isEqualTo(LiveWaypointPressure.LOW);
        assertThat(empty.tripWaypointId()).isEqualTo(seed.wp1);
        assertThat(empty.radiusMeters()).isEqualTo(300);
    }

    @Test
    void onlyFishingNeighborsInsideMeterRadiusCountAndSelfIsExcluded() {
        Seed seed = startSelfSession();
        ingestGps(seed.sessionId, Instant.now(), WP_LAT, WP_LNG);
        UUID fishingNear = insertNeighbor(FishingActivityState.FISHING, Instant.now(), offsetLat(20), WP_LNG);
        insertNeighbor(FishingActivityState.TRANSIT, Instant.now(), offsetLat(80), WP_LNG);
        insertNeighbor(FishingActivityState.PAUSED, Instant.now(), offsetLat(90), WP_LNG);
        insertNeighbor(FishingActivityState.UNKNOWN, Instant.now(), offsetLat(100), WP_LNG);
        insertNeighbor(FishingActivityState.FISHING, Instant.now(), offsetLat(400), WP_LNG);
        UUID interaction = UUID.randomUUID();
        insertFishOn(fishingNear, Instant.now().minusSeconds(60), interaction);
        insertFishOn(fishingNear, Instant.now().minusSeconds(30), interaction);

        LiveWaypointActivity activity = store.findNearby(seed.wp1, 300, seed.sessionId).orElseThrow();
        assertThat(activity.activeAnglersNearby()).isEqualTo(1);
        assertThat(activity.recentFishOnCount()).isEqualTo(1);
        assertThat(activity.pressure()).isEqualTo(LiveWaypointPressure.MODERATE);

        var result = tool.execute(liveRequest(seed.wp1, 300));
        assertThat(result.status()).isEqualTo(ToolResultStatus.OK);
        assertThat(result.data().path("activeAnglersNearby").asInt()).isEqualTo(1);
        assertThat(result.data().path("recentFishOnCount").asInt()).isEqualTo(1);
        assertThat(result.data().toString()).doesNotContain(seed.sessionId.toString(), fishingNear.toString());
        assertThat(result.data().toString()).doesNotContain("session_location_points");
    }

    @Test
    void degreeDeltaThatLooksSmallInDegreesIsStillOutsideMeterRadius() {
        Seed seed = startSelfSession();
        insertNeighbor(FishingActivityState.FISHING, Instant.now(), WP_LAT + 0.003, WP_LNG);

        LiveWaypointActivity miss = store.findNearby(seed.wp1, 300, seed.sessionId).orElseThrow();
        assertThat(miss.activeAnglersNearby()).isZero();

        LiveWaypointActivity hit = store.findNearby(seed.wp1, 400, seed.sessionId).orElseThrow();
        assertThat(hit.activeAnglersNearby()).isEqualTo(1);
    }

    @Test
    void rawGpsPointsAreNotScannedWhenProjectionIsMissingOrNonFishing() {
        Seed seed = startSelfSession();
        UUID ghost = insertSessionOnly(FishingActivityState.FISHING);
        jdbcTemplate.update(
                """
                INSERT INTO session_location_points (
                    id, fishing_session_id, recorded_at, received_at, location,
                    accuracy_m, client_point_id, quality, created_at
                ) VALUES (
                    ?, ?, now(), now(),
                    ST_SetSRID(ST_MakePoint(?, ?), 4326),
                    8, 'raw-near', 'ACCEPTED', now()
                )
                """,
                UUID.randomUUID(),
                ghost,
                WP_LNG,
                WP_LAT
        );

        LiveWaypointActivity none = store.findNearby(seed.wp1, 300, seed.sessionId).orElseThrow();
        assertThat(none.activeAnglersNearby()).isZero();

        store.upsertLocation(
                ghost, WP_LAT, WP_LNG, Instant.now(), 8.0, FishingActivityState.TRANSIT, DevSeedIds.LAKE_ID);
        LiveWaypointActivity stillNone = store.findNearby(seed.wp1, 300, seed.sessionId).orElseThrow();
        assertThat(stillNone.activeAnglersNearby()).isZero();
    }

    @Test
    void gpsPersistUpsertsProjectionAndPauseRewritesActivityWithoutANewPoint() throws Exception {
        Seed seed = startSelfSession();
        Instant t0 = Instant.parse("2026-09-16T17:00:00Z");
        ingestGps(seed.sessionId, t0, WP_LAT, WP_LNG);

        Projection before = readProjection(seed.sessionId);
        assertThat(before).isNotNull();
        assertThat(before.activityState()).isIn("TRANSIT", "UNKNOWN", "FISHING");
        Instant recordedAt = before.recordedAt();

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/waypoints/" + seed.wp1 + "/arrive"))
                        .content(event("arr-live", t0.plusSeconds(30).toString())))
                .andExpect(status().isOk());
        assertThat(readProjection(seed.sessionId).activityState()).isEqualTo("FISHING");
        assertThat(readProjection(seed.sessionId).recordedAt()).isEqualTo(recordedAt);

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/pause"))
                        .content(event("pause-live", t0.plusSeconds(40).toString())))
                .andExpect(status().isOk());
        Projection paused = readProjection(seed.sessionId);
        assertThat(paused.activityState()).isEqualTo("PAUSED");
        assertThat(paused.recordedAt()).isEqualTo(recordedAt);

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + seed.sessionId + "/resume"))
                        .content(event("resume-live", t0.plusSeconds(50).toString())))
                .andExpect(status().isOk());
        assertThat(readProjection(seed.sessionId).activityState()).isEqualTo("FISHING");
        assertThat(readProjection(seed.sessionId).recordedAt()).isEqualTo(recordedAt);
    }

    private Seed startSelfSession() {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(Instant.parse("2026-09-02T12:00:00Z"));
        plan.setPlanningAlgorithmVersion("live-waypoint-test");
        tripPlanRepository.save(plan);
        TripWaypoint wp1 = new TripWaypoint();
        wp1.setTripPlanId(plan.getId());
        wp1.setSequence(1);
        wp1.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(WP_LAT, WP_LNG)));
        wp1.setReason("live wp");
        tripWaypointRepository.save(wp1);
        try {
            MvcResult started = mockMvc.perform(asDev(post("/api/v1/trips/" + trip.getId() + "/fishing-sessions"))
                            .content("{}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            JsonNode tree = objectMapper.readTree(started.getResponse().getContentAsString());
            return new Seed(UUID.fromString(tree.get("id").asText()), wp1.getId());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void ingestGps(UUID sessionId, Instant recordedAt, double lat, double lng) {
        try {
            mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/locations"))
                            .content("""
                                    {"points":[{"clientPointId":"live-%s","recordedAt":"%s","location":{"lat":%s,"lng":%s},"accuracyM":8}]}
                                    """.formatted(UUID.randomUUID(), recordedAt, lat, lng)))
                    .andExpect(status().isOk());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private UUID insertNeighbor(FishingActivityState activity, Instant recordedAt, double lat, double lng) {
        UUID sessionId = insertSessionOnly(activity);
        store.upsertLocation(sessionId, lat, lng, recordedAt, 8.0, activity, DevSeedIds.LAKE_ID);
        return sessionId;
    }

    private UUID insertSessionOnly(FishingActivityState activity) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(user.getId() + "@live.test");
        user.setDisplayName("Live neighbor");
        userRepository.save(user);
        Trip trip = tripRepository.save(PlanningFixtures.trip(user.getId(), DevSeedIds.LAKE_ID, FishingMode.SHORE));
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO fishing_sessions (
                    id, trip_id, user_id, started_at, status, total_paused_seconds,
                    activity_state, activity_state_source, created_at
                ) VALUES (?, ?, ?, now(), 'ACTIVE', 0, ?, 'PROGRESS', now())
                """,
                sessionId,
                trip.getId(),
                user.getId(),
                activity.name()
        );
        return sessionId;
    }

    private void insertFishOn(UUID sessionId, Instant occurredAt, UUID interactionId) {
        jdbcTemplate.update(
                """
                INSERT INTO session_events (
                    id, fishing_session_id, schema_version, type, occurred_at, payload,
                    source, idempotency_key, created_at, fish_interaction_id
                ) VALUES (?, ?, ?, 'FISH_ON', ?, '{}'::jsonb, 'CLIENT', ?, now(), ?)
                """,
                UUID.randomUUID(),
                sessionId,
                GuidanceSchemaVersion.VALUE,
                java.sql.Timestamp.from(occurredAt),
                "fish-on-" + UUID.randomUUID(),
                interactionId
        );
    }

    private Projection readProjection(UUID sessionId) {
        return jdbcTemplate.query(
                """
                SELECT activity_state, recorded_at
                FROM session_live_position
                WHERE fishing_session_id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return new Projection(
                            rs.getString("activity_state"),
                            rs.getTimestamp("recorded_at").toInstant()
                    );
                },
                sessionId
        );
    }

    private static ToolRequestEnvelope liveRequest(UUID tripWaypointId, int radius) {
        ObjectNode args = com.aifishing.guidance.contracts.GuidanceContracts.mapper().createObjectNode();
        args.put("tripWaypointId", tripWaypointId.toString());
        args.put("radiusMeters", radius);
        return new ToolRequestEnvelope(
                GuidanceSchemaVersion.VALUE,
                ToolName.GET_LIVE_WAYPOINT_ACTIVITY,
                args,
                Instant.parse("2026-09-16T15:00:00Z")
        );
    }

    private static String event(String id, String occurredAt) {
        return "{\"clientEventId\":\"%s\",\"occurredAt\":\"%s\"}".formatted(id, occurredAt);
    }

    private static double offsetLat(double meters) {
        return WP_LAT + meters / GeoMetrics.metersPerDegreeLat();
    }

    private record Seed(UUID sessionId, UUID wp1) {
    }

    private record Projection(String activityState, Instant recordedAt) {
    }
}

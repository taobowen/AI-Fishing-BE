package com.aifishing.guidance.state;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.guidance.contracts.CompassDirection;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.guidance.contracts.WeatherSnapshot;
import com.aifishing.guidance.runtime.EnvironmentSnapshot;
import com.aifishing.guidance.spi.FishingSessionStateBuilder;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FishingSessionStateBuilderIT extends AbstractIntegrationTest {

    @Autowired
    private FishingSessionStateBuilder stateBuilder;

    @Test
    void buildUsesDistinctWaypointIdsAndSuppliedEnvironment() throws Exception {
        Seed seed = startSession();
        Instant now = Instant.parse("2026-09-16T14:00:00Z");
        EnvironmentSnapshot environment = new EnvironmentSnapshot(
                now,
                new WeatherSnapshot(
                        GuidanceSchemaVersion.VALUE,
                        now.minusSeconds(180),
                        WeatherCondition.CLOUDY,
                        16.0,
                        CompassDirection.W,
                        18.0,
                        1012.0
                ),
                false,
                3,
                PlanningFixtures.HEAD_LAT,
                PlanningFixtures.HEAD_LNG
        );

        FishingSessionState state = stateBuilder.build(seed.sessionId(), environment);

        assertThat(state.session().sessionId()).isEqualTo(seed.sessionId());
        assertThat(state.fishing().currentTripWaypointId()).isEqualTo(seed.tripWaypointId());
        assertThat(state.fishing().currentSessionWaypointProgressId()).isNotNull();
        assertThat(state.fishing().currentSessionWaypointProgressId()).isNotEqualTo(seed.tripWaypointId());
        assertThat(state.position().latitudeWgs84()).isEqualTo(PlanningFixtures.HEAD_LAT);
        assertThat(state.position().longitudeWgs84()).isEqualTo(PlanningFixtures.HEAD_LNG);
        assertThat(state.environment().weather()).isEqualTo(WeatherCondition.CLOUDY);
        assertThat(state.environment().windSpeedKph()).isEqualTo(16.0);
        assertThat(state.environment().weatherAgeMinutes()).isEqualTo(3);
        assertThat(state.recent().summaries()).isEmpty();
    }

    private Seed startSession() throws Exception {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(Instant.parse("2026-09-02T12:00:00Z"));
        plan.setPlanningAlgorithmVersion("guidance-state-test");
        tripPlanRepository.save(plan);
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setTripPlanId(plan.getId());
        waypoint.setSequence(1);
        waypoint.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(
                PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG)));
        waypoint.setReason("guidance state");
        tripWaypointRepository.save(waypoint);
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/trips/" + trip.getId() + "/fishing-sessions")).content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode tree = objectMapper.readTree(created.getResponse().getContentAsString());
        return new Seed(UUID.fromString(tree.get("id").asText()), waypoint.getId());
    }

    private record Seed(UUID sessionId, UUID tripWaypointId) {
    }
}

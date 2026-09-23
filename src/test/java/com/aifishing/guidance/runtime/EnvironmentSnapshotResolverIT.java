package com.aifishing.guidance.runtime;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.guidance.persistence.WeatherSnapshotEntity;
import com.aifishing.guidance.persistence.WeatherSnapshotRepository;
import com.aifishing.guidance.spi.EnvironmentSnapshotResolver;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import com.aifishing.strategy.weather.WeatherService;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnvironmentSnapshotResolverIT extends AbstractIntegrationTest {

    @MockitoBean
    private WeatherService weatherService;

    @Autowired
    private EnvironmentSnapshotResolver resolver;

    @Autowired
    private WeatherSnapshotRepository weatherSnapshotRepository;

    @Test
    void stalePersistedSnapshotRefreshesAndAppends() throws Exception {
        UUID sessionId = startSession();
        persistSnapshot(sessionId, Instant.parse("2026-09-16T10:00:00Z"), WeatherCondition.CLEAR);
        Instant retrievedAt = Instant.parse("2026-09-16T14:05:00Z");
        when(weatherService.forTrip(anyDouble(), anyDouble(), any(), any(), any(), any()))
                .thenReturn(new WeatherContext(
                        WeatherAvailability.FORECAST_AVAILABLE,
                        retrievedAt,
                        "open-meteo",
                        "America/Toronto",
                        LocalDate.of(2026, 9, 16),
                        false,
                        null,
                        14.0,
                        10.0,
                        240.0,
                        0.1,
                        70.0,
                        1013.0,
                        LocalTime.of(6, 42),
                        LocalTime.of(19, 31),
                        List.of(),
                        "test refresh"
                ));

        EnvironmentSnapshot snapshot = resolver.resolve(sessionId);

        assertThat(snapshot.refreshed()).isTrue();
        assertThat(snapshot.weather()).isNotNull();
        assertThat(weatherSnapshotRepository.findFirstByFishingSessionIdOrderByObservedAtDesc(sessionId))
                .isPresent()
                .get()
                .extracting(WeatherSnapshotEntity::getObservedAt)
                .isEqualTo(retrievedAt);
        verify(weatherService).forTrip(anyDouble(), anyDouble(), any(), any(), any(), any());
        assertThat(weatherSnapshotRepository.count()).isGreaterThanOrEqualTo(2);
    }

    private UUID startSession() throws Exception {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(Instant.parse("2026-09-02T12:00:00Z"));
        plan.setPlanningAlgorithmVersion("guidance-env-test");
        tripPlanRepository.save(plan);
        tripWaypointRepository.save(waypoint(plan.getId(), 1, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG));
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/trips/" + trip.getId() + "/fishing-sessions")).content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode tree = objectMapper.readTree(created.getResponse().getContentAsString());
        return UUID.fromString(tree.get("id").asText());
    }

    private TripWaypoint waypoint(UUID planId, int sequence, double lat, double lng) {
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setTripPlanId(planId);
        waypoint.setSequence(sequence);
        waypoint.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(lat, lng)));
        waypoint.setReason("guidance env " + sequence);
        return waypoint;
    }

    private void persistSnapshot(UUID sessionId, Instant observedAt, WeatherCondition condition) {
        WeatherSnapshotEntity entity = new WeatherSnapshotEntity();
        entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        entity.setFishingSessionId(sessionId);
        entity.setObservedAt(observedAt);
        entity.setEnvelope(Map.of(
                "schemaVersion", GuidanceSchemaVersion.VALUE,
                "observedAt", observedAt.toString(),
                "weather", condition.name()
        ));
        weatherSnapshotRepository.save(entity);
    }
}

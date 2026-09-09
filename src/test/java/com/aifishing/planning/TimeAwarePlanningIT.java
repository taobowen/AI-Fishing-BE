package com.aifishing.planning;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.strategy.weather.WeatherContext;
import com.aifishing.strategy.weather.WeatherProvider;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimeAwarePlanningIT extends AbstractIntegrationTest {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    @MockitoBean
    WeatherProvider weatherProvider;

    @Autowired
    LakeFeatureRepository featureRepository;
    @Autowired
    StrategyRunRepository strategyRunRepository;
    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;

    @Test
    void generatePlanDoesNotFetchWeatherPerCandidate() throws Exception {
        UUID tripId = seedShoreTripWithTwoHumps();
        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(
                tripId, PlanningFixtures.profile(), objectMapper));

        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.plannedLaunchDepartureAt", not(nullValue())))
                .andExpect(jsonPath("$.plan.plannedReturnAt", not(nullValue())))
                .andExpect(jsonPath("$.plan.scheduleAlgorithmVersion", is("1.4.0")))
                .andExpect(jsonPath("$.plan.waypoints[0].plannedArrivalAt", not(nullValue())))
                .andExpect(jsonPath("$.plan.waypoints[0].plannedDwellMinutes", not(nullValue())));

        verify(weatherProvider, never()).forecast(anyDouble(), anyDouble(), any(), any(), any(), any());
    }

    @Test
    void getDoesNotRecomputeAfterSnapshotChangeAndRegenerateMayDiffer() throws Exception {
        UUID tripId = seedShoreTripWithTwoHumps();
        var strategy = PlanningFixtures.completedStrategy(tripId, PlanningFixtures.profile(), objectMapper);
        WeatherContext mild = RoutePlannerHarness.hourly(List.of(
                new WeatherContext.HourlyWeather(java.time.LocalTime.of(6, 0), 18.0, 8.0, 270.0, 0.0, 10.0, 1013.0, 750.0, 750.0),
                new WeatherContext.HourlyWeather(java.time.LocalTime.of(12, 0), 20.0, 8.0, 270.0, 0.0, 10.0, 1013.0, 800.0, 800.0),
                new WeatherContext.HourlyWeather(java.time.LocalTime.of(15, 0), 19.0, 8.0, 270.0, 0.0, 10.0, 1013.0, 600.0, 600.0)
        ), 8, 10);
        strategy.setWeatherSnapshot(objectMapper.convertValue(mild, MAP));
        strategy = strategyRunRepository.save(strategy);

        String generated = mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var planA = objectMapper.readTree(generated).get("plan");
        String arrivalA = planA.get("waypoints").get(0).get("plannedArrivalAt").asText();
        String utilityA = planA.get("waypoints").get(0).path("scoreBreakdown").path("finalTimeAdjustedUtility").asText();

        WeatherContext cold = RoutePlannerHarness.hourly(List.of(
                new WeatherContext.HourlyWeather(java.time.LocalTime.of(6, 0), 1.0, 8.0, 270.0, 0.0, 10.0, 1013.0, 750.0, 750.0),
                new WeatherContext.HourlyWeather(java.time.LocalTime.of(12, 0), 2.0, 8.0, 270.0, 0.0, 10.0, 1013.0, 800.0, 800.0),
                new WeatherContext.HourlyWeather(java.time.LocalTime.of(15, 0), 1.0, 8.0, 270.0, 0.0, 10.0, 1013.0, 600.0, 600.0)
        ), 8, 10);
        strategy.setWeatherSnapshot(new HashMap<>(objectMapper.convertValue(cold, MAP)));
        strategyRunRepository.save(strategy);

        String frozen = mockMvc.perform(asDev(get("/api/v1/trips/" + tripId + "/plan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.waypoints[0].plannedArrivalAt", is(arrivalA)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(frozen).get("waypoints").get(0)
                        .path("scoreBreakdown").path("finalTimeAdjustedUtility").asDouble())
                .isEqualTo(Double.parseDouble(utilityA));

        String regenerated = mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.plan.version", is(2)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var planB = objectMapper.readTree(regenerated).get("plan");
        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId + "/plans")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status", is("GENERATED")))
                .andExpect(jsonPath("$[0].version", is(2)))
                .andExpect(jsonPath("$[1].status", is("SUPERSEDED")))
                .andExpect(jsonPath("$[1].version", is(1)));
        org.assertj.core.api.Assertions.assertThat(planB.get("waypoints").get(0).path("scoreBreakdown")
                        .path("finalTimeAdjustedUtility").asText())
                .isNotEqualTo(utilityA);
    }

    private UUID seedShoreTripWithTwoHumps() {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 50),
                2.5,
                3.5,
                0.9,
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
        Trip trip = PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE);
        return tripRepository.save(trip).getId();
    }
}

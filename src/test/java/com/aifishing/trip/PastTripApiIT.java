package com.aifishing.trip;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PastTripApiIT extends AbstractIntegrationTest {

    @Test
    void monthQueryIncludesOvernightTripAndExpiredUnfishedIsNotCompleted() throws Exception {
        ZoneId zone = ZoneId.of("America/Toronto");
        LocalDate startDate = LocalDate.now(zone).minusMonths(2);
        LocalDate endDate = startDate.plusDays(1);
        YearMonth month = YearMonth.from(endDate);

        mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "WALLEYE",
                          "plannedDate": "%s",
                          "plannedEndDate": "%s",
                          "fishingStartTime": "20:00:00",
                          "fishingEndTime": "05:00:00",
                          "fishingMode": "SHORE"
                        }
                        """.formatted(DevSeedIds.LAKE_ID, startDate, endDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedEndDate", is(endDate.toString())));

        mockMvc.perform(asDev(get("/api/v1/trips/past").param("month", month.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lakeName", is("Head Lake")))
                .andExpect(jsonPath("$[0].lakeCardImageUrl", is("http://localhost:8080/lakes/head.jpg")))
                .andExpect(jsonPath("$[0].primaryTargetSpecies", is("WALLEYE")))
                .andExpect(jsonPath("$[0].resultsAvailable", is(false)))
                .andExpect(jsonPath("$[0].completedSessionId").doesNotExist())
                .andExpect(jsonPath("$[0].sessionStatus").doesNotExist())
                .andExpect(jsonPath("$[0].plannedStartAt").exists())
                .andExpect(jsonPath("$[0].plannedEndAt").exists())
                .andExpect(jsonPath("$[0].visitedStops", is(0)));
    }

    @Test
    void completedPastSessionExposesViewResults() throws Exception {
        ZoneId zone = ZoneId.of("America/Toronto");
        LocalDate day = LocalDate.now(zone).minusDays(3);
        Trip trip = PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE);
        trip.setPlannedDate(day);
        trip.setPlannedEndDate(day);
        trip.setFishingStartTime(LocalTime.of(6, 0));
        trip.setFishingEndTime(LocalTime.of(15, 0));
        trip = tripRepository.save(trip);

        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(Instant.parse("2026-09-02T12:00:00Z"));
        plan.setPlanningAlgorithmVersion("past-test");
        tripPlanRepository.save(plan);

        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setTripPlanId(plan.getId());
        waypoint.setSequence(1);
        waypoint.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(44.75, -78.92)));
        waypoint.setReason("past");
        tripWaypointRepository.save(waypoint);

        MvcResult started = mockMvc.perform(asDev(post("/api/v1/trips/" + trip.getId() + "/fishing-sessions")).content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(started.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/end"))
                        .content("{\"clientEventId\":\"end-past\",\"occurredAt\":\"2026-09-02T16:30:00Z\"}"))
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        mockMvc.perform(asDev(get("/api/v1/fishing-sessions/" + sessionId + "/results")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId", is(sessionId)))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.performance.sessionId", is(sessionId)))
                .andExpect(jsonPath("$.summary").exists());

        YearMonth month = YearMonth.from(day);
        mockMvc.perform(asDev(get("/api/v1/trips/past").param("month", month.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tripId", is(trip.getId().toString())))
                .andExpect(jsonPath("$[0].tripPlanId", is(plan.getId().toString())))
                .andExpect(jsonPath("$[0].completedSessionId", is(sessionId)))
                .andExpect(jsonPath("$[0].resultsAvailable", is(true)))
                .andExpect(jsonPath("$[0].sessionStatus", is("COMPLETED")));
    }

    @Test
    void resultsForUnfinishedSessionAreNotFound() throws Exception {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(Instant.parse("2026-09-02T12:00:00Z"));
        plan.setPlanningAlgorithmVersion("past-test");
        tripPlanRepository.save(plan);
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setTripPlanId(plan.getId());
        waypoint.setSequence(1);
        waypoint.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(44.75, -78.92)));
        tripWaypointRepository.save(waypoint);

        String sessionId = objectMapper.readTree(mockMvc.perform(
                        asDev(post("/api/v1/trips/" + trip.getId() + "/fishing-sessions")).content("{}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asText();

        mockMvc.perform(asDev(get("/api/v1/fishing-sessions/" + sessionId + "/results")))
                .andExpect(status().isNotFound());
    }
}

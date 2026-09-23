package com.aifishing.trip;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.seed.DevSeedIds;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripApiIT extends AbstractIntegrationTest {

    @Test
    void createBoatTripAndRejectInvalidTimesAndCrossUserAccess() throws Exception {
        UUID boatId = saveBoat(DevSeedIds.USER_ID);

        MvcResult created = mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "secondaryTargetSpecies": ["WALLEYE"],
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "boatId": "%s",
                          "fishingMode": "BOAT",
                          "status": "DRAFT"
                        }
                        """.formatted(DevSeedIds.LAKE_ID, boatId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeZoneId", is("America/Toronto")))
                .andExpect(jsonPath("$.lakeCardImageUrl", is("http://localhost:8080/lakes/head.jpg")))
                .andExpect(jsonPath("$.primaryTargetSpecies", is("SMALLMOUTH_BASS")))
                .andExpect(jsonPath("$.secondaryTargetSpecies", contains("WALLEYE")))
                .andExpect(jsonPath("$.plans").doesNotExist())
                .andExpect(jsonPath("$.waypoints").doesNotExist())
                .andExpect(jsonPath("$.sessions").doesNotExist())
                .andReturn();
        String tripId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(asDev(get("/api/v1/trips/" + tripId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans").doesNotExist())
                .andExpect(jsonPath("$.waypoints").doesNotExist())
                .andExpect(jsonPath("$.sessions").doesNotExist());

        mockMvc.perform(asOther(get("/api/v1/trips/" + tripId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "15:00:00",
                          "fishingEndTime": "06:00:00",
                          "boatId": "%s",
                          "fishingMode": "BOAT"
                        }
                        """.formatted(DevSeedIds.LAKE_ID, boatId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedDate", is("2026-09-12")))
                .andExpect(jsonPath("$.plannedEndDate", is("2026-09-13")))
                .andExpect(jsonPath("$.fishingStartTime", is("15:00:00")))
                .andExpect(jsonPath("$.fishingEndTime", is("06:00:00")))
                .andExpect(jsonPath("$.plannedStartAt").exists())
                .andExpect(jsonPath("$.plannedEndAt").exists());

        mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "16:30:00",
                          "fishingEndTime": "20:00:00",
                          "boatId": "%s",
                          "fishingMode": "BOAT"
                        }
                        """.formatted(DevSeedIds.LAKE_ID, boatId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fishingStartTime", is("16:30:00")))
                .andExpect(jsonPath("$.fishingEndTime", is("20:00:00")));
    }

    @Test
    void boatModeRequiresOwnedBoatAndShoreModeRejectsBoatId() throws Exception {
        UUID otherBoatId = saveBoat(DevSeedIds.OTHER_USER_ID);

        mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "boatId": "%s",
                          "fishingMode": "BOAT"
                        }
                        """.formatted(DevSeedIds.LAKE_ID, otherBoatId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "boatId": "%s",
                          "fishingMode": "SHORE"
                        }
                        """.formatted(DevSeedIds.LAKE_ID, otherBoatId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "LARGEMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "07:00:00",
                          "fishingEndTime": "12:00:00",
                          "fishingMode": "SHORE"
                        }
                        """.formatted(DevSeedIds.LAKE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fishingMode", is("SHORE")))
                .andExpect(jsonPath("$.boatId").doesNotExist());
    }

    @Test
    void defaultListOmitsCancelledTrips() throws Exception {
        MvcResult kept = mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "WALLEYE",
                          "plannedDate": "2026-09-13",
                          "fishingStartTime": "07:00:00",
                          "fishingEndTime": "12:00:00",
                          "fishingMode": "SHORE"
                        }
                        """.formatted(DevSeedIds.LAKE_ID)))
                .andExpect(status().isCreated())
                .andReturn();
        String keptId = objectMapper.readTree(kept.getResponse().getContentAsString()).get("id").asText();

        MvcResult cancelled = mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "CRAPPIE",
                          "plannedDate": "2026-09-14",
                          "fishingStartTime": "08:00:00",
                          "fishingEndTime": "11:00:00",
                          "fishingMode": "SHORE"
                        }
                        """.formatted(DevSeedIds.LAKE_ID)))
                .andExpect(status().isCreated())
                .andReturn();
        String cancelledId = objectMapper.readTree(cancelled.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(asDev(patch("/api/v1/trips/" + cancelledId)).content("""
                        { "status": "CANCELLED" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        mockMvc.perform(asDev(get("/api/v1/trips")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(keptId)))
                .andExpect(jsonPath("$[*].id", not(hasItem(cancelledId))))
                .andExpect(jsonPath("$[*].lakeCardImageUrl", hasItem("http://localhost:8080/lakes/head.jpg")));

        mockMvc.perform(asDev(get("/api/v1/trips").param("status", "CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(cancelledId)))
                .andExpect(jsonPath("$[*].id", not(hasItem(keptId))));

        mockMvc.perform(asDev(get("/api/v1/trips/" + cancelledId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void acceptsSingleDigitHourClockTimes() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "LARGEMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "8:00",
                          "fishingEndTime": "15:00",
                          "fishingMode": "SHORE"
                        }
                        """.formatted(DevSeedIds.LAKE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fishingStartTime", is("08:00:00")))
                .andExpect(jsonPath("$.fishingEndTime", is("15:00:00")));
    }

    @Test
    void explicitSameDayAndOvernightDatesAndTwentyFourHourCap() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "WALLEYE",
                          "plannedDate": "2026-09-12",
                          "plannedEndDate": "2026-09-12",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "fishingMode": "SHORE"
                        }
                        """.formatted(DevSeedIds.LAKE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedDate", is("2026-09-12")))
                .andExpect(jsonPath("$.plannedEndDate", is("2026-09-12")));

        mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "WALLEYE",
                          "plannedDate": "2026-09-12",
                          "plannedEndDate": "2026-09-13",
                          "fishingStartTime": "20:00:00",
                          "fishingEndTime": "05:00:00",
                          "fishingMode": "SHORE"
                        }
                        """.formatted(DevSeedIds.LAKE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedEndDate", is("2026-09-13")));

        mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "WALLEYE",
                          "plannedDate": "2026-09-12",
                          "plannedEndDate": "2026-09-13",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "06:00:00",
                          "fishingMode": "SHORE"
                        }
                        """.formatted(DevSeedIds.LAKE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedEndDate", is("2026-09-13")));

        mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "WALLEYE",
                          "plannedDate": "2026-09-12",
                          "plannedEndDate": "2026-09-13",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "06:01:00",
                          "fishingMode": "SHORE"
                        }
                        """.formatted(DevSeedIds.LAKE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    private UUID saveBoat(UUID userId) {
        Boat boat = new Boat();
        boat.setUserId(userId);
        boat.setName("Test boat");
        boat.setType(BoatType.FISHING_BOAT);
        boat.setPropulsionTypes(List.of(PropulsionType.GAS_OUTBOARD));
        boat.setPrimaryTransitPropulsionType(PropulsionType.GAS_OUTBOARD);
        boat.setActive(true);
        return boatRepository.save(boat).getId();
    }
}

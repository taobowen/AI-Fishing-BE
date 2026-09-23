package com.aifishing.planning;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.seed.DevSeedIds;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WeatherPreviewIT extends AbstractIntegrationTest {

    @Test
    void unavailablePreviewDoesNotBlockTripCreate() throws Exception {
        LocalDate past = LocalDate.now(ZoneId.of("America/Toronto")).minusDays(2);
        mockMvc.perform(asDev(post("/api/v1/planning/weather-preview")).content("""
                        {
                          "lakeId": "%s",
                          "plannedDate": "%s",
                          "plannedEndDate": "%s",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00"
                        }
                        """.formatted(DevSeedIds.LAKE_ID, past, past)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available", is(false)));

        mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "WALLEYE",
                          "plannedDate": "%s",
                          "plannedEndDate": "%s",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "fishingMode": "SHORE"
                        }
                        """.formatted(DevSeedIds.LAKE_ID, past, past)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedDate", is(past.toString())));
    }
}

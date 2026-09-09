package com.aifishing.fishingprofile;

import com.aifishing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FishingProfileApiIT extends AbstractIntegrationTest {

    @Test
    void upsertAndGetFishingProfile() throws Exception {
        mockMvc.perform(asDev(get("/api/v1/me/fishing-profile")))
                .andExpect(status().isNotFound());

        mockMvc.perform(asDev(put("/api/v1/me/fishing-profile")).content("""
                        {
                          "experienceLevel": "INTERMEDIATE",
                          "preferredSpecies": ["SMALLMOUTH_BASS", "WALLEYE"],
                          "preferredFishingStyles": ["ned_rig", "jerkbait"],
                          "homeCity": "Toronto",
                          "homeRegion": "Ontario",
                          "homeCountry": "Canada",
                          "homeLocation": {"lat": 43.65, "lng": -79.38}
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experienceLevel", is("INTERMEDIATE")))
                .andExpect(jsonPath("$.preferredSpecies", contains("SMALLMOUTH_BASS", "WALLEYE")))
                .andExpect(jsonPath("$.homeLocation.lat", is(43.65)))
                .andExpect(jsonPath("$.homeLocation.lng", is(-79.38)));

        mockMvc.perform(asDev(get("/api/v1/me/fishing-profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeCity", is("Toronto")));
    }
}

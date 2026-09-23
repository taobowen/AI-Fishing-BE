package com.aifishing.boat;

import com.aifishing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BoatApiIT extends AbstractIntegrationTest {

    @Test
    void createBoatWithMultiplePropulsionTypesAndHideFromOtherUser() throws Exception {
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/me/boats")).content("""
                        {
                          "name": "Inflatable skiff",
                          "type": "INFLATABLE",
                          "propulsionTypes": ["GAS_OUTBOARD", "ELECTRIC_TROLLING"],
                          "primaryTransitPropulsionType": "GAS_OUTBOARD",
                          "motors": [
                            {"propulsionType": "GAS_OUTBOARD", "horsepower": 9.9},
                            {"propulsionType": "ELECTRIC_TROLLING", "thrustLb": 55}
                          ],
                          "maxSpeedKmh": 28.5
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("INFLATABLE")))
                .andExpect(jsonPath("$.propulsionTypes", contains("GAS_OUTBOARD", "ELECTRIC_TROLLING")))
                .andExpect(jsonPath("$.primaryTransitPropulsionType", is("GAS_OUTBOARD")))
                .andExpect(jsonPath("$.measuredCruiseSpeedKmh").doesNotExist())
                .andExpect(jsonPath("$.resolvedCapability.cruiseSpeedKmh.source", not("USER_OVERRIDE")))
                .andExpect(jsonPath("$.resolvedCapability.cruiseSpeedKmh.value", not(nullValue())))
                .andExpect(jsonPath("$.resolvedCapability.fingerprint").doesNotExist())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(asDev(get("/api/v1/me/boats/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedCapability.cruiseSpeedKmh.source", not("USER_OVERRIDE")))
                .andExpect(jsonPath("$.resolvedCapability.effective").doesNotExist())
                .andExpect(jsonPath("$.resolvedCapability.windDerateFraction").doesNotExist());

        mockMvc.perform(asOther(get("/api/v1/me/boats/" + id)))
                .andExpect(status().isNotFound());
    }

    @Test
    void paddleRejectsHorsepowerAndCreateSucceedsWhenResolveFallsBack() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/me/boats")).content("""
                        {
                          "name": "Canoe",
                          "type": "CANOE",
                          "propulsionTypes": ["PADDLE"],
                          "motors": [{"propulsionType": "PADDLE", "horsepower": 5}]
                        }
                        """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(asDev(post("/api/v1/me/boats")).content("""
                        {
                          "name": "Canoe",
                          "type": "CANOE",
                          "propulsionTypes": ["PADDLE"]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primaryTransitPropulsionType", is("PADDLE")))
                .andExpect(jsonPath("$.resolvedCapability.cruiseSpeedKmh.source", is("CONSERVATIVE_FALLBACK")));
    }

    @Test
    void primaryTransitMustBeSelectedPropulsion() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/me/boats")).content("""
                        {
                          "name": "Bad primary",
                          "type": "INFLATABLE",
                          "propulsionTypes": ["ELECTRIC_TROLLING"],
                          "primaryTransitPropulsionType": "GAS_OUTBOARD"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void firstUserBoatBecomesDefaultAndLaterBoatsCanTakeOver() throws Exception {
        MvcResult first = mockMvc.perform(asDev(post("/api/v1/me/boats")).content("""
                        {
                          "name": "Default skiff",
                          "type": "INFLATABLE",
                          "propulsionTypes": ["GAS_OUTBOARD"]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault", is(true)))
                .andReturn();
        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        MvcResult second = mockMvc.perform(asDev(post("/api/v1/me/boats")).content("""
                        {
                          "name": "Backup kayak",
                          "type": "KAYAK",
                          "propulsionTypes": ["PADDLE"],
                          "isDefault": true
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault", is(true)))
                .andReturn();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(asDev(get("/api/v1/me/boats/" + firstId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault", is(false)));
        mockMvc.perform(asDev(get("/api/v1/me/boats/" + secondId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault", is(true)));
    }

    @Test
    void deleteSoftHidesBoatFromDefaultList() throws Exception {
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/me/boats")).content("""
                        {
                          "name": "To remove",
                          "type": "KAYAK",
                          "propulsionTypes": ["PADDLE"]
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(asDev(delete("/api/v1/me/boats/" + id)))
                .andExpect(status().isNoContent());

        mockMvc.perform(asDev(get("/api/v1/me/boats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(id))));

        mockMvc.perform(asDev(get("/api/v1/me/boats/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    void acceptsLegacyOutboardAlias() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/me/boats")).content("""
                        {
                          "name": "Legacy alias",
                          "type": "FISHING_BOAT",
                          "propulsionTypes": ["OUTBOARD", "TROLLING_MOTOR"],
                          "primaryTransitPropulsionType": "OUTBOARD"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.propulsionTypes", contains("GAS_OUTBOARD", "ELECTRIC_TROLLING")))
                .andExpect(jsonPath("$.primaryTransitPropulsionType", is("GAS_OUTBOARD")));
    }

    @Test
    void freeTextCreateDefaultsMissingEquipment() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/me/boats")).content("""
                        {
                          "name": "Jon",
                          "configurationDescription": "12ft jon boat, not sure about the motor"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Jon")))
                .andExpect(jsonPath("$.type", is("OTHER")))
                .andExpect(jsonPath("$.propulsionTypes", contains("NONE")))
                .andExpect(jsonPath("$.configurationDescription", is("12ft jon boat, not sure about the motor")))
                .andExpect(jsonPath("$.freeTextHash", not(nullValue())))
                .andExpect(jsonPath("$.resolvedCapability.missingHints", not(nullValue())));
    }

    @Test
    void descriptionPatchDoesNotOverwriteUserMetrics() throws Exception {
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/me/boats")).content("""
                        {
                          "name": "Jon",
                          "configurationDescription": "12ft jon",
                          "measuredCruiseSpeedKmh": 12,
                          "comfortableRoundTripRangeKm": 18,
                          "windWaveOverride": "LOW"
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(asDev(patch("/api/v1/me/boats/" + id)).content("""
                        {
                          "configurationDescription": "12ft jon plus 24V 100Ah LiFePO4"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measuredCruiseSpeedKmh", is(12.0)))
                .andExpect(jsonPath("$.comfortableRoundTripRangeKm", is(18.0)))
                .andExpect(jsonPath("$.windWaveOverride", is("LOW")));
    }
}

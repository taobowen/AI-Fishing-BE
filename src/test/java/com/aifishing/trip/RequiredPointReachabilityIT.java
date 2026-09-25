package com.aifishing.trip;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.seed.DevSeedIds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequiredPointReachabilityIT extends AbstractIntegrationTest {

    @Test
    void shoreAndUnknownInputsSkipGrayOut() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/trips/required-point-reachability")).content("""
                        {
                          "fishingMode": "SHORE",
                          "routeStart": { "lat": 44.75, "lng": -78.92 }
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applyRangeCap", is(false)))
                .andExpect(jsonPath("$.oneWayCapKm", nullValue()))
                .andExpect(jsonPath("$.routeStart.lat", is(44.75)));

        mockMvc.perform(asDev(post("/api/v1/trips/required-point-reachability")).content("""
                        {
                          "fishingMode": "BOAT",
                          "routeStart": { "lat": 44.75, "lng": -78.92 }
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applyRangeCap", is(false)))
                .andExpect(jsonPath("$.oneWayCapKm", nullValue()));
    }

    @Test
    void boatWithLaunchReturnsCap() throws Exception {
        UUID boatId = saveBoat(DevSeedIds.USER_ID, BoatType.FISHING_BOAT);
        mockMvc.perform(asDev(post("/api/v1/trips/required-point-reachability")).content("""
                        {
                          "fishingMode": "BOAT",
                          "boatId": "%s",
                          "routeStart": { "lat": 44.75, "lng": -78.92 },
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00"
                        }
                        """.formatted(boatId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applyRangeCap", is(true)))
                .andExpect(jsonPath("$.oneWayCapKm", is(20.0)))
                .andExpect(jsonPath("$.routeStart.lat", is(44.75)))
                .andExpect(jsonPath("$.disclaimer", is(
                        "Early estimate only. Final Generate may still reject a point for weather, safety, time, or routing."
                )));
    }

    private UUID saveBoat(UUID userId, BoatType type) {
        Boat boat = new Boat();
        boat.setUserId(userId);
        boat.setName("Reach boat");
        boat.setType(type);
        boat.setPropulsionTypes(List.of(PropulsionType.GAS_OUTBOARD));
        boat.setPrimaryTransitPropulsionType(PropulsionType.GAS_OUTBOARD);
        boat.setActive(true);
        return boatRepository.save(boat).getId();
    }
}

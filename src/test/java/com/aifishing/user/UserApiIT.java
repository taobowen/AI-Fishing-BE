package com.aifishing.user;

import com.aifishing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiIT extends AbstractIntegrationTest {

    @Test
    void getAndPatchCurrentUser() throws Exception {
        mockMvc.perform(asDev(get("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("dev@aifishing.local")))
                .andExpect(jsonPath("$.displayName", is("Dev Angler")));

        mockMvc.perform(asDev(patch("/api/v1/me")).content("""
                        {"displayName":"Lake Captain"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Lake Captain")));
    }

    @Test
    void patchOwnedLureFamiliesAndKitSetupComplete() throws Exception {
        mockMvc.perform(asDev(patch("/api/v1/me")).content("""
                        {"ownedLureFamilies":["jigs","SOFT_PLASTICS","jigs"],"kitSetupComplete":true}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedLureFamilies[0]", is("JIGS")))
                .andExpect(jsonPath("$.ownedLureFamilies[1]", is("SOFT_PLASTICS")))
                .andExpect(jsonPath("$.ownedLureFamilies.length()", is(2)))
                .andExpect(jsonPath("$.kitSetupComplete", is(true)));

        mockMvc.perform(asDev(get("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedLureFamilies[0]", is("JIGS")))
                .andExpect(jsonPath("$.ownedLureFamilies[1]", is("SOFT_PLASTICS")))
                .andExpect(jsonPath("$.kitSetupComplete", is(true)));
    }

    @Test
    void unknownOwnedLureFamilyIsRejected() throws Exception {
        mockMvc.perform(asDev(patch("/api/v1/me")).content("""
                        {"ownedLureFamilies":["NED_RIG"]}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownUserIdIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }
}

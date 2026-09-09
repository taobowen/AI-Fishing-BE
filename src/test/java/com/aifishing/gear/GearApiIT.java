package com.aifishing.gear;

import com.aifishing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GearApiIT extends AbstractIntegrationTest {

    @Test
    void ownerCanCrudGearAndOtherUserGets404() throws Exception {
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/me/gear")).content("""
                        {"type":"ROD","name":"Victory","brand":"St. Croix"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Victory")))
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(asDev(get("/api/v1/me/gear/" + id)))
                .andExpect(status().isOk());

        mockMvc.perform(asOther(get("/api/v1/me/gear/" + id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));

        mockMvc.perform(asDev(delete("/api/v1/me/gear/" + id)))
                .andExpect(status().isNoContent());

        mockMvc.perform(asDev(get("/api/v1/me/gear/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    void lureProfileAutoNamesAndValidatesFamilyAwareSize() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/me/gear")).content("""
                        {
                          "type":"LURE",
                          "lureProfile":{
                            "lureFamily":"PADDLETAIL",
                            "lengthBand":"3_TO_4_IN",
                            "colors":["NATURAL","WHITE_PEARL"]
                          }
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("LURE")))
                .andExpect(jsonPath("$.lureProfile.lureFamily", is("PADDLETAIL")))
                .andExpect(jsonPath("$.name").isNotEmpty());

        mockMvc.perform(asDev(post("/api/v1/me/gear")).content("""
                        {
                          "type":"LURE",
                          "lureProfile":{
                            "lureFamily":"SPOON",
                            "lengthBand":"3_TO_4_IN",
                            "colors":["SILVER"]
                          }
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("LURE_PROFILE_INVALID")));

        mockMvc.perform(asDev(post("/api/v1/me/gear")).content("""
                        {
                          "type":"ROD",
                          "name":"Victory"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lureProfile").doesNotExist());
    }
}

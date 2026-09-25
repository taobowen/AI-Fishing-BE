package com.aifishing.analytics;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.analytics.domain.ProductAnalyticsEvent;
import com.aifishing.analytics.repo.ProductAnalyticsEventRepository;
import com.aifishing.seed.DevSeedIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductAnalyticsIT extends AbstractIntegrationTest {

    @Autowired
    private ProductAnalyticsEventRepository eventRepository;

    @Test
    void authenticatedUserCanPostEventAndOtherUserCannotReadIt() throws Exception {
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/analytics/events")).content("""
                        {
                          "name": "planning_mode_selected",
                          "properties": { "mode": "HYBRID" }
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("planning_mode_selected")))
                .andExpect(jsonPath("$.properties.mode", is("HYBRID")))
                .andReturn();

        UUID eventId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText());

        List<ProductAnalyticsEvent> owned = eventRepository.findByUserIdOrderByCreatedAtDesc(DevSeedIds.USER_ID);
        assertThat(owned).extracting(ProductAnalyticsEvent::getId).containsExactly(eventId);
        assertThat(owned.getFirst().getUserId()).isEqualTo(DevSeedIds.USER_ID);
        assertThat(owned.getFirst().getProperties()).containsEntry("mode", "HYBRID");

        // No read API — ownership is insert-scoped; another user must not see this row.
        assertThat(eventRepository.findByUserIdOrderByCreatedAtDesc(DevSeedIds.OTHER_USER_ID)).isEmpty();
        assertThat(eventRepository.countByUserId(DevSeedIds.OTHER_USER_ID)).isZero();

        mockMvc.perform(asOther(post("/api/v1/analytics/events")).content("""
                        {
                          "name": "planning_mode_selected",
                          "properties": { "mode": "AI" }
                        }
                        """))
                .andExpect(status().isCreated());

        assertThat(eventRepository.countByUserId(DevSeedIds.USER_ID)).isEqualTo(1);
        assertThat(eventRepository.countByUserId(DevSeedIds.OTHER_USER_ID)).isEqualTo(1);
        assertThat(eventRepository.findByUserIdOrderByCreatedAtDesc(DevSeedIds.OTHER_USER_ID))
                .extracting(ProductAnalyticsEvent::getId)
                .doesNotContain(eventId);
    }

    @Test
    void storesNameAndPropertiesForPlanningEvents() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/analytics/events")).content("""
                        {
                          "name": "planning_mode_selected",
                          "properties": { "mode": "CUSTOM" }
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("planning_mode_selected")))
                .andExpect(jsonPath("$.properties.mode", is("CUSTOM")));

        mockMvc.perform(asDev(post("/api/v1/analytics/events")).content("""
                        {
                          "name": "plan_generate_submitted",
                          "properties": {
                            "mode": "HYBRID",
                            "requiredPointCount": 2,
                            "templateTargetCount": 3
                          }
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("plan_generate_submitted")))
                .andExpect(jsonPath("$.properties.mode", is("HYBRID")))
                .andExpect(jsonPath("$.properties.requiredPointCount", is(2)))
                .andExpect(jsonPath("$.properties.templateTargetCount", is(3)));

        mockMvc.perform(asDev(post("/api/v1/analytics/events")).content("""
                        {
                          "name": "plan_generate_succeeded",
                          "properties": {
                            "mode": "AI",
                            "requiredPointCount": 0,
                            "templateTargetCount": 0,
                            "finalUserStopCount": 0,
                            "finalAiStopCount": 4,
                            "userFishingMinutes": 0,
                            "aiFishingMinutes": 90
                          }
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("plan_generate_succeeded")))
                .andExpect(jsonPath("$.properties.finalAiStopCount", is(4)))
                .andExpect(jsonPath("$.properties.aiFishingMinutes", is(90)));

        List<ProductAnalyticsEvent> stored = eventRepository.findByUserIdOrderByCreatedAtDesc(DevSeedIds.USER_ID);
        assertThat(stored).extracting(ProductAnalyticsEvent::getName)
                .containsExactlyInAnyOrder(
                        "planning_mode_selected",
                        "plan_generate_submitted",
                        "plan_generate_succeeded"
                );
        assertThat(stored).allSatisfy(event -> assertThat(event.getProperties()).isNotEmpty());
    }
}

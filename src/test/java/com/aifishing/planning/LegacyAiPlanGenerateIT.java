package com.aifishing.planning;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.repo.StrategyRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Old clients omit {@code planningMode}. Create + generate must stay on the AI-only path:
 * no template candidates, no required-point failure.
 */
class LegacyAiPlanGenerateIT extends AbstractIntegrationTest {

    @Autowired
    LakeFeatureRepository featureRepository;
    @Autowired
    StrategyRunRepository strategyRunRepository;
    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;

    @Test
    void createWithoutPlanningModeGeneratesAiOnlyPlanShape() throws Exception {
        ProcessingFixtures.seedBoundary(DevSeedIds.LAKE_ID, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG, 700, boundaryRepository);
        featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 50),
                2.5,
                3.5,
                0.86,
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

        MvcResult created = mockMvc.perform(asDev(post("/api/v1/trips")).content("""
                        {
                          "lakeId": "%s",
                          "primaryTargetSpecies": "SMALLMOUTH_BASS",
                          "plannedDate": "2026-09-12",
                          "fishingStartTime": "06:00:00",
                          "fishingEndTime": "15:00:00",
                          "fishingMode": "SHORE"
                        }
                        """.formatted(DevSeedIds.LAKE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planningMode", is("AI")))
                .andExpect(jsonPath("$.fishingTemplateId").doesNotExist())
                .andExpect(jsonPath("$.requiredPoints", is(org.hamcrest.Matchers.empty())))
                .andReturn();
        UUID tripId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

        var strategy = strategyRunRepository.save(PlanningFixtures.completedStrategy(
                tripId, PlanningFixtures.profile(), objectMapper));

        mockMvc.perform(asDev(post("/api/v1/trips/" + tripId + "/plan")
                        .content("{\"strategyRunId\":\"" + strategy.getId() + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.plan.waypoints.length()", greaterThan(0)))
                .andExpect(jsonPath("$.plan.waypoints[*].candidateSource", everyItem(is("AI"))))
                .andExpect(jsonPath("$.plan.planningBalance.mode", is("AI")))
                .andExpect(jsonPath("$.plan.planningBalance.requiredPointCount", is(0)))
                .andExpect(jsonPath("$.plan.planningBalance.templateTargetCount", is(0)))
                .andExpect(jsonPath("$.plan.planningBalance.finalUserStopCount", is(0)))
                .andExpect(jsonPath("$.plan.planningBalance.finalAiStopCount", greaterThan(0)))
                .andExpect(jsonPath("$.plan.featurePipeline", is("GIS")))
                .andExpect(jsonPath("$.plan.waypoints[0].location.lat", not(nullValue())));
    }
}

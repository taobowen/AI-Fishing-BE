package com.aifishing.seed;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ValidationCatalogIT extends AbstractIntegrationTest {

    @Autowired
    LakeDatasetStatusRepository datasetStatusRepository;

    @Test
    void ensureCatalogInsertsMissingLakesAndIsIdempotent() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/admin/lakes/validation-catalog")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lakes", hasSize(21)))
                .andExpect(jsonPath("$.lakes[?(@.id=='" + DevSeedIds.LAKE_ID + "')].created", org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.lakes[?(@.id=='" + DevSeedIds.RICE_LAKE_ID + "')].created", org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.lakes[?(@.id=='" + DevSeedIds.BALSAM_LAKE_ID + "')].created", org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.lakes[?(@.id=='" + DevSeedIds.BUCKHORN_LAKE_ID + "')].created", org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.lakes[?(@.id=='" + DevSeedIds.LOVESICK_LAKE_ID + "')].created", org.hamcrest.Matchers.hasItem(true)));

        assertThat(lakeRepository.existsById(DevSeedIds.RICE_LAKE_ID)).isTrue();
        assertThat(lakeRepository.existsById(DevSeedIds.SCUGOG_LAKE_ID)).isTrue();
        assertThat(lakeRepository.existsById(DevSeedIds.SIMCOE_LAKE_ID)).isTrue();
        assertThat(lakeRepository.existsById(DevSeedIds.BALSAM_LAKE_ID)).isTrue();
        assertThat(lakeRepository.existsById(DevSeedIds.CHRISTIE_LAKE_ID)).isTrue();
        assertThat(lakeRepository.findById(DevSeedIds.LAKE_ID).orElseThrow().getCardImagePath())
                .isEqualTo("lakes/head.jpg");
        assertThat(lakeRepository.findById(DevSeedIds.BALSAM_LAKE_ID).orElseThrow().getCardImagePath())
                .isEqualTo("lakes/balsam.jpg");
        assertThat(lakeRepository.findById(DevSeedIds.HEART_LAKE_ID).orElseThrow().getName()).isEqualTo("Heart Lake");
        assertThat(lakeRepository.findById(DevSeedIds.CHRISTIE_LAKE_ID).orElseThrow().getName()).isEqualTo("Christie Lake");
        long statuses = datasetStatusRepository.count();

        mockMvc.perform(asDev(post("/api/v1/admin/lakes/validation-catalog")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lakes", hasSize(21)))
                .andExpect(jsonPath("$.lakes[0].created", is(false)))
                .andExpect(jsonPath("$.lakes[20].created", is(false)));

        assertThat(datasetStatusRepository.count()).isEqualTo(statuses);
        assertThat(lakeRepository.count()).isEqualTo(21);
    }
}

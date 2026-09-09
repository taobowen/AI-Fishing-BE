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
                .andExpect(jsonPath("$.lakes", hasSize(4)))
                .andExpect(jsonPath("$.lakes[?(@.id=='" + DevSeedIds.LAKE_ID + "')].created", org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.lakes[?(@.id=='" + DevSeedIds.RICE_LAKE_ID + "')].created", org.hamcrest.Matchers.hasItem(true)));

        assertThat(lakeRepository.existsById(DevSeedIds.RICE_LAKE_ID)).isTrue();
        assertThat(lakeRepository.existsById(DevSeedIds.SCUGOG_LAKE_ID)).isTrue();
        assertThat(lakeRepository.existsById(DevSeedIds.SIMCOE_LAKE_ID)).isTrue();
        long statuses = datasetStatusRepository.count();

        mockMvc.perform(asDev(post("/api/v1/admin/lakes/validation-catalog")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lakes", hasSize(4)))
                .andExpect(jsonPath("$.lakes[0].created", is(false)))
                .andExpect(jsonPath("$.lakes[1].created", is(false)))
                .andExpect(jsonPath("$.lakes[2].created", is(false)))
                .andExpect(jsonPath("$.lakes[3].created", is(false)));

        assertThat(datasetStatusRepository.count()).isEqualTo(statuses);
        assertThat(lakeRepository.count()).isEqualTo(4);
    }
}

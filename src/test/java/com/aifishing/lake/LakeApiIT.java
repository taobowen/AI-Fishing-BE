package com.aifishing.lake;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.ingestion.repo.BathymetryContourRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.seed.DevSeedIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LakeApiIT extends AbstractIntegrationTest {

    @Autowired
    BathymetryContourRepository contourRepository;

    @Test
    void searchAndGetHeadLake() throws Exception {
        mockMvc.perform(asDev(get("/api/v1/lakes").param("query", "Head")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Head Lake")))
                .andExpect(jsonPath("$[0].timeZoneId", is("America/Toronto")))
                .andExpect(jsonPath("$[0].centroid.lat", is(44.75)))
                .andExpect(jsonPath("$[0].centroid.lng", is(-78.92)));

        mockMvc.perform(asDev(get("/api/v1/lakes/" + DevSeedIds.LAKE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.province", is("Ontario")));
    }

    @Test
    void lakesAreReadOnly() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/lakes")).content("""
                        {"name":"Should not exist"}
                        """))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void contoursReturnWaterDepthAndLineGeometry() throws Exception {
        BathymetryContour contour = new BathymetryContour();
        contour.setLakeId(DevSeedIds.LAKE_ID);
        contour.setProvider("TEST");
        contour.setSource("TEST");
        contour.setImportVersion("map-overlay");
        contour.setSourceRecordId("contour-1");
        contour.setDepthM(BigDecimal.valueOf(-3.7));
        contour.setGeometry(ProcessingFixtures.closedSquare(
                PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 40));
        contourRepository.save(contour);

        mockMvc.perform(asDev(get("/api/v1/lakes/" + DevSeedIds.LAKE_ID + "/contours")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].depthM", closeTo(3.7, 0.001)))
                .andExpect(jsonPath("$[0].geometry.lines[0].length()", greaterThan(1)))
                .andExpect(jsonPath("$[0].geometry.lines[0][0].lat").exists())
                .andExpect(jsonPath("$[0].geometry.lines[0][0].lng").exists());
    }
}

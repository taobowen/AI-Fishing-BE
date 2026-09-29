package com.aifishing.fishingtemplate;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.seed.DevSeedIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FishingTemplateApiIT extends AbstractIntegrationTest {

    /** Inside the seeded 600 m boundary, outside the 40 m centered island. */
    private static final double WATER_LNG = PlanningFixtures.HEAD_LNG + 0.002;
    private static final double WATER_LAT = PlanningFixtures.HEAD_LAT;

    @Autowired
    private LakeBoundaryRecordRepository boundaryRepository;
    @Autowired
    private LakeWaterwayRepository waterwayRepository;
    @Autowired
    private LakeDatasetStatusRepository statusRepository;

    @BeforeEach
    void seedWater() {
        ProcessingFixtures.seedShorelineOnly(
                DevSeedIds.LAKE_ID,
                PlanningFixtures.HEAD_LAT,
                PlanningFixtures.HEAD_LNG,
                waterwayRepository,
                boundaryRepository,
                statusRepository
        );
    }

    @Test
    void crudIsScopedToOwnerAndLake() throws Exception {
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/fishing-templates")).content("""
                        {
                          "lakeId": "%s",
                          "name": "Weed edges",
                          "targets": [
                            {
                              "kind": "POINT",
                              "name": "Spot A",
                              "geometry": { "type": "Point", "coordinates": [%s, %s] }
                            }
                          ]
                        }
                        """.formatted(
                DevSeedIds.LAKE_ID,
                WATER_LNG,
                WATER_LAT
        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Weed edges")))
                .andExpect(jsonPath("$.lakeId", is(DevSeedIds.LAKE_ID.toString())))
                .andExpect(jsonPath("$.targets", hasSize(1)))
                .andExpect(jsonPath("$.targets[0].kind", is("POINT")))
                .andReturn();
        String templateId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(asDev(get("/api/v1/fishing-templates").param("lakeId", DevSeedIds.LAKE_ID.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(templateId)));

        mockMvc.perform(asOther(get("/api/v1/fishing-templates/" + templateId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(asOther(get("/api/v1/fishing-templates").param("lakeId", DevSeedIds.LAKE_ID.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        seedLake(DevSeedIds.RICE_LAKE_ID, "Rice Lake", 44.2, -78.1);
        mockMvc.perform(asDev(get("/api/v1/fishing-templates")
                        .param("lakeId", DevSeedIds.RICE_LAKE_ID.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(asDev(patch("/api/v1/fishing-templates/" + templateId)).content("""
                        { "name": "Updated weed edges" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated weed edges")));

        mockMvc.perform(asDev(delete("/api/v1/fishing-templates/" + templateId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(asDev(get("/api/v1/fishing-templates/" + templateId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsPointOutsideWater() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/fishing-templates")).content("""
                        {
                          "lakeId": "%s",
                          "name": "Land spot",
                          "targets": [
                            {
                              "kind": "POINT",
                              "geometry": { "type": "Point", "coordinates": [-79.5, 43.5] }
                            }
                          ]
                        }
                        """.formatted(DevSeedIds.LAKE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("TEMPLATE_GEOMETRY_INVALID")));
    }

    @Test
    void rejectsLandCrossingPathAsOneSegment() throws Exception {
        double lng = WATER_LNG;
        double lat = WATER_LAT;
        mockMvc.perform(asDev(post("/api/v1/fishing-templates")).content("""
                        {
                          "lakeId": "%s",
                          "name": "Land path",
                          "targets": [
                            {
                              "kind": "PATH",
                              "geometry": {
                                "type": "LineString",
                                "coordinates": [
                                  [%s, %s],
                                  [%s, %s],
                                  [%s, %s]
                                ]
                              }
                            }
                          ]
                        }
                        """.formatted(
                DevSeedIds.LAKE_ID,
                lng, lat,
                lng + 0.05, lat,
                lng, lat + 0.001
        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("TEMPLATE_GEOMETRY_INVALID")));
    }

    @Test
    void rejectsTooManyTargetsWithoutSilentTruncate() throws Exception {
        StringBuilder targets = new StringBuilder();
        for (int i = 0; i < 41; i++) {
            if (i > 0) {
                targets.append(',');
            }
            targets.append("""
                    {
                      "kind": "POINT",
                      "geometry": { "type": "Point", "coordinates": [%s, %s] }
                    }
                    """.formatted(WATER_LNG, WATER_LAT));
        }
        mockMvc.perform(asDev(post("/api/v1/fishing-templates")).content("""
                        {
                          "lakeId": "%s",
                          "name": "Too many",
                          "targets": [%s]
                        }
                        """.formatted(DevSeedIds.LAKE_ID, targets)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("TEMPLATE_GEOMETRY_INVALID")));
    }

    @Test
    void storesZoneAsWaterIntersectionAndRejectsAnEmptyOne() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/fishing-templates")).content("""
                        {
                          "lakeId": "%s",
                          "name": "Bay",
                          "targets": [
                            {
                              "kind": "ZONE",
                              "name": "Pocket",
                              "sortOrder": 0,
                              "geometry": {
                                "type": "Polygon",
                                "coordinates": [[
                                  [%s, %s],
                                  [%s, %s],
                                  [%s, %s],
                                  [%s, %s]
                                ]]
                              }
                            }
                          ]
                        }
                        """.formatted(
                DevSeedIds.LAKE_ID,
                WATER_LNG, WATER_LAT,
                WATER_LNG + 0.001, WATER_LAT,
                WATER_LNG + 0.001, WATER_LAT + 0.001,
                WATER_LNG, WATER_LAT
        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targets[0].kind", is("ZONE")))
                .andExpect(jsonPath("$.targets[0].sortOrder", is(0)));

        mockMvc.perform(asDev(post("/api/v1/fishing-templates")).content("""
                        {
                          "lakeId": "%s",
                          "name": "Dry",
                          "targets": [
                            {
                              "kind": "ZONE",
                              "geometry": {
                                "type": "Polygon",
                                "coordinates": [[
                                  [-79.5, 43.5],
                                  [-79.49, 43.5],
                                  [-79.49, 43.51],
                                  [-79.5, 43.5]
                                ]]
                              }
                            }
                          ]
                        }
                        """.formatted(DevSeedIds.LAKE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("TEMPLATE_GEOMETRY_INVALID")));
    }

    @Test
    void simplifiesADenseFreehandPathBeforePersistence() throws Exception {
        StringBuilder coordinates = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            if (i > 0) {
                coordinates.append(',');
            }
            coordinates.append("[%s, %s]".formatted(WATER_LNG + i * 0.00001, WATER_LAT));
        }
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/fishing-templates")).content("""
                        {
                          "lakeId": "%s",
                          "name": "Scribble",
                          "targets": [
                            {
                              "kind": "PATH",
                              "geometry": { "type": "LineString", "coordinates": [%s] }
                            }
                          ]
                        }
                        """.formatted(DevSeedIds.LAKE_ID, coordinates)))
                .andExpect(status().isCreated())
                .andReturn();
        int stored = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("targets").get(0).get("geometry").get("coordinates").size();
        org.junit.jupiter.api.Assertions.assertTrue(stored < 8, "expected a simplified path, stored " + stored);
    }

    @Test
    void editsAndDeletesPointPathAndZoneWithoutTouchingAnotherTemplate() throws Exception {
        String path = """
                [%s, %s], [%s, %s]
                """.formatted(WATER_LNG, WATER_LAT, WATER_LNG + 0.0004, WATER_LAT);
        String zone = """
                [%s, %s], [%s, %s], [%s, %s], [%s, %s]
                """.formatted(
                WATER_LNG, WATER_LAT,
                WATER_LNG + 0.0008, WATER_LAT,
                WATER_LNG + 0.0008, WATER_LAT + 0.0008,
                WATER_LNG, WATER_LAT
        );
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/fishing-templates")).content("""
                        {
                          "lakeId": "%s",
                          "name": "Mixed",
                          "targets": [
                            { "kind": "POINT", "name": "A", "sortOrder": 0,
                              "geometry": { "type": "Point", "coordinates": [%s, %s] } },
                            { "kind": "PATH", "name": "B", "sortOrder": 1,
                              "geometry": { "type": "LineString", "coordinates": [%s] } },
                            { "kind": "ZONE", "name": "C", "sortOrder": 2,
                              "geometry": { "type": "Polygon", "coordinates": [[%s]] } }
                          ]
                        }
                        """.formatted(DevSeedIds.LAKE_ID, WATER_LNG, WATER_LAT, path, zone)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targets", hasSize(3)))
                .andExpect(jsonPath("$.targets[0].kind", is("POINT")))
                .andExpect(jsonPath("$.targets[1].kind", is("PATH")))
                .andExpect(jsonPath("$.targets[2].kind", is("ZONE")))
                .andReturn();
        String templateId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        MvcResult other = mockMvc.perform(asDev(post("/api/v1/fishing-templates")).content("""
                        {
                          "lakeId": "%s",
                          "name": "Untouched",
                          "targets": [
                            { "kind": "POINT", "name": "Keep",
                              "geometry": { "type": "Point", "coordinates": [%s, %s] } }
                          ]
                        }
                        """.formatted(DevSeedIds.LAKE_ID, WATER_LNG, WATER_LAT)))
                .andExpect(status().isCreated())
                .andReturn();
        String otherId = objectMapper.readTree(other.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(asOther(patch("/api/v1/fishing-templates/" + templateId)).content("""
                        { "name": "Stolen" }
                        """))
                .andExpect(status().isNotFound());

        String movedPath = """
                [%s, %s], [%s, %s], [%s, %s]
                """.formatted(
                WATER_LNG, WATER_LAT,
                WATER_LNG + 0.0003, WATER_LAT + 0.0002,
                WATER_LNG + 0.0006, WATER_LAT
        );
        mockMvc.perform(asDev(patch("/api/v1/fishing-templates/" + templateId)).content("""
                        {
                          "targets": [
                            { "kind": "POINT", "name": "A moved", "sortOrder": 1,
                              "geometry": { "type": "Point", "coordinates": [%s, %s] } },
                            { "kind": "PATH", "name": "B moved", "sortOrder": 0,
                              "geometry": { "type": "LineString", "coordinates": [%s] } }
                          ]
                        }
                        """.formatted(WATER_LNG + 0.0002, WATER_LAT, movedPath)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets", hasSize(2)))
                .andExpect(jsonPath("$.targets[0].kind", is("PATH")))
                .andExpect(jsonPath("$.targets[0].name", is("B moved")))
                .andExpect(jsonPath("$.targets[1].kind", is("POINT")))
                .andExpect(jsonPath("$.targets[1].name", is("A moved")));

        mockMvc.perform(asDev(get("/api/v1/fishing-templates/" + otherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Untouched")))
                .andExpect(jsonPath("$.targets", hasSize(1)))
                .andExpect(jsonPath("$.targets[0].name", is("Keep")));

        mockMvc.perform(asDev(patch("/api/v1/fishing-templates/" + templateId)).content("""
                        { "targets": [] }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets", hasSize(0)));
    }
}

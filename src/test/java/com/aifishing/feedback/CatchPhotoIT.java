package com.aifishing.feedback;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.storage.LocalObjectStore;
import com.aifishing.trip.domain.Trip;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatchPhotoIT extends AbstractIntegrationTest {

    @Autowired
    LocalObjectStore objectStore;

    @Test
    void completeUsesHeadMetadataAndRejectsOversizedObject() throws Exception {
        String catchId = createCatch();
        MvcResult upload = mockMvc.perform(asDev(post("/api/v1/catches/" + catchId + "/photos/upload"))
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photoId").exists())
                .andExpect(jsonPath("$.putUrl").exists())
                .andReturn();
        UUID photoId = UUID.fromString(objectMapper.readTree(upload.getResponse().getContentAsString()).get("photoId").asText());
        String key = jdbcTemplate.queryForObject("select s3_key from catch_photos where id = ?", String.class, photoId);

        objectStore.put(key, "tiny-jpeg".getBytes(), "image/jpeg");
        mockMvc.perform(asDev(post("/api/v1/catches/" + catchId + "/photos/" + photoId + "/complete")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("READY")))
                .andExpect(jsonPath("$.contentType", is("image/jpeg")))
                .andExpect(jsonPath("$.sizeBytes", is(9)))
                .andExpect(jsonPath("$.getUrl").exists());

        mockMvc.perform(asDev(get("/api/v1/catches/" + catchId + "/photos")))
                .andExpect(jsonPath("$.length()", is(1)));

        mockMvc.perform(asOther(post("/api/v1/catches/" + catchId + "/photos/upload"))
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(asDev(delete("/api/v1/catches/" + catchId + "/photos/" + photoId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(asDev(get("/api/v1/catches/" + catchId + "/photos")))
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void completeRejectsDisallowedTypeAndMissingObject() throws Exception {
        String catchId = createCatch();
        UUID missing = startUpload(catchId);
        mockMvc.perform(asDev(post("/api/v1/catches/" + catchId + "/photos/" + missing + "/complete")))
                .andExpect(status().isBadRequest());

        UUID wrongType = startUpload(catchId);
        String key = jdbcTemplate.queryForObject("select s3_key from catch_photos where id = ?", String.class, wrongType);
        objectStore.put(key, "not-an-image".getBytes(), "application/pdf");
        mockMvc.perform(asDev(post("/api/v1/catches/" + catchId + "/photos/" + wrongType + "/complete")))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(
                jdbcTemplate.queryForObject("select status from catch_photos where id = ?", String.class, wrongType)
        ).isEqualTo("PENDING");

        UUID huge = startUpload(catchId);
        String hugeKey = jdbcTemplate.queryForObject("select s3_key from catch_photos where id = ?", String.class, huge);
        objectStore.put(hugeKey, new byte[9_000_000], "image/jpeg");
        mockMvc.perform(asDev(post("/api/v1/catches/" + catchId + "/photos/" + huge + "/complete")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void photoFailureDoesNotVoidCatch() throws Exception {
        String catchId = createCatch();
        UUID photoId = startUpload(catchId);
        mockMvc.perform(asDev(post("/api/v1/catches/" + catchId + "/photos/" + photoId + "/complete")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asDev(get("/api/v1/catches/" + catchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    private UUID startUpload(String catchId) throws Exception {
        MvcResult upload = mockMvc.perform(asDev(post("/api/v1/catches/" + catchId + "/photos/upload"))
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(upload.getResponse().getContentAsString()).get("photoId").asText());
    }

    private String createCatch() throws Exception {
        Trip trip = tripRepository.save(PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE));
        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(Instant.parse("2026-09-02T12:00:00Z"));
        plan.setPlanningAlgorithmVersion("photo-test");
        tripPlanRepository.save(plan);
        TripWaypoint waypoint = new TripWaypoint();
        waypoint.setTripPlanId(plan.getId());
        waypoint.setSequence(1);
        waypoint.setLocation(geoMapper.toPoint(new com.aifishing.common.geo.GeoPointDto(44.75, -78.92)));
        waypoint.setReason("wp");
        tripWaypointRepository.save(waypoint);
        MvcResult started = mockMvc.perform(asDev(post("/api/v1/trips/" + trip.getId() + "/fishing-sessions")).content("{}"))
                .andReturn();
        JsonNode tree = objectMapper.readTree(started.getResponse().getContentAsString());
        String sessionId = tree.get("id").asText();
        Instant occurredAt = Instant.parse(tree.get("startedAt").asText()).plusSeconds(30);
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/fishing-sessions/" + sessionId + "/catches"))
                        .content("{\"clientCatchId\":\"photo-c1\",\"occurredAt\":\"" + occurredAt + "\",\"waypointId\":\"" + waypoint.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }
}

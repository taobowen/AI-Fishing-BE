package com.aifishing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiDocsIT extends AbstractIntegrationTest {

    @Test
    void sessionPathsAreDocumented() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode paths = objectMapper.readTree(json).get("paths");
        assertThat(paths.has("/api/v1/trips/{tripId}/fishing-sessions")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/current")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/locations")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/waypoints/{waypointId}/arrive")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/catches")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/performance")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/results")).isTrue();
        assertThat(paths.has("/api/v1/trips/past")).isTrue();
        assertThat(paths.has("/api/v1/planning/weather-preview")).isTrue();
        assertThat(paths.has("/api/v1/catches/{catchId}")).isTrue();
        assertThat(paths.has("/api/v1/catches/{catchId}/photos")).isTrue();
        assertThat(paths.has("/api/v1/catches/{catchId}/photos/upload")).isTrue();
        assertThat(paths.has("/api/v1/admin/lakes/validation-catalog")).isTrue();
        assertThat(paths.has("/api/v1/admin/lakes/{lakeId}/bootstrap-validation")).isTrue();
        assertThat(paths.has("/api/v1/me/boats/{id}/resolve-capability")).isTrue();
        assertThat(paths.has("/api/v1/lakes/{id}/boat-launches")).isTrue();
        assertThat(paths.has("/api/v1/lakes/{id}/boat-launches/custom-preview")).isTrue();
        assertThat(paths.has("/api/v1/lakes/{id}/planning-capabilities")).isTrue();
        assertThat(paths.has("/api/v1/admin/lakes/{lakeId}/spatial-snapshots")).isTrue();
        assertThat(paths.has("/api/v1/admin/lakes/jobs/{jobId}")).isTrue();
        assertThat(paths.has("/api/v1/admin/trips/{tripId}/plan-diagnostics")).isTrue();
        assertThat(paths.has("/api/v1/trips/{tripId}/plans/{planId}/tactics")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/guidance/decisions")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/guidance/current")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/guidance/decisions/{decisionId}/feedback")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/bites")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/fish-on")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/lure-events")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/ad-hoc-fishing/start")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/ad-hoc-fishing/end")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/stationary-prompt/dismiss")).isTrue();
        assertThat(paths.has("/api/v1/me/boats/analyze")).isTrue();
        JsonNode schemas = objectMapper.readTree(json).get("components").get("schemas");
        assertThat(schemas.get("CreateCatchRequest").get("properties").has("isTargetSpecies")).isTrue();
        assertThat(schemas.get("CreateCatchRequest").get("properties").has("sizeBucket")).isTrue();
        assertThat(schemas.get("UpdateCatchRequest").get("properties").has("isTargetSpecies")).isTrue();
        assertThat(schemas.get("UpdateCatchRequest").get("properties").has("sizeBucket")).isTrue();
        assertThat(schemas.get("CatchEventResponse").get("properties").has("isTargetSpecies")).isTrue();
        assertThat(schemas.get("CatchEventResponse").get("properties").has("sizeBucket")).isTrue();
        assertThat(schemas.get("UserResponse").get("properties").has("ownedLureFamilies")).isTrue();
        assertThat(schemas.get("UserResponse").get("properties").has("kitSetupComplete")).isTrue();
        assertThat(schemas.get("UpdateUserRequest").get("properties").has("ownedLureFamilies")).isTrue();
        assertThat(schemas.get("UpdateUserRequest").get("properties").has("kitSetupComplete")).isTrue();
        assertThat(schemas.has("AnalyzeBoatRequest")).isTrue();
        assertThat(schemas.has("AnalyzeBoatResponse")).isTrue();
        assertThat(schemas.get("AnalyzeBoatResponse").get("properties").has("extractionGaps")).isTrue();
        assertThat(schemas.get("AnalyzeBoatResponse").get("properties").has("missingHints")).isTrue();
        assertThat(schemas.get("BoatResponse").get("properties").has("windWaveOverride")).isTrue();
        assertThat(schemas.get("TripResponse").get("properties").has("lakeCardImageUrl")).isTrue();
        assertThat(schemas.get("PastTripResponse").get("properties").has("lakeCardImageUrl")).isTrue();
        assertThat(schemas.get("UserPlanSummaryResponse").get("properties").has("lakeCardImageUrl")).isTrue();
        assertThat(schemas.get("LakeSummaryResponse").get("properties").has("cardImageUrl")).isTrue();
        assertThat(schemas.get("LakeResponse").get("properties").has("cardImageUrl")).isTrue();
        if (Boolean.parseBoolean(System.getProperty("export.openapi", "false"))) {
            Path target = Path.of("..", "AI-Fishing-FE", "openapi.json").normalize();
            ObjectMapper pretty = objectMapper.copy();
            Files.writeString(target, pretty.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
        }
    }
}

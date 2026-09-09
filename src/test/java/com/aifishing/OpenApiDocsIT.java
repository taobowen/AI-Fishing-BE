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
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/locations")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/waypoints/{waypointId}/arrive")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/catches")).isTrue();
        assertThat(paths.has("/api/v1/fishing-sessions/{sessionId}/performance")).isTrue();
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
        assertThat(paths.has("/api/v1/admin/trips/{tripId}/plan-diagnostics")).isTrue();
        if (Boolean.parseBoolean(System.getProperty("export.openapi", "false"))) {
            Path target = Path.of("..", "AI-Fishing-FE", "openapi.json").normalize();
            ObjectMapper pretty = objectMapper.copy();
            Files.writeString(target, pretty.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
        }
    }
}

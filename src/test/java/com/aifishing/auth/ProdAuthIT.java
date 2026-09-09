package com.aifishing.auth;

import com.aifishing.seed.DevSeedIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(TestJwts.DecoderConfig.class)
class ProdAuthIT {

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.auth.issuer", () -> TestJwts.ISSUER);
        registry.add("app.auth.client-id", () -> TestJwts.CLIENT_ID);
        registry.add("app.auth.web-client-id", () -> TestJwts.WEB_CLIENT_ID);
        registry.add("app.raw.storage", () -> "local");
        registry.add("app.raw.local-dir", () -> "./target/test-raw");
        registry.add("app.s3.bucket", () -> "test-bucket");
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.admin.enabled", () -> "true");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetUsers() {
        jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/trips").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void xUserIdIsIgnoredInProd() throws Exception {
        mockMvc.perform(get("/api/v1/trips")
                        .header("X-User-Id", DevSeedIds.USER_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void idTokenIsRejected() throws Exception {
        String token = TestJwts.accessToken("sub-id", "id@example.com", TestJwts.CLIENT_ID, "id", List.of());
        mockMvc.perform(get("/api/v1/trips").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongClientIdIsRejected() throws Exception {
        String token = TestJwts.accessToken("sub-wrong", "wrong@example.com", "other-client", "access", List.of());
        mockMvc.perform(get("/api/v1/trips").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenAuthenticates() throws Exception {
        String token = TestJwts.accessToken("sub-ok", "ok@example.com", TestJwts.CLIENT_ID, "access", List.of());
        mockMvc.perform(get("/api/v1/trips").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void webClientAccessTokenAuthenticates() throws Exception {
        String token = TestJwts.accessToken("sub-web", "web@example.com", TestJwts.WEB_CLIENT_ID, "access", List.of());
        mockMvc.perform(get("/api/v1/trips").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void roleUserCannotCallAdmin() throws Exception {
        String token = TestJwts.accessToken("sub-user", "user@example.com", TestJwts.CLIENT_ID, "access", List.of());
        mockMvc.perform(get("/api/v1/admin/trips/" + UUID.randomUUID() + "/planning-runs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void emailConflictReturns409() throws Exception {
        String first = TestJwts.accessToken("sub-a", "shared@example.com", TestJwts.CLIENT_ID, "access", List.of());
        mockMvc.perform(get("/api/v1/trips").header("Authorization", "Bearer " + first))
                .andExpect(status().isOk());
        String second = TestJwts.accessToken("sub-b", "shared@example.com", TestJwts.CLIENT_ID, "access", List.of());
        mockMvc.perform(get("/api/v1/trips").header("Authorization", "Bearer " + second))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_EMAIL_CONFLICT"));
    }
}

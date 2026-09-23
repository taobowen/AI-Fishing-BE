package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.UsageTelemetry;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Testcontainers
class UsageTelemetryStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres")
    );

    private static JdbcTemplate jdbcTemplate;
    private static UsageTelemetryStore store;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new UsageTelemetryStore(mock(AgentRunRepository.class), jdbcTemplate);
    }

    @Test
    void v40AddsNullableUsageColumnsAndPersistsRawIdentityWithoutInventedZeros() throws Exception {
        assertThat(columnNullable("agent_runs", "raw_provider_usage")).isEqualTo("YES");
        assertThat(columnNullable("agent_runs", "usage_telemetry")).isEqualTo("YES");
        assertThat(columnNullable("guidance_eval_case_results", "raw_provider_usage")).isEqualTo("YES");
        assertThat(columnNullable("guidance_eval_case_results", "usage_telemetry")).isEqualTo("YES");

        UUID runId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into guidance_eval_runs (
                            id, schema_version, suite, kind, replay_mode, started_at
                        ) values (?, 'guidance.contracts.v1', 'platform/usage', 'PLATFORM_REGRESSION',
                            'FROZEN_REPLAY', now())
                        """,
                runId
        );
        jdbcTemplate.update(
                """
                        insert into guidance_eval_case_results (
                            id, eval_run_id, schema_version, case_id, status
                        ) values (?, ?, 'guidance.contracts.v1', 'platform/usage/unknown', 'PASS')
                        """,
                caseId,
                runId
        );

        UsageTelemetry unknown = UsageTelemetryAssembler.assemble(
                "deterministic", "fixture", "1", "prompt-1",
                Map.of(),
                TokenPricing.none()
        );
        store.persistForEvalCase(caseId, Map.of("id", "resp-1"), unknown);

        String raw = jdbcTemplate.queryForObject(
                "select raw_provider_usage::text from guidance_eval_case_results where id = ?",
                String.class,
                caseId
        );
        JsonNode telemetry = MAPPER.readTree(jdbcTemplate.queryForObject(
                "select usage_telemetry::text from guidance_eval_case_results where id = ?",
                String.class,
                caseId
        ));
        assertThat(raw).contains("resp-1");
        assertThat(telemetry.path("modelProvider").asText()).isEqualTo("deterministic");
        assertThat(telemetry.path("modelName").asText()).isEqualTo("fixture");
        assertThat(telemetry.has("inputTokens")).isFalse();
        assertThat(telemetry.has("outputTokens")).isFalse();
        assertThat(telemetry.has("totalTokens")).isFalse();
        assertThat(telemetry.has("costUsd")).isFalse();

        UsageTelemetry priced = UsageTelemetryAssembler.assemble(
                "openai", "gpt-4o", "v1", "prompt-1",
                Map.of("input_tokens", 200, "output_tokens", 50),
                new TokenPricing("pricing-2026-09", 0.01, 0.02)
        );
        store.persistForEvalCase(caseId, Map.of("input_tokens", 200, "output_tokens", 50), priced);
        JsonNode pricedNode = MAPPER.readTree(jdbcTemplate.queryForObject(
                "select usage_telemetry::text from guidance_eval_case_results where id = ?",
                String.class,
                caseId
        ));
        assertThat(pricedNode.path("pricingVersion").asText()).isEqualTo("pricing-2026-09");
        assertThat(pricedNode.path("inputTokens").asInt()).isEqualTo(200);
        assertThat(pricedNode.path("costUsd").asDouble()).isEqualTo(0.003);
    }

    private static String columnNullable(String table, String name) {
        return jdbcTemplate.queryForObject(
                """
                        select is_nullable
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = ?
                          and column_name = ?
                        """,
                String.class,
                table,
                name
        );
    }
}

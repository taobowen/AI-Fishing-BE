package com.aifishing.guidance;

import com.aifishing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuidanceFlywayV37IT extends AbstractIntegrationTest {

    @Test
    void nullGrainDimensionsCollideAndGeographyIsMeterReady() {
        UUID first = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into historical_performance (
                            id, schema_version, empirical_algorithm_version, updated_at
                        ) values (?, 'guidance.contracts.v1', 1, now())
                        """,
                first
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        insert into historical_performance (
                            id, schema_version, empirical_algorithm_version, updated_at
                        ) values (?, 'guidance.contracts.v1', 1, now())
                        """,
                UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update(
                """
                        insert into historical_performance (
                            id, schema_version, empirical_algorithm_version, updated_at
                        ) values (?, 'guidance.contracts.v1', 2, now())
                        """,
                UUID.randomUUID()
        );

        assertThat(columnType("session_live_position", "location")).isEqualTo("geography");
        assertThat(jdbcTemplate.queryForObject(
                """
                        select indexdef from pg_indexes
                        where schemaname = 'public'
                          and tablename = 'session_live_position'
                          and indexdef ilike '%gist%'
                        """,
                String.class
        )).contains("location");

        assertThat(column("user_action_events", "followed_recommendation")).isEqualTo("YES");
        assertThat(column("user_action_events", "recommendation_role")).isEqualTo("YES");
        assertThat(constraint("guidance_learning_outbox_job_type_check"))
                .contains("ATTRIBUTE_OUTCOME", "AGGREGATE_EMPIRICAL", "SESSION_SUMMARY",
                        "REFLECTION_EVAL", "PREFERENCE_UPDATE");
    }

    private String column(String table, String name) {
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

    private String columnType(String table, String name) {
        return jdbcTemplate.queryForObject(
                """
                        select udt_name
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

    private String constraint(String name) {
        return jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = ?",
                String.class,
                name
        );
    }
}

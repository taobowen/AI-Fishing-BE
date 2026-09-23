package com.aifishing.guidance;

import com.aifishing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceFlywayV34IT extends AbstractIntegrationTest {

    @Test
    void v34CreatesAuditTablesAndPhase4MemoryTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "select tablename from pg_tables where schemaname = 'public'",
                String.class
        );
        assertThat(tables).contains(
                "session_events",
                "weather_snapshots",
                "lure_events",
                "agent_runs",
                "agent_tool_calls",
                "agent_candidate_decisions",
                "agent_validations",
                "agent_delivered_decisions",
                "agent_feedback",
                "user_action_events",
                "guidance_plan_versions",
                "guidance_plan_steps",
                "historical_performance_contributions",
                "historical_performance",
                "user_fishing_preferences",
                "inferred_user_preferences",
                "semantic_memories",
                "session_summaries",
                "agent_reflections",
                "outcome_attributions",
                "session_live_position",
                "guidance_learning_outbox"
        );
        assertThat(tables).doesNotContain(
                "waypoint_performance",
                "lure_performance",
                "structure_performance"
        );

        assertThat(constraint("historical_perf_contrib_grain_key"))
                .contains("UNIQUE NULLS NOT DISTINCT")
                .contains("empirical_algorithm_version");
        assertThat(constraint("historical_performance_grain_key"))
                .contains("UNIQUE NULLS NOT DISTINCT")
                .contains("empirical_algorithm_version")
                .doesNotContain("fishing_session_id");

        assertThat(jdbcTemplate.queryForObject(
                """
                        select type || ',' || srid
                        from geography_columns
                        where f_table_schema = 'public'
                          and f_table_name = 'session_live_position'
                          and f_geography_column = 'location'
                        """,
                String.class
        )).isEqualToIgnoringCase("POINT,4326");

        assertThat(jdbcTemplate.queryForList(
                """
                        select indexdef from pg_indexes
                        where schemaname = 'public' and tablename = 'session_live_position'
                        """,
                String.class
        )).anyMatch(index -> index.toLowerCase().contains("gist") && index.contains("location"));
    }

    private String constraint(String name) {
        return jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = ?",
                String.class,
                name
        );
    }
}

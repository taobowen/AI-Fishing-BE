package com.aifishing.guidance;

import com.aifishing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceFlywayV36IT extends AbstractIntegrationTest {

    @Test
    void v36AddsActivityColumnsBiteAndOutbox() {
        assertThat(column("fishing_sessions", "activity_state")).isEqualTo("NO");
        assertThat(column("fishing_sessions", "activity_state_since")).isEqualTo("YES");
        assertThat(column("fishing_sessions", "activity_state_source")).isEqualTo("NO");
        assertThat(column("fishing_sessions", "activity_state_override")).isEqualTo("YES");
        assertThat(column("fishing_sessions", "activity_state_override_since")).isEqualTo("YES");
        assertThat(column("session_events", "fish_interaction_id")).isEqualTo("YES");

        assertThat(jdbcTemplate.queryForList(
                "select tablename from pg_tables where schemaname = 'public' and tablename = 'guidance_trigger_outbox'",
                String.class
        )).containsExactly("guidance_trigger_outbox");

        String eventTypes = jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = 'session_events_type_check'",
                String.class
        );
        assertThat(eventTypes).contains("BITE");

        String triggers = jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = 'agent_runs_trigger_check'",
                String.class
        );
        assertThat(triggers).contains("REPEATED_BITE_PATTERN");

        String outboxStatus = jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = 'guidance_trigger_outbox_status_check'",
                String.class
        );
        assertThat(outboxStatus).contains("PENDING", "CLAIMED", "DONE", "FAILED");
        assertThat(outboxStatus).doesNotContain("LOST_FISH");

        String outboxPrimary = jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = 'guidance_trigger_outbox_primary_trigger_check'",
                String.class
        );
        assertThat(outboxPrimary).contains("REPEATED_BITE_PATTERN", "NO_BITE_THRESHOLD");
        assertThat(outboxPrimary).doesNotContain("USER_REQUEST");
        assertThat(outboxPrimary).doesNotContain("LOST_FISH");
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
}

package com.aifishing.guidance;

import com.aifishing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceFlywayV35IT extends AbstractIntegrationTest {

    @Test
    void v35AllowsRunningTimeoutAndNullableSnapshots() {
        assertThat(nullable("state_snapshot")).isEqualTo("YES");
        assertThat(nullable("context_snapshot")).isEqualTo("YES");

        String check = jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = 'agent_runs_status_check'",
                String.class
        );
        assertThat(check).contains("RUNNING", "COMPLETED", "FALLBACK", "FAILED", "TIMEOUT");
    }

    private String nullable(String column) {
        return jdbcTemplate.queryForObject(
                """
                        select is_nullable
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = 'agent_runs'
                          and column_name = ?
                        """,
                String.class,
                column
        );
    }
}

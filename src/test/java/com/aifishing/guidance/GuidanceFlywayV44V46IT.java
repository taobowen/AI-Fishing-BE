package com.aifishing.guidance;

import com.aifishing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuidanceFlywayV44V46IT extends AbstractIntegrationTest {

    @Test
    void v44V45V46AddPolicyStampsRuntimeControlAndUnscorable() {
        assertThat(columnNullable("agent_runs", "agent_policy_version")).isEqualTo("YES");
        assertThat(columnNullable("agent_runs", "learning_algorithm_version")).isEqualTo("YES");
        assertThat(columnNullable("agent_runs", "learning_snapshot_version")).isEqualTo("YES");
        assertThat(columnNullable("agent_runs", "fallback_reason")).isEqualTo("YES");

        assertThat(tableExists("agent_runtime_control")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from agent_runtime_control where id = 1",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select production_version from agent_runtime_control where id = 1",
                String.class
        )).isEqualTo("v1");

        assertThat(constraint("guidance_eval_case_results_status_check"))
                .contains("PASS", "FAIL", "ERROR", "SKIP", "UNSCORABLE");

        UUID evalRunId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into guidance_eval_runs (
                            id, schema_version, suite, kind, replay_mode, started_at
                        ) values (?, 'guidance.contracts.v1', 'version-compare', 'SHADOW_REPLAY',
                            'FROZEN_REPLAY', now())
                        """,
                evalRunId
        );
        jdbcTemplate.update(
                """
                        insert into guidance_eval_case_results (
                            id, eval_run_id, schema_version, case_id, status, skip_reason
                        ) values (?, ?, 'guidance.contracts.v1', 'shadow/unscorable', 'UNSCORABLE',
                            'ACTION_DIFFERENT_OUTCOME_UNSCORABLE')
                        """,
                UUID.randomUUID(),
                evalRunId
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        insert into agent_runtime_control (
                            id, agent_enabled, production_version, shadow_enabled, learning_enabled, updated_at
                        ) values (2, TRUE, 'v1', FALSE, TRUE, now())
                        """
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                """
                        select exists (
                            select 1 from pg_tables
                            where schemaname = 'public' and tablename = ?
                        )
                        """,
                Boolean.class,
                table
        ));
    }

    private String columnNullable(String table, String name) {
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

    private String constraint(String name) {
        return jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = ?",
                String.class,
                name
        );
    }
}

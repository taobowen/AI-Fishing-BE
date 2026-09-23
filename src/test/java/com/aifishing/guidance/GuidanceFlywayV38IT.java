package com.aifishing.guidance;

import com.aifishing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuidanceFlywayV38IT extends AbstractIntegrationTest {

    @Test
    void v38AddsEvalTablesCoverageScenarioAndOutboxQuarantine() {
        assertThat(tableExists("guidance_eval_runs")).isTrue();
        assertThat(tableExists("guidance_eval_case_results")).isTrue();
        assertThat(tableExists("guidance_online_metric_rollups")).isTrue();

        assertThat(column("guidance_eval_runs", "replay_mode")).isEqualTo("NO");
        assertThat(column("guidance_eval_runs", "eval_coverage")).isEqualTo("YES");
        assertThat(column("guidance_eval_runs", "scenario_id")).isEqualTo("YES");
        assertThat(column("guidance_eval_runs", "seed")).isEqualTo("YES");
        assertThat(column("guidance_eval_runs", "simulation_version")).isEqualTo("YES");
        assertThat(column("guidance_eval_case_results", "scenario_id")).isEqualTo("YES");
        assertThat(column("guidance_learning_outbox", "quarantine_reason")).isEqualTo("YES");

        assertThat(constraint("guidance_eval_runs_kind_check")).contains("SIMULATION", "PLATFORM_REGRESSION");
        assertThat(constraint("guidance_eval_runs_replay_mode_check"))
                .contains("FROZEN_REPLAY", "COMPONENT_RECOMPUTE");
        assertThat(constraint("guidance_eval_case_results_status_check"))
                .contains("PASS", "FAIL", "ERROR", "SKIP");
        assertThat(constraint("guidance_learning_outbox_job_type_check")).contains("ONLINE_METRICS_ROLLUP");
        assertThat(constraint("guidance_learning_outbox_status_check")).contains("QUARANTINED", "DLQ");
        assertThat(constraint("guidance_learning_outbox_quarantine_reason_check")).contains("UNKNOWN_JOB_TYPE");

        UUID runId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into guidance_eval_runs (
                            id, schema_version, suite, kind, replay_mode,
                            loaded_cases, skipped_cases, attempted_cases, eval_coverage,
                            scenario_id, seed, started_at
                        ) values (?, 'guidance.contracts.v1', 'platform/safety-block', 'PLATFORM_REGRESSION',
                            'FROZEN_REPLAY', 3, 1, 4, 0.750, 'scenario-safety-block', 7, now())
                        """,
                runId
        );
        jdbcTemplate.update(
                """
                        insert into guidance_eval_case_results (
                            id, eval_run_id, schema_version, case_id, status, scenario_id, seed
                        ) values (?, ?, 'guidance.contracts.v1', 'platform/safety-block/return', 'SKIP',
                            'scenario-safety-block', 7)
                        """,
                UUID.randomUUID(),
                runId
        );
        jdbcTemplate.update(
                """
                        insert into guidance_online_metric_rollups (
                            id, schema_version, window_start, window_end, grain
                        ) values (?, 'guidance.contracts.v1', now(), now(), 'HOUR')
                        """,
                UUID.randomUUID()
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        insert into guidance_eval_runs (
                            id, schema_version, suite, kind, replay_mode, started_at
                        ) values (?, 'guidance.contracts.v1', 'bad', 'PLATFORM_REGRESSION', 'LIVE_MIX', now())
                        """,
                UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        insert into guidance_learning_outbox (
                            id, job_type, idempotency_key, payload, status
                        ) values (?, 'ONLINE_METRICS_ROLLUP', ?, '{}'::jsonb, 'QUARANTINED')
                        """,
                UUID.randomUUID(),
                "eval-quarantine-missing-reason"
        )).isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update(
                """
                        insert into guidance_learning_outbox (
                            id, job_type, idempotency_key, payload, status, quarantine_reason
                        ) values (?, 'ONLINE_METRICS_ROLLUP', ?, '{}'::jsonb, 'QUARANTINED', 'UNKNOWN_JOB_TYPE')
                        """,
                UUID.randomUUID(),
                "eval-quarantine-unknown-job"
        );
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

    private String constraint(String name) {
        return jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = ?",
                String.class,
                name
        );
    }
}

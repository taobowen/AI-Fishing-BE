-- Phase 5 eval foundation. Internal eval tables plus learning-outbox quarantine.
-- Eval HTTP is not exposed. SIMULATION columns are placeholders only.

CREATE TABLE guidance_eval_runs (
    id UUID PRIMARY KEY,
    schema_version VARCHAR(64) NOT NULL,
    suite VARCHAR(128) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    replay_mode VARCHAR(32) NOT NULL,
    recomputed_components JSONB NOT NULL DEFAULT '[]'::jsonb,
    component_versions JSONB NOT NULL DEFAULT '{}'::jsonb,
    pricing_version VARCHAR(64),
    loaded_cases INTEGER NOT NULL DEFAULT 0,
    skipped_cases INTEGER NOT NULL DEFAULT 0,
    attempted_cases INTEGER NOT NULL DEFAULT 0,
    passed_cases INTEGER,
    eval_coverage NUMERIC(4, 3),
    scenario_id VARCHAR(128),
    seed BIGINT,
    simulation_version VARCHAR(64),
    scenario_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT guidance_eval_runs_kind_check CHECK (kind IN (
        'PLATFORM_REGRESSION', 'AGENT_POLICY_EVAL', 'SHADOW_REPLAY', 'ONLINE_ROLLUP', 'SIMULATION'
    )),
    CONSTRAINT guidance_eval_runs_replay_mode_check CHECK (replay_mode IN (
        'FROZEN_REPLAY', 'COMPONENT_RECOMPUTE'
    )),
    CONSTRAINT guidance_eval_runs_counts_check CHECK (
        loaded_cases >= 0 AND skipped_cases >= 0 AND attempted_cases >= 0
        AND (passed_cases IS NULL OR passed_cases >= 0)
    )
);

CREATE INDEX idx_guidance_eval_runs_kind_started
    ON guidance_eval_runs (kind, started_at DESC);

CREATE TABLE guidance_eval_case_results (
    id UUID PRIMARY KEY,
    eval_run_id UUID NOT NULL REFERENCES guidance_eval_runs (id),
    schema_version VARCHAR(64) NOT NULL,
    case_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    allowed_actions JSONB NOT NULL DEFAULT '[]'::jsonb,
    forbidden_actions JSONB NOT NULL DEFAULT '[]'::jsonb,
    invariants JSONB NOT NULL DEFAULT '[]'::jsonb,
    actual_decision JSONB,
    actual_primary_action VARCHAR(32),
    recorded_primary_action VARCHAR(32),
    recorded_outcome_kind VARCHAR(16),
    skip_reason VARCHAR(500),
    error_message VARCHAR(1000),
    scenario_id VARCHAR(128),
    seed BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT guidance_eval_case_results_status_check CHECK (
        status IN ('PASS', 'FAIL', 'ERROR', 'SKIP')
    ),
    CONSTRAINT guidance_eval_case_results_recorded_outcome_check CHECK (
        recorded_outcome_kind IS NULL OR recorded_outcome_kind IN (
            'BITE', 'FISH_ON', 'CATCH_LANDED', 'CATCH_LOST', 'NO_BITE', 'NONE'
        )
    ),
    CONSTRAINT guidance_eval_case_results_actual_action_check CHECK (
        actual_primary_action IS NULL OR actual_primary_action IN (
            'STAY', 'MOVE', 'CHANGE_LURE', 'CHANGE_DEPTH', 'CHANGE_RETRIEVE', 'RETURN'
        )
    ),
    CONSTRAINT guidance_eval_case_results_recorded_action_check CHECK (
        recorded_primary_action IS NULL OR recorded_primary_action IN (
            'STAY', 'MOVE', 'CHANGE_LURE', 'CHANGE_DEPTH', 'CHANGE_RETRIEVE', 'RETURN'
        )
    )
);

CREATE INDEX idx_guidance_eval_case_results_run
    ON guidance_eval_case_results (eval_run_id, created_at);

-- Raw counts only. Rates are derived in OnlineGuidanceMetrics, never stored as a fake 0.
CREATE TABLE guidance_online_metric_rollups (
    id UUID PRIMARY KEY,
    schema_version VARCHAR(64) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    grain VARCHAR(16) NOT NULL,
    attribution_dimension VARCHAR(16),
    fish_on_success_count BIGINT NOT NULL DEFAULT 0,
    bite_signal_only_count BIGINT NOT NULL DEFAULT 0,
    no_fish_signal_count BIGINT NOT NULL DEFAULT 0,
    not_followed_count BIGINT NOT NULL DEFAULT 0,
    unattributed_count BIGINT NOT NULL DEFAULT 0,
    followed_recommendation_count BIGINT NOT NULL DEFAULT 0,
    explicit_accepted_count BIGINT NOT NULL DEFAULT 0,
    explicit_partial_count BIGINT NOT NULL DEFAULT 0,
    reject_count BIGINT NOT NULL DEFAULT 0,
    override_count BIGINT NOT NULL DEFAULT 0,
    candidate_count BIGINT NOT NULL DEFAULT 0,
    delivered_count BIGINT NOT NULL DEFAULT 0,
    candidate_unsafe_count BIGINT NOT NULL DEFAULT 0,
    candidate_invalid_waypoint_count BIGINT NOT NULL DEFAULT 0,
    validator_interception_count BIGINT NOT NULL DEFAULT 0,
    unsafe_delivered_count BIGINT NOT NULL DEFAULT 0,
    invalid_delivered_waypoint_count BIGINT NOT NULL DEFAULT 0,
    effective_fishing_effort_seconds BIGINT NOT NULL DEFAULT 0,
    pricing_version VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT guidance_online_metric_rollups_grain_check CHECK (
        grain IN ('HOUR', 'DAY')
    ),
    CONSTRAINT guidance_online_metric_rollups_dimension_check CHECK (
        attribution_dimension IS NULL OR attribution_dimension IN (
            'LOCATION', 'LURE', 'DEPTH', 'RETRIEVE'
        )
    ),
    CONSTRAINT guidance_online_metric_rollups_window_key UNIQUE NULLS NOT DISTINCT (
        window_start, window_end, grain, attribution_dimension
    )
);

ALTER TABLE guidance_learning_outbox
    DROP CONSTRAINT guidance_learning_outbox_job_type_check;

ALTER TABLE guidance_learning_outbox
    ADD CONSTRAINT guidance_learning_outbox_job_type_check CHECK (job_type IN (
        'ATTRIBUTE_OUTCOME', 'AGGREGATE_EMPIRICAL', 'SESSION_SUMMARY',
        'REFLECTION_EVAL', 'PREFERENCE_UPDATE', 'ONLINE_METRICS_ROLLUP'
    ));

ALTER TABLE guidance_learning_outbox
    DROP CONSTRAINT guidance_learning_outbox_status_check;

ALTER TABLE guidance_learning_outbox
    ADD CONSTRAINT guidance_learning_outbox_status_check CHECK (status IN (
        'PENDING', 'CLAIMED', 'DONE', 'FAILED', 'DLQ', 'QUARANTINED'
    ));

ALTER TABLE guidance_learning_outbox
    ADD COLUMN quarantine_reason VARCHAR(32);

ALTER TABLE guidance_learning_outbox
    ADD CONSTRAINT guidance_learning_outbox_quarantine_reason_check CHECK (
        quarantine_reason IS NULL OR quarantine_reason IN ('UNKNOWN_JOB_TYPE')
    );

ALTER TABLE guidance_learning_outbox
    ADD CONSTRAINT guidance_learning_outbox_quarantine_status_check CHECK (
        status <> 'QUARANTINED' OR quarantine_reason IS NOT NULL
    );

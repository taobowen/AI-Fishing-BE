-- Additive guidance/agent audit tables. Empirical memory, semantic memory, and
-- reflections stay logical-only until Phase 4 storage semantics are frozen.

CREATE TABLE session_events (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    schema_version VARCHAR(64) NOT NULL,
    type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    source VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT session_events_type_check CHECK (type IN (
        'SESSION_STARTED', 'GPS_UPDATED', 'GPS_ACCURACY_DEGRADED', 'WEATHER_UPDATED',
        'WAYPOINT_ENTERED', 'WAYPOINT_LEFT', 'LURE_CHANGED', 'DEPTH_CHANGED',
        'RETRIEVE_CHANGED', 'ADVICE_CREATED', 'ADVICE_ACCEPTED', 'ADVICE_REJECTED',
        'USER_MOVED', 'FISH_ON', 'CATCH_CREATED', 'NO_BITE', 'PLAN_REPLANNED',
        'SAFETY_ALERT', 'SESSION_PAUSED', 'SESSION_RESUMED', 'SESSION_COMPLETED'
    )),
    CONSTRAINT session_events_source_check CHECK (source IN ('CLIENT', 'SERVER', 'DERIVED')),
    CONSTRAINT session_events_session_idempotency_key UNIQUE (fishing_session_id, idempotency_key)
);

CREATE INDEX idx_session_events_session_occurred
    ON session_events (fishing_session_id, occurred_at);

CREATE TABLE weather_snapshots (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    schema_version VARCHAR(64) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    envelope JSONB NOT NULL,
    wind_speed_kph NUMERIC(8, 2),
    wind_direction VARCHAR(8),
    temperature_c NUMERIC(6, 2),
    pressure_hpa NUMERIC(8, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT weather_snapshots_wind_dir_check CHECK (
        wind_direction IS NULL OR wind_direction IN ('N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW')
    )
);

CREATE INDEX idx_weather_snapshots_session_observed
    ON weather_snapshots (fishing_session_id, observed_at);

CREATE TABLE lure_events (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    schema_version VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    lure_family VARCHAR(32) NOT NULL,
    presentation VARCHAR(32) NOT NULL,
    depth_m NUMERIC(8, 2),
    retrieve_style VARCHAR(32),
    envelope JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT lure_events_family_check CHECK (lure_family IN (
        'PADDLETAIL', 'MINNOW_SOFT_PLASTIC', 'GRUB', 'TUBE', 'NED_RIG', 'DROP_SHOT_BAIT',
        'JIG', 'JERKBAIT', 'CRANKBAIT', 'LIPLESS_CRANKBAIT', 'SPINNERBAIT', 'CHATTERBAIT',
        'SPOON', 'INLINE_SPINNER', 'TOPWATER', 'FROG', 'TEXAS_RIG', 'CAROLINA_RIG', 'BUZZBAIT', 'OTHER'
    )),
    CONSTRAINT lure_events_presentation_check CHECK (presentation IN (
        'STEADY_RETRIEVE', 'TWITCH_PAUSE', 'STOP_AND_GO', 'WALK_THE_DOG', 'POP_AND_PAUSE',
        'BUZZ', 'LIFT_DROP', 'HOP_ALONG_BOTTOM', 'DRAG_AND_SHAKE', 'SWIM_NEAR_BOTTOM',
        'YO_YO', 'DEAD_STICK', 'SLOW_ROLL', 'BURN', 'VERTICAL_JIG', 'CAST_ACROSS_CONTOUR', 'OTHER'
    )),
    CONSTRAINT lure_events_retrieve_check CHECK (
        retrieve_style IS NULL OR retrieve_style IN (
            'SLOW', 'MODERATE', 'FAST', 'STOP_AND_GO', 'TWITCH', 'DEAD_STICK', 'OTHER'
        )
    )
);

CREATE INDEX idx_lure_events_session_occurred
    ON lure_events (fishing_session_id, occurred_at);

CREATE TABLE agent_runs (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    schema_version VARCHAR(64) NOT NULL,
    trigger VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    state_snapshot JSONB NOT NULL,
    context_snapshot JSONB NOT NULL,
    memory_ref_ids JSONB,
    model_provider VARCHAR(64),
    model_name VARCHAR(128),
    model_version VARCHAR(64),
    prompt_version VARCHAR(64),
    tool_schema_version VARCHAR(64),
    context_version VARCHAR(64),
    guidance_plan_version INTEGER,
    decision_id UUID,
    trace_id VARCHAR(128),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_runs_trigger_check CHECK (trigger IN (
        'USER_REQUEST', 'NO_BITE_THRESHOLD', 'FISH_ON', 'WAYPOINT_REACHED',
        'PLAN_STEP_COMPLETED', 'SIGNIFICANT_WEATHER_CHANGE', 'SIGNIFICANT_LOCATION_CHANGE',
        'CONSECUTIVE_FAILURE', 'SAFETY_STATE_CHANGED', 'ROUTE_DEVIATION', 'RETURN_RISK_CHANGED'
    )),
    CONSTRAINT agent_runs_status_check CHECK (status IN ('COMPLETED', 'FALLBACK', 'FAILED'))
);

CREATE INDEX idx_agent_runs_session_started
    ON agent_runs (fishing_session_id, started_at DESC);

CREATE TABLE agent_tool_calls (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_runs (id),
    schema_version VARCHAR(64) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    request JSONB NOT NULL,
    result JSONB NOT NULL,
    latency_ms INTEGER,
    observed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_tool_calls_name_check CHECK (tool_name IN (
        'get_nearby_waypoints', 'get_waypoint_structure', 'get_live_waypoint_activity',
        'get_historical_performance', 'get_fishing_knowledge', 'get_alternative_route'
    ))
);

CREATE INDEX idx_agent_tool_calls_run ON agent_tool_calls (run_id, observed_at);

CREATE TABLE agent_candidate_decisions (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_runs (id),
    schema_version VARCHAR(64) NOT NULL,
    decision JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_candidate_decisions_run_key UNIQUE (run_id)
);

CREATE TABLE agent_validations (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_runs (id),
    schema_version VARCHAR(64) NOT NULL,
    result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_validations_run_key UNIQUE (run_id)
);

CREATE TABLE agent_delivered_decisions (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_runs (id),
    schema_version VARCHAR(64) NOT NULL,
    decision JSONB NOT NULL,
    fallback_used BOOLEAN NOT NULL,
    fallback_reason VARCHAR(500),
    system_confidence NUMERIC(4, 3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_delivered_decisions_run_key UNIQUE (run_id)
);

CREATE TABLE agent_feedback (
    id UUID PRIMARY KEY,
    delivered_decision_id UUID NOT NULL REFERENCES agent_delivered_decisions (id),
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    schema_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reject_reason VARCHAR(500),
    note VARCHAR(2000),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_feedback_status_check CHECK (status IN ('ACCEPTED', 'REJECTED', 'PARTIALLY_FOLLOWED'))
);

CREATE INDEX idx_agent_feedback_session ON agent_feedback (fishing_session_id, occurred_at);

CREATE TABLE user_action_events (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    delivered_decision_id UUID REFERENCES agent_delivered_decisions (id),
    schema_version VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    followed_primary BOOLEAN NOT NULL,
    actual_action VARCHAR(32),
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_action_events_action_check CHECK (
        actual_action IS NULL OR actual_action IN (
            'STAY', 'MOVE', 'CHANGE_LURE', 'CHANGE_DEPTH', 'CHANGE_RETRIEVE', 'RETURN'
        )
    )
);

CREATE INDEX idx_user_action_events_session
    ON user_action_events (fishing_session_id, occurred_at);

CREATE TABLE guidance_plan_versions (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    schema_version VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    parent_version INTEGER,
    replan_reason VARCHAR(128),
    replan_scope VARCHAR(32),
    created_by VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT guidance_plan_versions_created_by_check CHECK (created_by IN ('AGENT', 'SAFETY', 'USER')),
    CONSTRAINT guidance_plan_versions_scope_check CHECK (
        replan_scope IS NULL OR replan_scope IN ('TACTICAL_LOCAL', 'REGIONAL', 'GLOBAL', 'SAFETY_OVERRIDE')
    ),
    CONSTRAINT guidance_plan_versions_session_version_key UNIQUE (fishing_session_id, version)
);

CREATE TABLE guidance_plan_steps (
    id UUID PRIMARY KEY,
    guidance_plan_version_id UUID NOT NULL REFERENCES guidance_plan_versions (id),
    schema_version VARCHAR(64) NOT NULL,
    step INTEGER NOT NULL,
    type VARCHAR(32) NOT NULL,
    committed BOOLEAN NOT NULL,
    trip_waypoint_id UUID REFERENCES trip_waypoints (id),
    duration_minutes INTEGER,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT guidance_plan_steps_type_check CHECK (type IN (
        'STAY', 'MOVE', 'CHANGE_LURE', 'CHANGE_DEPTH', 'CHANGE_RETRIEVE', 'RETURN'
    )),
    CONSTRAINT guidance_plan_steps_version_step_key UNIQUE (guidance_plan_version_id, step)
);

CREATE INDEX idx_guidance_plan_versions_session
    ON guidance_plan_versions (fishing_session_id, version DESC);

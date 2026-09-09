CREATE TABLE trip_strategy_runs (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips (id),
    status VARCHAR(32) NOT NULL,
    model_id VARCHAR(128),
    prompt_version VARCHAR(64),
    feature_pipeline VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    fishing_context JSONB,
    weather_snapshot JSONB,
    strategy_profile JSONB,
    source_metadata JSONB,
    usage_metadata JSONB,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trip_strategy_runs_trip_started ON trip_strategy_runs (trip_id, started_at DESC);
CREATE INDEX idx_trip_strategy_runs_trip_status ON trip_strategy_runs (trip_id, status, completed_at DESC);

ALTER TABLE trip_strategy_runs
    ADD COLUMN feature_analysis_version VARCHAR(64);

CREATE TABLE trip_planning_runs (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips (id),
    strategy_run_id UUID REFERENCES trip_strategy_runs (id),
    status VARCHAR(32) NOT NULL,
    feature_pipeline VARCHAR(16),
    feature_analysis_version VARCHAR(64),
    algorithm_version VARCHAR(32),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    input_snapshot JSONB,
    ranking_config JSONB,
    filter_summary JSONB,
    warnings JSONB,
    usage_metadata JSONB,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trip_planning_runs_trip_started ON trip_planning_runs (trip_id, started_at DESC);
CREATE INDEX idx_trip_planning_runs_trip_status ON trip_planning_runs (trip_id, status, completed_at DESC);

ALTER TABLE trip_plans
    ADD COLUMN strategy_run_id UUID REFERENCES trip_strategy_runs (id),
    ADD COLUMN planning_run_id UUID REFERENCES trip_planning_runs (id),
    ADD COLUMN feature_pipeline VARCHAR(16),
    ADD COLUMN feature_analysis_version VARCHAR(64),
    ADD COLUMN planning_algorithm_version VARCHAR(32),
    ADD COLUMN overall_plan_confidence NUMERIC(6, 4),
    ADD COLUMN total_estimated_travel_distance_m NUMERIC(12, 2),
    ADD COLUMN total_estimated_travel_minutes NUMERIC(10, 2),
    ADD COLUMN warnings JSONB,
    ADD COLUMN score_snapshot JSONB;

ALTER TABLE trip_waypoints
    ADD COLUMN lake_feature_id UUID REFERENCES lake_features (id),
    ADD COLUMN representative_depth_m NUMERIC(8, 2),
    ADD COLUMN candidate_score NUMERIC(6, 4),
    ADD COLUMN score_breakdown JSONB,
    ADD COLUMN recommended_techniques JSONB,
    ADD COLUMN estimated_travel_distance_from_previous_m NUMERIC(12, 2),
    ADD COLUMN estimated_travel_minutes_from_previous NUMERIC(10, 2),
    ADD COLUMN metadata JSONB;

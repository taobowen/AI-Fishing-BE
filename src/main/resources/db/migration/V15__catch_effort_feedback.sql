CREATE TABLE session_pause_intervals (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    paused_at TIMESTAMPTZ NOT NULL,
    resumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_session_pause_intervals_session
    ON session_pause_intervals (fishing_session_id, paused_at);

CREATE UNIQUE INDEX idx_session_pause_intervals_one_open
    ON session_pause_intervals (fishing_session_id)
    WHERE resumed_at IS NULL;

CREATE TABLE catch_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    trip_id UUID NOT NULL REFERENCES trips (id),
    trip_plan_id UUID REFERENCES trip_plans (id),
    trip_waypoint_id UUID REFERENCES trip_waypoints (id),
    lake_feature_id UUID,
    client_catch_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    location GEOMETRY(Point, 4326),
    gps_accuracy_m NUMERIC(8, 2),
    association_method VARCHAR(32) NOT NULL,
    distance_to_waypoint_m NUMERIC(10, 2),
    status VARCHAR(32) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    species VARCHAR(32),
    length_cm NUMERIC(6, 2),
    weight_kg NUMERIC(6, 3),
    technique_type VARCHAR(32),
    lure_name VARCHAR(128),
    notes VARCHAR(2000),
    planned_feature_type VARCHAR(64),
    primary_target_species VARCHAR(32) NOT NULL,
    strategy_run_id UUID,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT catch_events_session_client_key UNIQUE (fishing_session_id, client_catch_id)
);

CREATE INDEX idx_catch_events_session_occurred
    ON catch_events (fishing_session_id, occurred_at);
CREATE INDEX idx_catch_events_user_occurred
    ON catch_events (user_id, occurred_at);
CREATE INDEX idx_catch_events_location
    ON catch_events USING GIST (location);

CREATE TABLE fishing_effort_segments (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    trip_waypoint_id UUID REFERENCES trip_waypoints (id),
    lake_feature_id UUID,
    segment_type VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL,
    duration_seconds INTEGER NOT NULL,
    representative_location GEOMETRY(Point, 4326),
    track_geometry GEOMETRY(LineString, 4326),
    confidence NUMERIC(4, 3) NOT NULL,
    derivation_version VARCHAR(64) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fishing_effort_segments_session
    ON fishing_effort_segments (fishing_session_id, started_at);
CREATE INDEX idx_fishing_effort_segments_type
    ON fishing_effort_segments (fishing_session_id, segment_type);
CREATE INDEX idx_fishing_effort_segments_track
    ON fishing_effort_segments USING GIST (track_geometry);
CREATE INDEX idx_fishing_effort_segments_rep
    ON fishing_effort_segments USING GIST (representative_location);

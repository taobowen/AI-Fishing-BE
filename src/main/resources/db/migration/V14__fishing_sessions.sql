ALTER TABLE fishing_sessions
    ADD COLUMN plan_version INTEGER,
    ADD COLUMN paused_at TIMESTAMPTZ,
    ADD COLUMN total_paused_seconds INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN summary JSONB;

CREATE UNIQUE INDEX idx_fishing_sessions_one_unfinished
    ON fishing_sessions (user_id)
    WHERE status IN ('ACTIVE', 'PAUSED');

CREATE TABLE session_location_points (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    location GEOMETRY(Point, 4326) NOT NULL,
    accuracy_m NUMERIC(8, 2),
    altitude_m NUMERIC(8, 2),
    speed_mps NUMERIC(8, 3),
    heading_degrees NUMERIC(6, 2),
    client_point_id VARCHAR(128) NOT NULL,
    quality VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT session_location_points_session_client_key UNIQUE (fishing_session_id, client_point_id)
);

CREATE INDEX idx_session_location_points_session_recorded
    ON session_location_points (fishing_session_id, recorded_at);
CREATE INDEX idx_session_location_points_location
    ON session_location_points USING GIST (location);

CREATE TABLE session_waypoint_progress (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    trip_waypoint_id UUID NOT NULL REFERENCES trip_waypoints (id),
    sequence INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    first_approached_at TIMESTAMPTZ,
    arrived_at TIMESTAMPTZ,
    departed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    skipped_at TIMESTAMPTZ,
    accumulated_dwell_seconds INTEGER NOT NULL DEFAULT 0,
    closest_distance_m NUMERIC(10, 2),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT session_waypoint_progress_session_waypoint_key UNIQUE (fishing_session_id, trip_waypoint_id)
);

CREATE INDEX idx_session_waypoint_progress_session
    ON session_waypoint_progress (fishing_session_id, sequence);

CREATE TABLE session_client_events (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    client_event_id VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    payload JSONB,
    applied BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT session_client_events_session_client_key UNIQUE (fishing_session_id, client_event_id)
);

CREATE INDEX idx_session_client_events_session_occurred
    ON session_client_events (fishing_session_id, occurred_at, received_at);

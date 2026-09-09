CREATE TABLE fishing_sessions (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips (id),
    user_id UUID NOT NULL REFERENCES users (id),
    trip_plan_id UUID REFERENCES trip_plans (id),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fishing_sessions_trip_id ON fishing_sessions (trip_id);
CREATE INDEX idx_fishing_sessions_user_id ON fishing_sessions (user_id);

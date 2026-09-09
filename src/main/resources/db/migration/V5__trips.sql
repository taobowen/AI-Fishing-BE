CREATE TABLE trips (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    lake_id UUID NOT NULL REFERENCES lakes (id),
    primary_target_species VARCHAR(64) NOT NULL,
    secondary_target_species JSONB NOT NULL DEFAULT '[]'::jsonb,
    planned_date DATE NOT NULL,
    fishing_start_time TIME NOT NULL,
    fishing_end_time TIME NOT NULL,
    boat_id UUID REFERENCES boats (id),
    fishing_mode VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT trips_time_range_chk CHECK (fishing_end_time > fishing_start_time)
);

CREATE INDEX idx_trips_user_id ON trips (user_id);
CREATE INDEX idx_trips_planned_date ON trips (planned_date);
CREATE INDEX idx_trips_lake_id ON trips (lake_id);

CREATE TABLE trip_plans (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips (id),
    version INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    metadata JSONB,
    CONSTRAINT trip_plans_trip_id_version_key UNIQUE (trip_id, version)
);

CREATE TABLE trip_waypoints (
    id UUID PRIMARY KEY,
    trip_plan_id UUID NOT NULL REFERENCES trip_plans (id),
    sequence INT NOT NULL,
    location geometry(Point, 4326) NOT NULL,
    planned_arrival_time TIME,
    planned_departure_time TIME,
    feature_type VARCHAR(64),
    min_depth_m NUMERIC(8, 2),
    max_depth_m NUMERIC(8, 2),
    recommended_technique VARCHAR(128),
    reason TEXT,
    CONSTRAINT trip_waypoints_plan_sequence_key UNIQUE (trip_plan_id, sequence)
);

CREATE INDEX idx_trip_waypoints_location ON trip_waypoints USING GIST (location);

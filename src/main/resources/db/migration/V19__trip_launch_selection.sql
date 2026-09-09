CREATE TABLE trip_launch_selections (
    trip_id UUID PRIMARY KEY REFERENCES trips (id) ON DELETE CASCADE,
    mode VARCHAR(32) NOT NULL,
    official_access_point_id UUID REFERENCES lake_access_points (id),
    requested_point geometry(Point, 4326),
    shore_access_point geometry(Point, 4326),
    route_start_point geometry(Point, 4326),
    snap_distance_m NUMERIC(8, 2),
    custom_access_type VARCHAR(32),
    shoreline_kind VARCHAR(32),
    resolution_version VARCHAR(64),
    warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT trip_launch_mode_chk CHECK (
        mode IN ('AUTO_RECOMMENDED', 'OFFICIAL_SELECTED', 'CUSTOM_SELECTED')
    )
);

CREATE INDEX idx_trip_launch_official ON trip_launch_selections (official_access_point_id);

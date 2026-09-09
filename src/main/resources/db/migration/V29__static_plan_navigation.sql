-- Plan-scoped transit LineStrings. Does not reschedule RoutePlanner times.
CREATE TABLE trip_plan_transit_legs (
    id UUID PRIMARY KEY,
    trip_plan_id UUID NOT NULL REFERENCES trip_plans (id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    from_kind VARCHAR(16) NOT NULL,
    to_kind VARCHAR(16) NOT NULL,
    from_visit_id UUID,
    to_visit_id UUID,
    transit_path geometry(LineString, 4326),
    path_distance_meters NUMERIC(12, 2),
    planned_travel_minutes NUMERIC(10, 2),
    source_water_path_id UUID REFERENCES lake_fishing_water_paths (id) ON DELETE SET NULL,
    navigation_version VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT trip_plan_transit_legs_seq UNIQUE (trip_plan_id, sequence)
);

CREATE INDEX idx_trip_plan_transit_legs_plan ON trip_plan_transit_legs (trip_plan_id);

-- Skip reason on existing SKIPPED status (historical SKIPPED is user skip).
ALTER TABLE session_waypoint_progress
    ADD COLUMN skip_reason VARCHAR(32);

UPDATE session_waypoint_progress
SET skip_reason = 'USER'
WHERE status = 'SKIPPED'
  AND skip_reason IS NULL;

-- Transit water-path cache identity: snapshot + nav version + from/to keys.
-- Do not invent a zoneId for lake-wide hops.
ALTER TABLE lake_fishing_water_paths
    ALTER COLUMN zone_id DROP NOT NULL;

CREATE UNIQUE INDEX lake_fishing_water_paths_transit_identity
    ON lake_fishing_water_paths (
        spatial_planning_snapshot_id,
        COALESCE(navigation_version, ''),
        from_key,
        to_key
    )
    WHERE zone_id IS NULL;

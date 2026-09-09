ALTER TABLE trip_waypoints
    ADD COLUMN IF NOT EXISTS tactical jsonb;

ALTER TABLE trip_stop_subtargets
    ADD COLUMN IF NOT EXISTS tactical jsonb;

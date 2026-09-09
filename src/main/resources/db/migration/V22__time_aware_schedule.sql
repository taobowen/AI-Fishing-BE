ALTER TABLE trip_waypoints
    ADD COLUMN planned_arrival_at TIMESTAMPTZ,
    ADD COLUMN planned_departure_at TIMESTAMPTZ,
    ADD COLUMN planned_dwell_minutes INTEGER,
    ADD COLUMN environment JSONB,
    ADD COLUMN why_this_time JSONB;

ALTER TABLE trip_plans
    ADD COLUMN planned_launch_departure_at TIMESTAMPTZ,
    ADD COLUMN planned_return_at TIMESTAMPTZ,
    ADD COLUMN total_fishing_minutes NUMERIC(10, 2),
    ADD COLUMN total_planned_minutes NUMERIC(10, 2),
    ADD COLUMN schedule_reserve_minutes INTEGER,
    ADD COLUMN total_wait_minutes INTEGER,
    ADD COLUMN schedule_algorithm_version VARCHAR(32),
    ADD COLUMN schedule_events JSONB;

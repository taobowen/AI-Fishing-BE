ALTER TABLE trip_plans
    ADD COLUMN tactics_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    ADD COLUMN tactics_started_at TIMESTAMPTZ,
    ADD COLUMN tactics_requested BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE trip_plans
SET tactics_status = 'READY',
    tactics_requested = TRUE
WHERE EXISTS (
    SELECT 1
    FROM trip_waypoints w
    WHERE w.trip_plan_id = trip_plans.id
      AND w.tactical IS NOT NULL
);

CREATE INDEX idx_trip_plans_tactics_status ON trip_plans (tactics_status, tactics_started_at);

ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_time_range_chk;

ALTER TABLE trips ADD COLUMN planned_end_date DATE;

UPDATE trips
SET planned_end_date = CASE
    WHEN fishing_end_time > fishing_start_time THEN planned_date
    ELSE planned_date + INTERVAL '1 day'
END
WHERE planned_end_date IS NULL;

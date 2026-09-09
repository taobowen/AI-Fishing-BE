ALTER TABLE lake_access_points
    ADD COLUMN ownership_type VARCHAR(32),
    ADD COLUMN association_distance_meters DOUBLE PRECISION;

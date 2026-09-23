ALTER TABLE catch_events
    ADD COLUMN is_target_species BOOLEAN,
    ADD COLUMN size_bucket VARCHAR(16);

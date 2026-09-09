ALTER TABLE boats
    ADD COLUMN IF NOT EXISTS wind_wave_override VARCHAR(16),
    ADD COLUMN IF NOT EXISTS free_text_hash VARCHAR(64);

ALTER TABLE users
    ADD COLUMN owned_lure_families JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN kit_setup_complete BOOLEAN NOT NULL DEFAULT FALSE;

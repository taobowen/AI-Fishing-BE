ALTER TABLE lakes
    ADD COLUMN ogf_id BIGINT,
    ADD COLUMN waterbody_lid VARCHAR(32),
    ADD COLUMN official_name VARCHAR(255),
    ADD COLUMN municipality VARCHAR(255),
    ADD COLUMN bbox_min_lng DOUBLE PRECISION,
    ADD COLUMN bbox_min_lat DOUBLE PRECISION,
    ADD COLUMN bbox_max_lng DOUBLE PRECISION,
    ADD COLUMN bbox_max_lat DOUBLE PRECISION,
    ADD COLUMN identity_metadata JSONB;

CREATE INDEX idx_lakes_ogf_id ON lakes (ogf_id);
CREATE INDEX idx_lakes_waterbody_lid ON lakes (waterbody_lid);

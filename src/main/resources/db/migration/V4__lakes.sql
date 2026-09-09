CREATE TABLE lakes (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    province VARCHAR(128) NOT NULL,
    country VARCHAR(128) NOT NULL,
    source VARCHAR(64) NOT NULL,
    source_lake_id VARCHAR(128),
    centroid geometry(Point, 4326) NOT NULL,
    boundary geometry(MultiPolygon, 4326),
    mean_depth_m NUMERIC(8, 2),
    max_depth_m NUMERIC(8, 2),
    time_zone_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lakes_name_lower ON lakes (lower(name));
CREATE INDEX idx_lakes_centroid ON lakes USING GIST (centroid);
CREATE INDEX idx_lakes_boundary ON lakes USING GIST (boundary);

CREATE TABLE bathymetry_contours (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    provider VARCHAR(64) NOT NULL,
    source_record_id VARCHAR(128),
    import_version VARCHAR(64) NOT NULL,
    source_metadata JSONB,
    depth_m NUMERIC(8, 2),
    geometry geometry(MultiLineString, 4326) NOT NULL,
    survey_date DATE,
    survey_method VARCHAR(128),
    horizontal_accuracy VARCHAR(128),
    source VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX idx_bathymetry_contours_src
    ON bathymetry_contours (lake_id, provider, source_record_id, import_version)
    WHERE source_record_id IS NOT NULL;
CREATE INDEX idx_bathymetry_contours_lake ON bathymetry_contours (lake_id);
CREATE INDEX idx_bathymetry_contours_geom ON bathymetry_contours USING GIST (geometry);

CREATE TABLE bathymetry_points (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    provider VARCHAR(64) NOT NULL,
    source_record_id VARCHAR(128),
    import_version VARCHAR(64) NOT NULL,
    source_metadata JSONB,
    depth_m NUMERIC(8, 2),
    location geometry(Point, 4326) NOT NULL,
    survey_date DATE,
    survey_method VARCHAR(128),
    accuracy VARCHAR(128),
    source VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX idx_bathymetry_points_src
    ON bathymetry_points (lake_id, provider, source_record_id, import_version)
    WHERE source_record_id IS NOT NULL;
CREATE INDEX idx_bathymetry_points_lake ON bathymetry_points (lake_id);
CREATE INDEX idx_bathymetry_points_geom ON bathymetry_points USING GIST (location);

CREATE TABLE lake_waterways (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    provider VARCHAR(64) NOT NULL,
    source_record_id VARCHAR(128),
    import_version VARCHAR(64) NOT NULL,
    source_metadata JSONB,
    type VARCHAR(32) NOT NULL,
    name VARCHAR(255),
    geometry geometry(Geometry, 4326) NOT NULL,
    source VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX idx_lake_waterways_src
    ON lake_waterways (lake_id, provider, source_record_id, import_version)
    WHERE source_record_id IS NOT NULL;
CREATE INDEX idx_lake_waterways_lake ON lake_waterways (lake_id);
CREATE INDEX idx_lake_waterways_geom ON lake_waterways USING GIST (geometry);

CREATE TABLE wetlands (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    provider VARCHAR(64) NOT NULL,
    source_record_id VARCHAR(128),
    import_version VARCHAR(64) NOT NULL,
    source_metadata JSONB,
    wetland_type VARCHAR(64),
    geometry geometry(Geometry, 4326) NOT NULL,
    source VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX idx_wetlands_src
    ON wetlands (lake_id, provider, source_record_id, import_version)
    WHERE source_record_id IS NOT NULL;
CREATE INDEX idx_wetlands_lake ON wetlands (lake_id);
CREATE INDEX idx_wetlands_geom ON wetlands USING GIST (geometry);

CREATE TABLE lake_fish_species (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    provider VARCHAR(64) NOT NULL,
    source_record_id VARCHAR(128),
    import_version VARCHAR(64) NOT NULL,
    source_metadata JSONB,
    source_species_name VARCHAR(255) NOT NULL,
    species VARCHAR(64),
    observation_type VARCHAR(64),
    observed_date DATE,
    source VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX idx_lake_fish_species_src
    ON lake_fish_species (lake_id, provider, source_record_id, import_version, source_species_name)
    WHERE source_record_id IS NOT NULL;
CREATE INDEX idx_lake_fish_species_lake ON lake_fish_species (lake_id);

CREATE TABLE fish_stocking_records (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    provider VARCHAR(64) NOT NULL,
    source_record_id VARCHAR(128),
    import_version VARCHAR(64) NOT NULL,
    source_metadata JSONB,
    source_species_name VARCHAR(255),
    species VARCHAR(64),
    stocking_year INTEGER,
    stocking_date DATE,
    quantity INTEGER,
    life_stage VARCHAR(64),
    source VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX idx_fish_stocking_src
    ON fish_stocking_records (lake_id, provider, source_record_id, import_version)
    WHERE source_record_id IS NOT NULL;
CREATE INDEX idx_fish_stocking_lake ON fish_stocking_records (lake_id);

CREATE TABLE fish_habitats (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    provider VARCHAR(64) NOT NULL,
    source_record_id VARCHAR(128),
    import_version VARCHAR(64) NOT NULL,
    source_metadata JSONB,
    source_species_name VARCHAR(255),
    species VARCHAR(64),
    habitat_type VARCHAR(128) NOT NULL,
    geometry geometry(Geometry, 4326),
    season_metadata JSONB,
    source VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX idx_fish_habitats_src
    ON fish_habitats (lake_id, provider, source_record_id, import_version)
    WHERE source_record_id IS NOT NULL;
CREATE INDEX idx_fish_habitats_lake ON fish_habitats (lake_id);
CREATE INDEX idx_fish_habitats_geom ON fish_habitats USING GIST (geometry);

CREATE TABLE lake_access_points (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    provider VARCHAR(64) NOT NULL,
    source_record_id VARCHAR(128),
    import_version VARCHAR(64) NOT NULL,
    source_metadata JSONB,
    type VARCHAR(64) NOT NULL,
    name VARCHAR(255),
    location geometry(Point, 4326) NOT NULL,
    boat_launch BOOLEAN,
    shore_access BOOLEAN,
    road_access BOOLEAN,
    parking BOOLEAN,
    source VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX idx_lake_access_src
    ON lake_access_points (lake_id, provider, source_record_id, import_version)
    WHERE source_record_id IS NOT NULL;
CREATE INDEX idx_lake_access_lake ON lake_access_points (lake_id);
CREATE INDEX idx_lake_access_geom ON lake_access_points USING GIST (location);

CREATE TABLE fishing_restrictions (
    id UUID PRIMARY KEY,
    lake_id UUID REFERENCES lakes (id),
    provider VARCHAR(64) NOT NULL,
    source_record_id VARCHAR(128),
    import_version VARCHAR(64) NOT NULL,
    source_metadata JSONB,
    geometry geometry(Geometry, 4326),
    species VARCHAR(64),
    source_species_name VARCHAR(255),
    restriction_type VARCHAR(128),
    valid_from DATE,
    valid_to DATE,
    recurring_season JSONB,
    raw_text TEXT,
    structured_data JSONB,
    source VARCHAR(128) NOT NULL,
    source_reference VARCHAR(1024)
);

CREATE UNIQUE INDEX idx_fishing_restrictions_src
    ON fishing_restrictions (lake_id, provider, source_record_id, import_version)
    WHERE source_record_id IS NOT NULL;
CREATE INDEX idx_fishing_restrictions_lake ON fishing_restrictions (lake_id);
CREATE INDEX idx_fishing_restrictions_geom ON fishing_restrictions USING GIST (geometry);

CREATE TABLE lake_boundaries (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    provider VARCHAR(64) NOT NULL,
    source_record_id VARCHAR(128),
    import_version VARCHAR(64) NOT NULL,
    source_metadata JSONB,
    geometry geometry(MultiPolygon, 4326) NOT NULL,
    source VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX idx_lake_boundaries_src
    ON lake_boundaries (lake_id, provider, source_record_id, import_version)
    WHERE source_record_id IS NOT NULL;
CREATE INDEX idx_lake_boundaries_lake ON lake_boundaries (lake_id);
CREATE INDEX idx_lake_boundaries_geom ON lake_boundaries USING GIST (geometry);

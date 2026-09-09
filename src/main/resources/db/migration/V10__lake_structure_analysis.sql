ALTER TABLE lakes
    ADD COLUMN processing_status VARCHAR(32),
    ADD COLUMN current_analysis_version VARCHAR(64),
    ADD COLUMN processing_error TEXT;

CREATE TABLE lake_analysis_runs (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    analysis_version VARCHAR(64) NOT NULL,
    algorithm_version VARCHAR(64) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    source_dataset_snapshot JSONB,
    parameters JSONB,
    feature_counts JSONB,
    error_summary JSONB
);

CREATE INDEX idx_lake_analysis_runs_lake ON lake_analysis_runs (lake_id, started_at DESC);

CREATE TABLE lake_feature_status (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    feature_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    record_count INTEGER,
    last_attempted_at TIMESTAMPTZ,
    last_successful_analysis_at TIMESTAMPTZ,
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT lake_feature_status_lake_type_key UNIQUE (lake_id, feature_type)
);

CREATE INDEX idx_lake_feature_status_lake ON lake_feature_status (lake_id);

CREATE TABLE lake_features (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    type VARCHAR(32) NOT NULL,
    geometry geometry(Geometry, 4326) NOT NULL,
    min_depth_m NUMERIC(8, 2),
    max_depth_m NUMERIC(8, 2),
    slope NUMERIC(10, 6),
    orientation NUMERIC(6, 2),
    area_m2 NUMERIC(16, 2),
    confidence NUMERIC(5, 4) NOT NULL,
    source_method VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    analysis_version VARCHAR(64) NOT NULL,
    analysis_run_id UUID REFERENCES lake_analysis_runs (id),
    source_dataset_snapshot JSONB,
    derivation_metadata JSONB,
    source_record_id VARCHAR(128)
);

CREATE INDEX idx_lake_features_lake ON lake_features (lake_id);
CREATE INDEX idx_lake_features_type ON lake_features (lake_id, type);
CREATE INDEX idx_lake_features_analysis ON lake_features (lake_id, analysis_version);
CREATE INDEX idx_lake_features_geom ON lake_features USING GIST (geometry);

CREATE TABLE derived_analysis_artifacts (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    analysis_run_id UUID NOT NULL REFERENCES lake_analysis_runs (id),
    analysis_version VARCHAR(64) NOT NULL,
    algorithm_version VARCHAR(64) NOT NULL,
    artifact_type VARCHAR(64) NOT NULL,
    parameters JSONB,
    source_dataset_snapshot JSONB,
    grid_metadata JSONB,
    checksum_sha256 VARCHAR(64) NOT NULL,
    storage_uri TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_derived_analysis_artifacts_lake ON derived_analysis_artifacts (lake_id, analysis_version);

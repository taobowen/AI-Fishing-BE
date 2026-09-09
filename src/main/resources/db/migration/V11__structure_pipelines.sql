ALTER TABLE lake_features
    ADD COLUMN pipeline VARCHAR(16) NOT NULL DEFAULT 'GIS';

ALTER TABLE lake_feature_status
    ADD COLUMN pipeline VARCHAR(16) NOT NULL DEFAULT 'GIS';

ALTER TABLE lake_analysis_runs
    ADD COLUMN pipeline VARCHAR(16) NOT NULL DEFAULT 'GIS';

ALTER TABLE lake_feature_status
    DROP CONSTRAINT lake_feature_status_lake_type_key;

ALTER TABLE lake_feature_status
    ADD CONSTRAINT lake_feature_status_lake_type_pipeline_key UNIQUE (lake_id, feature_type, pipeline);

CREATE INDEX idx_lake_features_pipeline ON lake_features (lake_id, pipeline, type);
CREATE INDEX idx_lake_feature_status_pipeline ON lake_feature_status (lake_id, pipeline);
CREATE INDEX idx_lake_analysis_runs_pipeline ON lake_analysis_runs (lake_id, pipeline, started_at DESC);

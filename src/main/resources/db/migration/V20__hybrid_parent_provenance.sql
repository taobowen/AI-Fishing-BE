ALTER TABLE lake_analysis_runs
    ADD COLUMN gis_parent_run_id UUID REFERENCES lake_analysis_runs (id),
    ADD COLUMN gis_analysis_version VARCHAR(64),
    ADD COLUMN vision_parent_run_id UUID REFERENCES lake_analysis_runs (id),
    ADD COLUMN vision_analysis_version VARCHAR(64),
    ADD COLUMN source_snapshot_id VARCHAR(80);

ALTER TABLE lake_analysis_runs
    ADD CONSTRAINT lake_analysis_runs_hybrid_parents_chk
    CHECK (
        pipeline <> 'HYBRID'
        OR (
            gis_parent_run_id IS NOT NULL
            AND gis_analysis_version IS NOT NULL
            AND vision_parent_run_id IS NOT NULL
            AND vision_analysis_version IS NOT NULL
            AND source_snapshot_id IS NOT NULL
        )
    ) NOT VALID;

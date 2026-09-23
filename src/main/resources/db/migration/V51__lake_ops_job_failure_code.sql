ALTER TABLE lake_ops_jobs
    ADD COLUMN failure_code VARCHAR(64),
    ADD COLUMN launch_attempted_at TIMESTAMPTZ;

ALTER TABLE lake_ops_jobs
    ADD CONSTRAINT lake_ops_jobs_failure_code_chk CHECK (
        failure_code IS NULL OR failure_code IN (
            'IDENTITY_RESOLUTION_FAILED',
            'GIS_PROCESSING_FAILED',
            'NO_PERSISTED_FEATURES',
            'SPATIAL_SNAPSHOT_FAILED',
            'WORKER_START_FAILED',
            'JOB_TIMEOUT'
        )
    );

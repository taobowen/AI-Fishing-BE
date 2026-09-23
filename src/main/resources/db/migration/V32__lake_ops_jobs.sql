CREATE TABLE lake_ops_jobs (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    kind VARCHAR(16) NOT NULL,
    dedupe_key VARCHAR(256) NOT NULL,
    params JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(16) NOT NULL,
    result JSONB,
    error_message TEXT,
    ecs_task_arn TEXT,
    heartbeat_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT lake_ops_jobs_kind_chk CHECK (kind IN ('IMPORT', 'PROCESS', 'SNAPSHOT')),
    CONSTRAINT lake_ops_jobs_status_chk CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE UNIQUE INDEX lake_ops_jobs_active_dedupe_uidx
    ON lake_ops_jobs (lake_id, kind, dedupe_key)
    WHERE status IN ('QUEUED', 'RUNNING');

CREATE INDEX idx_lake_ops_jobs_status_heartbeat
    ON lake_ops_jobs (status, heartbeat_at);

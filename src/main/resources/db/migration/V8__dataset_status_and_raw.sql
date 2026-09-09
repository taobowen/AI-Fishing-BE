CREATE TABLE lake_dataset_status (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    dataset_type VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    record_count INTEGER,
    last_attempted_at TIMESTAMPTZ,
    last_successful_import_at TIMESTAMPTZ,
    source_updated_at TIMESTAMPTZ,
    source_reference VARCHAR(1024),
    metadata JSONB,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT lake_dataset_status_lake_type_provider_key UNIQUE (lake_id, dataset_type, provider)
);

CREATE INDEX idx_lake_dataset_status_lake_id ON lake_dataset_status (lake_id);

CREATE TABLE raw_data_objects (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    dataset_type VARCHAR(64) NOT NULL,
    import_version VARCHAR(64) NOT NULL,
    page_index INTEGER NOT NULL,
    provider VARCHAR(64) NOT NULL,
    source_url TEXT,
    request_metadata JSONB,
    retrieved_at TIMESTAMPTZ NOT NULL,
    checksum_sha256 VARCHAR(64),
    content_type VARCHAR(128),
    storage_uri TEXT NOT NULL,
    http_status INTEGER,
    CONSTRAINT raw_data_objects_page_key UNIQUE (lake_id, dataset_type, import_version, page_index)
);

CREATE INDEX idx_raw_data_objects_lake_id ON raw_data_objects (lake_id);
